package com.tamerin.sysmonitor.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
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
import com.tamerin.sysmonitor.data.SystemTweaks
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
    var suspended by remember { mutableStateOf(false) }
    var dozeWhitelisted by remember { mutableStateOf(false) }
    var bgState by remember { mutableStateOf<AppActions.AppOpsState?>(null) }
    var receivers by remember { mutableStateOf<List<AppActions.Receiver>>(emptyList()) }
    var confirmDialog by remember { mutableStateOf<ConfirmAction?>(null) }
    val isSystemApp = ai != null && (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0

    LaunchedEffect(shizukuReady) {
        if (shizukuReady) {
            disabled = withContext(Dispatchers.IO) { AppActions.isAppDisabled(context, pkg) }
            suspended = withContext(Dispatchers.IO) { AppActions.isAppSuspended(context, pkg) }
            dozeWhitelisted = withContext(Dispatchers.IO) { SystemTweaks.isWhitelisted(context, pkg) }
            bgState = withContext(Dispatchers.IO) { AppActions.getAppOpsState(context, pkg) }
            receivers = withContext(Dispatchers.IO) { AppActions.listBootReceivers(context, pkg) }
        }
    }

    fun runAction(label: String, block: suspend () -> ShizukuHelper.CmdResult) {
        scope.launch {
            val res = withContext(Dispatchers.IO) { block() }
            lastAction = if (res.ok) "✓ $label" else "✗ $label: ${res.stderr.ifBlank { "Exit ${res.exitCode}" }}"
            disabled = withContext(Dispatchers.IO) { AppActions.isAppDisabled(context, pkg) }
            suspended = withContext(Dispatchers.IO) { AppActions.isAppSuspended(context, pkg) }
            bgState = withContext(Dispatchers.IO) { AppActions.getAppOpsState(context, pkg) }
        }
    }

    fun runMulti(label: String, results: List<Pair<String, ShizukuHelper.CmdResult>>) {
        val failed = results.filter { !it.second.ok }.map { it.first }
        lastAction = if (failed.isEmpty()) "✓ $label" else "⚠ $label, fehlgeschlagen: ${failed.joinToString(", ")}"
        scope.launch {
            disabled = withContext(Dispatchers.IO) { AppActions.isAppDisabled(context, pkg) }
            suspended = withContext(Dispatchers.IO) { AppActions.isAppSuspended(context, pkg) }
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

        StatCard("Speicher-Aktionen") {
            OutlinedButton(
                onClick = { runAction("Cache geleert") { AppActions.clearCache(context, pkg) } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Nur Cache leeren (safe)", fontSize = 13.sp) }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { confirmDialog = ConfirmAction.ClearData },
                colors = ButtonDefaults.buttonColors(containerColor = GaugeRed),
                modifier = Modifier.fillMaxWidth()
            ) { Text("App-Daten komplett zurücksetzen", fontSize = 13.sp) }
            Spacer(Modifier.height(4.dp))
            Text(
                "App-Reset löscht alle Einstellungen, Logins und lokalen Daten — App wirkt wie neu installiert.",
                color = OnSurfaceMuted, fontSize = 11.sp
            )
        }

        StatCard("Deinstallieren") {
            Button(
                onClick = { confirmDialog = ConfirmAction.Uninstall(isSystemApp) },
                colors = ButtonDefaults.buttonColors(containerColor = GaugeRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isSystemApp) "System-App für mich verstecken" else "App deinstallieren",
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (isSystemApp)
                    "System-Apps können nicht komplett gelöscht werden ohne Root. Aber: 'pm uninstall --user 0' versteckt sie für dich — Speicher wird frei, beim Werksreset kommt sie wieder."
                else "Vollständig deinstalliert. Wie aus dem Play Store entfernen.",
                color = OnSurfaceMuted, fontSize = 11.sp
            )
        }

        StatCard("App-Status") {
            // SUSPEND toggle (zuverlässiger als disable auf Samsung)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Aktiv", fontSize = 14.sp)
                    Text(
                        if (suspended) "Suspendiert — kann nicht starten, Daten erhalten"
                        else "Läuft normal",
                        color = OnSurfaceMuted, fontSize = 11.sp
                    )
                }
                Switch(
                    checked = !suspended,
                    onCheckedChange = { wantActive ->
                        runAction(if (wantActive) "Unsuspend" else "Suspend (pm suspend)") {
                            if (wantActive) AppActions.unsuspendApp(context, pkg)
                            else AppActions.suspendApp(context, pkg)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                        checkedTrackColor = Accent
                    )
                )
            }
            HorizontalDivider(color = androidx.compose.ui.graphics.Color(0x1FFFFFFF))
            // Legacy disable (für die wo's geht)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Disable (alt)", fontSize = 14.sp)
                    Text(
                        if (disabled) "Disabled" else "Enabled  ·  oft gesperrt auf Samsung",
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
                    }
                )
            }
        }

        StatCard("Deep-Freeze (stärkster Stop)") {
            Text(
                "Force-Stop + Standby-Bucket 'never' + UID-Idle + Suspend in einem Klick. " +
                    "Stärkste Methode ohne Root — App kann faktisch nicht mehr laufen, bis du sie aufweckst.",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { AppActions.deepFreeze(context, pkg) }
                            runMulti("Deep-Freeze", r)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GaugeRed),
                    modifier = Modifier.weight(1f)
                ) { Text("🥶 Einfrieren", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { AppActions.unfreeze(context, pkg) }
                            runMulti("Auftauen", r)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("☀ Auftauen", fontSize = 13.sp) }
            }
        }

        StatCard("Akku-Optimierung (Doze-Whitelist)") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auf Whitelist", fontSize = 14.sp)
                    Text(
                        if (dozeWhitelisted) "Darf in Doze laufen — frisst mehr Akku"
                        else "Wird in Doze gedrosselt — spart Akku",
                        color = OnSurfaceMuted, fontSize = 11.sp
                    )
                }
                Switch(
                    checked = dozeWhitelisted,
                    onCheckedChange = { wantOn ->
                        runAction(if (wantOn) "Whitelist + $pkg" else "Whitelist - $pkg") {
                            if (wantOn) SystemTweaks.addToWhitelist(context, pkg)
                            else SystemTweaks.removeFromWhitelist(context, pkg)
                        }
                        scope.launch {
                            dozeWhitelisted = withContext(Dispatchers.IO) {
                                SystemTweaks.isWhitelisted(context, pkg)
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                        checkedTrackColor = Accent
                    )
                )
            }
        }

        StatCard("Standby-Bucket") {
            Text(
                "Android-System teilt Apps in Buckets ein: active/working_set/frequent/rare/never. " +
                    "Je seltener, desto weniger Background-CPU/Netz/Wakeups bekommt die App.",
                color = OnSurfaceMuted, fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("active", "working_set", "rare", "never").forEach { bucket ->
                    OutlinedButton(
                        onClick = {
                            runAction("Bucket → $bucket") {
                                AppActions.setStandbyBucket(context, pkg, bucket)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
                    ) { Text(bucket, fontSize = 10.sp) }
                }
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

    // Confirmation dialog
    confirmDialog?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmDialog = null },
            title = { Text(action.title) },
            text = { Text(action.body(pkg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDialog = null
                        runAction(action.label) {
                            when (action) {
                                is ConfirmAction.ClearData -> AppActions.clearAllData(context, pkg)
                                is ConfirmAction.Uninstall -> AppActions.uninstall(context, pkg, action.isSystem)
                            }
                        }
                    }
                ) { Text("Bestätigen", color = GaugeRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDialog = null }) { Text("Abbrechen") }
            }
        )
    }
}

private sealed class ConfirmAction(val title: String, val label: String) {
    abstract fun body(pkg: String): String
    object ClearData : ConfirmAction("App-Daten löschen?", "Daten gelöscht") {
        override fun body(pkg: String) =
            "$pkg wird auf Werkszustand zurückgesetzt. Logins, Einstellungen und alles Lokale ist weg."
    }
    data class Uninstall(val isSystem: Boolean) : ConfirmAction(
        if (isSystem) "System-App verstecken?" else "App deinstallieren?",
        if (isSystem) "Versteckt" else "Deinstalliert"
    ) {
        override fun body(pkg: String) =
            if (isSystem) "$pkg wird für dich versteckt. Beim Werksreset kommt sie wieder."
            else "$pkg wird komplett entfernt."
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
