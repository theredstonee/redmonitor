package com.tamerin.sysmonitor.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.delay

private sealed class Pattern(val title: String) {
    data class Solid(val color: Color, val name: String) : Pattern(name)
    object Checkerboard : Pattern("Schachbrett (fein)")
    object Gradient : Pattern("RGB-Spektrum")
    object Grayscale : Pattern("Graustufen-Ramp")
    object ColorBars : Pattern("Farbbalken (SMPTE-Style)")
    object Crosshatch : Pattern("Gitter (Geometrie)")
}

private val PATTERNS = listOf<Pattern>(
    Pattern.Solid(Color.Red, "Rot vollflächig"),
    Pattern.Solid(Color.Green, "Grün vollflächig"),
    Pattern.Solid(Color.Blue, "Blau vollflächig"),
    Pattern.Solid(Color.White, "Weiß vollflächig"),
    Pattern.Solid(Color.Black, "Schwarz vollflächig"),
    Pattern.Solid(Color(0xFF808080), "50 % Grau"),
    Pattern.Checkerboard,
    Pattern.Gradient,
    Pattern.Grayscale,
    Pattern.ColorBars,
    Pattern.Crosshatch
)

@Composable
fun FullscreenDisplayTestScreen() {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(-1) }
    var paused by remember { mutableStateOf(false) }
    var secondsPerPattern by remember { mutableFloatStateOf(2.5f) }
    var progress by remember { mutableFloatStateOf(0f) }

    // Keep screen on while a pattern is showing
    DisposableEffect(index >= 0) {
        val activity = context as? Activity
        if (index >= 0) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Auto-advance ticker
    LaunchedEffect(index, paused, secondsPerPattern) {
        if (index < 0 || paused) return@LaunchedEffect
        progress = 0f
        val totalMs = (secondsPerPattern * 1000).toLong()
        val tickMs = 50L
        var elapsed = 0L
        while (elapsed < totalMs) {
            delay(tickMs)
            if (paused) return@LaunchedEffect
            elapsed += tickMs
            progress = elapsed.toFloat() / totalMs
        }
        index = if (index + 1 < PATTERNS.size) index + 1 else -1
    }

    if (index in PATTERNS.indices) {
        val p = PATTERNS[index]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable {
                    // Tap once = pause/resume. Long story: simple toggle.
                    paused = !paused
                }
        ) {
            when (p) {
                is Pattern.Solid -> Box(Modifier.fillMaxSize().background(p.color))
                Pattern.Checkerboard -> FullCheckerboard()
                Pattern.Gradient -> FullGradient()
                Pattern.Grayscale -> FullGrayscale()
                Pattern.ColorBars -> FullColorBars()
                Pattern.Crosshatch -> FullCrosshatch()
            }
            // Progress bar at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.7f))
                )
            }
            // Caption + controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                val textColor = if (p is Pattern.Solid && p.color.luminance() > 0.6f)
                    Color.Black else Color.White
                Text(
                    "${p.title}  ·  ${index + 1}/${PATTERNS.size}${if (paused) "  ·  PAUSE" else ""}",
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        index = if (index - 1 >= 0) index - 1 else PATTERNS.lastIndex
                    }) { Text("◀") }
                    OutlinedButton(onClick = { paused = !paused }) {
                        Text(if (paused) "▶ Weiter" else "⏸ Pause")
                    }
                    OutlinedButton(onClick = {
                        index = if (index + 1 < PATTERNS.size) index + 1 else -1
                    }) { Text("▶") }
                    OutlinedButton(onClick = { index = -1 }) { Text("✕ Ende") }
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("Display-Test") {
            Text(
                "Spielt automatisch ${PATTERNS.size} Vollbild-Muster nacheinander ab. " +
                    "Tap auf den Bildschirm = Pause / Weiter, Buttons unten = manuell.",
                color = OnSurfaceMuted,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Dauer pro Muster: ${"%.1f".format(secondsPerPattern)} s",
                fontSize = 13.sp
            )
            Slider(
                value = secondsPerPattern,
                onValueChange = { secondsPerPattern = it },
                valueRange = 1f..8f,
                steps = 13
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                paused = false
                index = 0
            }) { Text("Auto-Sequenz starten") }
        }

        StatCard("Einzeln testen") {
            PATTERNS.forEachIndexed { i, p ->
                OutlinedButton(
                    onClick = {
                        paused = true
                        index = i
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Text("${i + 1}. ${p.title}")
                }
            }
        }
    }
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

@Composable
private fun FullCheckerboard() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cell = 4f
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val odd = (x + y) % 2 == 1
                drawRect(
                    color = if (odd) Color.White else Color.Black,
                    topLeft = Offset(x * cell, y * cell),
                    size = androidx.compose.ui.geometry.Size(cell, cell)
                )
            }
        }
    }
}

@Composable
private fun FullGradient() {
    val colors = (0..360 step 20).map { hueToColor(it.toFloat()) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.horizontalGradient(colors))
    )
}

@Composable
private fun FullGrayscale() {
    Column(modifier = Modifier.fillMaxSize()) {
        val steps = 32
        for (i in 0 until steps) {
            val v = i.toFloat() / (steps - 1)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(v, v, v, 1f))
            )
        }
    }
}

@Composable
private fun FullColorBars() {
    val bars = listOf(
        Color.White, Color.Yellow, Color.Cyan, Color.Green,
        Color.Magenta, Color.Red, Color.Blue, Color.Black
    )
    Row(modifier = Modifier.fillMaxSize()) {
        bars.forEach { c ->
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(c))
        }
    }
}

@Composable
private fun FullCrosshatch() {
    Canvas(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val spacing = 40f
        var x = 0f
        while (x < size.width) {
            drawLine(
                color = Color.White,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
            x += spacing
        }
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color.White,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
            y += spacing
        }
    }
}

private fun hueToColor(h: Float): Color {
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
