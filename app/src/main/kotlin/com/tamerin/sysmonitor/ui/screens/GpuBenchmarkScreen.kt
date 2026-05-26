package com.tamerin.sysmonitor.ui.screens

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GpuBenchmarkScreen() {
    var fpsCurrent by remember { mutableIntStateOf(0) }
    var fpsAverage by remember { mutableIntStateOf(0) }
    var fpsMin by remember { mutableIntStateOf(0) }
    var fpsMax by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    val renderer = remember {
        BenchmarkRenderer { current, avg, mn, mx, sec ->
            fpsCurrent = current
            fpsAverage = avg
            fpsMin = mn
            fpsMax = mx
            elapsed = sec
        }
    }

    if (running) {
        // Fullscreen render mode — GLSurfaceView fills entire display, FPS overlay floats on top
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        setEGLContextClientVersion(2)
                        setRenderer(renderer)
                        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    }
                }
            )
            // FPS overlay top-left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    Text("$fpsCurrent fps", color = Accent, fontSize = 32.sp)
                    Text("avg $fpsAverage · min $fpsMin · max $fpsMax",
                        color = Color.White, fontSize = 12.sp)
                    Text("${elapsed}s", color = OnSurfaceMuted, fontSize = 11.sp)
                }
            }
            // Stop button bottom-center
            OutlinedButton(
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    running = false
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
            ) { Text("Stopp", color = Color.White) }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        StatCard("GPU-FPS Benchmark") {
            Text(
                "Rendert 200 sich drehende Quadrate über den kompletten Bildschirm und misst die Framerate.",
                color = OnSurfaceMuted,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                renderer.reset()
                elapsed = 0
                running = true
            }) { Text("Vollbild-Test starten") }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FpsValue("Aktuell", fpsCurrent)
            FpsValue("Schnitt", fpsAverage)
        }
        Spacer(Modifier.height(16.dp))
        StatCard("Stats") {
            KeyValueRow("Min FPS", fpsMin.toString())
            KeyValueRow("Max FPS", fpsMax.toString())
            KeyValueRow("Laufzeit", "$elapsed s")
        }
    }
}

@Composable
private fun FpsValue(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), color = Accent, fontSize = 42.sp)
        Text(label, color = OnSurfaceMuted, fontSize = 12.sp)
    }
}

private class BenchmarkRenderer(
    val onSample: (current: Int, avg: Int, min: Int, max: Int, elapsedSec: Int) -> Unit
) : GLSurfaceView.Renderer {

    private var frames = 0
    private var lastTimeNs = 0L
    private var startNs = 0L
    private var minFps = Int.MAX_VALUE
    private var maxFps = 0
    private var totalFrames = 0
    private var rot = 0f
    private var width = 0
    private var height = 0

    fun reset() {
        frames = 0
        lastTimeNs = 0L
        startNs = 0L
        minFps = Int.MAX_VALUE
        maxFps = 0
        totalFrames = 0
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        startNs = System.nanoTime()
        lastTimeNs = startNs
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
        width = w
        height = h
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        rot += 0.02f

        // Distribute shapes across the actual screen size
        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 3f).coerceAtLeast(100f)
        val shapeSize = (minOf(width, height) / 30).coerceAtLeast(8)

        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        for (i in 0 until 200) {
            val a = rot + i * 0.05f
            val r = radius * (0.3f + 0.7f * ((i % 11) / 10f))
            val x = (cx + cos(a.toDouble()) * r).toInt()
            val y = (cy + sin(a.toDouble()) * r).toInt()
            GLES20.glScissor(
                x.coerceIn(0, width - shapeSize),
                y.coerceIn(0, height - shapeSize),
                shapeSize, shapeSize
            )
            GLES20.glClearColor(
                ((sin(a.toDouble()) + 1) / 2).toFloat(),
                ((cos(a.toDouble()) + 1) / 2).toFloat(),
                0.7f,
                1f
            )
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)

        frames++
        totalFrames++
        val now = System.nanoTime()
        val dt = now - lastTimeNs
        if (dt >= 1_000_000_000L) {
            val fps = (frames * 1_000_000_000L / dt).toInt()
            if (fps in 1..999) {
                if (fps < minFps) minFps = fps
                if (fps > maxFps) maxFps = fps
            }
            val elapsedSec = ((now - startNs) / 1_000_000_000L).toInt().coerceAtLeast(1)
            val avg = totalFrames / elapsedSec
            onSample(fps, avg, if (minFps == Int.MAX_VALUE) 0 else minFps, maxFps, elapsedSec)
            frames = 0
            lastTimeNs = now
        }
    }
}
