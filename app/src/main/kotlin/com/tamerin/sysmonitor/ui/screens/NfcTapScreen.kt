package com.tamerin.sysmonitor.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

@Composable
fun NfcTapScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val adapter = remember { NfcAdapter.getDefaultAdapter(context) }
    var enabled by remember { mutableStateOf(adapter?.isEnabled == true) }
    var lastReads by remember { mutableStateOf<List<TagRead>>(emptyList()) }
    val readyStatus = stringResource(R.string.hw_nfctap_ready)
    var status by remember { mutableStateOf(readyStatus) }

    DisposableEffect(activity) {
        if (activity == null || adapter == null) {
            onDispose { }
            return@DisposableEffect onDispose { }
        }
        val callback = NfcAdapter.ReaderCallback { tag ->
            val read = readTag(tag)
            lastReads = (listOf(read) + lastReads).take(20)
            status = context.getString(
                R.string.hw_nfctap_detected,
                read.tech.firstOrNull() ?: context.getString(R.string.hw_nfctap_unknown)
            )
        }
        runCatching {
            adapter.enableReaderMode(
                activity, callback,
                NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                null
            )
        }
        onDispose {
            runCatching { adapter.disableReaderMode(activity) }
        }
    }

    LaunchedEffect(Unit) {
        enabled = adapter?.isEnabled == true
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(stringResource(R.string.hw_nfctap_title)) {
            KeyValueRow(stringResource(R.string.hw_nfctap_hardware), if (adapter != null) stringResource(R.string.hw_nfctap_present) else stringResource(R.string.hw_nfctap_missing))
            KeyValueRow(stringResource(R.string.hw_nfctap_nfc_enabled), if (enabled) stringResource(R.string.hw_yes_lc) else stringResource(R.string.hw_no_lc))
            if (adapter != null && !enabled) {
                Spacer(Modifier.height(6.dp))
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text(stringResource(R.string.hw_nfctap_turn_on)) }
            }
            Spacer(Modifier.height(6.dp))
            Text(status, color = OnSurfaceMuted, fontSize = 12.sp)
        }

        if (lastReads.isEmpty()) {
            StatCard(stringResource(R.string.hw_nfctap_instructions)) {
                Text(
                    stringResource(R.string.hw_nfctap_instructions_desc),
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
            }
        } else {
            lastReads.forEachIndexed { idx, r ->
                StatCard("Read #${lastReads.size - idx}") {
                    KeyValueRow("UID", r.uid)
                    KeyValueRow("Tech", r.tech.joinToString(", "))
                    KeyValueRow("Max-Size", r.maxSize?.let { "$it B" } ?: "—")
                    KeyValueRow("Writable", r.writable?.let { if (it) stringResource(R.string.hw_yes_lc) else stringResource(R.string.hw_no_lc) } ?: "—")
                    if (r.ndefPayload.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("NDEF:", color = Accent, fontSize = 11.sp)
                        Text(r.ndefPayload, color = OnSurfaceMuted, fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace)
                    }
                    if (r.error != null) {
                        Text("⚠ ${r.error}", color = GaugeOrange, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

private data class TagRead(
    val uid: String,
    val tech: List<String>,
    val maxSize: Int?,
    val writable: Boolean?,
    val ndefPayload: String,
    val error: String?
)

private fun readTag(tag: Tag): TagRead {
    val uid = tag.id.joinToString(" ") { "%02X".format(it) }
    val tech = tag.techList.map { it.substringAfterLast('.') }
    var max: Int? = null
    var writable: Boolean? = null
    var payload = ""
    var err: String? = null
    val ndef = runCatching { Ndef.get(tag) }.getOrNull()
    if (ndef != null) {
        try {
            ndef.connect()
            max = ndef.maxSize
            writable = ndef.isWritable
            val msg = ndef.cachedNdefMessage ?: ndef.ndefMessage
            payload = msg?.records?.joinToString("\n") { rec ->
                "type=${String(rec.type)}  payload=${String(rec.payload)}"
            }.orEmpty()
        } catch (t: Throwable) {
            err = t.message
        } finally {
            runCatching { ndef.close() }
        }
    }
    return TagRead(uid, tech, max, writable, payload, err)
}
