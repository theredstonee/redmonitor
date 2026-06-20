package com.tamerin.sysmonitor.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

@Composable
fun BarometerScreen() {
    val context = LocalContext.current
    val sm = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { sm.getDefaultSensor(Sensor.TYPE_PRESSURE) }
    var hpa by remember { mutableFloatStateOf(Float.NaN) }
    var seaLevel by remember { mutableStateOf("1013.25") }
    var min by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
    var max by remember { mutableFloatStateOf(Float.NEGATIVE_INFINITY) }

    DisposableEffect(sensor) {
        if (sensor == null) {
            onDispose { }
            return@DisposableEffect onDispose { }
        }
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val v = e.values.firstOrNull() ?: return
                hpa = v
                if (v < min) min = v
                if (v > max) max = v
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm.unregisterListener(l) }
    }

    val sl = seaLevel.toFloatOrNull() ?: SensorManager.PRESSURE_STANDARD_ATMOSPHERE
    val altitude = if (hpa.isNaN()) Float.NaN else SensorManager.getAltitude(sl, hpa)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("Barometer (TYPE_PRESSURE)") {
            if (sensor == null) {
                Text("Kein Drucksensor verbaut. Standardmäßig haben das nur Premium-Phones " +
                    "(Pixel, Samsung S/Note/Fold, iPhone-Gegenstücke).",
                    color = OnSurfaceMuted, fontSize = 12.sp)
                return@StatCard
            }
            KeyValueRow("Druck", if (hpa.isNaN()) "—" else "%.2f hPa".format(hpa))
            KeyValueRow("Min seit Start", if (min.isInfinite()) "—" else "%.2f hPa".format(min))
            KeyValueRow("Max seit Start", if (max.isInfinite()) "—" else "%.2f hPa".format(max))
            KeyValueRow("Sensor", sensor.name)
            KeyValueRow("Vendor", sensor.vendor)
            KeyValueRow("Max-Range", "%.1f hPa".format(sensor.maximumRange))
            KeyValueRow("Auflösung", "%.4f hPa".format(sensor.resolution))
        }

        if (sensor != null) {
            StatCard("Höhe (NN-Druck einstellbar)") {
                OutlinedTextField(
                    value = seaLevel,
                    onValueChange = { seaLevel = it },
                    label = { Text("Reduzierter Luftdruck auf NN (hPa)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (altitude.isNaN()) "—"
                    else "%.1f m über NN".format(altitude),
                    color = Accent,
                    fontSize = 32.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tipp: Aktuellen NN-Druck (QNH) für deinen Ort z.B. via dwd.de oder " +
                        "Aviation-Wetter (METAR) prüfen — Standard 1013.25 hPa ist die ICAO-Atmosphäre.",
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }
        }
    }
}
