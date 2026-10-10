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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.overlay.HudColor
import com.tamerin.sysmonitor.overlay.HudConfig
import com.tamerin.sysmonitor.overlay.HudMetric
import com.tamerin.sysmonitor.data.ForegroundAppReader
import com.tamerin.sysmonitor.overlay.HudPrefs
import com.tamerin.sysmonitor.overlay.HudProfileMode
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
        // Always send reload — OverlayService bails internally if not running
        OverlayService.reload(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Live Preview
        StatCard(stringResource(R.string.scr2_hud_preview_title)) {
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
                ) { Text(stringResource(R.string.scr2_hud_start)) }
                OutlinedButton(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                        OverlayService.stop(context)
                        hudRunning = false
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.scr2_hud_stop)) }
            }
            if (!hasPerm) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.scr2_hud_perm_missing),
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }
        }

        // Metriken
        StatCard(stringResource(R.string.scr2_hud_metrics_title)) {
            Text(
                stringResource(R.string.scr2_hud_metrics_desc),
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            HudMetric.values().forEach { metric ->
                ToggleRow(
                    label = stringResource(metric.labelRes),
                    checked = metric in config.enabledMetrics
                ) { checked ->
                    val newMetrics = config.enabledMetrics.toMutableSet().also {
                        if (checked) it.add(metric) else it.remove(metric)
                    }
                    apply(config.copy(enabledMetrics = newMetrics))
                }
            }
        }

        HudProfileCard(config = config, onChange = { apply(it) })

        // Größe
        StatCard(stringResource(R.string.scr2_hud_size_title)) {
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
                            stringResource(size.labelRes),
                            color = if (selected) Accent else Color.Unspecified,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Transparenz
        StatCard(stringResource(R.string.scr2_hud_transparency_title)) {
            KeyValueRow(stringResource(R.string.scr2_hud_opacity), "${(config.opacity * 100).toInt()} %")
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
        StatCard(stringResource(R.string.scr2_hud_accent_title)) {
            Text(
                stringResource(R.string.scr2_hud_accent_desc),
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
            Text(stringResource(config.color.labelRes), color = OnSurfaceMuted, fontSize = 11.sp)
        }

        // Edge-Snap
        StatCard("Edge-Snapping") {
            ToggleRow(
                label = stringResource(R.string.scr2_hud_edgesnap_label),
                checked = config.edgeSnap
            ) { apply(config.copy(edgeSnap = it)) }
        }

        // Reset
        StatCard("Reset") {
            Text(
                stringResource(R.string.scr2_hud_reset_desc),
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                apply(HudConfig.DEFAULT)
            }) {
                Text(stringResource(R.string.scr2_hud_reset_btn))
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
                    stringResource(R.string.scr2_hud_no_metrics),
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

/** Ein installiertes, startbares Paket - Label + Paketname. */
private data class LaunchableApp(val pkg: String, val label: String)

/**
 * HUD-Profile: das Overlay nur in bestimmten Apps zeigen (z. B. Spielen) oder
 * gezielt in bestimmten Apps unterdruecken.
 */
@Composable
private fun HudProfileCard(config: HudConfig, onChange: (HudConfig) -> Unit) {
    val context = LocalContext.current
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()
    var showPicker by remember { mutableStateOf(false) }
    var hasUsagePerm by remember { mutableStateOf(ForegroundAppReader.hasPermission(context)) }

    StatCard(stringResource(R.string.scr2_hud_profile_title)) {
        Text(
            stringResource(R.string.scr2_hud_profile_desc),
            color = OnSurfaceMuted, fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HudProfileMode.entries.forEach { mode ->
                val selected = config.profileMode == mode
                OutlinedButton(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                        hasUsagePerm = ForegroundAppReader.hasPermission(context)
                        onChange(config.copy(profileMode = mode))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = if (selected) BorderStroke(2.dp, Accent) else null
                ) {
                    Text(
                        stringResource(mode.labelRes),
                        color = if (selected) Accent else Color.Unspecified,
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (config.profileMode != HudProfileMode.ALWAYS) {
            Spacer(Modifier.height(12.dp))
            if (!hasUsagePerm) {
                Text(
                    stringResource(R.string.scr2_hud_usage_perm),
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                    runCatching { context.startActivity(ForegroundAppReader.permissionIntent()) }
                }) { Text(stringResource(R.string.scr2_hud_grant_access)) }
                Spacer(Modifier.height(10.dp))
            }
            OutlinedButton(
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    showPicker = true
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.scr2_hud_pick_apps_count, config.profileApps.size)) }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            selected = config.profileApps,
            onDismiss = { showPicker = false },
            onConfirm = { picked ->
                onChange(config.copy(profileApps = picked))
                showPicker = false
            }
        )
    }
}

@Composable
private fun AppPickerDialog(
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<String>().apply { addAll(selected) } }

    LaunchedEffect(Unit) {
        apps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(intent, 0)
                    .mapNotNull { ri ->
                        val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                        LaunchableApp(pkg, ri.loadLabel(pm).toString())
                    }
                    .distinctBy { it.pkg }
                    .sortedBy { it.label.lowercase() }
            }.getOrDefault(emptyList())
        }
    }

    val visible = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scr2_hud_pick_apps_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.scr2_hud_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (apps.isEmpty()) {
                    Text(stringResource(R.string.scr2_hud_loading_apps), color = OnSurfaceMuted, fontSize = 12.sp)
                } else {
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(visible, key = { it.pkg }) { app ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (app.pkg in picked) picked.remove(app.pkg)
                                        else picked.add(app.pkg)
                                    }
                            ) {
                                Checkbox(
                                    checked = app.pkg in picked,
                                    onCheckedChange = {
                                        if (it) picked.add(app.pkg) else picked.remove(app.pkg)
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.label, fontSize = 13.sp, maxLines = 1)
                                    Text(app.pkg, color = OnSurfaceMuted, fontSize = 10.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(picked.toSet()) }) { Text(stringResource(R.string.scr2_hud_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.scr2_hud_cancel)) }
        }
    )
}
