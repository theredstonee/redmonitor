package com.tamerin.sysmonitor.glyph

import android.content.Context

/**
 * Einstellungen des Glyph-Toys. Wie der Rest der App bewusst SharedPreferences,
 * kein DataStore — der Toy-Service liest hier synchron aus einem Worker-Thread.
 */
object GlyphPrefs {
    private const val PREFS = "glyph_prefs"
    private const val K_METRIC = "toy_metric"
    private const val K_BRIGHTNESS = "toy_brightness"
    private const val K_AOD_BRIGHTNESS = "toy_aod_brightness"

    private const val DEFAULT_BRIGHTNESS = 255
    private const val DEFAULT_AOD_BRIGHTNESS = 90

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun metric(context: Context): GlyphMetric =
        GlyphMetric.fromName(prefs(context).getString(K_METRIC, null))

    fun setMetric(context: Context, metric: GlyphMetric) {
        prefs(context).edit().putString(K_METRIC, metric.name).apply()
    }

    fun brightness(context: Context): Int =
        prefs(context).getInt(K_BRIGHTNESS, DEFAULT_BRIGHTNESS).coerceIn(10, 255)

    fun setBrightness(context: Context, value: Int) {
        prefs(context).edit().putInt(K_BRIGHTNESS, value.coerceIn(10, 255)).apply()
    }

    /** Always-On-Display faehrt dunkler, damit es nachts nicht blendet. */
    fun aodBrightness(context: Context): Int =
        prefs(context).getInt(K_AOD_BRIGHTNESS, DEFAULT_AOD_BRIGHTNESS).coerceIn(10, 255)

    fun setAodBrightness(context: Context, value: Int) {
        prefs(context).edit().putInt(K_AOD_BRIGHTNESS, value.coerceIn(10, 255)).apply()
    }
}
