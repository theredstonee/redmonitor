package com.tamerin.sysmonitor.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

@Composable
fun VibrationScreen() {
    val context = LocalContext.current
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("Info") {
            KeyValueRow("Vibrator vorhanden", if (vibrator.hasVibrator()) "Ja" else "Nein")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                KeyValueRow(
                    "Amplitudensteuerung",
                    if (vibrator.hasAmplitudeControl()) "Ja" else "Nein"
                )
            }
        }

        StatCard("Tests") {
            Text("Verschiedene Vibrationsmuster ausprobieren.",
                color = OnSurfaceMuted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))

            VibButton("Kurz (100 ms)") {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            VibButton("Lang (1 s)") {
                vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            VibButton("Doppel-Pulse") {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 100, 80, 100), -1
                    )
                )
            }
            VibButton("Heartbeat") {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 60, 60, 60, 300, 60, 60, 60, 300), -1
                    )
                )
            }
            VibButton("Stark (3 × 200 ms)") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasAmplitudeControl()) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 200, 100, 200, 100, 200),
                            intArrayOf(0, 255, 0, 255, 0, 255),
                            -1
                        )
                    )
                } else {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 200, 100, 200, 100, 200), -1
                        )
                    )
                }
            }
            VibButton("Click-Effekt") {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            }
            VibButton("Tick-Effekt") {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            }
            VibButton("Stopp") { vibrator.cancel() }
        }
    }
}

@Composable
private fun VibButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) { Text(label) }
}
