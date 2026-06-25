package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.data.WakelockReader
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.ShizukuCard
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentBubble
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Suspect(
    val pkg: String,
    val displayName: String,
    val score: Int,
    val reasons: List<String>
)

@Composable
fun BatteryDoctorScreen() {
    val context = LocalContext.current
    val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
    var loading by remember { mutableStateOf(false) }
    var suspects by remember { mutableStateOf<List<Suspect>>(emptyList()) }
    var globalNotes by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun analyze() {
        loading = true
        scope.launch {
            val (sus, notes) = withContext(Dispatchers.IO) { runAnalysis(context) }
            suspects = sus
            globalNotes = notes
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        if (!shizukuReady) {
            ShizukuCard(
                title = "Shizuku für Akku-Doktor",
                description = "Korreliert Wake-Locks (dumpsys power) mit dumpsys batterystats — " +
                    "beides braucht shell-Privileg."
            )
            return@Column
        }

        StatCard("Was killt meinen Akku?") {
            Text(
                "Analysiert die letzten Stunden: wer hält Wake-Locks, wer wacht am häufigsten auf, " +
                    "wer hat die meisten Foreground-Service-Wechsel. Score hoch = verdächtig.",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { analyze() },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (loading) "Analysiere…" else "Analyse starten") }
        }
        Spacer(Modifier.height(8.dp))

        if (globalNotes.isNotEmpty()) {
            StatCard("System-Beobachtungen") {
                globalNotes.forEach { n ->
                    Text("• $n", color = OnSurfaceMuted, fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 2.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (suspects.isNotEmpty()) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(suspects, key = { it.pkg }) { s -> SuspectCard(s) }
            }
        } else if (!loading) {
            StatCard("Noch keine Analyse") {
                Text("Tap auf 'Analyse starten'. Dauert 1-3 s.",
                    color = OnSurfaceMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SuspectCard(s: Suspect) {
    val color = when {
        s.score >= 10 -> GaugeRed
        s.score >= 5 -> GaugeOrange
        else -> AccentSoft
    }
    StatCard(s.displayName) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentBubble)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("Score ${s.score}", color = color, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.width(8.dp))
            Text(s.pkg, color = OnSurfaceMuted, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(6.dp))
        s.reasons.forEach { r ->
            Text("• $r", color = OnSurfaceMuted, fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 1.dp))
        }
    }
}

private suspend fun runAnalysis(context: android.content.Context): Pair<List<Suspect>, List<String>> {
    val notes = mutableListOf<String>()
    val scoreByPkg = mutableMapOf<String, MutableList<String>>()

    // 1) Wake-Locks → +1 pro aktivem Lock pro App
    val wl = WakelockReader.read(context)
    wl.entries.filterNot { it.isSystem }.forEach { entry ->
        val pkg = entry.packageName ?: "uid:${entry.uid}"
        scoreByPkg.getOrPut(pkg) { mutableListOf() } +=
            "Hält Wake-Lock '${entry.tag}' (${entry.type.removeSuffix("_WAKE_LOCK")})"
    }

    // 2) dumpsys batterystats Per-App-Wakeups + Job-Scheduler-Stats
    val bs = ShizukuHelper.runShell(context,
        "dumpsys batterystats --charged 2>/dev/null | head -800")
    if (bs.ok) {
        val text = bs.stdout
        // Sektion "Wake lock acquired: ..."
        val wakeupRe = Regex("""^\s+\*?(\S+\.\S+)\s+.*Wakeup alarm.*?(\d+)\s*times?""",
            RegexOption.MULTILINE)
        wakeupRe.findAll(text).forEach { m ->
            val pkg = m.groupValues[1]
            val times = m.groupValues[2].toIntOrNull() ?: 0
            if (times > 0) {
                scoreByPkg.getOrPut(pkg) { mutableListOf() } +=
                    "$times× Wakeup-Alarme (dumpsys batterystats)"
            }
        }
        // Top-mobile-radio-active oder TopApp-Section grob
        if (text.contains("Mobile radio active")) {
            notes += "Mobilfunk-Modem war aktiv — Roaming oder schlechtes Signal?"
        }
        if (text.contains("Wifi scan")) {
            val scans = Regex("""Wifi scan: (\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
            if (scans != null && scans > 50) notes += "$scans WLAN-Scans — viele Apps suchen Netze"
        }
    } else {
        notes += "dumpsys batterystats Exit ${bs.exitCode}: ${bs.stderr.take(80)}"
    }

    // 3) FG-Service-Count via dumpsys activity services
    val fg = ShizukuHelper.runShell(context,
        "dumpsys activity services 2>/dev/null | grep -E 'isForeground=true|packageName=' | head -200")
    if (fg.ok) {
        var current: String? = null
        for (line in fg.stdout.lines()) {
            val pn = Regex("""packageName=(\S+)""").find(line)?.groupValues?.get(1)
            if (pn != null) current = pn
            if (line.contains("isForeground=true") && current != null && current != context.packageName) {
                scoreByPkg.getOrPut(current!!) { mutableListOf() } += "Hält Foreground-Service"
            }
        }
    }

    val pm = context.packageManager
    val suspects = scoreByPkg.entries.map { (pkg, reasons) ->
        val label = runCatching {
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        }.getOrDefault(pkg)
        Suspect(pkg = pkg, displayName = label, score = reasons.size, reasons = reasons.distinct())
    }.sortedByDescending { it.score }.take(20)

    if (suspects.isEmpty()) notes += "Keine verdächtigen User-Apps gefunden — System läuft sauber."
    return suspects to notes
}

