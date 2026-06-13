package com.tamerin.sysmonitor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.Oem
import com.tamerin.sysmonitor.data.OemDetect
import com.tamerin.sysmonitor.data.OemRestrictionLevel
import com.tamerin.sysmonitor.data.OemSpec
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

/**
 * Small banner: detects OEM, shows badge + opens onboarding when tapped.
 * Hidden when restriction level is LOW (Pixel / stock Android).
 */
@Composable
fun OemHintBanner(onOpenSetup: () -> Unit, force: Boolean = false) {
    val spec = remember { OemDetect.detect() }
    if (!force && spec.restrictionLevel == OemRestrictionLevel.LOW) return

    val (bg, border, accent, label) = when (spec.restrictionLevel) {
        OemRestrictionLevel.HIGH -> Quad(Color(0x1FDC2626), Color(0x66DC2626), GaugeRed, "Eingerichtet werden")
        OemRestrictionLevel.MEDIUM -> Quad(Color(0x1FF97316), Color(0x66F97316), GaugeOrange, "Empfohlen anpassen")
        OemRestrictionLevel.LOW -> Quad(Color(0x1F22C55E), Color(0x6622C55E), GaugeGreen, "Optional")
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpenSetup() },
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.dp, border),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${spec.skinName}${spec.skinVersion?.let { " $it" } ?: ""} erkannt",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    if (spec.restrictionLevel == OemRestrictionLevel.HIGH)
                        "$label - sonst killt das System die App im Hintergrund."
                    else
                        "$label - tippen für Setup-Anleitung.",
                    color = OnSurfaceMuted,
                    fontSize = 11.sp
                )
            }
            Text("Setup >", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

/**
 * Full OEM-specific permission card.
 * Shows status of all known restrictions + buttons to fix each one.
 * Use inside any screen that needs a complete permission audit.
 */
@Composable
fun OemSetupCard(spec: OemSpec = remember { OemDetect.detect() }) {
    val context = LocalContext.current
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    var battOk by remember { mutableStateOf(OemDetect.isIgnoringBatteryOpt(context)) }
    var usageOk by remember { mutableStateOf(OemDetect.hasUsageStats(context)) }
    var notifOk by remember { mutableStateOf(OemDetect.hasNotificationPermission(context)) }
    var overlayOk by remember { mutableStateOf(OemDetect.hasOverlayPermission(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1500)
            battOk = OemDetect.isIgnoringBatteryOpt(context)
            usageOk = OemDetect.hasUsageStats(context)
            notifOk = OemDetect.hasNotificationPermission(context)
            overlayOk = OemDetect.hasOverlayPermission(context)
        }
    }

    StatCard(spec.displayName) {
        val levelLabel = when (spec.restrictionLevel) {
            OemRestrictionLevel.HIGH -> "Aggressive Limits"
            OemRestrictionLevel.MEDIUM -> "Moderate Limits"
            OemRestrictionLevel.LOW -> "Stock-nah"
        }
        val levelColor = when (spec.restrictionLevel) {
            OemRestrictionLevel.HIGH -> GaugeRed
            OemRestrictionLevel.MEDIUM -> GaugeOrange
            OemRestrictionLevel.LOW -> GaugeGreen
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(levelColor.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(levelLabel, color = levelColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))

        spec.notes.forEach { note ->
            Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                Text("- ", color = AccentSoft, fontSize = 12.sp)
                Text(note, color = OnSurfaceMuted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        PermissionRow(
            label = "Akku-Optimierung aus",
            help = "Sonst killt Doze die App im Tiefschlaf.",
            granted = battOk,
            onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                OemDetect.safeStart(context, OemDetect.batteryOptIntent(context))
            }
        )
        PermissionRow(
            label = "Nutzungsstatistiken (Top-App)",
            help = "Für 'aktive App'-Anzeige und faire Drain-Messung.",
            granted = usageOk,
            onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                OemDetect.safeStart(context, OemDetect.usageStatsIntent())
            }
        )
        PermissionRow(
            label = "Benachrichtigungen",
            help = "Update-Hinweise & Foreground-Service-Status.",
            granted = notifOk,
            onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                OemDetect.safeStart(context, OemDetect.notificationSettingsIntent(context))
            }
        )
        PermissionRow(
            label = "Über anderen Apps anzeigen",
            help = "Für das Floating-HUD Overlay nötig.",
            granted = overlayOk,
            onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                OemDetect.safeStart(context, OemDetect.overlayPermissionIntent(context))
            }
        )

        Spacer(Modifier.height(12.dp))

        val autostart = OemDetect.autostartIntent(spec.oem)
        if (autostart != null && OemDetect.canResolve(context, autostart)) {
            Button(
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    OemDetect.safeStart(context, autostart)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (spec.oem == Oem.SAMSUNG) "Schlafende Apps öffnen" else "Autostart-Manager öffnen") }
            Spacer(Modifier.height(6.dp))
        }

        if (spec.isMiui || spec.isHyperOs) {
            val miuiPerm = OemDetect.miuiOtherPermissionsIntent(context)
            if (miuiPerm != null) {
                Button(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                        OemDetect.safeStart(context, miuiPerm)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("MIUI: Andere Berechtigungen (Pop-ups)") }
                Spacer(Modifier.height(6.dp))
            }
            OutlinedButton(
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    OemDetect.safeStart(context, OemDetect.developerSettingsIntent())
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Entwickleroptionen (MIUI-Optimierung)") }
            Spacer(Modifier.height(6.dp))
        }

        OutlinedButton(
            onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                OemDetect.safeStart(context, OemDetect.appDetailsIntent(context))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("App-Details (alle Berechtigungen)") }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    help: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (granted) GaugeGreen else GaugeOrange,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(help, color = OnSurfaceMuted, fontSize = 11.sp)
        }
        Text(
            if (granted) "OK" else "Öffnen >",
            color = if (granted) GaugeGreen else Accent,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
