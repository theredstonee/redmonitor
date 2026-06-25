package com.tamerin.sysmonitor.cloud

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * First-Launch-Restore-Flow:
 *   1. Beim ersten App-Start (nach Install) Server pingen mit deviceId
 *   2. Server liefert opaken verschlüsselten Blob falls vorhanden
 *   3. Wir versuchen mit der lokalen Hardware-FP zu entschlüsseln
 *   4. Bei Erfolg: Restore-Dialog mit createdAt-Datum anzeigen
 *   5. User Ja → BackupSerializer.apply → Daten zurück
 *   6. User Nein → Flag setzen, beim nächsten Launch nicht mehr fragen
 */
object RestoreChecker {

    data class Available(
        val sourceVersion: String,
        val createdAt: Long,
        val plaintextJson: ByteArray
    )

    /** Returns Available oder null wenn kein passendes Backup. */
    suspend fun probe(context: Context): Available? = withContext(Dispatchers.IO) {
        if (CloudPrefs.restoreChecked(context)) return@withContext null
        if (!CloudPrefs.isEnabled(context)) return@withContext null

        val resp = runCatching { BackendClient.fetchBackup(context) }.getOrNull()
        if (resp == null) {
            // kein Backup oder Netz-Fehler — kein erneutes Probing nötig, nur dieses Mal
            CloudPrefs.setRestoreChecked(context, true)
            return@withContext null
        }
        val fp = DeviceIdProvider.hardwareFingerprint(context)
        val plaintext = runCatching { BackupCrypto.decrypt(resp.encryptedBlob, fp) }.getOrNull()
        if (plaintext == null) {
            // Hardware passt nicht (z.B. ANDROID_ID nach Factory-Reset anders) — kein Restore möglich
            CloudPrefs.setRestoreChecked(context, true)
            return@withContext null
        }
        // Parse Metadaten ohne komplett zu applien
        val root = org.json.JSONObject(String(plaintext, Charsets.UTF_8))
        Available(
            sourceVersion = root.optString("appVersion"),
            createdAt = root.optLong("createdAt"),
            plaintextJson = plaintext
        )
    }

    suspend fun applyAndMark(context: Context, a: Available): BackupSerializer.RestoreSummary {
        val summary = BackupSerializer.apply(context, a.plaintextJson)
        CloudPrefs.setRestoreChecked(context, true)
        return summary
    }

    fun decline(context: Context, a: Available) {
        CloudPrefs.setRestoreChecked(context, true)
        CloudPrefs.setRestoreDeclinedVersion(context, a.sourceVersion)
    }
}
