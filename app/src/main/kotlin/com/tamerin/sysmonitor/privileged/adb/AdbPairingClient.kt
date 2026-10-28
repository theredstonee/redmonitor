package com.tamerin.sysmonitor.privileged.adb

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLSocket
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.conscrypt.Conscrypt

/**
 * Wireless-Debugging-Pairing (ohne PC): verbindet den Pairing-Port über TLS 1.3, führt
 * den SPAKE2-Austausch mit dem 6-stelligen Code durch und tauscht danach verschlüsselt
 * die Peer-Infos aus (unser ADB-Public-Key → wird in `adbd` vertraut). Danach kann die
 * App dauerhaft über den Connect-Port verbinden, ohne PC.
 *
 * Wire-Format nach AOSP `adb/pairing_connection` + `adb/pairing_auth`:
 *  - Paket-Header: [version:1][type:1 (SPAKE2_MSG=0 / PEER_INFO=1)][payload_size:4 BE]
 *  - SPAKE2-Passwort = Pairing-Code ‖ 64 Byte TLS-Exported-Keying-Material (bindet den
 *    Code an genau diese TLS-Sitzung)
 *  - Session-Key = HKDF-SHA256(SPAKE2-Key, info) → AES-128-GCM, Nonce = Sequenz-Zähler
 *  - PEER_INFO = 8192-Byte-Struct [type:1][data:8191], verschlüsselt
 *
 * DEVICE-VERIFY: gegen echtes `adbd` testen (Android 11+, Wireless Debugging an).
 */
class AdbPairingClient(
    private val host: String,
    private val port: Int,
    private val pairingCode: String,
    private val key: AdbKey
) {
    /** Falscher Code — eigene Klasse, damit die UI das gezielt melden kann. */
    class WrongCodeException : IOException("Pairing code rejected")

    fun pair(connectTimeoutMs: Int = 8000): Result<Unit> = runCatching {
        if (!Spake2.isAvailable) throw IOException("SPAKE2 native library unavailable")

        val base = Socket().apply {
            connect(InetSocketAddress(host, port), connectTimeoutMs)
            soTimeout = IO_TIMEOUT_MS
        }
        val ssl = (AdbSsl.sslContext(key).socketFactory
            .createSocket(base, host, port, true) as SSLSocket).apply {
            useClientMode = true
            runCatching { enabledProtocols = arrayOf("TLSv1.3") }
            startHandshake()
        }
        ssl.use {
            val input = DataInputStream(ssl.inputStream)
            val output = DataOutputStream(ssl.outputStream)

            val exported = Conscrypt.exportKeyingMaterial(ssl, EXPORTED_KEY_LABEL, null, EXPORTED_KEY_SIZE)
                ?: throw IOException("TLS keying material unavailable")
            val password = pairingCode.toByteArray(Charsets.UTF_8) + exported

            // 1. SPAKE2-Nachrichten tauschen → gemeinsamer Schlüssel
            val cipher = Spake2(Spake2.Role.CLIENT, password).use { spake ->
                writePacket(output, TYPE_SPAKE2_MSG, spake.message)
                val theirMsg = readPacket(input, TYPE_SPAKE2_MSG)
                PairingCipher(spake.process(theirMsg))
            }

            // 2. Peer-Info tauschen: unser Public Key raus, Geräte-GUID rein
            writePacket(output, TYPE_PEER_INFO, cipher.encrypt(peerInfo(key.adbPublicKeyBytes())))
            val theirInfo = try {
                cipher.decrypt(readPacket(input, TYPE_PEER_INFO))
            } catch (e: AEADBadTagException) {
                throw WrongCodeException()
            } catch (e: java.io.EOFException) {
                // adbd schließt die Verbindung, wenn es UNSERE Peer-Info nicht entschlüsseln
                // kann — das passiert genau bei falschem Code.
                throw WrongCodeException()
            }
            if (theirInfo.size != PEER_INFO_SIZE) throw IOException("Invalid peer info")
        }
    }

    private fun peerInfo(publicKey: ByteArray): ByteArray {
        require(publicKey.size <= PEER_INFO_SIZE - 1) { "Public key too large" }
        return ByteArray(PEER_INFO_SIZE).also {
            it[0] = PEER_INFO_RSA_PUB_KEY
            publicKey.copyInto(it, 1)
        }
    }

    private fun writePacket(out: DataOutputStream, type: Byte, payload: ByteArray) {
        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
            .put(HEADER_VERSION).put(type).putInt(payload.size).array()
        out.write(header)
        out.write(payload)
        out.flush()
    }

    private fun readPacket(input: DataInputStream, expectedType: Byte): ByteArray {
        val header = ByteArray(HEADER_SIZE).also { input.readFully(it) }
        val buf = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        val version = buf.get()
        val type = buf.get()
        val size = buf.getInt()
        if (version != HEADER_VERSION) throw IOException("Unsupported pairing header version $version")
        if (type != expectedType) throw IOException("Unexpected pairing packet type $type")
        if (size <= 0 || size > MAX_PAYLOAD_SIZE) throw IOException("Invalid pairing payload size $size")
        return ByteArray(size).also { input.readFully(it) }
    }

    /**
     * AES-128-GCM wie `Aes128Gcm` in `pairing_auth.cpp`: Key per HKDF, Nonce = 64-bit
     * Sequenzzähler little-endian in den ersten 8 Byte, getrennte Zähler je Richtung.
     */
    private class PairingCipher(keyMaterial: ByteArray) {
        private val key = SecretKeySpec(
            ByteArray(16).also {
                HKDFBytesGenerator(SHA256Digest()).apply {
                    init(HKDFParameters(keyMaterial, null, HKDF_INFO))
                }.generateBytes(it, 0, it.size)
            },
            "AES"
        )
        private var encSeq = 0L
        private var decSeq = 0L

        fun encrypt(plain: ByteArray): ByteArray = run(Cipher.ENCRYPT_MODE, encSeq++, plain)
        fun decrypt(sealed: ByteArray): ByteArray = run(Cipher.DECRYPT_MODE, decSeq++, sealed)

        private fun run(mode: Int, seq: Long, data: ByteArray): ByteArray {
            val nonce = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putLong(seq).array()
            return Cipher.getInstance("AES/GCM/NoPadding").run {
                init(mode, key, GCMParameterSpec(128, nonce))
                doFinal(data)
            }
        }
    }

    companion object {
        private const val IO_TIMEOUT_MS = 15_000

        private const val HEADER_VERSION: Byte = 1
        private const val HEADER_SIZE = 6
        private const val TYPE_SPAKE2_MSG: Byte = 0
        private const val TYPE_PEER_INFO: Byte = 1

        private const val PEER_INFO_SIZE = 8192
        private const val PEER_INFO_RSA_PUB_KEY: Byte = 0
        private const val MAX_PAYLOAD_SIZE = PEER_INFO_SIZE * 2

        // Label inkl. Nullterminator (AOSP: sizeof("adb-label")).
        private const val EXPORTED_KEY_LABEL = "adb-label\u0000"
        private const val EXPORTED_KEY_SIZE = 64

        private val HKDF_INFO = "adb pairing_auth aes-128-gcm key".toByteArray(Charsets.US_ASCII)
    }
}
