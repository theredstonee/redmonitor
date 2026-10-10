package com.tamerin.sysmonitor.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.overlay.OverlayService
import com.tamerin.sysmonitor.settings.AppPrefs
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

@Composable
fun OverlayHudScreen(onCustomize: () -> Unit = {}) {
    val context = LocalContext.current
    var hasPerm by remember { mutableStateOf(checkPerm(context)) }
    var running by remember { mutableStateOf(false) }
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    LaunchedEffect(Unit) {
        hasPerm = checkPerm(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("Floating-HUD") {
            Text(
                stringResource(R.string.scr2_ovl_desc),
                color = OnSurfaceMuted,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            KeyValueRow(
                stringResource(R.string.scr2_ovl_perm_label),
                if (hasPerm) stringResource(R.string.scr2_ovl_granted) else stringResource(R.string.scr2_ovl_missing)
            )
        }

        if (!hasPerm) {
            StatCard(stringResource(R.string.scr2_ovl_grant_title)) {
                Text(
                    stringResource(R.string.scr2_ovl_grant_desc),
                    color = OnSurfaceMuted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.scr2_ovl_grant_btn))
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    hasPerm = checkPerm(context)
                }) {
                    Text(stringResource(R.string.scr2_ovl_recheck))
                }
            }
        } else {
            StatCard(stringResource(R.string.scr2_ovl_control_title)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                        OverlayService.start(context)
                        running = true
                    }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.scr2_ovl_start)) }
                    OutlinedButton(onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                        OverlayService.stop(context)
                        running = false
                    }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.scr2_ovl_stop)) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.scr2_ovl_fg_note),
                    color = OnSurfaceMuted,
                    fontSize = 11.sp
                )
            }

            StatCard("Autostart") {
                var autostart by remember { mutableStateOf(AppPrefs.isOverlayAutostartEnabled(context)) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.scr2_ovl_autostart_label), fontSize = 14.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.scr2_ovl_autostart_desc),
                            color = OnSurfaceMuted, fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = autostart,
                        onCheckedChange = {
                            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                            autostart = it
                            AppPrefs.setOverlayAutostartEnabled(context, it)
                            if (it) OverlayService.start(context)
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.scr2_ovl_autostart_warn),
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }

            StatCard(stringResource(R.string.scr2_ovl_customize_title)) {
                Text(
                    stringResource(R.string.scr2_ovl_customize_desc),
                    color = OnSurfaceMuted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                        onCustomize()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.scr2_ovl_customize_btn)) }
            }
        }
    }
}

private fun checkPerm(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else true
}
