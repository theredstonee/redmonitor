package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.BuildConfig
import com.tamerin.sysmonitor.glyph.GlyphMatrixRenderer
import com.tamerin.sysmonitor.glyph.GlyphMetric
import com.tamerin.sysmonitor.glyph.GlyphMetricReader
import com.tamerin.sysmonitor.glyph.GlyphPrefs
import com.tamerin.sysmonitor.glyph.GlyphSupport
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Glyph-Matrix-Screen: konfiguriert den Toy fuer das Nothing Phone (3) und zeigt
 * eine 1:1-Vorschau dessen, was auf der Rueckseite landet.
 *
 * Die Vorschau benutzt exakt denselben [GlyphMatrixRenderer] wie der Toy-Service —
 * was hier zu sehen ist, ist also nicht nachgebaut, sondern dasselbe Bild.
 */
@Composable
fun GlyphMatrixScreen() {
    val context = LocalContext.current
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    var metric by remember { mutableStateOf(GlyphPrefs.metric(context)) }
    var brightness by remember { mutableStateOf(GlyphPrefs.brightness(context)) }
    var aodBrightness by remember { mutableStateOf(GlyphPrefs.aodBrightness(context)) }
    var frame by remember { mutableStateOf(IntArray(GlyphMatrixRenderer.SIZE * GlyphMatrixRenderer.SIZE)) }
    var readout by remember { mutableStateOf("—") }

    val deviceSupported = remember { GlyphSupport.isDeviceSupported() }

    LaunchedEffect(metric, brightness) {
        while (true) {
            val sample = withContext(Dispatchers.IO) { GlyphMetricReader.read(context, metric) }
            frame = GlyphMatrixRenderer.render(
                value = sample.value,
                fraction = sample.fraction,
                marker = metric.marker,
                brightness = brightness
            )
            readout = "${sample.value}${metric.unit}"
            delay(2_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard("Vorschau (25 × 25)") {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                MatrixPreview(
                    frame = frame,
                    modifier = Modifier.size(260.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "${metric.label}: $readout",
                color = OnSurfaceMuted,
                fontSize = 13.sp
            )
        }

        StatCard("Metrik") {
            Text(
                "Auf dem Geraet blaetterst du mit langem Druck auf den Glyph-Button weiter.",
                color = OnSurfaceMuted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlyphMetric.entries.forEach { m ->
                    FilterChip(
                        selected = m == metric,
                        onClick = {
                            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                            metric = m
                            GlyphPrefs.setMetric(context, m)
                        },
                        label = { Text(m.marker.toString(), fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        StatCard("Helligkeit") {
            Text("Normal: $brightness", color = OnSurfaceMuted, fontSize = 12.sp)
            Slider(
                value = brightness.toFloat(),
                onValueChange = { brightness = it.toInt() },
                onValueChangeFinished = { GlyphPrefs.setBrightness(context, brightness) },
                valueRange = 10f..255f
            )
            Spacer(Modifier.height(8.dp))
            Text("Always-On: $aodBrightness", color = OnSurfaceMuted, fontSize = 12.sp)
            Slider(
                value = aodBrightness.toFloat(),
                onValueChange = { aodBrightness = it.toInt() },
                onValueChangeFinished = { GlyphPrefs.setAodBrightness(context, aodBrightness) },
                valueRange = 10f..255f
            )
            Text(
                "Im Always-On-Betrieb aktualisiert Nothing OS den Toy einmal pro Minute.",
                color = OnSurfaceMuted,
                fontSize = 11.sp
            )
        }

        StatCard("Status") {
            KeyValueRow("Glyph-SDK im Build", if (BuildConfig.HAS_GLYPH_SDK) "ja" else "nein")
            KeyValueRow("Glyph Matrix erkannt", if (deviceSupported) "ja (Phone 3)" else "nein")
            Spacer(Modifier.height(10.dp))
            when {
                !BuildConfig.HAS_GLYPH_SDK -> Text(
                    "Dieser Build wurde ohne das Nothing-SDK erstellt. Der Toy ist deshalb " +
                        "inaktiv — die Vorschau oben funktioniert trotzdem, sie braucht kein SDK.",
                    color = OnSurfaceMuted,
                    fontSize = 12.sp
                )
                !deviceSupported -> Text(
                    "Die Glyph Matrix gibt es nur beim Nothing Phone (3). Auf diesem Geraet " +
                        "laesst sich der Toy nicht aktivieren.",
                    color = OnSurfaceMuted,
                    fontSize = 12.sp
                )
                else -> {
                    Text(
                        "Aktiviere RedMonitor in Nothings Toys-Manager, danach erreichst du ihn " +
                            "mit kurzem Druck auf den Glyph-Button.",
                        color = OnSurfaceMuted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    var managerFailed by remember { mutableStateOf(false) }
                    Button(onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                        managerFailed = !GlyphSupport.openToysManager(context)
                    }) { Text("Toy aktivieren") }
                    if (managerFailed) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Toys-Manager liess sich nicht oeffnen (gibt es erst ab Nothing OS " +
                                "20250829). Manuell: Einstellungen › Glyph-Interface › Glyph-Toys.",
                            color = OnSurfaceMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatrixPreview(frame: IntArray, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val n = GlyphMatrixRenderer.SIZE
        val cell = size.minDimension / n
        val radius = cell * 0.38f
        for (y in 0 until n) {
            for (x in 0 until n) {
                val v = frame.getOrElse(y * n + x) { 0 }
                // Aus-LEDs bleiben schwach sichtbar, damit man das Raster erkennt
                val alpha = if (v <= 0) 0.05f else (v / 255f).coerceIn(0.08f, 1f)
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = radius,
                    center = Offset(x * cell + cell / 2f, y * cell + cell / 2f)
                )
            }
        }
    }
}
