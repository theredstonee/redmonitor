package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.battery.BatteryHistoryDatabase
import com.tamerin.sysmonitor.data.battery.BatterySample
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Range(val label: String, val ms: Long) {
    H6("6 h", 6L * 3600_000L),
    H24("24 h", 24L * 3600_000L),
    D3("3 Tg", 3L * 24 * 3600_000L),
    D7("7 Tg", 7L * 24 * 3600_000L)
}

@Composable
fun DischargeCurveScreen() {
    val context = LocalContext.current
    var range by remember { mutableStateOf(Range.H24) }
    var samples by remember { mutableStateOf<List<BatterySample>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(range) {
        loading = true
        samples = withContext(Dispatchers.IO) {
            val dao = BatteryHistoryDatabase.get(context).sampleDao()
            dao.samplesSince(System.currentTimeMillis() - range.ms)
        }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Range.values().forEach { r ->
                FilterChip(
                    selected = range == r,
                    onClick = { range = r },
                    label = { Text(r.label, fontSize = 11.sp) }
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (loading) "lädt…" else "${samples.size} Samples",
                color = OnSurfaceMuted, fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(8.dp))

        if (samples.size < 2) {
            StatCard("Noch keine Daten") {
                Text(
                    "Im gewählten Zeitraum gibt es weniger als 2 Samples. Der BatterySamplerWorker " +
                        "läuft alle 15 min und sammelt mit der Zeit. Lass die App ein paar Stunden offen " +
                        "und schau dann nochmal rein.",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
            }
            return@Column
        }

        ChartCard(samples, range)
        Spacer(Modifier.height(8.dp))
        StatsCard(samples)
    }
}

@Composable
private fun ChartCard(samples: List<BatterySample>, range: Range) {
    StatCard("Verlauf · ${range.label}") {
        val t0 = samples.first().timestampMs
        val t1 = samples.last().timestampMs
        val durMs = (t1 - t0).coerceAtLeast(1L)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Color(0x14FFFFFF))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                val padL = 28f; val padB = 18f; val padT = 8f; val padR = 8f
                val plotW = w - padL - padR
                val plotH = h - padT - padB

                // Horizontal grid 0/25/50/75/100
                for (p in listOf(0, 25, 50, 75, 100)) {
                    val y = padT + plotH * (1f - p / 100f)
                    drawLine(
                        color = Color(0x22FFFFFF),
                        start = Offset(padL, y),
                        end = Offset(w - padR, y),
                        strokeWidth = 1f
                    )
                }

                // Connect samples segment by segment, color by charging state
                fun xFor(t: Long) = padL + plotW * ((t - t0).toFloat() / durMs.toFloat())
                fun yFor(p: Float) = padT + plotH * (1f - (p.coerceIn(0f, 100f) / 100f))

                for (i in 1 until samples.size) {
                    val a = samples[i - 1]
                    val b = samples[i]
                    val segColor = when {
                        a.charging && b.charging -> GaugeGreen
                        b.percent < 15f -> GaugeRed
                        b.percent < 30f -> GaugeOrange
                        else -> AccentSoft
                    }
                    drawLine(
                        color = segColor,
                        start = Offset(xFor(a.timestampMs), yFor(a.percent)),
                        end = Offset(xFor(b.timestampMs), yFor(b.percent)),
                        strokeWidth = 3f
                    )
                }
                // Top frame
                drawLine(
                    color = Color(0x44FFFFFF),
                    start = Offset(padL, padT),
                    end = Offset(padL, padT + plotH),
                    strokeWidth = 1f
                )
                drawLine(
                    color = Color(0x44FFFFFF),
                    start = Offset(padL, padT + plotH),
                    end = Offset(w - padR, padT + plotH),
                    strokeWidth = 1f
                )
            }
            // Y-axis labels (overlayed via Compose Text in Box corners)
            Column(modifier = Modifier.fillMaxHeight().padding(start = 2.dp, top = 4.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.SpaceBetween) {
                listOf("100", "75", "50", "25", "0").forEach {
                    Text(it, color = OnSurfaceMuted, fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace)
                }
            }
            // X-axis time labels (left, middle, right)
            Row(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .padding(start = 30.dp, end = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                val fmt = SimpleDateFormat(
                    if (range == Range.H6 || range == Range.H24) "HH:mm" else "dd.MM HH:mm",
                    Locale.getDefault()
                )
                Text(fmt.format(Date(t0)), color = OnSurfaceMuted, fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace)
                Text(fmt.format(Date(t0 + (t1 - t0) / 2)), color = OnSurfaceMuted, fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace)
                Text(fmt.format(Date(t1)), color = OnSurfaceMuted, fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Legend(GaugeGreen, "lädt")
            Legend(AccentSoft, "normal")
            Legend(GaugeOrange, "< 30 %")
            Legend(GaugeRed, "< 15 %")
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color))
        Text(label, color = OnSurfaceMuted, fontSize = 10.sp)
    }
}

@Composable
private fun StatsCard(samples: List<BatterySample>) {
    val pctStart = samples.first().percent
    val pctEnd = samples.last().percent
    val maxPct = samples.maxOf { it.percent }
    val minPct = samples.minOf { it.percent }
    val durMin = (samples.last().timestampMs - samples.first().timestampMs) / 60_000L

    // Net change excluding charging segments
    var netDrop = 0f
    var dischargeMin = 0L
    for (i in 1 until samples.size) {
        val a = samples[i - 1]; val b = samples[i]
        if (!a.charging && !b.charging) {
            val drop = a.percent - b.percent
            if (drop > 0) netDrop += drop
            dischargeMin += (b.timestampMs - a.timestampMs) / 60_000L
        }
    }
    val ratePctPerH = if (dischargeMin > 0) netDrop / (dischargeMin / 60f) else 0f

    StatCard("Statistik im Zeitraum") {
        KeyValueRow("Start → Ende", "${pctStart.toInt()} % → ${pctEnd.toInt()} %")
        KeyValueRow("Spanne", "min ${minPct.toInt()} % · max ${maxPct.toInt()} %")
        KeyValueRow("Beobachtungszeit", "${durMin / 60} h ${durMin % 60} min")
        KeyValueRow("Davon Entlade-Zeit", "${dischargeMin / 60} h ${dischargeMin % 60} min")
        KeyValueRow("Drain-Rate (netto)", "%.2f %% / h".format(ratePctPerH))
        if (ratePctPerH > 0) {
            val toEmptyH = 100f / ratePctPerH
            KeyValueRow("Geschätzte Volllaufzeit (100 %)", "%.1f h".format(toEmptyH))
        }
    }
}
