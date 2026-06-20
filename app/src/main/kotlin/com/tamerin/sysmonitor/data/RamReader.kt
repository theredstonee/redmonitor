package com.tamerin.sysmonitor.data

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs

@androidx.compose.runtime.Immutable
data class RamSnapshot(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long,
    val percent: Float,
    val lowMemory: Boolean,
    val threshold: Long
)

@androidx.compose.runtime.Immutable
data class StorageSnapshot(
    val internalTotal: Long,
    val internalAvailable: Long,
    val internalPercent: Float
)

object MemoryReader {
    fun readRam(context: Context): RamSnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val used = info.totalMem - info.availMem
        val pct = if (info.totalMem > 0) used * 100f / info.totalMem else 0f
        return RamSnapshot(
            totalBytes = info.totalMem,
            availableBytes = info.availMem,
            usedBytes = used,
            percent = pct,
            lowMemory = info.lowMemory,
            threshold = info.threshold
        )
    }

    fun readStorage(): StorageSnapshot {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val available = stat.availableBytes
        val used = total - available
        val pct = if (total > 0) used * 100f / total else 0f
        return StorageSnapshot(
            internalTotal = total,
            internalAvailable = available,
            internalPercent = pct
        )
    }
}

fun Long.formatBytes(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = this.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format("%.2f %s", value, units[unit])
}
