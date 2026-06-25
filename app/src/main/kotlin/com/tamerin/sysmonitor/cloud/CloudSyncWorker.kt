package com.tamerin.sysmonitor.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class CloudSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!CloudPrefs.isEnabled(context)) return Result.success()

        // 1) Heartbeat (kleine, billige Anonymous-Telemetrie)
        runCatching {
            val r = BackendClient.heartbeat(context)
            if (r.ok) CloudPrefs.setLastHeartbeatMs(context, System.currentTimeMillis())
        }

        // 2) Backup nur einmal alle 24h (auch wenn Worker öfter feuert)
        val last = CloudPrefs.lastBackupMs(context)
        if (System.currentTimeMillis() - last >= TimeUnit.HOURS.toMillis(20)) {
            runCatching {
                val plaintext = BackupSerializer.collect(context)
                val fp = DeviceIdProvider.hardwareFingerprint(context)
                val encrypted = BackupCrypto.encrypt(plaintext, fp)
                val r = BackendClient.uploadBackup(context, encrypted)
                if (r.ok) CloudPrefs.setLastBackupMs(context, System.currentTimeMillis())
            }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "redmonitor_cloud_sync"

        /** Idempotent — sicher mehrfach aufrufbar. */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = PeriodicWorkRequestBuilder<CloudSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
