package com.tamerin.sysmonitor.glyph

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nothing.ketchum.Common

/**
 * Echte Variante — wird nur kompiliert, wenn `app/libs/glyph-matrix-sdk-2.0.aar`
 * vorliegt (siehe app/build.gradle.kts). Das Gegenstueck ohne SDK liegt unter
 * `src/noglyph` mit identischem Klassennamen.
 */
object GlyphSupport {

    const val COMPILED_IN = true

    /**
     * true nur auf dem Nothing Phone (3) — nur das hat die 25x25-Matrix.
     *
     * Die Vorpruefung auf Hersteller und SDK_INT ist kein Luxus: das SDK ist gegen
     * API 33 gebaut, RedMonitor laeuft ab 29. Ohne den Guard wuerde auf einem
     * Android-10-Geraet eine com.nothing.ketchum-Klasse geladen, die dort nicht
     * verifizierbar ist. Erst Hersteller pruefen, dann erst das SDK anfassen.
     */
    fun isDeviceSupported(): Boolean {
        if (!isNothingDevice()) return false
        return runCatching { Common.is23112() }.getOrDefault(false)
    }

    private fun isNothingDevice(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val mfg = (Build.MANUFACTURER ?: "").lowercase()
        val brand = (Build.BRAND ?: "").lowercase()
        return mfg.contains("nothing") || brand.contains("nothing")
    }

    fun matrixLength(): Int =
        if (!isNothingDevice()) GlyphMatrixRenderer.SIZE
        else runCatching { Common.getDeviceMatrixLength() }
            .getOrDefault(GlyphMatrixRenderer.SIZE)
            .takeIf { it > 0 } ?: GlyphMatrixRenderer.SIZE

    /**
     * Oeffnet Nothings Toys-Manager, damit der User RedMonitor als Toy aktivieren
     * kann. Existiert erst ab System-Version 20250829 — schlaegt es fehl, geben
     * wir false zurueck und die UI zeigt stattdessen die manuelle Anleitung.
     */
    fun openToysManager(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent().setComponent(
                ComponentName(
                    "com.nothing.thirdparty",
                    "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity"
                )
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)
}
