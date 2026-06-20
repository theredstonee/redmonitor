package com.tamerin.sysmonitor.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.compose.runtime.Immutable

@Immutable
data class BatteryConsumer(
    val packageName: String,
    val displayName: String,
    val mAh: Double,
    val sharePercent: Float,
    val source: Source,
    val isSystem: Boolean
) {
    enum class Source { DUMPSYS, ESTIMATED }
}

/**
 * Reads per-app battery consumption with two paths:
 *
 *  1. **DUMPSYS** (preferred — needs Shizuku ready):
 *     parses the "Estimated power use (mAh)" section of `dumpsys batterystats`,
 *     which is Android's own accounting based on CPU, radio, sensors, GPS, etc.
 *     This is the same data the Settings → Battery → "Battery usage" screen uses.
 *
 *  2. **ESTIMATED** (fallback, no Shizuku):
 *     uses UsageStatsManager.queryUsageStats over the last 24h, takes foreground
 *     time per app and scales to a rough mAh estimate. Far less accurate but
 *     gives the user *something*. Requires PACKAGE_USAGE_STATS permission.
 */
object BatteryConsumerReader {

    suspend fun read(context: Context, limit: Int = 15): List<BatteryConsumer> {
        val viaDumpsys = if (ShizukuHelper.state(context) == ShizukuHelper.State.Ready) {
            readDumpsys(context)
        } else emptyList()
        if (viaDumpsys.isNotEmpty()) return viaDumpsys.take(limit)
        return readEstimated(context).take(limit)
    }

    // ===== Dumpsys path =====

    private fun readDumpsys(context: Context): List<BatteryConsumer> {
        val res = ShizukuHelper.runCommand(context, "dumpsys", "batterystats", "--charged")
        if (!res.ok) return emptyList()
        return parseBatterystats(context, res.stdout)
    }

    /**
     * Parse the "Estimated power use (mAh)" block. Format varies a bit by API
     * level but consistently has lines like:
     *     Uid u0a234: 12.3 ( cpu=5.5 wake=2.1 wifi=4.7 )
     *     Uid 1000:   8.1 ( cpu=8.1 )
     * Capacity / Computed drain header gives us the total to compute shares.
     */
    private fun parseBatterystats(context: Context, output: String): List<BatteryConsumer> {
        val pm = context.packageManager
        val lines = output.lineSequence()
        var inSection = false
        var totalMah = 0.0
        val rows = mutableListOf<Triple<Int, Double, Boolean>>()

        val uidRegex = Regex("""^\s*Uid\s+(u\d+a\d+|\d+):\s*([\d.]+)""")
        val capacityRegex = Regex("""Computed drain:\s*([\d.]+)""")

        for (raw in lines) {
            val line = raw.trimEnd()
            if (line.contains("Estimated power use (mAh)")) {
                inSection = true
                continue
            }
            if (!inSection) continue
            // Empty line ends the section
            if (line.isBlank()) {
                if (rows.isNotEmpty()) break else continue
            }
            capacityRegex.find(line)?.let {
                totalMah = it.groupValues[1].toDoubleOrNull() ?: totalMah
            }
            val m = uidRegex.find(line) ?: continue
            val uidStr = m.groupValues[1]
            val mAh = m.groupValues[2].toDoubleOrNull() ?: continue
            val uid = parseUid(uidStr) ?: continue
            val isSystem = uid < Process.FIRST_APPLICATION_UID
            rows += Triple(uid, mAh, isSystem)
        }

        if (rows.isEmpty()) return emptyList()
        val total = if (totalMah > 0) totalMah else rows.sumOf { it.second }
        return rows
            .sortedByDescending { it.second }
            .mapNotNull { (uid, mAh, isSystem) ->
                val pkgs = pm.getPackagesForUid(uid) ?: arrayOf("uid $uid")
                val pkg = pkgs.firstOrNull() ?: return@mapNotNull null
                val label = runCatching {
                    pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
                }.getOrDefault(pkg)
                BatteryConsumer(
                    packageName = pkg,
                    displayName = label,
                    mAh = mAh,
                    sharePercent = if (total > 0) (mAh / total * 100f).toFloat() else 0f,
                    source = BatteryConsumer.Source.DUMPSYS,
                    isSystem = isSystem
                )
            }
    }

    /** "u0a234" → 10234 (Android UID convention: 10000 + 234 for user 0). */
    private fun parseUid(uidStr: String): Int? {
        if (uidStr.startsWith("u") && uidStr.contains("a")) {
            val parts = uidStr.removePrefix("u").split("a")
            if (parts.size != 2) return null
            val user = parts[0].toIntOrNull() ?: return null
            val appId = parts[1].toIntOrNull() ?: return null
            return user * 100_000 + 10_000 + appId
        }
        return uidStr.toIntOrNull()
    }

    // ===== Estimated path =====

    private fun readEstimated(context: Context): List<BatteryConsumer> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val now = System.currentTimeMillis()
        val dayAgo = now - 24 * 3600_000L
        val stats = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayAgo, now)
        }.getOrNull().orEmpty()
        if (stats.isEmpty()) return emptyList()

        val pm = context.packageManager
        // Rough heuristic: assume 50 mAh/h of foreground time for typical apps.
        // It's hand-wavey but produces a reasonable ranking.
        val mAhPerHourForeground = 50.0
        val rows = stats
            .filter { it.totalTimeInForeground > 60_000 }
            .map { s ->
                val hours = s.totalTimeInForeground / 3_600_000.0
                Triple(s.packageName, hours * mAhPerHourForeground, hours)
            }
        val total = rows.sumOf { it.second }
        return rows
            .sortedByDescending { it.second }
            .mapNotNull { (pkg, mAh, _) ->
                val label = runCatching {
                    pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
                }.getOrDefault(pkg)
                val isSystem = runCatching {
                    (pm.getApplicationInfo(pkg, 0).flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                }.getOrDefault(false)
                BatteryConsumer(
                    packageName = pkg,
                    displayName = label,
                    mAh = mAh,
                    sharePercent = if (total > 0) (mAh / total * 100f).toFloat() else 0f,
                    source = BatteryConsumer.Source.ESTIMATED,
                    isSystem = isSystem
                )
            }
    }
}
