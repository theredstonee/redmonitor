package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.ThermalReader
import com.tamerin.sysmonitor.data.ThermalZone
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.delay

@Composable
fun ThermalScreen() {
    var zones by remember { mutableStateOf<List<ThermalZone>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            zones = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ThermalReader.read()
            }
            delay(1500)
        }
    }

    if (zones.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            StatCard("Thermalzonen") {
                Text(
                    "Auf diesem Gerät sind keine Thermalzonen lesbar (Kernel-Restriktionen oder Hersteller hat Zugriff gesperrt).",
                    color = OnSurfaceMuted,
                    fontSize = 13.sp
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            StatCard("Übersicht") {
                KeyValueRow("Zonen gesamt", zones.size.toString())
                val hottest = zones.filter { !it.tempCelsius.isNaN() }.maxByOrNull { it.tempCelsius }
                if (hottest != null) {
                    KeyValueRow(
                        "Heißeste Zone",
                        "${hottest.type}: ${"%.1f".format(hottest.tempCelsius)} °C"
                    )
                }
            }
        }
        items(zones, key = { it.index }) { z ->
            StatCard("Zone ${z.index}: ${z.type}") {
                KeyValueRow(
                    "Temperatur",
                    if (z.tempCelsius.isNaN()) "—" else "${"%.1f".format(z.tempCelsius)} °C"
                )
            }
        }
    }
}
