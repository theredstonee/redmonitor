package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ColorTest(val name: String, val color: Color)

private val COLORS = listOf(
    ColorTest("Rot", Color.Red),
    ColorTest("Grün", Color.Green),
    ColorTest("Blau", Color.Blue),
    ColorTest("Weiß", Color.White),
    ColorTest("Schwarz", Color.Black),
    ColorTest("Cyan", Color.Cyan),
    ColorTest("Magenta", Color.Magenta),
    ColorTest("Gelb", Color.Yellow)
)

@Composable
fun DisplayTestScreen() {
    var fullscreen by remember { mutableIntStateOf(-1) }
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    if (fullscreen >= 0) {
        val c = COLORS[fullscreen]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(c.color)
                .clickable {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    fullscreen = if (fullscreen + 1 < COLORS.size) fullscreen + 1 else -1
                }
        ) {
            Text(
                "${c.name}  ·  Tap = nächste Farbe, Letzte = zurück",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
                color = if (c.color == Color.White || c.color == Color.Yellow || c.color == Color.Cyan)
                    Color.Black else Color.White,
                fontSize = 12.sp
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Tippe auf eine Farbe, um den Screen vollflächig damit zu füllen. Im Vollbild bringt jeder Tap die nächste Farbe.",
            fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        COLORS.forEachIndexed { idx, c ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(c.color)
                    .clickable {
                        haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                        fullscreen = idx
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    c.name,
                    color = if (c.color == Color.White || c.color == Color.Yellow || c.color == Color.Cyan)
                        Color.Black else Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
