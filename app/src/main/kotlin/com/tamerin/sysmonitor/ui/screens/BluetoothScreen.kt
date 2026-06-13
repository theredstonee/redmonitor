package com.tamerin.sysmonitor.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
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
fun BluetoothScreen() {
    val context = LocalContext.current
    val bm = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }
    val adapter = remember { bm?.adapter }

    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH
    var hasPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPerm = it }

    var bonded by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    LaunchedEffect(hasPerm, adapter) {
        if (adapter != null && hasPerm) {
            bonded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    @Suppress("MissingPermission")
                    adapter.bondedDevices?.toList() ?: emptyList()
                } catch (_: SecurityException) {
                    emptyList()
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        if (adapter == null) {
            StatCard("Bluetooth") {
                Text("Kein Bluetooth-Adapter auf diesem Gerät.",
                    color = OnSurfaceMuted, fontSize = 13.sp)
            }
            return@Column
        }

        StatCard("Status") {
            KeyValueRow("Aktiviert", if (adapter.isEnabled) "Ja" else "Nein")
            if (hasPerm) {
                @Suppress("MissingPermission")
                val name = try { adapter.name ?: "—" } catch (_: SecurityException) { "—" }
                KeyValueRow("Lokaler Name", name)
                @Suppress("DEPRECATION", "MissingPermission", "HardwareIds")
                val addr = try { adapter.address } catch (_: SecurityException) { "—" }
                if (addr != null && addr != "02:00:00:00:00:00") {
                    KeyValueRow("Lokale Adresse", addr)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                KeyValueRow("LE-Multi-Advertise",
                    if (adapter.isMultipleAdvertisementSupported) "Ja" else "Nein")
                KeyValueRow("Offload Filter",
                    if (adapter.isOffloadedFilteringSupported) "Ja" else "Nein")
                KeyValueRow("Offload Scan Batching",
                    if (adapter.isOffloadedScanBatchingSupported) "Ja" else "Nein")
            }
        }
        Spacer(Modifier.height(12.dp))

        if (!hasPerm) {
            StatCard("Berechtigung benötigt") {
                Text(
                    "Für gekoppelte Geräte braucht es BLUETOOTH_CONNECT (Android 12+) bzw. BLUETOOTH (älter).",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()
                Button(onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    launcher.launch(perm)
                }) { Text("Berechtigung erteilen") }
            }
            return@Column
        }

        StatCard("Gekoppelte Geräte (${bonded.size})") {
            if (bonded.isEmpty()) {
                Text("Keine gekoppelten Geräte.", color = OnSurfaceMuted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(bonded, key = { it.address }) { d ->
                @Suppress("MissingPermission")
                val name = try { d.name } catch (_: SecurityException) { null }
                StatCard(name ?: "<ohne Name>") {
                    KeyValueRow("Adresse", d.address ?: "—")
                    KeyValueRow("Typ", typeOf(d.type))
                    val majorClass = d.bluetoothClass?.majorDeviceClass ?: 0
                    KeyValueRow("Klasse", majorClassLabel(majorClass))
                }
            }
        }
    }
}

private fun typeOf(t: Int): String = when (t) {
    BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
    BluetoothDevice.DEVICE_TYPE_LE -> "Low Energy"
    BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
    else -> "Unbekannt"
}

private fun majorClassLabel(c: Int): String = when (c) {
    0x0100 -> "Computer"
    0x0200 -> "Telefon"
    0x0300 -> "Netzwerk"
    0x0400 -> "Audio/Video"
    0x0500 -> "Peripherie"
    0x0600 -> "Imaging"
    0x0700 -> "Wearable"
    0x0800 -> "Spielzeug"
    0x0900 -> "Gesundheit"
    else -> "—"
}
