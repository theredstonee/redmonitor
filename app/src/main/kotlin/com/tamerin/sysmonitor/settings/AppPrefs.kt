package com.tamerin.sysmonitor.settings

import android.content.Context

enum class HapticIntensity(val key: String, val label: String) {
    WEAK("weak", "Schwach"),
    MEDIUM("medium", "Mittel"),
    STRONG("strong", "Stark")
}

object AppPrefs {
    private const val PREFS = "app_prefs"
    private const val K_HAPTICS = "haptic_feedback"
    private const val K_HAPTIC_INTENSITY = "haptic_intensity"

    fun hapticFeedbackEnabled(context: Context): Boolean =
        prefs(context).getBoolean(K_HAPTICS, true)

    fun setHapticFeedbackEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_HAPTICS, value).apply()
    }

    fun hapticIntensity(context: Context): HapticIntensity {
        val key = prefs(context).getString(K_HAPTIC_INTENSITY, HapticIntensity.MEDIUM.key)
        return HapticIntensity.values().firstOrNull { it.key == key } ?: HapticIntensity.MEDIUM
    }

    fun setHapticIntensity(context: Context, intensity: HapticIntensity) {
        prefs(context).edit().putString(K_HAPTIC_INTENSITY, intensity.key).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
