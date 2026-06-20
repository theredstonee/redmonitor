package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.data.WakeLockEntry
import com.tamerin.sysmonitor.data.WakelockReader
import com.tamerin.sysmonitor.data.WakelockResult
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.ShizukuCard
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentBubble
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WakelockScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
    var result by remember { mutableStateOf<WakelockResult?>(null) }
    var showSystem by remember { mutableStateOf(true) }
    var live by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() { result = WakelockReader.read(context) }

    LaunchedEffect(lifecycleOwner, live, shizukuReady) {
        if (!shizukuReady) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            refresh()
            while (live) {
                delay(3000)
                refresh()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        if (!shizukuReady) {
            ShizukuCard(
                title = "Shizuku für Wake-Lock-Viewer",
                description = "dumpsys power liefert alle aktiven Wake-Locks (Owner-UID, Package, Flags). " +
                    "Ohne Shizuku wäre dafür DUMP-Permission nötig — die hat nur das System."
            )
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = { scope.launch { refresh() } },
                modifier = Modifier.weight(1f)
            ) { Text("Aktualisieren", fontSize = 12.sp) }
            if (!live) {
                Button(
                    onClick = { live = true },
                    modifier = Modifier.weight(1f)
                ) { Text("Live ein (3s)", fontSize = 12.sp) }
            } else {
                OutlinedButton(
                    onClick = { live = false },
                    modifier = Modifier.weight(1f)
                ) { Text("Live aus", fontSize = 12.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = showSystem,
                onClick = { showSystem = !showSystem },
                label = { Text("System-UIDs", fontSize = 11.sp) }
            )
            Spacer(Modifier.weight(1f))
            Text(
                result?.message ?: "Lädt…",
                color = OnSurfaceMuted, fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(8.dp))

        val current = result
        if (current == null) {
            Text("dumpsys power läuft…", color = OnSurfaceMuted, fontSize = 12.sp)
            return@Column
        }
        if (!current.ok) {
            StatCard("Fehler") {
                Text(current.message, color = GaugeOrange, fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace)
            }
            return@Column
        }

        val filtered = remember(current.entries, showSystem) {
            if (showSystem) current.entries else current.entries.filterNot { it.isSystem }
        }
        if (filtered.isEmpty()) {
            StatCard("Keine Einträge") {
                Text(
                    if (current.entries.isEmpty()) "Aktuell hält keine App ein Wake-Lock — Gerät kann sauber schlafen."
                    else "Nur System-UIDs halten Wake-Locks. 'System-UIDs' aktivieren zum Anzeigen.",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
            }
            return@Column
        }

        val byType = remember(filtered) { filtered.groupingBy { it.type }.eachCount() }
        StatCard("Übersicht") {
            byType.entries.sortedByDescending { it.value }.forEach { (type, n) ->
                KeyValueRow(type, "$n")
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { "${it.uid}_${it.type}_${it.tag}" }) { entry ->
                WakelockRow(entry)
            }
        }
    }
}

@Composable
private fun WakelockRow(entry: WakeLockEntry) {
    val typeColor = when {
        entry.type.contains("FULL") -> GaugeOrange
        entry.type.contains("SCREEN") -> GaugeOrange
        entry.type.contains("PARTIAL") -> Accent
        else -> AccentSoft
    }
    StatCard(entry.displayName ?: entry.packageName ?: "uid:${entry.uid}") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentBubble)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    entry.type.removeSuffix("_WAKE_LOCK").removeSuffix("_LOCK"),
                    color = typeColor, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                "UID ${entry.uid}${if (entry.pid >= 0) " · PID ${entry.pid}" else ""}",
                color = OnSurfaceMuted, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "'${entry.tag}'",
            color = AccentSoft, fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        entry.packageName?.let {
            Text(it, color = OnSurfaceMuted, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace)
        }
        if (entry.flags.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                "Flags: ${entry.flags.joinToString(" ")}",
                color = OnSurfaceMuted, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        entry.workSource?.let {
            Text("ws: $it", color = OnSurfaceMuted, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace)
        }
    }
}
