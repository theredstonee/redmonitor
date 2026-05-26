package com.tamerin.sysmonitor.ui.screens

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.delay

private sealed class ColorStep(val title: String) {
    data class Solid(val color: Color, val name: String) : ColorStep(name)
    data class Gradient(val name: String, val from: Color, val to: Color) : ColorStep(name)
    object Spectrum : ColorStep("HSV-Spektrum")
    object Grayscale : ColorStep("Graustufen-Ramp")
}

private val DEMO = listOf<ColorStep>(
    ColorStep.Solid(Color.Red, "Reines Rot"),
    ColorStep.Solid(Color.Green, "Reines Grün"),
    ColorStep.Solid(Color.Blue, "Reines Blau"),
    ColorStep.Solid(Color.Cyan, "Cyan"),
    ColorStep.Solid(Color.Magenta, "Magenta"),
    ColorStep.Solid(Color.Yellow, "Gelb"),
    ColorStep.Solid(Color.White, "Weiß"),
    ColorStep.Solid(Color.Black, "Schwarz"),
    ColorStep.Gradient("Rot-Verlauf 0 → 255", Color.Black, Color.Red),
    ColorStep.Gradient("Grün-Verlauf 0 → 255", Color.Black, Color.Green),
    ColorStep.Gradient("Blau-Verlauf 0 → 255", Color.Black, Color.Blue),
    ColorStep.Spectrum,
    ColorStep.Grayscale
)

@Composable
fun ColorSpaceScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    var index by remember { mutableIntStateOf(-1) }
    var paused by remember { mutableStateOf(false) }
    var secondsPerStep by remember { mutableFloatStateOf(2.5f) }
    var progress by remember { mutableFloatStateOf(0f) }

    // Keep screen on while in demo
    DisposableEffect(index >= 0) {
        if (index >= 0) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(index, paused, secondsPerStep) {
        if (index < 0 || paused) return@LaunchedEffect
        progress = 0f
        val totalMs = (secondsPerStep * 1000).toLong()
        val tickMs = 50L
        var elapsed = 0L
        while (elapsed < totalMs) {
            delay(tickMs)
            if (paused) return@LaunchedEffect
            elapsed += tickMs
            progress = elapsed.toFloat() / totalMs
        }
        index = if (index + 1 < DEMO.size) index + 1 else -1
    }

    if (index in DEMO.indices) {
        DemoFullscreen(
            step = DEMO[index],
            position = index,
            total = DEMO.size,
            paused = paused,
            progress = progress,
            onTapTogglePause = { paused = !paused },
            onPrev = { index = if (index - 1 >= 0) index - 1 else DEMO.lastIndex },
            onNext = { index = if (index + 1 < DEMO.size) index + 1 else -1 },
            onExit = { index = -1 }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard("Display & Farbraum") {
            val info = activity?.let { collectDisplayInfo(it) } ?: emptyList()
            if (info.isEmpty()) {
                Text("Display-Infos nicht verfügbar.", color = OnSurfaceMuted, fontSize = 12.sp)
            } else {
                info.forEach { (k, v) -> KeyValueRow(k, v) }
            }
        }

        StatCard("Auto-Demo (Vollbild)") {
            Text(
                "Spielt ${DEMO.size} Farben, Verläufe und Test-Muster automatisch nacheinander ab. " +
                    "Tap = Pause/Weiter, Buttons = manuell, ✕ = Ende.",
                color = OnSurfaceMuted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Text("Dauer pro Schritt: ${"%.1f".format(secondsPerStep)} s", fontSize = 13.sp)
            Slider(
                value = secondsPerStep,
                onValueChange = { secondsPerStep = it },
                valueRange = 1f..8f,
                steps = 13
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = {
                paused = false
                index = 0
            }) { Text("Demo starten") }
        }

        StatCard("Reine Farben (sRGB)") {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Swatch("R", Color.Red, Modifier.weight(1f))
                Swatch("G", Color.Green, Modifier.weight(1f))
                Swatch("B", Color.Blue, Modifier.weight(1f))
                Swatch("C", Color.Cyan, Modifier.weight(1f))
                Swatch("M", Color.Magenta, Modifier.weight(1f))
                Swatch("Y", Color.Yellow, Modifier.weight(1f))
            }
        }

        StatCard("Farbverläufe") {
            GradientBar("Rot 0 → 255", Color.Black, Color.Red)
            Spacer(Modifier.height(8.dp))
            GradientBar("Grün 0 → 255", Color.Black, Color.Green)
            Spacer(Modifier.height(8.dp))
            GradientBar("Blau 0 → 255", Color.Black, Color.Blue)
            Spacer(Modifier.height(12.dp))
            Text("Spektrum (HSV)", color = OnSurfaceMuted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            SpectrumBar()
        }

        StatCard("Graustufen-Ramp (Bit-Tiefe prüfen)") {
            Text(
                "Sauber abgestufte Streifen = mind. 8 Bit pro Kanal. Banding = nur 6 Bit + Dithering.",
                color = OnSurfaceMuted,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(6.dp))
            GrayscaleRamp(steps = 16)
            Spacer(Modifier.height(8.dp))
            Text("Stufenlos:", color = OnSurfaceMuted, fontSize = 11.sp)
            Spacer(Modifier.height(2.dp))
            GradientBar("", Color.Black, Color.White)
        }
    }
}

@Composable
private fun DemoFullscreen(
    step: ColorStep,
    position: Int,
    total: Int,
    paused: Boolean,
    progress: Float,
    onTapTogglePause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onTapTogglePause() }
    ) {
        when (step) {
            is ColorStep.Solid -> Box(Modifier.fillMaxSize().background(step.color))
            is ColorStep.Gradient -> Box(
                Modifier.fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(step.from, step.to)))
            )
            ColorStep.Spectrum -> {
                val colors = (0..360 step 20).map { hsvToColor(it.toFloat()) }
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(colors)))
            }
            ColorStep.Grayscale -> Column(Modifier.fillMaxSize()) {
                val steps = 32
                for (i in 0 until steps) {
                    val v = i.toFloat() / (steps - 1)
                    Box(Modifier.weight(1f).fillMaxWidth().background(Color(v, v, v, 1f)))
                }
            }
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            val textColor = if (step is ColorStep.Solid && step.color.luminance() > 0.6f)
                Color.Black else Color.White
            Text(
                "${step.title}  ·  ${position + 1}/$total${if (paused) "  ·  PAUSE" else ""}",
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPrev) { Text("◀") }
                OutlinedButton(onClick = onTapTogglePause) {
                    Text(if (paused) "▶ Weiter" else "⏸ Pause")
                }
                OutlinedButton(onClick = onNext) { Text("▶") }
                OutlinedButton(onClick = onExit) { Text("✕ Ende") }
            }
        }
    }
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

