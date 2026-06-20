package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.benchmark.BenchmarkRepository
import com.tamerin.sysmonitor.benchmark.db.BenchmarkRunWithSubs
import com.tamerin.sysmonitor.benchmark.db.BenchmarkType
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BenchHistoryScreen() {
    val context = LocalContext.current
    var filter by remember { mutableStateOf<BenchmarkType?>(null) }
    val runs by BenchmarkRepository.history(context, filter)
        .collectAsState(initial = emptyList())
    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY) }
    var expandedId by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Verlauf",
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${runs.size} ${if (runs.size == 1) "Run" else "Runs"}",
            color = OnSurfaceMuted, fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = filter == null,
                onClick = { filter = null },
                label = { Text("Alle", fontSize = 11.sp) }
            )
            BenchmarkType.values().forEach { t ->
                FilterChip(
                    selected = filter == t,
                    onClick = { filter = t },
                    label = { Text(t.displayName, fontSize = 11.sp) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (runs.isEmpty()) {
            StatCard("Noch keine Runs") {
                Text(
                    "Starte einen Benchmark — die Ergebnisse landen hier mit Score, Sub-Scores und Zeitstempel.",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(runs, key = { it.run.id }) { run ->
                RunRow(
                    run = run,
                    expanded = expandedId == run.run.id,
                    onToggle = { expandedId = if (expandedId == run.run.id) null else run.run.id },
                    dateFmt = dateFmt
                )
            }
        }
    }
}

@Composable
private fun RunRow(
    run: BenchmarkRunWithSubs,
    expanded: Boolean,
    onToggle: () -> Unit,
    dateFmt: SimpleDateFormat
) {
    val r = run.run
    StatCard("${r.type.displayName}  ·  ${dateFmt.format(Date(r.timestamp))}") {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Score",
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
                Text(
                    "%,d".format(r.totalScore),
                    color = Accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (r.singleScore != null && r.multiScore != null) {
                    Text(
                        "Single ${r.singleScore} · Multi ${r.multiScore}",
                        color = AccentSoft, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Text(
                if (expanded) "▾" else "▸",
                color = AccentSoft,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            KeyValueRow("Gerät", r.deviceModel)
            KeyValueRow("App-Version", r.appVersion)
            KeyValueRow("Android SDK", r.androidSdk.toString())
            KeyValueRow("Phase-Sek.", r.phaseSeconds.toString())
            KeyValueRow("Dauer", "${r.durationMs / 1000} s")
            if (run.subScores.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Sub-Scores", color = AccentSoft,
                    fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                run.subScores.forEach { sub ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(sub.name, color = OnSurfaceMuted, fontSize = 12.sp)
                        Text(
                            "${sub.singleScore} / ${sub.multiScore}",
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
