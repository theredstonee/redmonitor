package com.tamerin.sysmonitor.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.Routes
import com.tamerin.sysmonitor.ui.BatteryStandaloneActivity
import com.tamerin.sysmonitor.ui.GlyphMatrixStandaloneActivity
import com.tamerin.sysmonitor.ui.SensorsStandaloneActivity
import com.tamerin.sysmonitor.ui.components.HubEntry
import com.tamerin.sysmonitor.ui.components.HubGrid

@Composable
fun SystemHubScreen(onNavigate: (String) -> Unit) {
    val entries = listOf(
        HubEntry(Routes.CPU, "CPU", stringResource(R.string.bench_sys_cpu_sub), Icons.Filled.Speed),
        HubEntry(Routes.RAM, stringResource(R.string.bench_sys_ram_title), stringResource(R.string.bench_sys_ram_sub), Icons.Filled.Memory),
        HubEntry(Routes.BATTERY, stringResource(R.string.bench_sys_battery_title), stringResource(R.string.bench_sys_battery_sub), Icons.Filled.BatteryFull,
            activityClass = BatteryStandaloneActivity::class.java),
        HubEntry(Routes.SENSORS, stringResource(R.string.bench_sys_sensors_title), stringResource(R.string.bench_sys_sensors_sub), Icons.Filled.Sensors,
            activityClass = SensorsStandaloneActivity::class.java),
        HubEntry(Routes.GPU, "GPU", stringResource(R.string.bench_sys_gpu_sub), Icons.Filled.DeveloperBoard),
        HubEntry(Routes.NETWORK, stringResource(R.string.bench_sys_network_title), stringResource(R.string.bench_sys_network_sub), Icons.Filled.NetworkCheck),
        HubEntry(Routes.DISPLAY, stringResource(R.string.bench_sys_display_title), stringResource(R.string.bench_sys_display_sub), Icons.Filled.PhoneAndroid),
        HubEntry(Routes.HUD, stringResource(R.string.bench_sys_hud_title), stringResource(R.string.bench_sys_hud_sub), Icons.Filled.Layers),
        HubEntry(Routes.GLYPH, stringResource(R.string.bench_sys_glyph_title), stringResource(R.string.bench_sys_glyph_sub), Icons.Filled.GridView,
            badge = "Phone (3)", activityClass = GlyphMatrixStandaloneActivity::class.java),
        HubEntry(Routes.SYSTEM_TWEAKS, stringResource(R.string.bench_sys_tweaks_title), stringResource(R.string.bench_sys_tweaks_sub), Icons.Filled.Tune)
    )
    HubGrid(entries = entries, onClick = { onNavigate(it.route) })
}
