package com.tamerin.sysmonitor.benchmark

import android.content.Context
import android.os.PowerManager
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt
import kotlin.random.Random

data class CpuSubScore(
    val name: String,
    val singleOpsPerSec: Double,
    val multiOpsPerSec: Double,
    val singleScore: Int,
    val multiScore: Int
)

data class CpuBenchmarkResult(
    val subScores: List<CpuSubScore>,
    val singleScore: Int,
    val multiScore: Int,
    val parallelism: Float,
    val durationMs: Long,
    val phaseDurationSec: Int
)

/**
 * 4-phase CPU benchmark à la Geekbench/AnTuTu sub-tests:
 *  1. Integer arithmetic + hash-mix
 *  2. Floating-point math (sqrt + matrix-like multiply)
 *  3. SHA-256 cryptography
 *  4. Quicksort on random Int arrays
 *
 * Each phase runs for [phaseSeconds] in single-core mode, then again
 * in multi-core (one thread per available core, raw Threads at MAX
 * priority to avoid the Default-dispatcher's bounded pool).
 *
 * Total wall time = phaseSeconds × 4 × 2 (single + multi).
 */
object CpuBenchmark {

    suspend fun run(
        context: Context? = null,
        phaseSeconds: Int = 30,
        onProgress: (phase: String, doneSeconds: Int, totalSeconds: Int) -> Unit = { _, _, _ -> }
    ): CpuBenchmarkResult = withContext(Dispatchers.Default) {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val pm = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RedMonitor:Bench")
            ?.also {
                it.setReferenceCounted(false)
                runCatching { it.acquire(phaseSeconds * 8L * 1000L) }
            }
        if (context != null) runCatching { PerformanceBooster.boost(context) }

        val phases = listOf(
            CpuPhase("Integer", BenchmarkReferences.CPU_INT_REF_OPS, ::intLoop),
            CpuPhase("Float", BenchmarkReferences.CPU_FLOAT_REF_OPS, ::floatLoop),
            CpuPhase("Crypto (SHA-256)", BenchmarkReferences.CPU_CRYPTO_REF_HASHES, ::cryptoLoop),
            CpuPhase("Sort (Quicksort)", BenchmarkReferences.CPU_SORT_REF_ELEMENTS, ::sortLoop)
        )

        val totalSeconds = phases.size * 2 * phaseSeconds
        var elapsedSeconds = 0
        val totalStart = System.currentTimeMillis()
        val subScores = mutableListOf<CpuSubScore>()

        try {
            for (phase in phases) {
                onProgress("${phase.name} (single)", elapsedSeconds, totalSeconds)
                val singleOps = runSingle(phaseSeconds, phase.worker)
                elapsedSeconds += phaseSeconds

                onProgress("${phase.name} (multi)", elapsedSeconds, totalSeconds)
                val multiOps = runMulti(cores, phaseSeconds, phase.worker)
                elapsedSeconds += phaseSeconds

                val singleOpsPerSec = singleOps.toDouble() / phaseSeconds
                val multiOpsPerSec = multiOps.toDouble() / phaseSeconds
                subScores += CpuSubScore(
                    name = phase.name,
                    singleOpsPerSec = singleOpsPerSec,
                    multiOpsPerSec = multiOpsPerSec,
                    singleScore = normalize(singleOpsPerSec, phase.referenceOpsPerSec),
                    multiScore = normalize(multiOpsPerSec, phase.referenceOpsPerSec * cores * 0.85)
                )
            }
            val duration = System.currentTimeMillis() - totalStart
            val totalSingle = subScores.sumOf { it.singleScore } / phases.size
            val totalMulti = subScores.sumOf { it.multiScore } / phases.size
            val parallelism = if (totalSingle > 0) totalMulti.toFloat() / totalSingle.toFloat() else 0f

            CpuBenchmarkResult(
                subScores = subScores,
                singleScore = totalSingle,
                multiScore = totalMulti,
                parallelism = parallelism,
                durationMs = duration,
                phaseDurationSec = phaseSeconds
            )
        } finally {
            runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
        }
    }

    private class CpuPhase(
        val name: String,
        val referenceOpsPerSec: Double,
        val worker: (Long) -> Long
    )

    private fun runSingle(seconds: Int, worker: (Long) -> Long): Long {
        val deadlineMs = System.currentTimeMillis() + seconds * 1000L
        val result = AtomicLong(0)
        val t = Thread({
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) }
            result.set(worker(deadlineMs))
        }, "RM-CPU-Single").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
        }
        t.start()
        t.join()
        return result.get()
    }

    private fun runMulti(cores: Int, seconds: Int, worker: (Long) -> Long): Long {
        val deadlineMs = System.currentTimeMillis() + seconds * 1000L
        val results = Array(cores) { AtomicLong(0) }
        val threads = (0 until cores).map { idx ->
            Thread({
                runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) }
                results[idx].set(worker(deadlineMs))
            }, "RM-CPU-$idx").apply {
                isDaemon = true
                priority = Thread.MAX_PRIORITY
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        return results.sumOf { it.get() }
    }

    // ===== Sub-test workers (each returns ops counted until deadline) =====

    private fun intLoop(deadlineMs: Long): Long {
        var acc = 1234567L
        var ops = 0L
        while (System.currentTimeMillis() < deadlineMs) {
            var i = 0
            while (i < 200_000) {
                acc = ((acc * 31L) xor (acc shr 7)) + (i.toLong() * 13L)
                acc = acc xor (acc ushr 17)
                i++
            }
            ops += 200_000L
        }
        if (acc == Long.MIN_VALUE) error("unreachable")
        return ops
    }

    private fun floatLoop(deadlineMs: Long): Long {
        var x = 1.0001
        var y = 2.0002
        var z = 0.0
        var ops = 0L
        while (System.currentTimeMillis() < deadlineMs) {
            var i = 0
            while (i < 100_000) {
                z = sqrt(x * y + 1.0) + (x / (y + 0.5))
                y = z * 1.000003 + 0.5
                x = y - sqrt(z + 1.0)
                i++
            }
            ops += 100_000L
        }
        if (x == Double.NaN || y == Double.NaN || z == Double.NaN) error("nope")
        return ops
    }

    private fun cryptoLoop(deadlineMs: Long): Long {
        val md = MessageDigest.getInstance("SHA-256")
        val data = ByteArray(1024) { (it and 0xFF).toByte() }
        var hashes = 0L
        while (System.currentTimeMillis() < deadlineMs) {
            md.update(data)
            md.digest()
            md.reset()
            md.update(data)
            md.digest()
            md.reset()
            hashes += 2
        }
        return hashes
    }

    private fun sortLoop(deadlineMs: Long): Long {
        val size = 4096
        val template = IntArray(size) { Random.nextInt() }
        val buffer = IntArray(size)
        var elementsSorted = 0L
        while (System.currentTimeMillis() < deadlineMs) {
            System.arraycopy(template, 0, buffer, 0, size)
            java.util.Arrays.sort(buffer)
            elementsSorted += size
        }
        return elementsSorted
    }
}
