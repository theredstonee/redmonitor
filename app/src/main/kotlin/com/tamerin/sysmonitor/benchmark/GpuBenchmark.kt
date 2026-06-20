package com.tamerin.sysmonitor.benchmark

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import androidx.compose.runtime.Immutable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.random.Random

enum class GpuPhase(val label: String, val seconds: Int, val reference: Double) {
    VERTEX("Vertex-Throughput", 15, BenchmarkReferences.GPU_VERTEX_REF_PER_SEC),
    FILL_RATE("Fill-Rate (Overdraw)", 15, BenchmarkReferences.GPU_FILLRATE_REF_GPIXELS * 1_000_000_000.0),
    SHADER("Shader-Complexity", 15, BenchmarkReferences.GPU_SHADER_REF_FRAMES)
}

@Immutable
data class GpuSubScore(
    val phase: GpuPhase,
    val rawThroughput: Double,
    val unit: String,
    val avgFps: Int,
    val minFps: Int,
    val maxFps: Int,
    val score: Int
)

@Immutable
data class GpuBenchmarkResult(
    val subScores: List<GpuSubScore>,
    val totalScore: Int,
    val durationSec: Int
)

/**
 * Real GLES2 benchmark with three independently scored sub-tests:
 *
 *  - VERTEX: 50,000 rotating triangles per frame, simple vertex+fragment shader.
 *    Measures raw vertex-shader + primitive-assembly throughput.
 *
 *  - FILL_RATE: 30 full-screen quads with alpha blending (heavy overdraw).
 *    Measures ROP throughput / memory bandwidth for the framebuffer.
 *
 *  - SHADER: single full-screen quad with a fragment shader that runs 32
 *    iterations of a folding fractal (abs, dot, sin, cos, normalize per pixel).
 *    Measures raw ALU throughput per fragment.
 *
 * Each sub-test renders for [GpuPhase.seconds] seconds, fed by [GpuBenchmark]'s
 * GLSurfaceView.Renderer. The renderer self-rotates the phase and reports back
 * via [onPhaseComplete] / [onFrame].
 */
