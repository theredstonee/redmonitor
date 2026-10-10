package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.data.ThermalReader
import com.tamerin.sysmonitor.data.ThermalZone
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.delay

@Composable
fun ThermalScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var zones by remember { mutableStateOf<List<ThermalZone>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            zones = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ThermalReader.read(context)
            }
            delay(1500)
        }
    }

    if (zones.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            StatCard(stringResource(R.string.pwr_thermal_zones_title)) {
                Text(
                    stringResource(R.string.pwr_thermal_none),
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
            StatCard(stringResource(R.string.pwr_thermal_overview)) {
                KeyValueRow(stringResource(R.string.pwr_thermal_zones_total), zones.size.toString())
                val hottest = zones.filter { !it.tempCelsius.isNaN() }.maxByOrNull { it.tempCelsius }
                if (hottest != null) {
                    KeyValueRow(
                        stringResource(R.string.pwr_thermal_hottest),
                        "${hottest.type}: ${"%.1f".format(hottest.tempCelsius)} °C"
                    )
                }
            }
        }
        items(zones, key = { it.index }) { z ->
            StatCard(stringResource(R.string.pwr_thermal_zone_title, z.index, z.type)) {
                KeyValueRow(
                    stringResource(R.string.pwr_thermal_temperature),
                    if (z.tempCelsius.isNaN()) "—" else "${"%.1f".format(z.tempCelsius)} °C"
                )
            }
        }
    }
}
