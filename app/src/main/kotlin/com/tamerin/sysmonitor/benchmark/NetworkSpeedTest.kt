package com.tamerin.sysmonitor.benchmark

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class NetworkSpeedResult(
    val mbps: Double,
    val downloadedMb: Double,
    val durationMs: Long,
    val endpoint: String
)

object NetworkSpeedTest {
    /**
     * Cloudflare's speed test endpoint — returns arbitrary number of bytes via ?bytes=N.
     * Free, no auth, used by speed.cloudflare.com itself.
     */
    private const val ENDPOINT_BASE = "https://speed.cloudflare.com/__down?bytes="
    private const val BYTES = 25 * 1024 * 1024 // 25 MB

    suspend fun run(onProgress: (Double) -> Unit = {}): NetworkSpeedResult =
        withContext(Dispatchers.IO) {
            val url = URL("$ENDPOINT_BASE$BYTES")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.requestMethod = "GET"
            conn.useCaches = false
            conn.setRequestProperty("Cache-Control", "no-cache")
            val startNs = System.nanoTime()
            var bytesRead = 0L
            val buf = ByteArray(64 * 1024)
            try {
                conn.inputStream.use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        bytesRead += n
                        if (bytesRead and 0xFFFFF == 0L) {
                            val mb = bytesRead / 1024.0 / 1024.0
                            onProgress(mb)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            val ns = System.nanoTime() - startNs
            val mb = bytesRead / 1024.0 / 1024.0
            val mbits = bytesRead * 8.0 / 1_000_000.0
            val seconds = ns / 1e9
            val mbps = if (seconds > 0) mbits / seconds else 0.0
            NetworkSpeedResult(
                mbps = mbps,
                downloadedMb = mb,
                durationMs = (ns / 1_000_000L),
                endpoint = url.host
            )
        }
}
