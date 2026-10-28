package com.tamerin.sysmonitor.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.privileged.PrivilegedBackend
import com.tamerin.sysmonitor.privileged.PrivilegedManager
import com.tamerin.sysmonitor.privileged.adb.AdbConnectionManager
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
    title: String = stringResource(R.string.scr2_shz_title),
    description: String = stringResource(R.string.scr2_shz_desc),
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
            ShizukuHelper.State.NotInstalled -> stringResource(R.string.scr2_shz_state_notinstalled)
            ShizukuHelper.State.NotRunning -> stringResource(R.string.scr2_shz_state_notrunning)
            ShizukuHelper.State.NeedsPermission -> stringResource(R.string.scr2_shz_state_needsperm)
            ShizukuHelper.State.Ready -> when (PrivilegedManager.activeKind(context)) {
                PrivilegedBackend.ADB -> stringResource(R.string.scr2_shz_state_ready_adb)
                PrivilegedBackend.ROOT -> stringResource(R.string.scr2_shz_state_ready_root)
                else -> stringResource(R.string.scr2_shz_state_ready_shizuku)
            }
        })
        Spacer(Modifier.height(10.dp))
        when (state) {
            ShizukuHelper.State.NotInstalled -> NotInstalledBlock(context, haptic)
            ShizukuHelper.State.NotRunning -> NotRunningBlock(context, haptic)
            ShizukuHelper.State.NeedsPermission -> NeedsPermBlock(haptic)
            ShizukuHelper.State.Ready -> {
                var report by remember { mutableStateOf<ShizukuHelper.AutoGrantReport?>(null) }
                Text(
                    stringResource(R.string.scr2_shz_setup_done),
                    color = GaugeGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                LaunchedEffect(state) {
                    // Vollständiges Auto-Setup: alle Runtime-Perms + Special-Ops
                    // + Notification-Listener in einem Rutsch freischalten.
                    report = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        ShizukuHelper.autoGrantAllPermissions(context)
                    }
                    onReady?.invoke()
                }
                report?.let { r ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.scr2_shz_autogrant, r.granted.size, r.failed.size, r.skipped.size),
                        color = OnSurfaceMuted, fontSize = 11.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            // Re-run für Debug oder nach Permission-Revoke
                            kotlinx.coroutines.GlobalScope.launch(
                                kotlinx.coroutines.Dispatchers.IO
                            ) {
                                report = ShizukuHelper.autoGrantAllPermissions(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.scr2_shz_autogrant_rerun), fontSize = 11.sp) }
                }
            }
        }

        // Alternative ohne Shizuku: eigener eingebetteter ADB-Zugang (PC-Weg).
        Spacer(Modifier.height(14.dp))
        AdbAccessSection(context, haptic)
    }
}

/**
 * Aufklappbarer Bereich für den eigenen ADB-Zugang (ohne Shizuku). Die App ist selbst
 * der ADB-Client: einmal am PC `adb tcpip 5555` aktivieren, dann verbindet sich die App
 * zu 127.0.0.1:5555 und führt danach alle Kommandos selbst über ADB aus. Beim App-Start
 * verbindet sie automatisch wieder (sofern aktiviert).
 */
