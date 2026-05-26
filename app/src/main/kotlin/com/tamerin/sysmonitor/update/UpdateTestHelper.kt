package com.tamerin.sysmonitor.update

import android.content.Context

/**
 * Dev-only helper to simulate update flows without touching the real GitHub state.
 * - triggerFakeUpdate: pushes a fake release into the notifier + clears the dismissed flag
 *   so the next OverviewScreen / startup will show the banner + dialog
 * - clearDismissed: removes any "ignored" version flag so previously-dismissed updates show again
 */
object UpdateTestHelper {

    private const val PREFS = "update_test"
    private const val K_FAKE_VERSION = "fake_version"

    fun triggerFakeUpdate(context: Context) {
        val fake = ReleaseInfo(
            versionName = "99.0.0",
            tagName = "v99.0.0",
            name = "RedMonitor v99.0.0 (Test)",
            body = "Dies ist eine simulierte Test-Version.\n\n" +
                "• Beispiel-Feature A\n" +
                "• Beispiel-Feature B\n" +
                "• Beispiel-Bugfix",
            isPrerelease = false,
            htmlUrl = "https://github.com/theredstonee/redmonitor/releases",
            apkUrl = null,
            apkSizeBytes = 0
        )
        // Clear any "dismissed" flag for this fake version so the dialog/banner shows
        UpdatePrefs.dismissVersion(context, "")  // clear by setting to empty
        UpdatePrefs.setLatestSeenVersion(context, fake.versionName)
        UpdateNotifier.show(context, fake)
        // Stash so OverviewScreen + startup pick it up
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(K_FAKE_VERSION, fake.versionName)
            .apply()
    }

    fun clearDismissed(context: Context) {
        UpdatePrefs.dismissVersion(context, "")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun hasPendingFake(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(K_FAKE_VERSION, null) != null

    fun consumeFake(context: Context): ReleaseInfo? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val v = sp.getString(K_FAKE_VERSION, null) ?: return null
        sp.edit().remove(K_FAKE_VERSION).apply()
        return ReleaseInfo(
            versionName = v,
            tagName = "v$v",
            name = "RedMonitor v$v (Test)",
            body = "Test-Update — wurde manuell ausgelöst",
            isPrerelease = false,
            htmlUrl = "https://github.com/theredstonee/redmonitor/releases",
            apkUrl = null,
            apkSizeBytes = 0
        )
    }
}
