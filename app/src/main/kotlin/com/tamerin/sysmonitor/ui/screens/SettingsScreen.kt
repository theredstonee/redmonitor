package com.tamerin.sysmonitor.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.clickable
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
import com.tamerin.sysmonitor.settings.Haptic
import com.tamerin.sysmonitor.settings.HapticIntensity
import com.tamerin.sysmonitor.settings.HapticType
import com.tamerin.sysmonitor.update.UpdatePrefs
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

@Composable
fun SettingsScreen() {
    val context = LocalContext.current

    var includePre by remember { mutableStateOf(UpdatePrefs.includePrerelease(context)) }
    var notify by remember { mutableStateOf(UpdatePrefs.notificationsEnabled(context)) }
    var haptics by remember { mutableStateOf(AppPrefs.hapticFeedbackEnabled(context)) }
    var hapticIntensity by remember { mutableStateOf(AppPrefs.hapticIntensity(context)) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                    Haptic.perform(context, HapticType.CONFIRM)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("redmonitor://update"))
                        .setPackage(context.packageName)
                    runCatching { context.startActivity(intent) }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Auf Update prüfen") }
        }

        StatCard("Haptisches Feedback") {
            ToggleRow(
                label = "Haptik bei Aktionen",
                description = "Leichte Vibration bei Tap, kräftigere bei destruktiven Aktionen (Force-Stop, Delete, Drain)",
                checked = haptics
            ) {
                haptics = it
                AppPrefs.setHapticFeedbackEnabled(context, it)
                if (it) Haptic.perform(context, HapticType.CONFIRM)
            }

            if (haptics) {
                Spacer(Modifier.height(8.dp))
                Text("Intensität", color = OnSurfaceMuted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HapticIntensity.values().forEach { lvl ->
                        val selected = hapticIntensity == lvl
                        OutlinedButton(
                            onClick = {
                                hapticIntensity = lvl
                                AppPrefs.setHapticIntensity(context, lvl)
                                Haptic.perform(context, HapticType.TAP)
                            },
                            modifier = Modifier.weight(1f),
                            border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, Accent) else null
                        ) {
                            Text(
                                lvl.label,
                                color = if (selected) Accent else androidx.compose.ui.graphics.Color.Unspecified,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Tester:", color = OnSurfaceMuted, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { Haptic.perform(context, HapticType.TAP) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) { Text("Tap", fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = { Haptic.perform(context, HapticType.TOGGLE) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) { Text("Toggle", fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = { Haptic.perform(context, HapticType.DESTRUCTIVE) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) { Text("Destruktiv", fontSize = 11.sp) }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { Haptic.perform(context, HapticType.CONFIRM) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) { Text("Confirm", fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = { Haptic.perform(context, HapticType.ERROR) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) { Text("Error", fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = { Haptic.perform(context, HapticType.SLIDER_TICK) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) { Text("Slider", fontSize = 11.sp) }
                }
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
                    Haptic.perform(context, HapticType.CONFIRM)
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
                    Haptic.perform(context, HapticType.TAP)
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/theredstonee/redmonitor")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("GitHub-Repository öffnen") }
        }

        StatCard("App bewerten") {
            Text(
                "Wenn dir RedMonitor gefällt — lass eine Bewertung auf Trustpilot da. Hilft anderen, die App zu finden, und mir, sie besser zu machen.",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    Haptic.perform(context, HapticType.CONFIRM)
                    AppPrefs.setHasRated(context, true)
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://de.trustpilot.com/evaluate/theredstonee.de")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("⭐ Auf Trustpilot bewerten") }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    Haptic.perform(context, HapticType.TAP)
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://de.trustpilot.com/review/theredstonee.de")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Bewertungen ansehen") }
        }

        StatCard("Über") {
            KeyValueRow("Version", BuildConfig.VERSION_NAME)
            KeyValueRow("Build", BuildConfig.VERSION_CODE.toString())
            KeyValueRow("Anwendungs-ID", BuildConfig.APPLICATION_ID)
            KeyValueRow("Lizenz", "MIT")
            KeyValueRow("Hersteller", "TheRedStonee")
            Spacer(Modifier.height(12.dp))
            AppComponentsBlock(context)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    Haptic.perform(context, HapticType.TAP)
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

private data class AppComponent(
    val kind: String,
    val fqcn: String,
    val running: Boolean = false
)

private fun readComponents(context: Context): List<AppComponent> {
    val pm = context.packageManager
    val pkg = context.packageName
    val pi = runCatching {
        pm.getPackageInfo(
            pkg,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                PackageManager.GET_PROVIDERS or PackageManager.GET_RECEIVERS
        )
    }.getOrNull() ?: return emptyList()

    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    @Suppress("DEPRECATION")
    val runningServices: Set<String> = runCatching {
        am?.getRunningServices(Int.MAX_VALUE)
            ?.filter { it.service.packageName == pkg }
            ?.map { it.service.className }
            ?.toSet()
            ?: emptySet()
    }.getOrDefault(emptySet())

    val out = mutableListOf<AppComponent>()
    pi.activities?.forEach { out += AppComponent("Activity", it.name) }
    pi.services?.forEach { out += AppComponent("Service", it.name, running = it.name in runningServices) }
    pi.providers?.forEach { out += AppComponent("Provider", it.name) }
    pi.receivers?.forEach { out += AppComponent("Receiver", it.name) }
    return out.sortedWith(compareBy({ it.kind }, { it.fqcn }))
}

@Composable
private fun AppComponentsBlock(context: Context) {
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()
    var expanded by remember { mutableStateOf(false) }
    var comps by remember { mutableStateOf<List<AppComponent>>(emptyList()) }
    val pkg = remember { context.packageName }

    LaunchedEffect(Unit) {
        comps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            readComponents(context)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic(HapticType.TAP)
                expanded = !expanded
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Komponenten",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${comps.size} interne $pkg.* Klassen",
                color = OnSurfaceMuted,
                fontSize = 11.sp
            )
        }
        Text(
            if (expanded) "v" else ">",
            color = AccentSoft,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }

    if (expanded) {
        Spacer(Modifier.height(4.dp))
        val byKind = comps.groupBy { it.kind }
        listOf("Activity", "Service", "Provider", "Receiver").forEach { kind ->
            val items = byKind[kind].orEmpty()
            if (items.isEmpty()) return@forEach
            Text(
                "$kind  (${items.size})",
                color = Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
            )
            items.forEach { c ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        shortenFqcn(c.fqcn, pkg),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    if (kind == "Service" && c.running) {
                        Text(
                            "• läuft",
                            color = GaugeGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun shortenFqcn(fqcn: String, pkg: String): String {
    if (fqcn.startsWith("$pkg.")) return "." + fqcn.substring(pkg.length + 1)
    return fqcn
}

@Composable
private fun ToggleRow(
    label: String,
    description: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()
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
            checked = checked,
            onCheckedChange = {
                haptic(HapticType.TOGGLE)
                onChange(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = Accent
            )
        )
    }
}
