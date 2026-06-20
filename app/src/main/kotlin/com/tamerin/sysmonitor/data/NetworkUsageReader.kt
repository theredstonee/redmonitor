package com.tamerin.sysmonitor.data

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class AppNetworkUsage(
    val uid: Int,
    val packageName: String,
    val displayName: String,
    val isSystem: Boolean,
    val wifiBytes: Long,
    val mobileBytes: Long
) {
    val totalBytes: Long get() = wifiBytes + mobileBytes
}

/**
 * Pro-App Netzwerk-Verbrauch via NetworkStatsManager (Android 6+).
 *
 * Voraussetzung: PACKAGE_USAGE_STATS (User-Permission). Ohne diese
 * Permission liefert NetworkStatsManager nur Daten für den eigenen UID.
 *
 * Wir queryen Wi-Fi und Mobile separat seit [sinceMs] (default: 24 h).
 */
object NetworkUsageReader {

    suspend fun read(
        context: Context,
        sinceMs: Long = 24L * 3600_000L
    ): List<AppNetworkUsage> = withContext(Dispatchers.IO) {
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return@withContext emptyList()
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val from = now - sinceMs

        // Bucket WiFi traffic per UID
        val wifiByUid = mutableMapOf<Int, Long>()
        runCatching {
            nsm.querySummary(ConnectivityManager.TYPE_WIFI, null, from, now).use { stats ->
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    wifiByUid.merge(bucket.uid, bucket.rxBytes + bucket.txBytes) { a, b -> a + b }
                }
            }
        }
        val mobileByUid = mutableMapOf<Int, Long>()
        runCatching {
            nsm.querySummary(ConnectivityManager.TYPE_MOBILE, null, from, now).use { stats ->
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    mobileByUid.merge(bucket.uid, bucket.rxBytes + bucket.txBytes) { a, b -> a + b }
                }
            }
        }

        // Combine into per-UID entries, then resolve to package names
        val allUids = (wifiByUid.keys + mobileByUid.keys).distinct()
        val rows = allUids.mapNotNull { uid ->
            val wifi = wifiByUid[uid] ?: 0L
            val mobile = mobileByUid[uid] ?: 0L
            if (wifi == 0L && mobile == 0L) return@mapNotNull null
            val pkgs = runCatching { pm.getPackagesForUid(uid) }.getOrNull()
            val pkg = pkgs?.firstOrNull() ?: "uid:$uid"
            val (label, isSystem) = runCatching {
                val ai = pm.getApplicationInfo(pkg, 0)
                val name = ai.loadLabel(pm).toString()
                val system = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                name to system
            }.getOrDefault(pkg to true)
            AppNetworkUsage(uid, pkg, label, isSystem, wifi, mobile)
        }
        rows.sortedByDescending { it.totalBytes }
    }
}
