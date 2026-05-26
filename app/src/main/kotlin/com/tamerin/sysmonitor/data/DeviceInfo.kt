package com.tamerin.sysmonitor.data

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.sqrt

data class DisplaySnapshot(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val refreshRateHz: Float,
    val sizeInches: Float
)

data class DeviceInfoSnapshot(
    val manufacturer: String,
    val model: String,
    val device: String,
    val board: String,
    val brand: String,
    val product: String,
    val androidVersion: String,
    val sdk: Int,
    val securityPatch: String,
    val kernel: String,
    val bootloader: String,
    val fingerprint: String,
    val buildId: String,
    val buildHost: String,
    val buildTimeMs: Long,
    val uptimeMs: Long,
    val javaVm: String,
    val openSslVersion: String
)

object DeviceInfo {
    fun readDisplay(activity: Activity): DisplaySnapshot {
        val wm = activity.windowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        @Suppress("DEPRECATION")
        val refresh = wm.defaultDisplay.refreshRate
        val wIn = metrics.widthPixels / metrics.xdpi
        val hIn = metrics.heightPixels / metrics.ydpi
        val diag = sqrt((wIn * wIn + hIn * hIn).toDouble()).toFloat()
        return DisplaySnapshot(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            refreshRateHz = refresh,
            sizeInches = diag
        )
    }

    fun readDevice(): DeviceInfoSnapshot {
        return DeviceInfoSnapshot(
            manufacturer = Build.MANUFACTURER ?: "?",
            model = Build.MODEL ?: "?",
            device = Build.DEVICE ?: "?",
            board = Build.BOARD ?: "?",
            brand = Build.BRAND ?: "?",
            product = Build.PRODUCT ?: "?",
            androidVersion = Build.VERSION.RELEASE ?: "?",
            sdk = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH ?: "?",
            kernel = System.getProperty("os.version") ?: "?",
            bootloader = Build.BOOTLOADER ?: "?",
            fingerprint = Build.FINGERPRINT ?: "?",
            buildId = Build.ID ?: "?",
            buildHost = Build.HOST ?: "?",
            buildTimeMs = Build.TIME,
            uptimeMs = SystemClock.elapsedRealtime(),
            javaVm = "${System.getProperty("java.vm.name") ?: "?"} ${System.getProperty("java.vm.version") ?: ""}",
            openSslVersion = runCatching {
                Class.forName("com.android.org.conscrypt.NativeCrypto")
                    .getMethod("get_OPENSSL_VERSION_NUMBER")
                    .invoke(null).toString()
            }.getOrDefault("—")
        )
    }
}
