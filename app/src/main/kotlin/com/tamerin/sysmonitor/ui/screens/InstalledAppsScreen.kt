package com.tamerin.sysmonitor.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.data.formatBytes
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class InstalledApp(
    val pkg: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    val apkSizeBytes: Long
)

@Composable
fun InstalledAppsScreen() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var showSystem by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .map { ai ->
                    val pkgInfo = runCatching { pm.getPackageInfo(ai.packageName, 0) }.getOrNull()
                    val apkSize = runCatching {
                        File(ai.sourceDir).length() +
                            (ai.splitSourceDirs?.sumOf { File(it).length() } ?: 0L)
                    }.getOrDefault(0L)
                    InstalledApp(
                        pkg = ai.packageName,
                        name = ai.loadLabel(pm).toString(),
                        versionName = pkgInfo?.versionName ?: "?",
                        versionCode = pkgInfo?.longVersionCode ?: 0L,
                        isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        apkSizeBytes = apkSize
                    )
                }
                .sortedByDescending { it.apkSizeBytes }
        }
        loading = false
    }

    val filtered = remember(apps, showSystem) {
        if (showSystem) apps else apps.filterNot { it.isSystem }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                if (loading) stringResource(R.string.sys_ia_loading) else stringResource(R.string.sys_ia_apps_count, filtered.size),
                fontSize = 14.sp
            )
            Spacer(Modifier.weight(1f))
            val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()
            FilterChip(
                selected = showSystem,
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    showSystem = !showSystem
                },
                label = { Text(stringResource(R.string.sys_ia_show_system)) }
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.pkg }) { app ->
                StatCard(app.name) {
                    KeyValueRow(stringResource(R.string.sys_ia_package), app.pkg)
                    KeyValueRow(stringResource(R.string.sys_ia_version), "${app.versionName} (${app.versionCode})")
                    KeyValueRow(stringResource(R.string.sys_ia_apk_size), app.apkSizeBytes.formatBytes())
                    if (app.isSystem) {
                        Text(
                            stringResource(R.string.sys_ia_system_app),
                            color = OnSurfaceMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
