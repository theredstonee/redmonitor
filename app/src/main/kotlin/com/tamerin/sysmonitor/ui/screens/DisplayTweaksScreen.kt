package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.data.SystemTweaks
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DisplayTweaksScreen() {
    val context = LocalContext.current
    val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()
    var displayState by remember { mutableStateOf<SystemTweaks.DisplayState?>(null) }
    var anim by remember { mutableStateOf<SystemTweaks.AnimScales?>(null) }
    var showTouches by remember { mutableStateOf(false) }
    var pointerLoc by remember { mutableStateOf(false) }
    var aod by remember { mutableStateOf(false) }
    var dpiInput by remember { mutableStateOf("") }
    var sizeInput by remember { mutableStateOf("") }
    var animScale by remember { mutableFloatStateOf(1f) }
    var lastAction by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        displayState = withContext(Dispatchers.IO) { SystemTweaks.readDisplay(context) }
        anim = withContext(Dispatchers.IO) { SystemTweaks.readAnimScales(context) }
        animScale = anim?.window ?: 1f
        showTouches = withContext(Dispatchers.IO) { SystemTweaks.isShowTouches(context) }
        pointerLoc = withContext(Dispatchers.IO) { SystemTweaks.isPointerLocation(context) }
        aod = withContext(Dispatchers.IO) { SystemTweaks.isAlwaysOnDisplay(context) }
        displayState?.let {
            dpiInput = it.currentDpi.toString()
            sizeInput = it.currentSize
        }
    }

    LaunchedEffect(shizukuReady) {
        if (shizukuReady) refresh()
    }

    fun act(label: String, block: suspend () -> ShizukuHelper.CmdResult) {
        scope.launch {
            val res = withContext(Dispatchers.IO) { block() }
            lastAction = if (res.ok) "✓ $label" else "✗ $label: ${res.stderr.ifBlank { "Exit ${res.exitCode}" }}"
            refresh()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!shizukuReady) {
            StatCard("Shizuku benötigt") {
                Text("Display-Tweaks brauchen shell-Zugriff via Shizuku.",
                    color = OnSurfaceMuted, fontSize = 12.sp)
            }
            return@Column
        }

        StatCard("⚠ Warnung") {
            Text(
                "Falsche DPI/Auflösung kann UI bricken. Wenn du dich aussperrst: Smartphone neu starten — Werte gelten nur bis Reboot wenn du sie nicht über 'reset' zurücksetzt.",
                color = GaugeOrange, fontSize = 12.sp, fontWeight = FontWeight.Medium
            )
            lastAction?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = if (it.startsWith("✓")) GaugeGreen else GaugeRed, fontSize = 12.sp)
            }
        }

        // --- DPI ---
        StatCard("Display-Dichte (DPI)") {
            displayState?.let {
                KeyValueRow("Aktuell", "${it.currentDpi} dpi")
                KeyValueRow("Physisch (Werks)", "${it.physicalDpi} dpi")
            }
            Spacer(Modifier.height(8.dp))
            Text("Quick-Sets:", color = OnSurfaceMuted, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(320, 380, 420, 480, 540).forEach { dpi ->
                    OutlinedButton(
                        onClick = { act("DPI → $dpi") { SystemTweaks.setDpi(context, dpi) } },
                        contentPadding = PaddingValues(4.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("$dpi", fontSize = 11.sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = dpiInput,
                onValueChange = { dpiInput = it },
                label = { Text("Custom DPI") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        dpiInput.toIntOrNull()?.let { dpi ->
                            act("DPI → $dpi") { SystemTweaks.setDpi(context, dpi) }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Setzen") }
                OutlinedButton(
                    onClick = { act("DPI reset") { SystemTweaks.resetDpi(context) } },
                    modifier = Modifier.weight(1f)
                ) { Text("Zurücksetzen") }
            }
        }

        // --- Resolution ---
        StatCard("Auflösung") {
            displayState?.let {
                KeyValueRow("Aktuell", it.currentSize)
                KeyValueRow("Physisch (Werks)", it.physicalSize)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = sizeInput,
                onValueChange = { sizeInput = it },
                label = { Text("Format: 1080x2400") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (sizeInput.matches(Regex("""\d+x\d+"""))) {
                            act("Size → $sizeInput") { SystemTweaks.setSize(context, sizeInput) }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Setzen") }
                OutlinedButton(
                    onClick = { act("Size reset") { SystemTweaks.resetSize(context) } },
                    modifier = Modifier.weight(1f)
                ) { Text("Zurücksetzen") }
            }
        }

        // --- Animationen ---
        StatCard("Animations-Geschwindigkeit") {
            anim?.let {
                KeyValueRow("Fenster", "${it.window}×")
                KeyValueRow("Transition", "${it.transition}×")
                KeyValueRow("Animator", "${it.animator}×")
            }
            Spacer(Modifier.height(8.dp))
            Text("Alle drei gleichzeitig: ${"%.1f".format(animScale)}×",
                color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Slider(
                value = animScale,
                onValueChange = {
                    val before = animScale
                    animScale = it
                    if (kotlin.math.abs(before - it) >= 0.25f) {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.SLIDER_TICK)
                    }
                },
                onValueChangeFinished = {
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { SystemTweaks.setAllAnimScales(context, animScale) }
                        val ok = r.all { it.ok }
                        lastAction = if (ok) "✓ Anim → ${"%.1f".format(animScale)}×"
                            else "✗ Anim teilweise fehlgeschlagen"
                        refresh()
                    }
                },
                valueRange = 0f..2f,
                steps = 7
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    animScale = 0f
                    scope.launch {
                        withContext(Dispatchers.IO) { SystemTweaks.setAllAnimScales(context, 0f) }
                        refresh()
                        lastAction = "✓ Animationen aus — UI maximal schnell"
                    }
                }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Text("Aus (0×)", fontSize = 12.sp)
                }
                OutlinedButton(onClick = {
                    animScale = 1f
                    scope.launch {
                        withContext(Dispatchers.IO) { SystemTweaks.setAllAnimScales(context, 1f) }
                        refresh()
                        lastAction = "✓ Animationen normal (1×)"
                    }
                }, modifier = Modifier.weight(1f)) {
                    Text("Normal (1×)", fontSize = 12.sp)
                }
            }
        }

        // --- System-Toggles ---
        StatCard("Entwickler-Toggles") {
            ToggleRow(
                label = "Touches anzeigen",
                checked = showTouches
            ) { wantOn ->
                act(if (wantOn) "Touches an" else "Touches aus") {
                    SystemTweaks.setShowTouches(context, wantOn)
                }
            }
            ToggleRow(
                label = "Pointer-Location (Koordinaten oben)",
                checked = pointerLoc
            ) { wantOn ->
                act(if (wantOn) "Pointer an" else "Pointer aus") {
                    SystemTweaks.setPointerLocation(context, wantOn)
                }
            }
            ToggleRow(
                label = "Always-On Display",
                checked = aod
            ) { wantOn ->
                act(if (wantOn) "AOD an" else "AOD aus") {
                    SystemTweaks.setAlwaysOnDisplay(context, wantOn)
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = Accent
            )
        )
    }
}
