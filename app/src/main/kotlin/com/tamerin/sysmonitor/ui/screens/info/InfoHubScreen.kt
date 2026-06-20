package com.tamerin.sysmonitor.ui.screens.info

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.Composable
import com.tamerin.sysmonitor.Routes
import com.tamerin.sysmonitor.ui.NetworkUsageStandaloneActivity
import com.tamerin.sysmonitor.ui.NotificationLogStandaloneActivity
import com.tamerin.sysmonitor.ui.PermissionAuditStandaloneActivity
import com.tamerin.sysmonitor.ui.ShellTerminalStandaloneActivity
import com.tamerin.sysmonitor.ui.WakelockStandaloneActivity
import com.tamerin.sysmonitor.ui.components.HubEntry
import com.tamerin.sysmonitor.ui.components.HubGrid

private val INFO_ENTRIES = listOf(
    HubEntry(Routes.INFO_THERMAL, "Thermalzonen", "Live-Temperaturen", Icons.Filled.Thermostat),
    HubEntry(Routes.INFO_TELEPHONY, "SIM & Telefonie", "Operator, Netz, Signal", Icons.Filled.SimCard),
    HubEntry(Routes.INFO_WIFI, "WLAN-Scan", "Umliegende Netze", Icons.Filled.Wifi),
    HubEntry(Routes.INFO_BLUETOOTH, "Bluetooth", "Adapter + gekoppelt", Icons.Filled.Bluetooth),
    HubEntry(Routes.INFO_NFC, "NFC", "Status & Verfügbarkeit", Icons.Filled.Nfc),
    HubEntry(Routes.INFO_CAMERAS, "Kameras", "Specs aller Kameras", Icons.Filled.CameraAlt),
    HubEntry(Routes.INFO_CODECS, "Codecs", "Audio & Video, HW?", Icons.Filled.Movie),
    HubEntry(Routes.INFO_RUNNING, "Laufende Apps", "Aktiv + RAM", Icons.Filled.Memory),
    HubEntry(Routes.INFO_INSTALLED, "Installierte Apps", "Pakete + Größe", Icons.Filled.Apps),
    HubEntry(Routes.INFO_FEATURES, "Hardware-Features", "Was hat das Gerät", Icons.Filled.DeviceHub),
    HubEntry(Routes.INFO_PROPS, "System-Properties", "Alle Build-Props", Icons.Filled.Storage),
    HubEntry(Routes.INFO_LOGCAT, "Logcat", "Live System-Logs (Shizuku)", Icons.Filled.ListAlt),
    HubEntry(Routes.INFO_DOZE, "Doze-Whitelist", "Akku-Optimierung verwalten", Icons.Filled.BatteryAlert),
    HubEntry(Routes.OEM_SETUP, "Geräte-Setup", "MIUI/HyperOS/OneUI Permissions", Icons.Filled.Tune),
    HubEntry("info/network-usage", "Pro-App Traffic", "WLAN + Mobil pro App", Icons.Filled.NetworkCheck,
        activityClass = NetworkUsageStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("info/wakelocks", "Wake-Locks", "Was hält das Gerät wach", Icons.Filled.Lock,
        activityClass = WakelockStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("info/notif-log", "Notification-Log", "Alle eingehenden Notifications", Icons.Filled.Notifications,
        activityClass = NotificationLogStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("info/perm-audit", "Permission-Audit", "Wer hat welche Rechte", Icons.Filled.Security,
        activityClass = PermissionAuditStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("info/shell", "Shell-Terminal", "ADB-Shell in-app via Shizuku", Icons.Filled.Terminal,
        activityClass = ShellTerminalStandaloneActivity::class.java, badge = "NEU")
)

@Composable
fun InfoHubScreen(onNavigate: (String) -> Unit) {
    HubGrid(entries = INFO_ENTRIES, onClick = { onNavigate(it.route) })
}
