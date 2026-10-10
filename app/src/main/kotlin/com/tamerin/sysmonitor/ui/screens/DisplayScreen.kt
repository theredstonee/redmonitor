package com.tamerin.sysmonitor.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.data.DeviceInfo
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DisplayScreen() {
    val context = LocalContext.current
    val activity = context as? Activity

    val displaySnap = remember(activity) { activity?.let { DeviceInfo.readDisplay(it) } }
    var device by remember { mutableStateOf(DeviceInfo.readDevice()) }

    LaunchedEffect(Unit) {
        while (true) {
            device = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                DeviceInfo.readDevice()
            }
            delay(2500)
        }
    }

    val df = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard(stringResource(R.string.hw_disp_device)) {
            KeyValueRow(stringResource(R.string.hw_disp_brand), device.brand)
            KeyValueRow(stringResource(R.string.hw_disp_manufacturer), device.manufacturer)
            KeyValueRow(stringResource(R.string.hw_disp_model), device.model)
            KeyValueRow("Codename", device.device)
            KeyValueRow(stringResource(R.string.hw_disp_product), device.product)
            KeyValueRow("Board", device.board)
            KeyValueRow("Bootloader", device.bootloader)
        }

        StatCard("System") {
            KeyValueRow(stringResource(R.string.hw_disp_android_version), device.androidVersion)
            KeyValueRow("SDK-Level", device.sdk.toString())
            KeyValueRow(stringResource(R.string.hw_disp_security_patch), device.securityPatch)
            KeyValueRow("Kernel", device.kernel)
            KeyValueRow("Java VM", device.javaVm)
            KeyValueRow("OpenSSL", device.openSslVersion)
        }

        StatCard("Build") {
            KeyValueRow("Build-ID", device.buildId)
            KeyValueRow("Build-Host", device.buildHost)
            KeyValueRow(stringResource(R.string.hw_disp_build_date), df.format(Date(device.buildTimeMs)))
            KeyValueRow("Uptime", formatUptime(device.uptimeMs))
        }

        displaySnap?.let {
            StatCard("Display") {
                KeyValueRow(stringResource(R.string.hw_disp_resolution), "${it.widthPx} × ${it.heightPx} px")
                KeyValueRow(stringResource(R.string.hw_disp_density), "${it.densityDpi} dpi")
                KeyValueRow(stringResource(R.string.hw_disp_refresh_rate), "${"%.1f".format(it.refreshRateHz)} Hz")
                KeyValueRow(stringResource(R.string.hw_disp_diagonal), "${"%.2f".format(it.sizeInches)} ${stringResource(R.string.hw_disp_inch)}")
            }
        }

        StatCard("Fingerprint") {
            KeyValueRow("Build", device.fingerprint)
        }
    }
}

private fun formatUptime(ms: Long): String {
    val s = ms / 1000
    val d = s / 86400
    val h = (s % 86400) / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return when {
        d > 0 -> "$d d $h h $m min"
        h > 0 -> "$h h $m min $sec s"
        m > 0 -> "$m min $sec s"
        else -> "$sec s"
    }
}
