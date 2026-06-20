package com.tamerin.sysmonitor.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.tamerin.sysmonitor.settings.AppPrefs

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = AccentSoft,
    tertiary = AccentDim,
    background = BgDark,
    onBackground = OnSurface,
    surface = SurfaceDarkSolid,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceMuted,
    outline = DividerWhite,
    outlineVariant = DividerWhite,
    error = GaugeRed
)

@Composable
fun SysMonitorTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDynamic = supportsDynamic && AppPrefs.isMaterialYouEnabled(context)
    val scheme = if (useDynamic) {
        // Dynamic dark scheme based on user's wallpaper colors (Android 12+).
        // We still keep our custom accent for hot-path stats (Accent/AccentSoft singletons
        // are used directly in many Composables) — only Material's primary/secondary tokens
        // get the wallpaper hue.
        dynamicDarkColorScheme(context).copy(error = GaugeRed)
    } else {
        DarkColors
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        content = content
    )
}
