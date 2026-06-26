package com.tamerin.sysmonitor.ui

import androidx.compose.runtime.Composable
import com.tamerin.sysmonitor.ui.screens.BarometerScreen
import com.tamerin.sysmonitor.ui.screens.BatteryScreen
import com.tamerin.sysmonitor.ui.screens.BenchHistoryScreen
import com.tamerin.sysmonitor.ui.screens.BrightnessTestScreen
import com.tamerin.sysmonitor.ui.screens.CompassScreen
import com.tamerin.sysmonitor.ui.screens.CpuBenchmarkScreen
import com.tamerin.sysmonitor.ui.screens.ApkExtractorScreen
import com.tamerin.sysmonitor.ui.screens.BackgroundRestrictScreen
import com.tamerin.sysmonitor.ui.screens.BatteryDoctorScreen
import com.tamerin.sysmonitor.ui.screens.ChargingLimitScreen
import com.tamerin.sysmonitor.ui.screens.CrashLogScreen
import com.tamerin.sysmonitor.ui.screens.DevToolsScreen
import com.tamerin.sysmonitor.ui.screens.DischargeCurveScreen
import com.tamerin.sysmonitor.ui.screens.DisplayTestScreen
import com.tamerin.sysmonitor.ui.screens.DpiAnimScreen
import com.tamerin.sysmonitor.ui.screens.GnssLiveScreen
import com.tamerin.sysmonitor.ui.screens.PerfettoTraceScreen
import com.tamerin.sysmonitor.ui.screens.PingMonitorScreen
import com.tamerin.sysmonitor.ui.screens.PrivacyDashboardScreen
import com.tamerin.sysmonitor.ui.screens.PrivacyPolicyScreen
import com.tamerin.sysmonitor.ui.screens.SpeedTestScreen
import com.tamerin.sysmonitor.ui.screens.StorageAnalyzerScreen
import com.tamerin.sysmonitor.ui.screens.TermsScreen
import com.tamerin.sysmonitor.ui.screens.EarpieceTestScreen
import com.tamerin.sysmonitor.ui.screens.EdgeRejectionScreen
import com.tamerin.sysmonitor.ui.screens.FlashlightScreen
import com.tamerin.sysmonitor.ui.screens.IrBlasterScreen
import com.tamerin.sysmonitor.ui.screens.FullscreenDisplayTestScreen
import com.tamerin.sysmonitor.ui.screens.GpuBenchmarkScreen
import com.tamerin.sysmonitor.ui.screens.ImageBenchScreen
import com.tamerin.sysmonitor.ui.screens.MicTestScreen
import com.tamerin.sysmonitor.ui.screens.MultiTouchScreen
import com.tamerin.sysmonitor.ui.screens.NetworkSpeedScreen
import com.tamerin.sysmonitor.ui.screens.NfcTapScreen
import com.tamerin.sysmonitor.ui.screens.NetworkUsageScreen
import com.tamerin.sysmonitor.ui.screens.NotificationLogScreen
import com.tamerin.sysmonitor.ui.screens.PermissionAuditScreen
import com.tamerin.sysmonitor.ui.screens.RamBenchmarkScreen
import com.tamerin.sysmonitor.ui.screens.RandomIOScreen
import com.tamerin.sysmonitor.ui.screens.RouletteScreen
import com.tamerin.sysmonitor.ui.screens.SensorDetailScreen
import com.tamerin.sysmonitor.ui.screens.SensorsScreen
import com.tamerin.sysmonitor.ui.screens.ShellTerminalScreen
import com.tamerin.sysmonitor.ui.screens.SnakeGameScreen
import com.tamerin.sysmonitor.ui.screens.SpeakerTestScreen
import com.tamerin.sysmonitor.ui.screens.StorageBenchmarkScreen
import com.tamerin.sysmonitor.ui.screens.StressTestScreen
import com.tamerin.sysmonitor.ui.screens.WakelockScreen

class BatteryStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Akku & Drain-Test"
    @Composable override fun ScreenContent() { BatteryScreen() }
}

class StressTestStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Stresstest"
    @Composable override fun ScreenContent() { StressTestScreen() }
}

class CpuBenchmarkStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "CPU-Benchmark"
    @Composable override fun ScreenContent() { CpuBenchmarkScreen() }
}

class GpuBenchmarkStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "GPU-FPS"
    @Composable override fun ScreenContent() { GpuBenchmarkScreen() }
}

class RamBenchmarkStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "RAM-Speed"
    @Composable override fun ScreenContent() { RamBenchmarkScreen() }
}

class StorageBenchmarkStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Storage Sequenziell"
    @Composable override fun ScreenContent() { StorageBenchmarkScreen() }
}

class RandomIOStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Storage Random 4K"
    @Composable override fun ScreenContent() { RandomIOScreen() }
}

class ImageBenchStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Bild-Verarbeitung"
    @Composable override fun ScreenContent() { ImageBenchScreen() }
}

class NetworkSpeedStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Netz-Speed"
    @Composable override fun ScreenContent() { NetworkSpeedScreen() }
}

class FullscreenDisplayTestStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Display-Test"
    @Composable override fun ScreenContent() { FullscreenDisplayTestScreen() }
}

class DisplayTestStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Display-Farben"
    @Composable override fun ScreenContent() { DisplayTestScreen() }
}

class SpeakerTestStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Speaker-Ton"
    @Composable override fun ScreenContent() { SpeakerTestScreen() }
}

class MicTestStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Mikrofon"
    @Composable override fun ScreenContent() { MicTestScreen() }
}

class FlashlightStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Taschenlampe"
    @Composable override fun ScreenContent() { FlashlightScreen() }
}

class MultiTouchStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Multi-Touch"
    @Composable override fun ScreenContent() { MultiTouchScreen() }
}

class SensorsStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Sensoren"
    @Composable override fun ScreenContent() {
        SensorsScreen(onSelect = { sensorType ->
            startActivity(
                android.content.Intent(this, SensorDetailStandaloneActivity::class.java)
                    .putExtra(SensorDetailStandaloneActivity.EXTRA_SENSOR_TYPE, sensorType)
            )
        })
    }
}

class SensorDetailStandaloneActivity : BaseScreenActivity() {
    companion object {
        const val EXTRA_SENSOR_TYPE = "sensor_type"
    }

    override val screenTitle = "Sensor-Detail"
    @Composable override fun ScreenContent() {
        val sensorType = intent.getIntExtra(EXTRA_SENSOR_TYPE, 1)
        SensorDetailScreen(sensorType = sensorType)
    }
}

class SnakeGameStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "🐍 CPU-Snake"
    @Composable override fun ScreenContent() { SnakeGameScreen() }
}

class DevToolsStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Dev-Tools"
    @Composable override fun ScreenContent() { DevToolsScreen() }
}

class RouletteStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "☠ Russian Roulette"
    @Composable override fun ScreenContent() { RouletteScreen() }
}

class NetworkUsageStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Pro-App Netzwerk-Traffic"
    @Composable override fun ScreenContent() { NetworkUsageScreen() }
}

class WakelockStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Wake-Locks (dumpsys power)"
    @Composable override fun ScreenContent() { WakelockScreen() }
}

class NotificationLogStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Notification-Log"
    @Composable override fun ScreenContent() { NotificationLogScreen() }
}

class PermissionAuditStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Permission-Audit"
    @Composable override fun ScreenContent() { PermissionAuditScreen() }
}

class ShellTerminalStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Shell-Terminal (Shizuku)"
    @Composable override fun ScreenContent() { ShellTerminalScreen() }
}

class EarpieceTestStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Earpiece"
    @Composable override fun ScreenContent() { EarpieceTestScreen() }
}

class EdgeRejectionStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Edge-Rejection"
    @Composable override fun ScreenContent() { EdgeRejectionScreen() }
}

class BrightnessTestStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Brightness-Konsistenz"
    @Composable override fun ScreenContent() { BrightnessTestScreen() }
}

class NfcTapStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "NFC-Tap-Test"
    @Composable override fun ScreenContent() { NfcTapScreen() }
}

class IrBlasterStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "IR-Blaster"
    @Composable override fun ScreenContent() { IrBlasterScreen() }
}

class CompassStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Kompass"
    @Composable override fun ScreenContent() { CompassScreen() }
}

class BarometerStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Barometer / Höhe"
    @Composable override fun ScreenContent() { BarometerScreen() }
}

class DischargeCurveStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Akku-Verlauf"
    @Composable override fun ScreenContent() { DischargeCurveScreen() }
}

class ChargingLimitStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Smart Charging Limit"
    @Composable override fun ScreenContent() { ChargingLimitScreen() }
}

class BatteryDoctorStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Akku-Doktor"
    @Composable override fun ScreenContent() { BatteryDoctorScreen() }
}

class StorageAnalyzerStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Storage-Analyzer"
    @Composable override fun ScreenContent() { StorageAnalyzerScreen() }
}

class ApkExtractorStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "APK-Extractor"
    @Composable override fun ScreenContent() { ApkExtractorScreen() }
}

class CrashLogStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Crash-Logs"
    @Composable override fun ScreenContent() { CrashLogScreen() }
}

class SpeedTestStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Speed-Test"
    @Composable override fun ScreenContent() { SpeedTestScreen() }
}

class PingMonitorStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Multi-Server-Ping"
    @Composable override fun ScreenContent() { PingMonitorScreen() }
}

class GnssLiveStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "GNSS Live"
    @Composable override fun ScreenContent() { GnssLiveScreen() }
}

class PerfettoTraceStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Perfetto-Trace"
    @Composable override fun ScreenContent() { PerfettoTraceScreen() }
}

class DpiAnimStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "DPI / Animation"
    @Composable override fun ScreenContent() { DpiAnimScreen() }
}

class BackgroundRestrictStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Background-Restrict"
    @Composable override fun ScreenContent() { BackgroundRestrictScreen() }
}

class PrivacyPolicyStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Datenschutzerklärung"
    @Composable override fun ScreenContent() { PrivacyPolicyScreen() }
}

class TermsStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Nutzungsbedingungen"
    @Composable override fun ScreenContent() { TermsScreen() }
}

class PrivacyDashboardStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Privacy-Dashboard"
    @Composable override fun ScreenContent() { PrivacyDashboardScreen() }
}

class BenchHistoryStandaloneActivity : BaseScreenActivity() {
    override val screenTitle = "Benchmark-Verlauf"
    @Composable override fun ScreenContent() { BenchHistoryScreen() }
}
