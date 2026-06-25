package com.tamerin.sysmonitor.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tamerin.sysmonitor.cloud.CloudPrefs
import com.tamerin.sysmonitor.legal.LegalConstants
import com.tamerin.sysmonitor.legal.PrivacyText
import com.tamerin.sysmonitor.legal.TermsText
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

/**
 * Blockierender First-Launch-Dialog für DSGVO-Akzeptanz.
 * Lässt sich nicht wegswipen, nicht mit Back schließen. Nur via expliziten
 * Accept-Button (beide Checkboxen erforderlich) oder durch App-Beenden.
 */
@Composable
fun LegalAcceptanceDialog(
    onAccepted: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit
) {
    val context = LocalContext.current
    var privacyOk by remember { mutableStateOf(false) }
    var termsOk by remember { mutableStateOf(false) }
    var cloudOpt by remember { mutableStateOf(CloudPrefs.isEnabled(context)) }
    Dialog(
        onDismissRequest = { /* nicht wegklickbar */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF0000000)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF14141a))
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Willkommen bei RedMonitor",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Bevor du startest brauchen wir deine Zustimmung — gesetzlich erforderlich.",
                        color = OnSurfaceMuted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(16.dp))

                    Text("Kurzfassung", color = Accent, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(PrivacyText.SUMMARY, color = AccentSoft, fontSize = 12.sp,
                        lineHeight = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(TermsText.SUMMARY, color = AccentSoft, fontSize = 12.sp,
                        lineHeight = 16.sp)
                    Spacer(Modifier.height(16.dp))

                    // Cloud-Toggle prominent
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x14FFFFFF))
                        .padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Cloud-Sync aktivieren",
                                    color = Color.White, fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Anonymer Heartbeat + Ende-zu-Ende-verschlüsseltes Backup an " +
                                        LegalConstants.BACKEND_HOST + ". Default: an. Jederzeit in den Einstellungen abschaltbar.",
                                    color = OnSurfaceMuted, fontSize = 10.sp, lineHeight = 14.sp
                                )
                            }
                            Switch(checked = cloudOpt, onCheckedChange = {
                                cloudOpt = it
                                CloudPrefs.setEnabled(context, it)
                            })
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    // Checkbox + Link je Dokument
                    LegalCheckRow(
                        checked = privacyOk,
                        onChange = { privacyOk = it },
                        label = "Ich habe die Datenschutzerklärung gelesen und akzeptiere sie.",
                        onOpen = onOpenPrivacy
                    )
                    Spacer(Modifier.height(8.dp))
                    LegalCheckRow(
                        checked = termsOk,
                        onChange = { termsOk = it },
                        label = "Ich habe die Nutzungsbedingungen gelesen und akzeptiere sie.",
                        onOpen = onOpenTerms
                    )
                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = onAccepted,
                        enabled = privacyOk && termsOk,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Akzeptieren und starten") }
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = {
                            (context as? android.app.Activity)?.finishAffinity()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ablehnen und App beenden",
                            color = OnSurfaceMuted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Datenschutz v${LegalConstants.PRIVACY_VERSION} · " +
                            "AGB v${LegalConstants.TERMS_VERSION} · Stand ${LegalConstants.LEGAL_LAST_UPDATED}",
                        color = OnSurfaceMuted, fontSize = 9.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun LegalCheckRow(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    label: String,
    onOpen: () -> Unit
) {
    Row(verticalAlignment = Alignment.Top) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Column(modifier = Modifier.padding(top = 12.dp)) {
            Text(label, color = Color.White, fontSize = 12.sp, lineHeight = 16.sp)
            Text("→ Volltext anzeigen", color = Accent, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onOpen() })
        }
    }
}
