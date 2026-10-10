package com.tamerin.sysmonitor.privileged.adb

import android.content.Intent
import android.os.Bundle
import android.os.IBinder

/**
 * Übergibt den [PrivilegedService]-Binder vom app_process-Starter (UID 2000, KEIN
 * Android-Context) an die App. Der Starter hat keinen Context, deshalb wird der
 * `ActivityManager` über `ServiceManager` + Reflection geholt und ein expliziter
 * Broadcast an das App-Package geschickt, der den Binder als Bundle-Extra trägt.
 *
 * ⚠️ DEVICE-VERIFY — DIES IST DER ROM-/API-EMPFINDLICHE TEIL (Android 29–35):
 *  - Die Signatur von `broadcastIntent`/`broadcastIntentWithFeature` ändert sich
 *    zwischen API-Leveln (Anzahl/Reihenfolge der int/Bundle/String[]-Parameter).
 *    Deshalb wird die Methode per Namens-Scan gesucht und die Argumente aus den
 *    Parametertypen befüllt — auf echtem Gerät prüfen und ggf. je API festnageln.
 *  - Hintergrund-Broadcast-Limits können die Zustellung an einen nicht laufenden
 *    Prozess drosseln; der Starter wiederholt darum (siehe [Starter]).
 *
 * Der Intent/Bundle-Aufbau selbst (unten) ist stabil und wiederverwendbar.
 */
object BinderHandoff {

    fun sendToApp(service: IBinder): Boolean {
        val extras = Bundle().apply {
            putBinder(AdbConst.EXTRA_BINDER, service)
            putInt(AdbConst.EXTRA_VERSION, AdbConst.SERVER_VERSION)
        }
        val intent = Intent(AdbConst.BINDER_ACTION).apply {
            setPackage(AdbConst.APP_PKG)
            putExtras(extras)
            // FLAG_RECEIVER_FOREGROUND, damit der Broadcast nicht in der Queue verhungert.
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        return runCatching { broadcastReflected(intent) }.getOrDefault(false)
    }

    private fun broadcastReflected(intent: Intent): Boolean {
        val am = activityManager() ?: return false
        val method = am.javaClass.methods.firstOrNull {
            it.name == "broadcastIntentWithFeature" || it.name == "broadcastIntent"
        } ?: return false

        val types = method.parameterTypes
        val args = arrayOfNulls<Any?>(types.size)
        for (i in types.indices) {
            val t = types[i]
            args[i] = when {
                t == Intent::class.java -> intent
                t == Boolean::class.javaPrimitiveType -> false
                // int-Parameter (resultCode/appOp/userId/flags): 0 als konservativer
                // Default. DEVICE-VERIFY: manche API-Level erwarten appOp = -1 (OP_NONE)
                // bzw. userId = -2 (USER_CURRENT) — hier ggf. gezielt setzen.
                t == Int::class.javaPrimitiveType -> 0
                // IApplicationThread, IIntentReceiver, String, String[], Bundle → null
                else -> null
            }
        }
        method.isAccessible = true
        method.invoke(am, *args)
        return true
    }

    /** Holt das IActivityManager-Binder-Interface ohne Context (minSdk 29 ⇒ Stub existiert). */
    private fun activityManager(): Any? = runCatching {
        val sm = Class.forName("android.os.ServiceManager")
        val binder = sm.getMethod("getService", String::class.java)
            .invoke(null, "activity") as IBinder
        val stub = Class.forName("android.app.IActivityManager\$Stub")
        stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
    }.getOrNull()
}
