package com.tamerin.sysmonitor.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val state = UpdateChecker.check(
            applicationContext,
            UpdatePrefs.includePrerelease(applicationContext)
        )
        UpdatePrefs.setLastCheckMs(applicationContext, System.currentTimeMillis())
        if (state.error != null) return Result.retry()

        val release = state.latest
        if (state.hasUpdate && release != null) {
            UpdatePrefs.setLatestSeenVersion(applicationContext, release.versionName)
            // Only notify if user hasn't dismissed this version
            if (UpdatePrefs.dismissedVersion(applicationContext) != release.versionName) {
                UpdateNotifier.show(applicationContext, release)
            }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "redmonitor_update_check"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
