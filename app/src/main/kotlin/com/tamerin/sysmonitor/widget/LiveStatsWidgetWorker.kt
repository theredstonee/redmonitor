package com.tamerin.sysmonitor.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tamerin.sysmonitor.data.BatteryReader
import com.tamerin.sysmonitor.data.CpuReader
import com.tamerin.sysmonitor.data.MemoryReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Refreshes the Live-Stats home-screen widget. Runs every 15 minutes
 * (the WorkManager periodic minimum) plus on-demand when the receiver
 * fires (widget added, boot completed, app launched).
 */
class LiveStatsWidgetWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        // Prime sampler so the very first widget render has real values
        CpuReader.read(ctx, "widget")
        val cpu = CpuReader.read(ctx, "widget").totalPercent
        val ram = MemoryReader.readRam(ctx).percent
        val tempC = runCatching { BatteryReader.read(ctx).temperatureC }.getOrDefault(-1f)

        val manager = GlanceAppWidgetManager(ctx)
        val widget = LiveStatsWidget()
        manager.getGlanceIds(LiveStatsWidget::class.java).forEach { id ->
            updateAppWidgetState(ctx, id) { prefs ->
                prefs[LiveStatsWidget.CPU_PCT] = cpu
                prefs[LiveStatsWidget.RAM_PCT] = ram
                prefs[LiveStatsWidget.BATT_TEMP] = tempC
                prefs[LiveStatsWidget.UPDATED_AT] = System.currentTimeMillis()
            }
            widget.update(ctx, id)
        }
        Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "live_stats_widget_periodic"
        private const val ONE_SHOT_NAME = "live_stats_widget_oneshot"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<LiveStatsWidgetWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(Constraints.Builder().build()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun scheduleOneShot(context: Context) {
            val request = OneTimeWorkRequestBuilder<LiveStatsWidgetWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
        }
    }
}
