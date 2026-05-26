package com.tamerin.sysmonitor.ui.screens

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

@Composable
fun WifiScanScreen() {
    val context = LocalContext.current
    val wm = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    var hasPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPerm = it }

    var results by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                try {
                    @Suppress("MissingPermission")
                    results = wm.scanResults.sortedByDescending { it.level }
                } catch (_: SecurityException) {}
                scanning = false
            }
        }
        context.registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        if (!hasPerm) {
            StatCard("WLAN-Scan-Berechtigung") {
                Text(
                    "Für WLAN-Scans braucht Android ACCESS_FINE_LOCATION (auch wenn wir keinen Standort wollen — Google-Vorgabe).",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                    Text("Berechtigung erteilen")
                }
            }
            return@Column
        }
        StatCard("Steuerung") {
            Button(onClick = {
                scanning = true
                @Suppress("DEPRECATION")
                wm.startScan()
            }) { Text(if (scanning) "Scanne…" else "Scan starten") }
            Spacer(Modifier.height(4.dp))
            Text("${results.size} Netze gefunden", color = OnSurfaceMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results, key = { it.BSSID + it.SSID }) { r ->
                StatCard(r.SSID.ifBlank { "<verstecktes Netz>" }) {
                    KeyValueRow("BSSID", r.BSSID ?: "—")
                    KeyValueRow("Signal", "${r.level} dBm (${signalPercent(r.level)} %)")
                    KeyValueRow("Frequenz", "${r.frequency} MHz (${bandLabel(r.frequency)})")
                    KeyValueRow("Kanal", channelOf(r.frequency).toString())
                    KeyValueRow("Sicherheit", securityOf(r))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        KeyValueRow("WLAN-Standard", standardOf(r.wifiStandard))
                    }
                }
            }
        }
    }
}

private fun signalPercent(rssi: Int): Int = WifiManager.calculateSignalLevel(rssi, 101)
private fun bandLabel(freq: Int): String = when {
    freq < 2500 -> "2.4 GHz"
    freq < 5900 -> "5 GHz"
    freq < 7100 -> "6 GHz"
    else -> "?"
}
private fun channelOf(freq: Int): Int = when {
    freq == 2484 -> 14
    freq in 2412..2472 -> (freq - 2412) / 5 + 1
    freq in 5170..5825 -> (freq - 5000) / 5
    freq in 5955..7115 -> (freq - 5950) / 5
    else -> 0
}
private fun securityOf(r: ScanResult): String {
    val caps = r.capabilities ?: ""
    return when {
        caps.contains("WPA3") -> "WPA3"
        caps.contains("WPA2") -> "WPA2"
        caps.contains("WPA") -> "WPA"
        caps.contains("WEP") -> "WEP"
        caps.contains("EAP") -> "Enterprise"
        caps.isBlank() || caps == "[ESS]" -> "Offen"
        else -> caps
    }
}
private fun standardOf(s: Int): String = when (s) {
    ScanResult.WIFI_STANDARD_LEGACY -> "802.11 a/b/g"
    ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
    ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
    ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6 (802.11ax)"
    ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7 (802.11be)"
    else -> "Unbekannt"
}
