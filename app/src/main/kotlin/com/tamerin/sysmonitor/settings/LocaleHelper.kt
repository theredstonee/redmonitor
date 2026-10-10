package com.tamerin.sysmonitor.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Per-App-Sprache für eine reine Compose-/ComponentActivity-App (ohne AppCompat). Jede
 * Activity wickelt in `attachBaseContext` ihren Base-Context hiermit ein; dadurch greift
 * die gewählte Sprache auf die Ressourcen (res/values vs. res/values-en). Umschalten =
 * Pref setzen + Activity `recreate()`.
 *
 * "system" → der Systemsprache folgen (kein Override). "de"/"en" → erzwingen.
 */
object LocaleHelper {

    val SUPPORTED = listOf("system", "de", "en")

    fun wrap(base: Context): Context {
        val tag = AppPrefs.appLocale(base)
        if (tag == "system" || tag.isBlank()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
