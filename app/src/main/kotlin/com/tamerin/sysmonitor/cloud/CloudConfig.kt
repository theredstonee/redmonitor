package com.tamerin.sysmonitor.cloud

/**
 * Konfiguration für RedMonitor-Backend.
 *
 * Der DEVICE_ID_SALT ist absichtlich im APK eingebakt — er erlaubt es,
 * Hardware-Fingerprints in eine opake Device-ID zu hashen. Wer das APK
 * reverse-engineert kann zwar IDs für beliebige Geräte vorausberechnen,
 * aber der Server speichert nur E2E-verschlüsselte Blobs, die ohne den
 * Hardware-Fingerprint (= ohne physisches Device) nicht entschlüsselbar
 * sind. Das Salt ist also "Security by obscurity"-Protection, kein
 * Geheim-Anker.
 */
object CloudConfig {
    const val BACKEND_BASE = "https://redmonitor.redst.de"
    const val DEVICE_ID_SALT = "860b9ad070ddef8ec8ae7b4aa413d2a2"
    const val BACKUP_BLOB_VERSION = 1
}
