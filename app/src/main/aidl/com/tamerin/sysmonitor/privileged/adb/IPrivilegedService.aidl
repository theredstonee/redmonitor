package com.tamerin.sysmonitor.privileged.adb;

import com.tamerin.sysmonitor.privileged.adb.ShellResult;

/**
 * Binder-Schnittstelle des privilegierten Dienstes, der als `shell`-User (UID 2000)
 * über `app_process` läuft. Die App bindet diesen Dienst und ruft die Methoden per
 * Binder-IPC auf — SELinux-freundlich (normale Binder-Transaktion), im Gegensatz zu
 * cross-domain Sockets.
 */
interface IPrivilegedService {

    /** Führt ein Kommando (argv) als shell-User aus und liefert stdout/stderr/exit. */
    ShellResult exec(in String[] cmd);

    /** PID des laufenden Dienst-Prozesses (Diagnose). */
    int getPid();

    /** UID des Dienst-Prozesses — muss 2000 (shell) sein. */
    int getUid();

    /** Beendet den Dienst-Prozess sauber. */
    void destroy();
}
