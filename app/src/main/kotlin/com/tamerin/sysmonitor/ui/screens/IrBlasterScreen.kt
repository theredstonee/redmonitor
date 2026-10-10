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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.R
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
        StatCard(stringResource(R.string.hw_ir_title)) {
            KeyValueRow("Hardware", if (ir != null) stringResource(R.string.hw_ir_service_present) else stringResource(R.string.hw_ir_service_missing))
            KeyValueRow("IR-Emitter", if (hasEmitter) stringResource(R.string.hw_yes_lc) else stringResource(R.string.hw_no_lc))
            if (!hasEmitter) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.hw_ir_no_blaster),
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }
        }

        if (hasEmitter) {
            StatCard(stringResource(R.string.hw_ir_carrier_freqs)) {
                if (ranges.isEmpty()) {
                    Text(stringResource(R.string.hw_ir_no_ranges), color = OnSurfaceMuted, fontSize = 12.sp)
                } else {
                    ranges.forEach { r ->
                        KeyValueRow("${r.minFrequency} - ${r.maxFrequency} Hz", "—")
                    }
                }
            }

            StatCard(stringResource(R.string.hw_ir_send_bursts)) {
                Text(
                    stringResource(R.string.hw_ir_bursts_desc),
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            lastResult = sendBurst(context, ir, 38_000, false)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("38 kHz", fontSize = 11.sp) }
                    Button(
                        onClick = { lastResult = sendBurst(context, ir, 40_000, false) },
                        modifier = Modifier.weight(1f)
                    ) { Text("40 kHz", fontSize = 11.sp) }
                    Button(
                        onClick = { lastResult = sendBurst(context, ir, 56_000, false) },
                        modifier = Modifier.weight(1f)
                    ) { Text("56 kHz", fontSize = 11.sp) }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { lastResult = sendBurst(context, ir, 38_000, true) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.hw_ir_poweroff_sony)) }
                Spacer(Modifier.height(6.dp))
                Text(lastResult, color = OnSurfaceMuted, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private fun sendBurst(context: Context, ir: ConsumerIrManager?, freq: Int, long: Boolean): String {
    if (ir == null) return context.getString(R.string.hw_ir_manager_missing)
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
        context.getString(R.string.hw_ir_ok, freq / 1000, pattern.size)
    }.getOrElse { context.getString(R.string.hw_ir_error, it.message ?: "") }
}
