package com.tamerin.sysmonitor.ui.screens

import android.media.MediaCodecList
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    val codecs = remember { readCodecs() }
    var showEncoders by remember { mutableStateOf(true) }
    var showDecoders by remember { mutableStateOf(true) }
    var hwOnly by remember { mutableStateOf(false) }

    val filtered = remember(codecs, showEncoders, showDecoders, hwOnly) {
        codecs.filter {
            (if (it.isEncoder) showEncoders else showDecoders) &&
                (!hwOnly || it.isHardwareAccelerated)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(showEncoders, { showEncoders = !showEncoders }, label = { Text("Encoder") })
            FilterChip(showDecoders, { showDecoders = !showDecoders }, label = { Text("Decoder") })
            FilterChip(hwOnly, { hwOnly = !hwOnly }, label = { Text("Nur HW") })
        }
        Spacer(Modifier.height(8.dp))
        Text("${filtered.size} Codecs", color = OnSurfaceMuted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.name + it.isEncoder }) { c ->
                StatCard(c.name) {
                    KeyValueRow("Typ", if (c.isEncoder) "Encoder" else "Decoder")
                    KeyValueRow(
                        "Beschleunigung",
                        when {
                            c.isHardwareAccelerated -> "Hardware"
                            c.isSoftwareOnly -> "nur Software"
                            else -> "gemischt"
                        }
                    )
                    KeyValueRow("MIME-Typen", c.supportedTypes.joinToString(", "))
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
