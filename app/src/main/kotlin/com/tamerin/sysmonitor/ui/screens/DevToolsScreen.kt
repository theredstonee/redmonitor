package com.tamerin.sysmonitor.ui.screens

import android.os.Build
import android.os.Debug
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.settings.AppPrefs
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class ProcSource(val path: String, val label: String, val truncateLines: Int = 200)

private val PROC_SOURCES = listOf(
    ProcSource("/proc/version", "Kernel"),
    ProcSource("/proc/cpuinfo", "CPU-Info"),
    ProcSource("/proc/meminfo", "Memory"),
    ProcSource("/proc/stat", "Scheduler/Stat"),
    ProcSource("/proc/diskstats", "Disk-Stats"),
    ProcSource("/proc/mounts", "Mounts", truncateLines = 100),
    ProcSource("/proc/net/route", "Routing"),
    ProcSource("/proc/net/arp", "ARP-Tabelle"),
    ProcSource("/proc/net/tcp", "TCP-Sockets", truncateLines = 60),
    ProcSource("/proc/interrupts", "Interrupts", truncateLines = 80),
    ProcSource("/proc/loadavg", "Load-Average"),
    ProcSource("/proc/uptime", "Uptime")
)

@Composable
fun DevToolsScreen() {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()
    var selected by remember { mutableStateOf(PROC_SOURCES.first()) }
    var content by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready

    LaunchedEffect(selected, shizukuReady) {
        content = null
        error = null
        val (text, err) = withContext(Dispatchers.IO) {
            readProcSource(context, selected, shizukuReady)
        }
        content = text
        error = err
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("Dev-Tools — versteckter Bereich") {
            Text(
                "Du hast das Easter Egg gefunden. Hier siehst du rohe System-Dumps die normalerweise nur via ADB-Shell sichtbar sind. " +
                    "Manche Quellen brauchen Shizuku (geschützte /proc/-Bereiche, /proc/net/*).",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            KeyValueRow("Shizuku-Status", if (shizukuReady) "Bereit ✓" else "Nicht bereit")
            KeyValueRow("Snake-Highscore", AppPrefs.snakeHighScore(context).toString())
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.DESTRUCTIVE)
                    AppPrefs.setEasterEggUnlocked(context, false)
                    (context as? android.app.Activity)?.finish()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Easter Egg sperren (Re-Lock)") }
        }

        StatCard("App-Prozess-Speicher") {
            val memInfo = remember { Debug.MemoryInfo().also { Debug.getMemoryInfo(it) } }
            KeyValueRow("Total PSS", "${memInfo.totalPss} kB")
            KeyValueRow("Total Private Dirty", "${memInfo.totalPrivateDirty} kB")
            KeyValueRow("Total Shared Dirty", "${memInfo.totalSharedDirty} kB")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                memInfo.memoryStats.forEach { (k, v) ->
                    KeyValueRow(k, v)
                }
            }
            Spacer(Modifier.height(6.dp))
            KeyValueRow("Runtime Total", "${Runtime.getRuntime().totalMemory() / 1024} kB")
            KeyValueRow("Runtime Free", "${Runtime.getRuntime().freeMemory() / 1024} kB")
            KeyValueRow("Runtime Max", "${Runtime.getRuntime().maxMemory() / 1024} kB")
            KeyValueRow("Aktive Threads", Thread.activeCount().toString())
        }

        StatCard("Roh-Datenquelle") {
            Text("Wähle eine /proc-Datei:", color = OnSurfaceMuted, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            ProcSourceChips(selected) { src ->
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                selected = src
            }
            Spacer(Modifier.height(8.dp))
            Text(selected.path, color = AccentSoft, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 600.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF050505))
                .padding(8.dp)
        ) {
            when {
                error != null -> Text(
                    "Fehler: $error",
                    color = GaugeRed,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                content == null -> Text(
                    "Lese ${selected.path}…",
                    color = OnSurfaceMuted,
                    fontSize = 11.sp
                )
                content!!.isBlank() -> Text(
                    "(Datei leer oder kein Zugriff)",
                    color = OnSurfaceMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                else -> Text(
                    content!!,
                    color = Color(0xFFE5E7EB),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun ProcSourceChips(selected: ProcSource, onSelect: (ProcSource) -> Unit) {
    val rowState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rowState),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PROC_SOURCES.forEach { src ->
            FilterChip(
                selected = selected.path == src.path,
                onClick = { onSelect(src) },
                label = { Text(src.label, fontSize = 11.sp) }
            )
        }
    }
}

private suspend fun readProcSource(
    context: android.content.Context,
    src: ProcSource,
    shizukuReady: Boolean
): Pair<String?, String?> {
    return runCatching {
        // Try direct read first
        val direct = runCatching {
            File(src.path).useLines { lines ->
                lines.take(src.truncateLines).joinToString("\n")
            }
        }.getOrNull()
        if (!direct.isNullOrBlank()) {
            return@runCatching direct to null
        }
        // Fall back to Shizuku for protected paths
        if (shizukuReady) {
            val res = ShizukuHelper.runCommand(context, "cat", src.path)
            if (res.ok) {
                val truncated = res.stdout.lines().take(src.truncateLines).joinToString("\n")
                return@runCatching truncated to null
            }
            return@runCatching null to "Shizuku-Read fehlgeschlagen: exit ${res.exitCode}"
        }
        null to "Kein Direkt-Zugriff. Shizuku starten für ${src.path}."
    }.getOrElse { null to (it.message ?: "Unbekannter Fehler") }
}
