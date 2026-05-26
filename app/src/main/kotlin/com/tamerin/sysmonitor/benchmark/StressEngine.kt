package com.tamerin.sysmonitor.benchmark

import android.content.Context
import android.os.PowerManager
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlin.math.sqrt

/**
 * Stresstest: spawns N raw threads (one per core), each pinning at MAX priority and busy-looping.
 * Coroutines via Dispatchers.Default would share a small pool and get throttled by the scheduler
 * — raw Thread() gives the OS one thread per core to schedule, which is what we want for stress.
 */
class StressEngine {
    @Volatile private var running = false
    private val threads = mutableListOf<Thread>()
    private var wakeLock: PowerManager.WakeLock? = null

    val isRunning: Boolean get() = running

    fun start(scope: CoroutineScope) {
        // scope kept for API-compat; actual work is on raw threads
        startInternal(null)
    }

    fun start(context: Context) {
        startInternal(context)
    }

    private fun startInternal(context: Context?) {
        if (running) return
        running = true
        // Keep CPU awake while stress runs
        if (context != null) {
            runCatching {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val wl = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "RedMonitor:Stress"
                )
                wl.setReferenceCounted(false)
                wl.acquire(2 * 60 * 60 * 1000L) // 2h safety limit
                wakeLock = wl
            }
        }
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        repeat(cores) { idx ->
            val t = Thread({
                // High priority — Android's nice value -8 to -16 range
                runCatching {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                }
                var x = 1.0
                var n = 0L
                while (running) {
                    // No volatile-read in inner loop — that's a 10× perf hit
                    var i = 0
                    while (i < 50_000) {
                        x = sqrt(x + i.toDouble() + 1.0) * 1.000001
                        n += (i.toLong() * 17L) xor (n shr 5)
                        i++
                    }
                    if (x == Double.NaN) return@Thread
                }
            }, "RedMonitor-Stress-$idx").apply {
                isDaemon = true
                priority = Thread.MAX_PRIORITY
            }
            threads += t
            t.start()
        }
        // Boost AFTER spawning — moves all child threads into top-app cpuset (all big cores)
        if (context != null) {
            // Small delay so /proc/<pid>/task enumeration catches every spawned worker
            Thread {
                Thread.sleep(150)
                runCatching { PerformanceBooster.boost(context) }
            }.apply { isDaemon = true }.start()
        }
    }

    fun stop() {
        running = false
        threads.forEach { runCatching { it.interrupt() } }
        threads.clear()
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    fun stop(context: Context) {
        stop()
        // Move back to normal foreground cpuset
        runCatching { PerformanceBooster.unboost(context) }
    }
}
