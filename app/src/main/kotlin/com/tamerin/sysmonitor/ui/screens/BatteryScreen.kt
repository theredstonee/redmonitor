package com.tamerin.sysmonitor.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.data.ShizukuHelper
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlin.math.cos
import kotlin.math.sin
import com.tamerin.sysmonitor.benchmark.DrainEngine
import com.tamerin.sysmonitor.data.BatteryConsumer
import com.tamerin.sysmonitor.data.BatteryConsumerReader
import com.tamerin.sysmonitor.data.BatteryEstimator
import com.tamerin.sysmonitor.data.BatteryReader
import com.tamerin.sysmonitor.data.BatterySnapshot
import com.tamerin.sysmonitor.data.BatteryTimeEstimate
import com.tamerin.sysmonitor.ui.components.CircularGauge
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.delay

private fun emptyBattery() = BatterySnapshot(
    percent = 0f, isCharging = false, pluggedSource = "—", isWireless = false,
    healthLabel = "—", statusLabel = "—", temperatureC = 0f, voltageV = 0f,
    technology = "—", currentNowMa = 0L, averageCurrentMa = 0L,
    deltaDerivedCurrentMa = 0L, capacityMah = 0,
    capacityFullDesignMah = 0, capacityFullMah = 0, healthPercent = 0f,
    energyNwh = 0L, wattsNow = 0f, inputVoltageV = 0f, inputCurrentMa = 0L,
    wattsSource = "—", chargingSpeedLabel = "—", sensorTrust = false
)

