package com.tamerin.sysmonitor.data.battery

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Background sampler — runs at the WorkManager-minimum 15-min interval and
 * appends one row to [BatteryHistoryDatabase]. Combined with foreground
 * sampling (whenever BatteryScreen / HUD are open), that's plenty of data
 * for stable median drain rates within a day or two.
 */
class BatterySamplerWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        runCatching { BatteryHistoryTracker.sample(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "battery_sampler_periodic"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BatterySamplerWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(Constraints.Builder().build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
