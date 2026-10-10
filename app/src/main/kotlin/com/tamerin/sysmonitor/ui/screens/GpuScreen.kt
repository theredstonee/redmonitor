package com.tamerin.sysmonitor.ui.screens

import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tamerin.sysmonitor.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tamerin.sysmonitor.data.GpuInfo
import com.tamerin.sysmonitor.data.GpuInfoRenderer
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

@Composable
fun GpuScreen() {
    var info by remember { mutableStateOf<GpuInfo?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hidden 1x1 GL surface to capture GPU strings
        AndroidView(
            modifier = Modifier.size(1.dp),
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    setRenderer(GpuInfoRenderer { gpu ->
                        info = gpu
                    })
                    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                }
            }
        )

        val gpu = info
        if (gpu == null) {
            StatCard(stringResource(R.string.bench_gpuinfo_title)) {
                KeyValueRow("Status", stringResource(R.string.bench_gpuinfo_reading))
            }
        } else {
            StatCard(stringResource(R.string.bench_gpuinfo_title)) {
                KeyValueRow(stringResource(R.string.bench_gpuinfo_manufacturer), gpu.vendor)
                KeyValueRow("Renderer", gpu.renderer)
                KeyValueRow(stringResource(R.string.bench_gpuinfo_opengl_version), gpu.version)
                if (gpu.glslVersion.isNotBlank()) {
                    KeyValueRow(stringResource(R.string.bench_gpuinfo_glsl_version), gpu.glslVersion)
                }
                KeyValueRow("Extensions", "${gpu.extensions.size}")
            }

            StatCard("OpenGL Extensions") {
                gpu.extensions.take(60).forEach {
                    Text(
                        it,
                        color = OnSurfaceMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
                if (gpu.extensions.size > 60) {
                    Text(
                        stringResource(R.string.bench_gpuinfo_more, gpu.extensions.size - 60),
                        color = OnSurfaceMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
