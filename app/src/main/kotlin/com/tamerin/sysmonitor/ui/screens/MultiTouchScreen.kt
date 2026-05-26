package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.BgDark
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun MultiTouchScreen() {
    val pointers = remember { mutableStateMapOf<Int, Offset>() }
    var maxSimultaneous by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .pointerInteropFilter { event ->
                val active = mutableMapOf<Int, Offset>()
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    active[id] = Offset(event.getX(i), event.getY(i))
                }
                pointers.clear()
                pointers.putAll(active)
                if (active.size > maxSimultaneous) maxSimultaneous = active.size
                true
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            pointers.entries.forEachIndexed { idx, entry ->
                val hue = (idx * 60) % 360
                val c = hueToColor(hue.toFloat())
                drawCircle(c, radius = 80f, center = entry.value, alpha = 0.4f)
                drawCircle(c, radius = 30f, center = entry.value)
            }
        }
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Multi-Touch-Test",
                color = Accent,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp
            )
            Text(
                "Lege beliebig viele Finger auf den Screen. Jeder Finger wird mit eigener Farbe markiert.",
                color = OnSurfaceMuted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Aktive Punkte: ${pointers.size}   ·   Max gleichzeitig: $maxSimultaneous",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun hueToColor(hue: Float): Color {
    val h = (hue % 360f + 360f) % 360f
    val c = 1f
    val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
    val (r, g, b) = when {
        h < 60 -> Triple(c, x, 0f)
        h < 120 -> Triple(x, c, 0f)
        h < 180 -> Triple(0f, c, x)
        h < 240 -> Triple(0f, x, c)
        h < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r, g, b, 1f)
}
