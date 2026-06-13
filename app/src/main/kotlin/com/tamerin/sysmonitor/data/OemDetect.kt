package com.tamerin.sysmonitor.data

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

enum class Oem {
    XIAOMI, SAMSUNG, ONEPLUS, OPPO, VIVO, HUAWEI, HONOR,
    REALME, ASUS, MOTOROLA, GOOGLE, NOTHING, SONY, LG, GENERIC
}

enum class OemRestrictionLevel {
    /** Stock Android or near-stock - barely any extra steps required. */
    LOW,
    /** Light tweaks (Samsung One UI, Asus etc.). */
    MEDIUM,
    /** Aggressive background killers (MIUI/HyperOS, ColorOS, FuntouchOS, EMUI). */
    HIGH
}

data class OemSpec(
    val oem: Oem,
    val displayName: String,
    val skinName: String,
    val skinVersion: String?,
    val restrictionLevel: OemRestrictionLevel,
    val isMiui: Boolean,
    val isHyperOs: Boolean,
    val isOneUi: Boolean,
    val isColorOs: Boolean,
    val notes: List<String>
)

object OemDetect {

    fun detect(): OemSpec {
        val mfg = (Build.MANUFACTURER ?: "").lowercase()
        val brand = (Build.BRAND ?: "").lowercase()

        val miuiVer = sysProp("ro.miui.ui.version.name")
        val hyperOsVer = sysProp("ro.mi.os.version.name")
        val oneUiVer = sysProp("ro.build.version.oneui") ?: sysProp("ro.build.version.semplatform")
        val colorOsVer = sysProp("ro.build.version.opporom") ?: sysProp("ro.oppo.theme.version")
        val funtouchVer = sysProp("ro.vivo.os.version") ?: sysProp("ro.vivo.os.build.display.id")
        val emuiVer = sysProp("ro.build.version.emui")
        val magicOsVer = sysProp("ro.build.version.magic")

        val isMiui = !miuiVer.isNullOrBlank()
        val isHyperOs = !hyperOsVer.isNullOrBlank()
        val isOneUi = !oneUiVer.isNullOrBlank()
        val isColorOs = !colorOsVer.isNullOrBlank()

        val oem = when {
            mfg.contains("xiaomi") || brand.contains("xiaomi") ||
                brand.contains("redmi") || brand.contains("poco") -> Oem.XIAOMI
            mfg.contains("samsung") -> Oem.SAMSUNG
            mfg.contains("oneplus") -> Oem.ONEPLUS
            mfg.contains("oppo") -> Oem.OPPO
            mfg.contains("vivo") || mfg.contains("iqoo") -> Oem.VIVO
            mfg.contains("huawei") -> Oem.HUAWEI
            mfg.contains("honor") -> Oem.HONOR
            mfg.contains("realme") -> Oem.REALME
            mfg.contains("asus") -> Oem.ASUS
            mfg.contains("motorola") || mfg.contains("lenovo") -> Oem.MOTOROLA
            mfg.contains("google") -> Oem.GOOGLE
            mfg.contains("nothing") -> Oem.NOTHING
            mfg.contains("sony") -> Oem.SONY
            mfg.contains("lge") || mfg.contains("lg ") -> Oem.LG
            else -> Oem.GENERIC
        }

        val skinName: String
        val skinVersion: String?
        when {
            isHyperOs -> { skinName = "HyperOS"; skinVersion = hyperOsVer }
            isMiui -> { skinName = "MIUI"; skinVersion = miuiVer }
            isOneUi -> { skinName = "One UI"; skinVersion = oneUiVer }
            isColorOs -> { skinName = "ColorOS"; skinVersion = colorOsVer }
            !funtouchVer.isNullOrBlank() -> { skinName = "FuntouchOS / OriginOS"; skinVersion = funtouchVer }
            !emuiVer.isNullOrBlank() -> { skinName = "EMUI"; skinVersion = emuiVer }
            !magicOsVer.isNullOrBlank() -> { skinName = "MagicOS"; skinVersion = magicOsVer }
            oem == Oem.GOOGLE -> { skinName = "Pixel"; skinVersion = Build.VERSION.RELEASE }
            oem == Oem.NOTHING -> { skinName = "Nothing OS"; skinVersion = Build.VERSION.RELEASE }
            else -> { skinName = "Android"; skinVersion = Build.VERSION.RELEASE }
        }

        val displayName = "${Build.MANUFACTURER ?: "?"} - $skinName${skinVersion?.let { " $it" } ?: ""}"

        val level = when (oem) {
            Oem.XIAOMI, Oem.OPPO, Oem.VIVO, Oem.HUAWEI, Oem.HONOR, Oem.REALME -> OemRestrictionLevel.HIGH
            Oem.ONEPLUS -> if (isColorOs) OemRestrictionLevel.HIGH else OemRestrictionLevel.MEDIUM
            Oem.SAMSUNG, Oem.ASUS, Oem.MOTOROLA, Oem.SONY, Oem.LG -> OemRestrictionLevel.MEDIUM
            Oem.GOOGLE, Oem.NOTHING, Oem.GENERIC -> OemRestrictionLevel.LOW
        }

        val notes = buildList {
            if (isMiui || isHyperOs) {
                add("Aggressive Hintergrund-Limits - Autostart explizit erlauben.")
                add("'MIUI-Optimierung' in Entwickleroptionen kann Shizuku/ADB stören - bei Problemen aus.")
                add("'Pop-ups im Hintergrund anzeigen' für Live-Updates nötig.")
                add("Speicher-Bereinigung kann die App im Hintergrund killen - 'Sperren' im Recents-Bildschirm.")
            }
            if (oem == Oem.SAMSUNG) {
                add("Akku-Optimierung & 'Schlafende Apps' sperrt sonst Updates.")
                add("Game Booster / Edge-Panel kann Overlay blockieren.")
            }
            if (oem == Oem.OPPO || oem == Oem.REALME || (oem == Oem.ONEPLUS && isColorOs)) {
                add("ColorOS Autostart + 'Im Hintergrund laufen lassen' aktivieren.")
            }
            if (oem == Oem.VIVO) {
                add("Hintergrund-Energieverbrauch hochsetzen + 'Hintergrund-Aufrufe' erlauben.")
            }
            if (oem == Oem.HUAWEI || oem == Oem.HONOR) {
                add("App-Start manuell verwalten - alle drei Toggles AN.")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add("Eingeschränkter App-Standby-Bucket kann die App ausbremsen.")
            }
        }

        return OemSpec(
            oem = oem,
            displayName = displayName,
            skinName = skinName,
            skinVersion = skinVersion,
            restrictionLevel = level,
            isMiui = isMiui,
            isHyperOs = isHyperOs,
            isOneUi = isOneUi,
            isColorOs = isColorOs,
            notes = notes
        )
    }

