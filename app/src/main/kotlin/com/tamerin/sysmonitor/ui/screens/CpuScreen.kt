package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.CpuReader
import com.tamerin.sysmonitor.data.CpuSnapshot
import com.tamerin.sysmonitor.data.SocIdentifier
import com.tamerin.sysmonitor.data.TopCpuReader
import com.tamerin.sysmonitor.data.TopProc
import com.tamerin.sysmonitor.ui.components.CircularGauge
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.PercentBar
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun CpuScreen() {
    val context = LocalContext.current
    val soc = remember { SocIdentifier.identify() }
    var snap by remember {
        mutableStateOf(
            CpuSnapshot(0f, emptyList(), emptyList(), emptyList(), emptyList(), "?", 0, "", emptyList(), "", "—")
        )
    }
    var topProcs by remember { mutableStateOf<List<TopProc>>(emptyList()) }

    LaunchedEffect(Unit) {
        // Prime the delta sampler
        withContext(Dispatchers.IO) { CpuReader.read(context, "cpu-screen") }
        var tick = 0
        while (true) {
            snap = withContext(Dispatchers.IO) { CpuReader.read(context, "cpu-screen") }
            // Adaptive refresh: every 3rd tick (~3s) refresh top processes — dumpsys is heavier
            if (tick % 3 == 0) {
                topProcs = withContext(Dispatchers.IO) { TopCpuReader.read(context, "cpu-screen") }
            }
            tick++
            // 500ms if CPU > 30%, else 1s
            delay(if (snap.totalPercent > 30f) 500L else 1000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard("Prozessor") {
            Text(
                soc.fullLabel,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(soc.manufacturer, color = OnSurfaceMuted, fontSize = 13.sp)
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularGauge(percent = snap.totalPercent, label = "Gesamt-Last", size = 180.dp)
        }
        Text(
            "Datenquelle: ${snap.source}",
            color = OnSurfaceMuted, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth()
        )

        StatCard("Pro Kern") {
            if (snap.perCorePercent.isEmpty()) {
                Text(
                    "Keine Per-Core-Daten verfügbar. Auf neueren Android-Versionen ist /proc/stat ohne shell-Zugriff blockiert — aktiviere Shizuku für echte Werte.",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
            } else {
                snap.perCorePercent.forEachIndexed { idx, pct ->
                    val freq = snap.coreFrequenciesKHz.getOrNull(idx) ?: 0L
                    val maxF = snap.coreMaxFreqKHz.getOrNull(idx) ?: 0L
                    val sub = buildString {
                        if (freq > 0) append(" · ${freq / 1000} MHz")
                        if (maxF > 0) append(" / max ${maxF / 1000}")
                    }
                    PercentBar(label = "Core $idx$sub", percent = pct)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        StatCard("Top-CPU-Prozesse") {
            if (topProcs.isEmpty()) {
                Text(
                    if (snap.source == "shizuku" || snap.source == "system")
                        "Lade via dumpsys cpuinfo…"
                    else "Top-Prozesse brauchen Shizuku (dumpsys cpuinfo).",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
            } else {
                topProcs.forEach { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Text(
                            "${"%5.1f".format(p.cpuPercent)}%",
                            color = AccentSoft,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(60.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                p.processName,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                            Text(
                                "PID ${p.pid}  ·  user ${p.userPct}%  ·  kernel ${p.kernelPct}%",
                                color = OnSurfaceMuted, fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        StatCard("Prozessor-Info") {
            KeyValueRow("Kerne", snap.coreCount.toString())
            KeyValueRow("Architektur (primär)", snap.abi)
            if (snap.supportedAbis.size > 1) {
                KeyValueRow("Unterstützte ABIs", snap.supportedAbis.joinToString(", "))
            }
            KeyValueRow("Governor", snap.governor)
            KeyValueRow("Hardware", snap.hardware)
        }

        if (snap.coreMinFreqKHz.any { it > 0 } || snap.coreMaxFreqKHz.any { it > 0 }) {
            StatCard("Frequenz-Range pro Kern") {
                snap.perCorePercent.indices.forEach { idx ->
                    val minF = snap.coreMinFreqKHz.getOrNull(idx) ?: 0L
                    val maxF = snap.coreMaxFreqKHz.getOrNull(idx) ?: 0L
                    if (minF > 0 || maxF > 0) {
                        KeyValueRow("Core $idx", "${minF / 1000} – ${maxF / 1000} MHz")
                    }
                }
            }
        }
    }
}
