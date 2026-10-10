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
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tamerin.sysmonitor.R
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
import com.tamerin.sysmonitor.ui.PrivacyDashboardStandaloneActivity
import com.tamerin.sysmonitor.ui.ShellTerminalStandaloneActivity
import com.tamerin.sysmonitor.ui.SpeedTestStandaloneActivity
import com.tamerin.sysmonitor.ui.StorageAnalyzerStandaloneActivity
import com.tamerin.sysmonitor.ui.WakelockStandaloneActivity
import com.tamerin.sysmonitor.ui.components.HubEntry
import com.tamerin.sysmonitor.ui.components.HubGrid

@Composable
fun InfoHubScreen(onNavigate: (String) -> Unit) {
    val new = stringResource(R.string.bench_badge_new)
    val entries = listOf(
        HubEntry(Routes.INFO_THERMAL, stringResource(R.string.bench_info_thermal_title), stringResource(R.string.bench_info_thermal_sub), Icons.Filled.Thermostat),
        HubEntry(Routes.INFO_TELEPHONY, stringResource(R.string.bench_info_telephony_title), stringResource(R.string.bench_info_telephony_sub), Icons.Filled.SimCard),
        HubEntry(Routes.INFO_WIFI, stringResource(R.string.bench_info_wifi_title), stringResource(R.string.bench_info_wifi_sub), Icons.Filled.Wifi),
        HubEntry(Routes.INFO_BLUETOOTH, "Bluetooth", stringResource(R.string.bench_info_bluetooth_sub), Icons.Filled.Bluetooth),
        HubEntry(Routes.INFO_NFC, "NFC", stringResource(R.string.bench_info_nfc_sub), Icons.Filled.Nfc),
        HubEntry(Routes.INFO_CAMERAS, stringResource(R.string.bench_info_cameras_title), stringResource(R.string.bench_info_cameras_sub), Icons.Filled.CameraAlt),
        HubEntry(Routes.INFO_CODECS, stringResource(R.string.bench_info_codecs_title), stringResource(R.string.bench_info_codecs_sub), Icons.Filled.Movie),
        HubEntry(Routes.INFO_RUNNING, stringResource(R.string.bench_info_running_title), stringResource(R.string.bench_info_running_sub), Icons.Filled.Memory),
        HubEntry(Routes.INFO_INSTALLED, stringResource(R.string.bench_info_installed_title), stringResource(R.string.bench_info_installed_sub), Icons.Filled.Apps),
        HubEntry(Routes.INFO_FEATURES, stringResource(R.string.bench_info_features_title), stringResource(R.string.bench_info_features_sub), Icons.Filled.DeviceHub),
        HubEntry(Routes.INFO_PROPS, stringResource(R.string.bench_info_props_title), stringResource(R.string.bench_info_props_sub), Icons.Filled.Storage),
        HubEntry(Routes.INFO_LOGCAT, "Logcat", stringResource(R.string.bench_info_logcat_sub), Icons.Filled.ListAlt),
        HubEntry(Routes.INFO_DOZE, stringResource(R.string.bench_info_doze_title), stringResource(R.string.bench_info_doze_sub), Icons.Filled.BatteryAlert),
        HubEntry(Routes.OEM_SETUP, stringResource(R.string.bench_info_oem_title), stringResource(R.string.bench_info_oem_sub), Icons.Filled.Tune),
        HubEntry("info/network-usage", stringResource(R.string.bench_info_net_usage_title), stringResource(R.string.bench_info_net_usage_sub), Icons.Filled.NetworkCheck,
            activityClass = NetworkUsageStandaloneActivity::class.java, badge = new),
        HubEntry("info/wakelocks", stringResource(R.string.bench_info_wakelocks_title), stringResource(R.string.bench_info_wakelocks_sub), Icons.Filled.Lock,
            activityClass = WakelockStandaloneActivity::class.java, badge = new),
        HubEntry("info/notif-log", stringResource(R.string.bench_info_notif_title), stringResource(R.string.bench_info_notif_sub), Icons.Filled.Notifications,
            activityClass = NotificationLogStandaloneActivity::class.java, badge = new),
        HubEntry("info/perm-audit", stringResource(R.string.bench_info_perm_audit_title), stringResource(R.string.bench_info_perm_audit_sub), Icons.Filled.Security,
            activityClass = PermissionAuditStandaloneActivity::class.java, badge = new),
        HubEntry("info/shell", stringResource(R.string.bench_info_shell_title), stringResource(R.string.bench_info_shell_sub), Icons.Filled.Terminal,
            activityClass = ShellTerminalStandaloneActivity::class.java, badge = new),
        HubEntry("info/discharge", stringResource(R.string.bench_info_discharge_title), stringResource(R.string.bench_info_discharge_sub), Icons.Filled.ShowChart,
            activityClass = DischargeCurveStandaloneActivity::class.java, badge = new),
        HubEntry("info/charging-limit", "Charging-Limit", stringResource(R.string.bench_info_charging_limit_sub), Icons.Filled.BatteryChargingFull,
            activityClass = ChargingLimitStandaloneActivity::class.java, badge = new),
        HubEntry("info/battery-doctor", stringResource(R.string.bench_info_battery_doctor_title), stringResource(R.string.bench_info_battery_doctor_sub), Icons.Filled.HealthAndSafety,
            activityClass = BatteryDoctorStandaloneActivity::class.java, badge = new),
        HubEntry("info/storage", stringResource(R.string.bench_info_storage_title), stringResource(R.string.bench_info_storage_sub), Icons.Filled.FolderOpen,
            activityClass = StorageAnalyzerStandaloneActivity::class.java, badge = new),
        HubEntry("info/apk-extract", stringResource(R.string.bench_info_apk_title), stringResource(R.string.bench_info_apk_sub), Icons.Filled.GetApp,
            activityClass = ApkExtractorStandaloneActivity::class.java, badge = new),
        HubEntry("info/crash-logs", stringResource(R.string.bench_info_crash_title), stringResource(R.string.bench_info_crash_sub), Icons.Filled.BugReport,
            activityClass = CrashLogStandaloneActivity::class.java, badge = new),
        HubEntry("info/speed-test", stringResource(R.string.bench_info_speedtest_title), stringResource(R.string.bench_info_speedtest_sub), Icons.Filled.Speed,
            activityClass = SpeedTestStandaloneActivity::class.java, badge = new),
        HubEntry("info/ping", stringResource(R.string.bench_info_ping_title), stringResource(R.string.bench_info_ping_sub), Icons.Filled.NetworkPing,
            activityClass = PingMonitorStandaloneActivity::class.java, badge = new),
        HubEntry("info/gnss", stringResource(R.string.bench_info_gnss_title), stringResource(R.string.bench_info_gnss_sub), Icons.Filled.Satellite,
            activityClass = GnssLiveStandaloneActivity::class.java, badge = new),
        HubEntry("info/perfetto", stringResource(R.string.bench_info_perfetto_title), stringResource(R.string.bench_info_perfetto_sub), Icons.Filled.Timeline,
            activityClass = PerfettoTraceStandaloneActivity::class.java, badge = new),
        HubEntry("info/dpi-anim", stringResource(R.string.bench_info_dpi_title), stringResource(R.string.bench_info_dpi_sub), Icons.Filled.AspectRatio,
            activityClass = DpiAnimStandaloneActivity::class.java, badge = new),
        HubEntry("info/bg-restrict", stringResource(R.string.bench_info_bg_restrict_title), stringResource(R.string.bench_info_bg_restrict_sub), Icons.Filled.Block,
            activityClass = BackgroundRestrictStandaloneActivity::class.java, badge = new),
        HubEntry("info/privacy-dashboard", stringResource(R.string.bench_info_privacy_title), stringResource(R.string.bench_info_privacy_sub), Icons.Filled.PrivacyTip,
            activityClass = PrivacyDashboardStandaloneActivity::class.java, badge = new)
    )
    HubGrid(entries = entries, onClick = { onNavigate(it.route) })
}
