package com.tamerin.sysmonitor.ui.screens

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
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

    val renderer = remember {
        BenchmarkRenderer { current, avg, mn, mx, sec ->
            fpsCurrent = current
            fpsAverage = avg
            fpsMin = mn
            fpsMax = mx
            elapsed = sec
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        StatCard("GPU-FPS Benchmark") {
            Text(
                "Rendert 200 sich drehende Quadrate und misst die durchschnittliche Framerate.",
                color = OnSurfaceMuted,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                running = !running
                if (running) {
                    renderer.reset()
                    elapsed = 0
                }
            }) {
                Text(if (running) "Stopp" else "Start (10 s laufen lassen)")
            }
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

        Spacer(Modifier.height(16.dp))

        if (running) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        setEGLContextClientVersion(2)
                        setRenderer(renderer)
                        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    }
                }
            )
        }
    }
}

@Composable
private fun FpsValue(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            color = Accent,
            fontSize = 42.sp
        )
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

    fun reset() {
        frames = 0
        lastTimeNs = 0L
        startNs = 0L
        minFps = Int.MAX_VALUE
        maxFps = 0
        totalFrames = 0
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.043f, 0.058f, 0.078f, 1f)
        startNs = System.nanoTime()
        lastTimeNs = startNs
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        rot += 0.02f

        // Light fragment workload: draw lots of points with scissor moving
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        for (i in 0 until 200) {
            val a = rot + i * 0.05f
            val x = (cos(a.toDouble()) * 200 + 250).toInt()
            val y = (sin(a.toDouble()) * 200 + 250).toInt()
            GLES20.glScissor(x.coerceAtLeast(0), y.coerceAtLeast(0), 12, 12)
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
