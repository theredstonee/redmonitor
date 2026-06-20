package com.tamerin.sysmonitor.ui.screens

import android.content.Intent
import android.opengl.GLSurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.viewinterop.AndroidView
import com.tamerin.sysmonitor.BuildConfig
import com.tamerin.sysmonitor.benchmark.BenchmarkRepository
import com.tamerin.sysmonitor.benchmark.GpuBenchmarkRenderer
import com.tamerin.sysmonitor.benchmark.GpuBenchmarkResult
import com.tamerin.sysmonitor.benchmark.GpuPhase
import com.tamerin.sysmonitor.benchmark.GpuSubScore
import com.tamerin.sysmonitor.benchmark.db.BenchmarkDatabase
import com.tamerin.sysmonitor.benchmark.db.BenchmarkRun
import com.tamerin.sysmonitor.benchmark.db.BenchmarkSubScore
import com.tamerin.sysmonitor.benchmark.db.BenchmarkType
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GpuBenchmarkScreen() {
    val context = LocalContext.current
    val immersive = com.tamerin.sysmonitor.LocalImmersive.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var currentPhase by remember { mutableStateOf<GpuPhase?>(null) }
    var currentFps by remember { mutableIntStateOf(0) }
    var phaseElapsed by remember { mutableIntStateOf(0) }
    var partialScores by remember { mutableStateOf<List<GpuSubScore>>(emptyList()) }
    var result by remember { mutableStateOf<GpuBenchmarkResult?>(null) }
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    DisposableEffect(running) {
        immersive.value = running
        onDispose { immersive.value = false }
    }

    if (running) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        setEGLContextClientVersion(2)
                        setRenderer(GpuBenchmarkRenderer(
                            onFrame = { phase, fps, elapsed ->
                                currentPhase = phase
                                currentFps = fps
                                phaseElapsed = elapsed
                            },
                            onPhaseComplete = { sub ->
                                partialScores = partialScores + sub
                            },
                            onFinish = { res ->
                                result = res
                                running = false
                                scope.launch { saveGpuResult(context, res) }
                            }
                        ))
                        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    }
                }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        currentPhase?.label ?: "Starte…",
                        color = AccentSoft, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$currentFps fps",
                        color = Accent, fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "Phase $phaseElapsed / ${currentPhase?.seconds ?: 0}s",
                        color = Color.White, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (partialScores.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        partialScores.forEach { p ->
                            Text(
                                "✓ ${p.phase.label}: ${p.score}",
                                color = GaugeGreen,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    running = false
                    partialScores = emptyList()
                    currentPhase = null
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
            ) { Text("Abbrechen", color = Color.White) }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard("GPU-Benchmark (echte GLES2-Shader)") {
            Text(
                "3 Sub-Tests à 15 s mit echten OpenGL-ES-2-Shadern (Vertex- und Fragment-Shader, VBOs). " +
                    "Im Gegensatz zum alten Scissor-Trick wird hier wirklich gerendert.",
                color = OnSurfaceMuted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            GpuPhase.values().forEach { phase ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("· ${phase.label}", color = AccentSoft, fontSize = 12.sp)
                    Text("${phase.seconds} s", color = OnSurfaceMuted, fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "VERTEX: 50.000 rotierende Dreiecke pro Frame · " +
                    "FILL-RATE: 30× Vollbild-Quad mit Alpha-Blending (Overdraw) · " +
                    "SHADER: 32-Iter Fraktal-Fragment-Shader.",
                color = OnSurfaceMuted, fontSize = 11.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                        partialScores = emptyList()
                        result = null
                        running = true
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Vollbild-Test starten", fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                        context.startActivity(
                            Intent(context, com.tamerin.sysmonitor.ui.BenchHistoryStandaloneActivity::class.java)
                        )
                    }
                ) { Text("Verlauf") }
            }
        }

        result?.let { r ->
            StatCard("Gesamt-Score") {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()) {
                    val color = when {
                        r.totalScore < 100_000 -> GaugeOrange
                        r.totalScore < 500_000 -> AccentSoft
                        else -> GaugeGreen
                    }
                    Text(
                        "%,d".format(r.totalScore),
                        color = color, fontSize = 48.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text("Mittelwert aus 3 Sub-Tests", color = OnSurfaceMuted, fontSize = 11.sp)
                }
            }
            r.subScores.forEach { sub ->
                StatCard(sub.phase.label) {
                    KeyValueRow("Score", "%,d".format(sub.score))
                    KeyValueRow("Avg FPS", sub.avgFps.toString())
                    KeyValueRow("Min FPS", sub.minFps.toString())
                    KeyValueRow("Max FPS", sub.maxFps.toString())
                    KeyValueRow("Throughput", "${"%.1f".format(sub.rawThroughput)} ${sub.unit}")
                }
            }
        }
    }
}

/** Persist the GPU result into Room (the FGS-based path doesn't apply here
 *  because GLSurfaceView needs a Window/Surface, so we save inline). */
private suspend fun saveGpuResult(
    context: android.content.Context,
    res: GpuBenchmarkResult
) = withContext(Dispatchers.IO) {
    val dao = BenchmarkDatabase.get(context).benchmarkDao()
    val run = BenchmarkRun(
        type = BenchmarkType.GPU,
        timestamp = System.currentTimeMillis(),
        totalScore = res.totalScore,
        durationMs = res.durationSec * 1000L,
        phaseSeconds = res.durationSec / res.subScores.size.coerceAtLeast(1),
        deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
        androidSdk = android.os.Build.VERSION.SDK_INT,
        appVersion = BuildConfig.VERSION_NAME
    )
    val subs = res.subScores.map { sub ->
        BenchmarkSubScore(
            runId = 0,
            name = sub.phase.label,
            singleScore = sub.avgFps,
            multiScore = sub.score,
            singleOpsPerSec = sub.rawThroughput,
            multiOpsPerSec = sub.minFps.toDouble(),
            unit = sub.unit
        )
    }
    dao.insertRunWithSubs(run, subs)
    BenchmarkRepository.reset()
}
