package com.tamerin.sysmonitor.benchmark

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong

data class QdResult(
    val queueDepth: Int,
    val readIops: Int,
    val writeIops: Int,
    val readMbPerSec: Double,
    val writeMbPerSec: Double
)

data class RandomIOResult(
    val perQd: List<QdResult>,
    val totalScore: Int,
    val testFileSizeMb: Int,
    val durationSec: Int
)

/**
 * Random 4-KB IOPS across multiple queue depths (QD=1, QD=4, QD=16).
 *
 * QD=1 measures latency-bound serial performance — what you feel when an app
 * scrolls a SQLite DB.
 * QD=4 / QD=16 measure parallel throughput — what UFS+f2fs is actually capable
 * of when many threads request blocks at once. The gap between them reveals
 * how well the storage can pipeline requests.
 *
 * Each (QD × read/write) combination runs for [durationSec] seconds — far more
 * stable than the previous 2000-op fixed count which finished in under 200 ms
 * on fast UFS 4.0 devices.
 */
object RandomIOBenchmark {

    private const val FILE_SIZE_MB = 64
    private const val BLOCK_SIZE = 4096
    private val QUEUE_DEPTHS = listOf(1, 4, 16)

    suspend fun run(
        context: Context,
        durationSec: Int = 10,
        onProgress: (label: String, doneSec: Int, totalSec: Int) -> Unit = { _, _, _ -> }
    ): RandomIOResult = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "sysmonitor_rand_bench.bin")
        file.delete()

        // Initialize file with sequential data
        file.outputStream().use { os ->
            val chunk = ByteArray(1024 * 1024).also { for (i in it.indices) it[i] = (i and 0xFF).toByte() }
            repeat(FILE_SIZE_MB) { os.write(chunk) }
        }
        val fileBytes = file.length()
        val totalPhases = QUEUE_DEPTHS.size * 2
        var phaseIndex = 0

        try {
            val perQd = mutableListOf<QdResult>()
            for (qd in QUEUE_DEPTHS) {
                onProgress("QD=$qd · Random Read", phaseIndex * durationSec, totalPhases * durationSec)
                val readOps = randomOps(file, fileBytes, qd, durationSec, writeMode = false)
                phaseIndex++

                onProgress("QD=$qd · Random Write", phaseIndex * durationSec, totalPhases * durationSec)
                val writeOps = randomOps(file, fileBytes, qd, durationSec, writeMode = true)
                phaseIndex++

                val readIops = (readOps.toDouble() / durationSec).toInt()
                val writeIops = (writeOps.toDouble() / durationSec).toInt()
                perQd += QdResult(
                    queueDepth = qd,
                    readIops = readIops,
                    writeIops = writeIops,
                    readMbPerSec = readIops * BLOCK_SIZE / 1_048_576.0,
                    writeMbPerSec = writeIops * BLOCK_SIZE / 1_048_576.0
                )
            }

            // Score: average of QD=1 score and QD=16 score (read+write averaged)
            val qd1 = perQd.first { it.queueDepth == 1 }
            val qd16 = perQd.first { it.queueDepth == 16 }
            val qd1Score = (
                normalize(qd1.readIops.toDouble(), BenchmarkReferences.STORAGE_RAND_QD1_REF_IOPS) +
                normalize(qd1.writeIops.toDouble(), BenchmarkReferences.STORAGE_RAND_QD1_REF_IOPS)
            ) / 2
            val qd16Score = (
                normalize(qd16.readIops.toDouble(), BenchmarkReferences.STORAGE_RAND_QD16_REF_IOPS) +
                normalize(qd16.writeIops.toDouble(), BenchmarkReferences.STORAGE_RAND_QD16_REF_IOPS)
            ) / 2

            RandomIOResult(
                perQd = perQd,
                totalScore = (qd1Score + qd16Score) / 2,
                testFileSizeMb = FILE_SIZE_MB,
                durationSec = durationSec
            )
        } finally {
            file.delete()
        }
    }

    private suspend fun randomOps(
        file: File,
        fileBytes: Long,
        queueDepth: Int,
        durationSec: Int,
        writeMode: Boolean
    ): Long = coroutineScope {
        val deadline = System.currentTimeMillis() + durationSec * 1000L
        val totalOps = AtomicLong(0)
        val jobs = (0 until queueDepth).map { workerIdx ->
            async(Dispatchers.IO) {
                val random = java.util.Random(System.nanoTime() xor workerIdx.toLong())
                val buf = ByteArray(BLOCK_SIZE)
                val mode = if (writeMode) "rw" else "r"
                RandomAccessFile(file, mode).use { raf ->
                    var ops = 0L
                    while (System.currentTimeMillis() < deadline) {
                        val offset = (random.nextLong() and Long.MAX_VALUE) %
                            (fileBytes - BLOCK_SIZE)
                        raf.seek(offset and (BLOCK_SIZE - 1).inv().toLong())
                        if (writeMode) {
                            buf[0] = ops.toByte()
                            raf.write(buf)
                        } else {
                            raf.readFully(buf)
                        }
                        ops++
                    }
                    if (writeMode) raf.fd.sync()
                    totalOps.addAndGet(ops)
                }
            }
        }
        jobs.awaitAll()
        totalOps.get()
    }
}
