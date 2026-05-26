package com.tamerin.sysmonitor.benchmark

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class StressEngine {
    private val jobs = mutableListOf<Job>()

    fun start(scope: CoroutineScope) {
        if (jobs.isNotEmpty()) return
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        repeat(cores) {
            jobs += scope.launch(Dispatchers.Default) {
                var x = 1.0
                var n = 0L
                while (isActive) {
                    repeat(50_000) { i ->
                        x = sqrt(x + i.toDouble() + 1.0) * 1.000001
                        n += (i.toLong() * 17L) xor (n shr 5)
                    }
                    if (x == Double.NaN) return@launch
                }
            }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    val isRunning: Boolean get() = jobs.isNotEmpty()
}
