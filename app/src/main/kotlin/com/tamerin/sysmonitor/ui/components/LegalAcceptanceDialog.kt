package com.tamerin.sysmonitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

/**
 * DSGVO-Info-Dialog beim ersten App-Start.
 *
 * Bewusst KEIN „Ich akzeptiere"-Checkbox-Modell — Art. 13 DSGVO verlangt
 * INFORMATION über Datenverarbeitung, nicht Zustimmung dazu. Click-Akzeptanz
 * für Datenschutz wird von DE-Aufsichtsbehörden als irreführend kritisiert.
 *
 * Was wir machen:
 *   - Kurzer Info-Text mit was die App tut
 *   - Cloud-Sync-Toggle prominent (Default an, jederzeit umschaltbar) →
 *     das ist die echte „Einwilligung" für Art. 6 Abs. 1 lit. a
 *   - Links zum Volltext der Datenschutzerklärung + Nutzungsbedingungen
 *   - „Verstanden"-Button (= Info gesehen, nicht „akzeptiert")
 *
 * Für destruktive Features (Russian Roulette, Stresstest, Charging-Stop)
 * gibt es weiterhin separate per-Feature-Acks im jeweiligen Screen.
 */
@Composable
fun LegalAcceptanceDialog(
    onAccepted: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit
) {
    val context = LocalContext.current
    var cloudOpt by remember { mutableStateOf(CloudPrefs.isEnabled(context)) }
    Dialog(
        onDismissRequest = { /* User soll bewusst tippen */ },
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
                    .heightIn(max = 600.dp)
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
                        "Kurzer Hinweis zur Datenverarbeitung.",
                        color = OnSurfaceMuted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(16.dp))

                    Text(PrivacyText.SUMMARY, color = AccentSoft, fontSize = 13.sp,
                        lineHeight = 18.sp)
                    Spacer(Modifier.height(16.dp))

                    // Cloud-Toggle — das ist die echte Einwilligung
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
                                        LegalConstants.BACKEND_HOST + ". " +
                                        "Default: an. Jederzeit in den Einstellungen umschaltbar.",
                                    color = OnSurfaceMuted, fontSize = 11.sp, lineHeight = 14.sp
                                )
                            }
                            Switch(checked = cloudOpt, onCheckedChange = {
                                cloudOpt = it
                                CloudPrefs.setEnabled(context, it)
                            })
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Datenschutzerklärung",
                            color = Accent, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onOpenPrivacy() }
                        )
                        Text("·", color = OnSurfaceMuted, fontSize = 12.sp)
                        Text(
                            "Nutzungsbedingungen",
                            color = Accent, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onOpenTerms() }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Auch jederzeit in den Einstellungen unter 'Rechtliches' abrufbar.",
                        color = OnSurfaceMuted, fontSize = 10.sp
                    )
                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = onAccepted,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Verstanden, los geht's") }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Stand ${LegalConstants.LEGAL_LAST_UPDATED} · " +
                            "Datenschutz v${LegalConstants.PRIVACY_VERSION} · " +
                            "AGB v${LegalConstants.TERMS_VERSION}",
                        color = OnSurfaceMuted, fontSize = 9.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
