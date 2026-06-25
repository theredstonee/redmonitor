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
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Block
import androidx.compose.runtime.Composable
import com.tamerin.sysmonitor.Routes
import com.tamerin.sysmonitor.ui.ApkExtractorStandaloneActivity
import com.tamerin.sysmonitor.ui.BackgroundRestrictStandaloneActivity
import com.tamerin.sysmonitor.ui.BatteryDoctorStandaloneActivity
import com.tamerin.sysmonitor.ui.ChargingLimitStandaloneActivity
import com.tamerin.sysmonitor.ui.CrashLogStandaloneActivity
import com.tamerin.sysmonitor.ui.DischargeCurveStandaloneActivity
import com.tamerin.sysmonitor.ui.DpiAnimStandaloneActivity
import com.tamerin.sysmonitor.ui.GnssLiveStandaloneActivity
import com.tamerin.sysmonitor.ui.NetworkUsageStandaloneActivity
import com.tamerin.sysmonitor.ui.NotificationLogStandaloneActivity
import com.tamerin.sysmonitor.ui.PerfettoTraceStandaloneActivity
import com.tamerin.sysmonitor.ui.PermissionAuditStandaloneActivity
import com.tamerin.sysmonitor.ui.PingMonitorStandaloneActivity
import com.tamerin.sysmonitor.ui.ShellTerminalStandaloneActivity
import com.tamerin.sysmonitor.ui.SpeedTestStandaloneActivity
import com.tamerin.sysmonitor.ui.StorageAnalyzerStandaloneActivity
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
        activityClass = ShellTerminalStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("info/discharge", "Akku-Verlauf", "Discharge-Kurve aus History", Icons.Filled.ShowChart,
        activityClass = DischargeCurveStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("info/charging-limit", "Charging-Limit", "Auto-Stop bei X % via Shizuku", Icons.Filled.BatteryChargingFull,
        activityClass = ChargingLimitStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("info/battery-doctor", "Akku-Doktor", "Wer killt deinen Akku?", Icons.Filled.HealthAndSafety,
        activityClass = BatteryDoctorStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("info/storage", "Storage-Analyzer", "Was frisst deinen Speicher", Icons.Filled.FolderOpen,
        activityClass = StorageAnalyzerStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("info/apk-extract", "APK-Extract", "Installierte App als APK ziehen", Icons.Filled.GetApp,
        activityClass = ApkExtractorStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("info/crash-logs", "Crash-Logs", "Tombstones + Dropbox-Reports", Icons.Filled.BugReport,
        activityClass = CrashLogStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("info/speed-test", "Speed-Test", "Cloudflare Down/Up + Latency", Icons.Filled.Speed,
        activityClass = SpeedTestStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("info/ping", "Ping-Monitor", "4 Server live + Jitter-Graph", Icons.Filled.NetworkPing,
        activityClass = PingMonitorStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("info/gnss", "GNSS Live", "Satelliten-Status + SNR", Icons.Filled.Satellite,
        activityClass = GnssLiveStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("info/perfetto", "Perfetto-Trace", "System-Trace für ui.perfetto.dev", Icons.Filled.Timeline,
        activityClass = PerfettoTraceStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("info/dpi-anim", "DPI / Animation", "wm density + animator-scales", Icons.Filled.AspectRatio,
        activityClass = DpiAnimStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("info/bg-restrict", "BG-Restrict", "Bulk RUN_ANY_IN_BACKGROUND-Toggle", Icons.Filled.Block,
        activityClass = BackgroundRestrictStandaloneActivity::class.java, badge = "NEU")
)

@Composable
fun InfoHubScreen(onNavigate: (String) -> Unit) {
    HubGrid(entries = INFO_ENTRIES, onClick = { onNavigate(it.route) })
}
