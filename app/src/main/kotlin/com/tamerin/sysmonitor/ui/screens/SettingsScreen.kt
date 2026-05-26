package com.tamerin.sysmonitor.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.BuildConfig
import com.tamerin.sysmonitor.settings.AppPrefs
import com.tamerin.sysmonitor.update.UpdatePrefs
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

@Composable
fun SettingsScreen() {
    val context = LocalContext.current

    var includePre by remember { mutableStateOf(UpdatePrefs.includePrerelease(context)) }
    var notify by remember { mutableStateOf(UpdatePrefs.notificationsEnabled(context)) }
    var haptics by remember { mutableStateOf(AppPrefs.hapticFeedbackEnabled(context)) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("Updates testen (Dev)") {
            Text(
                "Simuliert einen Update-Dialog mit Fake-Daten — ohne dass auf GitHub etwas Neues sein muss.",
                color = OnSurfaceMuted, fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    com.tamerin.sysmonitor.update.UpdateTestHelper.triggerFakeUpdate(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Test-Update anzeigen") }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    com.tamerin.sysmonitor.update.UpdateTestHelper.clearDismissed(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Ignorierte Versionen zurücksetzen") }
        }

        StatCard("Updates") {
            ToggleRow(
                label = "Pre-Releases (Beta/RC) einschließen",
                description = "Auch Vorab-Versionen werden gefunden",
                checked = includePre
            ) {
                includePre = it
                UpdatePrefs.setIncludePrerelease(context, it)
            }
            ToggleRow(
                label = "Benachrichtigungen bei neuen Versionen",
                description = "Push-Notification, auch wenn App geschlossen",
                checked = notify
            ) {
                notify = it
                UpdatePrefs.setNotificationsEnabled(context, it)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("redmonitor://update"))
                        .setPackage(context.packageName)
                    runCatching { context.startActivity(intent) }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Auf Update prüfen") }
        }

        StatCard("Bedienung") {
            ToggleRow(
                label = "Haptisches Feedback bei Aktionen",
                description = "Leichte Vibration bei Tasten/Toggles (in Vorbereitung — Wirkung folgt im nächsten Update)",
                checked = haptics
            ) {
                haptics = it
                AppPrefs.setHapticFeedbackEnabled(context, it)
            }
        }

        StatCard("Bug melden / Feedback") {
            Text(
                "Wenn was nicht funktioniert oder du Wünsche hast — schreib ein Issue auf GitHub. Open Source, jeder kann mitlesen und mitschreiben.",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/theredstonee/redmonitor/issues/new")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("🐛 Bug melden / Feature anfragen") }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/theredstonee/redmonitor")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("GitHub-Repository öffnen") }
        }

        StatCard("Über") {
            KeyValueRow("Version", BuildConfig.VERSION_NAME)
            KeyValueRow("Build", BuildConfig.VERSION_CODE.toString())
            KeyValueRow("Anwendungs-ID", BuildConfig.APPLICATION_ID)
            KeyValueRow("Lizenz", "MIT")
            KeyValueRow("Hersteller", "TheRedStonee")
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.theredstonee.de")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("theredstonee.de") }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp)
            description?.let {
                Text(it, color = OnSurfaceMuted, fontSize = 11.sp)
            }
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = Accent
            )
        )
    }
}
