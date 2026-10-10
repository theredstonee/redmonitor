package com.tamerin.sysmonitor.privileged

import android.content.Context
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.data.ShizukuHelper.CmdResult

/**
 * [PrivilegedShell] über die externe Shizuku-App. Dünne Brücke: die eigentliche
 * Logik (State-Maschine, `newProcess` per Reflection) bleibt in [ShizukuHelper],
 * damit dessen Shizuku-spezifische API (`state`, Permission-Listener,
 * `autoGrantAllPermissions` …) unverändert weiterlebt.
 */
object ShizukuBackend : PrivilegedShell {

    override val kind = PrivilegedBackend.SHIZUKU

    // Nur der REINE Shizuku-Status — sonst würde eine ADB-Verbindung dieses Backend
    // fälschlich als bereit melden und PrivilegedManager würde Shizuku statt ADB wählen.
    override fun isReady(context: Context): Boolean =
        ShizukuHelper.shizukuState(context) == ShizukuHelper.State.Ready

    override fun exec(context: Context, cmd: Array<String>): CmdResult =
        ShizukuHelper.execDirect(cmd)
}
