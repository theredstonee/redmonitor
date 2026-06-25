package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.ShizukuCard
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Preset(val label: String, val atrace: String, val ftrace: String) {
    CPU("CPU + Scheduler", "sched freq idle", "sched_switch,sched_wakeup"),
    GFX("GFX + Frames", "gfx view sched", "sched_switch"),
    MEM("Memory + GC", "memory dalvik sched", "kmem,oom"),
    BOOT("Boot/Startup", "am view wm gfx sched", "sched_switch,binder_transaction")
}

@Composable
fun PerfettoTraceScreen() {
    val context = LocalContext.current
    val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
    val scope = rememberCoroutineScope()
    var preset by remember { mutableStateOf(Preset.CPU) }
    var durationSec by remember { mutableIntStateOf(10) }
    var running by remember { mutableStateOf(false) }
    var lastOutput by remember { mutableStateOf("") }
    var lastFile by remember { mutableStateOf<String?>(null) }

    fun startTrace() {
        running = true
        val outFile = "/sdcard/Download/perfetto-${System.currentTimeMillis() / 1000}.pftrace"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                // atrace ist auf praktisch jedem Gerät verfügbar; perfetto config wäre
                // ein Texterio-Block — atrace -t Nsec direkt ist einfacher und robust.
                val cmd = "atrace -z -t $durationSec ${preset.atrace} -o $outFile"
                ShizukuHelper.runShell(context, cmd)
            }
            lastOutput = if (result.ok) "OK · exit ${result.exitCode}" else
                "FAIL · exit ${result.exitCode}\n${result.stderr.take(200)}"
            lastFile = if (result.ok) outFile else null
            running = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!shizukuReady) {
            ShizukuCard(
                title = "Shizuku für System-Trace",
                description = "atrace / perfetto sind shell-only Tools. Output ein .pftrace-File " +
                    "das du in ui.perfetto.dev hochladen kannst für die volle Analyse."
            )
            return@Column
        }

        StatCard("Trace-Preset") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Preset.values().forEach { p ->
                    FilterChip(
                        selected = preset == p,
                        onClick = { preset = p },
                        label = { Text(p.label, fontSize = 10.sp) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Categories: ${preset.atrace}", color = OnSurfaceMuted,
                fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }

        StatCard("Dauer") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 10, 30, 60).forEach { sec ->
                    FilterChip(
                        selected = durationSec == sec,
                        onClick = { durationSec = sec },
                        label = { Text("${sec}s", fontSize = 11.sp) }
                    )
                }
            }
        }

        Button(
            onClick = { startTrace() },
            enabled = !running,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (running) "Tracing … ${durationSec}s" else "Trace starten",
                fontFamily = FontFamily.Monospace)
        }

        if (lastOutput.isNotBlank()) {
            StatCard("Letzter Run") {
                Text(lastOutput,
                    color = if (lastOutput.startsWith("OK")) GaugeGreen else GaugeRed,
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                lastFile?.let {
                    Spacer(Modifier.height(6.dp))
                    KeyValueRow("Datei", it.substringAfterLast('/'))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "→ Datei auf PC ziehen und in ui.perfetto.dev öffnen für UI-Analyse " +
                            "(Threads, CPU-Idle, Wakeups, Locks, Frame-Timing).",
                        color = OnSurfaceMuted, fontSize = 11.sp
                    )
                }
            }
        }
    }
}
