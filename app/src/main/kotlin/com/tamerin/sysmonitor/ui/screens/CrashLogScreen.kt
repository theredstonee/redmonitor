package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.ui.components.ShizukuCard
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class CrashEntry(val source: String, val timestamp: String, val tag: String, val raw: String)

private enum class CrashSource(val label: String) { TOMBSTONES("Tombstones"), DROPBOX("Dropbox") }

@Composable
fun CrashLogScreen() {
    val context = LocalContext.current
    val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf(CrashSource.TOMBSTONES) }
    var entries by remember { mutableStateOf<List<CrashEntry>>(emptyList()) }
    var selected by remember { mutableStateOf<CrashEntry?>(null) }
    var filter by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    fun refresh() {
        loading = true
        selected = null
        scope.launch {
            entries = withContext(Dispatchers.IO) {
                if (source == CrashSource.TOMBSTONES) readTombstones(context)
                else readDropbox(context)
            }
            loading = false
        }
    }

    LaunchedEffect(source) { if (shizukuReady) refresh() }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        if (!shizukuReady) {
            ShizukuCard(
                title = "Shizuku für Crash-Logs",
                description = "/data/tombstones/ (Native-Crashes) und dumpsys dropbox (Java-Crashes/ANRs) " +
                    "sind nur als root oder shell zugänglich. Shizuku liefert den shell-Zugriff."
            )
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CrashSource.values().forEach { s ->
                FilterChip(
                    selected = source == s,
                    onClick = { source = s },
                    label = { Text(s.label, fontSize = 11.sp) }
                )
            }
            Spacer(Modifier.weight(1f))
            Text(if (loading) "lädt…" else "${entries.size} Einträge",
                color = OnSurfaceMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = filter, onValueChange = { filter = it },
            label = { Text("Filter (Paket, Tag)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))

        val sel = selected
        if (sel != null) {
            StatCard("${sel.source} · ${sel.tag}") {
                Text(sel.timestamp, color = OnSurfaceMuted, fontSize = 10.sp)
                Spacer(Modifier.height(6.dp))
                Box(modifier = Modifier.background(Color(0xFF050505)).padding(6.dp)) {
                    Text(sel.raw, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.verticalScroll(rememberScrollState()))
                }
                Spacer(Modifier.height(6.dp))
                Text("× Schließen", color = Accent, fontSize = 12.sp,
                    modifier = Modifier.clickable { selected = null })
            }
            return@Column
        }

        val filtered = remember(entries, filter) {
            if (filter.isBlank()) entries
            else entries.filter { it.tag.contains(filter, true) || it.raw.contains(filter, true) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(filtered, key = { it.timestamp + it.tag }) { e ->
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                    .background(Color(0x14FFFFFF)).clickable { selected = e }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    val color = if (e.tag.contains("ANR")) GaugeOrange else GaugeRed
                    Text(e.tag, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f))
                    Text(e.timestamp, color = OnSurfaceMuted, fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

private fun readTombstones(context: android.content.Context): List<CrashEntry> {
    val ls = ShizukuHelper.runShell(context, "ls -lt /data/tombstones/ 2>/dev/null | head -20")
    if (!ls.ok) return emptyList()
    val result = mutableListOf<CrashEntry>()
    for (line in ls.stdout.lines()) {
        val name = line.split(" ").lastOrNull()?.trim() ?: continue
        if (!name.startsWith("tombstone_") || name.endsWith(".pb")) continue
        val content = ShizukuHelper.runShell(context, "head -200 /data/tombstones/$name").stdout
        val ts = Regex("""Timestamp:\s*(.+)""").find(content)?.groupValues?.get(1)?.trim() ?: name
        val proc = Regex("""Cmdline:\s*(\S+)""").find(content)?.groupValues?.get(1) ?: "?"
        val sig = Regex("""signal\s+\d+\s+\((\w+)\)""").find(content)?.groupValues?.get(1) ?: "CRASH"
        result += CrashEntry("Tombstone", ts, "$sig · $proc", content)
    }
    return result
}

private fun readDropbox(context: android.content.Context): List<CrashEntry> {
    val out = ShizukuHelper.runShell(context,
        "dumpsys dropbox --print --max-events 30 2>/dev/null").stdout
    if (out.isBlank()) return emptyList()
    val entries = mutableListOf<CrashEntry>()
    // Format: blocks separated by '========='
    val blocks = out.split(Regex("=========+"))
    for (block in blocks) {
        if (block.isBlank()) continue
        val firstLine = block.lineSequence().firstOrNull { it.isNotBlank() } ?: continue
        // e.g. "2026-06-20 14:32:18 system_app_crash"
        val ts = Regex("""(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})""").find(firstLine)?.value ?: "?"
        val tag = firstLine.substringAfter(ts).trim()
        entries += CrashEntry("Dropbox", ts, tag, block.take(8000))
    }
    return entries.distinctBy { it.timestamp + it.tag }
}
