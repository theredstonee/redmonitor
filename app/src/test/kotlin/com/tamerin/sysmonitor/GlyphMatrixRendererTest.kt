package com.tamerin.sysmonitor

import com.tamerin.sysmonitor.glyph.GlyphMatrixRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Glyph-Renderer ist bewusst frei von SDK- und Android-Bezuegen, damit genau das
 * hier moeglich ist: das Bild fuer die Rueckseite des Phone (3) pruefen, ohne ein
 * Phone (3) zu haben.
 */
class GlyphMatrixRendererTest {

    private val n = GlyphMatrixRenderer.SIZE

    private fun IntArray.at(x: Int, y: Int) = this[y * n + x]

    @Test
    fun `Frame hat exakt die Matrixgroesse`() {
        val frame = GlyphMatrixRenderer.render(42, 0.42f, 'C')
        assertEquals(25, n)
        assertEquals(625, frame.size)
    }

    @Test
    fun `alle Werte liegen im gueltigen Helligkeitsbereich`() {
        val frame = GlyphMatrixRenderer.render(88, 0.88f, 'R')
        assertTrue(frame.all { it in 0..255 })
    }

    @Test
    fun `voller Ring ist heller als leerer Ring`() {
        val empty = GlyphMatrixRenderer.render(0, 0f, null)
        val full = GlyphMatrixRenderer.render(100, 1f, null)
        assertTrue(full.sum() > empty.sum())
    }

    @Test
    fun `Ring startet oben und laeuft im Uhrzeigersinn`() {
        // Bei 25 % muss die rechte Ringhaelfte hell sein, die linke dunkel
        val frame = GlyphMatrixRenderer.render(25, 0.25f, null)
        val right = frame.at(x = n - 1, y = n / 2)   // 3 Uhr
        val left = frame.at(x = 0, y = n / 2)        // 9 Uhr
        assertTrue("3 Uhr muss bei 25% gefuellt sein", right > left)
    }

    @Test
    fun `Helligkeit skaliert den Frame`() {
        val dim = GlyphMatrixRenderer.render(50, 0.5f, 'C', brightness = 40)
        val bright = GlyphMatrixRenderer.render(50, 0.5f, 'C', brightness = 255)
        assertTrue(dim.max() <= 40)
        assertEquals(255, bright.max())
        assertTrue(bright.sum() > dim.sum())
    }

    @Test
    fun `Helligkeit null ergibt ein dunkles Bild`() {
        val frame = GlyphMatrixRenderer.render(50, 0.5f, 'C', brightness = 0)
        assertTrue(frame.all { it == 0 })
    }

    @Test
    fun `Marker aendert das Bild`() {
        val without = GlyphMatrixRenderer.render(50, 0.5f, null)
        val with = GlyphMatrixRenderer.render(50, 0.5f, 'C')
        assertTrue(with.sum() > without.sum())
    }

    @Test
    fun `unbekannter Marker wird ignoriert statt zu werfen`() {
        val frame = GlyphMatrixRenderer.render(50, 0.5f, 'Z')
        assertEquals(625, frame.size)
    }

    @Test
    fun `dreistellige Werte passen noch in die Matrix`() {
        // 100 % Akku ist der Normalfall, nicht der Sonderfall
        val frame = GlyphMatrixRenderer.render(100, 1f, 'B')
        assertEquals(625, frame.size)
        assertTrue(frame.sum() > 0)
    }

    @Test
    fun `Werte ausserhalb des Bereichs werden geklemmt statt zu werfen`() {
        GlyphMatrixRenderer.render(-5, -1f, 'C')
        GlyphMatrixRenderer.render(9999, 5f, 'T')
        // Kein Absturz = bestanden; der Toy darf an keinem Messwert sterben
    }

    @Test
    fun `Ziffern liegen innerhalb des Rings`() {
        // Die Ecken duerfen nie leuchten, sonst ragt etwas aus der runden Matrix
        val frame = GlyphMatrixRenderer.render(100, 1f, 'B')
        assertEquals(0, frame.at(0, 0))
        assertEquals(0, frame.at(n - 1, 0))
        assertEquals(0, frame.at(0, n - 1))
        assertEquals(0, frame.at(n - 1, n - 1))
    }
}
