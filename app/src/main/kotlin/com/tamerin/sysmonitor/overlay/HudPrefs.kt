package com.tamerin.sysmonitor.overlay

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.tamerin.sysmonitor.R

enum class HudMetric(val key: String, @StringRes val labelRes: Int) {
    CPU_PERCENT("cpu", R.string.misc_hud_metric_cpu_percent),
    PER_CORE("per_core", R.string.misc_hud_metric_per_core),
    PER_CORE_DETAIL("per_core_detail", R.string.misc_hud_metric_per_core_detail),
    CPU_FREQ_AVG("cpu_freq", R.string.misc_hud_metric_cpu_freq),
    CPU_TEMP("cpu_temp", R.string.misc_hud_metric_cpu_temp),
    RAM_PERCENT("ram", R.string.misc_hud_metric_ram_percent),
    BATTERY("batt", R.string.misc_hud_metric_battery),
    NETWORK("net", R.string.misc_hud_metric_network),
    FPS("fps", R.string.misc_hud_metric_fps),
    CLOCK("clock", R.string.misc_hud_metric_clock)
}

/**
 * Wann das HUD sichtbar ist. Braucht ausser bei [ALWAYS] den Nutzungsdaten-Zugriff,
 * weil sonst die Vordergrund-App nicht ermittelbar ist.
 */
enum class HudProfileMode(@StringRes val labelRes: Int) {
    ALWAYS(R.string.misc_hud_profile_always),
    ONLY_IN(R.string.misc_hud_profile_only_in),
    EXCEPT_IN(R.string.misc_hud_profile_except_in)
}

enum class HudSize(val scale: Float, @StringRes val labelRes: Int) {
    SMALL(0.85f, R.string.misc_hud_size_small),
    MEDIUM(1.0f, R.string.misc_hud_size_medium),
    LARGE(1.25f, R.string.misc_hud_size_large)
}

enum class HudColor(val hex: Long, @StringRes val labelRes: Int) {
    BRAND_RED(0xFFDC2626, R.string.misc_hud_color_brand_red),
    WHITE(0xFFFFFFFF, R.string.misc_hud_color_white),
    GREEN(0xFF22C55E, R.string.misc_hud_color_green),
    CYAN(0xFF06B6D4, R.string.misc_hud_color_cyan),
    AMBER(0xFFF59E0B, R.string.misc_hud_color_amber);

    fun composeColor(): Color = Color(hex)
}

data class HudConfig(
    val enabledMetrics: Set<HudMetric>,
    val size: HudSize,
    val opacity: Float,        // 0.3 .. 1.0
    val color: HudColor,
    val edgeSnap: Boolean,
    val x: Int,
    val y: Int,
    val profileMode: HudProfileMode,
    val profileApps: Set<String>
) {
    /**
     * Soll das HUD bei dieser Vordergrund-App sichtbar sein?
     * [foregroundPackage] null = unbekannt; dann bleibt das HUD sichtbar, damit ein
     * fehlender Nutzungsdaten-Zugriff es nicht dauerhaft verschwinden laesst.
     */
    fun isVisibleFor(foregroundPackage: String?): Boolean = when (profileMode) {
        HudProfileMode.ALWAYS -> true
        HudProfileMode.ONLY_IN ->
            if (foregroundPackage == null) true else foregroundPackage in profileApps
        HudProfileMode.EXCEPT_IN ->
            if (foregroundPackage == null) true else foregroundPackage !in profileApps
    }

    companion object {
        val DEFAULT = HudConfig(
            enabledMetrics = setOf(HudMetric.CPU_PERCENT, HudMetric.RAM_PERCENT, HudMetric.BATTERY, HudMetric.CPU_TEMP),
            size = HudSize.MEDIUM,
            opacity = 0.8f,
            color = HudColor.BRAND_RED,
            edgeSnap = true,
            x = 16,
            y = 80,
            profileMode = HudProfileMode.ALWAYS,
            profileApps = emptySet()
        )
    }
}

object HudPrefs {
    private const val PREFS = "hud_prefs"
    private const val K_METRICS = "enabled_metrics"
    private const val K_SIZE = "size"
    private const val K_OPACITY = "opacity"
    private const val K_COLOR = "color"
    private const val K_SNAP = "edge_snap"
    private const val K_X = "pos_x"
    private const val K_Y = "pos_y"
    private const val K_PROFILE_MODE = "profile_mode"
    private const val K_PROFILE_APPS = "profile_apps"

    fun load(context: Context): HudConfig {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val metricKeys = sp.getStringSet(K_METRICS, null)
        val metrics = if (metricKeys == null) {
            HudConfig.DEFAULT.enabledMetrics
        } else {
            HudMetric.values().filter { it.key in metricKeys }.toSet()
        }
        return HudConfig(
            enabledMetrics = metrics,
            size = runCatching { HudSize.valueOf(sp.getString(K_SIZE, HudSize.MEDIUM.name)!!) }
                .getOrDefault(HudSize.MEDIUM),
            opacity = sp.getFloat(K_OPACITY, 0.8f),
            color = runCatching { HudColor.valueOf(sp.getString(K_COLOR, HudColor.BRAND_RED.name)!!) }
                .getOrDefault(HudColor.BRAND_RED),
            edgeSnap = sp.getBoolean(K_SNAP, true),
            x = sp.getInt(K_X, 16),
            y = sp.getInt(K_Y, 80),
            profileMode = runCatching {
                HudProfileMode.valueOf(sp.getString(K_PROFILE_MODE, HudProfileMode.ALWAYS.name)!!)
            }.getOrDefault(HudProfileMode.ALWAYS),
            profileApps = sp.getStringSet(K_PROFILE_APPS, null)?.toSet() ?: emptySet()
        )
    }

    fun save(context: Context, config: HudConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putStringSet(K_METRICS, config.enabledMetrics.map { it.key }.toSet())
            putString(K_SIZE, config.size.name)
            putFloat(K_OPACITY, config.opacity)
            putString(K_COLOR, config.color.name)
            putBoolean(K_SNAP, config.edgeSnap)
            putInt(K_X, config.x)
            putInt(K_Y, config.y)
            putString(K_PROFILE_MODE, config.profileMode.name)
            putStringSet(K_PROFILE_APPS, config.profileApps.toSet())
        }.apply()
    }

    fun savePosition(context: Context, x: Int, y: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(K_X, x).putInt(K_Y, y).apply()
    }
}
