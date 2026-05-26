package com.tamerin.sysmonitor.benchmark

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import kotlin.system.measureNanoTime

data class CpuBenchmarkResult(
    val singleCoreOps: Long,
    val multiCoreOps: Long,
    val singleScore: Int,
    val multiScore: Int,
    val parallelism: Float,
    val durationMs: Long
)

/**
 * Local CPU benchmark — integer + float + sqrt loop, single vs all cores.
 * Score is normalized so that ~1000 = mid-range modern device.
 */
object CpuBenchmark {

    private const val DURATION_MS = 2500L
    private const val SCORE_DIVISOR = 100_000L

    suspend fun run(): CpuBenchmarkResult = withContext(Dispatchers.Default) {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val totalStart = System.currentTimeMillis()

        val singleOps = runWorker()

        val multiOpsList = coroutineScope {
            (0 until cores).map {
                async { runWorker() }
            }.awaitAll()
        }
        val multiOps = multiOpsList.sum()

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
    }

    private fun runWorker(): Long {
        val deadline = System.currentTimeMillis() + DURATION_MS
        var iSum = 0L
        var fSum = 0.0
        var ops = 0L
        while (System.currentTimeMillis() < deadline) {
            repeat(50_000) { i ->
                iSum += (i * 31L) xor (iSum shr 3)
                fSum += sqrt((i + 1).toDouble()) * 1.000001
            }
            ops += 50_000
        }
        // Use the sums so the JIT can't dead-strip.
        if (iSum == Long.MIN_VALUE && fSum == Double.NaN) error("unreachable")
        return ops
    }
}
