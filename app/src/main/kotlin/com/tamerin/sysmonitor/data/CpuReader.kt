package com.tamerin.sysmonitor.data

import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import java.io.File

data class CpuSnapshot(
    val totalPercent: Float,
    val perCorePercent: List<Float>,
    val coreFrequenciesKHz: List<Long>,
    val coreMinFreqKHz: List<Long>,
    val coreMaxFreqKHz: List<Long>,
    val governor: String,
    val coreCount: Int,
    val abi: String,
    val supportedAbis: List<String>,
    val hardware: String,
    /** Datenquelle für totalPercent: "system" (/proc/stat) oder "process" (own process) */
    val source: String
)

private data class CoreTimes(val idle: Long, val total: Long)

object CpuReader {
    private var lastTotal: CoreTimes? = null
    private var lastPerCore: List<CoreTimes> = emptyList()

    // Process-level fallback state
    private var lastProcCpuMs: Long = -1L
    private var lastWallClockMs: Long = -1L
    private var systemStatBroken: Boolean? = null

    private val coreCount: Int by lazy {
        runCatching {
            File("/sys/devices/system/cpu").listFiles { f ->
                f.name.matches(Regex("cpu[0-9]+"))
            }?.size ?: Runtime.getRuntime().availableProcessors()
        }.getOrDefault(Runtime.getRuntime().availableProcessors())
    }

    /** Backward-compatible read without context — falls back to direct file reads only. */
    fun read(): CpuSnapshot = readImpl(procStatLines = directReadProcStat(),
        directFreqs = directReadFreqs(), source = "direct")

    /** Preferred: uses Shizuku for /proc/stat + per-core freqs when available. */
    fun read(context: Context): CpuSnapshot {
        if (ShizukuHelper.state(context) == ShizukuHelper.State.Ready) {
            val (lines, freqs) = readBatchViaShizuku(context)
            if (lines.isNotEmpty()) {
                return readImpl(procStatLines = lines, directFreqs = freqs, source = "shizuku")
            }
        }
        return readImpl(procStatLines = directReadProcStat(),
            directFreqs = directReadFreqs(), source = "direct")
    }

    private fun directReadProcStat(): List<String> =
        runCatching { File("/proc/stat").readLines() }.getOrDefault(emptyList())

    private fun directReadFreqs(): List<Long> =
        (0 until coreCount).map { idx ->
            runCatching {
                File("/sys/devices/system/cpu/cpu$idx/cpufreq/scaling_cur_freq")
                    .readText().trim().toLong()
            }.getOrDefault(0L)
        }

    private fun readBatchViaShizuku(context: Context): Pair<List<String>, List<Long>> {
        // Single shell invocation gets both /proc/stat and all freqs at once — much faster than per-core spawn
        val cmd = "cat /proc/stat; echo '---FREQS---'; " +
            "for i in /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq; do " +
            "cat \$i 2>/dev/null || echo 0; done"
        val res = ShizukuHelper.runShell(context, cmd)
        if (!res.ok) return Pair(emptyList(), emptyList())
        val parts = res.stdout.split("---FREQS---")
        val statLines = parts.getOrNull(0)?.lines() ?: emptyList()
        val freqs = parts.getOrNull(1)?.lines()
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filter { it > 0 } ?: emptyList()
        return Pair(statLines, freqs)
    }

    private fun readImpl(procStatLines: List<String>, directFreqs: List<Long>, source: String): CpuSnapshot {
        val procStat = procStatLines

        val total = procStat.firstOrNull { it.startsWith("cpu ") }?.let(::parseCpuLine)
        val perCore = procStat.filter { it.matches(Regex("^cpu\\d+\\s.*")) }
            .mapNotNull(::parseCpuLine)

        var dataSource = source
        var totalPct = 0f

        if (total != null && total.total > 0) {
            val prev = lastTotal
            lastTotal = total
            if (prev != null) {
                val sysPct = percentDelta(prev, total)
                val delta = total.total - prev.total
                if (delta > 0) {
                    totalPct = sysPct
                    systemStatBroken = false
                } else {
                    systemStatBroken = true
                }
            }
        } else {
            systemStatBroken = true
        }

        val perCorePct = perCore.mapIndexed { idx, current ->
            val prev = lastPerCore.getOrNull(idx)
            prev?.let { percentDelta(it, current) } ?: 0f
        }
        lastPerCore = perCore

        if (systemStatBroken == true) {
            dataSource = "process"
            totalPct = readProcessCpuPercent()
        }

        val freqs = if (directFreqs.isNotEmpty()) directFreqs else readFreqs("scaling_cur_freq")
        val minFreqs = readFreqs("cpuinfo_min_freq")
        val maxFreqs = readFreqs("cpuinfo_max_freq")
        val governor = runCatching {
            File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor").readText().trim()
        }.getOrDefault("?")

        return CpuSnapshot(
            totalPercent = totalPct,
            perCorePercent = perCorePct,
            coreFrequenciesKHz = freqs,
            coreMinFreqKHz = minFreqs,
            coreMaxFreqKHz = maxFreqs,
            governor = governor,
            coreCount = coreCount,
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "?",
            supportedAbis = Build.SUPPORTED_ABIS?.toList() ?: emptyList(),
            hardware = Build.HARDWARE ?: "?",
            source = dataSource
        )
    }

    private fun readProcessCpuPercent(): Float {
        val procCpuMs = Process.getElapsedCpuTime()
        val wallMs = SystemClock.elapsedRealtime()
        val prevCpu = lastProcCpuMs
        val prevWall = lastWallClockMs
        lastProcCpuMs = procCpuMs
        lastWallClockMs = wallMs
        if (prevCpu < 0 || prevWall < 0) return 0f
        val dCpu = (procCpuMs - prevCpu).coerceAtLeast(0)
        val dWall = (wallMs - prevWall).coerceAtLeast(1)
        // Process CPU time is summed across all cores → max = dWall * coreCount
        val maxCpuMs = dWall * coreCount
        return (dCpu * 100f / maxCpuMs).coerceIn(0f, 100f)
    }

    private fun readFreqs(filename: String): List<Long> {
        return (0 until coreCount).map { idx ->
            runCatching {
                File("/sys/devices/system/cpu/cpu$idx/cpufreq/$filename")
                    .readText().trim().toLong()
            }.getOrDefault(0L)
        }
    }

    private fun parseCpuLine(line: String): CoreTimes? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 5) return null
        val numbers = parts.drop(1).mapNotNull { it.toLongOrNull() }
        if (numbers.size < 4) return null
        val idle = numbers[3] + (numbers.getOrNull(4) ?: 0L)
        val total = numbers.sum()
        return CoreTimes(idle = idle, total = total)
    }

    private fun percentDelta(prev: CoreTimes, current: CoreTimes): Float {
        val totalDiff = current.total - prev.total
        val idleDiff = current.idle - prev.idle
        if (totalDiff <= 0) return 0f
        val busy = (totalDiff - idleDiff).coerceAtLeast(0)
        return (busy * 100f / totalDiff).coerceIn(0f, 100f)
    }
}
