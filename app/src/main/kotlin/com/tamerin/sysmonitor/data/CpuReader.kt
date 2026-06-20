package com.tamerin.sysmonitor.data

import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import java.io.File

@Immutable
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
    val source: String
)

private data class CoreTimes(val idle: Long, val total: Long)

/** Per-caller state. Multiple readers (HUD, Stress, Bench) need independent state so they
 *  don't stomp on each other's "last sample" timestamps. */
private class SamplerState {
    var lastTotal: CoreTimes? = null
    var lastPerCore: List<CoreTimes> = emptyList()
    var lastProcCpuMs: Long = -1L
    var lastWallClockMs: Long = -1L
    var systemStatBroken: Boolean? = null
}

object CpuReader {
    private val states = mutableMapOf<String, SamplerState>()

    private val coreCount: Int by lazy {
        runCatching {
            File("/sys/devices/system/cpu").listFiles { f ->
                f.name.matches(Regex("cpu[0-9]+"))
            }?.size ?: Runtime.getRuntime().availableProcessors()
        }.getOrDefault(Runtime.getRuntime().availableProcessors())
    }

    /** Backward-compatible read without context; uses "default" bucket. */
    fun read(): CpuSnapshot = read(null, "default")

    /** Uses Shizuku when available; bucket defaults to "default". */
    fun read(context: Context): CpuSnapshot = read(context, "default")

    /** Independent state per [samplerKey]. Each consumer (HUD, Stress, Bench, CPU-Screen)
     *  should pick its own so they don't trample each other. */
    fun read(context: Context?, samplerKey: String): CpuSnapshot {
        val state = synchronized(states) { states.getOrPut(samplerKey) { SamplerState() } }
        if (context != null && ShizukuHelper.state(context) == ShizukuHelper.State.Ready) {
            val (lines, freqs) = readBatchViaShizuku(context)
            if (lines.isNotEmpty()) {
                return readImpl(state, lines, freqs, "shizuku")
            }
        }
        return readImpl(state, directReadProcStat(), directReadFreqs(), "direct")
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

    private fun readImpl(
        state: SamplerState,
        procStatLines: List<String>,
        directFreqs: List<Long>,
        source: String
    ): CpuSnapshot {
        val procStat = procStatLines

        val total = procStat.firstOrNull { it.startsWith("cpu ") }?.let(::parseCpuLine)
        val perCore = procStat.filter { it.matches(Regex("^cpu\\d+\\s.*")) }
            .mapNotNull(::parseCpuLine)

        var dataSource = source
        var totalPct = 0f

        if (total != null && total.total > 0) {
            val prev = state.lastTotal
            state.lastTotal = total
            if (prev != null) {
                val sysPct = percentDelta(prev, total)
                val delta = total.total - prev.total
                if (delta > 0) {
                    totalPct = sysPct
                    state.systemStatBroken = false
                } else {
                    state.systemStatBroken = true
                }
            }
        } else {
            state.systemStatBroken = true
        }

        val perCorePct = perCore.mapIndexed { idx, current ->
            val prev = state.lastPerCore.getOrNull(idx)
            prev?.let { percentDelta(it, current) } ?: 0f
        }
        state.lastPerCore = perCore

        if (state.systemStatBroken == true) {
            dataSource = "process"
            totalPct = readProcessCpuPercent(state)
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

    private fun readProcessCpuPercent(state: SamplerState): Float {
        val procCpuMs = Process.getElapsedCpuTime()
        val wallMs = SystemClock.elapsedRealtime()
        val prevCpu = state.lastProcCpuMs
        val prevWall = state.lastWallClockMs
        state.lastProcCpuMs = procCpuMs
        state.lastWallClockMs = wallMs
        if (prevCpu < 0 || prevWall < 0) return 0f
        val dCpu = (procCpuMs - prevCpu).coerceAtLeast(0)
        val dWall = (wallMs - prevWall).coerceAtLeast(1)
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
