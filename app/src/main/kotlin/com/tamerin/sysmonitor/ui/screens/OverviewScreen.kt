package com.tamerin.sysmonitor.ui.screens

import android.app.Activity
import android.net.TrafficStats
import android.os.SystemClock
import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.BatteryReader
import com.tamerin.sysmonitor.data.CpuReader
import com.tamerin.sysmonitor.data.DeviceInfo
import com.tamerin.sysmonitor.data.MemoryReader
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.data.TopCpuReader
import com.tamerin.sysmonitor.data.formatBytes
import com.tamerin.sysmonitor.ui.components.CircularGauge
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.SectionEyebrow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentBubble
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun OverviewScreen(onOpenUpdate: () -> Unit = {}) {
    val context = LocalContext.current
    val activity = context as? Activity
    var updateAvailable by remember {
        mutableStateOf<com.tamerin.sysmonitor.update.ReleaseInfo?>(null)
    }

    LaunchedEffect(Unit) {
        val state = com.tamerin.sysmonitor.update.UpdateChecker.check(
            context,
            com.tamerin.sysmonitor.update.UpdatePrefs.includePrerelease(context)
        )
        if (state.hasUpdate && state.latest != null &&
            com.tamerin.sysmonitor.update.UpdatePrefs.dismissedVersion(context) != state.latest.versionName) {
            updateAvailable = state.latest
        }
    }
    var cpuPct by remember { mutableFloatStateOf(0f) }
    var cpuSource by remember { mutableStateOf("—") }
    var perCorePct by remember { mutableStateOf<List<Float>>(emptyList()) }
    var ramPct by remember { mutableFloatStateOf(0f) }
    var ramUsed by remember { mutableLongStateOf(0L) }
    var ramTotal by remember { mutableLongStateOf(0L) }
    var batteryPct by remember { mutableFloatStateOf(0f) }
    var batteryTemp by remember { mutableFloatStateOf(0f) }
    var batteryWatts by remember { mutableFloatStateOf(0f) }
    var batteryCharging by remember { mutableStateOf(false) }
    var storagePct by remember { mutableFloatStateOf(0f) }

    // Netzwerk live
    var downKbs by remember { mutableFloatStateOf(0f) }
    var upKbs by remember { mutableFloatStateOf(0f) }
    var rxTotal by remember { mutableLongStateOf(0L) }
    var txTotal by remember { mutableLongStateOf(0L) }

    // FPS
    var fps by remember { mutableIntStateOf(0) }

    // Top-1 CPU app
    var topAppName by remember { mutableStateOf<String?>(null) }
    var topAppPct by remember { mutableFloatStateOf(0f) }
    var topAppShizukuReady by remember { mutableStateOf(false) }

    val device = remember { DeviceInfo.readDevice() }
    val displaySnap = remember(activity) { activity?.let { DeviceInfo.readDisplay(it) } }

    LaunchedEffect(Unit) {
        // Prime sampler off-thread so first display has a real delta
        withContext(Dispatchers.IO) { CpuReader.read(context, "overview") }
        var lastRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
        var lastTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
        var lastWall = SystemClock.elapsedRealtime()
        var tick = 0
        while (true) {
            // All blocking reads on IO; Shizuku shell + /proc IO would stall the UI otherwise
            val cpu = withContext(Dispatchers.IO) { CpuReader.read(context, "overview") }
            val ram = withContext(Dispatchers.IO) { MemoryReader.readRam(context) }
            val batt = withContext(Dispatchers.IO) { BatteryReader.read(context) }
            val storage = withContext(Dispatchers.IO) { MemoryReader.readStorage() }
            cpuPct = cpu.totalPercent
            cpuSource = cpu.source
            perCorePct = cpu.perCorePercent
            ramPct = ram.percent
            ramUsed = ram.usedBytes
            ramTotal = ram.totalBytes
            batteryPct = batt.percent
            batteryTemp = batt.temperatureC
            batteryWatts = batt.wattsNow
            batteryCharging = batt.isCharging
            storagePct = storage.internalPercent

            // Netzwerk-Delta
            val curRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
            val curTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
            val nowWall = SystemClock.elapsedRealtime()
            val dt = (nowWall - lastWall).coerceAtLeast(1L)
            downKbs = ((curRx - lastRx).coerceAtLeast(0L) * 1000f) / dt / 1024f
            upKbs = ((curTx - lastTx).coerceAtLeast(0L) * 1000f) / dt / 1024f
            rxTotal = curRx
            txTotal = curTx
            lastRx = curRx
            lastTx = curTx
            lastWall = nowWall

            // Top-App nur alle 3 Ticks (dumpsys cpuinfo ist teuer)
            tick++
            if (tick % 3 == 0) {
                topAppShizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
                if (topAppShizukuReady) {
                    val top = withContext(Dispatchers.IO) { TopCpuReader.read(context, limit = 5) }
                    val first = top.firstOrNull { !it.processName.startsWith("kworker") }
                    topAppName = first?.processName
                    topAppPct = first?.cpuPercent ?: 0f
                }
            }

            delay(1000)
        }
    }

    // FPS: zähle echte Choreographer-Frames, alle 1s als FPS publishen
    DisposableEffect(Unit) {
        val choreo = Choreographer.getInstance()
        var frames = 0
        var lastNanos = System.nanoTime()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frames++
                if (frameTimeNanos - lastNanos >= 1_000_000_000L) {
                    fps = frames
                    frames = 0
                    lastNanos = frameTimeNanos
                }
                choreo.postFrameCallback(this)
            }
        }
        choreo.postFrameCallback(callback)
        onDispose { choreo.removeFrameCallback(callback) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        updateAvailable?.let { release ->
            com.tamerin.sysmonitor.ui.components.UpdateBanner(
                fromVersion = com.tamerin.sysmonitor.BuildConfig.VERSION_NAME,
                toVersion = release.versionName,
                onClick = onOpenUpdate,
                onDismiss = {
                    com.tamerin.sysmonitor.update.UpdatePrefs.dismissVersion(context, release.versionName)
                    updateAvailable = null
                }
            )
        }

        // ===== HERO =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentBubble, Color.Transparent),
                        radius = 600f
                    )
                )
                .padding(vertical = 12.dp)
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                SectionEyebrow("Live Dashboard")
                Spacer(Modifier.height(8.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                            append("${device.manufacturer}\n")
                        }
                        withStyle(SpanStyle(color = Accent)) {
                            append(device.model)
                        }
                    },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 32.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android ${device.androidVersion} · SDK ${device.sdk} · Patch ${device.securityPatch}",
                    color = OnSurfaceMuted,
                    fontSize = 13.sp
                )
                displaySnap?.let {
                    Text(
                        "${it.widthPx} × ${it.heightPx} px · ${"%.0f".format(it.refreshRateHz)} Hz",
                        color = OnSurfaceMuted,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // ===== GAUGES =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularGauge(
                percent = cpuPct,
                label = "CPU",
                sublabel = when (cpuSource) {
                    "shizuku" -> "System"
                    "direct" -> "System"
                    "process" -> "nur App"
                    else -> null
                }
            )
            CircularGauge(percent = ramPct, label = "RAM")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularGauge(
                percent = batteryPct,
                label = if (batteryCharging) "Lädt ${"%.1f".format(batteryWatts)} W"
                        else "Akku",
                sublabel = "${"%.1f".format(batteryTemp)} °C"
            )
            CircularGauge(percent = storagePct, label = "Speicher")
        }

        StatCard("CPU pro Kern") {
            if (perCorePct.isEmpty()) {
                Text("Sampler wärmt auf…", color = OnSurfaceMuted, fontSize = 12.sp)
            } else {
                PerCoreBars(perCorePct)
                Spacer(Modifier.height(6.dp))
                val avg = perCorePct.average().toFloat()
                Text(
                    "Ø ${"%.0f".format(avg)} %  ·  ${perCorePct.size} Kerne",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
            }
        }

        StatCard("Netzwerk live") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NetCol("↓ Download", downKbs)
                NetCol("↑ Upload", upKbs)
            }
            Spacer(Modifier.height(8.dp))
            KeyValueRow("Empfangen gesamt", rxTotal.formatBytes())
            KeyValueRow("Gesendet gesamt", txTotal.formatBytes())
        }

        StatCard("Performance") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("FPS (App)", color = OnSurfaceMuted, fontSize = 12.sp)
                    val fpsColor = when {
                        fps >= 110 -> GaugeGreen
                        fps >= 55 -> Accent
                        fps >= 28 -> GaugeOrange
                        else -> GaugeRed
                    }
                    Text(
                        if (fps == 0) "—" else "$fps",
                        color = fpsColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Top-CPU-App", color = OnSurfaceMuted, fontSize = 12.sp)
                    val label = when {
                        !topAppShizukuReady -> "Shizuku nötig"
                        topAppName == null -> "—"
                        else -> shortenProcessName(topAppName!!)
                    }
                    Text(
                        label,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (topAppShizukuReady && topAppName != null) {
                        Text(
                            "${"%.1f".format(topAppPct)} %",
                            color = Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        StatCard("Speicher kompakt") {
            KeyValueRow("RAM verwendet", ramUsed.formatBytes())
            KeyValueRow("RAM gesamt", ramTotal.formatBytes())
            KeyValueRow("RAM-Auslastung", "${ramPct.toInt()} %")
            KeyValueRow("Interner Speicher", "${storagePct.toInt()} %")
        }
    }
}

@Composable
private fun PerCoreBars(values: List<Float>) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { idx, pct ->
            val p = pct.coerceIn(0f, 100f)
            val color = when {
                p >= 85f -> GaugeRed
                p >= 60f -> GaugeOrange
                p >= 30f -> Accent
                else -> GaugeGreen
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x22FFFFFF), RoundedCornerShape(3.dp))
                        .height(40.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((40f * (p / 100f)).dp.coerceAtLeast(2.dp))
                            .background(color, RoundedCornerShape(3.dp))
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "$idx",
                    color = OnSurfaceMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun NetCol(label: String, kbs: Float) {
    val (value, unit) = formatRate(kbs)
    Column {
        Text(label, color = OnSurfaceMuted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = Accent,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(4.dp))
            Text(
                unit,
                color = OnSurfaceMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

private fun formatRate(kbs: Float): Pair<String, String> = when {
    kbs >= 1024f -> "%.2f".format(kbs / 1024f) to "MB/s"
    kbs >= 1f -> "%.1f".format(kbs) to "KB/s"
    else -> "0" to "KB/s"
}

private fun shortenProcessName(raw: String): String {
    // "com.foo.bar:service" → "bar:service", "system_server" bleibt
    val base = raw.substringBefore(":")
    val suffix = raw.substringAfter(":", "")
    val last = base.substringAfterLast('.')
    return if (suffix.isEmpty()) last else "$last:$suffix"
}
