package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.benchmark.BenchmarkRepository
import com.tamerin.sysmonitor.benchmark.BenchmarkService
import com.tamerin.sysmonitor.benchmark.BenchmarkState
import com.tamerin.sysmonitor.benchmark.db.BenchmarkType
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

private val DURATION_OPTIONS = listOf(3 to "3 s", 5 to "5 s", 10 to "10 s", 20 to "20 s")

@Composable
fun RamBenchmarkScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var perTestSeconds by remember { mutableIntStateOf(5) }
    val state by BenchmarkRepository.state.collectAsState()
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    val s = state
    if (s is BenchmarkState.Running && s.type == BenchmarkType.RAM ||
        s is BenchmarkState.Done && s.type == BenchmarkType.RAM ||
        s is BenchmarkState.Error && s.type == BenchmarkType.RAM
    ) {
        BenchRunScreen(onExit = { BenchmarkRepository.reset() })
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard("RAM-Bandbreite (tier-aware)") {
            Text(
                "Misst Lesen/Schreiben/Kopieren über 4 Puffer-Größen: 32 KB (L1), 1 MB (L2), 16 MB (L3/SLC) und 256 MB (DRAM). " +
                    "Läuft im Foreground-Service.",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Text("Dauer pro Operation × Tier", color = OnSurfaceMuted, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DURATION_OPTIONS.forEach { (sec, short) ->
                    FilterChip(
                        selected = perTestSeconds == sec,
                        onClick = {
                            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                            perTestSeconds = sec
                        },
                        label = { Text(short, fontSize = 11.sp) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Gesamt: 4 Tiers × 3 Ops × ${perTestSeconds}s = ${4 * 3 * perTestSeconds}s",
                color = AccentSoft, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                        BenchmarkService.start(context, BenchmarkType.RAM, perTestSeconds)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Test starten", fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                        context.startActivity(
                            Intent(context, com.tamerin.sysmonitor.ui.BenchHistoryStandaloneActivity::class.java)
                        )
                    }
                ) { Text("Verlauf") }
            }
        }
    }
}
