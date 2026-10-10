package com.tamerin.sysmonitor.ui.screens

import android.media.MediaCodecList
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tamerin.sysmonitor.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

private data class CodecEntry(
    val name: String,
    val isEncoder: Boolean,
    val isHardwareAccelerated: Boolean,
    val isSoftwareOnly: Boolean,
    val supportedTypes: List<String>
)

@Composable
fun CodecsScreen() {
    var codecs by remember { mutableStateOf<List<CodecEntry>?>(null) }
    var showEncoders by remember { mutableStateOf(true) }
    var showDecoders by remember { mutableStateOf(true) }
    var hwOnly by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        codecs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { readCodecs() }
    }

    val list = codecs.orEmpty()
    val filtered = remember(list, showEncoders, showDecoders, hwOnly) {
        list.filter {
            (if (it.isEncoder) showEncoders else showDecoders) &&
                (!hwOnly || it.isHardwareAccelerated)
        }
    }

    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(showEncoders, {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                showEncoders = !showEncoders
            }, label = { Text("Encoder") })
            FilterChip(showDecoders, {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                showDecoders = !showDecoders
            }, label = { Text("Decoder") })
            FilterChip(hwOnly, {
                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                hwOnly = !hwOnly
            }, label = { Text(stringResource(R.string.bench_codecs_hw_only)) })
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (codecs == null) stringResource(R.string.bench_codecs_loading) else "${filtered.size} Codecs",
            color = OnSurfaceMuted, fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.name + it.isEncoder }) { c ->
                StatCard(c.name) {
                    KeyValueRow(stringResource(R.string.bench_type), if (c.isEncoder) "Encoder" else "Decoder")
                    KeyValueRow(
                        stringResource(R.string.bench_codecs_acceleration),
                        when {
                            c.isHardwareAccelerated -> stringResource(R.string.bench_codecs_hw)
                            c.isSoftwareOnly -> stringResource(R.string.bench_codecs_sw_only)
                            else -> stringResource(R.string.bench_codecs_mixed)
                        }
                    )
                    KeyValueRow(stringResource(R.string.bench_codecs_mime_types), c.supportedTypes.joinToString(", "))
                }
            }
        }
    }
}

private fun readCodecs(): List<CodecEntry> {
    return runCatching {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.map { info ->
            CodecEntry(
                name = info.name,
                isEncoder = info.isEncoder,
                isHardwareAccelerated = runCatching { info.isHardwareAccelerated }.getOrDefault(false),
                isSoftwareOnly = runCatching { info.isSoftwareOnly }.getOrDefault(false),
                supportedTypes = info.supportedTypes.toList()
            )
        }.sortedBy { it.name }
    }.getOrDefault(emptyList())
}
