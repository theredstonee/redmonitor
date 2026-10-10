package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tamerin.sysmonitor.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.benchmark.ImageBenchResult
import com.tamerin.sysmonitor.benchmark.ImageBenchmark
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.launch

@Composable
fun ImageBenchScreen() {
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ImageBenchResult?>(null) }
    val scope = rememberCoroutineScope()
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard(stringResource(R.string.bench_image_title)) {
            Text(
                stringResource(R.string.bench_image_desc),
                color = OnSurfaceMuted, fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = !running,
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                    running = true
                    result = null
                    scope.launch {
                        result = ImageBenchmark.run()
                        running = false
                    }
                }
            ) {
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.bench_running))
                } else Text(if (result == null) stringResource(R.string.bench_start_test) else stringResource(R.string.bench_test_again))
            }
        }

        result?.let { r ->
            StatCard(stringResource(R.string.bench_result)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "%.1f".format(r.megapixelsPerSec),
                        color = Accent,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("MPx/s", color = OnSurfaceMuted, fontSize = 14.sp)
                }
                Spacer(Modifier.height(12.dp))
                KeyValueRow(stringResource(R.string.bench_image_size), "${r.width} × ${r.height}")
                KeyValueRow(stringResource(R.string.bench_image_passes), "${r.passes}")
                KeyValueRow(stringResource(R.string.bench_duration), "${r.durationMs} ms")
            }
        }
    }
}
