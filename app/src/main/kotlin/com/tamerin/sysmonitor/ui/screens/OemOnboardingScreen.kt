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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.R
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
    val spec = remember { OemDetect.detect(context) }
    val scroll = rememberScrollState()
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionEyebrow(stringResource(R.string.oem_eyebrow_setup))
        Text(
            stringResource(R.string.oem_title),
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.oem_intro, spec.skinName),
            color = OnSurfaceMuted,
            fontSize = 13.sp
        )

        StatCard(stringResource(R.string.oem_device_card)) {
            Text(spec.displayName, color = AccentSoft, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(
                    R.string.oem_device_android,
                    android.os.Build.VERSION.RELEASE,
                    android.os.Build.VERSION.SDK_INT
                ),
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            if (spec.isMiui || spec.isHyperOs) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.oem_xiaomi_strict_note),
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }
        }

        OemSetupCard(spec)

        if (spec.oem == Oem.XIAOMI) {
            StatCard(stringResource(R.string.oem_xiaomi_recents_title)) {
                Text(
                    stringResource(R.string.oem_xiaomi_recents_body),
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
            }
        }

        if (spec.oem == Oem.SAMSUNG) {
            StatCard(stringResource(R.string.oem_samsung_sleep_title)) {
                Text(
                    stringResource(R.string.oem_samsung_sleep_body),
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
            }
        }

        StatCard(stringResource(R.string.oem_shizuku_title)) {
            Text(
                stringResource(R.string.oem_shizuku_body),
                color = OnSurfaceMuted, fontSize = 12.sp
            )
        }
        ShizukuCard(
            title = stringResource(R.string.oem_shizuku_status_title),
            description = stringResource(R.string.oem_shizuku_status_desc)
        )

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                AppPrefs.setOemOnboardingDone(context, true)
                onDone()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.oem_done_button)) }
        OutlinedButton(
            onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                AppPrefs.setOemOnboardingDone(context, true)
                onDone()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.oem_remind_later)) }
        Spacer(Modifier.height(24.dp))
    }
}
