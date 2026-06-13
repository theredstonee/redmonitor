package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
        StatCard("Verbindung") {
            KeyValueRow("Transport", snap.transportLabel)
            snap.ssid?.let { KeyValueRow("SSID", it) }
            snap.linkSpeedMbps?.let { KeyValueRow("Link-Speed", "$it Mbps") }
            KeyValueRow("Downstream (gemeldet)", "${snap.downstreamKbps} kbps")
            KeyValueRow("Upstream (gemeldet)", "${snap.upstreamKbps} kbps")
            KeyValueRow("Datenintensiv (metered)?", if (snap.isMetered) "Ja" else "Nein")
        }

        snap.signalLevelPercent?.let { signal ->
            StatCard("Signalstärke") {
                PercentBar("WLAN-Signal", signal)
            }
        }

        StatCard("IP-Adressen") {
            KeyValueRow("IPv4", snap.ipv4 ?: "—")
            KeyValueRow("IPv6", snap.ipv6 ?: "—")
        }

        StatCard("Traffic (seit Boot)") {
            KeyValueRow("Empfangen (RX)", snap.totalRxBytes.formatBytes())
            KeyValueRow("Gesendet (TX)", snap.totalTxBytes.formatBytes())
        }
    }
}
