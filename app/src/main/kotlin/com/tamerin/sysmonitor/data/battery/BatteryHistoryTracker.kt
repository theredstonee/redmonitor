package com.tamerin.sysmonitor.data.battery

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Immutable
data class DrainRates(
    /** Median mA drain while screen ON (over analysed window). */
    val screenOnMa: Int?,
    /** Median mA drain while screen OFF / idle. */
    val screenOffMa: Int?,
    /** How many samples / over how many days the analysis is based on. */
    val sampleCount: Int,
    val coveredHours: Double
)

/**
 * Sample-store + drain-rate aggregator. The key insight: charge-counter (µAh)
 * is a monotonic Coulomb counter, so the delta between two samples divided by
 * the elapsed time gives the *real* mean current draw during that interval.
 * Far more accurate than instantaneous BATTERY_PROPERTY_CURRENT_NOW, which
 * jitters wildly between samples.
 *
 * We bucket each interval into "screen-on" or "screen-off" based on the
 * screen state at the start of the interval, then take the median per bucket.
 * Median (not mean) is robust against the occasional bench-mode or game
 * spike that would skew an average.
 */
object BatteryHistoryTracker {

    private const val ANALYSIS_WINDOW_MS = 7L * 24 * 3600 * 1000  // 7 days
    private const val PURGE_OLDER_THAN_MS = 14L * 24 * 3600 * 1000 // 2 weeks retention

    /** Take one sample. Safe to call frequently — DB cost is ~1 ms. */
    suspend fun sample(context: Context) = withContext(Dispatchers.IO) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return@withContext
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val chargeUah = runCatching {
            bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        }.getOrDefault(0L)
        if (chargeUah <= 0 || chargeUah == Long.MIN_VALUE) return@withContext
        val pct = runCatching {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrDefault(-1)
        val charging = bm.isCharging
        val screenOn = pm?.isInteractive ?: true
        val sample = BatterySample(
            timestampMs = System.currentTimeMillis(),
            chargeCounterUah = chargeUah,
            percent = pct.toFloat().coerceAtLeast(0f),
            screenOn = screenOn,
            charging = charging
        )
        val dao = BatteryHistoryDatabase.get(context).sampleDao()
        dao.insert(sample)
        // Housekeep occasionally
        if ((sample.timestampMs / 1000) % 1000 < 50) {
            dao.purgeOlderThan(System.currentTimeMillis() - PURGE_OLDER_THAN_MS)
        }
    }

    suspend fun computeDrainRates(context: Context): DrainRates = withContext(Dispatchers.IO) {
        val dao = BatteryHistoryDatabase.get(context).sampleDao()
        val since = System.currentTimeMillis() - ANALYSIS_WINDOW_MS
        val all = dao.samplesSince(since)
        if (all.size < 3) return@withContext DrainRates(null, null, all.size, 0.0)

        val screenOnRates = mutableListOf<Double>()
        val screenOffRates = mutableListOf<Double>()
        var totalCoveredMs = 0L

        for (i in 1 until all.size) {
            val prev = all[i - 1]
            val cur = all[i]
            if (prev.charging || cur.charging) continue
            val dtMs = cur.timestampMs - prev.timestampMs
            if (dtMs < 30_000 || dtMs > 2 * 3600_000) continue  // 30 s..2 h windows only
            val deltaUah = prev.chargeCounterUah - cur.chargeCounterUah
            if (deltaUah <= 0) continue  // charge counter went up (charging or reset)
            // Convert µAh drained over dt ms → mean current in mA
            val hours = dtMs / 3_600_000.0
            val mA = (deltaUah / 1000.0) / hours
            // Sanity bounds for a phone — 1 mA..5000 mA
            if (mA !in 1.0..5_000.0) continue
            totalCoveredMs += dtMs
            // Bucket by start-of-window screen state
            if (prev.screenOn) screenOnRates += mA else screenOffRates += mA
        }
        DrainRates(
            screenOnMa = screenOnRates.takeIf { it.isNotEmpty() }?.median()?.toInt(),
            screenOffMa = screenOffRates.takeIf { it.isNotEmpty() }?.median()?.toInt(),
            sampleCount = all.size,
            coveredHours = totalCoveredMs / 3_600_000.0
        )
    }

    private fun List<Double>.median(): Double {
        if (isEmpty()) return 0.0
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid]
        }
    }
}

private val BatteryManager.isCharging: Boolean
    get() {
        val status = runCatching {
            getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        }.getOrDefault(BatteryManager.BATTERY_STATUS_UNKNOWN)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }
