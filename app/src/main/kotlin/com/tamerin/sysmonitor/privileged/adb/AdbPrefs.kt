package com.tamerin.sysmonitor.privileged.adb

import android.content.Context

/**
 * Persistente Einstellungen für den eigenen ADB-Zugang (SharedPreferences, wie die
 * übrigen *Prefs-Objects im Projekt). Merkt, ob der Nutzer den ADB-Weg aktiviert hat,
 * damit die App beim Start automatisch wieder verbinden kann.
 */
object AdbPrefs {
    private const val FILE = "adb_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOST = "host"
    private const val KEY_PORT = "port"

    const val DEFAULT_HOST = "127.0.0.1"
    const val DEFAULT_PORT = 5555

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()

    fun host(context: Context): String =
        prefs(context).getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST

    fun port(context: Context): Int = prefs(context).getInt(KEY_PORT, DEFAULT_PORT)

    fun setEndpoint(context: Context, host: String, port: Int) =
        prefs(context).edit().putString(KEY_HOST, host).putInt(KEY_PORT, port).apply()
}
