package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.ui.components.ShizukuCard
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class FsEntry(val path: String, val name: String, val sizeBytes: Long, val isDir: Boolean)

@Composable
fun StorageAnalyzerScreen() {
    val context = LocalContext.current
    val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf("/sdcard") }
    var entries by remember { mutableStateOf<List<FsEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(listOf("/sdcard")) }

    fun scan(p: String) {
        loading = true
        path = p
        scope.launch {
            entries = withContext(Dispatchers.IO) { listDir(context, p) }
            loading = false
        }
    }

    LaunchedEffect(Unit) { scan("/sdcard") }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        if (!shizukuReady) {
            ShizukuCard(
                title = "Shizuku für Storage-Analyzer",
                description = "Tiefer Verzeichnis-Scan auf /sdcard und /data ist auf Android 11+ " +
                    "ohne MANAGE_EXTERNAL_STORAGE blockiert. Shizuku-Shell sieht alles."
            )
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = {
                    if (history.size > 1) {
                        val newHist = history.dropLast(1)
                        history = newHist
                        scan(newHist.last())
                    }
                },
                enabled = history.size > 1
            ) { Text("←") }
            OutlinedButton(onClick = { scan("/sdcard") }) { Text("/sdcard", fontSize = 11.sp) }
            OutlinedButton(onClick = { scan("/data/data") }) { Text("/data/data", fontSize = 11.sp) }
            OutlinedButton(onClick = { scan(path) }) { Text("↻", fontFamily = FontFamily.Monospace) }
        }
        Spacer(Modifier.height(6.dp))
        Text(path, color = AccentSoft, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(4.dp))
        Text(
            if (loading) "scannt (du -sh)…"
            else "${entries.size} Einträge · ges. ${formatBytes(entries.sumOf { it.sizeBytes })}",
            color = OnSurfaceMuted, fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))

        val maxBytes = entries.firstOrNull()?.sizeBytes?.coerceAtLeast(1) ?: 1L
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(entries, key = { it.path }) { e ->
                EntryRow(e, maxBytes) {
                    if (e.isDir) {
                        history = history + e.path
                        scan(e.path)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(e: FsEntry, maxBytes: Long, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x14FFFFFF))
            .clickable(enabled = e.isDir, onClick = onClick)
            .padding(8.dp)
    ) {
        // Hintergrund-Balken proportional zur Größe
        Box(
            modifier = Modifier
                .fillMaxWidth(e.sizeBytes.toFloat() / maxBytes)
                .fillMaxHeight()
                .background(Accent.copy(alpha = 0.18f))
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (e.isDir) "📁 " else "  ",
                fontSize = 13.sp
            )
            Text(
                e.name,
                color = if (e.isDir) AccentSoft else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = if (e.isDir) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            Text(
                formatBytes(e.sizeBytes),
                color = OnSurfaceMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun listDir(context: android.content.Context, path: String): List<FsEntry> {
    // du -sh listet jede Top-Level-Datei + Ordner mit Summe drunter
    val res = ShizukuHelper.runShell(context,
        "du -sb '$path'/* 2>/dev/null | sort -nr")
    if (!res.ok || res.stdout.isBlank()) return emptyList()
    return res.stdout.lines().mapNotNull { line ->
        val parts = line.trim().split(Regex("\\s+"), limit = 2)
        if (parts.size != 2) return@mapNotNull null
        val bytes = parts[0].toLongOrNull() ?: return@mapNotNull null
        val full = parts[1]
        val isDir = ShizukuHelper.runShell(context, "[ -d '$full' ] && echo Y").stdout.contains("Y")
        FsEntry(full, full.substringAfterLast('/'), bytes, isDir)
    }
}

private fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "%.1f KB".format(b / (1L shl 10).toDouble())
    else -> "$b B"
}
