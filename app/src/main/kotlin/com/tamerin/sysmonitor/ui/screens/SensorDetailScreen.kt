package com.tamerin.sysmonitor.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.SensorRegistry
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.Sparkline
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

@Composable
fun SensorDetailScreen(sensorType: Int) {
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val sensor = remember { sensorManager.getDefaultSensor(sensorType) }
    val unit = remember { SensorRegistry.unit(sensorType) }
    val label = remember { SensorRegistry.typeLabel(sensorType) }

    var current by remember { mutableStateOf(FloatArray(0)) }
    val history = remember { mutableStateListOf<FloatArray>() }
    var samples by remember { mutableLongStateOf(0L) }

    DisposableEffect(sensorType) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                current = event.values.copyOf()
                history.add(event.values.copyOf())
                if (history.size > 240) history.removeAt(0)
                samples++
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    if (sensor == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            StatCard(label) {
                Text("Kein Sensor dieses Typs vorhanden.", color = OnSurfaceMuted, fontSize = 13.sp)
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("Live") {
            if (current.isEmpty()) {
                Text("warte auf Daten…", color = OnSurfaceMuted, fontSize = 13.sp)
            } else {
                current.forEachIndexed { idx, v ->
                    val axis = when (idx) { 0 -> "X"; 1 -> "Y"; 2 -> "Z"; else -> "Achse $idx" }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(axis, color = OnSurfaceMuted, fontSize = 14.sp, modifier = Modifier.width(40.dp))
                        Text(
                            "${"%.4f".format(v)} $unit",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        if (history.size > 1) {
            val axes = current.size.coerceAtMost(3)
            val colors = listOf(Accent, GaugeGreen, GaugeRed)
            StatCard("Verlauf (letzte ${history.size} Samples)") {
                for (axis in 0 until axes) {
                    val axisValues = history.map { it.getOrNull(axis) ?: 0f }
                    Text(
                        "Achse ${"XYZ".getOrNull(axis) ?: '?'}: min ${"%.3f".format(axisValues.min())}  ·  max ${"%.3f".format(axisValues.max())}",
                        color = OnSurfaceMuted,
                        fontSize = 11.sp
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(64.dp)) {
                        Sparkline(values = axisValues, color = colors[axis])
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        StatCard("Sensor-Specs") {
            KeyValueRow("Name", sensor.name)
            KeyValueRow("Hersteller", sensor.vendor)
            KeyValueRow("Typ-ID", sensor.type.toString())
            KeyValueRow("Maximal-Range", "${sensor.maximumRange} $unit")
            KeyValueRow("Auflösung", "${sensor.resolution} $unit")
            KeyValueRow("Power", "${sensor.power} mA")
            KeyValueRow("Min-Verzögerung", "${sensor.minDelay} µs")
            KeyValueRow("Max-Verzögerung", "${sensor.maxDelay} µs")
            KeyValueRow("FIFO max", sensor.fifoMaxEventCount.toString())
            KeyValueRow("FIFO reserviert", sensor.fifoReservedEventCount.toString())
            KeyValueRow("Wake-Up", if (sensor.isWakeUpSensor) "Ja" else "Nein")
            KeyValueRow("Samples empfangen", samples.toString())
        }
    }
}
