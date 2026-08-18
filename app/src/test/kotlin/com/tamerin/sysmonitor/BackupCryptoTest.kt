package com.tamerin.sysmonitor

import com.tamerin.sysmonitor.cloud.BackupCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Backup-Verschluesselung ist der Teil, bei dem ein stiller Fehler am teuersten
 * waere: geht der Key kaputt, sind alle Cloud-Backups unlesbar — und das merkt man
 * erst beim Restore. AES-GCM und HKDF gibt es auch auf der JVM, das laeuft also
 * ohne Geraet.
 */
class BackupCryptoTest {

    private val fingerprint = "Nothing-A059-abc123-arm64"
    private val plaintext = """{"prefs":{"haptic":true},"samples":42}""".toByteArray()

    @Test
    fun `Roundtrip auf gleicher Hardware liefert das Original`() {
        val blob = BackupCrypto.encrypt(plaintext, fingerprint)
        val back = BackupCrypto.decrypt(blob, fingerprint)
        assertArrayEquals(plaintext, back)
    }

    @Test
    fun `Blob traegt das RMB1-Magic`() {
        val blob = BackupCrypto.encrypt(plaintext, fingerprint)
        assertEquals('R'.code.toByte(), blob[0])
        assertEquals('M'.code.toByte(), blob[1])
        assertEquals('B'.code.toByte(), blob[2])
        assertEquals('1'.code.toByte(), blob[3])
    }

    @Test
    fun `andere Hardware kann nicht entschluesseln`() {
        // Das ist beabsichtigtes Verhalten, kein Bug: Geraetewechsel = Restore faellt aus
        val blob = BackupCrypto.encrypt(plaintext, fingerprint)
        assertThrows(Exception::class.java) {
            BackupCrypto.decrypt(blob, "Pixel-9-zzz999-arm64")
        }
    }

    @Test
    fun `manipulierter Ciphertext fliegt auf`() {
        val blob = BackupCrypto.encrypt(plaintext, fingerprint)
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()
        assertThrows(Exception::class.java) {
            BackupCrypto.decrypt(blob, fingerprint)
        }
    }

    @Test
    fun `zwei Verschluesselungen erzeugen verschiedene Blobs`() {
        // Zufaellige Nonce — sonst waere GCM angreifbar
        val a = BackupCrypto.encrypt(plaintext, fingerprint)
        val b = BackupCrypto.encrypt(plaintext, fingerprint)
        assertNotEquals(a.toList(), b.toList())
        // Trotzdem beide entschluesselbar
        assertArrayEquals(plaintext, BackupCrypto.decrypt(a, fingerprint))
        assertArrayEquals(plaintext, BackupCrypto.decrypt(b, fingerprint))
    }

    @Test
    fun `Key ist deterministisch pro Fingerprint`() {
        val k1 = BackupCrypto.deriveKey(fingerprint)
        val k2 = BackupCrypto.deriveKey(fingerprint)
        val k3 = BackupCrypto.deriveKey("anderes-geraet")
        assertArrayEquals(k1, k2)
        assertEquals(32, k1.size)
        assertFalse(k1.contentEquals(k3))
    }

    @Test
    fun `zu kurzer Blob wird abgewiesen`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(byteArrayOf(1, 2, 3), fingerprint)
        }
    }

    @Test
    fun `falsches Magic wird abgewiesen`() {
        val blob = BackupCrypto.encrypt(plaintext, fingerprint)
        blob[0] = 'X'.code.toByte()
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(blob, fingerprint)
        }
    }

    @Test
    fun `leerer Inhalt funktioniert`() {
        val blob = BackupCrypto.encrypt(ByteArray(0), fingerprint)
        assertTrue(BackupCrypto.decrypt(blob, fingerprint).isEmpty())
    }
}
