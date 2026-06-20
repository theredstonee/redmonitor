package com.tamerin.sysmonitor.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class LiveStatsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LiveStatsWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Kick off a one-shot refresh on every update broadcast (e.g. when a new
        // instance is added or system resends after boot).
        LiveStatsWidgetWorker.scheduleOneShot(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        LiveStatsWidgetWorker.schedulePeriodic(context)
        LiveStatsWidgetWorker.scheduleOneShot(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        LiveStatsWidgetWorker.cancel(context)
    }
}
