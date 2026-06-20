package com.tamerin.sysmonitor.ui.screens

import android.app.Activity
import android.net.TrafficStats
import android.os.SystemClock
import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
import com.tamerin.sysmonitor.data.DeviceInfoSnapshot
import com.tamerin.sysmonitor.data.DisplaySnapshot
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
fun OverviewScreen(onOpenUpdate: () -> Unit = {}, onOpenOemSetup: () -> Unit = {}) {
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
    var gpuPct by remember { mutableStateOf<Float?>(null) } // null = source nicht lesbar

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

    // === Lifecycle-aware parallel sampler loops ===
    // Each metric runs in its own coroutine on its own dispatcher with its own cadence.
    // repeatOnLifecycle(RESUMED) pausiert ALLE Sampler sobald die App pausiert (Screen
    // aus, andere App, Lock) → kein CPU/Akku-Verbrauch im Hintergrund.
    val lifecycleOwner = LocalLifecycleOwner.current

    // CPU + per-core (1 s, IO)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            withContext(Dispatchers.IO) { CpuReader.read(context, "overview") }
            while (true) {
                val cpu = withContext(Dispatchers.IO) { CpuReader.read(context, "overview") }
                cpuPct = cpu.totalPercent
                cpuSource = cpu.source
                perCorePct = cpu.perCorePercent
                delay(1000)
            }
        }
    }

    // RAM (1.5 s, IO)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val ram = withContext(Dispatchers.IO) { MemoryReader.readRam(context) }
                ramPct = ram.percent
                ramUsed = ram.usedBytes
                ramTotal = ram.totalBytes
                delay(1500)
            }
        }
    }

    // Battery (3 s, IO)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val batt = withContext(Dispatchers.IO) { BatteryReader.read(context) }
                batteryPct = batt.percent
                batteryTemp = batt.temperatureC
                batteryWatts = batt.wattsNow
                batteryCharging = batt.isCharging
                delay(3000)
            }
        }
    }

    // Storage (30 s, IO)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                storagePct = withContext(Dispatchers.IO) { MemoryReader.readStorage() }.internalPercent
                delay(30_000)
            }
        }
    }

    // GPU usage (1.5 s, IO)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                gpuPct = withContext(Dispatchers.IO) {
                    com.tamerin.sysmonitor.data.GpuUsageReader.read(context)
                }
                delay(1500)
            }
        }
    }

    // Network throughput (1 s, Default)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            withContext(Dispatchers.Default) {
                var lastRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
                var lastTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
                var lastWall = SystemClock.elapsedRealtime()
                while (true) {
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
                    delay(1000)
                }
            }
        }
    }

    // Top CPU app (3 s, IO — most expensive read because dumpsys cpuinfo)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val ready = withContext(Dispatchers.IO) {
                    ShizukuHelper.state(context) == ShizukuHelper.State.Ready
                }
                topAppShizukuReady = ready
                if (ready) {
                    val top = withContext(Dispatchers.IO) { TopCpuReader.read(context, limit = 5) }
                    val first = top.firstOrNull { !it.processName.startsWith("kworker") }
                    topAppName = first?.processName
                    topAppPct = first?.cpuPercent ?: 0f
                }
                delay(3000)
            }
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

        com.tamerin.sysmonitor.ui.components.OemHintBanner(onOpenSetup = onOpenOemSetup)

        HeroCard(device, displaySnap)

        TopGauges(cpuPct = cpuPct, cpuSource = cpuSource, ramPct = ramPct)
        BottomGauges(
            batteryPct = batteryPct,
            batteryCharging = batteryCharging,
            batteryWatts = batteryWatts,
            batteryTemp = batteryTemp,
            gpuPct = gpuPct
        )

        PerCoreCard(perCorePct)
        NetworkLiveCard(downKbs = downKbs, upKbs = upKbs, rxTotal = rxTotal, txTotal = txTotal)
        PerformanceCard(
            fps = fps,
            topAppName = topAppName,
            topAppPct = topAppPct,
            topAppShizukuReady = topAppShizukuReady
        )
        StorageCompactCard(ramUsed = ramUsed, ramTotal = ramTotal, ramPct = ramPct, storagePct = storagePct)
    }
}

