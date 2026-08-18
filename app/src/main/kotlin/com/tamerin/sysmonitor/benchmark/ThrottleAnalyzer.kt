package com.tamerin.sysmonitor.benchmark

import androidx.compose.runtime.Immutable

/** Eine Messung pro Sekunde waehrend des Stresstests. */
@Immutable
data class ThrottleSample(
    val elapsedSec: Int,
    val opsPerSec: Double,
    val tempC: Float,
    val freqMhz: Int
)

@Immutable
data class ThrottleResult(
    val baselineOpsPerSec: Double,
    val currentOpsPerSec: Double,
    /** Aktueller Durchsatz geteilt durch den Anfangsdurchsatz. 1.0 = kein Throttling. */
    val sustainedRatio: Float,
    /** Schlechtester Wert im gesamten Lauf. */
    val minRatio: Float,
    /** Sekunde, ab der der Durchsatz nachhaltig eingebrochen ist — null = kein Throttling. */
    val throttleAtSec: Int?,
    val peakTempC: Float,
    val baselineFreqMhz: Int,
    val currentFreqMhz: Int
) {
    val sustainedPercent: Int get() = (sustainedRatio * 100f).toInt()

    /** Grobe Einordnung fuer die UI. */
    val verdict: String get() = when {
        sustainedRatio >= 0.95f -> "Kein nennenswertes Throttling"
        sustainedRatio >= 0.85f -> "Leichtes Throttling"
        sustainedRatio >= 0.70f -> "Deutliches Throttling"
        else -> "Starkes Throttling"
    }
}

/**
 * Rechnet die Sekunden-Samples eines Stresstests in eine Sustained-Performance-Kennzahl um.
 *
 * Der Trick: unter Volllast bleibt die CPU-Auslastung bei 100 %, egal wie stark gedrosselt
 * wird — aussagekraeftig ist allein der **Durchsatz** (Iterationen/Sekunde, gezaehlt von
 * [StressEngine]). Faellt der, drosselt das Geraet.
 *
 * Bewusst pure und ohne Android-Bezug, damit es ohne Geraet testbar ist.
 */
object ThrottleAnalyzer {

    /** Die ersten Sekunden sind Anlauf (Threads starten, Boost greift) und zaehlen nicht. */
    const val WARMUP_SEC = 5

    /** Baseline = Durchschnitt zwischen Warmup-Ende und dieser Sekunde. */
    const val BASELINE_END_SEC = 20

    /** Unterhalb dieses Anteils der Baseline gilt es als Throttling. */
    const val THROTTLE_THRESHOLD = 0.90f

    /** So viele Samples in Folge muessen unter der Schwelle liegen, damit es zaehlt. */
    const val CONFIRM_SAMPLES = 3

    /** Glaettungsfenster fuer den aktuellen Wert. */
    private const val CURRENT_WINDOW = 5

    /**
     * @return null, solange noch keine belastbare Baseline vorliegt (< [WARMUP_SEC] + 2 Samples).
     */
    fun analyze(samples: List<ThrottleSample>): ThrottleResult? {
        // > statt >=: WARMUP_SEC ist die letzte Anlaufsekunde und gehoert noch nicht
        // zur Messung. Mit >= rutschte ein langsamer Anlaufwert in die Baseline und
        // liess spaetere Messungen wie Mehrleistung aussehen.
        val usable = samples.filter { it.elapsedSec > WARMUP_SEC && it.opsPerSec > 0.0 }
        if (usable.size < 2) return null

        val baselineWindow = usable.filter { it.elapsedSec <= BASELINE_END_SEC }
            .ifEmpty { usable.take(CURRENT_WINDOW) }
        val baseline = baselineWindow.map { it.opsPerSec }.average()
        if (baseline <= 0.0) return null

        val currentWindow = usable.takeLast(CURRENT_WINDOW)
        val current = currentWindow.map { it.opsPerSec }.average()

        val minRatio = usable.minOf { it.opsPerSec / baseline }.toFloat()

        return ThrottleResult(
            baselineOpsPerSec = baseline,
            currentOpsPerSec = current,
            sustainedRatio = (current / baseline).toFloat(),
            minRatio = minRatio,
            throttleAtSec = findThrottlePoint(usable, baseline),
            peakTempC = samples.maxOfOrNull { it.tempC } ?: Float.NaN,
            baselineFreqMhz = baselineWindow.map { it.freqMhz }.filter { it > 0 }
                .ifEmpty { listOf(0) }.average().toInt(),
            currentFreqMhz = currentWindow.map { it.freqMhz }.filter { it > 0 }
                .ifEmpty { listOf(0) }.average().toInt()
        )
    }

    /**
     * Erster Zeitpunkt, ab dem der Durchsatz [CONFIRM_SAMPLES] Messungen in Folge unter der
     * Schwelle bleibt. Die Bestaetigung verhindert, dass ein einzelner Ausreisser — etwa weil
     * kurz eine andere App Rechenzeit zieht — schon als Throttling durchgeht.
     */
    private fun findThrottlePoint(usable: List<ThrottleSample>, baseline: Double): Int? {
        var streak = 0
        var streakStart: Int? = null
        for (s in usable) {
            if (s.opsPerSec / baseline < THROTTLE_THRESHOLD) {
                if (streak == 0) streakStart = s.elapsedSec
                streak++
                if (streak >= CONFIRM_SAMPLES) return streakStart
            } else {
                streak = 0
                streakStart = null
            }
        }
        return null
    }
}
