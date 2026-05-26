package com.tamerin.sysmonitor.benchmark

import android.content.Context
import android.os.PowerManager
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

data class CpuBenchmarkResult(
    val singleCoreOps: Long,
    val multiCoreOps: Long,
    val singleScore: Int,
    val multiScore: Int,
    val parallelism: Float,
    val durationMs: Long
)

/**
 * Local CPU benchmark using raw threads at MAX priority. Coroutines via Dispatchers.Default
 * share a limited pool and get scheduler-throttled — direct Thread() with MAX_PRIORITY
 * gives the OS one independent thread per core to schedule freely.
 */
object CpuBenchmark {

    private const val DURATION_MS = 2500L
    private const val SCORE_DIVISOR = 100_000L

    suspend fun run(context: Context? = null): CpuBenchmarkResult = withContext(Dispatchers.Default) {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val pm = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RedMonitor:Bench")
            ?.also {
                it.setReferenceCounted(false)
                runCatching { it.acquire(60_000L) }
            }
        if (context != null) runCatching { PerformanceBooster.boost(context) }

        val totalStart = System.currentTimeMillis()
        try {
            val singleOps = runOnRawThread()
            val multiOps = runOnRawThreadsParallel(cores)
            val duration = System.currentTimeMillis() - totalStart
            val singleScore = (singleOps / SCORE_DIVISOR).toInt()
            val multiScore = (multiOps / SCORE_DIVISOR).toInt()
            val parallelism = if (singleOps > 0) multiOps.toFloat() / singleOps.toFloat() else 0f

            CpuBenchmarkResult(
                singleCoreOps = singleOps,
                multiCoreOps = multiOps,
                singleScore = singleScore,
                multiScore = multiScore,
                parallelism = parallelism,
                durationMs = duration
            )
        } finally {
            runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
        }
    }

    private fun runOnRawThread(): Long {
        val result = AtomicLong(0)
        val t = Thread({
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) }
            result.set(workerLoop())
        }, "RedMonitor-Bench-Single").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
        }
        t.start()
        t.join()
        return result.get()
    }

    private fun runOnRawThreadsParallel(cores: Int): Long {
        val results = Array(cores) { AtomicLong(0) }
        val threads = (0 until cores).map { idx ->
            Thread({
                runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) }
                results[idx].set(workerLoop())
            }, "RedMonitor-Bench-$idx").apply {
                isDaemon = true
                priority = Thread.MAX_PRIORITY
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        return results.sumOf { it.get() }
    }

    private fun workerLoop(): Long {
        val deadline = System.currentTimeMillis() + DURATION_MS
        var iSum = 0L
        var fSum = 0.0
        var ops = 0L
        while (System.currentTimeMillis() < deadline) {
            var i = 0
            while (i < 50_000) {
                iSum += (i * 31L) xor (iSum shr 3)
                fSum += sqrt((i + 1).toDouble()) * 1.000001
                i++
            }
            ops += 50_000
        }
        // Reference to prevent JIT dead-code elimination
        if (iSum == Long.MIN_VALUE && fSum == Double.NaN) error("unreachable")
        return ops
    }
}
