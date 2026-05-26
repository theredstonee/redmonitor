package com.tamerin.sysmonitor.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.tamerin.sysmonitor.data.AppActions
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TaskDetailScreen(pkg: String) {
    val context = LocalContext.current
    val pm = context.packageManager
    val ai = remember(pkg) { runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() }
    val pkgInfo = remember(pkg) { runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull() }
    val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
    val scope = rememberCoroutineScope()

    var lastAction by remember { mutableStateOf<String?>(null) }
    var disabled by remember { mutableStateOf(false) }
    var bgState by remember { mutableStateOf<AppActions.AppOpsState?>(null) }
    var receivers by remember { mutableStateOf<List<AppActions.Receiver>>(emptyList()) }

    LaunchedEffect(shizukuReady) {
        if (shizukuReady) {
            disabled = withContext(Dispatchers.IO) { AppActions.isAppDisabled(context, pkg) }
            bgState = withContext(Dispatchers.IO) { AppActions.getAppOpsState(context, pkg) }
            receivers = withContext(Dispatchers.IO) { AppActions.listBootReceivers(context, pkg) }
        }
    }

    fun runAction(label: String, block: suspend () -> ShizukuHelper.CmdResult) {
        scope.launch {
            val res = withContext(Dispatchers.IO) { block() }
            lastAction = if (res.ok) "✓ $label" else "✗ $label: ${res.stderr.ifBlank { "Exit ${res.exitCode}" }}"
            // Refresh state
            disabled = withContext(Dispatchers.IO) { AppActions.isAppDisabled(context, pkg) }
            bgState = withContext(Dispatchers.IO) { AppActions.getAppOpsState(context, pkg) }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(ai?.loadLabel(pm)?.toString() ?: pkg) {
            KeyValueRow("Paket", pkg)
            pkgInfo?.let {
                KeyValueRow("Version", "${it.versionName} (${it.longVersionCode})")
            }
            ai?.let {
                KeyValueRow("UID", it.uid.toString())
                KeyValueRow("Typ", if ((it.flags and ApplicationInfo.FLAG_SYSTEM) != 0) "System" else "User")
                KeyValueRow("Status", if (disabled) "Deaktiviert" else "Aktiv")
            }
            lastAction?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    color = if (it.startsWith("✓")) GaugeGreen else GaugeRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (!shizukuReady) {
            StatCard("Shizuku benötigt") {
                Text(
                    "Aktionen wie Force-Stop, App deaktivieren oder Hintergrund blockieren brauchen Shizuku. Setup unter System → Floating HUD → Akku-Karte.",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { AppActions.openAppInfo(context, pkg) }) {
                    Text("App-Info im System öffnen")
                }
            }
            return@Column
        }

        StatCard("Sofort-Aktionen") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { runAction("Force-Stop") { AppActions.forceStop(context, pkg) } },
                    colors = ButtonDefaults.buttonColors(containerColor = GaugeRed),
                    modifier = Modifier.weight(1f)
                ) { Text("Force-Stop", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = { runAction("Soft-Kill") { AppActions.softKill(context, pkg) } },
                    modifier = Modifier.weight(1f)
                ) { Text("Soft-Kill", fontSize = 13.sp) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Force-Stop beendet alle Prozesse sofort. Soft-Kill killt nur Cache/leere Prozesse — verträglicher.",
                color = OnSurfaceMuted, fontSize = 11.sp
            )
        }

        StatCard("App-Status") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("App aktiviert", fontSize = 14.sp)
                    Text(
                        if (disabled) "Disabled — App startet bis Reboot nicht mehr"
                        else "Enabled — startet normal",
                        color = OnSurfaceMuted, fontSize = 11.sp
                    )
                }
                Switch(
                    checked = !disabled,
                    onCheckedChange = { wantEnable ->
                        runAction(if (wantEnable) "Enable" else "Disable") {
                            if (wantEnable) AppActions.enableApp(context, pkg)
                            else AppActions.disableApp(context, pkg)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                        checkedTrackColor = Accent
                    )
                )
            }
        }

        StatCard("Hintergrund-Verhalten (AppOps)") {
            val bg = bgState
            if (bg == null) {
                Text("Lade…", color = OnSurfaceMuted, fontSize = 12.sp)
            } else {
                BgToggleRow(
                    label = "RUN_IN_BACKGROUND",
                    current = bg.runInBackground,
                    onSet = { allowed ->
                        runAction(if (allowed) "BG erlauben" else "BG sperren") {
                            AppActions.setBackgroundAllowed(context, pkg, allowed)
                        }
                    }
                )
                BgToggleRow(
                    label = "RUN_ANY_IN_BACKGROUND",
                    current = bg.runAnyInBackground,
                    onSet = { allowed ->
                        runAction(if (allowed) "Any-BG erlauben" else "Any-BG sperren") {
                            AppActions.setAnyBackgroundAllowed(context, pkg, allowed)
                        }
                    }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Beide auf 'deny' = App bekommt keine Wake-Locks mehr im Hintergrund. Sehr aggressiv — manche Apps brechen dabei (Push fehlt, Sync stoppt).",
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }
        }

        StatCard("Auto-Start (Boot-Receiver)") {
            if (receivers.isEmpty()) {
                Text(
                    "Diese App hat keine BOOT_COMPLETED-Receiver — startet also nicht automatisch nach Neustart.",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
            } else {
                Text(
                    "${receivers.size} Receiver hören auf Boot. Toggle deaktiviert sie individuell.",
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                receivers.forEach { r ->
                    var enabled by remember(r.component) { mutableStateOf(r.enabled) }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            r.component.substringAfter("/"),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Switch(
                            checked = enabled,
                            onCheckedChange = { newEnabled ->
                                enabled = newEnabled
                                runAction(
                                    if (newEnabled) "Receiver enable" else "Receiver disable"
                                ) {
                                    AppActions.toggleComponent(context, r.component, newEnabled)
                                }
                            }
                        )
                    }
                }
            }
        }

        StatCard("Sonstiges") {
            OutlinedButton(
                onClick = { AppActions.openAppInfo(context, pkg) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Im System-App-Info öffnen")
            }
        }
    }
}

@Composable
private fun BgToggleRow(label: String, current: String?, onSet: (Boolean) -> Unit) {
    val allowed = current == null || current.equals("allow", true)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp)
            Text(
                "aktuell: ${current ?: "—"}",
                color = OnSurfaceMuted, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Switch(
            checked = allowed,
            onCheckedChange = { onSet(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = Accent
            )
        )
    }
}
