package com.tamerin.sysmonitor.data

import android.content.Context
import java.io.File

/**
 * Reads live GPU utilization in % (0..100). Tries several vendor-specific
 * sysfs paths because there is no public Android API for this.
 *
 *  - Adreno (Qualcomm)         : /sys/class/kgsl/kgsl-3d0/devfreq/gpu_load → "42"
 *  - Adreno (older)            : /sys/class/kgsl/kgsl-3d0/gpubusy        → "32 100" (busy/total)
 *  - Mali (Tensor / Pixel)     : /sys/devices/platform/*mali*/utilization → "37"
 *  - Mali (Exynos)             : /sys/devices/platform/*mali*/utilization
 *  - Tegra (Nvidia)            : /sys/devices/platform/host1x/57000000.gpu/load → "456" (per-mille → /10)
 *
 * Most modern devices restrict these for normal apps. If direct read returns
 * null and Shizuku is ready, we `cat` via shell which sees them.
 */
object GpuUsageReader {

    // Try these in order. First number-or-percent reading wins.
    private val DIRECT_PERCENT_PATHS = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",       // Adreno A6xx/A7xx (most reliable on modern SDM/SM)
        "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_busy_percentage",
        "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",          // older Adreno
        "/sys/class/devfreq/gpufreq/gpu_load",                // some MTK
        "/sys/kernel/gpu/gpu_busy",                           // generic kernel
        "/sys/kernel/ged/hal/gpu_utilization"                 // MediaTek GED
    )
    private val ADRENO_RATIO = "/sys/class/kgsl/kgsl-3d0/gpubusy"

    /** Returns 0..100, or null if no source readable. */
    fun read(context: Context): Float? {
        // Direct read first
        readDirect()?.let { return it }
        // Shizuku fallback
        if (ShizukuHelper.state(context) == ShizukuHelper.State.Ready) {
            return readViaShizuku(context)
        }
        return null
    }

    private fun readDirect(): Float? {
        DIRECT_PERCENT_PATHS.forEach { path ->
            parsePercent(readFile(path))?.let { return it }
        }
        parseRatio(readFile(ADRENO_RATIO))?.let { return it }
        findMaliUtilizationFile()?.let {
            parsePercent(readFile(it.absolutePath))?.let { v -> return v }
        }
        // Tegra (Nvidia Shield etc.) per-mille
        readFile("/sys/devices/platform/host1x/57000000.gpu/load")?.let {
            it.trim().toIntOrNull()?.let { perMille ->
                if (perMille in 0..1000) return perMille / 10f
            }
        }
        return null
    }

    private fun readViaShizuku(context: Context): Float? {
        // Try percent-style paths first, then ratio, then globbed Mali/KGSL.
        // `cat` of a non-existent file errors silently with 2> /dev/null.
        val percentPaths = DIRECT_PERCENT_PATHS.joinToString(" ")
        val script = """
            for p in $percentPaths; do
              v=${'$'}(cat "${'$'}p" 2>/dev/null)
              [ -n "${'$'}v" ] && echo "P|${'$'}v" && exit 0
            done
            v=${'$'}(cat $ADRENO_RATIO 2>/dev/null)
            [ -n "${'$'}v" ] && echo "R|${'$'}v" && exit 0
            for p in /sys/devices/platform/*mali*/utilization /sys/devices/platform/*mali*/device/utilization /sys/devices/platform/*kgsl*/devfreq/*/gpu_load /sys/devices/platform/*kgsl*/gpu_busy_percentage; do
              v=${'$'}(cat "${'$'}p" 2>/dev/null)
              [ -n "${'$'}v" ] && echo "P|${'$'}v" && exit 0
            done
            echo "X|"
        """.trimIndent()
        val res = ShizukuHelper.runShell(context, script)
        if (!res.ok || res.stdout.isBlank()) return null
        val (kind, value) = res.stdout.trim().split("|", limit = 2).let {
            if (it.size != 2) return null
            it[0] to it[1].trim()
        }
        return when (kind) {
            "P" -> parsePercent(value)
            "R" -> parseRatio(value)
            else -> null
        }
    }

    private fun readFile(path: String): String? = runCatching {
        val f = File(path)
        if (!f.canRead()) return@runCatching null
        f.readText()
    }.getOrNull()

    private fun parsePercent(s: String?): Float? {
        if (s.isNullOrBlank()) return null
        val n = s.trim().toIntOrNull() ?: return null
        if (n in 0..100) return n.toFloat()
        if (n in 0..1000) return n / 10f
        return null
    }

    /** Format "busy total" — typical Adreno "gpubusy". */
    private fun parseRatio(s: String?): Float? {
        if (s.isNullOrBlank()) return null
        val parts = s.trim().split(Regex("\\s+"))
        if (parts.size < 2) return null
        val busy = parts[0].toLongOrNull() ?: return null
        val total = parts[1].toLongOrNull() ?: return null
        if (total <= 0) return null
        val pct = (busy.toDouble() / total * 100.0).toFloat()
        return pct.coerceIn(0f, 100f)
    }

    private fun findMaliUtilizationFile(): File? {
        val platform = File("/sys/devices/platform")
        if (!platform.isDirectory) return null
        val candidates = platform.listFiles { f -> f.isDirectory && f.name.contains("mali", ignoreCase = true) }
            ?: return null
        for (dir in candidates) {
            val direct = File(dir, "utilization")
            if (direct.exists() && direct.canRead()) return direct
            val nested = File(dir, "device/utilization")
            if (nested.exists() && nested.canRead()) return nested
        }
        return null
    }
}
