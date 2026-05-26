package com.tamerin.sysmonitor.settings

import android.content.Context

object AppPrefs {
    private const val PREFS = "app_prefs"
    private const val K_HAPTICS = "haptic_feedback"

    fun hapticFeedbackEnabled(context: Context): Boolean =
        prefs(context).getBoolean(K_HAPTICS, true)

    fun setHapticFeedbackEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_HAPTICS, value).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