@Composable
fun BatteryScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    var snap by remember { mutableStateOf(emptyBattery()) }
    var timeEstimate by remember {
        mutableStateOf(
            BatteryTimeEstimate(
                BatteryTimeEstimate.State.UNKNOWN, null,
                BatteryTimeEstimate.Confidence.NONE, "—"
            )
        )
    }
    var topConsumers by remember { mutableStateOf<List<BatteryConsumer>>(emptyList()) }
    var drainRates by remember {
        mutableStateOf(com.tamerin.sysmonitor.data.battery.DrainRates(null, null, 0, 0.0))
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                timeEstimate = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    BatteryEstimator.estimate(context)
                }
                kotlinx.coroutines.delay(5_000)
            }
        }
    }

    // Opportunistic sampler while BatteryScreen open — feeds the history DB
    // beyond the 15-min WorkManager cadence so drain rates stabilise faster.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                com.tamerin.sysmonitor.data.battery.BatteryHistoryTracker.sample(context)
                drainRates = com.tamerin.sysmonitor.data.battery.BatteryHistoryTracker
                    .computeDrainRates(context)
                kotlinx.coroutines.delay(60_000)
            }
        }
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                topConsumers = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    BatteryConsumerReader.read(context, limit = 10)
                }
                kotlinx.coroutines.delay(60_000)
            }
        }
    }

    val drainEngine = remember { DrainEngine() }
    var drainActive by remember { mutableStateOf(false) }
    var drainStartPct by remember { mutableFloatStateOf(0f) }
    var drainStartMs by remember { mutableLongStateOf(0L) }
    var drainRatePctPerHour by remember { mutableFloatStateOf(0f) }
    var drainAutoStopMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            if (drainEngine.running) drainEngine.stop(context, activity)
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                snap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    BatteryReader.read(context)
                }
                if (drainActive && drainEngine.running) {
                    val elapsedMs = System.currentTimeMillis() - drainStartMs
                    if (elapsedMs > 5_000) {
                        val dropped = drainStartPct - snap.percent
                        val hours = elapsedMs / 3_600_000f
                        drainRatePctPerHour = if (hours > 0) dropped / hours else 0f
                    }
                    if (snap.percent in 0.1f..10f) {
                        drainEngine.stop(context, activity)
                        drainActive = false
                        drainAutoStopMsg = context.getString(R.string.pwr_bat_auto_stop, snap.percent.toInt())
                    }
                }
                delay(1500)
            }
        }
    }

    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularGauge(
                percent = snap.percent,
                label = if (snap.isCharging) stringResource(R.string.pwr_bat_charging_source, snap.pluggedSource) else snap.pluggedSource,
                size = 180.dp,
                colorOverride = com.tamerin.sysmonitor.ui.theme.batteryGaugeColor(snap.percent)
            )
        }

        // === TIME-REMAINING / TIME-TO-FULL CARD ===
        TimeEstimateCard(timeEstimate, snap)

        // === SCENARIO ESTIMATES (Video / Screen / Idle) — AccuBattery style ===
        if (!snap.isCharging && snap.percent > 0) {
            ScenarioEstimatesCard(snap, drainRates)
        }

        // === TOP BATTERY CONSUMERS CARD ===
        TopConsumersCard(topConsumers)

        // === DRAIN MODE CARD ===
        StatCard(if (drainActive) stringResource(R.string.pwr_bat_drain_active_title) else stringResource(R.string.pwr_bat_drainer_title)) {
            Text(
                stringResource(R.string.pwr_bat_drain_desc),
                color = OnSurfaceMuted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.pwr_bat_drain_warn),
                color = GaugeRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(12.dp))

            if (drainActive) {
                Button(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                        drainEngine.stop(context, activity)
                        drainActive = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GaugeRed),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.pwr_bat_drain_stop)) }
                Spacer(Modifier.height(12.dp))
                KeyValueRow(
                    stringResource(R.string.pwr_bat_drain_rate),
                    if (drainRatePctPerHour > 0)
                        "${"%.1f".format(drainRatePctPerHour)} %/h"
                    else stringResource(R.string.pwr_bat_measuring)
                )
                KeyValueRow(
                    stringResource(R.string.pwr_bat_consumed_since_start),
                    "${"%.1f".format(drainStartPct - snap.percent)} %"
                )
                val elapsedSec = ((System.currentTimeMillis() - drainStartMs) / 1000).toInt()
                KeyValueRow(stringResource(R.string.pwr_bat_runtime), "${elapsedSec / 60} min ${elapsedSec % 60} s")
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.pwr_bat_active_channels), color = OnSurfaceMuted, fontSize = 11.sp,
                    fontWeight = FontWeight.Medium)
                drainEngine.activeChannels.forEach { ch ->
                    Text("• $ch", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.pwr_bat_gpu_burner),
                    color = OnSurfaceMuted, fontSize = 11.sp)
                GpuBurner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            } else {
                Button(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.DESTRUCTIVE)
                        drainStartPct = snap.percent
                        drainStartMs = System.currentTimeMillis()
                        drainRatePctPerHour = 0f
                        drainAutoStopMsg = null
                        drainEngine.start(context, activity, scope)
                        drainActive = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GaugeRed),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = snap.percent > 10f
                ) { Text(stringResource(R.string.pwr_bat_drain_activate)) }
                drainAutoStopMsg?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Accent, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold)
                }
                if (snap.percent <= 10f) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.pwr_bat_too_low),
                        color = OnSurfaceMuted, fontSize = 11.sp)
                }
            }
        }

        LeistungJetztCard(
            wattsNow = snap.wattsNow,
            currentNowMa = snap.currentNowMa,
            voltageV = snap.voltageV,
            averageCurrentMa = snap.averageCurrentMa,
            wattsSource = snap.wattsSource,
            isCharging = snap.isCharging,
            sensorTrust = snap.sensorTrust,
            inputVoltageV = snap.inputVoltageV,
            inputCurrentMa = snap.inputCurrentMa
        )
        LadeStatusCard(
            statusLabel = snap.statusLabel,
            pluggedSource = snap.pluggedSource,
            isWireless = snap.isWireless,
            chargingSpeedLabel = snap.chargingSpeedLabel,
            isCharging = snap.isCharging,
            wattsNow = snap.wattsNow,
            sensorTrust = snap.sensorTrust
        )
        AkkuZustandCard(
            percent = snap.percent,
            healthLabel = snap.healthLabel,
            healthPercent = snap.healthPercent,
            temperatureC = snap.temperatureC
        )
        TechnikCard(
            technology = snap.technology,
            voltageV = snap.voltageV,
            capacityMah = snap.capacityMah,
            capacityFullMah = snap.capacityFullMah,
            capacityFullDesignMah = snap.capacityFullDesignMah,
            energyNwh = snap.energyNwh
        )

        com.tamerin.sysmonitor.ui.components.ShizukuCard(
            title = stringResource(R.string.pwr_bat_shizuku_title),
            description = stringResource(R.string.pwr_bat_shizuku_desc)
        )

        StatCard(stringResource(R.string.pwr_bat_diag_title)) {
            Text(
                stringResource(R.string.pwr_bat_diag_desc),
                color = OnSurfaceMuted, fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            KeyValueRow("Status (BatteryManager)", if (snap.isCharging) stringResource(R.string.pwr_bat_charging) else stringResource(R.string.pwr_bat_discharging))
            KeyValueRow(stringResource(R.string.pwr_bat_best_source), snap.wattsSource)
            KeyValueRow(stringResource(R.string.pwr_bat_best_current), "${snap.currentNowMa} mA")
            KeyValueRow("Δ Charge-Counter", if (snap.deltaDerivedCurrentMa != 0L) "${snap.deltaDerivedCurrentMa} mA" else stringResource(R.string.pwr_bat_waiting_sampling))
            KeyValueRow("BatteryManager AVG", "${snap.averageCurrentMa} mA")
            if (snap.inputVoltageV > 0.5f) {
                KeyValueRow(
                    stringResource(R.string.pwr_bat_usb_pd_source),
                    "${"%.2f".format(snap.inputVoltageV)} V × ${snap.inputCurrentMa} mA = " +
                        "${"%.1f".format(snap.inputVoltageV * kotlin.math.abs(snap.inputCurrentMa.toFloat()) / 1000f)} W"
                )
            } else {
                val shizuku = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
                KeyValueRow(
                    "USB / PD",
                    if (!snap.isCharging) stringResource(R.string.pwr_bat_usb_pd_disconnected)
                    else if (!shizuku) stringResource(R.string.pwr_bat_usb_pd_locked_noshizuku)
                    else stringResource(R.string.pwr_bat_usb_pd_samsung_locked)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.pwr_bat_diag_hint),
                color = OnSurfaceMuted, fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun GpuBurner(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "burner")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "burner-t"
    )
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = kotlin.math.min(size.width, size.height) / 2f
        // 250 rotating shapes — heavy GPU + over-draw on purpose
        for (i in 0 until 250) {
            val phase = (i / 250f + t) * 2f * Math.PI.toFloat()
            val r = maxR * (0.15f + 0.85f * ((i * 17 % 100) / 100f))
            val x = cx + cos(phase.toDouble()).toFloat() * r * 0.6f
            val y = cy + sin(phase.toDouble()).toFloat() * r * 0.6f
            val hue = (i * 360f / 250f + t * 360f) % 360f
            drawCircle(
                color = hsv(hue, 1f, 1f, 0.3f),
                radius = 12f + (i % 7) * 2f,
                center = Offset(x, y),
                style = Stroke(width = 2f)
            )
        }
    }
}

