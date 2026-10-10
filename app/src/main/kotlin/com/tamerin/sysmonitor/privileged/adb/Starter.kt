package com.tamerin.sysmonitor.privileged.adb

import android.os.Looper

/**
 * Einstiegspunkt des privilegierten Dienstes für den **PC-Weg**. Wird einmalig per
 * ADB von einem PC gestartet (der PC-`adb` ist bereits mit dem Gerät gekoppelt, es
 * ist also KEIN eigenes Pairing/SPAKE2 nötig):
 *
 * ```
 * adb shell CLASSPATH=<apk-pfad> app_process /system/bin \
 *     --nice-name=com.tamerin.sysmonitor:priv \
 *     com.tamerin.sysmonitor.privileged.adb.Starter
 * ```
 *
 * Der genaue Befehl wird zur Laufzeit von `AdbSetup.buildPcCommand()` erzeugt (mit
 * dem echten APK-Pfad des Geräts) und dem Nutzer angezeigt.
 *
 * Der Prozess läuft danach als eigener `shell`-User-Prozess (UID 2000), hält einen
 * [PrivilegedService]-Binder, übergibt ihn an die App und bleibt bis `destroy()` bzw.
 * bis zum Reboot am Leben (`Looper.loop()`).
 */
object Starter {

    @JvmStatic
    fun main(args: Array<String>) {
        // app_process hat noch keinen Looper — für Binder-Callbacks/Alive-Halten anlegen.
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper()

        val service = PrivilegedService()

        // Binder an die App reichen. DEVICE-VERIFY: die reflektierte Broadcast-Zustellung
        // ist der ROM-/API-empfindliche Teil (siehe BinderHandoff) und wird auf dem Gerät
        // iteriert. Wiederholte Versuche, falls die App noch nicht bereit ist.
        Thread {
            repeat(10) { attempt ->
                val ok = BinderHandoff.sendToApp(service)
                if (ok) return@Thread
                Thread.sleep(500L * (attempt + 1))
            }
        }.start()

        Looper.loop()
    }
}
