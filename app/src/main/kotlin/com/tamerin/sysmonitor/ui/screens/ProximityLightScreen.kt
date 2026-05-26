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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        StatCard("Proximity (Annäherung)") {
            if (proximity == null) {
                Text("Kein Proximity-Sensor verbaut.", color = OnSurfaceMuted, fontSize = 13.sp)
            } else {
                KeyValueRow("Sensor", proximity.name)
                KeyValueRow("Maximaler Wert", "${proximity.maximumRange} cm")
                KeyValueRow("Aktueller Wert", if (proxValue.isNaN()) "—" else "${"%.2f".format(proxValue)} cm")
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
                        if (proxNear) "OBJEKT NAH" else "FREI",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Halte den Finger vorne übers Display (oberhalb des Earpiece). Bei Annäherung leuchtet das Feld grün.",
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }
        }

        StatCard("Lichtsensor") {
            if (light == null) {
                Text("Kein Lichtsensor verbaut.", color = OnSurfaceMuted, fontSize = 13.sp)
            } else {
                KeyValueRow("Sensor", light.name)
                KeyValueRow("Aktuell", "${"%.1f".format(lightLx)} lx")
                KeyValueRow("Gesehenes Maximum", "${"%.0f".format(lightMax)} lx")
                Spacer(Modifier.height(8.dp))
                PercentBar(
                    "Helligkeit",
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
                        bucket(lightLx),
                        color = if ((lightLx / lightMax) > 0.5f) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Halte das Telefon ans Fenster und ins Dunkle — der Wert ändert sich live.",
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }
        }
    }
}

private fun bucket(lx: Float): String = when {
    lx < 1f -> "Stockfinster"
    lx < 10f -> "Sehr dunkel"
    lx < 50f -> "Dunkler Raum"
    lx < 200f -> "Wohnraum"
    lx < 500f -> "Helles Zimmer"
    lx < 1000f -> "Büro"
    lx < 5000f -> "Bewölkt draußen"
    lx < 25_000f -> "Tageslicht"
    else -> "Direktes Sonnenlicht"
}