// ===== Stable per-card composables =====
// Each takes ONLY the primitives it renders → Strong-Skipping (Compose 2.0+)
// keeps the others from recomposing when unrelated state ticks (every 1-3 s).

@Composable
private fun HeroCard(device: DeviceInfoSnapshot, displaySnap: DisplaySnapshot?) {
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
                color = OnSurfaceMuted, fontSize = 13.sp
            )
            displaySnap?.let {
                Text(
                    "${it.widthPx} × ${it.heightPx} px · ${"%.0f".format(it.refreshRateHz)} Hz",
                    color = OnSurfaceMuted, fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun TopGauges(cpuPct: Float, cpuSource: String, ramPct: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularGauge(
            percent = cpuPct,
            label = "CPU",
            sublabel = when (cpuSource) {
                "shizuku", "direct" -> "System"
                "process" -> "nur App"
                else -> null
            }
        )
        CircularGauge(percent = ramPct, label = "RAM")
    }
}

@Composable
private fun BottomGauges(
    batteryPct: Float,
    batteryCharging: Boolean,
    batteryWatts: Float,
    batteryTemp: Float,
    gpuPct: Float?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularGauge(
            percent = batteryPct,
            label = if (batteryCharging) "Lädt ${"%.1f".format(batteryWatts)} W" else "Akku",
            sublabel = "${"%.1f".format(batteryTemp)} °C",
            colorOverride = com.tamerin.sysmonitor.ui.theme.batteryGaugeColor(batteryPct)
        )
        CircularGauge(
            percent = gpuPct ?: 0f,
            label = "GPU",
            sublabel = if (gpuPct == null) "n/a · Shizuku?" else null
        )
    }
}

@Composable
private fun PerCoreCard(perCorePct: List<Float>) {
    StatCard("CPU pro Kern") {
        if (perCorePct.isEmpty()) {
            Text("Sampler wärmt auf…", color = OnSurfaceMuted, fontSize = 12.sp)
        } else {
            PerCoreBars(perCorePct)
            Spacer(Modifier.height(6.dp))
            val avg = remember(perCorePct) { perCorePct.average().toFloat() }
            Text(
                "Ø ${"%.0f".format(avg)} %  ·  ${perCorePct.size} Kerne",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun NetworkLiveCard(downKbs: Float, upKbs: Float, rxTotal: Long, txTotal: Long) {
    StatCard("Netzwerk live") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NetCol("↓ Download", downKbs)
            NetCol("↑ Upload", upKbs)
        }
        Spacer(Modifier.height(8.dp))
        KeyValueRow("Empfangen gesamt", remember(rxTotal) { rxTotal.formatBytes() })
        KeyValueRow("Gesendet gesamt", remember(txTotal) { txTotal.formatBytes() })
    }
}

@Composable
private fun PerformanceCard(
    fps: Int,
    topAppName: String?,
    topAppPct: Float,
    topAppShizukuReady: Boolean
) {
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
                    else -> shortenProcessName(topAppName)
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
}

@Composable
private fun StorageCompactCard(ramUsed: Long, ramTotal: Long, ramPct: Float, storagePct: Float) {
    StatCard("Speicher kompakt") {
        KeyValueRow("RAM verwendet", remember(ramUsed) { ramUsed.formatBytes() })
        KeyValueRow("RAM gesamt", remember(ramTotal) { ramTotal.formatBytes() })
        KeyValueRow("RAM-Auslastung", "${ramPct.toInt()} %")
        KeyValueRow("Interner Speicher", "${storagePct.toInt()} %")
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
