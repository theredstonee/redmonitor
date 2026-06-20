package com.tamerin.sysmonitor.benchmark

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

data class StorageBenchmarkResult(
    val sustainedWriteMbPerSec: Double,
    val sustainedReadMbPerSec: Double,
    val peakWriteMbPerSec: Double,
    val peakReadMbPerSec: Double,
    val writeScore: Int,
    val readScore: Int,
    val totalScore: Int,
    val totalBytesWritten: Long,
    val durationSec: Int,
    val location: String
)

/**
 * Sustained sequential storage benchmark.
 *
 * The previous version wrote 32 MB in one burst → caching in the kernel page
 * cache, the result was essentially RAM bandwidth not storage bandwidth.
 * Now we write for [durationSec] seconds continuously and report:
 *   - SUSTAINED MB/s (avg over the whole window after the first 2s warmup)
 *   - PEAK MB/s (best 1s window — shows the SLC-cache-burst capacity on UFS)
 *
 * fsync() forces the data out of page cache so the timing reflects the actual
 * flash device + filesystem, not just memory.
 */
object StorageBenchmark {

    private const val CHUNK_BYTES = 1024 * 1024 // 1 MB chunks
    private const val FILE_CAP_BYTES = 512L * 1024L * 1024L // 512 MB cap
    private const val FSYNC_INTERVAL_BYTES = 32L * 1024L * 1024L // 32 MB

    suspend fun run(
        context: Context,
        durationSec: Int = 15,
        onProgress: (label: String, doneSec: Int, totalSec: Int) -> Unit = { _, _, _ -> }
    ): StorageBenchmarkResult = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "sysmonitor_storage_bench.bin")
        file.delete()
        val chunk = ByteArray(CHUNK_BYTES).also { for (i in it.indices) it[i] = (i and 0xFF).toByte() }

        // ===== WRITE phase =====
        // We cap the file at 512 MB and seek back to 0 when reaching the cap.
        // fsync every 32 MB bypasses the page cache so the measured throughput
        // reflects the actual flash + filesystem stack, not just RAM.
        onProgress("Sequential Write", 0, durationSec * 2)
        val writeStartNs = System.nanoTime()
        val writeDeadlineNs = writeStartNs + durationSec * 1_000_000_000L
        var totalWritten = 0L
        var peakWriteMbs = 0.0
        var lastWindowBytes = 0L
        var lastWindowStartNs = writeStartNs
        var sinceFsync = 0L
        var posInFile = 0L

        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(FILE_CAP_BYTES)
            raf.seek(0)
            while (System.nanoTime() < writeDeadlineNs) {
                if (posInFile + CHUNK_BYTES > FILE_CAP_BYTES) {
                    raf.seek(0)
                    posInFile = 0L
                }
                raf.write(chunk)
                posInFile += CHUNK_BYTES
                totalWritten += CHUNK_BYTES
                lastWindowBytes += CHUNK_BYTES
                sinceFsync += CHUNK_BYTES

                if (sinceFsync >= FSYNC_INTERVAL_BYTES) {
                    raf.fd.sync()
                    sinceFsync = 0L
                }

                val now = System.nanoTime()
                val windowNs = now - lastWindowStartNs
                if (windowNs >= 1_000_000_000L) {
                    val mbs = (lastWindowBytes / 1_048_576.0) / (windowNs / 1e9)
                    if (mbs > peakWriteMbs) peakWriteMbs = mbs
                    lastWindowBytes = 0L
                    lastWindowStartNs = now
                    val elapsed = ((now - writeStartNs) / 1_000_000_000L).toInt()
                    onProgress("Sequential Write", elapsed, durationSec * 2)
                }
            }
            raf.fd.sync()
        }
        val writeTotalNs = System.nanoTime() - writeStartNs
        val sustainedWriteMbs = (totalWritten / 1_048_576.0) / (writeTotalNs / 1e9)

        // ===== READ phase =====
        onProgress("Sequential Read", durationSec, durationSec * 2)
        // Drop page cache as best we can: re-open as new fd
        val readStartNs = System.nanoTime()
        val readDeadlineNs = readStartNs + durationSec * 1_000_000_000L
        var totalRead = 0L
        var peakReadMbs = 0.0
        lastWindowBytes = 0L
        lastWindowStartNs = readStartNs
        val readBuf = ByteArray(CHUNK_BYTES)
        var sum = 0L

        FileInputStream(file).use { fis ->
            while (System.nanoTime() < readDeadlineNs) {
                val n = fis.read(readBuf)
                if (n <= 0) {
                    // EOF — rewind by re-opening (StreamingFile would be cleaner but this is fine)
                    fis.close()
                    FileInputStream(file).use { fis2 ->
                        val n2 = fis2.read(readBuf)
                        if (n2 > 0) {
                            sum += readBuf[0].toLong()
                            totalRead += n2
                            lastWindowBytes += n2
                        }
                    }
                    continue
                }
                sum += readBuf[0].toLong()
                totalRead += n
                lastWindowBytes += n

                val now = System.nanoTime()
                val windowNs = now - lastWindowStartNs
                if (windowNs >= 1_000_000_000L) {
                    val mbs = (lastWindowBytes / 1_048_576.0) / (windowNs / 1e9)
                    if (mbs > peakReadMbs) peakReadMbs = mbs
                    lastWindowBytes = 0L
                    lastWindowStartNs = now
                    val elapsed = durationSec + ((now - readStartNs) / 1_000_000_000L).toInt()
                    onProgress("Sequential Read", elapsed, durationSec * 2)
                }
            }
        }
        if (sum == Long.MIN_VALUE) error("nope")
        val readTotalNs = System.nanoTime() - readStartNs
        val sustainedReadMbs = (totalRead / 1_048_576.0) / (readTotalNs / 1e9)

        file.delete()

        val writeScore = normalize(sustainedWriteMbs, BenchmarkReferences.STORAGE_SEQ_WRITE_REF_MBS)
        val readScore = normalize(sustainedReadMbs, BenchmarkReferences.STORAGE_SEQ_READ_REF_MBS)

        StorageBenchmarkResult(
            sustainedWriteMbPerSec = sustainedWriteMbs,
            sustainedReadMbPerSec = sustainedReadMbs,
            peakWriteMbPerSec = peakWriteMbs,
            peakReadMbPerSec = peakReadMbs,
            writeScore = writeScore,
            readScore = readScore,
            totalScore = (writeScore + readScore) / 2,
            totalBytesWritten = totalWritten,
            durationSec = durationSec,
            location = context.cacheDir.absolutePath
        )
    }
}
