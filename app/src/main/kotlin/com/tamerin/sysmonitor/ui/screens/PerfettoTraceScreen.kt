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
        // Erst /data/local/tmp/ (shell-owned, immer beschreibbar via Shizuku),
        // dann nach /sdcard/Download/ mit korrekten Permissions/Owner kopieren,
        // sonst sieht der User-Filemanager die Datei nicht (Android FUSE-Mount-
        // Ownership-Quirks bei Files die von shell-User direkt nach /sdcard/ landen).
        val ts = System.currentTimeMillis() / 1000
        val name = "redmonitor-trace-${ts}.pftrace"
        val tmp = "/data/local/tmp/$name"
        val out = "/sdcard/Download/$name"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val cmd = "atrace -z -t $durationSec ${preset.atrace} -o '$tmp' && " +
                    "mkdir -p /sdcard/Download && " +
                    "cp '$tmp' '$out' && " +
                    "chmod 666 '$out' && " +
                    "chown media_rw:media_rw '$out' 2>/dev/null; " +
                    "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE " +
                    "-d 'file://$out' >/dev/null 2>&1; " +
                    "rm '$tmp'; echo done"
                ShizukuHelper.runShell(context, cmd)
            }
            lastOutput = if (result.ok) "OK · gespeichert unter $out" else
                "FAIL · exit ${result.exitCode}\n${result.stderr.take(200)}"
            lastFile = if (result.ok) out else null
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
