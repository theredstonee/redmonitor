package com.tamerin.sysmonitor.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import com.tamerin.sysmonitor.R
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import com.tamerin.sysmonitor.cloud.BackendClient
import com.tamerin.sysmonitor.data.AppPermissionUsage
import com.tamerin.sysmonitor.data.OpAccess
import com.tamerin.sysmonitor.data.PermissionUsageReader
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.ui.components.ShizukuCard
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentBubble
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Was haben Apps zuletzt tatsächlich gemacht" - Privacy-Dashboard.
 * Daten aus `dumpsys appops` (via Shizuku), zeigt LETZTEN tatsächlichen
 * Zugriff je App+Op, nicht nur "Hat Permission erteilt".
 */
@Composable
fun PrivacyDashboardScreen() {
    val context = LocalContext.current
    val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppPermissionUsage>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var showSystem by remember { mutableStateOf(false) }
    var filterText by remember { mutableStateOf("") }
    var maxAgeFilter by remember { mutableStateOf(AgeFilter.ALL) }
    var opFilter by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        loading = true
        scope.launch {
            apps = withContext(Dispatchers.IO) { PermissionUsageReader.read(context) }
            loading = false
        }
    }

    LaunchedEffect(shizukuReady) { if (shizukuReady) refresh() }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Bewusst VOR dem Shizuku-Gate: das Loeschen der eigenen Cloud-Daten darf
        // nicht daran haengen, ob Shizuku laeuft.
        CloudDataCard()
        Spacer(Modifier.height(12.dp))

        if (!shizukuReady) {
            ShizukuCard(
                title = stringResource(R.string.sys_pd_shizuku_title),
                description = stringResource(R.string.sys_pd_shizuku_desc)
            )
            return@Column
        }

        StatCard(stringResource(R.string.sys_pd_who_used)) {
            Text(
                stringResource(R.string.sys_pd_desc),
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { refresh() }, enabled = !loading,
                    modifier = Modifier.weight(1f)) {
                    Text(if (loading) stringResource(R.string.sys_pd_loading) else stringResource(R.string.sys_pd_refresh), fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(value = filterText, onValueChange = { filterText = it },
            label = { Text(stringResource(R.string.sys_pd_filter)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AgeFilter.values().forEach { a ->
                FilterChip(selected = maxAgeFilter == a, onClick = { maxAgeFilter = a },
                    label = { Text(stringResource(a.labelRes), fontSize = 10.sp) })
            }
            FilterChip(selected = showSystem, onClick = { showSystem = !showSystem },
                label = { Text(stringResource(R.string.sys_pd_system), fontSize = 10.sp) })
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val knownOps = listOf("CAMERA", "RECORD_AUDIO", "FINE_LOCATION",
                "READ_CONTACTS", "READ_SMS", "READ_PHONE_STATE", "BODY_SENSORS")
            FilterChip(selected = opFilter == null, onClick = { opFilter = null },
                label = { Text(stringResource(R.string.sys_pd_all_ops), fontSize = 10.sp) })
            knownOps.forEach { op ->
                FilterChip(selected = opFilter == op, onClick = {
                    opFilter = if (opFilter == op) null else op
                }, label = {
                    Text(op.take(8), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                })
            }
        }
        Spacer(Modifier.height(8.dp))

        val filtered = remember(apps, showSystem, filterText, maxAgeFilter, opFilter) {
            val q = filterText.trim().lowercase()
            apps.asSequence()
                .filter { showSystem || !it.isSystem }
                .filter { app ->
                    q.isEmpty() ||
                        app.displayName.lowercase().contains(q) ||
                        app.packageName.lowercase().contains(q) ||
                        app.ops.any { it.op.lowercase().contains(q) }
                }
                .filter { app ->
                    opFilter == null || app.ops.any { it.op == opFilter }
                }
                .filter { app ->
                    val keep = if (maxAgeFilter == AgeFilter.ALL) true else {
                        app.ops.any { o ->
                            o.lastAccessAgoMs in 1..maxAgeFilter.maxMs
                        }
                    }
                    keep
                }
                .toList()
        }

        Text(stringResource(R.string.sys_pd_apps_total, filtered.size, apps.size),
            color = OnSurfaceMuted, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.packageName }) { app ->
                UsageCard(app, opFilter)
            }
        }
    }
}

private enum class AgeFilter(@StringRes val labelRes: Int, val maxMs: Long) {
    ALL(R.string.misc_agefilter_all, Long.MAX_VALUE),
    H1(R.string.misc_agefilter_1h, 3_600_000L),
    H24(R.string.misc_agefilter_24h, 86_400_000L),
    D7(R.string.misc_agefilter_7d, 7L * 86_400_000L),
    D30(R.string.misc_agefilter_30d, 30L * 86_400_000L)
}

@Composable
private fun UsageCard(app: AppPermissionUsage, opFilter: String?) {
    val recent = app.ops.filter { it.lastAccessAgoMs in 1..3_600_000L }
    StatCard(app.displayName) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(app.packageName, color = OnSurfaceMuted, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace)
                Text("UID ${app.uid}", color = OnSurfaceMuted, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace)
            }
            if (recent.isNotEmpty()) {
                Box(modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(GaugeRed.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("LIVE (${recent.size})", color = GaugeRed, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val shown = if (opFilter != null) app.ops.filter { it.op == opFilter } else app.ops
        shown.forEach { o -> OpRow(o) }
    }
}

@Composable
private fun OpRow(o: OpAccess) {
    val context = LocalContext.current
    val color = when {
        o.lastAccessAgoMs == 0L -> OnSurfaceMuted
        o.lastAccessAgoMs <= 60_000L -> GaugeRed       // < 1m
        o.lastAccessAgoMs <= 3_600_000L -> GaugeOrange // < 1h
        o.lastAccessAgoMs <= 86_400_000L -> AccentSoft // < 24h
        else -> OnSurfaceMuted
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(AccentBubble)
            .padding(horizontal = 5.dp, vertical = 1.dp)) {
            Text(o.op, color = AccentSoft, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(6.dp))
        Text("mode=${o.mode}", color = if (o.mode == "allow") GaugeGreen
            else if (o.mode == "foreground") AccentSoft else OnSurfaceMuted,
            fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.weight(1f))
        Text(PermissionUsageReader.formatAgo(context, o.lastAccessAgoMs),
            color = color, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Selbstbedienung fuer DSGVO Art. 17: loescht alle freiwillig hochgeladenen
 * Benchmark-Scores dieses Geraets vom Server.
 */
@Composable
private fun CloudDataCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()
    var showConfirm by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    StatCard(stringResource(R.string.sys_pd_cloud_title)) {
        Text(
            stringResource(R.string.sys_pd_cloud_desc),
            color = OnSurfaceMuted, fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = {
            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
            showConfirm = true
        }) { Text(stringResource(R.string.sys_pd_cloud_delete_btn)) }
        status?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = OnSurfaceMuted, fontSize = 12.sp)
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.sys_pd_cloud_confirm_title)) },
            text = {
                Text(
                    stringResource(R.string.sys_pd_cloud_confirm_body)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.DESTRUCTIVE)
                    showConfirm = false
                    status = context.getString(R.string.sys_pd_cloud_deleting)
                    scope.launch {
                        val res = runCatching {
                            BackendClient.deleteBenchmarks(context)
                        }.getOrNull()
                        status = if (res?.ok == true) {
                            context.getString(R.string.sys_pd_cloud_deleted)
                        } else {
                            context.getString(R.string.sys_pd_cloud_delete_fail)
                        }
                    }
                }) { Text(stringResource(R.string.sys_pd_cloud_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.sys_pd_cancel)) }
            }
        )
    }
}
