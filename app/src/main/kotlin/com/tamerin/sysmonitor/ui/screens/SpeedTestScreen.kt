package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

private data class PhaseResult(val mbps: Double, val transferredMb: Double, val seconds: Double)

@Composable
fun SpeedTestScreen() {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var currentPhase by remember { mutableStateOf("Bereit") }
    var liveMbps by remember { mutableDoubleStateOf(0.0) }
    var latencyMs by remember { mutableIntStateOf(0) }
    var jitterMs by remember { mutableIntStateOf(0) }
    var downloadResult by remember { mutableStateOf<PhaseResult?>(null) }
    var uploadResult by remember { mutableStateOf<PhaseResult?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun start() {
        running = true
        errorMsg = null
        downloadResult = null; uploadResult = null
        scope.launch {
            try {
                currentPhase = "Latency ..."
                val (lat, jit) = withContext(Dispatchers.IO) { measureLatency() }
                latencyMs = lat; jitterMs = jit

                currentPhase = "Download ..."
                downloadResult = withContext(Dispatchers.IO) {
                    runCatching { downloadTest { mbps -> liveMbps = mbps } }
                        .onFailure { errorMsg = "Download: ${it.message ?: it::class.simpleName}" }
                        .getOrNull()
                }

                if (errorMsg == null) {
                    currentPhase = "Upload ..."
                    uploadResult = withContext(Dispatchers.IO) {
                        runCatching { uploadTest { mbps -> liveMbps = mbps } }
                            .onFailure { errorMsg = "Upload: ${it.message ?: it::class.simpleName}" }
                            .getOrNull()
                    }
                }
                currentPhase = if (errorMsg != null) "Fehler" else "Fertig"
            } catch (t: Throwable) {
                errorMsg = t.message ?: t::class.simpleName ?: "Unbekannter Fehler"
                currentPhase = "Fehler"
            } finally {
                running = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("Speed-Test (Cloudflare)") {
            Text(
                "Latency, Download und Upload gegen speed.cloudflare.com. " +
                    "Single-Connection — d.h. der typische 'realistische' Real-World-Wert, " +
                    "nicht der maximale Multi-Stream-Wert wie bei Ookla.",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            KeyValueRow("Phase", currentPhase)
            if (running) {
                KeyValueRow("Live", "${"%.1f".format(liveMbps)} Mbps")
            }
        }

        errorMsg?.let {
            StatCard("Fehler") {
                Text(it, color = androidx.compose.ui.graphics.Color(0xFFDC2626),
                    fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }

        StatCard("Latency") {
            KeyValueRow("Ping", if (latencyMs > 0) "$latencyMs ms" else "—")
            KeyValueRow("Jitter", if (jitterMs > 0) "$jitterMs ms" else "—")
        }
        downloadResult?.let { r ->
            StatCard("Download") {
                Text("${"%.2f".format(r.mbps)} Mbps", color = GaugeGreen, fontSize = 32.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                KeyValueRow("Übertragen", "${"%.1f".format(r.transferredMb)} MB")
                KeyValueRow("Dauer", "${"%.1f".format(r.seconds)} s")
            }
        }
        uploadResult?.let { r ->
            StatCard("Upload") {
                Text("${"%.2f".format(r.mbps)} Mbps", color = Accent, fontSize = 32.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                KeyValueRow("Übertragen", "${"%.1f".format(r.transferredMb)} MB")
                KeyValueRow("Dauer", "${"%.1f".format(r.seconds)} s")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { start() }, enabled = !running, modifier = Modifier.weight(1f)) {
                Text(if (running) "Läuft…" else "Start")
            }
            OutlinedButton(onClick = {
                downloadResult = null; uploadResult = null; latencyMs = 0; jitterMs = 0
                currentPhase = "Bereit"
            }, enabled = !running, modifier = Modifier.weight(1f)) { Text("Reset") }
        }
    }
}

private fun measureLatency(): Pair<Int, Int> {
    val samples = mutableListOf<Long>()
    repeat(10) {
        val start = System.nanoTime()
        runCatching {
            val c = URL("https://speed.cloudflare.com/__down?bytes=0").openConnection() as HttpURLConnection
            c.requestMethod = "HEAD"
            c.connectTimeout = 3000
            c.readTimeout = 3000
            c.responseCode
            c.disconnect()
        }
        samples += (System.nanoTime() - start) / 1_000_000
    }
    if (samples.size < 2) return 0 to 0
    val sorted = samples.sorted()
    val median = sorted[sorted.size / 2].toInt()
    // Jitter = mean absolute difference between consecutive
    val jit = samples.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average().toInt()
    return median to jit
}

private suspend fun downloadTest(onProgress: (Double) -> Unit): PhaseResult {
    val bytes = 50L * 1024 * 1024  // 50 MB - genug für realistische Messung, aber nicht so viel dass Mobile-Daten leiden
    val url = URL("https://speed.cloudflare.com/__down?bytes=$bytes")
    val c = url.openConnection() as HttpURLConnection
    c.connectTimeout = 8000
    c.readTimeout = 20000
    c.setRequestProperty("Accept-Encoding", "identity")  // kein gzip - sonst lügen die Zahlen
    try {
        c.inputStream.use { input ->
            val buf = ByteArray(64 * 1024)
            var total = 0L
            val start = System.nanoTime()
            var lastReport = start
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                val now = System.nanoTime()
                val seconds = (now - start) / 1_000_000_000.0
                if (seconds > 12.0) break
                if (now - lastReport > 200_000_000L) {
                    onProgress(total * 8.0 / 1_000_000.0 / max(seconds, 0.05))
                    lastReport = now
                }
            }
            val seconds = (System.nanoTime() - start) / 1_000_000_000.0
            val mbps = total * 8.0 / 1_000_000.0 / max(seconds, 0.05)
            return PhaseResult(mbps, total / (1024.0 * 1024.0), seconds)
        }
    } finally {
        runCatching { c.disconnect() }
    }
}

private suspend fun uploadTest(onProgress: (Double) -> Unit): PhaseResult {
    val payload = ByteArray(4 * 1024 * 1024)  // 4 MB chunk
    val chunks = 4
    val url = URL("https://speed.cloudflare.com/__up")
    val c = url.openConnection() as HttpURLConnection
    c.requestMethod = "POST"
    c.doOutput = true
    c.connectTimeout = 8000
    c.readTimeout = 20000
    c.setFixedLengthStreamingMode(payload.size.toLong() * chunks)
    try {
        c.outputStream.use { out ->
            var total = 0L
            val start = System.nanoTime()
            var lastReport = start
            repeat(chunks) {
                out.write(payload); out.flush()
                total += payload.size
                val now = System.nanoTime()
                if (now - lastReport > 200_000_000L) {
                    val secs = (now - start) / 1_000_000_000.0
                    onProgress(total * 8.0 / 1_000_000.0 / max(secs, 0.05))
                    lastReport = now
                }
            }
            val seconds = (System.nanoTime() - start) / 1_000_000_000.0
            runCatching { c.responseCode }
            val mbps = total * 8.0 / 1_000_000.0 / max(seconds, 0.05)
            return PhaseResult(mbps, total / (1024.0 * 1024.0), seconds)
        }
    } finally {
        runCatching { c.disconnect() }
    }
}
