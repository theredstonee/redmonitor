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
import com.tamerin.sysmonitor.data.LanDevice
import com.tamerin.sysmonitor.data.LanScanner
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch

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
    var lanDevices by remember { mutableStateOf<List<LanDevice>>(emptyList()) }
    var lanScanning by remember { mutableStateOf(false) }
    var lanProgress by remember { mutableIntStateOf(0) }
    var lanTotal by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

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
        StatCard("Access-Points scannen") {
            Button(onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                scanning = true
                @Suppress("DEPRECATION")
                wm.startScan()
            }) { Text(if (scanning) "Scanne…" else "AP-Scan starten") }
            Spacer(Modifier.height(4.dp))
            Text("${results.size} Netze gefunden", color = OnSurfaceMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))

        StatCard("Geräte im Netzwerk (LAN-Scan)") {
            Text(
                "Findet alle Geräte im selben WLAN: TCP-Probe auf 10 gängige Ports + /proc/net/arp. " +
                    "Subnet wird automatisch erkannt, /24 wird voll gescannt.",
                color = OnSurfaceMuted, fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                    lanScanning = true
                    lanDevices = emptyList()
                    lanProgress = 0
                    lanTotal = 0
                    scope.launch {
                        val res = LanScanner.scan(context) { done, total ->
                            lanProgress = done
                            lanTotal = total
                        }
                        lanDevices = res
                        lanScanning = false
                    }
                },
                enabled = !lanScanning
            ) { Text(if (lanScanning) "Scanne… ($lanProgress/$lanTotal)" else "LAN-Scan starten") }
            Spacer(Modifier.height(4.dp))
            Text(
                "${lanDevices.size} Geräte gefunden",
                color = if (lanDevices.isNotEmpty()) GaugeGreen else OnSurfaceMuted,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (lanDevices.isNotEmpty()) {
                item {
                    Text(
                        "LAN-Geräte",
                        color = Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(lanDevices, key = { it.ip }) { d ->
                    StatCard(
                        when {
                            d.isSelf -> "Dieses Gerät (${d.ip})"
                            d.isGateway -> "Router/Gateway (${d.ip})"
                            else -> d.hostname ?: d.ip
                        }
                    ) {
                        KeyValueRow("IP", d.ip)
                        d.mac?.let {
                            KeyValueRow("MAC", it)
                            val vendor = ouiVendor(it)
                            if (vendor != null) KeyValueRow("Hersteller", vendor)
                        }
                        d.hostname?.takeIf { it != d.ip }?.let { KeyValueRow("Hostname", it) }
                        if (d.openPorts.isNotEmpty()) {
                            KeyValueRow("Offene Ports", d.openPorts.joinToString(", "))
                        }
                        if (d.isSelf) {
                            Text("← du", color = AccentSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        if (d.isGateway) {
                            Text("← Default-Gateway", color = AccentSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (results.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Access-Points in Reichweite",
                        color = Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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

/** Tiny OUI lookup for the most common vendors. */
private fun ouiVendor(mac: String): String? {
    val prefix = mac.uppercase().replace(":", "").take(6)
    return OUI_TABLE[prefix]
}

private val OUI_TABLE = mapOf(
    "001A11" to "Google", "F4F5D8" to "Google", "70B3D5" to "Google",
    "001B63" to "Apple", "001CB3" to "Apple", "001D4F" to "Apple",
    "9810E8" to "Apple", "ACDE48" to "Apple",
    "001E58" to "Samsung", "002339" to "Samsung", "0025E5" to "Samsung",
    "F8E9C2" to "Samsung", "B8BBAF" to "Samsung",
    "001882" to "Xiaomi", "286C07" to "Xiaomi", "8CFD18" to "Xiaomi",
    "F48E38" to "Xiaomi", "98FAE3" to "Xiaomi",
    "F0B429" to "Huawei", "00E0FC" to "Huawei", "8C34FD" to "Huawei",
    "B4E62D" to "OnePlus", "C8E0EB" to "OnePlus",
    "DCA6BD" to "Raspberry Pi", "B827EB" to "Raspberry Pi", "E45F01" to "Raspberry Pi",
    "001A2B" to "Cisco", "0014A4" to "Cisco", "001B0D" to "Cisco",
    "001E2A" to "Netgear", "20E52A" to "Netgear", "C03F0E" to "Netgear",
    "F0F002" to "AVM (FRITZ!)", "1CB72C" to "AVM (FRITZ!)", "C0C9E3" to "AVM (FRITZ!)",
    "94DEB8" to "AVM (FRITZ!)", "381428" to "AVM (FRITZ!)",
    "001CDF" to "TP-Link", "5057A8" to "TP-Link", "B0487A" to "TP-Link",
    "001D7E" to "Asus", "BCEE7B" to "Asus", "9C5C8E" to "Asus",
    "001310" to "Linksys", "30B5C2" to "TP-Link",
    "EC1A59" to "Belkin",
    "B886A8" to "Sonos", "DCB54F" to "Sonos",
    "94A408" to "Nintendo", "B8AE6E" to "Nintendo",
    "001FE2" to "Sony", "5400E2" to "Sony", "FCF152" to "Sony",
    "E063DA" to "Amazon (Echo)", "FCA667" to "Amazon", "F0D2F1" to "Amazon",
    "180373" to "Microsoft", "C8B5B7" to "Microsoft"
)

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
