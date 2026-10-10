package com.tamerin.sysmonitor.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.PercentBar
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

@Composable
fun ProximityLightScreen() {
    val context = LocalContext.current
    val sm = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val proximity = remember { sm.getDefaultSensor(Sensor.TYPE_PROXIMITY) }
    val light = remember { sm.getDefaultSensor(Sensor.TYPE_LIGHT) }

    var proxValue by remember { mutableFloatStateOf(Float.NaN) }
    var proxNear by remember { mutableStateOf(false) }
    var lightLx by remember { mutableFloatStateOf(0f) }
    var lightMax by remember { mutableFloatStateOf(10_000f) }

    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_PROXIMITY -> {
                        proxValue = event.values[0]
                        proxNear = proxValue < (proximity?.maximumRange ?: 5f)
                    }
                    Sensor.TYPE_LIGHT -> {
                        lightLx = event.values[0]
                        if (lightLx > lightMax) lightMax = lightLx
                    }
                }
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        proximity?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        light?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(listener) }
    }

    val proxColor by animateColorAsState(
        targetValue = if (proxNear) Color(0xFF22C55E) else Color(0xFF1B2330),
        animationSpec = tween(150),
        label = "prox"
    )
    val lightColor by animateColorAsState(
        targetValue = Color(
            red = (lightLx / lightMax).coerceIn(0f, 1f),
            green = (lightLx / lightMax).coerceIn(0f, 1f),
            blue = (lightLx / lightMax).coerceIn(0f, 1f) * 0.7f + 0.2f
        ),
        animationSpec = tween(150),
        label = "light"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard(stringResource(R.string.hw_prox_title)) {
            if (proximity == null) {
                Text(stringResource(R.string.hw_prox_no_sensor), color = OnSurfaceMuted, fontSize = 13.sp)
            } else {
                KeyValueRow("Sensor", proximity.name)
                KeyValueRow(stringResource(R.string.hw_prox_max_value), "${proximity.maximumRange} cm")
                KeyValueRow(stringResource(R.string.hw_prox_current_value), if (proxValue.isNaN()) "—" else "${"%.2f".format(proxValue)} cm")
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(proxColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (proxNear) stringResource(R.string.hw_prox_object_near) else stringResource(R.string.hw_prox_free),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.hw_prox_hint),
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }
        }

        StatCard(stringResource(R.string.hw_light_title)) {
            if (light == null) {
                Text(stringResource(R.string.hw_light_no_sensor), color = OnSurfaceMuted, fontSize = 13.sp)
            } else {
                KeyValueRow("Sensor", light.name)
                KeyValueRow(stringResource(R.string.hw_light_current), "${"%.1f".format(lightLx)} lx")
                KeyValueRow(stringResource(R.string.hw_light_max_seen), "${"%.0f".format(lightMax)} lx")
                Spacer(Modifier.height(8.dp))
                PercentBar(
                    stringResource(R.string.hw_light_brightness),
                    (lightLx / lightMax * 100f).coerceIn(0f, 100f),
                    valueText = "${"%.0f".format(lightLx)} lx"
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(lightColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(bucket(lightLx)),
                        color = if ((lightLx / lightMax) > 0.5f) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.hw_light_hint),
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }
        }
    }
}

private fun bucket(lx: Float): Int = when {
    lx < 1f -> R.string.hw_lux_pitchdark
    lx < 10f -> R.string.hw_lux_verydark
    lx < 50f -> R.string.hw_lux_darkroom
    lx < 200f -> R.string.hw_lux_living
    lx < 500f -> R.string.hw_lux_brightroom
    lx < 1000f -> R.string.hw_lux_office
    lx < 5000f -> R.string.hw_lux_overcast
    lx < 25_000f -> R.string.hw_lux_daylight
    else -> R.string.hw_lux_sunlight
}
