package com.tamerin.sysmonitor.ui.screens

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.delay

private data class RunningApp(
    val pkg: String,
    val lastUsed: Long,
    val totalForeground: Long
)

private data class RunningAppsTick(
    val access: Boolean,
    val fetched: List<RunningApp>,
    val pid: Int,
    val memMb: Int
)

@Composable
fun RunningAppsScreen() {
    val context = LocalContext.current
    var hasUsageAccess by remember { mutableStateOf(checkUsageAccess(context)) }
    var apps by remember { mutableStateOf<List<RunningApp>>(emptyList()) }
    var ownPid by remember { mutableIntStateOf(0) }
    var ownMemMb by remember { mutableIntStateOf(0) }

    LaunchedEffect(hasUsageAccess) {
        while (true) {
            val (access, fetched, pid, memMb) = kotlinx.coroutines.withContext(
                kotlinx.coroutines.Dispatchers.IO
            ) {
                val access = checkUsageAccess(context)
                val fetched = if (access) readUsage(context) else emptyList()
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val pid = Process.myPid()
                val mem = am.getProcessMemoryInfo(intArrayOf(pid)).firstOrNull()?.totalPss?.div(1024) ?: 0
                RunningAppsTick(access, fetched, pid, mem)
            }
            hasUsageAccess = access
            if (access) apps = fetched
            ownPid = pid
            ownMemMb = memMb
            delay(3000)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StatCard(stringResource(R.string.sys_ra_own_process)) {
                KeyValueRow("PID", ownPid.toString())
                KeyValueRow("RAM (PSS)", "$ownMemMb MB")
            }
        }
        if (!hasUsageAccess) {
            item {
                StatCard(stringResource(R.string.sys_ra_perm_needed)) {
                    Text(
                        stringResource(R.string.sys_ra_perm_desc),
                        color = OnSurfaceMuted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()
                    Button(onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }) {
                        Text(stringResource(R.string.sys_ra_grant_perm))
                    }
                }
            }
        } else {
            item {
                StatCard(stringResource(R.string.sys_ra_active_apps)) {
                    KeyValueRow(stringResource(R.string.sys_ra_count), apps.size.toString())
                    Text(
                        stringResource(R.string.sys_ra_privacy_note),
                        color = OnSurfaceMuted,
                        fontSize = 11.sp
                    )
                }
            }
            items(apps, key = { it.pkg }) { app ->
                StatCard(app.pkg) {
                    KeyValueRow(stringResource(R.string.sys_ra_foreground_time), "${app.totalForeground / 1000} s")
                    KeyValueRow(stringResource(R.string.sys_ra_last_active), relativeTime(context, app.lastUsed))
                }
            }
        }
    }
}

private fun checkUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun readUsage(context: Context): List<RunningApp> {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val end = System.currentTimeMillis()
    val start = end - 60 * 60 * 1000L
    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end) ?: return emptyList()
    return stats
        .filter { it.totalTimeInForeground > 0 }
        .map { RunningApp(it.packageName, it.lastTimeUsed, it.totalTimeInForeground) }
        .sortedByDescending { it.lastUsed }
        .take(50)
}

private fun relativeTime(context: Context, timestamp: Long): String {
    if (timestamp <= 0) return "—"
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> context.getString(R.string.sys_ra_ago_sec, (diff / 1000).toInt())
        diff < 3_600_000 -> context.getString(R.string.sys_ra_ago_min, (diff / 60_000).toInt())
        diff < 86_400_000 -> context.getString(R.string.sys_ra_ago_hour, (diff / 3_600_000).toInt())
        else -> context.getString(R.string.sys_ra_ago_day, (diff / 86_400_000).toInt())
    }
}
