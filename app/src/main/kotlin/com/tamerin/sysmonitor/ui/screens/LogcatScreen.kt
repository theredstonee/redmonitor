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
    EVENTS("events", "Events"),
    RADIO("radio", "Radio")
}

private enum class Depth(val tail: Int?, val short: String) {
    K1(1000, "1k"),
    K5(5000, "5k"),
    K20(20000, "20k"),
    ALL(null, "Alles")
}

@Composable
fun LogcatScreen() {
    val context = LocalContext.current
    val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var status by remember { mutableStateOf("Bereit. 'Aktualisieren' drücken oder 'Live ein'.") }
    var streaming by remember { mutableStateOf(false) }
    var filterText by remember { mutableStateOf("") }
    var minLevel by remember { mutableStateOf(LogLevel.VERBOSE) }
    var buffer by remember { mutableStateOf(LogBuffer.ALL) }
    var depth by remember { mutableStateOf(Depth.K5) }
    var rawMode by remember { mutableStateOf(false) }
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    suspend fun runLogcat(bufArg: String, tail: Int?): ShizukuHelper.CmdResult {
        val args = mutableListOf("logcat", "-d", "-b", bufArg, "-v", "threadtime")
        if (tail != null) {
            args += "-t"
            args += tail.toString()
        }
        return ShizukuHelper.runCommand(context, *args.toTypedArray())
    }

    /**
     * Multi-buffer fallback: if the single -b all call returns nothing
     * (some MIUI/HyperOS builds restrict the 'all' alias for the shell user),
     * query each known buffer individually and merge.
     */
    suspend fun pull(): Pair<List<String>, String> = withContext(Dispatchers.IO) {
        val tail = depth.tail
        if (buffer == LogBuffer.ALL) {
            val primary = runLogcat("all", tail)
            if (primary.ok && primary.stdout.isNotBlank()) {
                val parsed = primary.stdout.lines().filter { it.isNotBlank() }
                return@withContext parsed to "${parsed.size} Zeilen aus allen Buffern (Tiefe ${depth.short})."
            }
            // Fallback: merge per-buffer
            val merged = mutableListOf<String>()
            val errs = mutableListOf<String>()
            for (b in listOf("main", "system", "crash", "events", "radio")) {
                val r = runLogcat(b, tail)
                when {
                    !r.ok -> errs += "$b: Exit ${r.exitCode}"
                    r.stdout.isBlank() -> Unit
                    else -> merged += r.stdout.lines().filter { it.isNotBlank() }
                }
            }
            val sorted = merged.sortedWith(LogcatComparator)
            val msg = if (sorted.isEmpty()) {
                "Alle Buffer leer. ${errs.joinToString(" / ")}"
            } else {
                "${sorted.size} Zeilen (merge: main+system+crash+events+radio · Tiefe ${depth.short})."
            }
            sorted to msg
        } else {
            val res = runLogcat(buffer.arg, tail)
            when {
                !res.ok -> emptyList<String>() to
                    "logcat exit ${res.exitCode}: ${res.stderr.ifBlank { "stdout leer" }}"
                res.stdout.isBlank() -> emptyList<String>() to
                    "Buffer '${buffer.short}' liefert nichts. Anderen Buffer probieren?"
                else -> {
                    val parsed = res.stdout.lines().filter { it.isNotBlank() }
                    parsed to "${parsed.size} Zeilen aus Buffer '${buffer.short}' (Tiefe ${depth.short})."
                }
            }
        }
    }

    fun fetchOnce() {
        scope.launch {
            status = "Lade Buffer '${buffer.short}' (Tiefe ${depth.short})..."
            val (raw, msg) = pull()
            lines = raw
            status = msg
            if (autoScroll && raw.isNotEmpty()) {
                runCatching { listState.scrollToItem(raw.lastIndex) }
            }
        }
    }

    fun startStream() {
        streaming = true
        scope.launch {
            while (streaming) {
                val (raw, msg) = pull()
                if (raw.isNotEmpty()) lines = raw
                status = if (streaming) "LIVE · $msg" else msg
                if (autoScroll && lines.isNotEmpty()) {
                    runCatching { listState.scrollToItem(lines.lastIndex) }
                }
                delay(1500)
            }
        }
    }

    // Filtering ~20k lines through regex + contains() in remember{} on the main
    // thread caused visible jank. Push it to Default dispatcher and publish.
    var filtered by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(lines, filterText, minLevel, rawMode) {
        filtered = withContext(Dispatchers.Default) {
            if (rawMode && filterText.isBlank()) return@withContext lines
            val minOrd = minLevel.ordinal
            lines.filter { line ->
                if (filterText.isNotBlank() && !line.contains(filterText, true)) return@filter false
                if (rawMode) return@filter true
                val level = extractLevel(line) ?: return@filter true
                val idx = LogLevel.values().indexOfFirst { it.flag == level.toString() }
                idx < 0 || idx >= minOrd
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        if (!shizukuReady) {
            com.tamerin.sysmonitor.ui.components.ShizukuCard(
                title = "Shizuku für Logcat",
                description = "Logcat liest System-Logs aller Apps. Android schützt das mit READ_LOGS - " +
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

        Text("Buffer", color = OnSurfaceMuted, fontSize = 10.sp)
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
        Spacer(Modifier.height(4.dp))

        Text("Tiefe", color = OnSurfaceMuted, fontSize = 10.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Depth.values().forEach { d ->
                FilterChip(
                    selected = depth == d,
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                        depth = d
                    },
                    label = { Text(d.short, fontSize = 11.sp) }
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
                    selected = minLevel == lv && !rawMode,
                    enabled = !rawMode,
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                        minLevel = lv
                    },
                    label = { Text(lv.short, fontSize = 11.sp) }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = rawMode,
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    rawMode = !rawMode
                },
                label = { Text("Raw (alles)", fontSize = 11.sp) }
            )
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
            buildString {
                append("${filtered.size} sichtbar / ${lines.size} geladen · ")
                append(if (rawMode) "Raw" else "Min ${minLevel.short}")
                append(" · ")
                append(status)
            },
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

/**
 * Tolerant level extractor. Matches:
 *  - threadtime: `06-15 12:34:56.789  1234  5678 I MyTag: message`
 *  - long/brief: `I/MyTag(1234): message` or `I(1234): message`
 *  - bare events/binary lines: returns null (caller decides what to do)
 */
private fun extractLevel(line: String): Char? {
    // threadtime: two integers followed by single level letter
    val threadtime = Regex("""\s+\d+\s+\d+\s+([VDIWEF])\s""")
    threadtime.find(line)?.groupValues?.get(1)?.firstOrNull()?.let { return it }
    // brief: starts with `X/...` where X is a level letter
    if (line.length >= 2 && line[1] == '/' && line[0] in "VDIWEF") return line[0]
    return null
}

/**
 * Sort merged logs by their leading timestamp (MM-DD hh:mm:ss.mmm).
 * Lines without a parseable timestamp are kept in original relative order
 * (Kotlin's sortWith is stable).
 */
private val LogcatComparator = Comparator<String> { a, b ->
    val ta = extractTimestamp(a)
    val tb = extractTimestamp(b)
    when {
        ta == null && tb == null -> 0
        ta == null -> 1
        tb == null -> -1
        else -> ta.compareTo(tb)
    }
}

private fun extractTimestamp(line: String): String? {
    if (line.length < 18) return null
    val candidate = line.substring(0, 18)
    if (candidate[2] == '-' && candidate[5] == ' ' &&
        candidate[8] == ':' && candidate[11] == ':' && candidate[14] == '.') {
        return candidate
    }
    return null
}
