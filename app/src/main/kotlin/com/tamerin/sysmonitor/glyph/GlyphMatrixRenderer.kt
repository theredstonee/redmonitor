package com.tamerin.sysmonitor.glyph

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Rendert eine Metrik in ein Glyph-Matrix-Bild — **ohne** jeden Bezug auf das
 * Nothing-SDK. Ergebnis ist ein IntArray mit [SIZE] * [SIZE] Helligkeitswerten
 * (0..255), genau das Format das `GlyphMatrixManager.setMatrixFrame(int[])` will.
 *
 * Bewusst pure: dieselbe Funktion speist die echte Matrix auf dem Phone (3) UND
 * die 25x25-Vorschau in der App, und laesst sich ohne Geraet testen.
 *
 * Layout (25x25):
 *   - Aussen ein Ring-Gauge, im Uhrzeigersinn ab 12 Uhr, gefuellt = Prozentwert
 *   - Mitte die Zahl (1-3 Ziffern, 5x7-Font)
 *   - Darueber ein 3x5-Buchstabe als Marker, welche Metrik gerade laeuft
 */
object GlyphMatrixRenderer {

    /** Kantenlaenge der Matrix des Phone (3). Phone (4a) Pro haette 13. */
    const val SIZE = 25

    private const val OFF = 0

    // Ring-Geometrie in Pixeln, Mittelpunkt liegt auf (12,12)
    private const val CENTER = 12f
    private const val RING_INNER = 10.2f
    private const val RING_OUTER = 12.3f

    /**
     * @param value      Anzeigewert, wird auf ganze Zahl gerundet (z.B. 42 fuer 42 %)
     * @param fraction   Ring-Fuellung 0f..1f
     * @param marker     Ein Zeichen aus [LETTERS] als Metrik-Kennung, oder null
     * @param brightness Maximalhelligkeit 0..255 (AOD faehrt hier runter)
     */
    fun render(
        value: Int,
        fraction: Float,
        marker: Char?,
        brightness: Int = 255
    ): IntArray {
        val full = brightness.coerceIn(0, 255)
        // Ungefuellter Teil des Rings bleibt schwach sichtbar, damit man die Skala sieht
        val dim = (full * 0.16f).toInt().coerceAtLeast(if (full > 0) 1 else 0)
        val out = IntArray(SIZE * SIZE)

        drawRing(out, fraction.coerceIn(0f, 1f), full, dim)
        marker?.let { drawMarker(out, it, full) }
        drawValue(out, value, full)
        return out
    }

    private fun drawRing(out: IntArray, fraction: Float, full: Int, dim: Int) {
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val dx = x - CENTER
                val dy = y - CENTER
                val dist = hypot(dx, dy)
                if (dist < RING_INNER || dist > RING_OUTER) continue
                // Winkel ab 12 Uhr im Uhrzeigersinn, normiert auf 0f..1f
                var a = atan2(dx, -dy)
                if (a < 0f) a += (2f * Math.PI).toFloat()
                val pos = a / (2f * Math.PI).toFloat()
                out[y * SIZE + x] = if (pos <= fraction) full else dim
            }
        }
    }

    private fun drawMarker(out: IntArray, marker: Char, full: Int) {
        val glyph = LETTERS[marker.uppercaseChar()] ?: return
        blit(out, glyph, LETTER_W, LETTER_H, x0 = (SIZE - LETTER_W) / 2, y0 = 4, level = full)
    }

    private fun drawValue(out: IntArray, value: Int, full: Int) {
        val digits = value.coerceIn(0, 999).toString()
        val totalW = digits.length * DIGIT_W + (digits.length - 1) * DIGIT_GAP
        var x = (SIZE - totalW) / 2
        for (c in digits) {
            val glyph = DIGITS[c - '0']
            blit(out, glyph, DIGIT_W, DIGIT_H, x0 = x, y0 = 11, level = full)
            x += DIGIT_W + DIGIT_GAP
        }
    }

    private fun blit(out: IntArray, rows: Array<String>, w: Int, h: Int, x0: Int, y0: Int, level: Int) {
        for (ry in 0 until h) {
            val row = rows[ry]
            for (rx in 0 until w) {
                if (row[rx] != '#') continue
                val x = x0 + rx
                val y = y0 + ry
                if (x !in 0 until SIZE || y !in 0 until SIZE) continue
                out[y * SIZE + x] = level
            }
        }
    }

    private const val DIGIT_W = 5
    private const val DIGIT_H = 7
    private const val DIGIT_GAP = 1
    private const val LETTER_W = 3
    private const val LETTER_H = 5

    private val DIGITS: Array<Array<String>> = arrayOf(
        arrayOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."), // 0
        arrayOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."), // 1
        arrayOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"), // 2
        arrayOf("#####", "...#.", "..##.", "....#", "....#", "#...#", ".###."), // 3
        arrayOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."), // 4
        arrayOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."), // 5
        arrayOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."), // 6
        arrayOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."), // 7
        arrayOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."), // 8
        arrayOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##..")  // 9
    )

    /** 3x5-Marker. Absichtlich klein — es geht nur um „welche Metrik sehe ich". */
    private val LETTERS: Map<Char, Array<String>> = mapOf(
        'C' to arrayOf("###", "#..", "#..", "#..", "###"),
        'R' to arrayOf("##.", "#.#", "##.", "#.#", "#.#"),
        'B' to arrayOf("##.", "#.#", "##.", "#.#", "##."),
        'T' to arrayOf("###", ".#.", ".#.", ".#.", ".#."),
        'F' to arrayOf("###", "#..", "##.", "#..", "#.."),
        'N' to arrayOf("#.#", "###", "###", "#.#", "#.#"),
        'S' to arrayOf("###", "#..", "###", "..#", "###")
    )
}
