package com.tamerin.sysmonitor.glyph

import android.content.Context

/**
 * No-Op-Variante. Wird kompiliert, wenn `app/libs/glyph-matrix-sdk-2.0.aar` fehlt —
 * das SDK ist proprietaer und darf nicht im Repo liegen (siehe .gitignore).
 * Dadurch baut ein frischer Clone durch, nur eben ohne Glyph-Support.
 *
 * Muss signaturgleich zu `src/glyph/.../GlyphSupport.kt` bleiben.
 */
object GlyphSupport {

    const val COMPILED_IN = false

    fun isDeviceSupported(): Boolean = false

    fun matrixLength(): Int = GlyphMatrixRenderer.SIZE

    fun openToysManager(context: Context): Boolean = false
}
