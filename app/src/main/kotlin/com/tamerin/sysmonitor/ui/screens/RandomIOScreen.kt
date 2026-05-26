package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.benchmark.RandomIOBenchmark
import com.tamerin.sysmonitor.benchmark.RandomIOResult
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.launch

@Composable
fun RandomIOScreen() {
    val context = LocalContext.current
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RandomIOResult?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard("Random I/O (4K)") {
            Text(
                "Misst zufälliges Lesen und Schreiben mit 4 KB Blöcken. Diese Werte sind wichtiger als sequenziell, weil Apps und das System fast nur so zugreifen.",
                color = OnSurfaceMuted, fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = !running,
                onClick = {
                    running = true
                    result = null
                    scope.launch {
                        result = runCatching { RandomIOBenchmark.run(context) }.getOrNull()
                        running = false
                    }
                }
            ) {
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Läuft…")
                } else Text(if (result == null) "Test starten" else "Erneut testen")
            }
        }

        result?.let { r ->
            StatCard("Ergebnis (${r.testFileSizeMb} MB Datei)") {
                KeyValueRow("Random Read IOPS", "${r.read4kIops}")
                KeyValueRow("Random Write IOPS", "${r.write4kIops}")
                KeyValueRow("Random Read", "${"%.2f".format(r.readMbPerSec)} MB/s")
                KeyValueRow("Random Write", "${"%.2f".format(r.writeMbPerSec)} MB/s")
            }
        }
    }
}
