package com.tamerin.sysmonitor.benchmark

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.measureNanoTime

data class RamBenchmarkResult(
    val writeMbPerSec: Double,
    val readMbPerSec: Double,
    val copyMbPerSec: Double,
    val bufferSizeMb: Int
)

object RamBenchmark {
    private const val BUFFER_MB = 64
    private const val BYTES_PER_MB = 1024L * 1024L

    suspend fun run(): RamBenchmarkResult = withContext(Dispatchers.Default) {
        val sizeBytes = BUFFER_MB * BYTES_PER_MB.toInt()
        val src = ByteArray(sizeBytes)
        val dst = ByteArray(sizeBytes)

        // Write: fill with pattern
        val writeNs = measureNanoTime {
            for (i in src.indices) src[i] = (i and 0xFF).toByte()
        }

        // Read: sum all bytes
        var sum = 0L
        val readNs = measureNanoTime {
            for (i in src.indices) sum += src[i].toInt()
        }

        // Copy: array copy
        val copyNs = measureNanoTime {
            System.arraycopy(src, 0, dst, 0, sizeBytes)
        }
        // Reference to prevent dead-code elimination
        if (sum == Long.MIN_VALUE && dst[0] == Byte.MIN_VALUE && dst[1] == Byte.MAX_VALUE) error("x")

        RamBenchmarkResult(
            writeMbPerSec = throughput(BUFFER_MB.toLong(), writeNs),
            readMbPerSec = throughput(BUFFER_MB.toLong(), readNs),
            copyMbPerSec = throughput(BUFFER_MB.toLong(), copyNs),
            bufferSizeMb = BUFFER_MB
        )
    }

    private fun throughput(mb: Long, ns: Long): Double =
        if (ns <= 0) 0.0 else (mb.toDouble() * 1_000_000_000.0) / ns.toDouble()
}
