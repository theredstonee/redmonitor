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
import androidx.compose.ui.res.stringResource
import com.tamerin.sysmonitor.R
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

@Composable
fun BenchmarkHubScreen(onNavigate: (String) -> Unit) {
    val entries = listOf(
        HubEntry(Routes.BM_CPU, stringResource(R.string.bench_cpu_benchmark), stringResource(R.string.bench_hub_cpu_sub), Icons.Filled.Calculate,
            activityClass = CpuBenchmarkStandaloneActivity::class.java),
        HubEntry(Routes.BM_RAM, stringResource(R.string.bench_hub_ram_title), stringResource(R.string.bench_hub_ram_sub), Icons.Filled.Memory,
            activityClass = RamBenchmarkStandaloneActivity::class.java),
        HubEntry(Routes.BM_STORAGE, stringResource(R.string.bench_hub_storage_title), stringResource(R.string.bench_hub_storage_sub), Icons.Filled.SdStorage,
            activityClass = StorageBenchmarkStandaloneActivity::class.java),
        HubEntry(Routes.BM_RANDOM, stringResource(R.string.bench_hub_random_title), stringResource(R.string.bench_hub_random_sub), Icons.Filled.Shuffle,
            activityClass = RandomIOStandaloneActivity::class.java),
        HubEntry(Routes.BM_GPU, stringResource(R.string.bench_hub_gpu_title), stringResource(R.string.bench_hub_gpu_sub), Icons.Filled.VideogameAsset,
            activityClass = GpuBenchmarkStandaloneActivity::class.java),
        HubEntry(Routes.BM_IMAGE, stringResource(R.string.bench_hub_image_title), stringResource(R.string.bench_hub_image_sub), Icons.Filled.Image,
            activityClass = ImageBenchStandaloneActivity::class.java),
        HubEntry(Routes.BM_NETWORK, stringResource(R.string.bench_hub_network_title), stringResource(R.string.bench_hub_network_sub), Icons.Filled.CloudDownload,
            activityClass = NetworkSpeedStandaloneActivity::class.java),
        HubEntry(Routes.BM_STRESS, stringResource(R.string.bench_stress_test), stringResource(R.string.bench_hub_stress_sub), Icons.Filled.LocalFireDepartment,
            activityClass = StressTestStandaloneActivity::class.java),
        HubEntry(Routes.BM_COLOR, stringResource(R.string.bench_hub_color_title), stringResource(R.string.bench_hub_color_sub), Icons.Filled.ColorLens),
        HubEntry(Routes.BM_DISPLAY, stringResource(R.string.bench_hub_display_title), stringResource(R.string.bench_hub_display_sub), Icons.Filled.Palette,
            activityClass = FullscreenDisplayTestStandaloneActivity::class.java),
        HubEntry("bench/history", stringResource(R.string.bench_history), stringResource(R.string.bench_hub_history_sub), Icons.Filled.History,
            activityClass = BenchHistoryStandaloneActivity::class.java, badge = stringResource(R.string.bench_badge_new))
    )
    HubGrid(entries = entries, onClick = { onNavigate(it.route) })
}
