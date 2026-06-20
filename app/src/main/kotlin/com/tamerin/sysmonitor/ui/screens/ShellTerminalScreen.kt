package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class TerminalLine(
    val kind: Kind,
    val text: String
) {
    enum class Kind { PROMPT, STDOUT, STDERR, INFO, EXIT_OK, EXIT_FAIL }
}

private val QUICK_COMMANDS = listOf(
    "getprop ro.build.version.release",
    "getprop ro.product.manufacturer",
    "pm list packages -3",
    "dumpsys battery",
    "dumpsys power | head -40",
    "top -n 1 -b -m 10",
    "uptime",
    "df -h",
    "ip -br addr",
    "wm size",
    "settings list system"
)

@Composable
fun ShellTerminalScreen() {
    val context = LocalContext.current
    val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
    var input by remember { mutableStateOf("") }
    val lines = remember { mutableStateListOf<TerminalLine>() }
    val history = remember { mutableStateListOf<String>() }
    var historyIdx by remember { mutableStateOf(-1) }
    var running by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    fun run(cmd: String) {
        if (cmd.isBlank()) return
        history.add(0, cmd)
        historyIdx = -1
        lines += TerminalLine(TerminalLine.Kind.PROMPT, "$ $cmd")
        running = true
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                ShizukuHelper.runShell(context, cmd)
            }
            if (res.stdout.isNotBlank()) {
                res.stdout.lines().forEach { lines += TerminalLine(TerminalLine.Kind.STDOUT, it) }
            }
            if (res.stderr.isNotBlank()) {
                res.stderr.lines().forEach { lines += TerminalLine(TerminalLine.Kind.STDERR, it) }
            }
            lines += TerminalLine(
                if (res.ok) TerminalLine.Kind.EXIT_OK else TerminalLine.Kind.EXIT_FAIL,
                "[exit ${res.exitCode}]"
            )
            running = false
            runCatching { listState.scrollToItem(lines.size - 1) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        if (!shizukuReady) {
            ShizukuCard(
                title = "Shizuku für In-App-Shell",
                description = "Die Terminal-Console führt Kommandos als `shell`-User aus — " +
                    "gleiche Privilegien wie `adb shell`. Ohne Shizuku ginge das nur via Root."
            )
            return@Column
        }

        Text("Quick:", color = OnSurfaceMuted, fontSize = 10.sp)
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(QUICK_COMMANDS.size) { idx ->
                val cmd = QUICK_COMMANDS[idx]
                FilterChip(
                    selected = false,
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                        input = cmd
                    },
                    label = {
                        Text(cmd.take(28), fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace)
                    }
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Shell-Kommando (sh -c)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                    val cmd = input.trim()
                    input = ""
                    run(cmd)
                },
                enabled = !running && input.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text(if (running) "läuft…" else "Ausführen", fontSize = 12.sp) }
            OutlinedButton(
                onClick = {
                    if (history.isNotEmpty()) {
                        historyIdx = (historyIdx + 1).coerceAtMost(history.lastIndex)
                        input = history[historyIdx]
                    }
                },
                enabled = history.isNotEmpty()
            ) { Text("↑", fontFamily = FontFamily.Monospace) }
            OutlinedButton(
                onClick = {
                    if (historyIdx > 0) {
                        historyIdx -= 1
                        input = history[historyIdx]
                    } else {
                        historyIdx = -1
                        input = ""
                    }
                },
                enabled = historyIdx >= 0
            ) { Text("↓", fontFamily = FontFamily.Monospace) }
            OutlinedButton(
                onClick = { lines.clear() }
            ) { Text("Clear", fontSize = 11.sp) }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF050505))
                .padding(8.dp)
        ) {
            items(lines.size, key = { it }) { idx ->
                val line = lines[idx]
                val color = when (line.kind) {
                    TerminalLine.Kind.PROMPT -> AccentSoft
                    TerminalLine.Kind.STDOUT -> Color(0xFFE0E0E0)
                    TerminalLine.Kind.STDERR -> GaugeOrange
                    TerminalLine.Kind.INFO -> OnSurfaceMuted
                    TerminalLine.Kind.EXIT_OK -> Accent
                    TerminalLine.Kind.EXIT_FAIL -> GaugeRed
                }
                val weight = if (line.kind == TerminalLine.Kind.PROMPT)
                    FontWeight.Bold else FontWeight.Normal
                Text(
                    line.text,
                    color = color,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = weight
                )
            }
        }
    }
}

