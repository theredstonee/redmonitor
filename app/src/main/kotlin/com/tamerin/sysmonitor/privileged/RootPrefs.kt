package com.tamerin.sysmonitor.privileged

import android.content.Context

/**
 * Opt-in für das [RootBackend]. Standard aus: ein `su`-Aufruf zeigt den Dialog des
 * Root-Managers, das soll nur nach bewusster Entscheidung in den Einstellungen passieren.
 */
object RootPrefs {
    private const val FILE = "root_prefs"
    private const val KEY_ENABLED = "enabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
}
