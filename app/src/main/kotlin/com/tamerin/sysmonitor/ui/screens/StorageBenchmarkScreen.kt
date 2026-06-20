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
import androidx.compose.ui.platform.LocalContext
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

private val DURATIONS = listOf(10 to "10 s", 15 to "15 s", 30 to "30 s", 60 to "1 min")

@Composable
fun StorageBenchmarkScreen() {
    val context = LocalContext.current
    var durationSec by remember { mutableIntStateOf(15) }
    val state by BenchmarkRepository.state.collectAsState()
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    val s = state
    if (s is BenchmarkState.Running && s.type == BenchmarkType.STORAGE_SEQUENTIAL ||
        s is BenchmarkState.Done && s.type == BenchmarkType.STORAGE_SEQUENTIAL ||
        s is BenchmarkState.Error && s.type == BenchmarkType.STORAGE_SEQUENTIAL
    ) {
        BenchRunScreen(onExit = { BenchmarkRepository.reset() })
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard("Storage Sequenziell — Sustained") {
            Text(
                "Schreibt und liest kontinuierlich. Sustained = echter Flash-Durchsatz (fsync alle 32 MB). " +
                    "Peak = SLC-Cache-Burst.",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Text("Dauer pro Phase (Write + Read)", color = OnSurfaceMuted, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DURATIONS.forEach { (sec, short) ->
                    FilterChip(
                        selected = durationSec == sec,
                        onClick = {
                            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                            durationSec = sec
                        },
                        label = { Text(short, fontSize = 11.sp) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Gesamt: ${durationSec * 2} s · 512 MB File-Cap",
                color = AccentSoft, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                        BenchmarkService.start(context, BenchmarkType.STORAGE_SEQUENTIAL, durationSec)
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
