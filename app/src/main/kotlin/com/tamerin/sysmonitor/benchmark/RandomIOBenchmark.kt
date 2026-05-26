package com.tamerin.sysmonitor.benchmark

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import kotlin.system.measureNanoTime

data class RandomIOResult(
    val read4kIops: Int,
    val write4kIops: Int,
    val readMbPerSec: Double,
    val writeMbPerSec: Double,
    val testFileSizeMb: Int
)

object RandomIOBenchmark {
    private const val FILE_SIZE_MB = 16
    private const val BLOCK_SIZE = 4096
    private const val OPS_PER_TEST = 2000

    suspend fun run(context: Context): RandomIOResult = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "sysmonitor_rand.bin")
        // Initialize file with sequential data
        file.outputStream().use { os ->
            val chunk = ByteArray(1024 * 1024)
            for (i in chunk.indices) chunk[i] = (i and 0xFF).toByte()
            repeat(FILE_SIZE_MB) { os.write(chunk) }
        }
        val fileBytes = file.length()
        val random = java.util.Random(42)
        val buf = ByteArray(BLOCK_SIZE)

        // Random read
        var readSum = 0L
        val readNs = measureNanoTime {
            RandomAccessFile(file, "r").use { raf ->
                repeat(OPS_PER_TEST) {
                    val offset = (random.nextLong() and Long.MAX_VALUE) %
                        (fileBytes - BLOCK_SIZE)
                    raf.seek(offset)
                    raf.readFully(buf)
                    readSum += buf[0].toInt()
                }
            }
        }

        // Random write
        val writeNs = measureNanoTime {
            RandomAccessFile(file, "rw").use { raf ->
                repeat(OPS_PER_TEST) { i ->
                    val offset = (random.nextLong() and Long.MAX_VALUE) %
                        (fileBytes - BLOCK_SIZE)
                    raf.seek(offset)
                    buf[0] = i.toByte()
                    raf.write(buf)
                }
                raf.fd.sync()
            }
        }
        if (readSum == Long.MIN_VALUE) error("x")
        file.delete()

        val readSec = readNs / 1e9
        val writeSec = writeNs / 1e9
        val readMb = OPS_PER_TEST * BLOCK_SIZE / 1024.0 / 1024.0
        val writeMb = OPS_PER_TEST * BLOCK_SIZE / 1024.0 / 1024.0

        RandomIOResult(
            read4kIops = (OPS_PER_TEST / readSec).toInt(),
            write4kIops = (OPS_PER_TEST / writeSec).toInt(),
            readMbPerSec = readMb / readSec,
            writeMbPerSec = writeMb / writeSec,
            testFileSizeMb = FILE_SIZE_MB
        )
    }
}
