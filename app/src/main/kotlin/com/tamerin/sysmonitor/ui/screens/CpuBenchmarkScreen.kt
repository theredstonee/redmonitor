package com.tamerin.sysmonitor.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.tamerin.sysmonitor.benchmark.BenchmarkDuration
import com.tamerin.sysmonitor.benchmark.BenchmarkRepository
import com.tamerin.sysmonitor.benchmark.BenchmarkService
import com.tamerin.sysmonitor.benchmark.BenchmarkState
import com.tamerin.sysmonitor.benchmark.db.BenchmarkType
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

@Composable
fun CpuBenchmarkScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var duration by remember { mutableStateOf(BenchmarkDuration.QUICK) }
    val state by BenchmarkRepository.state.collectAsState()
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    // While a CPU run is in flight or just finished, hand the screen over to
    // the fullscreen AnTuTu-style runner. State persists across rotation and
    // Activity recreation because it lives in the singleton repository.
    val s = state
    if (s is BenchmarkState.Running && s.type == BenchmarkType.CPU) {
        BenchRunScreen(onExit = { /* keep running */ })
        return
    }
    if (s is BenchmarkState.Done && s.type == BenchmarkType.CPU) {
        BenchRunScreen(onExit = { BenchmarkRepository.reset() })
        return
    }
    if (s is BenchmarkState.Error && s.type == BenchmarkType.CPU) {
        BenchRunScreen(onExit = { BenchmarkRepository.reset() })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard("CPU-Benchmark") {
            Text(
                "4 Sub-Tests à la Geekbench: Integer-Mathe, Floating-Point, SHA-256-Crypto, Quicksort. " +
                    "Single + Multi-Core. Läuft im Foreground-Service — Activity darf in den Hintergrund.",
                color = OnSurfaceMuted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Text("Dauer pro Phase (× 4 Tests × 2 Modi)", color = OnSurfaceMuted, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BenchmarkDuration.values().forEach { d ->
                    FilterChip(
                        selected = duration == d,
                        onClick = {
                            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                            duration = d
                        },
                        label = { Text(d.short, fontSize = 11.sp) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val total = duration.seconds * 8
            Text(
                "Gesamt: ~${total / 60} min ${total % 60} s",
                color = AccentSoft, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                        BenchmarkService.start(context, BenchmarkType.CPU, duration.seconds)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Benchmark starten", fontWeight = FontWeight.Bold) }
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

        StatCard("Hinweise") {
            Text(
                "· Schließe vorher Spiele/Streaming-Apps für stabilere Scores\n" +
                    "· Bench-Service läuft als Foreground-Notification — du kannst die App im Hintergrund lassen\n" +
                    "· Ergebnis landet automatisch im Verlauf",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
        }
    }
}
