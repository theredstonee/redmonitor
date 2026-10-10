package com.tamerin.sysmonitor.privileged.adb

import android.content.Context

/**
 * Erzeugt die konkreten Befehle für den **PC-Weg**. Der APK-Pfad wird zur Laufzeit
 * aus dem eigenen `applicationInfo` gelesen — dafür ist KEINE Shell nötig.
 */
object AdbSetup {

    /** Pfad der installierten Basis-APK (enthält die Starter-Klasse). */
    fun apkPath(context: Context): String = context.applicationInfo.sourceDir

    /**
     * Der eine Befehl, den der Nutzer am PC (`adb`) ausführt, um den privilegierten
     * Dienst zu starten. Der PC-`adb` ist bereits mit dem Gerät gekoppelt — kein
     * eigenes Pairing nötig.
     */
    fun buildPcCommand(context: Context): String {
        val apk = apkPath(context)
        return "adb shell CLASSPATH=$apk app_process /system/bin " +
            "--nice-name=${AdbConst.PROCESS_NICE_NAME} ${AdbConst.STARTER_CLASS}"
    }

    /**
     * Variante ohne den `adb`-Prefix — falls der Nutzer schon in einer `adb shell`
     * sitzt oder es per Wireless-Debugging-Terminal (Schritt 5) ausführt.
     */
    fun buildShellCommand(context: Context): String {
        val apk = apkPath(context)
        return "CLASSPATH=$apk app_process /system/bin " +
            "--nice-name=${AdbConst.PROCESS_NICE_NAME} ${AdbConst.STARTER_CLASS}"
    }
}
