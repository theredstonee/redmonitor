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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tamerin.sysmonitor.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.benchmark.NetworkSpeedResult
import com.tamerin.sysmonitor.benchmark.NetworkSpeedTest
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.launch

@Composable
fun NetworkSpeedScreen() {
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<NetworkSpeedResult?>(null) }
    var progressMb by remember { mutableDoubleStateOf(0.0) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard(stringResource(R.string.bench_netspeed_title)) {
            Text(
                stringResource(R.string.bench_netspeed_desc),
                color = OnSurfaceMuted, fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = !running,
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                    running = true
                    result = null
                    error = null
                    progressMb = 0.0
                    scope.launch {
                        try {
                            result = NetworkSpeedTest.run { progressMb = it }
                        } catch (e: Exception) {
                            error = context.getString(R.string.bench_netspeed_error, e.message ?: "")
                        }
                        running = false
                    }
                }
            ) {
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("${"%.1f".format(progressMb)} MB / 25 MB")
                } else Text(if (result == null) stringResource(R.string.bench_start_test) else stringResource(R.string.bench_test_again))
            }
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = androidx.compose.ui.graphics.Color.Red, fontSize = 12.sp)
            }
        }

        result?.let { r ->
            StatCard(stringResource(R.string.bench_result)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "%.1f".format(r.mbps),
                        color = Accent,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Mbps", color = OnSurfaceMuted, fontSize = 14.sp)
                }
                Spacer(Modifier.height(12.dp))
                KeyValueRow(stringResource(R.string.bench_netspeed_downloaded), "${"%.2f".format(r.downloadedMb)} MB")
                KeyValueRow(stringResource(R.string.bench_duration), "${r.durationMs} ms")
                KeyValueRow("Endpoint", r.endpoint)
            }
        }
    }
}
