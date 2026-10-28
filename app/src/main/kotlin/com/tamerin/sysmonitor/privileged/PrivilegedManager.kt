package com.tamerin.sysmonitor.privileged

import android.content.Context
import com.tamerin.sysmonitor.data.ShizukuHelper.CmdResult

/**
 * Wählt zur Laufzeit das beste verfügbare privilegierte Backend und ist der
 * einzige Ausführungspunkt für privilegierte Kommandos. Die `ShizukuHelper`-
 * Fassade (`runCommand`/`runShell`/`readFile`/`dumpsysBattery`) delegiert hierher,
 * wodurch **alle bestehenden Call-Sites ohne Änderung** backend-agnostisch werden.
 *
 * Priorität: [RootBackend] (falls aktiviert + gewährt) → [ShizukuBackend] (falls bereit)
 * → [AdbBackend] (falls verbunden). Root zuerst, weil der Nutzer es ausdrücklich
 * eingeschaltet hat und es keinen fremden Dienst braucht, der wegsterben kann.
 */
object PrivilegedManager {

    /** Reihenfolge = Priorität. */
    private val backends: List<PrivilegedShell> = listOf(RootBackend, ShizukuBackend, AdbBackend)

    /** Das aktuell nutzbare Backend, oder `null` wenn keins bereit ist. */
    fun active(context: Context): PrivilegedShell? =
        backends.firstOrNull { it.isReady(context) }

    /** Welches Backend gerade bedient — für Diagnose/UI. */
    fun activeKind(context: Context): PrivilegedBackend? = active(context)?.kind

    /** true, wenn irgendein privilegiertes Backend bereit ist. */
    fun isAnyReady(context: Context): Boolean = active(context) != null

    /** Führt ein Kommando über das aktive Backend aus. */
    fun exec(context: Context, cmd: Array<String>): CmdResult =
        active(context)?.exec(context, cmd)
            ?: CmdResult(-1, "", "Kein privilegiertes Backend bereit (Root/Shizuku/ADB)")
}
