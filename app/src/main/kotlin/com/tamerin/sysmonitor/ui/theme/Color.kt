package com.tamerin.sysmonitor.ui.theme

import androidx.compose.ui.graphics.Color

// === TheRedStonee palette ===
val BgDark = Color(0xFF000000)
val BgSoftDark = Color(0xFF0A0A0A)
val SurfaceDark = Color(0x80111111)          // rgba(17,17,17,0.5)
val SurfaceDarkSolid = Color(0xFF111111)
val SurfaceVariantDark = Color(0xFF1A1A1A)
val SurfaceHover = Color(0xB3111111)         // rgba(17,17,17,0.7)
val DividerWhite = Color(0x0FFFFFFF)         // rgba(255,255,255,0.06)
val DividerWhiteHover = Color(0x4DDC2626)    // rgba(220,38,38,0.3)

val OnSurface = Color(0xFFF3F4F6)            // gray-100
val OnSurfaceMuted = Color(0xFF9CA3AF)       // gray-400
val OnSurfaceDim = Color(0xFF6B7280)         // gray-500

// Primary brand red
val Accent = Color(0xFFDC2626)               // red-600 primary
val AccentDim = Color(0xFFB91C1C)            // red-700 hover
val AccentSoft = Color(0xFFF87171)           // red-400 for tinted text
val AccentBubble = Color(0x1FDC2626)         // rgba(220,38,38,0.12) icon bg
val AccentBorder = Color(0x33DC2626)         // rgba(220,38,38,0.2)
val AccentGlow = Color(0x40DC2626)           // for shadows

// Gauge colors (more vivid on the red theme)
val GaugeGreen = Color(0xFF22C55E)
val GaugeYellow = Color(0xFFEAB308)
val GaugeOrange = Color(0xFFF97316)
val GaugeRed = Color(0xFFDC2626)

fun gaugeColor(percent: Float): Color = when {
    percent < 50f -> GaugeGreen
    percent < 75f -> GaugeYellow
    percent < 90f -> GaugeOrange
    else -> GaugeRed
}