@Composable
private fun AdbAccessSection(
    context: Context,
    haptic: (com.tamerin.sysmonitor.settings.HapticType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var adbConnected by remember { mutableStateOf(AdbConnectionManager.isConnected()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            adbConnected = AdbConnectionManager.isConnected()
            kotlinx.coroutines.delay(2000)
        }
    }

    OutlinedButton(
        onClick = {
            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
            expanded = !expanded
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            if (expanded) stringResource(R.string.scr2_shz_adb_collapse)
            else stringResource(R.string.scr2_shz_adb_expand),
            fontSize = 11.sp
        )
    }

    if (!expanded) return

    Spacer(Modifier.height(10.dp))
    Text(
        stringResource(R.string.scr2_shz_adb_desc),
        color = OnSurfaceMuted, fontSize = 12.sp
    )
    Spacer(Modifier.height(6.dp))
    KeyValueRow(stringResource(R.string.scr2_shz_adb_label), if (adbConnected) stringResource(R.string.scr2_shz_connected) else stringResource(R.string.scr2_shz_not_connected))
    Spacer(Modifier.height(10.dp))

    StepRow("1.", stringResource(R.string.scr2_shz_step_pc_title), stringResource(R.string.scr2_shz_step_pc_hint))
    Spacer(Modifier.height(6.dp))
    val tcpipCmd = "adb tcpip 5555"
    Text(
        tcpipCmd,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace
    )
    Spacer(Modifier.height(6.dp))
    OutlinedButton(
        onClick = {
            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("adb tcpip", tcpipCmd))
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text(stringResource(R.string.scr2_shz_copy_cmd), fontSize = 11.sp) }
    Spacer(Modifier.height(6.dp))
    OutlinedButton(
        onClick = {
            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
            openSettings(
                context,
                android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
                android.provider.Settings.ACTION_SETTINGS
            )
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text(stringResource(R.string.scr2_shz_open_dev_settings), fontSize = 11.sp) }

    Spacer(Modifier.height(4.dp))
    StepRow("2.", stringResource(R.string.scr2_shz_step_connect_title), stringResource(R.string.scr2_shz_step_connect_hint))
    Spacer(Modifier.height(8.dp))

    if (!adbConnected) {
        Button(
            enabled = !busy,
            onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                busy = true
                message = null
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val res = AdbConnectionManager.connect(context)
                    val connected = AdbConnectionManager.isConnected()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        busy = false
                        adbConnected = connected
                        message = if (res.isSuccess) context.getString(R.string.scr2_shz_connected)
                        else context.getString(R.string.scr2_shz_failed, res.exceptionOrNull()?.message ?: context.getString(R.string.scr2_shz_unknown))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) stringResource(R.string.scr2_shz_connecting) else stringResource(R.string.scr2_shz_connect_now), fontSize = 12.sp) }
    } else {
        OutlinedButton(
            onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    AdbConnectionManager.disconnect(context)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        adbConnected = false
                        message = context.getString(R.string.scr2_shz_disconnected_msg)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.scr2_shz_disconnect), fontSize = 12.sp) }
    }

    message?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, color = OnSurfaceMuted, fontSize = 11.sp)
    }
    Spacer(Modifier.height(6.dp))
    Text(
        stringResource(R.string.scr2_shz_tcpip_note),
        color = OnSurfaceMuted, fontSize = 10.sp
    )

    // --- Ohne PC: Wireless Debugging ---
    Spacer(Modifier.height(14.dp))
    Text(
        stringResource(R.string.scr2_shz_wireless_title),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.scr2_shz_wireless_desc),
        color = OnSurfaceMuted, fontSize = 11.sp
    )
    Spacer(Modifier.height(6.dp))
    OutlinedButton(
        onClick = {
            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
            openSettings(
                context,
                "android.settings.ADB_WIRELESS_SETTINGS",
                android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
                android.provider.Settings.ACTION_SETTINGS
            )
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text(stringResource(R.string.scr2_shz_open_wireless_settings), fontSize = 11.sp) }
    Spacer(Modifier.height(8.dp))
    var code by remember { mutableStateOf("") }
    OutlinedTextField(
        value = code,
        onValueChange = { v -> if (v.length <= 6 && v.all { it.isDigit() }) code = v },
        label = { Text(stringResource(R.string.scr2_shz_pairing_label), fontSize = 11.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val res = AdbConnectionManager.pairWireless(context, code)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        message = if (res.isSuccess) context.getString(R.string.scr2_shz_paired)
                        else context.getString(R.string.scr2_shz_pairing_fail, res.exceptionOrNull()?.message ?: context.getString(R.string.scr2_shz_failed_word))
                    }
                }
            },
            modifier = Modifier.weight(1f)
        ) { Text(stringResource(R.string.scr2_shz_pair), fontSize = 11.sp) }
        OutlinedButton(
            onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val res = AdbConnectionManager.connectWireless(context)
                    val connected = AdbConnectionManager.isConnected()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        adbConnected = connected
                        message = if (res.isSuccess) context.getString(R.string.scr2_shz_wireless_connected)
                        else context.getString(R.string.scr2_shz_wireless_fail, res.exceptionOrNull()?.message ?: context.getString(R.string.scr2_shz_failed_word))
                    }
                }
            },
            modifier = Modifier.weight(1f)
        ) { Text(stringResource(R.string.scr2_shz_wireless_connect), fontSize = 11.sp) }
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
            stringResource(R.string.scr2_shz_detected_github, sdkInt, android.os.Build.MANUFACTURER),
            color = AccentSoft, fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
            openUrl(context, "https://github.com/RikkaApps/Shizuku/releases/latest")
        }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.scr2_shz_github_recommended))
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = {
            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
            openUrl(context, "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")
        }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.scr2_shz_try_play))
        }
    } else {
        Text(
            stringResource(R.string.scr2_shz_detected_play, sdkInt, android.os.Build.MANUFACTURER),
            color = OnSurfaceMuted, fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
            openUrl(context, "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")
        }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.scr2_shz_open_play))
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = {
            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
            openUrl(context, "https://github.com/RikkaApps/Shizuku/releases/latest")
        }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.scr2_shz_alt_github))
        }
    }
    Spacer(Modifier.height(6.dp))
    OutlinedButton(onClick = {
        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
        openUrl(context, "https://shizuku.rikka.app/guide/setup/")
    }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.scr2_shz_setup_guide))
    }
}

