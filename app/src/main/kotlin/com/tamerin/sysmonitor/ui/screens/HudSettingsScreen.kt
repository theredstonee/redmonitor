package com.tamerin.sysmonitor.ui.screens

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.overlay.HudColor
import com.tamerin.sysmonitor.overlay.HudConfig
import com.tamerin.sysmonitor.overlay.HudMetric
import com.tamerin.sysmonitor.overlay.HudPrefs
import com.tamerin.sysmonitor.overlay.HudSize
import com.tamerin.sysmonitor.overlay.OverlayService
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

@Composable
fun HudSettingsScreen() {
    val context = LocalContext.current
    var config by remember { mutableStateOf(HudPrefs.load(context)) }
    var hasPerm by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        )
    }
    var hudRunning by remember { mutableStateOf(false) }
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    fun apply(newConfig: HudConfig) {
        config = newConfig
        HudPrefs.save(context, newConfig)
        if (hudRunning) OverlayService.reload(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Live Preview
        StatCard("Vorschau") {
            HudPreview(config = config)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                        OverlayService.start(context)
                        hudRunning = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = hasPerm
                ) { Text("HUD starten") }
                OutlinedButton(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                        OverlayService.stop(context)
                        hudRunning = false
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Stopp") }
            }
            if (!hasPerm) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Overlay-Berechtigung fehlt — siehe System → Floating HUD",
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }
        }

        // Metriken
        StatCard("Metriken") {
            Text(
                "Welche Werte sollen im HUD angezeigt werden?",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            HudMetric.values().forEach { metric ->
                ToggleRow(
                    label = metric.label,
                    checked = metric in config.enabledMetrics
                ) { checked ->
                    val newMetrics = config.enabledMetrics.toMutableSet().also {
                        if (checked) it.add(metric) else it.remove(metric)
                    }
                    apply(config.copy(enabledMetrics = newMetrics))
                }
            }
        }

        // Größe
        StatCard("Größe") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudSize.values().forEach { size ->
                    val selected = config.size == size
                    OutlinedButton(
                        onClick = {
                            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                            apply(config.copy(size = size))
                        },
                        modifier = Modifier.weight(1f),
                        border = if (selected) BorderStroke(2.dp, Accent) else null
                    ) {
                        Text(
                            size.label,
                            color = if (selected) Accent else Color.Unspecified,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Transparenz
        StatCard("Transparenz") {
            KeyValueRow("Deckkraft", "${(config.opacity * 100).toInt()} %")
            Slider(
                value = config.opacity,
                onValueChange = {
                    if (kotlin.math.abs(config.opacity - it) >= 0.1f) {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.SLIDER_TICK)
                    }
                    apply(config.copy(opacity = it))
                },
                valueRange = 0.3f..1.0f
            )
        }

        // Akzentfarbe
        StatCard("Akzentfarbe") {
            Text(
                "Farbe für Labels und Border",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HudColor.values().forEach { c ->
                    val selected = config.color == c
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.composeColor())
                            .clickable {
                                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                                apply(config.copy(color = c))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Text("✓", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(config.color.label, color = OnSurfaceMuted, fontSize = 11.sp)
        }

        // Edge-Snap
        StatCard("Edge-Snapping") {
            ToggleRow(
                label = "Beim Loslassen an Bildschirmrand schnappen",
                checked = config.edgeSnap
            ) { apply(config.copy(edgeSnap = it)) }
        }

        // Reset
        StatCard("Reset") {
            Text(
                "Setzt alle HUD-Einstellungen auf den Standard zurück.",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                apply(HudConfig.DEFAULT)
            }) {
                Text("Auf Standard zurücksetzen")
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TOGGLE)
                onChange(!checked)
            }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TOGGLE)
                onChange(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Accent
            )
        )
    }
}

@Composable
private fun HudPreview(config: HudConfig) {
    val alpha = config.opacity
    val bg = Color(0xFF0A0A0A).copy(alpha = alpha)
    val accent = config.color.composeColor()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            config.enabledMetrics.forEachIndexed { i, metric ->
                val (label, value) = sampleFor(metric)
                Row {
                    Text(
                        "$label ",
                        color = accent,
                        fontSize = (11f * config.size.scale).sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        value,
                        color = Color(0xFFF3F4F6),
                        fontSize = (11f * config.size.scale).sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            if (config.enabledMetrics.isEmpty()) {
                Text(
                    "(keine Metriken aktiv)",
                    color = OnSurfaceMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun sampleFor(metric: HudMetric): Pair<String, String> = when (metric) {
    HudMetric.CPU_PERCENT -> "CPU" to "42%"
    HudMetric.PER_CORE -> "•" to "▃▄▆█▂▃▄▅"
    HudMetric.PER_CORE_DETAIL -> "C0" to "45% 2400MHz · C1 22% 1800MHz · …"
    HudMetric.CPU_FREQ_AVG -> "F" to "2147 MHz"
    HudMetric.CPU_TEMP -> "T" to "47°C"
    HudMetric.RAM_PERCENT -> "RAM" to "63%"
    HudMetric.BATTERY -> "Akku" to "78% ⚡18.5W"
    HudMetric.NETWORK -> "↓↑" to "1.2MB/s  248KB/s"
    HudMetric.FPS -> "FPS" to "120"
    HudMetric.CLOCK -> "⌚" to "14:32  2h47m"
}
