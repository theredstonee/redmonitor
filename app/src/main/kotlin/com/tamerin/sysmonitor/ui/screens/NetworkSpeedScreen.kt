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
import com.tamerin.sysmonitor.benchmark.NetworkSpeedResult
import com.tamerin.sysmonitor.benchmark.NetworkSpeedTest
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.launch

@Composable
fun NetworkSpeedScreen() {
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<NetworkSpeedResult?>(null) }
    var progressMb by remember { mutableDoubleStateOf(0.0) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard("Netzwerk Download-Speed") {
            Text(
                "Lädt 25 MB von Cloudflare und misst die effektive Bandbreite in Mbps.",
                color = OnSurfaceMuted, fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = !running,
                onClick = {
                    running = true
                    result = null
                    error = null
                    progressMb = 0.0
                    scope.launch {
                        try {
                            result = NetworkSpeedTest.run { progressMb = it }
                        } catch (e: Exception) {
                            error = "Fehler: ${e.message}"
                        }
                        running = false
                    }
                }
            ) {
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("${"%.1f".format(progressMb)} MB / 25 MB")
                } else Text(if (result == null) "Test starten" else "Erneut testen")
            }
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = androidx.compose.ui.graphics.Color.Red, fontSize = 12.sp)
            }
        }

        result?.let { r ->
            StatCard("Ergebnis") {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "%.1f".format(r.mbps),
                        color = Accent,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Mbps", color = OnSurfaceMuted, fontSize = 14.sp)
                }
                Spacer(Modifier.height(12.dp))
                KeyValueRow("Heruntergeladen", "${"%.2f".format(r.downloadedMb)} MB")
                KeyValueRow("Dauer", "${r.durationMs} ms")
                KeyValueRow("Endpoint", r.endpoint)
            }
        }
    }
}
