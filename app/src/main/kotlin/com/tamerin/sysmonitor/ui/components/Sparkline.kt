package com.tamerin.sysmonitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    minY: Float? = null,
    maxY: Float? = null
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (values.size < 2) return@Canvas
        val mn = minY ?: values.min()
        val mx = maxY ?: values.max()
        val range = (mx - mn).coerceAtLeast(0.0001f)
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { idx, v ->
            val x = idx * stepX
            val y = size.height - ((v - mn) / range) * size.height
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 3f))
    }
}
