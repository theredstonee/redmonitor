package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.Oem
import com.tamerin.sysmonitor.data.OemDetect
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.settings.AppPrefs
import com.tamerin.sysmonitor.ui.components.OemSetupCard
import com.tamerin.sysmonitor.ui.components.SectionEyebrow
import com.tamerin.sysmonitor.ui.components.ShizukuCard
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

@Composable
fun OemOnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val spec = remember { OemDetect.detect() }
    val scroll = rememberScrollState()
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionEyebrow("Setup")
        Text(
            "Gerät vorbereiten",
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Damit RedMonitor auf deinem ${spec.skinName}-Gerät zuverlässig läuft, " +
                "musst du je nach Hersteller ein paar Limits lockern. " +
                "Wir gehen sie hier Schritt für Schritt durch.",
            color = OnSurfaceMuted,
            fontSize = 13.sp
        )

        StatCard("Gerät") {
            Text(spec.displayName, color = AccentSoft, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            if (spec.isMiui || spec.isHyperOs) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Xiaomi-Geräte schränken Apps am stärksten ein - alle Schritte unten sind hier wichtig.",
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }
        }

        OemSetupCard(spec)

        if (spec.oem == Oem.XIAOMI) {
            StatCard("Xiaomi-spezifisch: Recents sperren") {
                Text(
                    "Im Recents-Bildschirm (Übersicht aller offenen Apps) auf die RedMonitor-Karte lange drücken " +
                        "und 'Sperren' / Vorhängeschloss tippen. Damit überlebt die App den 'Alles schließen'-Wisch " +
                        "und den MIUI Memory Cleaner.",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
            }
        }

        if (spec.oem == Oem.SAMSUNG) {
            StatCard("Samsung-spezifisch: Schlafende Apps") {
                Text(
                    "Settings -> Akku -> Hintergrund-Nutzungslimits -> Schlafende Apps. " +
                        "RedMonitor darf NICHT in dieser Liste stehen. Falls doch: entfernen.",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
            }
        }

        StatCard("Shizuku (für Logcat, Tasks, Doze, ...)") {
            Text(
                "Shizuku gibt dieser App shell-User-Rechte ohne Root. Wird gebraucht für: " +
                    "Logcat lesen, Top-CPU-App ermitteln, Doze-Whitelist verwalten, App-Force-Stop.",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
        }
        ShizukuCard(
            title = "Shizuku-Status",
            description = "Status & Setup-Schritte falls noch nicht bereit."
        )

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                AppPrefs.setOemOnboardingDone(context, true)
                onDone()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Fertig - zur App") }
        OutlinedButton(
            onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                AppPrefs.setOemOnboardingDone(context, true)
                onDone()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Später erinnern (nicht mehr beim Start zeigen)") }
        Spacer(Modifier.height(24.dp))
    }
}
