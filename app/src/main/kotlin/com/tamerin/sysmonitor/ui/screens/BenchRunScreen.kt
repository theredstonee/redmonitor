package com.tamerin.sysmonitor.ui.screens

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.benchmark.BenchmarkRepository
import com.tamerin.sysmonitor.benchmark.BenchmarkService
import com.tamerin.sysmonitor.benchmark.BenchmarkState
import com.tamerin.sysmonitor.benchmark.db.BenchmarkType
import com.tamerin.sysmonitor.data.BatteryReader
import com.tamerin.sysmonitor.data.ThermalReader
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun BenchRunScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val state by BenchmarkRepository.state.collectAsState()
    val immersive = com.tamerin.sysmonitor.LocalImmersive.current

    DisposableEffect(Unit) {
        immersive.value = true
        onDispose { immersive.value = false }
    }

    // Live ticker — keeps seconds counter moving between sparse service callbacks.
    var liveElapsedSec by remember { mutableIntStateOf(0) }
    var liveStartMs by remember { mutableLongStateOf(0L) }

    // Live temperature samplers — CPU% removed because during a bench it's
    // either pegged at 100 (uninteresting) or read inaccurately due to UI
    // thread starvation. Temperatures stay useful for spotting thermal throttle.
    var battTempC by remember { mutableFloatStateOf(Float.NaN) }
    var thermalC by remember { mutableFloatStateOf(Float.NaN) }

    val running = state is BenchmarkState.Running

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        liveStartMs = SystemClock.elapsedRealtime()
        liveElapsedSec = 0
        while (running) {
            liveElapsedSec = ((SystemClock.elapsedRealtime() - liveStartMs) / 1000).toInt()
            delay(250)
        }
    }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (running) {
            val batt = withContext(Dispatchers.IO) { BatteryReader.read(context).temperatureC }
            val zones = withContext(Dispatchers.IO) { ThermalReader.read(context) }
            val hottest = withContext(Dispatchers.IO) { ThermalReader.hottestCpuZone(zones)?.tempCelsius }
            battTempC = batt
            // /sys/class/thermal is locked on many Android 10+ devices. If we
            // get nothing back, surface the hottest readable Zone of any kind,
            // and as last resort the battery temperature (close to skin temp).
            thermalC = when {
                hottest != null -> hottest
                zones.any { !it.tempCelsius.isNaN() } ->
                    zones.filter { !it.tempCelsius.isNaN() && it.tempCelsius in 5f..150f }
                        .maxByOrNull { it.tempCelsius }?.tempCelsius ?: Float.NaN
                !batt.isNaN() && batt > 0 -> batt
                else -> Float.NaN
            }
            delay(1500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1A0303), Color.Black),
                    radius = 1400f
                )
            )
    ) {
        when (val s = state) {
            is BenchmarkState.Idle -> {
                Text(
                    "Kein Benchmark aktiv",
                    color = OnSurfaceMuted,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                ExitButton(onExit, "Zurück")
            }

            is BenchmarkState.Running -> RunningContent(
                state = s,
                liveElapsedSec = liveElapsedSec,
                battTempC = battTempC,
                thermalC = thermalC,
                onStop = {
                    BenchmarkService.stop(context)
                    onExit()
                }
            )

            is BenchmarkState.Done -> DoneContent(s, onExit)

            is BenchmarkState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Fehler", color = GaugeRed, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(s.message, color = OnSurfaceMuted, fontSize = 14.sp)
                }
                ExitButton(onExit, "Schließen")
            }
        }
    }
}