/**
 * AccuBattery-style three-scenario estimate. Uses REAL battery capacity
 * (mAh) when available, combined with typical per-scenario current draw.
 * The 'now' line uses the LIVE measured current — that one is accurate.
 *
 * For truly history-learned drain rates (like AccuBattery) we'd need
 * weeks of per-session tracking which we don't (yet) do.
 */
@Composable
private fun ScenarioEstimatesCard(
    snap: BatterySnapshot,
    drainRates: com.tamerin.sysmonitor.data.battery.DrainRates
) {
    val fullMah = when {
        snap.capacityFullMah > 0 -> snap.capacityFullMah
        snap.capacityFullDesignMah > 0 -> snap.capacityFullDesignMah
        else -> 4500
    }
    val remainingMah = (fullMah * snap.percent / 100f).toInt()
    val hasValidLiveCurrent = snap.currentNowMa != 0L &&
        kotlin.math.abs(snap.currentNowMa) in 1L..20_000L

    StatCard(stringResource(R.string.pwr_bat_scenario_title, snap.percent.toInt())) {
        Text(
            stringResource(R.string.pwr_bat_scenario_basis, remainingMah, fullMah),
            color = OnSurfaceMuted, fontSize = 11.sp
        )
        Spacer(Modifier.height(6.dp))

        // Build scenarios: live current + measured (if learned) + reference
        val scenarios = mutableListOf<ScenarioRow>()
        if (hasValidLiveCurrent) {
            scenarios += ScenarioRow("⚡", stringResource(R.string.pwr_bat_scenario_live),
                kotlin.math.abs(snap.currentNowMa).toInt(), Source.LIVE)
        }
        if (drainRates.screenOnMa != null) {
            scenarios += ScenarioRow("📱", stringResource(R.string.pwr_bat_scenario_screen_on),
                drainRates.screenOnMa, Source.LEARNED)
        }
        if (drainRates.screenOffMa != null) {
            scenarios += ScenarioRow("💤", stringResource(R.string.pwr_bat_scenario_standby),
                drainRates.screenOffMa, Source.LEARNED)
        }
        // Reference scenarios (always shown so user can compare)
        scenarios += ScenarioRow("🎬", stringResource(R.string.pwr_bat_scenario_video_ref), 600, Source.REFERENCE)
        if (drainRates.screenOnMa == null) {
            scenarios += ScenarioRow("📱", stringResource(R.string.pwr_bat_scenario_screen_on_ref), 350, Source.REFERENCE)
        }
        scenarios += ScenarioRow("🌐", stringResource(R.string.pwr_bat_scenario_browser_ref), 200, Source.REFERENCE)
        if (drainRates.screenOffMa == null) {
            scenarios += ScenarioRow("💤", stringResource(R.string.pwr_bat_scenario_standby_ref), 40, Source.REFERENCE)
        }

        scenarios.forEach { sc ->
            val hours = remainingMah.toDouble() / sc.drainMa.coerceAtLeast(1)
            val totalMin = (hours * 60).toInt().coerceAtLeast(0)
            val h = totalMin / 60
            val m = totalMin % 60
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${sc.icon}  ${sc.label}", color = OnSurfaceMuted, fontSize = 13.sp)
                        if (sc.source == Source.LEARNED) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(GaugeGreen.copy(alpha = 0.2f),
                                        androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(stringResource(R.string.pwr_bat_measured), color = GaugeGreen,
                                    fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        "${sc.drainMa} mA",
                        color = OnSurfaceMuted, fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                Text(
                    when {
                        h >= 24 -> stringResource(R.string.pwr_bat_dur_days, h / 24, h % 24)
                        h > 0 -> stringResource(R.string.pwr_bat_dur_hours, h, m.toString().padStart(2, '0'))
                        else -> stringResource(R.string.pwr_bat_dur_mins, m)
                    },
                    color = when (sc.source) {
                        Source.LIVE -> Accent
                        Source.LEARNED -> GaugeGreen
                        Source.REFERENCE -> AccentSoft
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val footerBase = when {
            drainRates.screenOnMa != null && drainRates.screenOffMa != null ->
                stringResource(R.string.pwr_bat_scenario_measured_from,
                    drainRates.sampleCount, "%.1f".format(drainRates.coveredHours))
            drainRates.sampleCount < 10 ->
                stringResource(R.string.pwr_bat_scenario_collecting, drainRates.sampleCount)
            else ->
                stringResource(R.string.pwr_bat_scenario_insufficient)
        }
        val footerSuffix = if (hasValidLiveCurrent) stringResource(R.string.pwr_bat_scenario_live_suffix) else ""
        Text(
            footerBase + footerSuffix,
            color = OnSurfaceMuted, fontSize = 10.sp
        )
    }
}

private enum class Source { LIVE, LEARNED, REFERENCE }
private data class ScenarioRow(val icon: String, val label: String, val drainMa: Int, val source: Source)

// ===== Extracted snap-driven cards (stable per-field params) =====

@Composable
private fun LeistungJetztCard(
    wattsNow: Float,
    currentNowMa: Long,
    voltageV: Float,
    averageCurrentMa: Long,
    wattsSource: String,
    isCharging: Boolean,
    sensorTrust: Boolean,
    inputVoltageV: Float,
    inputCurrentMa: Long
) {
    StatCard(stringResource(R.string.pwr_bat_power_now_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (sensorTrust) "%.2f".format(wattsNow) else "—",
                    color = if (isCharging) GaugeGreen else Accent,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isCharging) stringResource(R.string.pwr_bat_w_input) else stringResource(R.string.pwr_bat_w_consumption),
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (sensorTrust) "%.0f".format(kotlin.math.abs(currentNowMa.toFloat())) else "—",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 32.sp, fontWeight = FontWeight.SemiBold
                )
                Text(stringResource(R.string.pwr_bat_ma_current), color = OnSurfaceMuted, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "%.2f".format(voltageV),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 32.sp, fontWeight = FontWeight.SemiBold
                )
                Text(stringResource(R.string.pwr_bat_v_voltage), color = OnSurfaceMuted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        KeyValueRow(
            stringResource(R.string.pwr_bat_direction),
            if (isCharging) stringResource(R.string.pwr_bat_direction_in) else stringResource(R.string.pwr_bat_direction_out)
        )
        KeyValueRow(
            stringResource(R.string.pwr_bat_average),
            if (sensorTrust) "%.0f mA".format(kotlin.math.abs(averageCurrentMa.toFloat())) else "—"
        )
        KeyValueRow(stringResource(R.string.pwr_bat_measure_source), wattsSource)
        if (isCharging && inputVoltageV > 0.5f) {
            Spacer(Modifier.height(8.dp))
            KeyValueRow(
                stringResource(R.string.pwr_bat_usb_input),
                "${"%.2f".format(inputVoltageV)} V × ${kotlin.math.abs(inputCurrentMa)} mA"
            )
        }
        if (!sensorTrust) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.pwr_bat_no_sensor),
                color = OnSurfaceMuted, fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun LadeStatusCard(
    statusLabel: String,
    pluggedSource: String,
    isWireless: Boolean,
    chargingSpeedLabel: String,
    isCharging: Boolean,
    wattsNow: Float,
    sensorTrust: Boolean
) {
    StatCard(stringResource(R.string.pwr_bat_charge_status_title)) {
        KeyValueRow(stringResource(R.string.pwr_bat_status), statusLabel)
        KeyValueRow(stringResource(R.string.pwr_bat_power_source), pluggedSource)
        KeyValueRow(stringResource(R.string.pwr_bat_wireless), if (isWireless) stringResource(R.string.pwr_bat_wireless_yes) else stringResource(R.string.pwr_bat_no))
        KeyValueRow(stringResource(R.string.pwr_bat_charge_speed), chargingSpeedLabel)
        if (isCharging && wattsNow >= 15f && sensorTrust) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.pwr_bat_fast_charging), color = GaugeGreen,
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AkkuZustandCard(
    percent: Float,
    healthLabel: String,
    healthPercent: Float,
    temperatureC: Float
) {
    StatCard(stringResource(R.string.pwr_bat_health_title)) {
        KeyValueRow(stringResource(R.string.pwr_bat_charge_level), "${percent.toInt()} %")
        KeyValueRow(stringResource(R.string.pwr_bat_health_system), healthLabel)
        if (healthPercent in 1f..100f) {
            KeyValueRow(
                stringResource(R.string.pwr_bat_wear),
                stringResource(R.string.pwr_bat_wear_value, "%.0f".format(healthPercent))
            )
        }
        KeyValueRow(stringResource(R.string.pwr_bat_temperature), "${"%.1f".format(temperatureC)} °C")
        if (temperatureC >= 45f) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.pwr_bat_overheated), color = GaugeRed,
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun TechnikCard(
    technology: String,
    voltageV: Float,
    capacityMah: Int,
    capacityFullMah: Int,
    capacityFullDesignMah: Int,
    energyNwh: Long
) {
    StatCard(stringResource(R.string.pwr_bat_tech_title)) {
        KeyValueRow(stringResource(R.string.pwr_bat_technology), technology)
        KeyValueRow(stringResource(R.string.pwr_bat_voltage), "${"%.2f".format(voltageV)} V")
        if (capacityMah > 0) KeyValueRow(stringResource(R.string.pwr_bat_capacity_now), "$capacityMah mAh")
        if (capacityFullMah > 0) KeyValueRow(stringResource(R.string.pwr_bat_capacity_full), "$capacityFullMah mAh")
        if (capacityFullDesignMah > 0) KeyValueRow(stringResource(R.string.pwr_bat_capacity_design), "$capacityFullDesignMah mAh")
        if (energyNwh > 0) KeyValueRow(stringResource(R.string.pwr_bat_energy_remaining), "${energyNwh / 1_000_000L} mWh")
    }
}

@Composable
private fun TimeEstimateCard(estimate: BatteryTimeEstimate, snap: BatterySnapshot) {
    val (title, color, prefix) = when (estimate.state) {
        BatteryTimeEstimate.State.CHARGING ->
            Triple(stringResource(R.string.pwr_bat_te_charging), AccentSoft, "geladen in")
        BatteryTimeEstimate.State.FULL ->
            Triple(stringResource(R.string.pwr_bat_te_full), GaugeGreen, "")
        BatteryTimeEstimate.State.DISCHARGING ->
            Triple(stringResource(R.string.pwr_bat_te_discharging), Accent, "noch")
        BatteryTimeEstimate.State.UNKNOWN ->
            Triple(stringResource(R.string.pwr_bat_te_unknown), OnSurfaceMuted, "")
    }
    val context = LocalContext.current
    StatCard(title) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    estimate.formatRemaining(context),
                    color = color,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Text(
                    when (estimate.confidence) {
                        BatteryTimeEstimate.Confidence.HIGH -> stringResource(R.string.pwr_bat_te_conf_high, estimate.source)
                        BatteryTimeEstimate.Confidence.MEDIUM -> stringResource(R.string.pwr_bat_te_conf_medium, estimate.source)
                        BatteryTimeEstimate.Confidence.LOW -> stringResource(R.string.pwr_bat_te_conf_low)
                        BatteryTimeEstimate.Confidence.NONE -> stringResource(R.string.pwr_bat_te_conf_none)
                    },
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val hasValidCurrent = snap.currentNowMa != 0L &&
            kotlin.math.abs(snap.currentNowMa) < 20_000L  // <20 A sanity
        val hasValidWatts = snap.wattsNow > 0.05f && snap.wattsNow < 200f
        when {
            hasValidWatts -> {
                KeyValueRow(
                    if (snap.isCharging) stringResource(R.string.pwr_bat_te_charge_power) else stringResource(R.string.pwr_bat_te_discharge_power),
                    "%.1f W".format(snap.wattsNow)
                )
            }
        }
        if (hasValidCurrent) {
            val mA = kotlin.math.abs(snap.currentNowMa)
            KeyValueRow(
                stringResource(R.string.pwr_bat_te_current_now),
                if (snap.isCharging) "+$mA mA" else "−$mA mA"
            )
        }
        if (!hasValidWatts && !hasValidCurrent) {
            Text(
                stringResource(R.string.pwr_bat_te_need_shizuku),
                color = OnSurfaceMuted, fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun TopConsumersCard(consumers: List<BatteryConsumer>) {
    StatCard(stringResource(R.string.pwr_bat_top_title)) {
        if (consumers.isEmpty()) {
            Text(
                stringResource(R.string.pwr_bat_top_loading),
                color = OnSurfaceMuted, fontSize = 11.sp
            )
            return@StatCard
        }
        val isEstimated = consumers.first().source == BatteryConsumer.Source.ESTIMATED
        Text(
            if (isEstimated) stringResource(R.string.pwr_bat_top_estimated)
            else stringResource(R.string.pwr_bat_top_real),
            color = OnSurfaceMuted, fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        val maxShare = consumers.first().sharePercent.coerceAtLeast(1f)
        consumers.forEach { c ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        c.displayName + if (c.isSystem) "  " + stringResource(R.string.pwr_bat_system_tag) else "",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        "${"%.1f".format(c.mAh)} mAh",
                        color = AccentSoft,
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color(0x22FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(c.sharePercent / maxShare)
                            .fillMaxHeight()
                            .background(if (c.isSystem) OnSurfaceMuted else Accent)
                    )
                }
                Text(
                    "${"%.1f".format(c.sharePercent)} %  ·  ${c.packageName}",
                    color = OnSurfaceMuted,
                    fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

private fun hsv(h: Float, s: Float, v: Float, alpha: Float): Color {
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
    return Color(r, g, b, alpha)
}