    // ===== Permission checks =====

    fun isIgnoringBatteryOpt(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
    }

    fun hasUsageStats(context: Context): Boolean {
        return runCatching {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun hasOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

    // ===== Intents =====

    fun batteryOptIntent(context: Context): Intent {
        return if (isIgnoringBatteryOpt(context)) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun usageStatsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun overlayPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun developerSettingsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** OEM-specific autostart manager. Returns null if none is known for this OEM. */
    fun autostartIntent(oem: Oem): Intent? {
        val intent = when (oem) {
            Oem.XIAOMI -> Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            Oem.OPPO, Oem.REALME -> Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
            Oem.ONEPLUS -> Intent().setClassName(
                "com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
            )
            Oem.VIVO -> Intent().setClassName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            )
            Oem.HUAWEI, Oem.HONOR -> Intent().setClassName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
            Oem.SAMSUNG -> Intent().setClassName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
            Oem.ASUS -> Intent().setClassName(
                "com.asus.mobilemanager",
                "com.asus.mobilemanager.MainActivity"
            )
            else -> null
        }
        return intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** MIUI/HyperOS only: 'Other permissions' contains background pop-ups, lockscreen etc. */
    fun miuiOtherPermissionsIntent(context: Context): Intent? {
        return runCatching {
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                .putExtra("extra_pkgname", context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.getOrNull()
    }

    fun canResolve(context: Context, intent: Intent): Boolean {
        return intent.resolveActivity(context.packageManager) != null
    }

    fun safeStart(context: Context, intent: Intent): Boolean {
        return runCatching {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else false
        }.getOrDefault(false)
    }

    private fun sysProp(name: String): String? = runCatching {
        val cls = Class.forName("android.os.SystemProperties")
        val get = cls.getMethod("get", String::class.java)
        (get.invoke(null, name) as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
