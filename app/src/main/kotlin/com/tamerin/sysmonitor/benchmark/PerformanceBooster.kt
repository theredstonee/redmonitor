package com.tamerin.sysmonitor.benchmark

import android.content.Context
import android.os.Process
import com.tamerin.sysmonitor.data.ShizukuHelper

/**
 * Without root, Android caps unprivileged apps to LITTLE cores via cgroup/cpuset.
 * Shizuku (shell user) can write to /dev/cpuset/top-app/tasks to move our process
 * into the foreground/big-core group. This is the strongest legal boost short of root.
 */
object PerformanceBooster {

    fun boost(context: Context) {
        if (ShizukuHelper.state(context) != ShizukuHelper.State.Ready) return
        val pkg = context.packageName
        val pid = Process.myPid()

        // Standby + Doze + battery saver off
        runCatching { ShizukuHelper.runCommand(context, "am", "set-standby-bucket", pkg, "active") }
        runCatching { ShizukuHelper.runCommand(context, "cmd", "deviceidle", "whitelist", "+$pkg") }
        runCatching { ShizukuHelper.runShell(context, "settings put global low_power 0") }

        // === The important one: cpuset move ===
        // /dev/cpuset/top-app contains all CPU cores (including big.LITTLE big cluster).
        // /dev/cpuset/background contains only little cores. Default for non-foreground.
        // Moving our process + every thread there gives us access to all cores.
        val script = """
            P=$pid
            # Move main process
            echo ${'$'}P > /dev/cpuset/top-app/tasks 2>/dev/null
            # Move all threads (worker threads from Thread() spawn)
            for tid_path in /proc/${'$'}P/task/*; do
                tid=${'$'}(basename ${'$'}tid_path)
                echo ${'$'}tid > /dev/cpuset/top-app/tasks 2>/dev/null
            done
            # Schedtune for Android 10/11 with EAS
            echo ${'$'}P > /dev/stune/top-app/tasks 2>/dev/null
            # Move children into foreground cpuctl too (CPU bandwidth controller)
            echo ${'$'}P > /dev/cpuctl/top-app/tasks 2>/dev/null
        """.trimIndent()
        runCatching { ShizukuHelper.runShell(context, script) }
    }

    fun unboost(context: Context) {
        if (ShizukuHelper.state(context) != ShizukuHelper.State.Ready) return
        // Move back to foreground (normal app group) so we don't permanently hog big cores
        val pid = Process.myPid()
        val script = """
            P=$pid
            echo ${'$'}P > /dev/cpuset/foreground/tasks 2>/dev/null
            for tid_path in /proc/${'$'}P/task/*; do
                tid=${'$'}(basename ${'$'}tid_path)
                echo ${'$'}tid > /dev/cpuset/foreground/tasks 2>/dev/null
            done
        """.trimIndent()
        runCatching { ShizukuHelper.runShell(context, script) }
    }
}
