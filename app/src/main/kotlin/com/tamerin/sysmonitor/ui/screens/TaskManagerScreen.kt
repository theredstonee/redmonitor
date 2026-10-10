package com.tamerin.sysmonitor.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.data.AppActions
import com.tamerin.sysmonitor.data.ProcessReadResult
import com.tamerin.sysmonitor.data.ProcessReader
import com.tamerin.sysmonitor.data.RunningApp
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.AccentBubble
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.DividerWhite
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import com.tamerin.sysmonitor.ui.theme.SurfaceDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SortMode(@StringRes val labelRes: Int) {
    RAM_DESC(R.string.misc_sortmode_ram_desc),
    NAME(R.string.misc_sortmode_name),
    PID(R.string.misc_sortmode_pid),
    USER_FIRST(R.string.misc_sortmode_user_first)
}

@Composable
fun TaskManagerScreen(onSelect: (String) -> Unit = {}) {
    val context = LocalContext.current
    var result by remember { mutableStateOf(ProcessReadResult(emptyList(), "lade…", 0, "")) }
    var loading by remember { mutableStateOf(true) }
    var showSystem by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.RAM_DESC) }
    val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
    val scope = rememberCoroutineScope()
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    LaunchedEffect(Unit) {
        while (true) {
            result = withContext(Dispatchers.IO) { ProcessReader.read(context) }
            loading = false
            delay(3000)
        }
    }

    val filtered = remember(result, showSystem, query, sortMode) {
        val base = result.apps.filter {
            (showSystem || !it.isSystem) &&
                (query.isBlank() || it.displayName.contains(query, true) || it.pkg.contains(query, true))
        }
        when (sortMode) {
            SortMode.RAM_DESC -> base.sortedByDescending { it.rssKb }
            SortMode.NAME -> base.sortedBy { it.displayName.lowercase() }
            SortMode.PID -> base.sortedBy { it.pid }
            SortMode.USER_FIRST -> base.sortedWith(compareBy({ it.isSystem }, { -it.rssKb }))
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (!shizukuReady) {
            com.tamerin.sysmonitor.ui.components.ShizukuCard(
                title = stringResource(R.string.sys_tm_shizuku_title),
                description = stringResource(R.string.sys_tm_shizuku_desc)
            )
            Spacer(Modifier.height(12.dp))
        }
        // Diagnostic card
        StatCard(stringResource(R.string.sys_tm_status)) {
            Text(result.source, color = OnSurfaceMuted, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.sys_tm_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = showSystem,
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    showSystem = !showSystem
                },
                label = { Text(stringResource(R.string.sys_tm_system), fontSize = 11.sp) }
            )
            SortMode.values().forEach { mode ->
                FilterChip(
                    selected = sortMode == mode,
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                        sortMode = mode
                    },
                    label = { Text(stringResource(mode.labelRes), fontSize = 11.sp) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (loading) stringResource(R.string.sys_tm_loading) else stringResource(R.string.sys_tm_apps_shown, filtered.size),
            color = OnSurfaceMuted, fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.pid.toString() + it.pkg }) { app ->
                AppRow(
                    app = app,
                    canStop = shizukuReady,
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                        onSelect(app.pkg)
                    },
                    onStop = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.DESTRUCTIVE)
                        scope.launch(Dispatchers.IO) {
                            AppActions.forceStop(context, app.pkg)
                        }
                    },
                    onLongPress = { AppActions.openAppInfo(context, app.pkg) }
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    app: RunningApp,
    canStop: Boolean,
    onClick: () -> Unit,
    onStop: () -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, DividerWhite),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentBubble),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    app.displayName.firstOrNull()?.uppercase() ?: "?",
                    color = AccentSoft,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.displayName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    "${app.pkg}  ·  PID ${app.pid}",
                    color = OnSurfaceMuted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                formatMem(app.rssKb),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            if (canStop) {
                IconButton(onClick = onStop) {
                    Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.sys_tm_forcestop_cd), tint = GaugeRed)
                }
            }
        }
    }
}

private fun formatMem(kb: Long): String = when {
    kb <= 0 -> "—"
    kb < 1024 -> "${kb} KB"
    kb < 1024L * 1024 -> "${"%.1f".format(kb / 1024f)} MB"
    else -> "${"%.2f".format(kb / 1024f / 1024f)} GB"
}
