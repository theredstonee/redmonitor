package com.tamerin.sysmonitor.data

import java.io.File

data class ThermalZone(
    val index: Int,
    val type: String,
    val tempCelsius: Float
)

object ThermalReader {
    fun read(): List<ThermalZone> {
        val base = File("/sys/class/thermal")
        if (!base.exists()) return emptyList()
        val zones = base.listFiles { f -> f.name.startsWith("thermal_zone") } ?: return emptyList()
        return zones.mapNotNull { dir ->
            val idx = dir.name.removePrefix("thermal_zone").toIntOrNull() ?: return@mapNotNull null
            val type = runCatching { File(dir, "type").readText().trim() }.getOrDefault("?")
            val tempRaw = runCatching { File(dir, "temp").readText().trim().toLong() }.getOrNull()
            val tempC = tempRaw?.let { if (it > 1000) it / 1000f else it.toFloat() } ?: Float.NaN
            ThermalZone(idx, type, tempC)
        }.sortedBy { it.index }
    }

    /**
     * Returns the hottest CPU/SoC zone temperature. Filters out battery/skin sensors
     * so the stress test reflects actual silicon temperature, not the case.
     */
    fun hottestCpuZone(zones: List<ThermalZone>): ThermalZone? {
        val hints = listOf("cpu", "soc", "tsens", "apc", "kgsl", "gpu", "big", "little", "silver", "gold")
        return zones
            .filter { !it.tempCelsius.isNaN() && it.tempCelsius in 5f..150f }
            .filter { z -> hints.any { h -> z.type.contains(h, ignoreCase = true) } }
            .maxByOrNull { it.tempCelsius }
            ?: zones.filter { !it.tempCelsius.isNaN() && it.tempCelsius in 5f..150f }
                .maxByOrNull { it.tempCelsius }
    }
}
