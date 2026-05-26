package com.tamerin.sysmonitor.benchmark

import android.content.Context
import com.tamerin.sysmonitor.data.ShizukuHelper

/**
 * Tells Android's scheduler that our process is important. None of these requires root —
 * but they DO require Shizuku for the shell commands. Falls back to no-op if Shizuku missing.
 *
 * On Samsung especially, Adaptive Battery silently throttles foreground apps to ~30% of cores
 * unless they're in the `active` standby bucket and on the doze whitelist.
 */
object PerformanceBooster {

    fun boost(context: Context) {
        if (ShizukuHelper.state(context) != ShizukuHelper.State.Ready) return
        val pkg = context.packageName
        runCatching { ShizukuHelper.runCommand(context, "am", "set-standby-bucket", pkg, "active") }
        runCatching { ShizukuHelper.runCommand(context, "cmd", "deviceidle", "whitelist", "+$pkg") }
        // Disable battery saver during bench (best-effort)
        runCatching { ShizukuHelper.runShell(context, "settings put global low_power 0") }
    }

    fun unboost(context: Context) {
        // We intentionally do NOT remove from doze whitelist — keep what user set.
        // Standby bucket falls back naturally over time.
    }
}
