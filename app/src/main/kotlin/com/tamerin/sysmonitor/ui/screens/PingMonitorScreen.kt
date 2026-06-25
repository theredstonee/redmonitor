package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.Socket

private data class Target(val name: String, val host: String, val port: Int, val color: Color)

private val TARGETS = listOf(
    Target("1.1.1.1", "1.1.1.1", 443, GaugeGreen),
    Target("8.8.8.8", "8.8.8.8", 443, AccentSoft),
    Target("heise.de", "www.heise.de", 443, GaugeOrange),
    Target("steam-cdn", "steamcommunity.com", 443, GaugeRed)
)

@Composable
fun PingMonitorScreen() {
    val scope = rememberCoroutineScope()
    val series = remember { mutableStateMapOf<String, MutableList<Long>>() }
    var jobs by remember { mutableStateOf<List<Job>>(emptyList()) }
    var running by remember { mutableStateOf(false) }

    fun start() {
        running = true
        TARGETS.forEach { series[it.name] = mutableListOf() }
        val newJobs = TARGETS.map { t ->
            scope.launch {
                while (isActive) {
                    val ms = withContext(Dispatchers.IO) { tcpPing(t.host, t.port) }
                    val list = series[t.name] ?: continue
                    list += ms
                    if (list.size > 80) list.removeAt(0)
                    series[t.name] = list.toMutableList()  // trigger recompose
                    delay(1000)
                }
            }
        }
        jobs = newJobs
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        running = false
    }

    DisposableEffect(Unit) { onDispose { jobs.forEach { it.cancel() } } }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (!running) start() }, enabled = !running,
                modifier = Modifier.weight(1f)) { Text("Start") }
            OutlinedButton(onClick = { stop() }, enabled = running,
                modifier = Modifier.weight(1f)) { Text("Stop") }
        }
        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxWidth().height(220.dp)
            .background(Color(0x14FFFFFF))) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                val pad = 24f
                val maxMs = (series.values.flatten().maxOrNull() ?: 100L).coerceAtLeast(100L)
                // Y-Grid at 100ms increments
                val step = when {
                    maxMs > 500 -> 100L
                    maxMs > 200 -> 50L
                    else -> 25L
                }
                var y = 0L
                while (y <= maxMs) {
                    val yp = pad + (h - 2 * pad) * (1f - y.toFloat() / maxMs)
                    drawLine(Color(0x22FFFFFF), Offset(pad, yp), Offset(w - pad, yp), 1f)
                    y += step
                }
                TARGETS.forEach { t ->
                    val data = series[t.name].orEmpty()
                    if (data.size < 2) return@forEach
                    val plotW = w - 2 * pad
                    for (i in 1 until data.size) {
                        val x0 = pad + plotW * (i - 1f) / 80f
                        val x1 = pad + plotW * i.toFloat() / 80f
                        val y0 = pad + (h - 2 * pad) * (1f - data[i - 1] / maxMs.toFloat())
                        val y1 = pad + (h - 2 * pad) * (1f - data[i] / maxMs.toFloat())
                        drawLine(t.color, Offset(x0, y0), Offset(x1, y1), 2f)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        TARGETS.forEach { t ->
            val data = series[t.name].orEmpty().filter { it > 0 }
            val lost = (series[t.name]?.count { it < 0 }) ?: 0
            val pl = if ((series[t.name]?.size ?: 0) > 0)
                100f * lost / (series[t.name]!!.size) else 0f
            val median = data.sorted().let { if (it.isEmpty()) 0 else it[it.size / 2].toInt() }
            val jitter = if (data.size > 1)
                data.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average().toInt() else 0
            StatCard("${t.name}") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(t.color))
                    Spacer(Modifier.width(8.dp))
                    Text(t.host, color = OnSurfaceMuted, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(4.dp))
                KeyValueRow("Median", "$median ms")
                KeyValueRow("Jitter", "$jitter ms")
                KeyValueRow("Packet-Loss", "%.1f %% (%d)".format(pl, lost))
                KeyValueRow("Samples", "${series[t.name]?.size ?: 0}")
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun tcpPing(host: String, port: Int): Long {
    return runCatching {
        val start = System.nanoTime()
        Socket().use { s ->
            s.connect(java.net.InetSocketAddress(InetAddress.getByName(host), port), 2000)
        }
        (System.nanoTime() - start) / 1_000_000
    }.getOrDefault(-1L)
}
