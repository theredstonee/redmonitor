package com.tamerin.sysmonitor.cloud

import android.content.Context

object CloudPrefs {
    private const val PREFS = "cloud_prefs"
    private const val K_ENABLED = "cloud_backup_enabled"
    private const val K_LAST_HEARTBEAT = "last_heartbeat_ms"
    private const val K_LAST_BACKUP = "last_backup_ms"
    private const val K_RESTORE_CHECKED = "first_launch_restore_checked"
    private const val K_RESTORE_DECLINED_VERSION = "restore_declined_version"

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(K_ENABLED, true)  // default on — anonymes Heartbeat ist OK

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_ENABLED, value).apply()
    }

    fun lastHeartbeatMs(context: Context): Long = prefs(context).getLong(K_LAST_HEARTBEAT, 0L)
    fun setLastHeartbeatMs(context: Context, v: Long) {
        prefs(context).edit().putLong(K_LAST_HEARTBEAT, v).apply()
    }

    fun lastBackupMs(context: Context): Long = prefs(context).getLong(K_LAST_BACKUP, 0L)
    fun setLastBackupMs(context: Context, v: Long) {
        prefs(context).edit().putLong(K_LAST_BACKUP, v).apply()
    }

    fun restoreChecked(context: Context): Boolean = prefs(context).getBoolean(K_RESTORE_CHECKED, false)
    fun setRestoreChecked(context: Context, v: Boolean) {
        prefs(context).edit().putBoolean(K_RESTORE_CHECKED, v).apply()
    }

    fun restoreDeclinedVersion(context: Context): String? =
        prefs(context).getString(K_RESTORE_DECLINED_VERSION, null)

    fun setRestoreDeclinedVersion(context: Context, v: String) {
        prefs(context).edit().putString(K_RESTORE_DECLINED_VERSION, v).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
