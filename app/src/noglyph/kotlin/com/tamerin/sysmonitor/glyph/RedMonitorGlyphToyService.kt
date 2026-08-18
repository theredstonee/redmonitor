package com.tamerin.sysmonitor.glyph

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * No-Op-Variante des Glyph-Toys ohne Nothing-SDK (siehe `src/noglyph/.../GlyphSupport.kt`).
 *
 * Die Klasse existiert nur, damit der `<service>`-Eintrag im Manifest in beiden
 * Build-Varianten auf eine echte Klasse zeigt. Sie bindet nichts und zeichnet nichts.
 */
class RedMonitorGlyphToyService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