@Composable
private fun Swatch(label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.Black, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GradientBar(label: String, from: Color, to: Color) {
    if (label.isNotEmpty()) {
        Text(label, color = OnSurfaceMuted, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Brush.horizontalGradient(listOf(from, to)))
    )
}

@Composable
private fun SpectrumBar() {
    val colors = (0..360 step 30).map { hsvToColor(it.toFloat()) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Brush.horizontalGradient(colors))
    )
}

@Composable
private fun GrayscaleRamp(steps: Int) {
    Row(modifier = Modifier.fillMaxWidth().height(28.dp)) {
        for (i in 0 until steps) {
            val v = i.toFloat() / (steps - 1)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(v, v, v, 1f))
            )
        }
    }
}

private fun hsvToColor(h: Float): Color {
    val s = 1f; val v = 1f
    val c = v * s
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

private fun collectDisplayInfo(activity: Activity): List<Pair<String, String>> {
    val list = mutableListOf<Pair<String, String>>()
    val dm = activity.getSystemService(android.content.Context.DISPLAY_SERVICE) as DisplayManager
    val display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return list

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        list += "Wide Color Gamut" to if (activity.windowManager.defaultDisplay.isWideColorGamut) "Ja" else "Nein"
        val pref = activity.window.colorMode
        list += "Aktueller Color-Mode" to when (pref) {
            android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT -> "Default (sRGB)"
            android.content.pm.ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT -> "Wide Color Gamut"
            android.content.pm.ActivityInfo.COLOR_MODE_HDR -> "HDR"
            else -> "?"
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val caps = display.hdrCapabilities
        val typesArr: IntArray? = caps?.supportedHdrTypes
        val hdrTypes: List<String> = if (typesArr == null) emptyList() else {
            val out = mutableListOf<String>()
            for (t in typesArr) {
                val label = when (t) {
                    android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                    android.view.Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                    android.view.Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
                    android.view.Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                    else -> null
                }
                if (label != null) out += label
            }
            out
        }
        val hdrLabel = if (hdrTypes.isEmpty()) "Keine" else hdrTypes.joinToString(", ")
        list += "HDR-Unterstützung" to hdrLabel
        if (caps != null && caps.desiredMaxLuminance > 0) {
            list += "Max Luminanz" to "${caps.desiredMaxLuminance.toInt()} nits"
            list += "Min Luminanz" to "${"%.3f".format(caps.desiredMinLuminance)} nits"
        }
    }
    list += "Refresh-Rate" to "${"%.1f".format(display.refreshRate)} Hz"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val modes = display.supportedModes
        if (modes.size > 1) {
            list += "Modi" to modes.joinToString(", ") {
                "${it.physicalWidth}×${it.physicalHeight} @ ${"%.0f".format(it.refreshRate)}Hz"
            }
        }
    }
    return list
}
