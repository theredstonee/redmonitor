package com.tamerin.sysmonitor.ui.screens

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private enum class Channel(val label: String) { LEFT("Nur Links"), RIGHT("Nur Rechts"), BOTH("Beide") }

@Composable
fun SpeakerTestScreen() {
    var freq by remember { mutableFloatStateOf(440f) }
    var channel by remember { mutableStateOf(Channel.BOTH) }
    var volume by remember { mutableFloatStateOf(0.5f) }
    var playing by remember { mutableStateOf(false) }
    var track by remember { mutableStateOf<AudioTrack?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    DisposableEffect(Unit) {
        onDispose {
            job?.cancel()
            track?.stop(); track?.release()
            track = null
        }
    }

    fun stop() {
        job?.cancel()
        track?.stop()
        track?.release()
        track = null
        playing = false
    }

    fun start() {
        stop()
        val sampleRate = 44100
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        t.setVolume(volume)
        track = t
        playing = true

        job = scope.launch(Dispatchers.Default) {
            val chunk = ShortArray(2048)
            var phase = 0.0
            val twoPi = 2.0 * PI
            t.play()
            while (isActive) {
                val f = freq.toDouble()
                val phaseStep = twoPi * f / sampleRate
                var i = 0
                while (i < chunk.size) {
                    val sample = (sin(phase) * 32_767 * volume).toInt().toShort()
                    val left = if (channel == Channel.RIGHT) 0 else sample.toInt()
                    val right = if (channel == Channel.LEFT) 0 else sample.toInt()
                    chunk[i] = left.toShort()
                    chunk[i + 1] = right.toShort()
                    phase += phaseStep
                    if (phase > twoPi) phase -= twoPi
                    i += 2
                }
                t.write(chunk, 0, chunk.size)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("Speaker-Tongenerator") {
            Text(
                "Spielt einen Sinuston über den ausgewählten Kanal. Lass den Ton kurz laufen und prüfe, ob beide Lautsprecher tatsächlich klingen.",
                color = OnSurfaceMuted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            KeyValueRow("Frequenz", "${freq.toInt()} Hz")
            Slider(
                value = freq,
                onValueChange = {
                    freq = it
                    if (playing) start()
                },
                valueRange = 50f..15_000f
            )
            Spacer(Modifier.height(8.dp))
            KeyValueRow("Lautstärke", "${(volume * 100).toInt()} %")
            Slider(
                value = volume,
                onValueChange = {
                    volume = it
                    track?.setVolume(it)
                },
                valueRange = 0f..1f
            )
        }

        StatCard("Kanal") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Channel.values().forEach { c ->
                    val sel = channel == c
                    OutlinedButton(
                        onClick = {
                            channel = c
                            if (playing) start()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(c.label, fontSize = 12.sp) }
                }
            }
        }

        StatCard("Steuerung") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                        start()
                    },
                    enabled = !playing
                ) { Text("Ton starten") }
                OutlinedButton(
                    onClick = {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                        stop()
                    },
                    enabled = playing
                ) { Text("Stop") }
            }
            Spacer(Modifier.height(8.dp))
            QuickFreq("440 Hz (A4)") { freq = 440f; if (playing) start() }
            QuickFreq("1 kHz Referenz") { freq = 1000f; if (playing) start() }
            QuickFreq("10 kHz (Höhen)") { freq = 10_000f; if (playing) start() }
            QuickFreq("100 Hz (Bass)") { freq = 100f; if (playing) start() }
        }
    }
}

@androidx.compose.runtime.Composable
private fun QuickFreq(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) { Text(label) }
}
