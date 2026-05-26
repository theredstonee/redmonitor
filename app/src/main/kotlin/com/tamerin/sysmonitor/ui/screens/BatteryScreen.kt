package com.tamerin.sysmonitor.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.ShizukuHelper
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlin.math.cos
import kotlin.math.sin
import com.tamerin.sysmonitor.benchmark.DrainEngine
import com.tamerin.sysmonitor.data.BatteryReader
import com.tamerin.sysmonitor.data.BatterySnapshot
import com.tamerin.sysmonitor.ui.components.CircularGauge
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.delay

private fun emptyBattery() = BatterySnapshot(
    percent = 0f, isCharging = false, pluggedSource = "—", isWireless = false,
    healthLabel = "—", statusLabel = "—", temperatureC = 0f, voltageV = 0f,
    technology = "—", currentNowMa = 0L, averageCurrentMa = 0L,
    deltaDerivedCurrentMa = 0L, capacityMah = 0,
    capacityFullDesignMah = 0, capacityFullMah = 0, healthPercent = 0f,
    energyNwh = 0L, wattsNow = 0f, inputVoltageV = 0f, inputCurrentMa = 0L,
    wattsSource = "—", chargingSpeedLabel = "—", sensorTrust = false
)

@Composable
fun BatteryScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    var snap by remember { mutableStateOf(emptyBattery()) }

    val drainEngine = remember { DrainEngine() }
    var drainActive by remember { mutableStateOf(false) }
    var drainStartPct by remember { mutableFloatStateOf(0f) }
    var drainStartMs by remember { mutableLongStateOf(0L) }
    var drainRatePctPerHour by remember { mutableFloatStateOf(0f) }
    var drainAutoStopMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            if (drainEngine.running) drainEngine.stop(context, activity)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            snap = BatteryReader.read(context)
            // Drain-Tracking
            if (drainActive && drainEngine.running) {
                val elapsedMs = System.currentTimeMillis() - drainStartMs
                if (elapsedMs > 5_000) {
                    val dropped = drainStartPct - snap.percent
                    val hours = elapsedMs / 3_600_000f
                    drainRatePctPerHour = if (hours > 0) dropped / hours else 0f
                }
                // Auto-Stop bei 10 %
                if (snap.percent in 0.1f..10f) {
                    drainEngine.stop(context, activity)
                    drainActive = false
                    drainAutoStopMsg = "⛔ Auto-Stop bei ${snap.percent.toInt()} % Akku"
                }
            }
            delay(1500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularGauge(
                percent = snap.percent,
                label = if (snap.isCharging) "Lädt (${snap.pluggedSource})" else snap.pluggedSource,
                size = 180.dp
            )
        }

        // === DRAIN MODE CARD ===
        StatCard(if (drainActive) "🔥 DRAIN MODE AKTIV" else "Akku-Drainer") {
            Text(
                "Lädt CPU, Bildschirm, Taschenlampe, Vibration, Lautsprecher und GPS gleichzeitig voll aus, um den Akku schnellstmöglich zu leeren. Stoppt automatisch bei 10 % Akkustand.",
                color = OnSurfaceMuted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "⚠ Gerät wird sehr heiß und sehr laut. Lege es auf eine harte Oberfläche, nicht in die Tasche. Lange Drain-Zyklen können dem Akku schaden.",
                color = GaugeRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(12.dp))

            if (drainActive) {
                Button(
                    onClick = {
                        drainEngine.stop(context, activity)
                        drainActive = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GaugeRed),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Drain Mode STOPPEN") }
                Spacer(Modifier.height(12.dp))
                KeyValueRow(
                    "Drain-Rate",
                    if (drainRatePctPerHour > 0)
                        "${"%.1f".format(drainRatePctPerHour)} %/h"
                    else "messe…"
                )
                KeyValueRow(
                    "Akku verbraucht seit Start",
                    "${"%.1f".format(drainStartPct - snap.percent)} %"
                )
                val elapsedSec = ((System.currentTimeMillis() - drainStartMs) / 1000).toInt()
                KeyValueRow("Laufzeit", "${elapsedSec / 60} min ${elapsedSec % 60} s")
                Spacer(Modifier.height(8.dp))
                Text("Aktive Kanäle:", color = OnSurfaceMuted, fontSize = 11.sp,
                    fontWeight = FontWeight.Medium)
                drainEngine.activeChannels.forEach { ch ->
                    Text("• $ch", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                Text("GPU-Burner (rendert dauerhaft):",
                    color = OnSurfaceMuted, fontSize = 11.sp)
                GpuBurner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            } else {
                Button(
                    onClick = {
                        drainStartPct = snap.percent
                        drainStartMs = System.currentTimeMillis()
                        drainRatePctPerHour = 0f
                        drainAutoStopMsg = null
                        drainEngine.start(context, activity, scope)
                        drainActive = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GaugeRed),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = snap.percent > 10f
                ) { Text("Drain Mode AKTIVIEREN") }
                drainAutoStopMsg?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Accent, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold)
                }
                if (snap.percent <= 10f) {
                    Spacer(Modifier.height(6.dp))
                    Text("Akku schon zu niedrig — erst laden.",
                        color = OnSurfaceMuted, fontSize = 11.sp)
                }
            }
        }

        StatCard("Leistung jetzt") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (snap.sensorTrust) "%.2f".format(snap.wattsNow) else "—",
                        color = if (snap.isCharging) GaugeGreen else Accent,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (snap.isCharging) "W Eingang" else "W Verbrauch",
                        color = OnSurfaceMuted, fontSize = 12.sp
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (snap.sensorTrust) "%.0f".format(kotlin.math.abs(snap.currentNowMa.toFloat())) else "—",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 32.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text("mA Strom", color = OnSurfaceMuted, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%.2f".format(snap.voltageV),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 32.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text("V Spannung", color = OnSurfaceMuted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            KeyValueRow(
                "Richtung",
                if (snap.isCharging) "→ Eingang (Akku wird geladen)"
                else "← Ausgang (System nutzt Akku)"
            )
            KeyValueRow(
                "Durchschnitt",
                if (snap.sensorTrust) "%.0f mA".format(kotlin.math.abs(snap.averageCurrentMa.toFloat())) else "—"
            )
            KeyValueRow("Mess-Quelle", snap.wattsSource)
            if (snap.isCharging && snap.inputVoltageV > 0.5f) {
                Spacer(Modifier.height(8.dp))
                KeyValueRow(
                    "USB-Eingang (PD)",
                    "${"%.2f".format(snap.inputVoltageV)} V × ${kotlin.math.abs(snap.inputCurrentMa)} mA"
                )
            }
            if (!snap.sensorTrust) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "⚠ Auf diesem Gerät liefert weder BatteryManager noch sysfs sinnvolle Strom-Messwerte. " +
                        "Typisch für Emulatoren und manche OEM-Sperren.",
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }
        }

        StatCard("Lade-Status") {
            KeyValueRow("Status", snap.statusLabel)
            KeyValueRow("Stromquelle", snap.pluggedSource)
            KeyValueRow("Wireless?", if (snap.isWireless) "Ja (Qi/induktiv)" else "Nein")
            KeyValueRow("Lade-Geschwindigkeit", snap.chargingSpeedLabel)
            if (snap.isCharging && snap.wattsNow >= 15f && snap.sensorTrust) {
                Spacer(Modifier.height(6.dp))
                Text("⚡ Schnellladen aktiv", color = GaugeGreen,
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }

        StatCard("Akku-Zustand") {
            KeyValueRow("Ladezustand", "${snap.percent.toInt()} %")
            KeyValueRow("Gesundheit (System)", snap.healthLabel)
            if (snap.healthPercent in 1f..100f) {
                KeyValueRow("Akku-Verschleiß (berechnet)",
                    "${"%.0f".format(snap.healthPercent)} % der Werks-Kapazität")
            }
            KeyValueRow("Temperatur", "${"%.1f".format(snap.temperatureC)} °C")
            if (snap.temperatureC >= 45f) {
                Spacer(Modifier.height(6.dp))
                Text("⚠ Akku überhitzt", color = GaugeRed,
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }

        StatCard("Technik") {
            KeyValueRow("Technologie", snap.technology)
            KeyValueRow("Spannung", "${"%.2f".format(snap.voltageV)} V")
            if (snap.capacityMah > 0)
                KeyValueRow("Aktuelle Kapazität", "${snap.capacityMah} mAh")
            if (snap.capacityFullMah > 0)
                KeyValueRow("Voll-Kapazität", "${snap.capacityFullMah} mAh")
            if (snap.capacityFullDesignMah > 0)
                KeyValueRow("Werks-Kapazität (design)", "${snap.capacityFullDesignMah} mAh")
            if (snap.energyNwh > 0)
                KeyValueRow("Energie verbleibend", "${snap.energyNwh / 1_000_000L} mWh")
        }

        ShizukuCard()

        StatCard("Diagnose (Mess-Methoden)") {
            Text(
                "Falls die angezeigte Watt-Zahl nicht zu deinem Ladegerät passt — hier siehst du was jede einzelne Sensor-Quelle liefert. Auf manchen OEMs (Samsung, Xiaomi) liefert nur eine davon zuverlässige Werte.",
                color = OnSurfaceMuted, fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            KeyValueRow("Status (BatteryManager)", if (snap.isCharging) "Lädt" else "Entlädt")
            KeyValueRow("Beste Quelle", snap.wattsSource)
            KeyValueRow("Bester Strom (verwendet)", "${snap.currentNowMa} mA")
            KeyValueRow("Δ Charge-Counter", if (snap.deltaDerivedCurrentMa != 0L) "${snap.deltaDerivedCurrentMa} mA" else "wartet (~2 s Sampling)")
            KeyValueRow("BatteryManager AVG", "${snap.averageCurrentMa} mA")
            if (snap.inputVoltageV > 0.5f) {
                KeyValueRow(
                    "USB / PD (sysfs oder dumpsys)",
                    "${"%.2f".format(snap.inputVoltageV)} V × ${snap.inputCurrentMa} mA = " +
                        "${"%.1f".format(snap.inputVoltageV * kotlin.math.abs(snap.inputCurrentMa.toFloat()) / 1000f)} W"
                )
            } else {
                val shizuku = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
                KeyValueRow(
                    "USB / PD",
                    if (!snap.isCharging) "—  (nicht angeschlossen)"
                    else if (!shizuku) "gesperrt (Shizuku nicht aktiv)"
                    else "Samsung sperrt auch via Shizuku (SELinux) — Δ-Methode greift stattdessen"
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "💡 Hinweis: Ohne Root kann KEINE App die echte Wand-Watt-Zahl messen, wenn der OEM die USB-Pfade sperrt. Die Δ-Charge-Counter-Methode misst was wirklich im Akku ankommt — auf S22+ typisch 18–22 W bei dem 25-W-Charger (Konversionsverluste).",
                color = OnSurfaceMuted, fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun StepRow(num: String, title: String, hint: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            num,
            color = Accent,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.width(22.dp)
        )
        Column {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp,
                fontWeight = FontWeight.Medium)
            Text(hint, color = OnSurfaceMuted, fontSize = 10.sp)
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

@Composable
private fun ShizukuCard() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(ShizukuHelper.state(context)) }

    DisposableEffect(Unit) {
        val listener = rikka.shizuku.Shizuku.OnRequestPermissionResultListener { _, _ ->
            state = ShizukuHelper.state(context)
        }
        ShizukuHelper.addPermissionListener(listener)
        onDispose { ShizukuHelper.removePermissionListener(listener) }
    }

    // Re-check state periodically (user might install/start Shizuku while card is shown)
    LaunchedEffect(Unit) {
        while (true) {
            state = ShizukuHelper.state(context)
            kotlinx.coroutines.delay(2500)
        }
    }

    StatCard("Shizuku — echte PD-Watt freischalten") {
        Text(
            "Samsung & manche andere OEMs sperren die USB-Sysfs-Pfade per SELinux. " +
                "Mit Shizuku (eine offizielle, quelloffene App von Rikka) kann diese App im shell-User-Kontext lesen — dann sehen wir die echten Watt vom Charger (5/9/15/20 V × USB-Strom).",
            color = OnSurfaceMuted, fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        KeyValueRow("Status", when (state) {
            ShizukuHelper.State.NotInstalled -> "Nicht installiert"
            ShizukuHelper.State.NotRunning -> "Installiert, aber nicht gestartet"
            ShizukuHelper.State.NeedsPermission -> "Läuft — Berechtigung fehlt"
            ShizukuHelper.State.Ready -> "Bereit ✓ (PD-Watt verfügbar)"
        })
        Spacer(Modifier.height(10.dp))
        when (state) {
            ShizukuHelper.State.NotInstalled -> {
                // Smart detection: Shizuku Play-Store-Version targets older SDK,
                // Google blocks "not for your device" on Android 15+ and on many Samsungs.
                val sdkInt = android.os.Build.VERSION.SDK_INT
                val manufacturer = android.os.Build.MANUFACTURER?.lowercase().orEmpty()
                val recommendGithub = sdkInt >= android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM ||
                    manufacturer.contains("samsung")

                if (recommendGithub) {
                    Text(
                        "Erkannt: Android $sdkInt · ${android.os.Build.MANUFACTURER}. " +
                            "Auf deinem Gerät blockiert der Play Store Shizuku meistens. " +
                            "Empfohlen: APK direkt von GitHub.",
                        color = AccentSoft, fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        openUrl(context, "https://github.com/RikkaApps/Shizuku/releases/latest")
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("APK von GitHub laden (empfohlen)")
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = {
                        openUrl(context, "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Trotzdem Play Store versuchen")
                    }
                } else {
                    Text(
                        "Erkannt: Android $sdkInt · ${android.os.Build.MANUFACTURER}. " +
                            "Play Store sollte bei dir funktionieren.",
                        color = OnSurfaceMuted, fontSize = 11.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        openUrl(context, "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Im Play Store öffnen")
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = {
                        openUrl(context, "https://github.com/RikkaApps/Shizuku/releases/latest")
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Alternativ: APK von GitHub")
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = {
                    openUrl(context, "https://shizuku.rikka.app/guide/setup/")
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Setup-Anleitung")
                }
            }
            ShizukuHelper.State.NotRunning -> {
                Text(
                    "Shizuku ist installiert, läuft aber nicht. Du musst ihn jetzt starten:",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                StepRow("1.", "Einstellungen öffnen", "Android-Einstellungen → Entwickleroptionen")
                StepRow("2.", "Drahtloses Debugging AN", "muss eingeschaltet bleiben")
                StepRow("3.", "Pairing-Code holen", "in Entwickleroptionen: 'Gerät mit Pairing-Code koppeln'")
                StepRow("4.", "Shizuku öffnen", "Pair via Wireless Debugging → Code eingeben")
                StepRow("5.", "In Shizuku: Start", "grüner Status oben muss erscheinen")
                StepRow("6.", "Hierher zurückkommen", "Status springt automatisch")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            runCatching {
                                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Entwickler-Settings", fontSize = 12.sp) }
                    Button(
                        onClick = {
                            val launchIntent = context.packageManager
                                .getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            if (launchIntent != null) context.startActivity(launchIntent)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Shizuku öffnen", fontSize = 12.sp) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Hinweis: Shizuku überlebt keinen Reboot. Nach jedem Neustart musst du Schritte 2-5 wiederholen (außer du hast Root).",
                    color = OnSurfaceMuted, fontSize = 10.sp
                )
            }
            ShizukuHelper.State.NeedsPermission -> {
                Text(
                    "Shizuku läuft. Jetzt fehlt nur noch deine Erlaubnis für diese App.",
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { ShizukuHelper.requestPermission() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Berechtigung erteilen") }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Es erscheint ein Shizuku-Dialog — auf 'Allow' tippen.",
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }
            ShizukuHelper.State.Ready -> {
                Text(
                    "Perfekt. Die App kann jetzt die echten USB-PD-Werte deines Chargers lesen.",
                    color = androidx.compose.ui.graphics.Color(0xFF22C55E),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun GpuBurner(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "burner")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "burner-t"
    )
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = kotlin.math.min(size.width, size.height) / 2f
        // 250 rotating shapes — heavy GPU + over-draw on purpose
        for (i in 0 until 250) {
            val phase = (i / 250f + t) * 2f * Math.PI.toFloat()
            val r = maxR * (0.15f + 0.85f * ((i * 17 % 100) / 100f))
            val x = cx + cos(phase.toDouble()).toFloat() * r * 0.6f
            val y = cy + sin(phase.toDouble()).toFloat() * r * 0.6f
            val hue = (i * 360f / 250f + t * 360f) % 360f
            drawCircle(
                color = hsv(hue, 1f, 1f, 0.3f),
                radius = 12f + (i % 7) * 2f,
                center = Offset(x, y),
                style = Stroke(width = 2f)
            )
        }
    }
}

private fun hsv(h: Float, s: Float, v: Float, alpha: Float): Color {
    val c = v * s
    val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
    val (r, g, b) = when {
        h < 60 -> Triple(c, x, 0f)
        h < 120 -> Triple(x, c, 0f)
        h < 180 -> Triple(0f, c, x)
        h < 240 -> Triple(0f, x, c)
        h < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r, g, b, alpha)
}
