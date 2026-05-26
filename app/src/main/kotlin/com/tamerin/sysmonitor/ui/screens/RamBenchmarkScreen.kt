package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.benchmark.RamBenchmark
import com.tamerin.sysmonitor.benchmark.RamBenchmarkResult
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.launch

@Composable
fun RamBenchmarkScreen() {
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RamBenchmarkResult?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard("RAM-Speed") {
            Text(
                "Misst Schreiben, Lesen und Kopieren von 64 MB im Arbeitsspeicher.",
                color = OnSurfaceMuted,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = !running,
                onClick = {
                    running = true
                    result = null
                    scope.launch {
                        result = RamBenchmark.run()
                        running = false
                    }
                }
            ) {
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Läuft…")
                } else {
                    Text(if (result == null) "Test starten" else "Erneut testen")
                }
            }
        }

        result?.let { r ->
            StatCard("Ergebnis (${r.bufferSizeMb} MB Puffer)") {
                KeyValueRow("Schreiben", "${"%.0f".format(r.writeMbPerSec)} MB/s")
                KeyValueRow("Lesen", "${"%.0f".format(r.readMbPerSec)} MB/s")
                KeyValueRow("Kopieren", "${"%.0f".format(r.copyMbPerSec)} MB/s")
            }
        }
    }
}
