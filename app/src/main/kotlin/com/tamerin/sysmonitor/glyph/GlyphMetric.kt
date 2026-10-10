package com.tamerin.sysmonitor.glyph

import android.content.Context
import androidx.annotation.StringRes
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.data.BatteryReader
import com.tamerin.sysmonitor.data.CpuReader
import com.tamerin.sysmonitor.data.MemoryReader
import com.tamerin.sysmonitor.data.ThermalReader

/**
 * Was der Glyph-Toy auf der Rueckseite anzeigt. Langer Druck auf den
 * Glyph-Button schaltet zur naechsten Metrik weiter.
 */
enum class GlyphMetric(val marker: Char, @StringRes val labelRes: Int, val unit: String) {
    CPU('C', R.string.misc_glyph_metric_cpu, "%"),
    RAM('R', R.string.misc_glyph_metric_ram, "%"),
    BATTERY('B', R.string.misc_glyph_metric_battery, "%"),
    TEMP('T', R.string.misc_glyph_metric_temp, "°C");

    fun next(): GlyphMetric = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromName(name: String?): GlyphMetric =
            entries.firstOrNull { it.name == name } ?: CPU
    }
}

/** Ein Messwert, fertig fuer [GlyphMatrixRenderer.render]. */
data class GlyphSample(val value: Int, val fraction: Float)

object GlyphMetricReader {

    /** Blockierend (sysfs/dumpsys) — gehoert auf einen Hintergrund-Thread. */
    fun read(context: Context, metric: GlyphMetric): GlyphSample = runCatching {
        when (metric) {
            GlyphMetric.CPU -> {
                val pct = CpuReader.read(context, "glyph").totalPercent
                sample(pct, pct / 100f)
            }
            GlyphMetric.RAM -> {
                val pct = MemoryReader.readRam(context).percent
                sample(pct, pct / 100f)
            }
            GlyphMetric.BATTERY -> {
                val pct = BatteryReader.read(context).percent
                sample(pct, pct / 100f)
            }
            GlyphMetric.TEMP -> {
                val zones = ThermalReader.read(context)
                val t = ThermalReader.hottestCpuZone(zones)?.tempCelsius
                if (t == null || t.isNaN()) {
                    GlyphSample(0, 0f)
                } else {
                    // Ring skaliert 0..100 °C — darueber laeuft er einfach voll
                    sample(t, t / 100f)
                }
            }
        }
    }.getOrDefault(GlyphSample(0, 0f))

    private fun sample(value: Float, fraction: Float): GlyphSample {
        if (value.isNaN()) return GlyphSample(0, 0f)
        return GlyphSample(value.toInt().coerceIn(0, 999), fraction.coerceIn(0f, 1f))
    }
}
