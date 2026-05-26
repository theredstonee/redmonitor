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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                KeyValueRow("Vorhanden", "Nein")
                Text(
                    "Dieses Gerät hat keinen NFC-Chip.",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
                return@StatCard
            }
            KeyValueRow("Vorhanden", "Ja")
            KeyValueRow("Aktiviert", if (adapter.isEnabled) "Ja" else "Nein")
            if (!adapter.isEnabled) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_NFC_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }) { Text("NFC in Einstellungen aktivieren") }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Hinweis: Live-Tag-Erkennung würde ein vollwertiges Foreground-Dispatch-System brauchen — hier zeigen wir nur Verfügbarkeit und Status.",
                color = OnSurfaceMuted, fontSize = 11.sp
            )
        }
    }
}
