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
import com.tamerin.sysmonitor.benchmark.ImageBenchResult
import com.tamerin.sysmonitor.benchmark.ImageBenchmark
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.launch

@Composable
fun ImageBenchScreen() {
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ImageBenchResult?>(null) }
    val scope = rememberCoroutineScope()
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard("Bild-Verarbeitung (CPU)") {
            Text(
                "Wendet einen 3×3-Blur sechsmal auf ein 1920×1080-Bild an (ohne Allokationen, rein Integer-Arithmetik).",
                color = OnSurfaceMuted, fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = !running,
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                    running = true
                    result = null
                    scope.launch {
                        result = ImageBenchmark.run()
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
            StatCard("Ergebnis") {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "%.1f".format(r.megapixelsPerSec),
                        color = Accent,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("MPx/s", color = OnSurfaceMuted, fontSize = 14.sp)
                }
                Spacer(Modifier.height(12.dp))
                KeyValueRow("Bildgröße", "${r.width} × ${r.height}")
                KeyValueRow("Durchgänge", "${r.passes}")
                KeyValueRow("Dauer", "${r.durationMs} ms")
            }
        }
    }
}
