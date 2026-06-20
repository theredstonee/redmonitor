package com.tamerin.sysmonitor.ui.screens.benchmark

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.runtime.Composable
import com.tamerin.sysmonitor.Routes
import com.tamerin.sysmonitor.ui.BenchHistoryStandaloneActivity
import com.tamerin.sysmonitor.ui.CpuBenchmarkStandaloneActivity
import com.tamerin.sysmonitor.ui.FullscreenDisplayTestStandaloneActivity
import com.tamerin.sysmonitor.ui.GpuBenchmarkStandaloneActivity
import com.tamerin.sysmonitor.ui.ImageBenchStandaloneActivity
import com.tamerin.sysmonitor.ui.NetworkSpeedStandaloneActivity
import com.tamerin.sysmonitor.ui.RamBenchmarkStandaloneActivity
import com.tamerin.sysmonitor.ui.RandomIOStandaloneActivity
import com.tamerin.sysmonitor.ui.StorageBenchmarkStandaloneActivity
import com.tamerin.sysmonitor.ui.StressTestStandaloneActivity
import com.tamerin.sysmonitor.ui.components.HubEntry
import com.tamerin.sysmonitor.ui.components.HubGrid

private val BENCH_ENTRIES = listOf(
    HubEntry(Routes.BM_CPU, "CPU-Benchmark", "Single + Multi-Core Score", Icons.Filled.Calculate,
        activityClass = CpuBenchmarkStandaloneActivity::class.java),
    HubEntry(Routes.BM_RAM, "RAM-Speed", "Lesen / Schreiben MB/s", Icons.Filled.Memory,
        activityClass = RamBenchmarkStandaloneActivity::class.java),
    HubEntry(Routes.BM_STORAGE, "Storage Seq.", "Sequenzielles Lesen/Schreiben", Icons.Filled.SdStorage,
        activityClass = StorageBenchmarkStandaloneActivity::class.java),
    HubEntry(Routes.BM_RANDOM, "Storage 4K Random", "IOPS — wichtig für Apps", Icons.Filled.Shuffle,
        activityClass = RandomIOStandaloneActivity::class.java),
    HubEntry(Routes.BM_GPU, "GPU-FPS", "Render-Test", Icons.Filled.VideogameAsset,
        activityClass = GpuBenchmarkStandaloneActivity::class.java),
    HubEntry(Routes.BM_IMAGE, "Bild-Verarbeitung", "CPU Bildfilter MPx/s", Icons.Filled.Image,
        activityClass = ImageBenchStandaloneActivity::class.java),
    HubEntry(Routes.BM_NETWORK, "Netz-Speed", "Download von Cloudflare", Icons.Filled.CloudDownload,
        activityClass = NetworkSpeedStandaloneActivity::class.java),
    HubEntry(Routes.BM_STRESS, "Stresstest", "CPU-Vollast + Throttling", Icons.Filled.LocalFireDepartment,
        activityClass = StressTestStandaloneActivity::class.java),
    HubEntry(Routes.BM_COLOR, "Farbraum & Gamut", "sRGB, HDR, Gradienten", Icons.Filled.ColorLens),
    HubEntry(Routes.BM_DISPLAY, "Display-Test", "Auto-Sequenz Vollbild-Muster", Icons.Filled.Palette,
        activityClass = FullscreenDisplayTestStandaloneActivity::class.java),
    HubEntry("bench/history", "Verlauf", "Alle Runs mit Sub-Scores", Icons.Filled.History,
        activityClass = BenchHistoryStandaloneActivity::class.java, badge = "NEU")
)

@Composable
fun BenchmarkHubScreen(onNavigate: (String) -> Unit) {
    HubGrid(entries = BENCH_ENTRIES, onClick = { onNavigate(it.route) })
}
