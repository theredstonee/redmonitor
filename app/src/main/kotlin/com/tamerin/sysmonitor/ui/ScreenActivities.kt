package com.tamerin.sysmonitor.ui

import androidx.compose.runtime.Composable
import com.tamerin.sysmonitor.ui.screens.BatteryScreen
import com.tamerin.sysmonitor.ui.screens.CpuBenchmarkScreen
import com.tamerin.sysmonitor.ui.screens.DevToolsScreen
import com.tamerin.sysmonitor.ui.screens.DisplayTestScreen
import com.tamerin.sysmonitor.ui.screens.FlashlightScreen
import com.tamerin.sysmonitor.ui.screens.FullscreenDisplayTestScreen
import com.tamerin.sysmonitor.ui.screens.GpuBenchmarkScreen
import com.tamerin.sysmonitor.ui.screens.ImageBenchScreen
import com.tamerin.sysmonitor.ui.screens.MicTestScreen
import com.tamerin.sysmonitor.ui.screens.MultiTouchScreen
import com.tamerin.sysmonitor.ui.screens.NetworkSpeedScreen
import com.tamerin.sysmonitor.ui.screens.RamBenchmarkScreen
import com.tamerin.sysmonitor.ui.screens.RandomIOScreen
import com.tamerin.sysmonitor.ui.screens.SensorDetailScreen
import com.tamerin.sysmonitor.ui.screens.SensorsScreen
import com.tamerin.sysmonitor.ui.screens.SnakeGameScreen
import com.tamerin.sysmonitor.ui.screens.SpeakerTestScreen
import com.tamerin.sysmonitor.ui.screens.StorageBenchmarkScreen
import com.tamerin.sysmonitor.ui.screens.StressTestScreen

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
