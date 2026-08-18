package com.tamerin.sysmonitor

import com.tamerin.sysmonitor.data.AppActions
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.update.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests fuer die reinen Parser der App. Alles hier laeuft ohne Geraet, ohne Shizuku
 * und ohne Android-Framework — das sind genau die Funktionen, die stillschweigend
 * kaputtgehen, wenn sich ein Ausgabeformat aendert.
 */
class UpdateCheckerTest {

    @Test
    fun `neuere Version wird erkannt`() {
        assertTrue(UpdateChecker.compareVersions("1.7.0", "1.6.4") > 0)
        assertTrue(UpdateChecker.compareVersions("1.6.10", "1.6.9") > 0)
        assertTrue(UpdateChecker.compareVersions("2.0.0", "1.99.99") > 0)
    }

    @Test
    fun `aeltere Version wird erkannt`() {
        assertTrue(UpdateChecker.compareVersions("1.6.3", "1.6.4") < 0)
        assertTrue(UpdateChecker.compareVersions("1.5.0", "1.6.0") < 0)
    }

    @Test
    fun `gleiche Version ergibt null`() {
        assertEquals(0, UpdateChecker.compareVersions("1.6.4", "1.6.4"))
    }

    @Test
    fun `fehlende Stellen zaehlen als null`() {
        // "1.7" und "1.7.0" sind dieselbe Version
        assertEquals(0, UpdateChecker.compareVersions("1.7", "1.7.0"))
        assertTrue(UpdateChecker.compareVersions("1.7.1", "1.7") > 0)
    }

    @Test
    fun `Muell fuehrt nicht zu falschem Update-Hinweis`() {
        // Nicht-numerische Teile werden verworfen, nicht als Fehler geworfen
        assertEquals(0, UpdateChecker.compareVersions("", ""))
        assertTrue(UpdateChecker.compareVersions("1.7.0-beta", "1.7.0") >= 0)
    }
}

class DumpsysBatteryParserTest {

    @Test
    fun `PD-Wattage wird aus dumpsys gelesen`() {
        val out = """
            Current Battery Service state:
              AC powered: false
              USB powered: true
              Wireless powered: false
              Max charging current: 3000000
              Max charging voltage: 9000000
              level: 67
        """.trimIndent()

        val parsed = ShizukuHelper.parseDumpsys(out)

        assertEquals(3_000_000L, parsed.maxChargingCurrentUa)
        assertEquals(9_000_000L, parsed.maxChargingVoltageUv)
        assertTrue(parsed.usbPowered)
        assertFalse(parsed.acPowered)
        assertFalse(parsed.wirelessPowered)
    }

    @Test
    fun `leere Ausgabe ergibt Nullwerte statt Absturz`() {
        val parsed = ShizukuHelper.parseDumpsys("")
        assertEquals(0L, parsed.maxChargingCurrentUa)
        assertFalse(parsed.acPowered)
    }

    @Test
    fun `unerwartete Werte kippen den Parser nicht`() {
        val parsed = ShizukuHelper.parseDumpsys(
            "Max charging current: n/a\nAC powered: true"
        )
        assertEquals(0L, parsed.maxChargingCurrentUa)
        assertTrue(parsed.acPowered)
    }
}

class AppOpsParserTest {

    @Test
    fun `beide Background-Ops werden gelesen`() {
        val out = """
            com.example.app::
              RUN_IN_BACKGROUND: allow
              RUN_ANY_IN_BACKGROUND: deny
        """.trimIndent()

        val state = AppActions.parseAppOps(out)

        assertEquals("allow", state.runInBackground)
        assertEquals("deny", state.runAnyInBackground)
    }

    @Test
    fun `Semikolon-Suffix wird abgeschnitten`() {
        // Manche ROMs haengen ";time=..." an
        val state = AppActions.parseAppOps("RUN_IN_BACKGROUND: ignore; time=+1h2m")
        assertEquals("ignore", state.runInBackground)
    }

    @Test
    fun `RUN_ANY wird nicht faelschlich als RUN gelesen`() {
        val state = AppActions.parseAppOps("RUN_ANY_IN_BACKGROUND: allow")
        assertEquals(null, state.runInBackground)
        assertEquals("allow", state.runAnyInBackground)
    }

    @Test
    fun `fehlende Ops ergeben null`() {
        val state = AppActions.parseAppOps("COARSE_LOCATION: allow")
        assertEquals(null, state.runInBackground)
        assertEquals(null, state.runAnyInBackground)
    }
}
