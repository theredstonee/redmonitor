package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LogLevel(val flag: String, val short: String) {
    VERBOSE("V", "V"), DEBUG("D", "D"), INFO("I", "I"),
    WARN("W", "W"), ERROR("E", "E"), FATAL("F", "F")
}

private enum class LogBuffer(val arg: String, val short: String) {
    ALL("all", "All"),
    MAIN("main", "Main"),
    SYSTEM("system", "System"),
    CRASH("crash", "Crash"),
    EVENTS("events", "Events")
}

@Composable
fun LogcatScreen() {
    val context = LocalContext.current
    val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var status by remember { mutableStateOf("Bereit. 'Aktualisieren' drücken oder 'Live ein'.") }
    var streaming by remember { mutableStateOf(false) }
    var filterText by remember { mutableStateOf("") }
    var minLevel by remember { mutableStateOf(LogLevel.INFO) }
    var buffer by remember { mutableStateOf(LogBuffer.ALL) }
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    suspend fun pull(tail: Int): Pair<List<String>, String> = withContext(Dispatchers.IO) {
        val res = ShizukuHelper.runCommand(
            context, "logcat", "-d", "-b", buffer.arg, "-v", "threadtime", "-t", tail.toString()
        )
        when {
            !res.ok -> emptyList<String>() to
                "logcat exit ${res.exitCode}: ${res.stderr.ifBlank { "stdout leer" }}"
            res.stdout.isBlank() -> emptyList<String>() to
                "Buffer '${buffer.short}' liefert nichts. Anderen Buffer probieren?"
            else -> {
                val parsed = res.stdout.lines().filter { it.isNotBlank() }
                parsed to "${parsed.size} Zeilen aus Buffer '${buffer.short}'."
            }
        }
    }

    fun fetchOnce() {
        scope.launch {
            status = "Lade Buffer '${buffer.short}'…"
            val (raw, msg) = pull(500)
            lines = raw
            status = msg
        }
    }

    fun startStream() {
        streaming = true
        scope.launch {
            while (streaming) {
                val (raw, msg) = pull(300)
                if (raw.isNotEmpty()) lines = raw
                status = if (streaming) "🟢 Live · $msg" else msg
                if (autoScroll && lines.isNotEmpty()) {
                    runCatching { listState.scrollToItem(lines.lastIndex) }
                }
                delay(1500)
            }
        }
    }

    val filtered = remember(lines, filterText, minLevel) {
        val minOrd = minLevel.ordinal
        lines.filter { line ->
            val level = extractLevel(line) ?: return@filter true
            val passLevel = (LogLevel.values().indexOfFirst { it.flag == level.toString() }) >= minOrd
            passLevel && (filterText.isBlank() || line.contains(filterText, true))
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        if (!shizukuReady) {
            com.tamerin.sysmonitor.ui.components.ShizukuCard(
                title = "Shizuku für Logcat",
                description = "Logcat liest System-Logs aller Apps. Android schützt das mit READ_LOGS — " +
                    "die haben nur System-Apps und der shell-User. Shizuku gibt uns shell-Zugriff."
            )
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    fetchOnce()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Aktualisieren", fontSize = 12.sp) }
            if (!streaming) {
                Button(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                        startStream()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Live ein", fontSize = 12.sp) }
            } else {
                OutlinedButton(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                        streaming = false
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Live aus", fontSize = 12.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LogBuffer.values().forEach { b ->
                FilterChip(
                    selected = buffer == b,
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                        buffer = b
                    },
                    label = { Text(b.short, fontSize = 11.sp) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = filterText,
            onValueChange = { filterText = it },
            label = { Text("Filter (Tag, PID, Text)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LogLevel.values().forEach { lv ->
                FilterChip(
                    selected = minLevel == lv,
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                        minLevel = lv
                    },
                    label = { Text(lv.short, fontSize = 11.sp) }
                )
            }
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = autoScroll,
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    autoScroll = !autoScroll
                },
                label = { Text("Auto-Scroll", fontSize = 11.sp) }
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${filtered.size} sichtbar · Min-Level ${minLevel.short} · $status",
            color = OnSurfaceMuted, fontSize = 11.sp
        )
        Spacer(Modifier.height(4.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .background(Color(0xFF050505))
                .padding(6.dp)
        ) {
            items(filtered.size, key = { it }) { idx ->
                val line = filtered[idx]
                val level = extractLevel(line)
                val color = when (level) {
                    'V' -> OnSurfaceMuted
                    'D' -> Color(0xFFB0B6C0)
                    'I' -> AccentSoft
                    'W' -> GaugeOrange
                    'E' -> GaugeRed
                    'F' -> GaugeRed
                    else -> OnSurfaceMuted
                }
                Text(
                    line,
                    color = color,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (level == 'E' || level == 'F') FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/** Logcat threadtime format: 06-15 12:34:56.789  1234  5678 I MyTag: message */
private fun extractLevel(line: String): Char? {
    val regex = Regex("""\s+\d+\s+\d+\s+([VDIWEF])\s""")
    return regex.find(line)?.groupValues?.get(1)?.firstOrNull()
}
