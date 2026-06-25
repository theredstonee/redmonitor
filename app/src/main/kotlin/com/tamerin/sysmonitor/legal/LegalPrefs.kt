package com.tamerin.sysmonitor.legal

import android.content.Context

/**
 * Speichert welche Legal-Versionen der User akzeptiert hat. Wenn die aktuelle
 * LegalConstants-Version höher ist, muss er beim nächsten Start neu zustimmen.
 */
object LegalPrefs {
    private const val PREFS = "legal_prefs"
    private const val K_PRIVACY_VERSION = "accepted_privacy_version"
    private const val K_TERMS_VERSION = "accepted_terms_version"
    private const val K_ACCEPTED_AT = "accepted_at_ms"

    fun acceptedPrivacyVersion(context: Context): Int =
        prefs(context).getInt(K_PRIVACY_VERSION, 0)

    fun acceptedTermsVersion(context: Context): Int =
        prefs(context).getInt(K_TERMS_VERSION, 0)

    fun acceptedAtMs(context: Context): Long =
        prefs(context).getLong(K_ACCEPTED_AT, 0L)

    fun hasAcceptedCurrent(context: Context): Boolean =
        acceptedPrivacyVersion(context) >= LegalConstants.PRIVACY_VERSION &&
            acceptedTermsVersion(context) >= LegalConstants.TERMS_VERSION

    fun markAccepted(context: Context) {
        prefs(context).edit()
            .putInt(K_PRIVACY_VERSION, LegalConstants.PRIVACY_VERSION)
            .putInt(K_TERMS_VERSION, LegalConstants.TERMS_VERSION)
            .putLong(K_ACCEPTED_AT, System.currentTimeMillis())
            .apply()
    }

    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
