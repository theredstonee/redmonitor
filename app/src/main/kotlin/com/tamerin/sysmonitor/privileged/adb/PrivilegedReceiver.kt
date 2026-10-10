package com.tamerin.sysmonitor.privileged.adb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Empfängt den vom app_process-Starter gereichten Binder (expliziter Broadcast an das
 * App-Package) und übergibt ihn an den [AdbConnectionManager]. Muss `exported=true`
 * sein, weil der Starter als `shell`-User (anderer Prozess/UID) sendet; die Echtheit
 * wird im Manager über die Dienst-UID (== 2000) geprüft.
 */
class PrivilegedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AdbConst.BINDER_ACTION) return
        val extras = intent.extras ?: return
        val binder = extras.getBinder(AdbConst.EXTRA_BINDER) ?: return
        AdbConnectionManager.onBinderReceived(binder)
    }
}
