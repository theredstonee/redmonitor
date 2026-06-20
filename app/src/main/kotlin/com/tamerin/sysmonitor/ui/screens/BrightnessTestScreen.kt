package com.tamerin.sysmonitor.ui.screens

import android.app.Activity
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.delay

/**
 * Display-Brightness-Konsistenz: stepped 0/25/50/75/100% mit window-level
 * BRIGHTNESS_OVERRIDE_FULL, plus Auto-Cycling. Zeigt parallel den System-Wert.
 * Auf manchen OLEDs zickt das Backlight unter ~10% (PWM-Stepping sichtbar).
 */
@Composable
fun BrightnessTestScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var cycling by remember { mutableStateOf(false) }
    var fullscreenWhite by remember { mutableStateOf(false) }
    var systemBrightness by remember { mutableStateOf(systemBrightness(context)) }

    DisposableEffect(Unit) {
        onDispose { resetBrightness(activity) }
    }

    LaunchedEffect(brightness, activity) {
        activity?.window?.attributes = activity?.window?.attributes?.apply {
            screenBrightness = brightness
        }
        systemBrightness = systemBrightness(context)
    }

    LaunchedEffect(cycling) {
        if (!cycling) return@LaunchedEffect
        val steps = listOf(0.05f, 0.25f, 0.5f, 0.75f, 1f)
        var i = 0
        while (cycling) {
            brightness = steps[i % steps.size]
            i++
            delay(2500)
        }
    }

    if (fullscreenWhite) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentAlignment = Alignment.BottomCenter
        ) {
            Button(
                onClick = { fullscreenWhite = false },
                modifier = Modifier.padding(24.dp)
            ) { Text("Zurück") }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("Window-Brightness") {
            Text(
                "Überschreibt die System-Helligkeit nur für diese Activity. Schiebe den Regler " +
                    "auf 5% / 10% / 20% — viele OLEDs zeigen dort sichtbares PWM-Flicker oder " +
                    "Color-Banding. 100% deckt Max-Brightness ab.",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            KeyValueRow("Window-Wert", "${(brightness * 100).toInt()} %")
            Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 0.01f..1f)
            Spacer(Modifier.height(4.dp))
            KeyValueRow("System-Wert", systemBrightness?.let { "$it / 255" } ?: "n/a")
        }

        StatCard("Quick-Steps") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.05f, 0.25f, 0.5f, 0.75f, 1f).forEach { v ->
                    OutlinedButton(
                        onClick = { brightness = v },
                        modifier = Modifier.weight(1f)
                    ) { Text("${(v * 100).toInt()}", fontSize = 11.sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { cycling = !cycling },
                    modifier = Modifier.weight(1f)
                ) { Text(if (cycling) "Cycle stop" else "Auto-Cycle", fontSize = 12.sp) }
                Button(
                    onClick = { fullscreenWhite = true },
                    modifier = Modifier.weight(1f)
                ) { Text("Weißbild", fontSize = 12.sp) }
            }
        }
    }
}

private fun systemBrightness(context: android.content.Context): Int? = runCatching {
    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
}.getOrNull()

private fun resetBrightness(activity: Activity?) {
    activity?.window?.attributes = activity?.window?.attributes?.apply {
        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }
}
