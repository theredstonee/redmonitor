package com.tamerin.sysmonitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentBubble
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

private data class Step(val title: String, val body: String, val cta: String)

private val STEPS = listOf(
    Step(
        "Willkommen bei RedMonitor",
        "Eine lokale, ad-freie Sysinfo-App. Zeigt Live-CPU, RAM, Akku, Sensoren, " +
            "Benchmarks, Hardware-Tests und vieles mehr. Alles bleibt auf dem Gerät — " +
            "keine Cloud, keine Telemetry.",
        "Weiter"
    ),
    Step(
        "Was du machen kannst",
        "• Live-Stats im 'Live'-Tab\n" +
            "• Benchmark-Suite: CPU, RAM, Storage, GPU, Stresstest\n" +
            "• Hardware-Tests: Touch, Display, Speaker, Mic, NFC, IR, Kompass…\n" +
            "• Pro-App-Traffic, Wake-Locks, Notification-Log, Permission-Audit\n" +
            "• Floating HUD über allen Apps + Quick-Settings-Tile",
        "Weiter"
    ),
    Step(
        "Shizuku = mehr Tiefe",
        "Viele Power-Features (Logcat, Wake-Locks, Shell-Terminal, Force-Stop, " +
            "automatische Permission-Unblocks) brauchen Shizuku — eine kleine App, " +
            "die der RedMonitor Shell-Privilegien gibt, ohne Root. Optional, aber empfohlen.",
        "Los geht's"
    )
)

@Composable
fun OnboardingDialog(onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    val current = STEPS[step]
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentBubble)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("${step + 1}/${STEPS.size}", color = AccentSoft, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Text(current.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Text(current.body, color = OnSurfaceMuted, fontSize = 13.sp, lineHeight = 18.sp)
        },
        confirmButton = {
            Button(onClick = {
                if (step < STEPS.lastIndex) step += 1 else onDismiss()
            }) { Text(current.cta) }
        },
        dismissButton = {
            if (step > 0) {
                OutlinedButton(onClick = { step -= 1 }) { Text("Zurück") }
            } else {
                TextButton(onClick = onDismiss) { Text("Überspringen") }
            }
        }
    )
}