class GpuBenchmarkRenderer(
    private val onFrame: (phase: GpuPhase, currentFps: Int, elapsedSec: Int) -> Unit,
    private val onPhaseComplete: (GpuSubScore) -> Unit,
    private val onFinish: (GpuBenchmarkResult) -> Unit
) : GLSurfaceView.Renderer {

    @Volatile var cancelled = false

    private var width = 0
    private var height = 0
    private var phaseIndex = 0
    private var phaseStartNs = 0L
    private var phaseFrames = 0
    private var phaseMinFps = Int.MAX_VALUE
    private var phaseMaxFps = 0
    private var lastFpsSampleNs = 0L
    private var framesSinceFpsSample = 0
    private val results = mutableListOf<GpuSubScore>()
    private val benchStartNs = System.nanoTime()

    private var vertexProgram = 0
    private var quadProgram = 0
    private var shaderProgram = 0
    private var triBuffer: FloatBuffer? = null
    private var quadBuffer: FloatBuffer? = null
    private val triCount = 50_000
    private val triVertices = triCount * 3 * 4 // 3 verts × {x,y,r,g}

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        vertexProgram = buildProgram(VERTEX_PHASE_VS, VERTEX_PHASE_FS)
        quadProgram = buildProgram(QUAD_VS, QUAD_FS)
        shaderProgram = buildProgram(QUAD_VS, FRACTAL_FS)

        triBuffer = makeTriBuffer()
        quadBuffer = makeQuadBuffer()

        phaseStartNs = System.nanoTime()
        lastFpsSampleNs = phaseStartNs
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
        width = w
        height = h
    }

    override fun onDrawFrame(gl: GL10?) {
        if (cancelled) return
        if (phaseIndex >= GpuPhase.values().size) return

        val phase = GpuPhase.values()[phaseIndex]
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val time = ((System.nanoTime() - benchStartNs) / 1e9).toFloat()
        when (phase) {
            GpuPhase.VERTEX -> drawVertexPhase(time)
            GpuPhase.FILL_RATE -> drawFillRatePhase(time)
            GpuPhase.SHADER -> drawShaderPhase(time)
        }
        phaseFrames++
        framesSinceFpsSample++

        val now = System.nanoTime()
        val sinceSample = now - lastFpsSampleNs
        if (sinceSample >= 1_000_000_000L) {
            val fps = (framesSinceFpsSample * 1_000_000_000L / sinceSample).toInt()
            if (fps in 1..999) {
                if (fps < phaseMinFps) phaseMinFps = fps
                if (fps > phaseMaxFps) phaseMaxFps = fps
            }
            lastFpsSampleNs = now
            framesSinceFpsSample = 0
            val elapsedSec = ((now - phaseStartNs) / 1_000_000_000L).toInt()
            onFrame(phase, fps, elapsedSec)
        }

        val phaseElapsedNs = now - phaseStartNs
        if (phaseElapsedNs >= phase.seconds * 1_000_000_000L) {
            // Phase complete — compute sub-score
            val elapsedSec = phaseElapsedNs / 1e9
            val avgFps = (phaseFrames / elapsedSec).toInt()
            val (raw, unit) = when (phase) {
                GpuPhase.VERTEX -> (phaseFrames.toDouble() * triCount * 3 / elapsedSec) to "Vertices/s"
                GpuPhase.FILL_RATE -> {
                    val pixelsPerFrame = width.toLong() * height * 30L // 30 overdraw layers
                    (pixelsPerFrame.toDouble() * phaseFrames / elapsedSec) to "Pixel/s"
                }
                GpuPhase.SHADER -> (phaseFrames / elapsedSec * 100) to "Frames×100/s"
            }
            val sub = GpuSubScore(
                phase = phase,
                rawThroughput = raw,
                unit = unit,
                avgFps = avgFps,
                minFps = if (phaseMinFps == Int.MAX_VALUE) avgFps else phaseMinFps,
                maxFps = phaseMaxFps,
                score = normalize(raw, phase.reference)
            )
            results += sub
            onPhaseComplete(sub)

            // Advance phase
            phaseIndex++
            phaseStartNs = now
            phaseFrames = 0
            phaseMinFps = Int.MAX_VALUE
            phaseMaxFps = 0
            lastFpsSampleNs = now
            framesSinceFpsSample = 0

            if (phaseIndex >= GpuPhase.values().size) {
                val totalScore = if (results.isNotEmpty()) results.sumOf { it.score } / results.size else 0
                val totalSec = GpuPhase.values().sumOf { it.seconds }
                onFinish(GpuBenchmarkResult(results.toList(), totalScore, totalSec))
            }
        }
    }

    // ===== Phase rendering =====

    private fun drawVertexPhase(time: Float) {
        GLES20.glUseProgram(vertexProgram)
        val aPos = GLES20.glGetAttribLocation(vertexProgram, "a_pos")
        val aCol = GLES20.glGetAttribLocation(vertexProgram, "a_col")
        val uTime = GLES20.glGetUniformLocation(vertexProgram, "u_time")
        GLES20.glUniform1f(uTime, time)

        val buf = triBuffer!!
        buf.position(0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 4 * 4, buf)

        buf.position(2)
        GLES20.glEnableVertexAttribArray(aCol)
        GLES20.glVertexAttribPointer(aCol, 2, GLES20.GL_FLOAT, false, 4 * 4, buf)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, triCount * 3)

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aCol)
    }

    private fun drawFillRatePhase(time: Float) {
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(quadProgram)
        val aPos = GLES20.glGetAttribLocation(quadProgram, "a_pos")
        val uColor = GLES20.glGetUniformLocation(quadProgram, "u_color")
        val buf = quadBuffer!!
        buf.position(0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, buf)
        for (i in 0 until 30) {
            val a = time + i * 0.1f
            val r = (kotlin.math.sin(a) + 1) / 2
            val g = (kotlin.math.cos(a * 1.3f) + 1) / 2
            val b = (kotlin.math.sin(a * 0.7f) + 1) / 2
            GLES20.glUniform4f(uColor, r, g, b, 0.08f)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun drawShaderPhase(time: Float) {
        GLES20.glUseProgram(shaderProgram)
        val aPos = GLES20.glGetAttribLocation(shaderProgram, "a_pos")
        val uTime = GLES20.glGetUniformLocation(shaderProgram, "u_time")
        val uRes = GLES20.glGetUniformLocation(shaderProgram, "u_res")
        GLES20.glUniform1f(uTime, time)
        GLES20.glUniform2f(uRes, width.toFloat(), height.toFloat())
        val buf = quadBuffer!!
        buf.position(0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
    }

    // ===== Shader / VBO helpers =====

    private fun buildProgram(vs: String, fs: String): Int {
        val v = compile(GLES20.GL_VERTEX_SHADER, vs)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        return s
    }

    private fun makeTriBuffer(): FloatBuffer {
        val rand = Random(42)
        val data = FloatArray(triVertices)
        var i = 0
        repeat(triCount) {
            val cx = rand.nextFloat() * 2f - 1f
            val cy = rand.nextFloat() * 2f - 1f
            val sz = 0.005f + rand.nextFloat() * 0.015f
            val r = rand.nextFloat()
            val g = rand.nextFloat()
            for (v in 0 until 3) {
                val angle = v * 2.094f
                data[i++] = cx + sz * kotlin.math.cos(angle)
                data[i++] = cy + sz * kotlin.math.sin(angle)
                data[i++] = r
                data[i++] = g
            }
        }
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(data); position(0) }
    }

    private fun makeQuadBuffer(): FloatBuffer {
        val data = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(data); position(0) }
    }
}

