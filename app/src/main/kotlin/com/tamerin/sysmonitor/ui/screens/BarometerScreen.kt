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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.R
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
        StatCard(stringResource(R.string.hw_baro_title)) {
            if (sensor == null) {
                Text(stringResource(R.string.hw_baro_no_sensor),
                    color = OnSurfaceMuted, fontSize = 12.sp)
                return@StatCard
            }
            KeyValueRow(stringResource(R.string.hw_baro_pressure), if (hpa.isNaN()) "—" else "%.2f hPa".format(hpa))
            KeyValueRow(stringResource(R.string.hw_baro_min_since_start), if (min.isInfinite()) "—" else "%.2f hPa".format(min))
            KeyValueRow(stringResource(R.string.hw_baro_max_since_start), if (max.isInfinite()) "—" else "%.2f hPa".format(max))
            KeyValueRow("Sensor", sensor.name)
            KeyValueRow("Vendor", sensor.vendor)
            KeyValueRow("Max-Range", "%.1f hPa".format(sensor.maximumRange))
            KeyValueRow(stringResource(R.string.hw_baro_resolution), "%.4f hPa".format(sensor.resolution))
        }

        if (sensor != null) {
            StatCard(stringResource(R.string.hw_baro_altitude_title)) {
                OutlinedTextField(
                    value = seaLevel,
                    onValueChange = { seaLevel = it },
                    label = { Text(stringResource(R.string.hw_baro_sealevel_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (altitude.isNaN()) "—"
                    else stringResource(R.string.hw_baro_altitude_value, "%.1f".format(altitude)),
                    color = Accent,
                    fontSize = 32.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.hw_baro_tip),
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }
        }
    }
}
