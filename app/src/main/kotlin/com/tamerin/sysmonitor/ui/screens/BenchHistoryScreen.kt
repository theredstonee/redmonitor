package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.benchmark.BenchmarkRepository
import com.tamerin.sysmonitor.cloud.BackendClient
import com.tamerin.sysmonitor.benchmark.db.BenchmarkRunWithSubs
import com.tamerin.sysmonitor.benchmark.db.BenchmarkType
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
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
            Spacer(Modifier.height(10.dp))
            BenchmarkComparison(run = run)
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

/**
 * Geraete-Vergleich zu einem Benchmark-Lauf.
 *
 * Zwei bewusst getrennte Dinge:
 *  - Die Percentile werden beim Aufklappen automatisch **gelesen**. Dabei geht nur
 *    SoC, Benchmark-Typ und der Score als Query raus, keine Geraete-ID, und der
 *    Server speichert davon nichts.
 *  - Das **Hochladen** passiert nur auf Knopfdruck und wird jedes Mal einzeln
 *    bestaetigt. Kein "nicht mehr fragen", keine gemerkte Einwilligung.
 */
@Composable
private fun BenchmarkComparison(run: BenchmarkRunWithSubs) {
    val context = LocalContext.current
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()
    val r = run.run

    var percentile by remember(r.id) {
        mutableStateOf<BackendClient.PercentileResponse?>(null)
    }
    var loading by remember(r.id) { mutableStateOf(true) }
    var showConfirm by remember(r.id) { mutableStateOf(false) }
    var uploadState by remember(r.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loading = true
        percentile = runCatching {
            BackendClient.fetchPercentile(context, r.type.name, r.totalScore)
        }.getOrNull()
        loading = false
    }

    LaunchedEffect(r.id) { refresh() }

    Text("Vergleich", color = AccentSoft, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))

    val p = percentile
    when {
        loading -> Text("Lade Vergleichswerte ...", color = OnSurfaceMuted, fontSize = 12.sp)
        p == null -> Text(
            "Vergleich gerade nicht erreichbar.",
            color = OnSurfaceMuted, fontSize = 12.sp
        )
        !p.enoughData -> Text(
            "Noch zu wenig Daten fuer dieses Geraet " +
                "(" + p.sampleCount + " von " + p.minSamplesHint() + " noetig).",
            color = OnSurfaceMuted, fontSize = 12.sp
        )
        else -> {
            Text(
                "Schneller als " + (p.percentile ?: 0) + " % der Geraete",
                color = GaugeGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp
            )
            KeyValueRow("Vergleichsgeraete", p.sampleCount.toString())
            p.averageScore?.let { KeyValueRow("Durchschnitt", "%,d".format(it)) }
            p.bestScore?.let { KeyValueRow("Bester Wert", "%,d".format(it)) }
        }
    }

    Spacer(Modifier.height(8.dp))
    Button(onClick = {
        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
        showConfirm = true
    }) { Text("Ergebnis beitragen") }

    uploadState?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, color = OnSurfaceMuted, fontSize = 12.sp)
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Ergebnis hochladen?") },
            text = {
                Text(
                    "Hochgeladen werden: Benchmark-Typ (" + r.type.displayName + "), " +
                        "Gesamt-Score, Single- und Multi-Score, alle " + run.subScores.size +
                        " Sub-Scores, dein Geraetemodell, der SoC, die Android-Version und " +
                        "die App-Version.\n\n" +
                        "Kein Name, kein Konto, keine Standortdaten. Die Zuordnung laeuft " +
                        "ueber eine anonyme Geraete-Kennung, mit der du deine Daten " +
                        "jederzeit im Privacy-Dashboard wieder loeschen kannst."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                    showConfirm = false
                    uploadState = "Lade hoch ..."
                    scope.launch {
                        val res = runCatching {
                            BackendClient.uploadBenchmark(
                                context = context,
                                benchType = r.type.name,
                                totalScore = r.totalScore,
                                singleScore = r.singleScore,
                                multiScore = r.multiScore,
                                subScores = run.subScores.map { sub ->
                                    BackendClient.SubScoreUpload(
                                        name = sub.name,
                                        singleScore = sub.singleScore,
                                        multiScore = sub.multiScore,
                                        unit = sub.unit
                                    )
                                }
                            )
                        }.getOrNull()
                        uploadState = if (res?.ok == true) {
                            refresh()
                            "Danke — Ergebnis beigetragen."
                        } else {
                            "Upload fehlgeschlagen. Spaeter nochmal versuchen."
                        }
                    }
                }) { Text("Hochladen") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Abbrechen") }
            }
        )
    }
}

/** Der Server nennt die noetige Mindestmenge nicht im Fehlerfall — hier gespiegelt. */
private fun BackendClient.PercentileResponse.minSamplesHint(): Int = 5