// ===== Shaders =====

private const val VERTEX_PHASE_VS = """
attribute vec2 a_pos;
attribute vec2 a_col;
uniform float u_time;
varying vec3 v_col;
void main() {
    float a = u_time + a_pos.x * 3.0 + a_pos.y * 2.0;
    mat2 r = mat2(cos(a), -sin(a), sin(a), cos(a));
    vec2 p = r * a_pos;
    gl_Position = vec4(p, 0.0, 1.0);
    v_col = vec3(a_col, 1.0 - a_col.x);
}
"""

private const val VERTEX_PHASE_FS = """
precision mediump float;
varying vec3 v_col;
void main() { gl_FragColor = vec4(v_col, 1.0); }
"""

private const val QUAD_VS = """
attribute vec2 a_pos;
varying vec2 v_uv;
void main() {
    v_uv = (a_pos + 1.0) * 0.5;
    gl_Position = vec4(a_pos, 0.0, 1.0);
}
"""

private const val QUAD_FS = """
precision mediump float;
uniform vec4 u_color;
void main() { gl_FragColor = u_color; }
"""

private const val FRACTAL_FS = """
precision highp float;
varying vec2 v_uv;
uniform float u_time;
uniform vec2 u_res;
void main() {
    vec2 p = (v_uv - 0.5) * 4.0;
    p.x *= u_res.x / u_res.y;
    vec3 col = vec3(0.0);
    for (int i = 0; i < 32; i++) {
        float fi = float(i);
        p = abs(p) / max(dot(p, p), 0.001) - 0.7;
        col += vec3(
            sin(fi * 0.31 + u_time),
            cos(fi * 0.47 + u_time * 1.3),
            sin(fi * 0.71 + u_time * 0.6)
        ) * 0.04;
    }
    col = abs(sin(col * 1.8));
    gl_FragColor = vec4(col, 1.0);
}
"""
