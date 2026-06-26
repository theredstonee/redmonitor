package com.tamerin.sysmonitor.data

/**
 * Lade-Steuerung mit zwei Strategien:
 *
 *   1. **Kernel-Sysfs** (Hard-Stop) — schreibt direkt in /sys/class/power_supply/...
 *      Echter Hardware-Stop. Funktioniert auf Pixel, einigen OPs, alten Xiaomi.
 *      Auf modernem MIUI/HyperOS und Samsung Knox sind die Pfade entfernt oder
 *      gesperrt — auch mit root geht da nichts ohne Kernel-Patch.
 *
 *   2. **dumpsys battery** (Soft-Stop) — `dumpsys battery set ac 0 / usb 0 /
 *      wireless 0` gaukelt Android vor dass kein Ladegerät dran ist. Der
 *      OS-managed Top-Up-Loop hält an, der PMIC bekommt keine "weiter laden"-
 *      Befehle. Auf den meisten Geräten reicht das praktisch aus. Reset
 *      bringt alles wieder. Funktioniert via Shizuku-shell, kein root nötig.
 */
object ChargingControl {

    enum class Strategy { KERNEL_SYSFS, DUMPSYS_SOFT }

    data class Probe(
        val strategy: Strategy,
        val path: String,
        val onValue: String,
        val offValue: String,
        val description: String
    )

    private val SYSFS_CANDIDATES = listOf(
        // (path, on-value, off-value)
        Triple("/sys/class/power_supply/battery/input_suspend", "0", "1"),
        Triple("/sys/class/power_supply/battery/charging_enabled", "1", "0"),
        Triple("/sys/class/power_supply/battery/store_mode", "0", "1"),
        Triple("/sys/class/qcom-battery/restrict_chg", "0", "1"),
        Triple("/sys/class/power_supply/battery/battery_charging_enabled", "1", "0"),
        Triple("/sys/class/oplus_chg/battery/charging_enabled", "1", "0")
    )

    fun probe(context: android.content.Context): Probe? {
        if (ShizukuHelper.state(context) != ShizukuHelper.State.Ready) return null

        // 1) Versuche Kernel-Sysfs (Hard-Stop)
        for ((path, on, off) in SYSFS_CANDIDATES) {
            val exists = ShizukuHelper.runShell(context,
                "[ -e $path ] && [ -w $path ] && echo OK").stdout.contains("OK")
            if (exists) return Probe(
                strategy = Strategy.KERNEL_SYSFS,
                path = path,
                onValue = on,
                offValue = off,
                description = "Hardware-Stop via Kernel-Sysfs"
            )
        }

        // 2) Fallback: dumpsys battery (Soft-Stop) — geht auf jedem Gerät mit shell
        val testReset = ShizukuHelper.runShell(context, "dumpsys battery reset 2>&1")
        if (testReset.ok) return Probe(
            strategy = Strategy.DUMPSYS_SOFT,
            path = "dumpsys battery set ac/usb/wireless",
            onValue = "reset",
            offValue = "0",
            description = "Soft-Stop via OS (kein echter Hardware-Stop, aber " +
                "PMIC bekommt kein \"weiter laden\" mehr)"
        )

        return null
    }

    fun stopCharging(context: android.content.Context, probe: Probe): Boolean = when (probe.strategy) {
        Strategy.KERNEL_SYSFS -> {
            ShizukuHelper.runShell(context, "echo ${probe.offValue} > ${probe.path}").ok
        }
        Strategy.DUMPSYS_SOFT -> {
            // Alle 3 Charger-Quellen "ausstecken"
            val a = ShizukuHelper.runShell(context, "dumpsys battery set ac 0").ok
            val u = ShizukuHelper.runShell(context, "dumpsys battery set usb 0").ok
            val w = ShizukuHelper.runShell(context, "dumpsys battery set wireless 0").ok
            a && u && w
        }
    }

    fun resumeCharging(context: android.content.Context, probe: Probe): Boolean = when (probe.strategy) {
        Strategy.KERNEL_SYSFS -> {
            ShizukuHelper.runShell(context, "echo ${probe.onValue} > ${probe.path}").ok
        }
        Strategy.DUMPSYS_SOFT -> {
            // reset macht ALLE Werte wieder auf "echte System-Erkennung"
            ShizukuHelper.runShell(context, "dumpsys battery reset").ok
        }
    }
}
