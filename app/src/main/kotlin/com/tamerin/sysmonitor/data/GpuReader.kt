package com.tamerin.sysmonitor.data

import android.opengl.GLSurfaceView
import androidx.compose.runtime.Immutable
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

@Immutable
data class GpuInfo(
    val vendor: String,
    val renderer: String,
    val version: String,
    val glslVersion: String,
    val extensions: List<String>
)

class GpuInfoRenderer(
    val onReady: (GpuInfo) -> Unit
) : GLSurfaceView.Renderer {
    @Volatile private var captured = false

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        if (captured) return
        captured = true
        val vendor = gl.glGetString(GL10.GL_VENDOR) ?: "?"
        val renderer = gl.glGetString(GL10.GL_RENDERER) ?: "?"
        val version = gl.glGetString(GL10.GL_VERSION) ?: "?"
        val glsl = gl.glGetString(0x8B8C) ?: ""
        val ext = gl.glGetString(GL10.GL_EXTENSIONS)?.split(" ")?.filter { it.isNotBlank() }
            ?: emptyList()
        onReady(GpuInfo(vendor, renderer, version, glsl, ext))
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {}

    override fun onDrawFrame(gl: GL10) {
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT)
    }
}
