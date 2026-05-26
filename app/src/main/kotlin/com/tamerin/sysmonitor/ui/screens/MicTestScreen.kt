package com.tamerin.sysmonitor.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tamerin.sysmonitor.ui.components.PercentBar
import com.tamerin.sysmonitor.ui.components.Sparkline
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.sqrt

@Composable
fun MicTestScreen() {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    var listening by remember { mutableStateOf(false) }
    var levelPercent by remember { mutableFloatStateOf(0f) }
    var levelDb by remember { mutableFloatStateOf(-100f) }
    var peakDb by remember { mutableFloatStateOf(-100f) }
    val history = remember { mutableStateListOf<Float>() }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    DisposableEffect(Unit) {
        onDispose { job?.cancel() }
    }

    fun stop() {
        job?.cancel()
        listening = false
    }

    fun start() {
        stop()
        listening = true
        peakDb = -100f
        history.clear()
        job = scope.launch(Dispatchers.Default) {
            val sampleRate = 44100
            val bufSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)
            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufSize
                )
            } catch (e: SecurityException) {
                listening = false
                return@launch
            }
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                listening = false
                return@launch
            }
            try {
                recorder.startRecording()
                val buf = ShortArray(2048)
                while (isActive) {
                    val n = recorder.read(buf, 0, buf.size)
                    if (n > 0) {
                        var sumSq = 0.0
                        for (i in 0 until n) {
                            val s = buf[i].toDouble() / 32_768.0
                            sumSq += s * s
                        }
                        val rms = sqrt(sumSq / n)
                        val db = if (rms > 0) 20 * log10(rms) else -100.0
                        withContext(Dispatchers.Main) {
                            levelDb = db.toFloat().coerceIn(-100f, 0f)
                            if (levelDb > peakDb) peakDb = levelDb
                            levelPercent = ((levelDb + 100f) / 100f * 100f).coerceIn(0f, 100f)
                            history.add(levelPercent)
                            if (history.size > 200) history.removeAt(0)
                        }
                    }
                }
            } finally {
                recorder.stop()
                recorder.release()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!granted) {
            StatCard("Mikrofon-Berechtigung") {
                Text(
                    "Für den Pegelmeter brauchen wir Zugriff aufs Mikrofon.",
                    color = OnSurfaceMuted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { permLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text("Berechtigung erteilen")
                }
            }
            return@Column
        }

        StatCard("Pegelmeter") {
            Text(
                "${"%.0f".format(levelDb)} dB FS",
                color = Accent,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )
            Text("Peak: ${"%.0f".format(peakDb)} dB FS", color = OnSurfaceMuted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            PercentBar("Live-Pegel", levelPercent, valueText = "${levelPercent.toInt()} %")
        }

        if (history.size > 1) {
            StatCard("Verlauf") {
                Box(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                    Sparkline(values = history.toList(), color = Accent, minY = 0f, maxY = 100f)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                    start()
                },
                enabled = !listening
            ) { Text("Aufnahme starten") }
            OutlinedButton(
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    stop()
                },
                enabled = listening
            ) { Text("Stop") }
        }

        Text(
            "Halt dem Mikrofon was vor (Klatschen, Pfeifen, Sprache) und prüf, ob die Anzeige reagiert.",
            color = OnSurfaceMuted,
            fontSize = 11.sp
        )
    }
}
