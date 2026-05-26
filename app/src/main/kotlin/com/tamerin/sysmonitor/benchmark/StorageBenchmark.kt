package com.tamerin.sysmonitor.benchmark

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.system.measureNanoTime

data class StorageBenchmarkResult(
    val seqWriteMbPerSec: Double,
    val seqReadMbPerSec: Double,
    val testFileSizeMb: Int,
    val location: String
)

object StorageBenchmark {
    private const val FILE_SIZE_MB = 32
    private const val CHUNK_BYTES = 1024 * 1024 // 1 MB

    suspend fun run(context: Context): StorageBenchmarkResult = withContext(Dispatchers.IO) {
        val testFile = File(context.cacheDir, "sysmonitor_bench.bin")
        val chunk = ByteArray(CHUNK_BYTES)
        for (i in chunk.indices) chunk[i] = (i and 0xFF).toByte()

        // Write
        val writeNs = measureNanoTime {
            FileOutputStream(testFile).use { fos ->
                repeat(FILE_SIZE_MB) { fos.write(chunk) }
                fos.fd.sync()
            }
        }

        // Read
        var sum = 0L
        val readBuf = ByteArray(CHUNK_BYTES)
        val readNs = measureNanoTime {
            FileInputStream(testFile).use { fis ->
                var read: Int
                while (fis.read(readBuf).also { read = it } > 0) {
                    for (i in 0 until read) sum += readBuf[i].toInt()
                }
            }
        }
        if (sum == Long.MIN_VALUE) error("x")
        testFile.delete()

        StorageBenchmarkResult(
            seqWriteMbPerSec = throughput(FILE_SIZE_MB.toLong(), writeNs),
            seqReadMbPerSec = throughput(FILE_SIZE_MB.toLong(), readNs),
            testFileSizeMb = FILE_SIZE_MB,
            location = context.cacheDir.absolutePath
        )
    }

    private fun throughput(mb: Long, ns: Long): Double =
        if (ns <= 0) 0.0 else (mb.toDouble() * 1_000_000_000.0) / ns.toDouble()
}
