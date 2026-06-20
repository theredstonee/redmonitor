package com.tamerin.sysmonitor.ui.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
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
import androidx.compose.ui.platform.LocalContext
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

/**
 * Earpiece (Hörmuschel) testen — Ton über USAGE_VOICE_COMMUNICATION + STREAM_VOICE_CALL,
 * dazu AudioManager.mode = IN_COMMUNICATION + speakerphone OFF.
 * So routet Android den Stream auf den Earpiece-Speaker statt auf den Loudspeaker.
 */
@Composable
fun EarpieceTestScreen() {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var freq by remember { mutableFloatStateOf(440f) }
    var volume by remember { mutableFloatStateOf(0.7f) }
    var playing by remember { mutableStateOf(false) }
    var track by remember { mutableStateOf<AudioTrack?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var route by remember { mutableStateOf("—") }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val prevMode = audio.mode
        val prevSpeaker = audio.isSpeakerphoneOn
        onDispose {
            job?.cancel()
            track?.stop(); track?.release()
            track = null
            runCatching {
                audio.mode = prevMode
                audio.isSpeakerphoneOn = prevSpeaker
            }
        }
    }

    fun stop() {
        job?.cancel()
        track?.stop(); track?.release(); track = null
        playing = false
        runCatching {
            audio.mode = AudioManager.MODE_NORMAL
            audio.isSpeakerphoneOn = false
        }
    }

    fun routeName(t: AudioTrack?): String = runCatching {
        val rd = t?.routedDevice ?: return@runCatching "—"
        when (rd.type) {
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "EARPIECE ✓"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "LOUDSPEAKER (Routing fail)"
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BT-SCO"
            else -> "Type ${rd.type}"
        }
    }.getOrDefault("—")

    fun start() {
        stop()
        runCatching {
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
            audio.isSpeakerphoneOn = false
        }
        val sampleRate = 16_000
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setLegacyStreamType(AudioManager.STREAM_VOICE_CALL)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        t.setVolume(volume)
        track = t
        playing = true

        job = scope.launch(Dispatchers.Default) {
            val chunk = ShortArray(1024)
            var phase = 0.0
            val twoPi = 2.0 * PI
            t.play()
            while (isActive) {
                val phaseStep = twoPi * freq.toDouble() / sampleRate
                for (i in chunk.indices) {
                    chunk[i] = (sin(phase) * 32_767 * volume).toInt().toShort()
                    phase += phaseStep
                    if (phase > twoPi) phase -= twoPi
                }
                t.write(chunk, 0, chunk.size)
                route = routeName(t)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("Earpiece (Hörmuschel)") {
            Text(
                "Spielt einen Ton mit USAGE_VOICE_COMMUNICATION + Mode IN_COMMUNICATION " +
                    "und SpeakerphoneOff — damit routet Android den Stream auf die kleine " +
                    "Hörmuschel oben am Telefon, nicht auf den Loudspeaker.",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            KeyValueRow("Aktuelles Routing", route)
        }

        StatCard("Tonparameter") {
            KeyValueRow("Frequenz", "${freq.toInt()} Hz")
            Slider(
                value = freq,
                onValueChange = { freq = it; if (playing) start() },
                valueRange = 100f..3000f
            )
            Spacer(Modifier.height(8.dp))
            KeyValueRow("Lautstärke", "${(volume * 100).toInt()} %")
            Slider(
                value = volume,
                onValueChange = { volume = it; track?.setVolume(it) },
                valueRange = 0f..1f
            )
        }

        StatCard("Steuerung") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { start() }, enabled = !playing) { Text("Earpiece-Ton an") }
                OutlinedButton(onClick = { stop() }, enabled = playing) { Text("Stop") }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Halte das Telefon ans Ohr wie bei einem Anruf, um den Ton aus der Hörmuschel zu hören.",
                color = OnSurfaceMuted, fontSize = 11.sp
            )
        }
    }
}
