package com.tamerin.sysmonitor.ui.screens

import android.app.Activity
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.BatteryReader
import com.tamerin.sysmonitor.data.CpuReader
import com.tamerin.sysmonitor.data.DeviceInfo
import com.tamerin.sysmonitor.data.MemoryReader
import com.tamerin.sysmonitor.data.formatBytes
import com.tamerin.sysmonitor.ui.components.CircularGauge
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.SectionEyebrow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentBubble
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.delay

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
    var ramPct by remember { mutableFloatStateOf(0f) }
    var ramUsed by remember { mutableLongStateOf(0L) }
    var ramTotal by remember { mutableLongStateOf(0L) }
    var batteryPct by remember { mutableFloatStateOf(0f) }
    var batteryTemp by remember { mutableFloatStateOf(0f) }
    var batteryWatts by remember { mutableFloatStateOf(0f) }
    var batteryCharging by remember { mutableStateOf(false) }
    var storagePct by remember { mutableFloatStateOf(0f) }

    val device = remember { DeviceInfo.readDevice() }
    val displaySnap = remember(activity) { activity?.let { DeviceInfo.readDisplay(it) } }

    LaunchedEffect(Unit) {
        CpuReader.read()
        while (true) {
            val cpu = CpuReader.read()
            val ram = MemoryReader.readRam(context)
            val batt = BatteryReader.read(context)
            val storage = MemoryReader.readStorage()
            cpuPct = cpu.totalPercent
            ramPct = ram.percent
            ramUsed = ram.usedBytes
            ramTotal = ram.totalBytes
            batteryPct = batt.percent
            batteryTemp = batt.temperatureC
            batteryWatts = batt.wattsNow
            batteryCharging = batt.isCharging
            storagePct = storage.internalPercent
            delay(1000)
        }
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
            CircularGauge(percent = cpuPct, label = "CPU")
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

        StatCard("Speicher kompakt") {
            KeyValueRow("RAM verwendet", ramUsed.formatBytes())
            KeyValueRow("RAM gesamt", ramTotal.formatBytes())
            KeyValueRow("RAM-Auslastung", "${ramPct.toInt()} %")
            KeyValueRow("Interner Speicher", "${storagePct.toInt()} %")
        }
    }
}
