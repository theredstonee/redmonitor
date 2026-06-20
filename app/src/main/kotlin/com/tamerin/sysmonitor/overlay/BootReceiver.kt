package com.tamerin.sysmonitor.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.tamerin.sysmonitor.settings.AppPrefs

/**
 * Startet den OverlayService nach dem Boot (oder nach App-Update / Quickboot),
 * wenn der User Autostart aktiviert UND Overlay-Permission erteilt hat.
 *
 * Wichtig: OEM-Killer (MIUI/HyperOS, OneUI, OPPO etc.) blocken BOOT_COMPLETED
 * auch wenn die Permission steht. Dafür gibt es den OEM-Setup-Screen, der den
 * User in die jeweilige Autostart-Liste schickt.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val accepted = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!accepted) return

        if (!AppPrefs.isOverlayAutostartEnabled(context)) return

        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
        if (!hasOverlay) return

        runCatching { OverlayService.start(context) }
    }
}
