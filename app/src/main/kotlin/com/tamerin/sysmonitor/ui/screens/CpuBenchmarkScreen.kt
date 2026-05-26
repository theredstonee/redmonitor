package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.benchmark.CpuBenchmark
import com.tamerin.sysmonitor.benchmark.CpuBenchmarkResult
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.launch

@Composable
fun CpuBenchmarkScreen() {
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CpuBenchmarkResult?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard("CPU-Benchmark") {
            Text(
                "Misst Integer- und Float-Operationen einmal auf einem Kern und dann parallel auf allen Kernen.",
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
                        result = CpuBenchmark.run()
                        running = false
                    }
                }
            ) {
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Läuft… ca. 5 s")
                } else {
                    Text(if (result == null) "Benchmark starten" else "Erneut testen")
                }
            }
        }

        result?.let { r ->
            StatCard("Ergebnis") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ScoreColumn("Single-Core", r.singleScore)
                    ScoreColumn("Multi-Core", r.multiScore)
                }
                Spacer(Modifier.height(12.dp))
                KeyValueRow("Single-Core Ops", "${r.singleCoreOps / 1_000_000} Mio")
                KeyValueRow("Multi-Core Ops", "${r.multiCoreOps / 1_000_000} Mio")
                KeyValueRow("Parallelitäts-Faktor", "${"%.2f".format(r.parallelism)}×")
                KeyValueRow("Test-Dauer", "${r.durationMs} ms")
            }
        }
    }
}

@Composable
private fun ScoreColumn(label: String, score: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(score.toString(), color = Accent, fontWeight = FontWeight.Bold, fontSize = 36.sp)
        Text(label, color = OnSurfaceMuted, fontSize = 12.sp)
    }
}
