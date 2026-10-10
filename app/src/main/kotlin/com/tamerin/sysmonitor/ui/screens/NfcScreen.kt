package com.tamerin.sysmonitor.ui.screens

import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

@Composable
fun NfcScreen() {
    val context = LocalContext.current
    val adapter = remember { NfcAdapter.getDefaultAdapter(context) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        StatCard("NFC") {
            if (adapter == null) {
                KeyValueRow(stringResource(R.string.hw_nfc_present), stringResource(R.string.hw_no))
                Text(
                    stringResource(R.string.hw_nfc_no_chip),
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
                return@StatCard
            }
            KeyValueRow(stringResource(R.string.hw_nfc_present), stringResource(R.string.hw_yes))
            KeyValueRow(stringResource(R.string.hw_nfc_enabled), if (adapter.isEnabled) stringResource(R.string.hw_yes) else stringResource(R.string.hw_no))
            if (!adapter.isEnabled) {
                Spacer(Modifier.height(8.dp))
                val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()
                Button(onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    val intent = Intent(Settings.ACTION_NFC_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }) { Text(stringResource(R.string.hw_nfc_enable_settings)) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.hw_nfc_hint),
                color = OnSurfaceMuted, fontSize = 11.sp
            )
        }
    }
}
