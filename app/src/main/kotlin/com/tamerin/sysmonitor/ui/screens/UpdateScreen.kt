package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.update.UpdateChecker
import com.tamerin.sysmonitor.update.UpdateInstaller
import com.tamerin.sysmonitor.update.UpdatePrefs
import com.tamerin.sysmonitor.update.UpdateState
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UpdateScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val df = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY) }

    var includePre by remember { mutableStateOf(UpdatePrefs.includePrerelease(context)) }
    var notify by remember { mutableStateOf(UpdatePrefs.notificationsEnabled(context)) }
    var state by remember { mutableStateOf<UpdateState?>(null) }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f to 0f) }
    var installing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun runCheck() {
        checking = true
        statusMessage = null
        scope.launch {
            val s = UpdateChecker.check(context, includePre)
            state = s
            UpdatePrefs.setLastCheckMs(context, System.currentTimeMillis())
            s.latest?.let { UpdatePrefs.setLatestSeenVersion(context, it.versionName) }
            checking = false
        }
    }

    LaunchedEffect(Unit) { runCheck() }

    val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
    val lastCheck = UpdatePrefs.lastCheckMs(context)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("Aktuelle Version") {
            KeyValueRow("Installiert", state?.current ?: "?")
            KeyValueRow("Letzter Check", if (lastCheck > 0) df.format(Date(lastCheck)) else "—")
        }

        if (checking) {
            StatCard("Prüfe…") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Verbinde mit GitHub…", color = OnSurfaceMuted, fontSize = 13.sp)
                }
            }
        } else {
            val s = state
            when {
                s == null -> Unit
                s.error != null -> StatCard("Fehler") {
                    Text(s.error, color = GaugeRed, fontSize = 13.sp)
                }
                s.hasUpdate && s.latest != null -> StatCard("🎉 Update verfügbar") {
                    Text(
                        "${s.latest.name}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${s.current} → ${s.latest.versionName}${if (s.latest.isPrerelease) "  (Pre-Release)" else ""}",
                        color = AccentSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium
                    )
                    if (s.latest.apkSizeBytes > 0) {
                        Text(
                            "APK: ${"%.1f".format(s.latest.apkSizeBytes / 1024.0 / 1024.0)} MB",
                            color = OnSurfaceMuted, fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (s.latest.body.isNotBlank()) {
                        Text(
                            s.latest.body.take(1500),
                            color = OnSurfaceMuted, fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    if (downloading) {
                        Text(
                            "Lade… ${"%.1f".format(progress.first)} / ${"%.1f".format(progress.second)} MB",
                            color = OnSurfaceMuted, fontSize = 12.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { if (progress.second > 0) (progress.first / progress.second).toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (installing) {
                        Text("Installiere via Shizuku…", color = OnSurfaceMuted, fontSize = 12.sp)
                    } else {
                        if (s.latest.apkUrl != null) {
                            if (shizukuReady) {
                                Button(
                                    onClick = {
                                        downloading = true
                                        scope.launch {
                                            val dl = UpdateInstaller.downloadApk(context, s.latest.apkUrl) { d, t ->
                                                progress = d.toFloat() to t.toFloat()
                                            }
                                            downloading = false
                                            when (dl) {
                                                is UpdateInstaller.DownloadResult.Success -> {
                                                    installing = true
                                                    val (ok, msg) = UpdateInstaller.installViaShizuku(context, dl.file)
                                                    installing = false
                                                    statusMessage = if (ok) "✓ Update installiert!" else "✗ $msg"
                                                }
                                                is UpdateInstaller.DownloadResult.Error ->
                                                    statusMessage = "✗ Download: ${dl.message}"
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("⚡ Auto-Install via Shizuku") }
                                Spacer(Modifier.height(6.dp))
                            }
                            OutlinedButton(
                                onClick = {
                                    downloading = true
                                    scope.launch {
                                        val dl = UpdateInstaller.downloadApk(context, s.latest.apkUrl) { d, t ->
                                            progress = d.toFloat() to t.toFloat()
                                        }
                                        downloading = false
                                        when (dl) {
                                            is UpdateInstaller.DownloadResult.Success ->
                                                UpdateInstaller.installViaSystem(context, dl.file)
                                            is UpdateInstaller.DownloadResult.Error ->
                                                statusMessage = "✗ Download: ${dl.message}"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("APK laden + manuell installieren") }
                            Spacer(Modifier.height(6.dp))
                        }
                        OutlinedButton(
                            onClick = { UpdateInstaller.openGithubRelease(context, s.latest.htmlUrl) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Release-Seite öffnen") }
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                UpdatePrefs.dismissVersion(context, s.latest.versionName)
                                statusMessage = "Diese Version wird nicht mehr beworben"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Update ignorieren", fontSize = 12.sp) }
                    }
                    statusMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            color = if (it.startsWith("✓")) GaugeGreen else GaugeRed,
                            fontSize = 12.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }
                else -> StatCard("Aktuell") {
                    Text(
                        "✓ Du nutzt die neueste Version (${s.current}).",
                        color = GaugeGreen, fontSize = 13.sp, fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        StatCard("Steuerung") {
            Button(
                onClick = { runCheck() },
                enabled = !checking && !downloading && !installing,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Jetzt auf Update prüfen") }
        }

        StatCard("Einstellungen") {
            ToggleRowSimple(
                label = "Pre-Releases (Beta/RC) einschließen",
                checked = includePre
            ) {
                includePre = it
                UpdatePrefs.setIncludePrerelease(context, it)
            }
            ToggleRowSimple(
                label = "Benachrichtigungen bei neuen Versionen",
                checked = notify
            ) {
                notify = it
                UpdatePrefs.setNotificationsEnabled(context, it)
            }
        }
    }
}

@Composable
private fun ToggleRowSimple(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = Accent
            )
        )
    }
}
