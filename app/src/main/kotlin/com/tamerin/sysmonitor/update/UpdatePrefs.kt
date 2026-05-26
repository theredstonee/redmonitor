package com.tamerin.sysmonitor.update

import android.content.Context

object UpdatePrefs {
    private const val PREFS = "update_prefs"
    private const val K_INCLUDE_PRE = "include_prerelease"
    private const val K_LAST_CHECK = "last_check_ms"
    private const val K_LATEST_SEEN = "latest_seen_version"
    private const val K_DISMISSED = "dismissed_version"
    private const val K_LAST_RESULT = "last_result_version"
    private const val K_NOTIFY_ENABLED = "notify_enabled"

    fun includePrerelease(context: Context): Boolean =
        prefs(context).getBoolean(K_INCLUDE_PRE, false)

    fun setIncludePrerelease(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_INCLUDE_PRE, value).apply()
    }

    fun lastCheckMs(context: Context): Long =
        prefs(context).getLong(K_LAST_CHECK, 0)

    fun setLastCheckMs(context: Context, ms: Long) {
        prefs(context).edit().putLong(K_LAST_CHECK, ms).apply()
    }

    fun latestSeenVersion(context: Context): String? =
        prefs(context).getString(K_LATEST_SEEN, null)

    fun setLatestSeenVersion(context: Context, version: String) {
        prefs(context).edit().putString(K_LATEST_SEEN, version).apply()
    }

    fun dismissedVersion(context: Context): String? =
        prefs(context).getString(K_DISMISSED, null)

    fun dismissVersion(context: Context, version: String) {
        prefs(context).edit().putString(K_DISMISSED, version).apply()
    }

    fun lastResultVersion(context: Context): String? =
        prefs(context).getString(K_LAST_RESULT, null)

    fun setLastResultVersion(context: Context, version: String?) {
        prefs(context).edit().putString(K_LAST_RESULT, version).apply()
    }

    fun notificationsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(K_NOTIFY_ENABLED, true)

    fun setNotificationsEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_NOTIFY_ENABLED, value).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
