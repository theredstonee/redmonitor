package com.tamerin.sysmonitor

import com.tamerin.sysmonitor.benchmark.ThrottleAnalyzer
import com.tamerin.sysmonitor.benchmark.ThrottleSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThrottleAnalyzerTest {

    // ops steht bewusst zuletzt: sonst bindet die Trailing-Lambda an freq
    private fun run(
        seconds: Int,
        temp: (Int) -> Float = { 40f + it * 0.3f },
        freq: (Int) -> Int = { 2800 },
        ops: (Int) -> Double
    ) = (1..seconds).map { ThrottleSample(it, ops(it), temp(it), freq(it)) }

    @Test
    fun `zu wenig Daten ergibt kein Ergebnis`() {
        assertNull(ThrottleAnalyzer.analyze(emptyList()))
        // Alles innerhalb des Warmups zaehlt nicht
        assertNull(ThrottleAnalyzer.analyze(run(4) { 1_000_000.0 }))
    }

    @Test
    fun `konstante Leistung ergibt kein Throttling`() {
        val r = ThrottleAnalyzer.analyze(run(60) { 1_000_000.0 })
        assertNotNull(r)
        assertEquals(100, r!!.sustainedPercent)
        assertNull(r.throttleAtSec)
        assertTrue(r.verdict.contains("Kein"))
    }

    @Test
    fun `halbierter Durchsatz wird als starkes Throttling erkannt`() {
        // Ab Sekunde 30 bricht die Leistung auf die Haelfte ein
        val r = ThrottleAnalyzer.analyze(
            run(60) { if (it < 30) 1_000_000.0 else 500_000.0 }
        )
        assertNotNull(r)
        assertEquals(50, r!!.sustainedPercent)
        assertEquals(30, r.throttleAtSec)
        assertTrue(r.verdict.contains("Stark"))
    }

    @Test
    fun `einzelner Ausreisser gilt nicht als Throttling`() {
        // Genau ein Einbruch — z.B. weil kurz eine andere App Rechenzeit zog
        val r = ThrottleAnalyzer.analyze(
            run(60) { if (it == 40) 100_000.0 else 1_000_000.0 }
        )
        assertNotNull(r)
        assertNull("Ein einzelner Ausreisser darf nicht zaehlen", r!!.throttleAtSec)
    }

    @Test
    fun `Throttling-Punkt ist der Beginn des Einbruchs, nicht die Bestaetigung`() {
        val r = ThrottleAnalyzer.analyze(
            run(60) { if (it < 25) 1_000_000.0 else 700_000.0 }
        )
        assertEquals(25, r!!.throttleAtSec)
    }

    @Test
    fun `Warmup-Sekunden verfaelschen die Baseline nicht`() {
        // Die ersten Sekunden sind langsam (Threads laufen an) — das darf die
        // Baseline nicht druecken, sonst sieht spaeter alles nach Mehrleistung aus
        val r = ThrottleAnalyzer.analyze(
            run(60) { if (it <= ThrottleAnalyzer.WARMUP_SEC) 10_000.0 else 1_000_000.0 }
        )
        assertNotNull(r)
        assertEquals(100, r!!.sustainedPercent)
    }

    @Test
    fun `leichtes Throttling wird als solches benannt`() {
        val r = ThrottleAnalyzer.analyze(
            run(60) { if (it < 30) 1_000_000.0 else 900_000.0 }
        )
        assertEquals(90, r!!.sustainedPercent)
        assertTrue(r.verdict.contains("Leicht"))
    }

    @Test
    fun `minRatio haelt den schlechtesten Moment fest`() {
        val r = ThrottleAnalyzer.analyze(
            run(60) { if (it in 40..42) 300_000.0 else 1_000_000.0 }
        )
        assertNotNull(r)
        assertTrue("minRatio muss den Einbruch zeigen", r!!.minRatio < 0.35f)
        // Aktuell laeuft es wieder normal
        assertEquals(100, r.sustainedPercent)
    }

    @Test
    fun `Taktabfall wird mitgefuehrt`() {
        val r = ThrottleAnalyzer.analyze(
            run(60, freq = { if (it < 30) 2800 else 1600 }) {
                if (it < 30) 1_000_000.0 else 600_000.0
            }
        )
        assertNotNull(r)
        assertEquals(2800, r!!.baselineFreqMhz)
        assertEquals(1600, r.currentFreqMhz)
    }

    @Test
    fun `Spitzentemperatur kommt aus allen Samples`() {
        val r = ThrottleAnalyzer.analyze(
            run(60, temp = { if (it == 50) 91.5f else 45f }) { 1_000_000.0 }
        )
        assertEquals(91.5f, r!!.peakTempC, 0.01f)
    }

    @Test
    fun `Nullwerte im Durchsatz kippen die Auswertung nicht`() {
        val r = ThrottleAnalyzer.analyze(run(60) { if (it % 10 == 0) 0.0 else 1_000_000.0 })
        assertNotNull(r)
        assertEquals(100, r!!.sustainedPercent)
    }
}
