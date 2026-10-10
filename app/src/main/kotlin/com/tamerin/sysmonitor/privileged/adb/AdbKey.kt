package com.tamerin.sysmonitor.privileged.adb

import android.content.Context
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date
import javax.crypto.Cipher
import javax.security.auth.x500.X500Principal
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/**
 * RSA-2048-Identität dieser App gegenüber `adbd`: persistentes Schlüsselpaar, das
 * ADB-Public-Key-Format (für den Key-Austausch beim Pairing) und ein selbstsigniertes
 * X.509-Cert für die TLS-1.3-Verbindung.
 *
 * Deterministisch und ohne Netz — aber gegen echtes `adbd` noch nicht verifiziert
 * (DEVICE-VERIFY: v. a. das android_pubkey-Encoding inkl. n0inv/rr).
 */
class AdbKey private constructor(
    val keyPair: KeyPair,
    val certificate: X509Certificate
) {
    /**
     * Public Key im ADB-Format (`android_pubkey.c`): Base64 einer 524-Byte-Struktur
     * `[u32 words=64][u32 n0inv][modulus 256B LE][rr 256B LE][u32 exponent]`, gefolgt
     * von " <name>". Genau das erwartet adbd im Auth-/Pairing-Peer-Info.
     */
    fun adbPublicKey(name: String = KEY_NAME): String {
        val pub = keyPair.public as RSAPublicKey
        val n = pub.modulus
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n0inv = r32.subtract(n.mod(r32).modInverse(r32)).mod(r32) // -n^-1 mod 2^32
        val r = BigInteger.ONE.shiftLeft(2048)
        val rr = r.multiply(r).mod(n)                                  // R^2 mod n

        val buf = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(MODULUS_WORDS)
        buf.putInt(n0inv.toInt())
        buf.put(toLittleEndian(n, MODULUS_SIZE))
        buf.put(toLittleEndian(rr, MODULUS_SIZE))
        buf.putInt(pub.publicExponent.toInt())

        return Base64.getEncoder().encodeToString(buf.array()) + " " + name
    }

    /**
     * Signiert das 20-Byte-Auth-Token von `adbd`. adbd ruft `RSA_sign(NID_sha1, …)`:
     * das Token wird als SHA1-Digest behandelt, in die DigestInfo-ASN.1 gepackt und
     * per PKCS#1 v1.5 RSA-signiert. In Java = RSA-„encrypt" (privater Key) über
     * `SHA1-Prefix || token`.
     */
    fun signAdbToken(token: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keyPair.private)
        return cipher.doFinal(SHA1_DIGEST_INFO_PREFIX + token)
    }

    /** ADB-Public-Key als NUL-terminierte Bytes (Payload für A_AUTH Typ RSAPUBLICKEY). */
    fun adbPublicKeyBytes(): ByteArray = (adbPublicKey() + "\u0000").toByteArray(Charsets.UTF_8)

    private fun toLittleEndian(value: BigInteger, size: Int): ByteArray {
        val raw = value.toByteArray() // big-endian, evtl. führendes 0x00 oder kürzer
        val src = if (raw.size > size) raw.copyOfRange(raw.size - size, raw.size) else raw
        val be = ByteArray(size)
        System.arraycopy(src, 0, be, size - src.size, src.size)
        val le = ByteArray(size)
        for (i in 0 until size) le[i] = be[size - 1 - i]
        return le
    }

    companion object {
        const val KEY_NAME = "redmonitor@android"
        private const val MODULUS_SIZE = 256
        private const val MODULUS_WORDS = 64

        /** ASN.1 DigestInfo-Prefix für SHA-1 (PKCS#1 v1.5), wie von adbd erwartet. */
        private val SHA1_DIGEST_INFO_PREFIX = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02,
            0x1a, 0x05, 0x00, 0x04, 0x14
        )

        /** Lädt das persistente Schlüsselpaar/Cert oder erzeugt es beim ersten Mal. */
        fun getOrCreate(context: Context): AdbKey {
            val dir = File(context.filesDir, "adb").apply { mkdirs() }
            val keyFile = File(dir, "adbkey.pk8")
            val certFile = File(dir, "adbkey.crt")

            if (keyFile.exists() && certFile.exists()) {
                runCatching {
                    val kf = KeyFactory.getInstance("RSA")
                    val priv = kf.generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                    val cert = CertificateFactory.getInstance("X.509")
                        .generateCertificate(certFile.inputStream()) as X509Certificate
                    return AdbKey(KeyPair(cert.publicKey, priv), cert)
                }
                // Bei kaputten Dateien: neu erzeugen.
            }

            val kpg = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
            val kp = kpg.generateKeyPair()
            val cert = selfSignedCert(kp)
            keyFile.writeBytes(kp.private.encoded)
            certFile.writeBytes(cert.encoded)
            return AdbKey(kp, cert)
        }

        private fun selfSignedCert(kp: KeyPair): X509Certificate {
            val now = System.currentTimeMillis()
            val subject = X500Principal("CN=RedMonitor,O=Tamerin,C=DE")
            val builder = JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(now),
                Date(now - 24L * 60 * 60 * 1000),
                Date(now + 30L * 365 * 24 * 60 * 60 * 1000),
                subject,
                kp.public
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
            return JcaX509CertificateConverter().getCertificate(builder.build(signer))
        }
    }
}
