package com.tamerin.sysmonitor.data

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
data class BatteryTimeEstimate(
    val state: State,
    val remainingMs: Long?,    // null if not estimable
    val confidence: Confidence,
    val source: String         // human-readable: "Android-API", "Strom-Berechnung", "—"
) {
    enum class State { CHARGING, DISCHARGING, FULL, UNKNOWN }
    enum class Confidence { HIGH, MEDIUM, LOW, NONE }

    fun formatRemaining(): String {
        val ms = remainingMs ?: return "—"
        if (ms <= 0) return "—"
        val totalMin = (ms / 60_000).toInt()
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h <= 0 -> "$m min"
            h < 24 -> "${h} h ${m.toString().padStart(2, '0')} min"
            else -> "${h / 24} Tage ${h % 24} h"
        }
    }
}

/**
 * Computes a best-effort estimate for battery time remaining (discharging) or
 * time-to-full (charging). Strategy:
 *
 *  1. If charging and API ≥ 28: use [BatteryManager.computeChargeTimeRemaining]
 *     — Android tracks this in the kernel using actual charge curve history.
 *
 *  2. Otherwise compute from instant current draw + remaining capacity:
 *     hours = capacity_mAh / |current_mA|
 *     Less accurate (snapshot only, doesn't account for adaptive throttling)
 *     but works everywhere.
 *
 *  3. If neither works (battery API not exposed): return UNKNOWN.
 */
object BatteryEstimator {

    fun estimate(context: Context): BatteryTimeEstimate {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return BatteryTimeEstimate(BatteryTimeEstimate.State.UNKNOWN, null,
                BatteryTimeEstimate.Confidence.NONE, "—")

        val charging = bm.isCharging
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        if (pct >= 99 && charging) {
            return BatteryTimeEstimate(BatteryTimeEstimate.State.FULL, 0L,
                BatteryTimeEstimate.Confidence.HIGH, "Voll")
        }

        // Path 1: Android's own estimate (charging only, API 28+)
        if (charging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val androidMs = runCatching { bm.computeChargeTimeRemaining() }.getOrDefault(-1L)
            if (androidMs > 0) {
                return BatteryTimeEstimate(
                    state = BatteryTimeEstimate.State.CHARGING,
                    remainingMs = androidMs,
                    confidence = BatteryTimeEstimate.Confidence.HIGH,
                    source = "Android-Lade-API"
                )
            }
        }

        // Path 2: compute from current + capacity
        val chargeNowUah = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val currentNowUa = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        // Filter sentinels and physically impossible values (>20 A in µA)
        val currentValid = chargeNowUah > 0 &&
            currentNowUa != 0L &&
            currentNowUa != Long.MIN_VALUE &&
            currentNowUa != Long.MAX_VALUE &&
            abs(currentNowUa) < 20_000_000L
        if (currentValid) {
            val absCurrentMa = abs(currentNowUa / 1000.0)
            if (absCurrentMa < 1.0) {
                return BatteryTimeEstimate(
                    if (charging) BatteryTimeEstimate.State.CHARGING else BatteryTimeEstimate.State.DISCHARGING,
                    null,
                    BatteryTimeEstimate.Confidence.NONE,
                    "kein Strom"
                )
            }
            val chargeMah = chargeNowUah / 1000.0

            if (charging) {
                // Estimate full capacity from current pct
                val fullMah = if (pct in 1..100) chargeMah * 100.0 / pct else chargeMah
                val toFillMah = (fullMah - chargeMah).coerceAtLeast(0.0)
                val hours = toFillMah / absCurrentMa
                return BatteryTimeEstimate(
                    state = BatteryTimeEstimate.State.CHARGING,
                    remainingMs = (hours * 3600_000).toLong(),
                    confidence = BatteryTimeEstimate.Confidence.MEDIUM,
                    source = "Strom × Kapazität"
                )
            } else {
                val hours = chargeMah / absCurrentMa
                return BatteryTimeEstimate(
                    state = BatteryTimeEstimate.State.DISCHARGING,
                    remainingMs = (hours * 3600_000).toLong(),
                    confidence = BatteryTimeEstimate.Confidence.MEDIUM,
                    source = "Strom × Kapazität"
                )
            }
        }

        return BatteryTimeEstimate(
            if (charging) BatteryTimeEstimate.State.CHARGING else BatteryTimeEstimate.State.DISCHARGING,
            null,
            BatteryTimeEstimate.Confidence.NONE,
            "—"
        )
    }
}
