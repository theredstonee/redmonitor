package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tamerin.sysmonitor.data.MemoryReader
import com.tamerin.sysmonitor.data.RamSnapshot
import com.tamerin.sysmonitor.data.StorageSnapshot
import com.tamerin.sysmonitor.data.formatBytes
import com.tamerin.sysmonitor.ui.components.CircularGauge
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.PercentBar
import com.tamerin.sysmonitor.ui.components.StatCard
import kotlinx.coroutines.delay

@Composable
fun RamScreen() {
    val context = LocalContext.current
    var ram by remember {
        mutableStateOf(RamSnapshot(0, 0, 0, 0f, false, 0))
    }
    var storage by remember {
        mutableStateOf(StorageSnapshot(0, 0, 0f))
    }

    // RAM (1.5 s) + Storage (15 s) as independent loops on IO dispatcher.
    LaunchedEffect(Unit) {
        while (true) {
            ram = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                MemoryReader.readRam(context)
            }
            delay(1500)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            storage = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                MemoryReader.readStorage()
            }
            delay(15_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularGauge(percent = ram.percent, label = "RAM", size = 160.dp)
            CircularGauge(percent = storage.internalPercent, label = "Speicher", size = 160.dp)
        }

        StatCard("Arbeitsspeicher") {
            PercentBar(
                "Belegt",
                ram.percent,
                valueText = "${ram.usedBytes.formatBytes()} / ${ram.totalBytes.formatBytes()}"
            )
            Spacer(Modifier.height(12.dp))
            KeyValueRow("Verfügbar", ram.availableBytes.formatBytes())
            KeyValueRow("Low-Memory-Schwelle", ram.threshold.formatBytes())
            KeyValueRow("Low Memory?", if (ram.lowMemory) "Ja" else "Nein")
        }

        StatCard("Interner Speicher") {
            PercentBar(
                "Belegt",
                storage.internalPercent,
                valueText = "${(storage.internalTotal - storage.internalAvailable).formatBytes()} / ${storage.internalTotal.formatBytes()}"
            )
            Spacer(Modifier.height(12.dp))
            KeyValueRow("Frei", storage.internalAvailable.formatBytes())
            KeyValueRow("Gesamt", storage.internalTotal.formatBytes())
        }
    }
}
