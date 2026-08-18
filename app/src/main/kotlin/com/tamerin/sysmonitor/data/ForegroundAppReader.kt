package com.tamerin.sysmonitor.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

/**
 * Ermittelt, welche App gerade im Vordergrund laeuft.
 *
 * Seit Android 5 gibt es dafuer keine offene API mehr — `getRunningTasks` liefert nur
 * noch die eigene App. Der einzige Weg ohne Root ist `UsageStatsManager`, und der
 * braucht die Sonderberechtigung „Nutzungsdaten-Zugriff". Ohne sie liefert [current]
 * schlicht null, und alles was darauf aufbaut muss damit klarkommen.
 */
object ForegroundAppReader {

    /** Fenster, in dem nach dem letzten Vordergrund-Wechsel gesucht wird. */
    private const val LOOKBACK_MS = 60_000L

    fun hasPermission(context: Context): Boolean = runCatching {
        val aom = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = aom.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** Systemeinstellungen fuer den Nutzungsdaten-Zugriff. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Paketname der aktuellen Vordergrund-App, oder null wenn unbekannt.
     *
     * Ausgewertet werden Events statt `queryUsageStats`, weil letzteres nur
     * aggregierte Nutzungszeiten liefert und bei kurzen Wechseln danebenliegt.
     */
    fun current(context: Context): String? = runCatching {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - LOOKBACK_MS, now) ?: return null
        val event = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                last = event.packageName
            }
        }
        last
    }.getOrNull()
}
