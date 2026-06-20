package com.tamerin.sysmonitor.benchmark

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.tamerin.sysmonitor.BuildConfig
import com.tamerin.sysmonitor.benchmark.db.BenchmarkDatabase
import com.tamerin.sysmonitor.benchmark.db.BenchmarkRun
import com.tamerin.sysmonitor.benchmark.db.BenchmarkSubScore
import com.tamerin.sysmonitor.benchmark.db.BenchmarkType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the benchmark worker thread.
 *
 * Why a service: heavy CPU work survives Activity recreation. The Activity can
 * be paused (back to home), even killed by the OS — the bench keeps running and
 * the result lands in Room.
 *
 * Why foreground: long-running CPU work without a foreground notification is
 * killed by Android's background limits within seconds on Android 8+.
 */
class BenchmarkService : Service() {

    companion object {
        private const val CHANNEL_ID = "bench_run"
        private const val NOTIFICATION_ID = 0xBE7C // arbitrary
        private const val EXTRA_TYPE = "bench_type"
        private const val EXTRA_PHASE_SECONDS = "phase_seconds"

        fun start(context: Context, type: BenchmarkType, phaseSeconds: Int) {
            val intent = Intent(context, BenchmarkService::class.java).apply {
                putExtra(EXTRA_TYPE, type.name)
                putExtra(EXTRA_PHASE_SECONDS, phaseSeconds)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BenchmarkService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var runJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val typeName = intent?.getStringExtra(EXTRA_TYPE) ?: return START_NOT_STICKY
        val type = runCatching { BenchmarkType.valueOf(typeName) }.getOrNull()
            ?: return START_NOT_STICKY
        val phaseSeconds = intent.getIntExtra(EXTRA_PHASE_SECONDS, 30).coerceAtLeast(5)

        ensureChannel()
        startForegroundNotification(type, "Vorbereiten...", 0, 1)

        runJob?.cancel()
        runJob = scope.launch {
            BenchmarkRepository.publish(
                BenchmarkState.Running(type, "Vorbereiten...", 0, 1)
            )
            runCatching { runBenchmark(type, phaseSeconds) }
                .onFailure { e ->
                    BenchmarkRepository.publish(
                        BenchmarkState.Error(type, e.message ?: "unbekannter Fehler")
                    )
                }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private suspend fun runBenchmark(type: BenchmarkType, phaseSeconds: Int) {
        val onProgress = { phase: String, done: Int, total: Int ->
            BenchmarkRepository.publish(
                BenchmarkState.Running(
                    type = type,
                    phaseLabel = phase,
                    doneSeconds = done,
                    totalSeconds = total
                )
            )
            updateNotification(type, phase, done, total)
        }

        when (type) {
            BenchmarkType.CPU -> {
                val res = CpuBenchmark.run(
                    context = this,
                    phaseSeconds = phaseSeconds,
                    onProgress = onProgress
                )
                persistCpu(res)
            }
            BenchmarkType.RAM -> {
                val res = RamBenchmark.run(perTestSeconds = phaseSeconds) { label, done, total ->
                    onProgress(label, done, total)
                }
                persistRam(res)
            }
            BenchmarkType.STORAGE_SEQUENTIAL -> {
                val res = StorageBenchmark.run(this, phaseSeconds) { label, done, total ->
                    onProgress(label, done, total)
                }
                persistStorageSeq(res)
            }
            BenchmarkType.STORAGE_RANDOM -> {
                val res = RandomIOBenchmark.run(this, phaseSeconds) { label, done, total ->
                    onProgress(label, done, total)
                }
                persistStorageRandom(res)
            }
            BenchmarkType.GPU, BenchmarkType.IMAGE -> {
                // These run interactively in their Activity for now.
                BenchmarkRepository.publish(
                    BenchmarkState.Error(type, "Dieser Bench läuft (noch) in der Activity, nicht im Service.")
                )
            }
        }
    }

    private suspend fun persistCpu(res: CpuBenchmarkResult) {
        val dao = BenchmarkDatabase.get(this).benchmarkDao()
        val run = baseRun(BenchmarkType.CPU, res.singleScore + res.multiScore / 2, res.durationMs, res.phaseDurationSec)
            .copy(
                totalScore = (res.singleScore + res.multiScore) / 2,
                singleScore = res.singleScore,
                multiScore = res.multiScore
            )
        val subs = res.subScores.map { sub ->
            BenchmarkSubScore(
                runId = 0,
                name = sub.name,
                singleScore = sub.singleScore,
                multiScore = sub.multiScore,
                singleOpsPerSec = sub.singleOpsPerSec,
                multiOpsPerSec = sub.multiOpsPerSec,
                unit = "ops/s"
            )
        }
        val id = dao.insertRunWithSubs(run, subs)
        BenchmarkRepository.publish(BenchmarkState.Done(id, BenchmarkType.CPU, run.totalScore))
    }

    private suspend fun persistRam(res: RamBenchmarkResult) {
        val dao = BenchmarkDatabase.get(this).benchmarkDao()
        val run = baseRun(BenchmarkType.RAM, res.totalScore, res.perTestSeconds * 12L * 1000L, res.perTestSeconds)
        val subs = res.tiers.map { t ->
            BenchmarkSubScore(
                runId = 0,
                name = t.tierName,
                singleScore = t.score,
                multiScore = t.score,
                singleOpsPerSec = (t.readMbPerSec + t.writeMbPerSec + t.copyMbPerSec) / 3,
                multiOpsPerSec = 0.0,
                unit = "MB/s"
            )
        }
        val id = dao.insertRunWithSubs(run, subs)
        BenchmarkRepository.publish(BenchmarkState.Done(id, BenchmarkType.RAM, run.totalScore))
    }

    private suspend fun persistStorageSeq(res: StorageBenchmarkResult) {
        val dao = BenchmarkDatabase.get(this).benchmarkDao()
        val run = baseRun(BenchmarkType.STORAGE_SEQUENTIAL, res.totalScore,
            res.durationSec * 2L * 1000L, res.durationSec)
        val subs = listOf(
            BenchmarkSubScore(0, 0, "Write Sustained", res.writeScore, res.writeScore,
                res.sustainedWriteMbPerSec, res.peakWriteMbPerSec, "MB/s"),
            BenchmarkSubScore(0, 0, "Read Sustained", res.readScore, res.readScore,
                res.sustainedReadMbPerSec, res.peakReadMbPerSec, "MB/s")
        )
        val id = dao.insertRunWithSubs(run, subs)
        BenchmarkRepository.publish(BenchmarkState.Done(id, BenchmarkType.STORAGE_SEQUENTIAL, run.totalScore))
    }

    private suspend fun persistStorageRandom(res: RandomIOResult) {
        val dao = BenchmarkDatabase.get(this).benchmarkDao()
        val run = baseRun(BenchmarkType.STORAGE_RANDOM, res.totalScore,
            res.durationSec * 6L * 1000L, res.durationSec)
        val subs = res.perQd.map { q ->
            BenchmarkSubScore(
                runId = 0,
                name = "QD=${q.queueDepth}",
                singleScore = q.readIops,
                multiScore = q.writeIops,
                singleOpsPerSec = q.readMbPerSec,
                multiOpsPerSec = q.writeMbPerSec,
                unit = "IOPS"
            )
        }
        val id = dao.insertRunWithSubs(run, subs)
        BenchmarkRepository.publish(BenchmarkState.Done(id, BenchmarkType.STORAGE_RANDOM, run.totalScore))
    }

    private fun baseRun(
        type: BenchmarkType,
        score: Int,
        durationMs: Long,
        phaseSeconds: Int
    ): BenchmarkRun = BenchmarkRun(
        type = type,
        timestamp = System.currentTimeMillis(),
        totalScore = score,
        durationMs = durationMs,
        phaseSeconds = phaseSeconds,
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
        androidSdk = Build.VERSION.SDK_INT,
        appVersion = BuildConfig.VERSION_NAME
    )

    private fun ensureChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Benchmark läuft",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Fortschritt eines laufenden Benchmarks"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun startForegroundNotification(
        type: BenchmarkType,
        phase: String,
        done: Int,
        total: Int
    ) {
        val notification = buildNotification(type, phase, done, total)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(type: BenchmarkType, phase: String, done: Int, total: Int) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(type, phase, done, total))
    }

    private fun buildNotification(type: BenchmarkType, phase: String, done: Int, total: Int) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("${type.displayName}-Benchmark läuft")
            .setContentText("$phase  ·  $done / $total s")
            .setProgress(total.coerceAtLeast(1), done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    packageManager.getLaunchIntentForPackage(packageName)
                        ?: Intent(),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    override fun onDestroy() {
        runJob?.cancel()
        super.onDestroy()
    }
}
