package com.tamerin.sysmonitor.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates an app-specific baseline profile that precompiles RedMonitor's hot
 * startup + critical-tab paths. Run with:
 *
 *     ./gradlew :app:generateReleaseBaselineProfile
 *
 * The plugin will start the app, drive it through this scenario on a connected
 * device, capture the methods that were JIT-compiled, and write the profile to
 *
 *     app/src/main/baseline-prof.txt
 *
 * which is bundled into the release APK so the OS AOT-compiles those paths at
 * install time. Net win: ~30% faster startup + smoother first scroll/animation.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.tamerin.sysmonitor",
        includeInStartupProfile = true
    ) {
        // Cold start the launcher activity. profileinstaller picks up startup
        // profile separately from the runtime profile.
        startActivityAndWait()

        // Touch each top-level tab once so the profile covers everything the
        // user sees in the first 5 seconds.
        listOf("System", "Tasks", "Bench", "Tests", "Info", "Live").forEach { label ->
            device.findObject(androidx.test.uiautomator.By.text(label))?.click()
            device.waitForIdle()
        }
    }
}
