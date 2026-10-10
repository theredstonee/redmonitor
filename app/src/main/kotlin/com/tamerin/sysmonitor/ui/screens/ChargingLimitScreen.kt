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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.R
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
                    lastAction = context.getString(R.string.pwr_chg_auto_stop, pct.toInt())
                }
            }
            if (autoMode && probe != null && !charging && pct < limit - 5) {
                if (ChargingControl.resumeCharging(context, probe!!)) {
                    lastAction = context.getString(R.string.pwr_chg_auto_resume, pct.toInt())
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
                title = stringResource(R.string.pwr_chg_shizuku_title),
                description = stringResource(R.string.pwr_chg_shizuku_desc)
            )
            return@Column
        }

        StatCard(stringResource(R.string.pwr_chg_title)) {
            val p = probe
            KeyValueRow(stringResource(R.string.pwr_chg_battery_now), "${pct.toInt()} % " +
                if (charging) stringResource(R.string.pwr_chg_charging_paren)
                else stringResource(R.string.pwr_chg_not_plugged))
            KeyValueRow(stringResource(R.string.pwr_chg_method), when (p?.strategy) {
                ChargingControl.Strategy.KERNEL_SYSFS -> stringResource(R.string.pwr_chg_method_sysfs)
                ChargingControl.Strategy.DUMPSYS_SOFT -> stringResource(R.string.pwr_chg_method_dumpsys)
                else -> "—"
            })
            KeyValueRow(stringResource(R.string.pwr_chg_path), p?.path?.substringAfterLast('/') ?: "—")
            if (p == null) {
                Text(
                    stringResource(R.string.pwr_chg_no_method),
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
                return@StatCard
            }
            Spacer(Modifier.height(4.dp))
            Text(p.description, color = OnSurfaceMuted, fontSize = 11.sp)
            if (p.strategy == ChargingControl.Strategy.DUMPSYS_SOFT) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.pwr_chg_softstop_note),
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            KeyValueRow(stringResource(R.string.pwr_chg_last_action), lastAction)
        }

        if (probe != null) {
            StatCard(stringResource(R.string.pwr_chg_set_limit)) {
                Text(stringResource(R.string.pwr_chg_limit_value, limit.toInt()), color = OnSurfaceMuted, fontSize = 12.sp)
                Slider(value = limit, onValueChange = { limit = it }, valueRange = 50f..100f, steps = 10)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = autoMode, onCheckedChange = { autoMode = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pwr_chg_auto_stop_label), fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.pwr_chg_polling_note, (limit - 5).toInt(), limit.toInt()),
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }

            StatCard(stringResource(R.string.pwr_chg_manual_actions)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (ChargingControl.stopCharging(context, probe!!)) {
                                lastAction = context.getString(R.string.pwr_chg_manual_stopped)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Stop NOW", fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = {
                            if (ChargingControl.resumeCharging(context, probe!!)) {
                                lastAction = context.getString(R.string.pwr_chg_manual_resumed)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Resume", fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                }
            }
        }
    }
}
