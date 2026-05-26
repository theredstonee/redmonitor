package com.tamerin.sysmonitor.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.SensorRegistry
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

@Composable
fun SensorsScreen(onSelect: (Int) -> Unit = {}) {
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val allSensors = remember { sensorManager.getSensorList(Sensor.TYPE_ALL) }
    val live = remember {
        mutableStateMapOf<Int, FloatArray>()
    }

    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                live[event.sensor.type] = event.values.copyOf()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        allSensors.forEach { sensor ->
            sensorManager.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StatCard("Übersicht") {
                KeyValueRow("Sensoren gesamt", allSensors.size.toString())
                KeyValueRow("Aktive Live-Werte", live.size.toString())
                Text(
                    "Tippe einen Sensor an, um Detail-Verlauf und alle Specs zu sehen.",
                    color = OnSurfaceMuted,
                    fontSize = 11.sp
                )
            }
        }

        items(allSensors, key = { it.type.toString() + it.name }) { sensor ->
            val values = live[sensor.type]
            val label = SensorRegistry.typeLabel(sensor.type)
            val unit = SensorRegistry.unit(sensor.type)
            Box(modifier = Modifier.clickable { onSelect(sensor.type) }) {
                StatCard(label) {
                    Text(
                        sensor.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (values != null && values.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            values.take(3).joinToString("  ") { "${"%.2f".format(it)}$unit" },
                            color = OnSurfaceMuted,
                            fontSize = 12.sp
                        )
                    } else {
                        Text("warte…", color = OnSurfaceMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