@Composable
private fun NotRunningBlock(
    context: Context,
    haptic: (com.tamerin.sysmonitor.settings.HapticType) -> Unit
) {
    Text(
        stringResource(R.string.scr2_shz_notrunning_intro),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(8.dp))
    StepRow("1.", stringResource(R.string.scr2_shz_nr_step1_title), stringResource(R.string.scr2_shz_nr_step1_hint))
    StepRow("2.", stringResource(R.string.scr2_shz_nr_step2_title), stringResource(R.string.scr2_shz_nr_step2_hint))
    StepRow("3.", stringResource(R.string.scr2_shz_nr_step3_title), stringResource(R.string.scr2_shz_nr_step3_hint))
    StepRow("4.", stringResource(R.string.scr2_shz_nr_step4_title), stringResource(R.string.scr2_shz_nr_step4_hint))
    StepRow("5.", stringResource(R.string.scr2_shz_nr_step5_title), stringResource(R.string.scr2_shz_nr_step5_hint))
    StepRow("6.", stringResource(R.string.scr2_shz_nr_step6_title), stringResource(R.string.scr2_shz_nr_step6_hint))
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
        ) { Text(stringResource(R.string.scr2_shz_dev_settings), fontSize = 12.sp) }
        Button(
            onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                val launchIntent = context.packageManager
                    .getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (launchIntent != null) context.startActivity(launchIntent)
            },
            modifier = Modifier.weight(1f)
        ) { Text(stringResource(R.string.scr2_shz_nr_step4_title), fontSize = 12.sp) }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.scr2_shz_notrunning_note),
        color = OnSurfaceMuted, fontSize = 10.sp
    )
}

@Composable
private fun NeedsPermBlock(haptic: (com.tamerin.sysmonitor.settings.HapticType) -> Unit) {
    Text(
        stringResource(R.string.scr2_shz_needsperm_intro),
        color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
            ShizukuHelper.requestPermission()
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text(stringResource(R.string.scr2_shz_grant_perm)) }
    Spacer(Modifier.height(6.dp))
    Text(
        stringResource(R.string.scr2_shz_allow_hint),
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

/**
 * Öffnet die erste System-Einstellungsseite, die sich starten lässt. Reihenfolge =
 * spezifischste zuerst (z. B. der Wireless-Debugging-Screen ab Android 11), mit
 * Entwickleroptionen und den allgemeinen Einstellungen als Fallback — so landet der
 * Nutzer immer auf einer passenden Seite statt im Nichts.
 */
private fun openSettings(context: Context, vararg actions: String) {
    for (action in actions) {
        val ok = runCatching {
            context.startActivity(
                Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (ok) return
    }
}
