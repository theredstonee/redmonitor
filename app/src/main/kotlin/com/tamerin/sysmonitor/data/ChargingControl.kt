package com.tamerin.sysmonitor.data

/**
 * OEM-spezifische Sysfs-Pfade fürs Charge-Enable-Toggle.
 * Wir probieren sie alle durch und merken uns den ersten beschreibbaren.
 */
object ChargingControl {

    data class ProbeResult(val path: String, val onValue: String, val offValue: String, val invert: Boolean)

    private val CANDIDATES = listOf(
        // AOSP/Pixel
        Triple("/sys/class/power_supply/battery/input_suspend", "0", "1") to true,    // invert: 1 = off
        // Stock Qualcomm
        Triple("/sys/class/power_supply/battery/charging_enabled", "1", "0") to false,
        // Some MediaTek
        Triple("/sys/class/power_supply/battery/store_mode", "0", "1") to true,
        // Xiaomi/HyperOS variants
        Triple("/sys/class/qcom-battery/restrict_chg", "0", "1") to true,
        Triple("/sys/class/power_supply/battery/battery_charging_enabled", "1", "0") to false,
        // OnePlus / Realme
        Triple("/sys/class/oplus_chg/battery/charging_enabled", "1", "0") to false
    )

    /**
     * Probiert alle Kandidaten via Shizuku und gibt den ersten zurück der existiert
     * UND beschreibbar ist. Schreibt nichts permanent — testet nur ob `echo` durchgeht.
     */
    fun probe(context: android.content.Context): ProbeResult? {
        if (ShizukuHelper.state(context) != ShizukuHelper.State.Ready) return null
        for ((triple, invert) in CANDIDATES) {
            val path = triple.first
            val exists = ShizukuHelper.runShell(context,
                "[ -e $path ] && [ -w $path ] && echo OK").stdout.contains("OK")
            if (exists) {
                return ProbeResult(
                    path = path,
                    onValue = triple.second,
                    offValue = triple.third,
                    invert = invert
                )
            }
        }
        return null
    }

    fun stopCharging(context: android.content.Context, probe: ProbeResult): Boolean {
        val res = ShizukuHelper.runShell(context, "echo ${probe.offValue} > ${probe.path}")
        return res.ok
    }

    fun resumeCharging(context: android.content.Context, probe: ProbeResult): Boolean {
        val res = ShizukuHelper.runShell(context, "echo ${probe.onValue} > ${probe.path}")
        return res.ok
    }
}
