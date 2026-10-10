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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.R
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
        lastAction = if (failed.isEmpty()) "✓ $label" else "⚠ $label, ${context.getString(R.string.sys_td_failed)}: ${failed.joinToString(", ")}"
        scope.launch {
            disabled = withContext(Dispatchers.IO) { AppActions.isAppDisabled(context, pkg) }
            suspended = withContext(Dispatchers.IO) { AppActions.isAppSuspended(context, pkg) }
        }
    }

    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(ai?.loadLabel(pm)?.toString() ?: pkg) {
            KeyValueRow(stringResource(R.string.sys_td_package), pkg)
            pkgInfo?.let {
                KeyValueRow(stringResource(R.string.sys_td_version), "${it.versionName} (${it.longVersionCode})")
            }
            ai?.let {
                KeyValueRow("UID", it.uid.toString())
                KeyValueRow(stringResource(R.string.sys_td_type), if ((it.flags and ApplicationInfo.FLAG_SYSTEM) != 0) stringResource(R.string.sys_td_type_system) else stringResource(R.string.sys_td_type_user))
                KeyValueRow(stringResource(R.string.sys_td_status), if (disabled) stringResource(R.string.sys_td_status_disabled) else stringResource(R.string.sys_td_status_active))
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
            com.tamerin.sysmonitor.ui.components.ShizukuCard(
                title = stringResource(R.string.sys_td_shizuku_title),
                description = stringResource(R.string.sys_td_shizuku_desc)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { AppActions.openAppInfo(context, pkg) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.sys_td_open_appinfo_fallback))
            }
            return@Column
        }

        StatCard(stringResource(R.string.sys_td_instant_actions)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.DESTRUCTIVE)
                        runAction("Force-Stop") { AppActions.forceStop(context, pkg) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GaugeRed),
                    modifier = Modifier.weight(1f)
                ) { Text("Force-Stop", fontSize = 13.sp) }
                Button(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.DESTRUCTIVE)
                        runAction("Crash (am crash)") { AppActions.crashApp(context, pkg) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GaugeRed),
                    modifier = Modifier.weight(1f)
                ) { Text("Crash", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                        runAction("Soft-Kill") { AppActions.softKill(context, pkg) }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Soft-Kill", fontSize = 13.sp) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.sys_td_instant_desc),
                color = OnSurfaceMuted, fontSize = 11.sp
            )
        }

        StatCard(stringResource(R.string.sys_td_clear_cache)) {
            OutlinedButton(
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    runAction("Cache geleert") { AppActions.clearCache(context, pkg) }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.sys_td_clear_cache_btn), fontSize = 13.sp) }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.sys_td_clear_cache_desc),
                color = OnSurfaceMuted, fontSize = 11.sp
            )
        }

        StatCard(stringResource(R.string.sys_td_uninstall)) {
            Button(
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.DESTRUCTIVE)
                    confirmDialog = ConfirmAction.Uninstall(isSystemApp)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GaugeRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isSystemApp) stringResource(R.string.sys_td_hide_system_app) else stringResource(R.string.sys_td_uninstall_app),
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (isSystemApp)
                    stringResource(R.string.sys_td_uninstall_system_desc)
                else stringResource(R.string.sys_td_uninstall_user_desc),
                color = OnSurfaceMuted, fontSize = 11.sp
            )
        }

        StatCard(stringResource(R.string.sys_td_app_status)) {
            // SUSPEND toggle (zuverlässiger als disable auf Samsung)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.sys_td_active), fontSize = 14.sp)
                    Text(
                        if (suspended) stringResource(R.string.sys_td_suspended_desc)
                        else stringResource(R.string.sys_td_running_normal),
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
                    Text(stringResource(R.string.sys_td_disable_legacy), fontSize = 14.sp)
                    Text(
                        if (disabled) stringResource(R.string.sys_td_disabled) else stringResource(R.string.sys_td_enabled_samsung),
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

        StatCard(stringResource(R.string.sys_td_deepfreeze)) {
            Text(
                stringResource(R.string.sys_td_deepfreeze_desc),
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.DESTRUCTIVE)
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { AppActions.deepFreeze(context, pkg) }
                            runMulti("Deep-Freeze", r)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GaugeRed),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.sys_td_freeze_btn), fontSize = 13.sp) }
                OutlinedButton(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { AppActions.unfreeze(context, pkg) }
                            runMulti("Auftauen", r)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.sys_td_unfreeze_btn), fontSize = 13.sp) }
            }
        }

        StatCard(stringResource(R.string.sys_td_doze)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.sys_td_on_whitelist), fontSize = 14.sp)
                    Text(
                        if (dozeWhitelisted) stringResource(R.string.sys_td_doze_on_desc)
                        else stringResource(R.string.sys_td_doze_off_desc),
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

        StatCard(stringResource(R.string.sys_td_standby)) {
            Text(
                stringResource(R.string.sys_td_standby_desc),
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

        StatCard(stringResource(R.string.sys_td_bg)) {
            val bg = bgState
            if (bg == null) {
                Text(stringResource(R.string.sys_td_loading), color = OnSurfaceMuted, fontSize = 12.sp)
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
                    stringResource(R.string.sys_td_bg_desc),
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }
        }

        StatCard(stringResource(R.string.sys_td_autostart)) {
            if (receivers.isEmpty()) {
                Text(
                    stringResource(R.string.sys_td_no_boot_receivers),
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
            } else {
                Text(
                    stringResource(R.string.sys_td_boot_receivers_count, receivers.size),
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
                                haptic(com.tamerin.sysmonitor.settings.HapticType.TOGGLE)
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

        StatCard(stringResource(R.string.sys_td_misc)) {
            OutlinedButton(
                onClick = { AppActions.openAppInfo(context, pkg) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.sys_td_open_in_system))
            }
        }

        // Big gap so the destructive button is visually isolated
        Spacer(Modifier.height(48.dp))

        StatCard(stringResource(R.string.sys_td_danger_zone)) {
            Text(
                stringResource(R.string.sys_td_danger_desc),
                color = GaugeRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.DESTRUCTIVE)
                    confirmDialog = ConfirmAction.ClearData
                },
                colors = ButtonDefaults.buttonColors(containerColor = GaugeRed),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.sys_td_reset_btn), fontSize = 13.sp) }
        }

        Spacer(Modifier.height(24.dp))
    }

    // Confirmation dialog
    confirmDialog?.let { action ->
        val dialogTitle = when (action) {
            is ConfirmAction.ClearData -> stringResource(R.string.sys_td_cd_title)
            is ConfirmAction.Uninstall ->
                if (action.isSystem) stringResource(R.string.sys_td_un_title_sys)
                else stringResource(R.string.sys_td_un_title)
        }
        val dialogBody = when (action) {
            is ConfirmAction.ClearData -> stringResource(R.string.sys_td_cd_body, pkg)
            is ConfirmAction.Uninstall ->
                if (action.isSystem) stringResource(R.string.sys_td_un_body_sys, pkg)
                else stringResource(R.string.sys_td_un_body, pkg)
        }
        val actionLabel = when (action) {
            is ConfirmAction.ClearData -> stringResource(R.string.sys_td_cd_label)
            is ConfirmAction.Uninstall ->
                if (action.isSystem) stringResource(R.string.sys_td_un_label_sys)
                else stringResource(R.string.sys_td_un_label)
        }
        AlertDialog(
            onDismissRequest = { confirmDialog = null },
            title = { Text(dialogTitle) },
            text = { Text(dialogBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.DESTRUCTIVE)
                        confirmDialog = null
                        runAction(actionLabel) {
                            when (action) {
                                is ConfirmAction.ClearData -> AppActions.clearAllData(context, pkg)
                                is ConfirmAction.Uninstall -> AppActions.uninstall(context, pkg, action.isSystem)
                            }
                        }
                    }
                ) { Text(stringResource(R.string.sys_td_confirm), color = GaugeRed) }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    confirmDialog = null
                }) { Text(stringResource(R.string.sys_td_cancel)) }
            }
        )
    }
}

private sealed interface ConfirmAction {
    object ClearData : ConfirmAction
    data class Uninstall(val isSystem: Boolean) : ConfirmAction
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
