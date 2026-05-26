package com.tamerin.sysmonitor.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.overlay.OverlayService
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

@Composable
fun OverlayHudScreen() {
    val context = LocalContext.current
    var hasPerm by remember { mutableStateOf(checkPerm(context)) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasPerm = checkPerm(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("Floating-HUD") {
            Text(
                "Zeigt eine kleine Live-Anzeige (CPU, RAM, Akku, Temp, Ladewatt) über allen anderen Apps. Drag = verschieben, Tap = App öffnen.",
                color = OnSurfaceMuted,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            KeyValueRow("Overlay-Berechtigung", if (hasPerm) "Erteilt" else "Fehlt")
        }

        if (!hasPerm) {
            StatCard("Berechtigung erteilen") {
                Text(
                    "Android verlangt für Overlays die spezielle Berechtigung „Über anderen Apps anzeigen“. Du musst sie in den Systemeinstellungen aktivieren.",
                    color = OnSurfaceMuted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }) {
                    Text("Berechtigung in Einstellungen erteilen")
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { hasPerm = checkPerm(context) }) {
                    Text("Status neu prüfen")
                }
            }
        } else {
            StatCard("Steuerung") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        OverlayService.start(context)
                        running = true
                    }) { Text("HUD starten") }
                    OutlinedButton(onClick = {
                        OverlayService.stop(context)
                        running = false
                    }) { Text("HUD stoppen") }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Das HUD läuft als Foreground-Service mit dauerhafter Benachrichtigung — Android verlangt das. Über die Benachrichtigung kannst du es auch beenden.",
                    color = OnSurfaceMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun checkPerm(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else true
}
