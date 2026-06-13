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
    private const val K_RATED = "trustpilot_rated"
    private const val K_RATE_LAST_SHOWN = "trustpilot_last_shown"
    private const val K_LAUNCH_COUNT = "launch_count"
    private const val K_OEM_ONBOARDING_DONE = "oem_onboarding_done"
    private const val RATE_INTERVAL_MS = 3L * 24L * 3600L * 1000L

    fun hasRated(context: Context): Boolean =
        prefs(context).getBoolean(K_RATED, false)

    fun setHasRated(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_RATED, value).apply()
    }

    fun launchCount(context: Context): Int =
        prefs(context).getInt(K_LAUNCH_COUNT, 0)

    fun incrementLaunchCount(context: Context): Int {
        val p = prefs(context)
        val n = p.getInt(K_LAUNCH_COUNT, 0) + 1
        p.edit().putInt(K_LAUNCH_COUNT, n).apply()
        return n
    }

    fun markRatePromptShown(context: Context) {
        prefs(context).edit().putLong(K_RATE_LAST_SHOWN, System.currentTimeMillis()).apply()
    }

    /** Modal at app start: every 3 days if not yet rated, starting from 2nd launch. */
    fun shouldShowRatePrompt(context: Context): Boolean {
        if (hasRated(context)) return false
        if (launchCount(context) < 2) return false
        val lastShown = prefs(context).getLong(K_RATE_LAST_SHOWN, 0L)
        return System.currentTimeMillis() - lastShown >= RATE_INTERVAL_MS
    }

    fun isOemOnboardingDone(context: Context): Boolean =
        prefs(context).getBoolean(K_OEM_ONBOARDING_DONE, false)

    fun setOemOnboardingDone(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_OEM_ONBOARDING_DONE, value).apply()
    }

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
