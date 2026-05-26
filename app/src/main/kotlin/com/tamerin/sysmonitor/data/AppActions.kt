package com.tamerin.sysmonitor.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object AppActions {

    /** Open the system app-info screen for this package (no Shizuku needed). */
    fun openAppInfo(context: Context, pkg: String) {
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun forceStop(context: Context, pkg: String): ShizukuHelper.CmdResult =
        ShizukuHelper.runCommand(context, "am", "force-stop", pkg)

    /** Softer than force-stop — only kills cached/empty processes. */
    fun softKill(context: Context, pkg: String): ShizukuHelper.CmdResult =
        ShizukuHelper.runCommand(context, "am", "kill", pkg)

    fun isAppDisabled(context: Context, pkg: String): Boolean {
        val res = ShizukuHelper.runCommand(context, "pm", "dump", pkg)
        return res.stdout.lineSequence().any {
            it.trim().startsWith("enabled=") && it.contains("false")
        }
    }

    fun isAppSuspended(context: Context, pkg: String): Boolean {
        val res = ShizukuHelper.runCommand(context, "pm", "dump", pkg)
        return res.stdout.lineSequence().any {
            it.trim().contains("suspended=true")
        }
    }

    fun disableApp(context: Context, pkg: String): ShizukuHelper.CmdResult =
        ShizukuHelper.runCommand(context, "pm", "disable-user", "--user", "0", pkg)

    fun enableApp(context: Context, pkg: String): ShizukuHelper.CmdResult =
        ShizukuHelper.runCommand(context, "pm", "enable", pkg)

    /** Suspend app — works on Android 10+ via shell. More reliable than disable on Samsung. */
    fun suspendApp(context: Context, pkg: String): ShizukuHelper.CmdResult =
        ShizukuHelper.runCommand(context, "pm", "suspend", pkg)

    fun unsuspendApp(context: Context, pkg: String): ShizukuHelper.CmdResult =
        ShizukuHelper.runCommand(context, "pm", "unsuspend", pkg)

    /** Sets app standby bucket — `never` = app never runs in background. */
    fun setStandbyBucket(context: Context, pkg: String, bucket: String): ShizukuHelper.CmdResult =
        ShizukuHelper.runCommand(context, "am", "set-standby-bucket", pkg, bucket)

    /** Forces the app's uid into IDLE state immediately (releases wakelocks, jobs canceled). */
    fun makeUidIdle(context: Context, pkg: String): ShizukuHelper.CmdResult =
        ShizukuHelper.runCommand(context, "am", "make-uid-idle", pkg)

    /**
     * "Deep-Freeze" combo: force-stop + suspend + bucket never + make-uid-idle.
     * Strongest stop you can do as shell user without root.
     */
    fun deepFreeze(context: Context, pkg: String): List<Pair<String, ShizukuHelper.CmdResult>> {
        return listOf(
            "Force-Stop" to forceStop(context, pkg),
            "Bucket: never" to setStandbyBucket(context, pkg, "never"),
            "UID-Idle" to makeUidIdle(context, pkg),
            "Suspend" to suspendApp(context, pkg)
        )
    }

    fun unfreeze(context: Context, pkg: String): List<Pair<String, ShizukuHelper.CmdResult>> {
        return listOf(
            "Unsuspend" to unsuspendApp(context, pkg),
            "Bucket: active" to setStandbyBucket(context, pkg, "active")
        )
    }

    /** Clear only the app's cache (safe). */
    fun clearCache(context: Context, pkg: String): ShizukuHelper.CmdResult =
        ShizukuHelper.runCommand(context, "pm", "clear", "--cache-only", pkg)

    /** Clear ALL app data (full reset — destructive, like fresh install). */
    fun clearAllData(context: Context, pkg: String): ShizukuHelper.CmdResult =
        ShizukuHelper.runCommand(context, "pm", "clear", pkg)

    /**
     * Uninstall the app. For user-installed apps this fully removes it.
     * For system apps this hides it from current user (--user 0).
     */
    fun uninstall(context: Context, pkg: String, isSystem: Boolean): ShizukuHelper.CmdResult {
        return if (isSystem) {
            ShizukuHelper.runCommand(context, "pm", "uninstall", "--user", "0", pkg)
        } else {
            ShizukuHelper.runCommand(context, "pm", "uninstall", pkg)
        }
    }

    /** Force-stop a list of packages in one go. Returns map of pkg → result. */
    fun batchForceStop(context: Context, pkgs: List<String>): Map<String, ShizukuHelper.CmdResult> =
        pkgs.associateWith { forceStop(context, it) }

    // --- AppOps for background restriction ---

    fun setBackgroundAllowed(context: Context, pkg: String, allowed: Boolean): ShizukuHelper.CmdResult {
        val mode = if (allowed) "allow" else "deny"
        return ShizukuHelper.runCommand(
            context, "cmd", "appops", "set", pkg, "RUN_IN_BACKGROUND", mode
        )
    }

    fun setAnyBackgroundAllowed(context: Context, pkg: String, allowed: Boolean): ShizukuHelper.CmdResult {
        val mode = if (allowed) "allow" else "deny"
        return ShizukuHelper.runCommand(
            context, "cmd", "appops", "set", pkg, "RUN_ANY_IN_BACKGROUND", mode
        )
    }

    data class AppOpsState(val runInBackground: String?, val runAnyInBackground: String?)

    fun getAppOpsState(context: Context, pkg: String): AppOpsState {
        val res = ShizukuHelper.runCommand(context, "cmd", "appops", "get", pkg)
        if (!res.ok) return AppOpsState(null, null)
        var run: String? = null
        var any: String? = null
        for (line in res.stdout.lineSequence()) {
            val l = line.trim()
            // Lines look like: "RUN_IN_BACKGROUND: allow"  or  "RUN_IN_BACKGROUND: deny;"
            if (l.startsWith("RUN_IN_BACKGROUND:")) run = l.substringAfter(":").substringBefore(";").trim()
            if (l.startsWith("RUN_ANY_IN_BACKGROUND:")) any = l.substringAfter(":").substringBefore(";").trim()
        }
        return AppOpsState(run, any)
    }

    // --- Boot receivers ---

    data class Receiver(val component: String, val enabled: Boolean)

    /**
     * List BOOT_COMPLETED receivers for a package. Uses `cmd package query-receivers` which is
     * available on Android 10+ as shell user.
     */
    fun listBootReceivers(context: Context, pkg: String): List<Receiver> {
        // First get receivers from dumpsys package
        val dump = ShizukuHelper.runCommand(context, "pm", "dump", pkg)
        if (!dump.ok) return emptyList()
        val receivers = mutableListOf<Receiver>()
        var inReceivers = false
        var currentComponent: String? = null
        for (raw in dump.stdout.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("Receiver Resolver Table:") || line.startsWith("BroadcastReceiver:")) {
                inReceivers = true
                continue
            }
            if (inReceivers && line.startsWith("Service Resolver Table:")) break
            if (!inReceivers) continue
            // Look for android.intent.action.BOOT_COMPLETED in scope, then collect component name
            if (line.contains("android.intent.action.BOOT_COMPLETED") ||
                line.contains("android.intent.action.LOCKED_BOOT_COMPLETED") ||
                line.contains("android.intent.action.QUICKBOOT_POWERON")
            ) {
                // The component name is usually on a nearby line like "abc1234 com.foo.bar/.MyReceiver"
                currentComponent?.let { receivers += Receiver(it, isComponentEnabled(context, it)) }
                currentComponent = null
            }
            // Capture component candidate
            val compMatch = Regex("""([\w.]+)/([\w.${'$'}]+)""").find(line)
            if (compMatch != null && line.contains(pkg)) {
                currentComponent = compMatch.groupValues[1] + "/" + compMatch.groupValues[2]
            }
        }
        return receivers.distinctBy { it.component }
    }

    fun isComponentEnabled(context: Context, component: String): Boolean {
        val res = ShizukuHelper.runCommand(context, "pm", "dump", component.substringBefore("/"))
        // Heuristic — just check if the component appears with state 2 (disabled)
        for (line in res.stdout.lineSequence()) {
            if (line.contains(component) && line.contains("enabled=false")) return false
        }
        return true
    }

    fun toggleComponent(context: Context, component: String, enable: Boolean): ShizukuHelper.CmdResult {
        val verb = if (enable) "enable" else "disable"
        return ShizukuHelper.runCommand(context, "pm", verb, component)
    }
}
