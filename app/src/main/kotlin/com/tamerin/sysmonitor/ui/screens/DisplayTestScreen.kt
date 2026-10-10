package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.R

private data class ColorTest(val nameRes: Int, val color: Color)

private val COLORS = listOf(
    ColorTest(R.string.hw_color_red, Color.Red),
    ColorTest(R.string.hw_color_green, Color.Green),
    ColorTest(R.string.hw_color_blue, Color.Blue),
    ColorTest(R.string.hw_color_white, Color.White),
    ColorTest(R.string.hw_color_black, Color.Black),
    ColorTest(R.string.hw_color_cyan, Color.Cyan),
    ColorTest(R.string.hw_color_magenta, Color.Magenta),
    ColorTest(R.string.hw_color_yellow, Color.Yellow)
)

@Composable
fun DisplayTestScreen() {
    var fullscreen by remember { mutableIntStateOf(-1) }
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()
    val immersive = com.tamerin.sysmonitor.LocalImmersive.current

    androidx.compose.runtime.LaunchedEffect(fullscreen) {
        immersive.value = fullscreen >= 0
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { immersive.value = false }
    }

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
                stringResource(R.string.hw_disptest_fullscreen_hint, stringResource(c.nameRes)),
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
            stringResource(R.string.hw_disptest_intro),
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
                    stringResource(c.nameRes),
                    color = if (c.color == Color.White || c.color == Color.Yellow || c.color == Color.Cyan)
                        Color.Black else Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
