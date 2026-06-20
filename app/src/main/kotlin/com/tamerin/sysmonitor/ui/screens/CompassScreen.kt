package com.tamerin.sysmonitor.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun CompassScreen() {
    val context = LocalContext.current
    val sm = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val rotation = remember { sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    val mag = remember { sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }
    var azimuth by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }
    var roll by remember { mutableFloatStateOf(0f) }
    var fieldStrength by remember { mutableFloatStateOf(0f) }
    var fieldAccuracy by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        val rotMat = FloatArray(9)
        val orient = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotMat, e.values)
                        SensorManager.getOrientation(rotMat, orient)
                        azimuth = ((Math.toDegrees(orient[0].toDouble()).toFloat()) + 360f) % 360f
                        pitch = Math.toDegrees(orient[1].toDouble()).toFloat()
                        roll = Math.toDegrees(orient[2].toDouble()).toFloat()
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
                        fieldStrength = sqrt(x * x + y * y + z * z)
                    }
                }
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {
                if (s?.type == Sensor.TYPE_MAGNETIC_FIELD) fieldAccuracy = accuracy
            }
        }
        rotation?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        mag?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(listener) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("Kompass") {
            if (rotation == null) {
                Text("ROTATION_VECTOR-Sensor fehlt. Versuche Magnetfeld pur.",
                    color = OnSurfaceMuted, fontSize = 12.sp)
            }
            KeyValueRow("Heading", "${azimuth.roundToInt()}° (${headingName(azimuth)})")
            KeyValueRow("Pitch", "${pitch.roundToInt()}°")
            KeyValueRow("Roll", "${roll.roundToInt()}°")
            KeyValueRow("Magnetfeld", "%.1f µT".format(fieldStrength))
            KeyValueRow("Accuracy", accuracyLabel(fieldAccuracy))
            if (fieldAccuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {
                Spacer(Modifier.height(6.dp))
                Text("Kalibrieren: Telefon in einer ∞-Bewegung schwenken (alle Achsen).",
                    color = GaugeOrange, fontSize = 11.sp)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().rotate(-azimuth)) {
                val cx = size.width / 2f; val cy = size.height / 2f
                val r = minOf(cx, cy) * 0.85f
                drawCircle(color = AccentSoft, radius = r, center = Offset(cx, cy),
                    style = Stroke(width = 2f))
                for (deg in 0 until 360 step 30) {
                    val rad = Math.toRadians(deg.toDouble()).toFloat()
                    val outer = Offset(cx + r * sin(rad), cy - r * cos(rad))
                    val inner = Offset(cx + (r - 24f) * sin(rad), cy - (r - 24f) * cos(rad))
                    drawLine(color = AccentSoft, start = inner, end = outer, strokeWidth = 2f)
                }
                // North arrow (always points to magnetic north)
                val tipN = Offset(cx, cy - r)
                drawLine(color = Accent, start = Offset(cx, cy), end = tipN, strokeWidth = 6f)
                drawCircle(color = Accent, radius = 10f, center = tipN)
                // South side
                val tipS = Offset(cx, cy + r)
                drawLine(color = OnSurfaceMuted, start = Offset(cx, cy), end = tipS, strokeWidth = 3f)
            }
            Text(
                "N",
                color = Accent,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.offset(y = (-100).dp).rotate(-azimuth)
            )
            Text(
                "${azimuth.roundToInt()}°",
                color = AccentSoft,
                fontFamily = FontFamily.Monospace,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun headingName(deg: Float): String {
    val names = listOf("N", "NO", "O", "SO", "S", "SW", "W", "NW")
    return names[((deg + 22.5f) / 45f).toInt() % 8]
}

private fun accuracyLabel(a: Int) = when (a) {
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "hoch ✓"
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "mittel"
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "niedrig"
    SensorManager.SENSOR_STATUS_UNRELIABLE -> "unzuverlässig"
    else -> "unbekannt"
}
