package com.tamerin.sysmonitor.privileged.adb

import android.os.Parcelable
import com.tamerin.sysmonitor.data.ShizukuHelper.CmdResult
import kotlinx.parcelize.Parcelize

/**
 * Über Binder transportierbares Ergebnis eines Shell-Kommandos. Bewusst blockierend
 * (stdout/stderr komplett) für den ersten Wurf; Streaming über ParcelFileDescriptor
 * kann später ergänzt werden. Wird an der Fassade in [CmdResult] übersetzt.
 */
@Parcelize
data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) : Parcelable {
    fun toCmdResult(): CmdResult = CmdResult(exitCode, stdout, stderr)
}
