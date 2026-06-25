package com.tamerin.sysmonitor.cloud

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

/**
 * Berechnet die persistente Device-ID aus Hardware-Eigenschaften + Server-Salt.
 *
 * Stabil über:
 *   - App-Reinstall ✓
 *   - OS-Update ✓ (ANDROID_ID bleibt; HARDWARE/BOARD/MANUFACTURER auch)
 *
 * Wechselt bei:
 *   - Factory-Reset (ANDROID_ID wechselt)
 *   - Hardware-Wechsel
 *
 * Returns: 64-stelliger Hex-String (SHA-256).
 */
object DeviceIdProvider {

    @Volatile private var cached: String? = null
    @Volatile private var cachedFingerprint: String? = null

    fun deviceId(context: Context): String {
        cached?.let { return it }
        val fp = hardwareFingerprint(context)
        val composite = fp + "|" + CloudConfig.DEVICE_ID_SALT
        val hash = sha256Hex(composite)
        cached = hash
        return hash
    }

    /**
     * Reiner Hardware-Fingerprint (OHNE Server-Salt). Wird intern für
     * die E2E-Key-Derivation gebraucht — diese soll NICHT vom Server-Salt
     * abhängen, damit der Server theoretisch nichts entschlüsseln kann
     * selbst wenn er das Salt kennt.
     */
    fun hardwareFingerprint(context: Context): String {
        cachedFingerprint?.let { return it }
        @SuppressLint("HardwareIds")
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        }.getOrDefault("")
        // ANDROID_ID + relativ stabile Build-Properties (Bootloader/SoC/Mainboard)
        val fp = listOf(
            androidId,
            Build.MANUFACTURER.orEmpty(),
            Build.MODEL.orEmpty(),
            Build.BOARD.orEmpty(),
            Build.HARDWARE.orEmpty(),
            Build.BOOTLOADER.orEmpty()
        ).joinToString("|")
        cachedFingerprint = fp
        return fp
    }

    private fun sha256Hex(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
