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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tamerin.sysmonitor.R
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
                Text(stringResource(R.string.hw_bt_no_adapter),
                    color = OnSurfaceMuted, fontSize = 13.sp)
            }
            return@Column
        }

        StatCard(stringResource(R.string.hw_bt_status)) {
            KeyValueRow(stringResource(R.string.hw_bt_enabled), if (adapter.isEnabled) stringResource(R.string.hw_yes) else stringResource(R.string.hw_no))
            if (hasPerm) {
                @Suppress("MissingPermission")
                val name = try { adapter.name ?: "—" } catch (_: SecurityException) { "—" }
                KeyValueRow(stringResource(R.string.hw_bt_local_name), name)
                @Suppress("DEPRECATION", "MissingPermission", "HardwareIds")
                val addr = try { adapter.address } catch (_: SecurityException) { "—" }
                if (addr != null && addr != "02:00:00:00:00:00") {
                    KeyValueRow(stringResource(R.string.hw_bt_local_address), addr)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                KeyValueRow("LE-Multi-Advertise",
                    if (adapter.isMultipleAdvertisementSupported) stringResource(R.string.hw_yes) else stringResource(R.string.hw_no))
                KeyValueRow("Offload Filter",
                    if (adapter.isOffloadedFilteringSupported) stringResource(R.string.hw_yes) else stringResource(R.string.hw_no))
                KeyValueRow("Offload Scan Batching",
                    if (adapter.isOffloadedScanBatchingSupported) stringResource(R.string.hw_yes) else stringResource(R.string.hw_no))
            }
        }
        Spacer(Modifier.height(12.dp))

        if (!hasPerm) {
            StatCard(stringResource(R.string.hw_bt_perm_required)) {
                Text(
                    stringResource(R.string.hw_bt_perm_desc),
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()
                Button(onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    launcher.launch(perm)
                }) { Text(stringResource(R.string.hw_grant_permission)) }
            }
            return@Column
        }

        StatCard(stringResource(R.string.hw_bt_paired, bonded.size)) {
            if (bonded.isEmpty()) {
                Text(stringResource(R.string.hw_bt_no_paired), color = OnSurfaceMuted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(bonded, key = { it.address }) { d ->
                @Suppress("MissingPermission")
                val name = try { d.name } catch (_: SecurityException) { null }
                StatCard(name ?: stringResource(R.string.hw_bt_no_name)) {
                    KeyValueRow(stringResource(R.string.hw_bt_address), d.address ?: "—")
                    KeyValueRow(stringResource(R.string.hw_bt_type), stringResource(typeOf(d.type)))
                    val majorClass = d.bluetoothClass?.majorDeviceClass ?: 0
                    KeyValueRow(stringResource(R.string.hw_bt_class), stringResource(majorClassLabel(majorClass)))
                }
            }
        }
    }
}

private fun typeOf(t: Int): Int = when (t) {
    BluetoothDevice.DEVICE_TYPE_CLASSIC -> R.string.hw_bttype_classic
    BluetoothDevice.DEVICE_TYPE_LE -> R.string.hw_bttype_le
    BluetoothDevice.DEVICE_TYPE_DUAL -> R.string.hw_bttype_dual
    else -> R.string.hw_bttype_unknown
}

private fun majorClassLabel(c: Int): Int = when (c) {
    0x0100 -> R.string.hw_btclass_computer
    0x0200 -> R.string.hw_btclass_phone
    0x0300 -> R.string.hw_btclass_network
    0x0400 -> R.string.hw_btclass_av
    0x0500 -> R.string.hw_btclass_peripheral
    0x0600 -> R.string.hw_btclass_imaging
    0x0700 -> R.string.hw_btclass_wearable
    0x0800 -> R.string.hw_btclass_toy
    0x0900 -> R.string.hw_btclass_health
    else -> R.string.hw_btclass_dash
}
