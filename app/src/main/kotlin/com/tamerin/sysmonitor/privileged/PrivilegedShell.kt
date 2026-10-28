package com.tamerin.sysmonitor.privileged

import android.content.Context
import com.tamerin.sysmonitor.data.ShizukuHelper.CmdResult

/**
 * Ein privilegierter Shell-Kanal im `shell`-User-Kontext (UID 2000, bei Root UID 0).
 * Implementierungen mit identischem Vertrag:
 *
 *  - [RootBackend]    — `su` auf gerooteten Geräten (Opt-in in den Einstellungen).
 *  - [ShizukuBackend] — über die externe Shizuku-App (Binder → `newProcess`).
 *  - [AdbBackend]     — eigener eingebetteter ADB-Client über Wireless Debugging
 *                       (127.0.0.1), ohne Shizuku-App und ohne Root.
 *
 * Beide liefern dieselben Ergebnisse, damit die ~45 bestehenden Call-Sites, die
 * heute über die `ShizukuHelper`-Fassade laufen, backend-agnostisch werden — ohne
 * dass eine einzige dieser Call-Sites angefasst werden muss. Die Auswahl trifft
 * [PrivilegedManager].
 */
interface PrivilegedShell {

    /** Welches Backend dieser Kanal ist — für Diagnose/UI. */
    val kind: PrivilegedBackend

    /** true, wenn dieser Kanal JETZT sofort Kommandos ausführen kann. */
    fun isReady(context: Context): Boolean

    /**
     * Führt ein Kommando (argv-Form, kein Shell-Parsing) als `shell`-User aus und
     * liefert stdout/stderr/exit. Darf blockieren — Aufrufer sind bereits off-main.
     */
    fun exec(context: Context, cmd: Array<String>): CmdResult
}

/** Verfügbare privilegierte Backends, in Prioritätsreihenfolge von [PrivilegedManager]. */
enum class PrivilegedBackend { ROOT, SHIZUKU, ADB }
