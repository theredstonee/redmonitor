package com.tamerin.sysmonitor.widget

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.data.CpuReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Quick-Settings-Tile mit live aktualisierter CPU-Last in %.
 * Aktiv nur wenn der User die Tile aufgeklappt hat — Android schickt
 * onStartListening, wir pollen dann CpuReader. Bei onStopListening wieder aus.
 */
class CpuLiveTileService : TileService() {

    private var scope: CoroutineScope? = null
    private var pollJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        // Prime sampler
        s.launch { runCatching { CpuReader.read(this@CpuLiveTileService, "tile") } }
        pollJob = s.launch {
            while (true) {
                val pct = runCatching {
                    CpuReader.read(this@CpuLiveTileService, "tile").totalPercent
                }.getOrDefault(-1f)
                updateTile(pct)
                delay(1500)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        pollJob?.cancel()
        scope?.cancel()
        scope = null
    }

    override fun onClick() {
        super.onClick()
        // Tap auf Tile → öffnet App
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    android.app.PendingIntent.getActivity(
                        this, 0, intent,
                        android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun updateTile(cpuPct: Float) {
        val tile = qsTile ?: return
        tile.label = "CPU"
        tile.contentDescription = "CPU-Auslastung"
        tile.subtitle = if (cpuPct < 0) "—" else "${cpuPct.toInt()}%"
        tile.state = when {
            cpuPct < 0 -> Tile.STATE_INACTIVE
            cpuPct < 60 -> Tile.STATE_ACTIVE
            else -> Tile.STATE_ACTIVE
        }
        tile.icon = Icon.createWithResource(this, R.mipmap.ic_launcher)
        tile.updateTile()
    }
}
