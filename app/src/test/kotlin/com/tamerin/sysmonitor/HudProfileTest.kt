package com.tamerin.sysmonitor

import com.tamerin.sysmonitor.overlay.HudConfig
import com.tamerin.sysmonitor.overlay.HudProfileMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HudProfileTest {

    private fun config(mode: HudProfileMode, vararg apps: String) =
        HudConfig.DEFAULT.copy(profileMode = mode, profileApps = apps.toSet())

    @Test
    fun `ALWAYS zeigt das HUD immer`() {
        val c = config(HudProfileMode.ALWAYS)
        assertTrue(c.isVisibleFor("com.spiel.xyz"))
        assertTrue(c.isVisibleFor(null))
    }

    @Test
    fun `ONLY_IN zeigt nur die ausgewaehlten Apps`() {
        val c = config(HudProfileMode.ONLY_IN, "com.spiel.xyz")
        assertTrue(c.isVisibleFor("com.spiel.xyz"))
        assertFalse(c.isVisibleFor("com.whatsapp"))
    }

    @Test
    fun `EXCEPT_IN blendet die ausgewaehlten Apps aus`() {
        val c = config(HudProfileMode.EXCEPT_IN, "com.netflix")
        assertFalse(c.isVisibleFor("com.netflix"))
        assertTrue(c.isVisibleFor("com.spiel.xyz"))
    }

    @Test
    fun `unbekannte Vordergrund-App laesst das HUD sichtbar`() {
        // Ohne Nutzungsdaten-Zugriff liefert der Reader null. Das HUD darf dann NICHT
        // dauerhaft verschwinden — sonst sucht der User den Fehler woanders.
        assertTrue(config(HudProfileMode.ONLY_IN, "com.spiel.xyz").isVisibleFor(null))
        assertTrue(config(HudProfileMode.EXCEPT_IN, "com.netflix").isVisibleFor(null))
    }

    @Test
    fun `leere Auswahl bei ONLY_IN blendet ueberall aus`() {
        // Konsequent: "nur in diesen Apps" ohne Apps heisst nirgends
        assertFalse(config(HudProfileMode.ONLY_IN).isVisibleFor("com.spiel.xyz"))
    }

    @Test
    fun `leere Auswahl bei EXCEPT_IN zeigt ueberall`() {
        assertTrue(config(HudProfileMode.EXCEPT_IN).isVisibleFor("com.spiel.xyz"))
    }

    @Test
    fun `mehrere Apps werden alle beruecksichtigt`() {
        val c = config(HudProfileMode.ONLY_IN, "com.a", "com.b", "com.c")
        assertTrue(c.isVisibleFor("com.b"))
        assertFalse(c.isVisibleFor("com.d"))
    }
}
