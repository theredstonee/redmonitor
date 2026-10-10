package com.tamerin.sysmonitor.privileged

import android.content.Context
import com.tamerin.sysmonitor.data.ShizukuHelper.CmdResult
import com.tamerin.sysmonitor.privileged.adb.AdbConnectionManager

/**
 * [PrivilegedShell] über einen eigenen, eingebetteten ADB-Zugang. Ergebnis ist
 * derselbe `shell`-User-Kontext (UID 2000) wie bei Shizuku im ADB-Modus, aber ohne
 * die externe Shizuku-App.
 *
 * Zustand und Ausführung liegen im [AdbConnectionManager]: „bereit" heißt, dass der
 * per app_process gestartete [com.tamerin.sysmonitor.privileged.adb.PrivilegedService]-
 * Binder verbunden ist. Solange das nicht steht, wählt [PrivilegedManager] weiterhin
 * Shizuku.
 */
object AdbBackend : PrivilegedShell {

    override val kind = PrivilegedBackend.ADB

    override fun isReady(context: Context): Boolean = AdbConnectionManager.isConnected()

    override fun exec(context: Context, cmd: Array<String>): CmdResult =
        AdbConnectionManager.exec(cmd)
}
