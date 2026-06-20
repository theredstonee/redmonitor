package com.tamerin.sysmonitor.data

import android.content.Context
import java.io.File

@androidx.compose.runtime.Immutable
data class ThermalZone(
    val index: Int,
    val type: String,
    val tempCelsius: Float
)

object ThermalReader {

    /**
     * Reads /sys/class/thermal directly. If the kernel-level path is locked
     * (common on Android 10+ stock + MIUI/OneUI) and a context is supplied,
     * falls back to Shizuku shell access — which sees the same files as the
     * shell user, which usually CAN read thermal sensors.
     */
    fun read(context: Context? = null): List<ThermalZone> {
        val direct = readDirect()
        if (direct.isNotEmpty()) return direct
        if (context == null) return direct
        if (ShizukuHelper.state(context) != ShizukuHelper.State.Ready) return direct
        return readViaShizuku(context)
    }

    private fun readDirect(): List<ThermalZone> {
        val base = File("/sys/class/thermal")
        if (!base.exists()) return emptyList()
        val zones = base.listFiles { f -> f.name.startsWith("thermal_zone") } ?: return emptyList()
        return zones.mapNotNull { dir ->
            val idx = dir.name.removePrefix("thermal_zone").toIntOrNull() ?: return@mapNotNull null
            val type = runCatching { File(dir, "type").readText().trim() }.getOrDefault("?")
            val tempRaw = runCatching { File(dir, "temp").readText().trim().toLong() }.getOrNull()
            val tempC = tempRaw?.let { if (it > 1000) it / 1000f else it.toFloat() } ?: Float.NaN
            ThermalZone(idx, type, tempC)
        }.sortedBy { it.index }
    }

    /**
     * One Shizuku shell call enumerates every thermal zone + reads its type and
     * temp atomically. Way cheaper than spawning N shell processes.
     */
    private fun readViaShizuku(context: Context): List<ThermalZone> {
        val script = "for d in /sys/class/thermal/thermal_zone*; do " +
            "n=\$(basename \$d); idx=\${n#thermal_zone}; " +
            "t=\$(cat \$d/type 2>/dev/null); " +
            "v=\$(cat \$d/temp 2>/dev/null); " +
            "echo \"\$idx|\$t|\$v\"; done"
        val res = ShizukuHelper.runShell(context, script)
        if (!res.ok || res.stdout.isBlank()) return emptyList()
        return res.stdout.lines().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 3) return@mapNotNull null
            val idx = parts[0].toIntOrNull() ?: return@mapNotNull null
            val type = parts[1].ifBlank { "?" }
            val tempRaw = parts[2].toLongOrNull() ?: return@mapNotNull null
            val tempC = if (tempRaw > 1000) tempRaw / 1000f else tempRaw.toFloat()
            ThermalZone(idx, type, tempC)
        }.sortedBy { it.index }
    }

    /**
     * Returns the hottest CPU/SoC zone. Filters out battery/skin sensors so the
     * stress test reflects actual silicon temperature, not the case.
     */
    fun hottestCpuZone(zones: List<ThermalZone>): ThermalZone? {
        val hints = listOf("cpu", "soc", "tsens", "apc", "kgsl", "gpu", "big", "little", "silver", "gold")
        return zones
            .filter { !it.tempCelsius.isNaN() && it.tempCelsius in 5f..150f }
            .filter { z -> hints.any { h -> z.type.contains(h, ignoreCase = true) } }
            .maxByOrNull { it.tempCelsius }
            ?: zones.filter { !it.tempCelsius.isNaN() && it.tempCelsius in 5f..150f }
                .maxByOrNull { it.tempCelsius }
    }
}
