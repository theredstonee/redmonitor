package com.tamerin.sysmonitor.ui.screens

import android.content.Context
import android.hardware.ConsumerIrManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

/**
 * IR-Blaster-Test (Xiaomi, Huawei, alte LG/HTC haben das).
 * Listet die unterstützten Carrier-Frequenzen und sendet Test-Bursts —
 * fürs sichtbare Testing am Handy-Camera-Sensor (Phone-Cam sieht IR-LEDs).
 */
@Composable
fun IrBlasterScreen() {
    val context = LocalContext.current
    val ir = remember {
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    }
    val hasEmitter = ir?.hasIrEmitter() == true
    val ranges = remember {
        runCatching { ir?.carrierFrequencies?.toList() }.getOrNull().orEmpty()
    }
    var lastResult by remember { mutableStateOf("—") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("IR-Blaster (ConsumerIrManager)") {
            KeyValueRow("Hardware", if (ir != null) "Service vorhanden" else "Service fehlt")
            KeyValueRow("IR-Emitter", if (hasEmitter) "ja" else "nein")
            if (!hasEmitter) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Kein IR-Blaster verbaut. Standardmäßig haben nur Xiaomi (bis Mi 11/12), " +
                        "Huawei (Mate-Serie) und ältere LG/HTC IR-LEDs.",
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }
        }

        if (hasEmitter) {
            StatCard("Carrier-Frequenzen") {
                if (ranges.isEmpty()) {
                    Text("Hardware meldet keine Ranges.", color = OnSurfaceMuted, fontSize = 12.sp)
                } else {
                    ranges.forEach { r ->
                        KeyValueRow("${r.minFrequency} - ${r.maxFrequency} Hz", "—")
                    }
                }
            }

            StatCard("Test-Bursts senden") {
                Text(
                    "Sendet 5×40 kHz Pulse à 1 ms ein/aus — am Handy-Selfie-Cam ist die IR-LED " +
                        "als helles weiß-blaues Leuchten sichtbar.",
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            lastResult = sendBurst(ir, 38_000, false)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("38 kHz", fontSize = 11.sp) }
                    Button(
                        onClick = { lastResult = sendBurst(ir, 40_000, false) },
                        modifier = Modifier.weight(1f)
                    ) { Text("40 kHz", fontSize = 11.sp) }
                    Button(
                        onClick = { lastResult = sendBurst(ir, 56_000, false) },
                        modifier = Modifier.weight(1f)
                    ) { Text("56 kHz", fontSize = 11.sp) }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { lastResult = sendBurst(ir, 38_000, true) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Power-Off Sony-Standard (12 Bits)") }
                Spacer(Modifier.height(6.dp))
                Text(lastResult, color = OnSurfaceMuted, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private fun sendBurst(ir: ConsumerIrManager?, freq: Int, long: Boolean): String {
    if (ir == null) return "ConsumerIrManager fehlt"
    val pattern = if (long) {
        // Sony SIRC standard: 2.4ms header + 0.6ms gap, 12 bits each 0.6ms gap with 0.6/1.2ms mark
        intArrayOf(2400, 600,
            1200, 600, 600, 600, 1200, 600, 600, 600, 600, 600,
            1200, 600, 600, 600, 600, 600, 600, 600, 600, 600,
            600, 600, 1200, 600)
    } else {
        intArrayOf(1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000)
    }
    return runCatching {
        ir.transmit(freq, pattern)
        "OK · ${freq / 1000} kHz · ${pattern.size} Slots"
    }.getOrElse { "Fehler: ${it.message}" }
}
