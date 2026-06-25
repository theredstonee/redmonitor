package com.tamerin.sysmonitor.cloud

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * E2E-Verschlüsselung für Backup-Blobs.
 *
 * Key-Derivation:
 *   ikm        = SHA-256(hardware-fingerprint)        (32 bytes)
 *   prk        = HKDF-Extract(salt=fixed, ikm)        (HKDF-SHA256)
 *   key        = HKDF-Expand(prk, info="rm-backup-v1", L=32)
 *
 * Cipher: AES-256-GCM, 12-byte zufällige Nonce, 128-bit Tag.
 *
 * Wire-Format des verschlüsselten Blobs:
 *   [magic 4B 'RMB1'] [nonce 12B] [ciphertext + tag N B]
 *
 * Bei Hardware-Wechsel: ikm ändert sich → key ändert sich → Entschlüsselung schlägt fehl.
 * Bei Reinstall auf gleicher Hardware: gleiches Ergebnis → kann eigenes vorheriges Backup lesen.
 */
object BackupCrypto {

    private const val GCM_TAG_BITS = 128
    private const val NONCE_LEN = 12
    private val MAGIC = byteArrayOf('R'.code.toByte(), 'M'.code.toByte(), 'B'.code.toByte(), '1'.code.toByte())
    private val HKDF_SALT = "redmonitor-hkdf-salt-v1".toByteArray(Charsets.UTF_8)
    private val INFO = "rm-backup-v1".toByteArray(Charsets.UTF_8)

    fun deriveKey(hardwareFingerprint: String): ByteArray {
        val ikm = java.security.MessageDigest.getInstance("SHA-256")
            .digest(hardwareFingerprint.toByteArray(Charsets.UTF_8))
        val prk = hkdfExtract(HKDF_SALT, ikm)
        return hkdfExpand(prk, INFO, 32)
    }

    fun encrypt(plaintext: ByteArray, hardwareFingerprint: String): ByteArray {
        val key = deriveKey(hardwareFingerprint)
        val nonce = ByteArray(NONCE_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        }
        val ct = cipher.doFinal(plaintext)
        return MAGIC + nonce + ct
    }

    /** Wirft GeneralSecurityException wenn Hardware nicht passt oder Blob korrupt. */
    fun decrypt(blob: ByteArray, hardwareFingerprint: String): ByteArray {
        require(blob.size > MAGIC.size + NONCE_LEN) { "Blob too small" }
        require(blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Wrong magic" }
        val nonce = blob.copyOfRange(MAGIC.size, MAGIC.size + NONCE_LEN)
        val ct = blob.copyOfRange(MAGIC.size + NONCE_LEN, blob.size)
        val key = deriveKey(hardwareFingerprint)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        }
        return cipher.doFinal(ct)
    }

    // RFC 5869 HKDF
    private fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray =
        hmacSha256(salt, ikm)

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val out = ByteArray(length)
        var t = ByteArray(0)
        var pos = 0
        var n = 1
        while (pos < length) {
            t = hmacSha256(prk, t + info + byteArrayOf(n.toByte()))
            val take = minOf(t.size, length - pos)
            System.arraycopy(t, 0, out, pos, take)
            pos += take
            n += 1
        }
        return out
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        return mac.doFinal(data)
    }
}
