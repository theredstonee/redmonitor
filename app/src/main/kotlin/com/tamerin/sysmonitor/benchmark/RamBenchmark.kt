package com.tamerin.sysmonitor.benchmark

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RamTierResult(
    val tierName: String,
    val bufferKb: Int,
    val readMbPerSec: Double,
    val writeMbPerSec: Double,
    val copyMbPerSec: Double,
    val score: Int
)

data class RamBenchmarkResult(
    val tiers: List<RamTierResult>,
    val totalScore: Int,
    val perTestSeconds: Int
)

/**
 * Tier-aware RAM benchmark. Runs each operation against four different buffer
 * sizes so you can see the L1/L2/L3/DRAM bandwidth cliff:
 *   - 32 KB   → fits L1 cache on most ARM big-cores
 *   - 1 MB    → falls into L2
 *   - 16 MB   → falls into shared L3 / SLC
 *   - 256 MB  → hits DRAM
 *
 * Each tier×op pair runs for `perTestSeconds`, the throughput is the median of
 * 5 measurement windows to suppress GC/scheduling noise.
 */
object RamBenchmark {

    private const val BYTES_PER_MB = 1024L * 1024L

    private data class Tier(
        val name: String,
        val bufferKb: Int,
        val readRef: Double,
        val writeRef: Double,
        val copyRef: Double
    )

    private val TIERS = listOf(
        Tier(
            "L1-Cache (32 KB)", 32,
            BenchmarkReferences.RAM_L1_REF_MBS,
            BenchmarkReferences.RAM_L1_REF_MBS * 0.8,
            BenchmarkReferences.RAM_L1_REF_MBS * 0.9
        ),
        Tier(
            "L2-Cache (1 MB)", 1024,
            BenchmarkReferences.RAM_L2_REF_MBS,
            BenchmarkReferences.RAM_L2_REF_MBS * 0.8,
            BenchmarkReferences.RAM_L2_REF_MBS * 0.9
        ),
        Tier(
            "L3 / SLC (16 MB)", 16 * 1024,
            BenchmarkReferences.RAM_L3_REF_MBS,
            BenchmarkReferences.RAM_L3_REF_MBS * 0.8,
            BenchmarkReferences.RAM_L3_REF_MBS * 0.9
        ),
        Tier(
            "DRAM (256 MB)", 256 * 1024,
            BenchmarkReferences.RAM_DRAM_REF_MBS,
            BenchmarkReferences.RAM_DRAM_REF_MBS * 0.8,
            BenchmarkReferences.RAM_DRAM_REF_MBS * 0.9
        )
    )

    suspend fun run(
        perTestSeconds: Int = 5,
        onProgress: (label: String, doneSeconds: Int, totalSeconds: Int) -> Unit = { _, _, _ -> }
    ): RamBenchmarkResult = withContext(Dispatchers.Default) {
        val total = TIERS.size * perTestSeconds * 3 // read/write/copy
        var done = 0
        val tierResults = mutableListOf<RamTierResult>()
        for (tier in TIERS) {
            val sizeBytes = tier.bufferKb * 1024
            val src = ByteArray(sizeBytes)
            val dst = ByteArray(sizeBytes)
            // Touch every page first so the kernel actually backs them in RAM.
            for (i in src.indices step 4096) src[i] = 1

            onProgress("${tier.name} · Write", done, total)
            val writeMbs = bandwidth(sizeBytes, perTestSeconds) { writeBuffer(src) }
            done += perTestSeconds

            onProgress("${tier.name} · Read", done, total)
            val readMbs = bandwidth(sizeBytes, perTestSeconds) { readBuffer(src) }
            done += perTestSeconds

            onProgress("${tier.name} · Copy", done, total)
            val copyMbs = bandwidth(sizeBytes, perTestSeconds) {
                System.arraycopy(src, 0, dst, 0, sizeBytes)
            }
            done += perTestSeconds

            val score = (normalize(readMbs, tier.readRef) +
                normalize(writeMbs, tier.writeRef) +
                normalize(copyMbs, tier.copyRef)) / 3

            tierResults += RamTierResult(
                tierName = tier.name,
                bufferKb = tier.bufferKb,
                readMbPerSec = readMbs,
                writeMbPerSec = writeMbs,
                copyMbPerSec = copyMbs,
                score = score
            )
        }
        val totalScore = tierResults.sumOf { it.score } / tierResults.size
        RamBenchmarkResult(
            tiers = tierResults,
            totalScore = totalScore,
            perTestSeconds = perTestSeconds
        )
    }

    /**
     * Run [op] in a tight loop until [seconds] elapsed, then return throughput
     * in MB/s. Median of 5 windows trims outliers from GC pauses or thread
     * migration mid-window.
     */
    private inline fun bandwidth(bufferBytes: Int, seconds: Int, op: () -> Unit): Double {
        val windowMs = seconds * 1000L / 5L
        val samples = DoubleArray(5)
        for (s in 0 until 5) {
            var bytesTouched = 0L
            val deadline = System.currentTimeMillis() + windowMs
            while (System.currentTimeMillis() < deadline) {
                op()
                bytesTouched += bufferBytes
            }
            val mb = bytesTouched.toDouble() / BYTES_PER_MB
            samples[s] = mb / (windowMs / 1000.0)
        }
        samples.sort()
        return samples[2] // median
    }

    private fun writeBuffer(buf: ByteArray) {
        // Sequential write — equivalent to memset but byte-level so we
        // measure unaccelerated memory stores rather than vectorized intrinsics.
        var v = 0
        for (i in buf.indices) {
            buf[i] = v.toByte()
            v = (v + 1) and 0xFF
        }
    }

    private fun readBuffer(buf: ByteArray): Long {
        // Sequential read, sum to prevent dead-code elimination
        var sum = 0L
        for (i in buf.indices) sum += buf[i].toInt()
        return sum
    }
}
