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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.R
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
    val context = LocalContext.current
    val spec = remember { OemDetect.detect(context) }
    if (!force && spec.restrictionLevel == OemRestrictionLevel.LOW) return

    val (bg, border, accent, label) = when (spec.restrictionLevel) {
        OemRestrictionLevel.HIGH -> Quad(Color(0x1FDC2626), Color(0x66DC2626), GaugeRed, stringResource(R.string.comp_oem_badge_setup))
        OemRestrictionLevel.MEDIUM -> Quad(Color(0x1FF97316), Color(0x66F97316), GaugeOrange, stringResource(R.string.comp_oem_badge_recommended))
        OemRestrictionLevel.LOW -> Quad(Color(0x1F22C55E), Color(0x6622C55E), GaugeGreen, stringResource(R.string.comp_oem_badge_optional))
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
                    stringResource(
                        R.string.comp_oem_detected,
                        "${spec.skinName}${spec.skinVersion?.let { " $it" } ?: ""}"
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    if (spec.restrictionLevel == OemRestrictionLevel.HIGH)
                        stringResource(R.string.comp_oem_hint_high, label)
                    else
                        stringResource(R.string.comp_oem_hint_tap, label),
                    color = OnSurfaceMuted,
                    fontSize = 11.sp
                )
            }
            Text(stringResource(R.string.comp_oem_setup_arrow), color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

/**
 * Full OEM-specific permission card.
 * Shows status of all known restrictions + buttons to fix each one.
 * Use inside any screen that needs a complete permission audit.
 */
@Composable
fun OemSetupCard(specOverride: OemSpec? = null) {
    val context = LocalContext.current
    val spec = specOverride ?: remember { OemDetect.detect(context) }
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
            OemRestrictionLevel.HIGH -> stringResource(R.string.comp_oem_level_high)
            OemRestrictionLevel.MEDIUM -> stringResource(R.string.comp_oem_level_medium)
            OemRestrictionLevel.LOW -> stringResource(R.string.comp_oem_level_low)
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
            label = stringResource(R.string.comp_oem_perm_battery),
            help = stringResource(R.string.comp_oem_perm_battery_help),
            granted = battOk,
            onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                OemDetect.safeStart(context, OemDetect.batteryOptIntent(context))
            }
        )
        PermissionRow(
            label = stringResource(R.string.comp_oem_perm_usage),
            help = stringResource(R.string.comp_oem_perm_usage_help),
            granted = usageOk,
            onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                OemDetect.safeStart(context, OemDetect.usageStatsIntent())
            }
        )
        PermissionRow(
            label = stringResource(R.string.comp_oem_perm_notif),
            help = stringResource(R.string.comp_oem_perm_notif_help),
            granted = notifOk,
            onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                OemDetect.safeStart(context, OemDetect.notificationSettingsIntent(context))
            }
        )
        PermissionRow(
            label = stringResource(R.string.comp_oem_perm_overlay),
            help = stringResource(R.string.comp_oem_perm_overlay_help),
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
            ) { Text(if (spec.oem == Oem.SAMSUNG) stringResource(R.string.comp_oem_btn_samsung_sleep) else stringResource(R.string.comp_oem_btn_autostart)) }
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
                ) { Text(stringResource(R.string.comp_oem_btn_miui_perms)) }
                Spacer(Modifier.height(6.dp))
            }
            OutlinedButton(
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    OemDetect.safeStart(context, OemDetect.developerSettingsIntent())
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.comp_oem_btn_developer)) }
            Spacer(Modifier.height(6.dp))
        }

        OutlinedButton(
            onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                OemDetect.safeStart(context, OemDetect.appDetailsIntent(context))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.comp_oem_btn_app_details)) }
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
            if (granted) stringResource(R.string.comp_oem_status_ok) else stringResource(R.string.comp_oem_status_open),
            color = if (granted) GaugeGreen else Accent,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
