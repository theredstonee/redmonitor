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
    private const val K_EASTER_EGG_UNLOCKED = "easter_egg_unlocked"
    private const val K_SNAKE_HIGH_SCORE = "snake_high_score"
    private const val K_ROULETTE_ACK = "roulette_acknowledged"
    private const val K_AUTOSTART_OVERLAY = "autostart_overlay_on_boot"
    private const val K_MATERIAL_YOU = "material_you_dynamic_theme"
    private const val K_ONBOARDING_DONE = "first_launch_onboarding_done"
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

    fun isEasterEggUnlocked(context: Context): Boolean =
        prefs(context).getBoolean(K_EASTER_EGG_UNLOCKED, false)

    fun setEasterEggUnlocked(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_EASTER_EGG_UNLOCKED, value).apply()
    }

    fun snakeHighScore(context: Context): Int =
        prefs(context).getInt(K_SNAKE_HIGH_SCORE, 0)

    fun setSnakeHighScore(context: Context, value: Int) {
        prefs(context).edit().putInt(K_SNAKE_HIGH_SCORE, value).apply()
    }

    fun isRouletteAcknowledged(context: Context): Boolean =
        prefs(context).getBoolean(K_ROULETTE_ACK, false)

    fun setRouletteAcknowledged(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_ROULETTE_ACK, value).apply()
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

    fun isOverlayAutostartEnabled(context: Context): Boolean =
        prefs(context).getBoolean(K_AUTOSTART_OVERLAY, false)

    fun setOverlayAutostartEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_AUTOSTART_OVERLAY, value).apply()
    }

    fun isMaterialYouEnabled(context: Context): Boolean =
        prefs(context).getBoolean(K_MATERIAL_YOU, false)

    fun setMaterialYouEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_MATERIAL_YOU, value).apply()
    }

    fun isFirstLaunchOnboardingDone(context: Context): Boolean =
        prefs(context).getBoolean(K_ONBOARDING_DONE, false)

    fun setFirstLaunchOnboardingDone(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_ONBOARDING_DONE, value).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