@Composable
private fun BoxScope.RunningContent(
    state: BenchmarkState.Running,
    liveElapsedSec: Int,
    battTempC: Float,
    thermalC: Float,
    onStop: () -> Unit
) {
    val displayedSec = maxOf(state.doneSeconds, liveElapsedSec)
    val total = state.totalSeconds.coerceAtLeast(1)
    val progress = (displayedSec.toFloat() / total).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(500),
        label = "bench-progress"
    )
    val remainingSec = (total - displayedSec).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // Bottom padding reserves space so the fixed Abbrechen button
            // (rendered via BoxScope.align below) never overlaps the last row.
            .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            state.type.displayName.uppercase(),
            color = AccentSoft,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "BENCHMARK",
            color = Accent,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 6.sp
        )
        Spacer(Modifier.height(24.dp))

        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(220.dp)) {
                val stroke = 18.dp.toPx()
                val diameter = size.minDimension - stroke
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                drawArc(
                    color = Color(0xFF222222),
                    startAngle = 135f, sweepAngle = 270f, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = Accent,
                    startAngle = 135f, sweepAngle = 270f * animatedProgress, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${(animatedProgress * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 50.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "${displayedSec}s · ${formatRemaining(remainingSec)}",
                    color = OnSurfaceMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            state.phaseLabel,
            color = AccentSoft,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.height(24.dp))

        // Live temperatures during the run
        TemperatureRow(battTempC, thermalC)

        Spacer(Modifier.height(20.dp))

        if (state.partialSubScores.isNotEmpty()) {
            Text(
                "ABGESCHLOSSENE SUB-TESTS",
                color = OnSurfaceMuted,
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(8.dp))
            state.partialSubScores.forEach { sub ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(sub.name, color = Color.White, fontSize = 13.sp)
                    Text(
                        "${sub.singleScore} / ${sub.multiScore}",
                        color = AccentSoft,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // Fixed Abbrechen — pinned to bottom center, always reachable.
    // Solid black backdrop so it stays readable over any background.
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xCC000000), Color(0xFF000000))
                )
            )
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        OutlinedButton(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Abbrechen", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TemperatureRow(battTempC: Float, thermalC: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        PressureCell(
            label = "BATT °C",
            value = if (battTempC.isNaN()) "—" else "%.1f".format(battTempC),
            sub = null,
            tint = thermalColor(battTempC)
        )
        PressureCell(
            label = "CPU °C",
            value = if (thermalC.isNaN()) "—" else "%.1f".format(thermalC),
            sub = null,
            tint = thermalColor(thermalC)
        )
    }
}

@Composable
private fun PressureCell(label: String, value: String, sub: String?, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = OnSurfaceMuted, fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            color = tint,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        if (sub != null) {
            Text(sub, color = OnSurfaceMuted, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace)
        }
    }
}

private fun thermalColor(c: Float): Color = when {
    c.isNaN() -> OnSurfaceMuted
    c < 35f -> GaugeGreen
    c < 42f -> AccentSoft
    c < 48f -> GaugeOrange
    else -> GaugeRed
}

private fun formatRemaining(remainingSec: Int): String {
    return when {
        remainingSec <= 0 -> "fast fertig"
        remainingSec < 60 -> "~${remainingSec}s übrig"
        else -> "~${remainingSec / 60}m ${remainingSec % 60}s übrig"
    }
}

@Composable
private fun BoxScope.DoneContent(state: BenchmarkState.Done, onExit: () -> Unit) {
    val scoreColor = when {
        state.totalScore < 100_000 -> GaugeOrange
        state.totalScore < 500_000 -> AccentSoft
        else -> GaugeGreen
    }
    Column(
        modifier = Modifier.align(Alignment.Center).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            state.type.displayName.uppercase(),
            color = OnSurfaceMuted,
            fontSize = 14.sp,
            letterSpacing = 4.sp
        )
        Spacer(Modifier.height(12.dp))
        Text("SCORE", color = OnSurfaceMuted, fontSize = 12.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "%,d".format(state.totalScore),
            color = scoreColor,
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(24.dp))
        Text("Gespeichert in Verlauf", color = OnSurfaceMuted, fontSize = 12.sp)
    }
    OutlinedButton(
        onClick = onExit,
        modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)
    ) { Text("Fertig", color = Color.White) }
}

@Composable
private fun BoxScope.ExitButton(onExit: () -> Unit, label: String) {
    OutlinedButton(
        onClick = onExit,
        modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)
    ) { Text(label, color = Color.White) }
}
