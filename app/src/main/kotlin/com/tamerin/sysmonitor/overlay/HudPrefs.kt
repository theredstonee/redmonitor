package com.tamerin.sysmonitor.overlay

import android.content.Context
import androidx.compose.ui.graphics.Color

enum class HudMetric(val key: String, val label: String) {
    CPU_PERCENT("cpu", "CPU %"),
    PER_CORE("per_core", "Pro-Core-Balken"),
    CPU_TEMP("cpu_temp", "CPU/SoC-Temperatur"),
    RAM_PERCENT("ram", "RAM %"),
    BATTERY("batt", "Akku % + Watt"),
    NETWORK("net", "Netz Down/Up"),
    FPS("fps", "FPS"),
    CLOCK("clock", "Uhrzeit + Uptime")
}

enum class HudSize(val scale: Float, val label: String) {
    SMALL(0.85f, "Klein"),
    MEDIUM(1.0f, "Mittel"),
    LARGE(1.25f, "Groß")
}

enum class HudColor(val hex: Long, val label: String) {
    BRAND_RED(0xFFDC2626, "Brand-Rot"),
    WHITE(0xFFFFFFFF, "Weiß"),
    GREEN(0xFF22C55E, "Grün"),
    CYAN(0xFF06B6D4, "Cyan"),
    AMBER(0xFFF59E0B, "Bernstein");

    fun composeColor(): Color = Color(hex)
}

data class HudConfig(
    val enabledMetrics: Set<HudMetric>,
    val size: HudSize,
    val opacity: Float,        // 0.3 .. 1.0
    val color: HudColor,
    val edgeSnap: Boolean,
    val x: Int,
    val y: Int
) {
    companion object {
        val DEFAULT = HudConfig(
            enabledMetrics = setOf(HudMetric.CPU_PERCENT, HudMetric.RAM_PERCENT, HudMetric.BATTERY, HudMetric.CPU_TEMP),
            size = HudSize.MEDIUM,
            opacity = 0.8f,
            color = HudColor.BRAND_RED,
            edgeSnap = true,
            x = 16,
            y = 80
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
            y = sp.getInt(K_Y, 80)
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
        }.apply()
    }

    fun savePosition(context: Context, x: Int, y: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(K_X, x).putInt(K_Y, y).apply()
    }
}
