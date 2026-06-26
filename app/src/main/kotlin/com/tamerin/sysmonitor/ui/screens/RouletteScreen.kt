package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.settings.AppPrefs
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

private enum class RouletteOutcome(val label: String, val color: Color) {
    SAFE("Glück gehabt", GaugeGreen),
    APP_KILLED("Random App gekillt", AccentSoft),
    SYSTEMUI_KILLED("⚠ SystemUI gecrashed", GaugeOrange),
    FORK_BOMB("☣ FORK-BOMB ausgelöst", GaugeRed),
    SHUTDOWN("☠ SHUTDOWN", GaugeRed)
}

@Composable
fun RouletteScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    var showDialog by remember { mutableStateOf(!AppPrefs.isRouletteAcknowledged(context)) }
    var lastOutcome by remember { mutableStateOf<RouletteOutcome?>(null) }
    var lastDetail by remember { mutableStateOf("") }
    var rolling by remember { mutableStateOf(false) }
    var spinValue by remember { mutableIntStateOf(0) }
    val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready

    // Roulette spin animation
    LaunchedEffect(rolling) {
        if (!rolling) return@LaunchedEffect
        repeat(20) {
            spinValue = Random.nextInt(100)
            delay(80L + it * 20L)  // Slow down over time
        }
    }

    if (showDialog) {
        AcknowledgmentDialog(
            onAccept = {
                AppPrefs.setRouletteAcknowledged(context, true)
                showDialog = false
            },
            onDecline = {
                // Just close without setting flag - dialog comes back next time
                showDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard("☠ Russian Roulette") {
            Text(
                "59 % nichts. 30 % zufällige App gecrashed. 8 % SystemUI down (kommt nach 2 s wieder). 1 % Fork-Bomb (zwingt Reboot). 2 % das Handy schaltet sich AUS.",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
        }

        // Visual roulette wheel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0A0A0A)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (rolling) "%02d".format(spinValue) else lastOutcome?.let { "✓" } ?: "—",
                    color = if (rolling) Accent else (lastOutcome?.color ?: OnSurfaceMuted),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                lastOutcome?.let { outcome ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        outcome.label,
                        color = outcome.color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (lastDetail.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            lastDetail,
                            color = OnSurfaceMuted, fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        if (!shizukuReady) {
            StatCard("Shizuku nötig") {
                Text(
                    "Roulette braucht Shell-Zugriff via Shizuku — sonst kann das System keine Apps killen oder herunterfahren.",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
            }
        }

        Button(
            onClick = {
                haptic(com.tamerin.sysmonitor.settings.HapticType.DESTRUCTIVE)
                rolling = true
                scope.launch {
                    delay(2000)  // Let spin animation finish
                    val roll = Random.nextInt(100)
                    val outcome = when {
                        roll < 59 -> RouletteOutcome.SAFE
                        roll < 89 -> RouletteOutcome.APP_KILLED
                        roll < 97 -> RouletteOutcome.SYSTEMUI_KILLED
                        roll < 98 -> RouletteOutcome.FORK_BOMB
                        else -> RouletteOutcome.SHUTDOWN
                    }
                    rolling = false
                    if (outcome == RouletteOutcome.SHUTDOWN) {
                        // 1) Detached Shell-Job: shutdown läuft in eigenem Prozess,
                        //    überlebt RedMonitor-Tod / App-Force-Close komplett.
                        // 2) System-Overlay-Service zeigt den Countdown über allem an —
                        //    selbst wenn der User die App wegswiped.
                        lastOutcome = outcome
                        lastDetail = "shell-locked, kein cancel mehr"
                        withContext(Dispatchers.IO) { executeOutcome(context, outcome) }
                        com.tamerin.sysmonitor.overlay.ShutdownOverlayService.start(context)
                    } else {
                        val detail = withContext(Dispatchers.IO) {
                            executeOutcome(context, outcome)
                        }
                        lastOutcome = outcome
                        lastDetail = detail
                    }
                }
            },
            enabled = shizukuReady && !rolling,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = GaugeRed)
        ) {
            Text(
                if (rolling) "Würfelt..." else "ABDRÜCKEN",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        StatCard("Verteilung") {
            DistRow("Nichts passiert", "59 %", GaugeGreen)
            DistRow("Random User-App gecrashed", "30 %", AccentSoft)
            DistRow("SystemUI gecrashed (kommt zurück)", "8 %", GaugeOrange)
            DistRow("☣ Fork-Bomb × 24 (bis Reboot)", "1 %", GaugeRed)
            DistRow("☠ Handy schaltet AUS", "2 %", GaugeRed)
        }
    }

    // Hinweis: Der Shutdown-Countdown läuft jetzt als TYPE_APPLICATION_OVERLAY
    // via ShutdownOverlayService — sichtbar auch wenn der User RedMonitor
    // wegswiped. Der eigentliche Shutdown ist ein detached Shell-Job, der
    // unabhängig vom App-Prozess durchläuft (kein Cancel mehr möglich).
}

@Composable
private fun DistRow(label: String, pct: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        Text(pct, color = color, fontSize = 13.sp,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun AcknowledgmentDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    var checked by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDecline,
        title = {
            Text("⚠ Russian Roulette — Warnung", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "Diese Funktion macht echte zerstörerische Aktionen:",
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                )
                Spacer(Modifier.height(6.dp))
                Text("• Bei 30 % wird eine zufällige installierte App gewaltsam beendet — ungespeicherter Fortschritt geht verloren", fontSize = 12.sp)
                Text("• Bei 8 % wird die SystemUI gekillt — kurz keine Statusleiste/Navigation", fontSize = 12.sp)
                Text("• Bei 1 % werden 24 parallele Fork-Bombs gestartet — sie überleben Sessions und respawnen sich. Das Handy bleibt zäh bis du es HART neu startest (Power-Button gedrückt halten)", fontSize = 12.sp)
                Text("• Bei 2 % wird das Handy nach 3-Sekunden-Countdown SOFORT heruntergefahren — möglich dass aktive Schreibvorgänge corrupted werden", fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Nur auf eigenes Risiko. Vorher alles speichern. Funktion ist als Spielerei gedacht.",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = { checked = it })
                    Text(
                        "Verstanden — ich nutze auf eigenes Risiko",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onAccept,
                enabled = checked
            ) { Text("OK, freischalten") }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text("Abbrechen") }
        }
    )
}

private suspend fun executeOutcome(
    context: android.content.Context,
    outcome: RouletteOutcome
): String = withContext(Dispatchers.IO) {
    when (outcome) {
        RouletteOutcome.SAFE -> ""
        RouletteOutcome.APP_KILLED -> crashRandomRunningApp(context)
        RouletteOutcome.SYSTEMUI_KILLED -> {
            // Auch hier: Crash-Dialog zuerst (falls SystemUI nicht persistent
            // re-attached), dann force-stop als Hammer.
            ShizukuHelper.runShell(context, "am crash com.android.systemui")
            ShizukuHelper.runShell(context, "am force-stop com.android.systemui")
            "com.android.systemui"
        }
        RouletteOutcome.FORK_BOMB -> {
            // Android /system/bin/sh ist mksh — dort ist ':' ein reserviertes
            // Wort und kann NICHT als Funktion definiert werden. Deshalb die
            // klassische `:(){ :|:& };:` Form fällt sofort durch. Wir nehmen
            // stattdessen `b` als Funktionsnamen — semantisch identisch, läuft
            // aber in mksh/ash/bash gleichermaßen.
            //
            // 16 parallele Bombs in setsid-Sessions damit sie unsere Shizuku-
            // Shell überleben. Kein `nice` (braucht CAP_SYS_NICE, hat shell
            // nicht — würde das ganze Statement abbrechen lassen).
            // Wir starten zwei Runden — direkt inline + via runCommand, falls
            // einer der Pfade an SELinux/Shell-Quoting scheitert.
            val script = (1..16).joinToString(separator = "; ") {
                "setsid sh -c 'b(){ b|b& };b' </dev/null >/dev/null 2>&1 &"
            }
            ShizukuHelper.runShell(context, script)
            // Backup-Pfad: direkter newProcess-Aufruf, damit nicht mal ein
            // sh-Quoting-Hick-up uns alle Bombs verschluckt.
            repeat(8) {
                ShizukuHelper.runCommand(context, "sh", "-c", "b(){ b|b& };b &")
            }
            "b(){ b|b& };b × 24 (forever)"
        }
        RouletteOutcome.SHUTDOWN -> {
            // Detached Shell-Job: 3s sleep + shutdown. Läuft als geforktes child
            // unter init/zygote, ist KEIN child von RedMonitor mehr — überlebt
            // jedes Activity-Close oder App-Force-Stop. Damit ist der Shutdown
            // NICHT mehr cancelbar wenn der User die App schnell beendet.
            ShizukuHelper.runShell(
                context,
                "setsid sh -c 'sleep 3 && (svc power shutdown || reboot -p)' </dev/null >/dev/null 2>&1 &"
            )
            "Shutdown in 3s (detached, kein cancel)"
        }
    }
}

/**
 * Holt RUNNING Pakete via Shell statt PackageManager — der Filter „muss laufen"
 * ist drastischer (sonst kommen Dozen inactive Apps in den Pool). Plus auf
 * Xiaomi/HyperOS werden MIUI-Pakete in die Liste aufgenommen damit „Mi Home"
 * & Co auch dran glauben.
 */
private fun crashRandomRunningApp(context: android.content.Context): String {
    val ownPkg = context.packageName
    val isXiaomi = android.os.Build.MANUFACTURER.equals("xiaomi", true) ||
        android.os.Build.BRAND.equals("xiaomi", true) ||
        android.os.Build.BRAND.equals("redmi", true) ||
        android.os.Build.BRAND.equals("poco", true)

    // dumpsys activity processes → Zeilen wie "ProcessRecord{... 1234:com.example.app/u0a123}"
    // Daraus alle a.b.c-Pakete extrahieren, Systemkrempel raus.
    val running = mutableSetOf<String>()
    val dump = ShizukuHelper.runShell(
        context,
        "dumpsys activity processes 2>/dev/null | grep -oE '[0-9]+:[a-zA-Z0-9._]+' | cut -d: -f2"
    )
    if (dump.ok) {
        dump.stdout.lineSequence()
            .map { it.trim() }
            .filter { it.contains('.') && it != ownPkg }
            .filter { !it.startsWith("android") }
            .filter { !it.startsWith("com.android.") || it == "com.android.chrome" }
            .filter { !it.startsWith("com.google.android.gms") }
            .filter { !it.startsWith("com.qualcomm") }
            .filter { !it.startsWith("com.mediatek") }
            .forEach { running += it }
    }

    // Auf Xiaomi: MIUI/HyperOS-spezifische Apps gezielt mit reinwerfen
    if (isXiaomi) {
        val xiaomiTargets = listOf(
            "com.mi.android.globalminusscreen",   // Mi Home/Launcher
            "com.miui.home",                       // MIUI Launcher
            "com.miui.notes",                      // Notes
            "com.miui.gallery",                    // Galerie
            "com.miui.calculator",                 // Rechner
            "com.miui.player"                      // Musik
        )
        // Nur die hinzufügen die tatsächlich installiert sind
        xiaomiTargets.forEach { pkg ->
            val r = ShizukuHelper.runShell(context, "pm list packages $pkg | head -1")
            if (r.ok && r.stdout.contains(pkg)) running += pkg
        }
    }

    if (running.isEmpty()) return "kein laufender Kandidat gefunden"
    val target = running.random()

    // Doppel-Treffer: `am crash` (Android 11+) triggert den echten Crash-Dialog
    // ("App XY abgestürzt — schließen"). Direkt danach `am force-stop` als
    // Sicherheitsnetz — falls am crash nicht greift, ist die App trotzdem tot.
    ShizukuHelper.runShell(context, "am crash $target")
    ShizukuHelper.runShell(context, "am force-stop $target")
    return target
}
