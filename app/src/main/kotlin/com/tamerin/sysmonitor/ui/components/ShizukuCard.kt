package com.tamerin.sysmonitor.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

/**
 * Shared Shizuku setup card. Used by Battery (for PD watts), Tasks (for process list + actions),
 * Logcat, Doze-Whitelist, etc.
 *
 * [title] and [description] let each call site explain WHY Shizuku is needed in that context.
 */
@Composable
fun ShizukuCard(
    title: String = "Shizuku einrichten",
    description: String = "Mit Shizuku (offizielle, quelloffene App von Rikka) bekommt diese App " +
        "Zugriff auf System-APIs im shell-User-Kontext — ohne Root. Damit sehen wir mehr Daten " +
        "und können mehr Aktionen ausführen.",
    onReady: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()
    var state by remember { mutableStateOf(ShizukuHelper.state(context)) }

    DisposableEffect(Unit) {
        val listener = rikka.shizuku.Shizuku.OnRequestPermissionResultListener { _, _ ->
            state = ShizukuHelper.state(context)
        }
        ShizukuHelper.addPermissionListener(listener)
        onDispose { ShizukuHelper.removePermissionListener(listener) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            state = ShizukuHelper.state(context)
            kotlinx.coroutines.delay(2500)
        }
    }

    StatCard(title) {
        Text(description, color = OnSurfaceMuted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        KeyValueRow("Status", when (state) {
            ShizukuHelper.State.NotInstalled -> "Nicht installiert"
            ShizukuHelper.State.NotRunning -> "Installiert, aber nicht gestartet"
            ShizukuHelper.State.NeedsPermission -> "Läuft — Berechtigung fehlt"
            ShizukuHelper.State.Ready -> "Bereit ✓"
        })
        Spacer(Modifier.height(10.dp))
        when (state) {
            ShizukuHelper.State.NotInstalled -> NotInstalledBlock(context, haptic)
            ShizukuHelper.State.NotRunning -> NotRunningBlock(context, haptic)
            ShizukuHelper.State.NeedsPermission -> NeedsPermBlock(haptic)
            ShizukuHelper.State.Ready -> {
                Text(
                    "Perfekt. Setup abgeschlossen.",
                    color = GaugeGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                LaunchedEffect(state) { onReady?.invoke() }
            }
        }
    }
}

@Composable
private fun NotInstalledBlock(
    context: Context,
    haptic: (com.tamerin.sysmonitor.settings.HapticType) -> Unit
) {
    val sdkInt = android.os.Build.VERSION.SDK_INT
    val manufacturer = android.os.Build.MANUFACTURER?.lowercase().orEmpty()
    val recommendGithub = sdkInt >= android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM ||
        manufacturer.contains("samsung")

    if (recommendGithub) {
        Text(
            "Erkannt: Android $sdkInt · ${android.os.Build.MANUFACTURER}. " +
                "Auf deinem Gerät blockiert der Play Store Shizuku meistens. " +
                "Empfohlen: APK direkt von GitHub.",
            color = AccentSoft, fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
            openUrl(context, "https://github.com/RikkaApps/Shizuku/releases/latest")
        }, modifier = Modifier.fillMaxWidth()) {
            Text("APK von GitHub laden (empfohlen)")
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = {
            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
            openUrl(context, "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Trotzdem Play Store versuchen")
        }
    } else {
        Text(
            "Erkannt: Android $sdkInt · ${android.os.Build.MANUFACTURER}. " +
                "Play Store sollte bei dir funktionieren.",
            color = OnSurfaceMuted, fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
            openUrl(context, "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Im Play Store öffnen")
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = {
            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
            openUrl(context, "https://github.com/RikkaApps/Shizuku/releases/latest")
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Alternativ: APK von GitHub")
        }
    }
    Spacer(Modifier.height(6.dp))
    OutlinedButton(onClick = {
        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
        openUrl(context, "https://shizuku.rikka.app/guide/setup/")
    }, modifier = Modifier.fillMaxWidth()) {
        Text("Setup-Anleitung")
    }
}

@Composable
private fun NotRunningBlock(
    context: Context,
    haptic: (com.tamerin.sysmonitor.settings.HapticType) -> Unit
) {
    Text(
        "Shizuku ist installiert, läuft aber nicht. Du musst ihn jetzt starten:",
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(8.dp))
    StepRow("1.", "Einstellungen öffnen", "Android-Einstellungen → Entwickleroptionen")
    StepRow("2.", "Drahtloses Debugging AN", "muss eingeschaltet bleiben")
    StepRow("3.", "Pairing-Code holen", "in Entwickleroptionen: 'Gerät mit Pairing-Code koppeln'")
    StepRow("4.", "Shizuku öffnen", "Pair via Wireless Debugging → Code eingeben")
    StepRow("5.", "In Shizuku: Start", "grüner Status oben muss erscheinen")
    StepRow("6.", "Hierher zurückkommen", "Status springt automatisch")
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                runCatching {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            },
            modifier = Modifier.weight(1f)
        ) { Text("Entwickler-Settings", fontSize = 12.sp) }
        Button(
            onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                val launchIntent = context.packageManager
                    .getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (launchIntent != null) context.startActivity(launchIntent)
            },
            modifier = Modifier.weight(1f)
        ) { Text("Shizuku öffnen", fontSize = 12.sp) }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Hinweis: Shizuku überlebt keinen Reboot. Nach jedem Neustart musst du Schritte 2-5 wiederholen (außer du hast Root).",
        color = OnSurfaceMuted, fontSize = 10.sp
    )
}

@Composable
private fun NeedsPermBlock(haptic: (com.tamerin.sysmonitor.settings.HapticType) -> Unit) {
    Text(
        "Shizuku läuft. Jetzt fehlt nur noch deine Erlaubnis für diese App.",
        color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
            ShizukuHelper.requestPermission()
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Berechtigung erteilen") }
    Spacer(Modifier.height(6.dp))
    Text(
        "Es erscheint ein Shizuku-Dialog — auf 'Allow' tippen.",
        color = OnSurfaceMuted, fontSize = 11.sp
    )
}

@Composable
private fun StepRow(num: String, title: String, hint: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = androidx.compose.ui.Alignment.Top
    ) {
        Text(
            num,
            color = Accent,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.width(22.dp)
        )
        Column {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp,
                fontWeight = FontWeight.Medium)
            Text(hint, color = OnSurfaceMuted, fontSize = 10.sp)
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
