package com.tamerin.sysmonitor.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class AppPermissions(
    val packageName: String,
    val displayName: String,
    val isSystem: Boolean,
    val granted: List<String>,
    val requested: List<String>
) {
    fun categories(): Set<PermCategory> = granted.mapNotNull { PermCategory.fromPermission(it) }.toSet()
}

@Immutable
data class CategoryGroup(
    val category: PermCategory,
    val apps: List<AppPermissions>
)

enum class PermCategory(val label: String, val description: String) {
    Location("Standort", "ACCESS_FINE/COARSE/BACKGROUND_LOCATION"),
    Camera("Kamera", "CAMERA"),
    Microphone("Mikrofon", "RECORD_AUDIO"),
    Contacts("Kontakte", "READ/WRITE_CONTACTS, GET_ACCOUNTS"),
    Calendar("Kalender", "READ/WRITE_CALENDAR"),
    Sms("SMS", "READ/SEND/RECEIVE_SMS, READ_CELL_BROADCASTS"),
    Phone("Telefon/Anrufe", "READ_PHONE_STATE, CALL_PHONE, CALL_LOG"),
    Storage("Speicher/Medien", "READ/WRITE_EXTERNAL_STORAGE, READ_MEDIA_*"),
    Body("Körper/Aktivität", "BODY_SENSORS, ACTIVITY_RECOGNITION"),
    Notifications("Benachrichtigungen", "POST_NOTIFICATIONS"),
    NearbyDevices("Geräte in der Nähe", "BLUETOOTH_SCAN/CONNECT, NEARBY_WIFI_DEVICES"),
    Special("Spezielle Rechte", "SYSTEM_ALERT_WINDOW, PACKAGE_USAGE_STATS, BIND_ACCESSIBILITY/DEVICE_ADMIN");

    companion object {
        fun fromPermission(name: String): PermCategory? = when (name) {
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION" -> Location
            "android.permission.CAMERA" -> Camera
            "android.permission.RECORD_AUDIO",
            "android.permission.CAPTURE_AUDIO_OUTPUT" -> Microphone
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.GET_ACCOUNTS" -> Contacts
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR" -> Calendar
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.RECEIVE_MMS",
            "android.permission.RECEIVE_WAP_PUSH",
            "android.permission.READ_CELL_BROADCASTS" -> Sms
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.CALL_PHONE",
            "android.permission.ANSWER_PHONE_CALLS",
            "android.permission.PROCESS_OUTGOING_CALLS",
            "android.permission.ADD_VOICEMAIL",
            "android.permission.USE_SIP",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG" -> Phone
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" -> Storage
            "android.permission.BODY_SENSORS",
            "android.permission.BODY_SENSORS_BACKGROUND",
            "android.permission.ACTIVITY_RECOGNITION" -> Body
            "android.permission.POST_NOTIFICATIONS" -> Notifications
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.NEARBY_WIFI_DEVICES",
            "android.permission.UWB_RANGING" -> NearbyDevices
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.PACKAGE_USAGE_STATS",
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            "android.permission.BIND_DEVICE_ADMIN",
            "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
            "android.permission.MANAGE_OVERLAY_PERMISSION",
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.SCHEDULE_EXACT_ALARM" -> Special
            else -> null
        }
    }
}

object PermissionAuditReader {

    suspend fun read(context: Context): List<AppPermissions> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS
        val pkgs: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(flags)
        }
        pkgs.mapNotNull { pi ->
            val requested = pi.requestedPermissions?.toList().orEmpty()
            if (requested.isEmpty()) return@mapNotNull null
            val flagsArr = pi.requestedPermissionsFlags
            val granted = requested.mapIndexedNotNull { i, perm ->
                val f = flagsArr?.getOrNull(i) ?: 0
                if ((f and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) perm else null
            }
            val ai = pi.applicationInfo ?: return@mapNotNull null
            val label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(pi.packageName)
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            AppPermissions(
                packageName = pi.packageName,
                displayName = label,
                isSystem = isSystem,
                granted = granted,
                requested = requested
            )
        }.sortedBy { it.displayName.lowercase() }
    }

    fun groupByCategory(apps: List<AppPermissions>): List<CategoryGroup> {
        val map = LinkedHashMap<PermCategory, MutableList<AppPermissions>>()
        for (cat in PermCategory.values()) map[cat] = mutableListOf()
        for (app in apps) {
            for (cat in app.categories()) {
                map.getValue(cat).add(app)
            }
        }
        return map.entries
            .filter { it.value.isNotEmpty() }
            .map { CategoryGroup(it.key, it.value.sortedBy { a -> a.displayName.lowercase() }) }
    }
}
