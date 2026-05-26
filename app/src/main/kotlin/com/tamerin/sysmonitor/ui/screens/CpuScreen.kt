package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import com.tamerin.sysmonitor.data.CpuReader
import com.tamerin.sysmonitor.data.CpuSnapshot
import com.tamerin.sysmonitor.data.SocIdentifier
import com.tamerin.sysmonitor.ui.components.CircularGauge
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.PercentBar
import com.tamerin.sysmonitor.ui.components.StatCard
import kotlinx.coroutines.delay

@Composable
fun CpuScreen() {
    val soc = remember { SocIdentifier.identify() }
    var snap by remember {
        mutableStateOf(
            CpuSnapshot(0f, emptyList(), emptyList(), emptyList(), emptyList(), "?", 0, "", emptyList(), "", "—")
        )
    }

    LaunchedEffect(Unit) {
        CpuReader.read()
        while (true) {
            snap = CpuReader.read()
            delay(1000)
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

        StatCard("Pro Kern (%)") {
            snap.perCorePercent.forEachIndexed { idx, pct ->
                val freq = snap.coreFrequenciesKHz.getOrNull(idx) ?: 0L
                val minF = snap.coreMinFreqKHz.getOrNull(idx) ?: 0L
                val maxF = snap.coreMaxFreqKHz.getOrNull(idx) ?: 0L
                val sub = buildString {
                    if (freq > 0) append(" · ${freq / 1000} MHz")
                    if (maxF > 0) append(" / max ${maxF / 1000}")
                }
                PercentBar(
                    label = "Core $idx$sub",
                    percent = pct
                )
                Spacer(Modifier.height(8.dp))
                if (minF > 0 && maxF > 0) {
                    // tiny range hint
                }
            }
            if (snap.perCorePercent.isEmpty()) {
                KeyValueRow("Status", "Erfasse…")
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
                        KeyValueRow(
                            "Core $idx",
                            "${minF / 1000} – ${maxF / 1000} MHz"
                        )
                    }
                }
            }
        }
    }
}
