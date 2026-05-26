package com.tamerin.sysmonitor.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import java.io.File
import kotlin.math.abs

data class BatterySnapshot(
    val percent: Float,
    val isCharging: Boolean,
    val pluggedSource: String,
    val isWireless: Boolean,
    val healthLabel: String,
    val statusLabel: String,
    val temperatureC: Float,
    val voltageV: Float,
    val technology: String,
    val currentNowMa: Long,
    val averageCurrentMa: Long,
    val deltaDerivedCurrentMa: Long,
    val capacityMah: Int,
    val capacityFullDesignMah: Int,
    val capacityFullMah: Int,
    val healthPercent: Float,
    val energyNwh: Long,
    val wattsNow: Float,
    val inputVoltageV: Float,
    val inputCurrentMa: Long,
    val wattsSource: String,
    val chargingSpeedLabel: String,
    val sensorTrust: Boolean
)

object BatteryReader {
    // Persistent state for charge-counter delta sampling (the most reliable method on Samsung/OEMs)
    private var lastChargeUah: Long = Long.MIN_VALUE
    private var lastChargeSampleMs: Long = 0L
    private var emaCurrentMa: Float = 0f
    private const val MIN_SAMPLE_GAP_MS = 2_000L

    fun read(context: Context): BatterySnapshot {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter)
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) level * 100f / scale else 0f

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val pluggedRaw = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val plugged = when (pluggedRaw) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC (Netzteil)"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless / Qi"
            else -> "Akkubetrieb"
        }
        val isWireless = pluggedRaw == BatteryManager.BATTERY_PLUGGED_WIRELESS

        val health = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Gut"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Überhitzt"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Defekt"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Überspannung"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Fehler"
            BatteryManager.BATTERY_HEALTH_COLD -> "Zu kalt"
            else -> "Unbekannt"
        }

        val statusLabel = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Lädt"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Entlädt"
            BatteryManager.BATTERY_STATUS_FULL -> "Voll"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Nicht ladend"
            BatteryManager.BATTERY_STATUS_UNKNOWN -> "Unbekannt"
            else -> "—"
        }

        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val mV = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "—"

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        // ---- Battery-side current via BatteryManager ----
        val bmCurrent = runCatching {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrDefault(0)
        val bmAvg = runCatching {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        }.getOrDefault(0)
        val capacityRaw = runCatching {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        }.getOrDefault(0)
        val energyNwh = runCatching {
            bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
        }.getOrDefault(0L)

        val bmCurrentMa = normalizeToMa(bmCurrent)
        val avgMa = normalizeToMa(bmAvg)
        var battVoltageMv = mV
        if (battVoltageMv == 0) {
            battVoltageMv = readSysfsVoltageMv(
                context,
                "/sys/class/power_supply/battery/voltage_now"
            ) ?: 0
        }

        // ---- DELTA method: sample charge-counter over time = REAL current ----
        // This is what Ampere/AccuBattery use, and it works regardless of OEM restrictions
        // because it's pure math on a counter that's exposed by every Android.
        val deltaCurrentMa = computeDeltaCurrentMa(capacityRaw.toLong())

        // ---- Pick best current source ----
        // Priority: delta-derived (most accurate) > CURRENT_NOW > CURRENT_AVERAGE
        val bestCurrentMa = when {
            deltaCurrentMa != 0L -> deltaCurrentMa
            bmCurrentMa != 0L -> bmCurrentMa
            avgMa != 0L -> avgMa
            else -> 0L
        }

        // ---- USB / Input-side (true wall-power if not blocked) ----
        var usbVoltageMv = readSysfsVoltageMv(
            context,
            "/sys/class/power_supply/usb/voltage_now",
            "/sys/class/power_supply/usb_pd/voltage_now",
            "/sys/class/power_supply/charger/voltage_now",
            "/sys/class/power_supply/ac/voltage_now",
            "/sys/class/power_supply/main/voltage_now"
        ) ?: 0
        var usbCurrentMa = readSysfsLong(
            context,
            "/sys/class/power_supply/usb/current_now",
            "/sys/class/power_supply/usb_pd/current_now",
            "/sys/class/power_supply/charger/current_now",
            "/sys/class/power_supply/ac/current_now",
            "/sys/class/power_supply/main/current_now",
            "/sys/class/power_supply/battery/input_current_now"
        )?.let { normalizeToMa(it.toInt()) } ?: 0L

        // Samsung blocks USB-sysfs even via Shizuku — but `dumpsys battery` works as shell user
        // and contains the negotiated PD voltage/current.
        if ((usbVoltageMv == 0 || usbCurrentMa == 0L) && charging
            && ShizukuHelper.state(context) == ShizukuHelper.State.Ready) {
            val dump = ShizukuHelper.dumpsysBattery(context)
            if (dump != null) {
                if (usbVoltageMv == 0 && dump.maxChargingVoltageUv > 0) {
                    // µV → mV
                    usbVoltageMv = (dump.maxChargingVoltageUv / 1000L).toInt()
                }
                if (usbCurrentMa == 0L && dump.maxChargingCurrentUa > 0) {
                    // µA → mA
                    usbCurrentMa = dump.maxChargingCurrentUa / 1000L
                }
            }
        }

        val battVoltageV = battVoltageMv / 1000f
        val battAmps = abs(bestCurrentMa) / 1000f
        val battWatts = battVoltageV * battAmps

        val usbVoltageV = usbVoltageMv / 1000f
        val usbAmps = abs(usbCurrentMa) / 1000f
        val usbWatts = usbVoltageV * usbAmps

        // ---- Pick best wattage display ----
        val (watts, source) = when {
            charging && usbWatts in 0.5f..200f && usbVoltageV >= 4.5f ->
                usbWatts to "USB-Eingang (echte PD-Leistung)"
            charging && battWatts in 0.1f..200f -> {
                val method = when {
                    deltaCurrentMa != 0L -> "Akku-Seite über Charge-Counter Δ (genau)"
                    else -> "Akku-Seite via BatteryManager (Momentwert)"
                }
                battWatts to method
            }
            !charging && battWatts in 0.05f..200f -> battWatts to "Akku-Seite (System-Verbrauch)"
            else -> 0f to "—"
        }

        val sensorTrust = watts > 0.1f || (!charging && abs(bestCurrentMa) > 5L)

        val speedLabel = if (charging) {
            if (!sensorTrust || watts < 0.2f) {
                "Sensor liefert keine Werte"
            } else when {
                watts >= 80f -> "Hyper-Charging (${"%.1f".format(watts)} W)"
                watts >= 50f -> "Super-Fast-Charging 2.0 (${"%.1f".format(watts)} W)"
                watts >= 30f -> "Super-Fast-Charging (${"%.1f".format(watts)} W)"
                watts >= 15f -> "Schnellladen (${"%.1f".format(watts)} W)"
                watts >= 7.5f -> "Standard-Schnellladen (${"%.1f".format(watts)} W)"
                watts >= 2.5f -> "Normales Laden (${"%.1f".format(watts)} W)"
                else -> "Trickle-Charging (${"%.2f".format(watts)} W)"
            }
        } else "—"

        val designMah = readSysfsLong(context, "/sys/class/power_supply/battery/charge_full_design")
            ?.let { it / 1000 }?.toInt() ?: 0
        val fullMah = readSysfsLong(context, "/sys/class/power_supply/battery/charge_full")
            ?.let { it / 1000 }?.toInt() ?: 0
        val healthPct = if (designMah > 0 && fullMah > 0) fullMah * 100f / designMah else 0f

        return BatterySnapshot(
            percent = pct,
            isCharging = charging,
            pluggedSource = plugged,
            isWireless = isWireless,
            healthLabel = health,
            statusLabel = statusLabel,
            temperatureC = tempTenths / 10f,
            voltageV = battVoltageV,
            technology = tech,
            currentNowMa = bestCurrentMa,
            averageCurrentMa = avgMa,
            deltaDerivedCurrentMa = deltaCurrentMa,
            capacityMah = capacityRaw / 1000,
            capacityFullDesignMah = designMah,
            capacityFullMah = fullMah,
            healthPercent = healthPct,
            energyNwh = energyNwh,
            wattsNow = watts,
            inputVoltageV = usbVoltageV,
            inputCurrentMa = usbCurrentMa,
            wattsSource = source,
            chargingSpeedLabel = speedLabel,
            sensorTrust = sensorTrust
        )
    }

    /**
     * Returns charging current in mA derived from BATTERY_PROPERTY_CHARGE_COUNTER (µAh)
     * sampled over real time. Positive = charging, negative = discharging.
     * Falls back to 0 if not enough time has elapsed or counter unreliable.
     */
    private fun computeDeltaCurrentMa(currentChargeUah: Long): Long {
        if (currentChargeUah <= 0L) return 0L
        val nowMs = SystemClock.elapsedRealtime()
        val prevCharge = lastChargeUah
        val prevMs = lastChargeSampleMs
        // First call — store and return 0
        if (prevCharge == Long.MIN_VALUE || prevMs == 0L) {
            lastChargeUah = currentChargeUah
            lastChargeSampleMs = nowMs
            return 0L
        }
        val deltaMs = nowMs - prevMs
        if (deltaMs < MIN_SAMPLE_GAP_MS) return emaCurrentMa.toLong()
        // delta Q in µAh, over delta t in ms
        val deltaUah = currentChargeUah - prevCharge
        // I (µA) = ΔQ (µAh) × 3_600_000 / Δt (ms)
        val instMa = (deltaUah.toDouble() * 3_600_000.0 / deltaMs.toDouble() / 1000.0).toFloat()
        // Sanity: anything > 25 A is unrealistic for a phone
        val clamped = instMa.coerceIn(-25_000f, 25_000f)
        // EMA smoothing
        emaCurrentMa = if (emaCurrentMa == 0f) clamped else emaCurrentMa * 0.5f + clamped * 0.5f
        lastChargeUah = currentChargeUah
        lastChargeSampleMs = nowMs
        return emaCurrentMa.toLong()
    }

    /** Convert µA or mA reading to mA, by guessing based on magnitude. */
    private fun normalizeToMa(raw: Int): Long {
        if (raw == 0) return 0L
        val absVal = abs(raw.toLong())
        return when {
            absVal > 50_000 -> raw.toLong() / 1000  // µA → mA
            else -> raw.toLong()                    // already mA
        }
    }


    private fun readSysfsLong(context: Context, vararg paths: String): Long? {
        val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
        for (path in paths) {
            val direct = runCatching { File(path).readText().trim().toLong() }.getOrNull()
            if (direct != null && direct != 0L) return direct
            if (shizukuReady) {
                val viaShizuku = ShizukuHelper.readLong(context, path)
                if (viaShizuku != null && viaShizuku != 0L) return viaShizuku
            }
        }
        return null
    }

    private fun readSysfsVoltageMv(context: Context, vararg paths: String): Int? {
        val raw = readSysfsLong(context, *paths) ?: return null
        return if (raw > 100_000) (raw / 1000).toInt() else raw.toInt()
    }
}
