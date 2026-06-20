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

private val DURATIONS = listOf(5 to "5 s", 10 to "10 s", 20 to "20 s", 30 to "30 s")

@Composable
fun RandomIOScreen() {
    val context = LocalContext.current
    var durationSec by remember { mutableIntStateOf(10) }
    val state by BenchmarkRepository.state.collectAsState()
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    val s = state
    if (s is BenchmarkState.Running && s.type == BenchmarkType.STORAGE_RANDOM ||
        s is BenchmarkState.Done && s.type == BenchmarkType.STORAGE_RANDOM ||
        s is BenchmarkState.Error && s.type == BenchmarkType.STORAGE_RANDOM
    ) {
        BenchRunScreen(onExit = { BenchmarkRepository.reset() })
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard("Random I/O · 4K · QD 1/4/16") {
            Text(
                "Random-4-KB-Operationen bei drei Queue-Depths. QD=1 = wie SQLite-Scrolling, QD=16 = was UFS+f2fs max. pipelinen kann.",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Text("Dauer pro Phase (3 QDs × 2 r/w)", color = OnSurfaceMuted, fontSize = 11.sp)
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
                "Gesamt: ${durationSec * 6} s",
                color = AccentSoft, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                        BenchmarkService.start(context, BenchmarkType.STORAGE_RANDOM, durationSec)
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
