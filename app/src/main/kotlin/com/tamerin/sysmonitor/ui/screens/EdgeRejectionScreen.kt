package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

/**
 * Curved-Screen Touch-Edge-Rejection-Check.
 * Zeichnet einen Rahmen (innere Edge-Zone) und zählt:
 *   - Total Touches
 *   - In den Edge-Zonen registriert (= no rejection)
 *   - Im Center registriert (sollte immer klappen)
 * Auf gut kalibrierten Curved-Displays werden 90%+ der absichtlichen Edge-Touches
 * abgelehnt — falsche Positive (versehentliche Handflächen-Berührungen) auch.
 */
@Composable
fun EdgeRejectionScreen() {
    val density = LocalDensity.current
    val edgePxState = remember(density) { density.run { 24.dp.toPx() } }
    var lastTouches by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var totalTouches by remember { mutableStateOf(0) }
    var edgeHits by remember { mutableStateOf(0) }
    var centerHits by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        StatCard("Edge-Rejection-Test") {
            Text(
                "Tippe absichtlich auf den orangen Rahmen-Bereich (Edge-Zone, ca. 24 dp). " +
                    "Wenn Touches dort als Hit gezählt werden, rejected dein Curved-Screen NICHT — " +
                    "schlecht für Palm-Rejection beim Spielen / Lesen.",
                color = OnSurfaceMuted, fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            KeyValueRow("Touches gesamt", "$totalTouches")
            KeyValueRow("Edge-Hits", "$edgeHits (höher = schlechter)")
            KeyValueRow("Center-Hits", "$centerHits")
        }
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = {
                    totalTouches = 0; edgeHits = 0; centerHits = 0
                    lastTouches = emptyList()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Reset", fontSize = 12.sp) }
            OutlinedButton(
                onClick = { lastTouches = emptyList() },
                modifier = Modifier.weight(1f)
            ) { Text("Marker löschen", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF050505))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            e.changes.forEach { c ->
                                if (c.pressed && c.previousPressed.not()) {
                                    val p = c.position
                                    totalTouches += 1
                                    val w = size.width; val h = size.height
                                    val isEdge = p.x < edgePxState || p.y < edgePxState ||
                                        p.x > w - edgePxState || p.y > h - edgePxState
                                    if (isEdge) edgeHits += 1 else centerHits += 1
                                    lastTouches = (lastTouches + p).takeLast(50)
                                }
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                drawRect(
                    color = GaugeOrange.copy(alpha = 0.15f),
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(w, edgePxState)
                )
                drawRect(
                    color = GaugeOrange.copy(alpha = 0.15f),
                    topLeft = Offset(0f, h - edgePxState),
                    size = androidx.compose.ui.geometry.Size(w, edgePxState)
                )
                drawRect(
                    color = GaugeOrange.copy(alpha = 0.15f),
                    topLeft = Offset(0f, edgePxState),
                    size = androidx.compose.ui.geometry.Size(edgePxState, h - 2 * edgePxState)
                )
                drawRect(
                    color = GaugeOrange.copy(alpha = 0.15f),
                    topLeft = Offset(w - edgePxState, edgePxState),
                    size = androidx.compose.ui.geometry.Size(edgePxState, h - 2 * edgePxState)
                )
                drawRect(
                    color = GaugeOrange,
                    topLeft = Offset(edgePxState, edgePxState),
                    size = androidx.compose.ui.geometry.Size(w - 2 * edgePxState, h - 2 * edgePxState),
                    style = Stroke(width = 2f)
                )
                lastTouches.forEach { p ->
                    val isEdge = p.x < edgePxState || p.y < edgePxState ||
                        p.x > w - edgePxState || p.y > h - edgePxState
                    drawCircle(
                        color = if (isEdge) GaugeOrange else Accent,
                        radius = 14f,
                        center = p
                    )
                }
            }
        }
    }
}
