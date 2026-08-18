package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.benchmark.StressEngine
import com.tamerin.sysmonitor.benchmark.ThrottleAnalyzer
import com.tamerin.sysmonitor.benchmark.ThrottleResult
import com.tamerin.sysmonitor.benchmark.ThrottleSample
import com.tamerin.sysmonitor.data.BatteryReader
import com.tamerin.sysmonitor.data.CpuReader
import com.tamerin.sysmonitor.data.ThermalReader
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.Sparkline
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import com.tamerin.sysmonitor.ui.theme.gaugeColor
import kotlinx.coroutines.delay

@Composable
fun StressTestScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { StressEngine() }
    var running by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableIntStateOf(0) }
    var battTempC by remember { mutableFloatStateOf(0f) }
    var cpuZoneTempC by remember { mutableFloatStateOf(Float.NaN) }
    var cpuZoneName by remember { mutableStateOf<String?>(null) }
    var cpuPct by remember { mutableFloatStateOf(0f) }
    var cpuSource by remember { mutableStateOf("—") }
    var avgFreqMhz by remember { mutableIntStateOf(0) }
    val tempHistory = remember { mutableStateListOf<Float>() }
    val cpuHistory = remember { mutableStateListOf<Float>() }
    val opsHistory = remember { mutableStateListOf<Float>() }
    val throttleSamples = remember { mutableStateListOf<ThrottleSample>() }
    var throttle by remember { mutableStateOf<ThrottleResult?>(null) }

    val activity = context as? android.app.Activity
    DisposableEffect(running) {
        if (running) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            engine.stop()
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            CpuReader.read(context, "stress")
        }
        // Durchsatz-Tracking: Delta des Iterationszaehlers der StressEngine pro Sekunde
        var lastOps = 0L
        var lastOpsMs = 0L
        while (true) {
            val tick = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val cpu = CpuReader.read(context, "stress")
                val batt = BatteryReader.read(context)
                val zones = ThermalReader.read(context)
                val hottest = ThermalReader.hottestCpuZone(zones)
                Triple(cpu, batt, hottest)
            }
            val cpu = tick.first
            val batt = tick.second
            val hottest = tick.third
            cpuPct = cpu.totalPercent
            cpuSource = cpu.source
            battTempC = batt.temperatureC
            cpuZoneTempC = hottest?.tempCelsius ?: Float.NaN
            cpuZoneName = hottest?.type
            avgFreqMhz = if (cpu.coreFrequenciesKHz.isNotEmpty()) {
                (cpu.coreFrequenciesKHz.filter { it > 0 }.average() / 1000).toInt()
            } else 0

            if (running) {
                elapsedSec++
                val displayTemp = if (!cpuZoneTempC.isNaN()) cpuZoneTempC else battTempC
                tempHistory.add(displayTemp)
                cpuHistory.add(cpuPct)
                if (tempHistory.size > 120) tempHistory.removeAt(0)
                if (cpuHistory.size > 120) cpuHistory.removeAt(0)

                val nowMs = android.os.SystemClock.elapsedRealtime()
                val ops = engine.opsCount()
                val delta = ops - lastOps
                if (lastOpsMs > 0L && delta >= 0L) {
                    val dtSec = (nowMs - lastOpsMs).coerceAtLeast(1L) / 1000.0
                    val opsPerSec = delta / dtSec
                    if (opsPerSec > 0.0) {
                        throttleSamples.add(
                            ThrottleSample(elapsedSec, opsPerSec, displayTemp, avgFreqMhz)
                        )
                        opsHistory.add((opsPerSec / 1_000_000.0).toFloat())
                        if (opsHistory.size > 120) opsHistory.removeAt(0)
                        throttle = ThrottleAnalyzer.analyze(throttleSamples)
                    }
                }
                lastOps = ops
                lastOpsMs = nowMs
            } else {
                // Nach dem Stopp den Zaehler neu einlesen, damit der naechste Lauf sauber startet
                lastOps = engine.opsCount()
                lastOpsMs = 0L
            }
            delay(1000)
        }
    }

    val displayTemp = if (!cpuZoneTempC.isNaN()) cpuZoneTempC else battTempC
    val tempLabel = if (cpuZoneName != null) "CPU/SoC ($cpuZoneName)" else "Akku-Sensor"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard("Stresstest") {
            Text(
                "Lädt alle CPU-Kerne dauerhaft mit 100 % aus. Das Gerät wird heiß — bei Throttling sinken Frequenz und Auslastung.",
                color = OnSurfaceMuted,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()
            Button(onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.DESTRUCTIVE)
                if (running) {
                    engine.stop(context)
                    running = false
                } else {
                    tempHistory.clear()
                    cpuHistory.clear()
                    opsHistory.clear()
                    throttleSamples.clear()
                    throttle = null
                    elapsedSec = 0
                    engine.start(context)
                    running = true
                }
            }) {
                Text(if (running) "Stresstest stoppen" else "Stresstest starten")
            }
            if (running) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Laufzeit: ${elapsedSec / 60}:${"%02d".format(elapsedSec % 60)}",
                    color = OnSurfaceMuted,
                    fontSize = 12.sp
                )
            }
        }

        StatCard("Live") {
            KeyValueRow("CPU-Auslastung", "${cpuPct.toInt()} %")
            KeyValueRow(
                "Quelle",
                when (cpuSource) {
                    "system" -> "System (/proc/stat)"
                    "process" -> "Prozess-CPU-Zeit (Fallback)"
                    else -> cpuSource
                }
            )
            KeyValueRow("Ø Frequenz", if (avgFreqMhz > 0) "$avgFreqMhz MHz" else "—")
            KeyValueRow(
                "Temperatur ($tempLabel)",
                if (displayTemp > 0f) "${"%.1f".format(displayTemp)} °C" else "—"
            )
            if (displayTemp >= 40f) {
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        displayTemp >= 80f -> "⚠ Throttling sehr wahrscheinlich"
                        displayTemp >= 60f -> "Heiß — Throttling möglich"
                        else -> "Warm"
                    },
                    color = if (displayTemp >= 80f) GaugeRed else GaugeOrange,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
            if (cpuZoneTempC.isNaN()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Hinweis: Keine CPU-Thermalzone lesbar — zeige stattdessen Akku-Temperatur (auf Emulatoren oft fix bei 25 °C).",
                    color = OnSurfaceMuted,
                    fontSize = 11.sp
                )
            }
        }

        throttle?.let { r ->
            StatCard("Sustained Performance") {
                Text(
                    "${r.sustainedPercent} %",
                    color = when {
                        r.sustainedRatio >= 0.95f -> GaugeGreen
                        r.sustainedRatio >= 0.85f -> GaugeOrange
                        else -> GaugeRed
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp
                )
                Text(r.verdict, color = OnSurfaceMuted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                KeyValueRow(
                    "Throttling ab",
                    r.throttleAtSec?.let { "${it / 60}:${"%02d".format(it % 60)} min" }
                        ?: "bisher nicht"
                )
                KeyValueRow("Schlechtester Wert", "${(r.minRatio * 100).toInt()} %")
                KeyValueRow(
                    "Takt Start → jetzt",
                    if (r.baselineFreqMhz > 0) "${r.baselineFreqMhz} → ${r.currentFreqMhz} MHz" else "—"
                )
                KeyValueRow(
                    "Spitzentemperatur",
                    if (!r.peakTempC.isNaN() && r.peakTempC > 0f) "${"%.1f".format(r.peakTempC)} °C" else "—"
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Gemessen wird der echte Durchsatz der Stress-Threads, nicht die " +
                        "Auslastung — die bleibt beim Drosseln bei 100 %. Die ersten " +
                        "${ThrottleAnalyzer.WARMUP_SEC} Sekunden sind Anlauf und zählen nicht.",
                    color = OnSurfaceMuted,
                    fontSize = 11.sp
                )
            }
        }

        if (opsHistory.size > 1) {
            StatCard("Verlauf Durchsatz") {
                Box(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                    Sparkline(
                        values = opsHistory.toList(),
                        color = MaterialTheme.colorScheme.primary,
                        minY = 0f
                    )
                }
                Text(
                    "Mio. Iterationen/s · höher ist besser",
                    color = OnSurfaceMuted,
                    fontSize = 11.sp
                )
            }
        }

        if (cpuHistory.size > 1) {
            StatCard("Verlauf CPU-Last") {
                Box(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                    Sparkline(
                        values = cpuHistory.toList(),
                        color = gaugeColor(cpuHistory.last()),
                        minY = 0f,
                        maxY = 100f
                    )
                }
            }
        }
        if (tempHistory.size > 1) {
            StatCard("Verlauf Temperatur") {
                Box(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                    Sparkline(
                        values = tempHistory.toList(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    "Min ${"%.1f".format(tempHistory.min())} °C  ·  Max ${"%.1f".format(tempHistory.max())} °C",
                    color = OnSurfaceMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}
