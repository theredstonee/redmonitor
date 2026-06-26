package com.tamerin.sysmonitor.ui.screens

import android.os.BatteryManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.ChargingControl
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.ShizukuCard
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.delay

@Composable
fun ChargingLimitScreen() {
    val context = LocalContext.current
    val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
    var probe by remember { mutableStateOf<ChargingControl.Probe?>(null) }
    var limit by remember { mutableFloatStateOf(80f) }
    var autoMode by remember { mutableStateOf(false) }
    var pct by remember { mutableFloatStateOf(0f) }
    var charging by remember { mutableStateOf(false) }
    var lastAction by remember { mutableStateOf("—") }

    LaunchedEffect(shizukuReady) {
        if (shizukuReady) probe = ChargingControl.probe(context)
    }

    LaunchedEffect(autoMode, limit) {
        while (true) {
            val bm = context.getSystemService(android.content.Context.BATTERY_SERVICE)
                as? BatteryManager
            pct = (bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0).toFloat()
            val status = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            if (autoMode && probe != null && charging && pct >= limit) {
                if (ChargingControl.stopCharging(context, probe!!)) {
                    lastAction = "Auto-Stop bei ${pct.toInt()} %"
                }
            }
            if (autoMode && probe != null && !charging && pct < limit - 5) {
                if (ChargingControl.resumeCharging(context, probe!!)) {
                    lastAction = "Auto-Resume bei ${pct.toInt()} %"
                }
            }
            delay(15_000)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!shizukuReady) {
            ShizukuCard(
                title = "Shizuku für Charging-Stop",
                description = "Charge-Toggle schreibt direkt in den Kernel-Sysfs " +
                    "(/sys/class/power_supply/battery/...). Das erlaubt nur der shell-User — " +
                    "Shizuku gibt uns den Zugriff."
            )
            return@Column
        }

        StatCard("Smart Charging Limit") {
            KeyValueRow("Akku jetzt", "${pct.toInt()} % " + if (charging) "(lädt)" else "(nicht am Strom)")
            KeyValueRow("Methode", when (probe?.strategy) {
                ChargingControl.Strategy.KERNEL_SYSFS -> "Hardware-Stop (Sysfs)"
                ChargingControl.Strategy.DUMPSYS_SOFT -> "Soft-Stop (dumpsys)"
                else -> "—"
            })
            KeyValueRow("Pfad", probe?.path?.substringAfterLast('/') ?: "—")
            if (probe == null) {
                Text(
                    "Konnte weder Sysfs noch dumpsys-Fallback einrichten. Shizuku-shell down?",
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
                return@StatCard
            }
            Spacer(Modifier.height(4.dp))
            Text(probe.description, color = OnSurfaceMuted, fontSize = 11.sp)
            if (probe.strategy == ChargingControl.Strategy.DUMPSYS_SOFT) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "ℹ Auf modernem MIUI/HyperOS und Samsung Knox sind die Kernel-Pfade " +
                        "gelocked — selbst mit root nicht aushebelbar ohne Kernel-Patch. " +
                        "Wir nehmen daher den OS-Level-Soft-Stop: `dumpsys battery set ac/usb/wireless 0`. " +
                        "Android stoppt seine Charging-Top-Up-Loop. Status-Icon zeigt entladen, " +
                        "der PMIC bekommt kein 'weiter laden'-Kommando. Wirksamkeit variiert je nach " +
                        "Vendor — manche PMICs fahren trotzdem mit minimaler Erhaltungsladung weiter.",
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            KeyValueRow("Letzte Aktion", lastAction)
        }

        if (probe != null) {
            StatCard("Limit einstellen") {
                Text("Limit: ${limit.toInt()} %", color = OnSurfaceMuted, fontSize = 12.sp)
                Slider(value = limit, onValueChange = { limit = it }, valueRange = 50f..100f, steps = 10)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = autoMode, onCheckedChange = { autoMode = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Auto-Stop wenn Akku Limit erreicht", fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Polling alle 15 s. Resume automatisch wenn Akku 5 % unter Limit fällt. " +
                        "Damit kann der Akku zwischen ${(limit - 5).toInt()}–${limit.toInt()} % " +
                        "schweben, statt 24/7 auf 100 % gequetscht zu werden.",
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }

            StatCard("Manuelle Aktionen") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (ChargingControl.stopCharging(context, probe!!)) {
                                lastAction = "Manuell gestoppt"
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Stop NOW", fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = {
                            if (ChargingControl.resumeCharging(context, probe!!)) {
                                lastAction = "Manuell fortgesetzt"
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Resume", fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                }
            }
        }
    }
}
