package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tamerin.sysmonitor.R
import androidx.compose.ui.unit.dp
import com.tamerin.sysmonitor.data.NetworkReader
import com.tamerin.sysmonitor.data.NetworkSnapshot
import com.tamerin.sysmonitor.data.formatBytes
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.PercentBar
import com.tamerin.sysmonitor.ui.components.StatCard
import kotlinx.coroutines.delay

@Composable
fun NetworkScreen() {
    val context = LocalContext.current
    var snap by remember {
        mutableStateOf(
            NetworkSnapshot("—", null, null, null, 0, 0, null, null, 0, 0, false)
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            snap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                NetworkReader.read(context)
            }
            delay(2000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard(stringResource(R.string.bench_net_connection)) {
            KeyValueRow("Transport", snap.transportLabel)
            snap.ssid?.let { KeyValueRow("SSID", it) }
            snap.linkSpeedMbps?.let { KeyValueRow("Link-Speed", "$it Mbps") }
            KeyValueRow(stringResource(R.string.bench_net_downstream), "${snap.downstreamKbps} kbps")
            KeyValueRow(stringResource(R.string.bench_net_upstream), "${snap.upstreamKbps} kbps")
            KeyValueRow(stringResource(R.string.bench_net_metered), if (snap.isMetered) stringResource(R.string.bench_yes) else stringResource(R.string.bench_no))
        }

        snap.signalLevelPercent?.let { signal ->
            StatCard(stringResource(R.string.bench_net_signal_strength)) {
                PercentBar(stringResource(R.string.bench_net_wifi_signal), signal)
            }
        }

        StatCard(stringResource(R.string.bench_net_ip_addresses)) {
            KeyValueRow("IPv4", snap.ipv4 ?: "—")
            KeyValueRow("IPv6", snap.ipv6 ?: "—")
        }

        StatCard(stringResource(R.string.bench_net_traffic_since_boot)) {
            KeyValueRow(stringResource(R.string.bench_net_received_rx), snap.totalRxBytes.formatBytes())
            KeyValueRow(stringResource(R.string.bench_net_sent_tx), snap.totalTxBytes.formatBytes())
        }
    }
}
