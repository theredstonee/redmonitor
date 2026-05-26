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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.delay

private data class RunningApp(
    val pkg: String,
    val lastUsed: Long,
    val totalForeground: Long
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
            hasUsageAccess = checkUsageAccess(context)
            if (hasUsageAccess) {
                apps = readUsage(context)
            }
            // Own process info — works without permission
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            ownPid = Process.myPid()
            val info = am.getProcessMemoryInfo(intArrayOf(ownPid)).firstOrNull()
            if (info != null) {
                ownMemMb = info.totalPss / 1024
            }
            delay(3000)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StatCard("Eigener Prozess") {
                KeyValueRow("PID", ownPid.toString())
                KeyValueRow("RAM (PSS)", "$ownMemMb MB")
            }
        }
        if (!hasUsageAccess) {
            item {
                StatCard("Berechtigung benötigt") {
                    Text(
                        "Seit Android 7 darf eine App nur den eigenen Prozess sehen. Für eine Übersicht aller aktiven Apps brauchen wir „Nutzungsdaten“-Zugriff (PACKAGE_USAGE_STATS).",
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
                        Text("Berechtigung erteilen")
                    }
                }
            }
        } else {
            item {
                StatCard("Aktive Apps (letzte Stunde)") {
                    KeyValueRow("Anzahl", apps.size.toString())
                    Text(
                        "Aus Datenschutzgründen liefert Android keine Live-PIDs anderer Apps mehr — wir zeigen alle Apps, die kürzlich im Vordergrund waren.",
                        color = OnSurfaceMuted,
                        fontSize = 11.sp
                    )
                }
            }
            items(apps, key = { it.pkg }) { app ->
                StatCard(app.pkg) {
                    KeyValueRow("Vordergrundzeit", "${app.totalForeground / 1000} s")
                    KeyValueRow("Zuletzt aktiv", relativeTime(app.lastUsed))
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

private fun relativeTime(timestamp: Long): String {
    if (timestamp <= 0) return "—"
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "vor ${diff / 1000} s"
        diff < 3_600_000 -> "vor ${diff / 60_000} min"
        diff < 86_400_000 -> "vor ${diff / 3_600_000} h"
        else -> "vor ${diff / 86_400_000} d"
    }
}
