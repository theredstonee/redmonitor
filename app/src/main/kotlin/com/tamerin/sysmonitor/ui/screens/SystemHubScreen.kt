package com.tamerin.sysmonitor.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import com.tamerin.sysmonitor.Routes
import com.tamerin.sysmonitor.ui.BatteryStandaloneActivity
import com.tamerin.sysmonitor.ui.SensorsStandaloneActivity
import com.tamerin.sysmonitor.ui.components.HubEntry
import com.tamerin.sysmonitor.ui.components.HubGrid

private val SYSTEM_ENTRIES = listOf(
    HubEntry(Routes.CPU, "CPU", "Last, Frequenzen, Kerne", Icons.Filled.Speed),
    HubEntry(Routes.RAM, "RAM & Speicher", "Auslastung & Storage", Icons.Filled.Memory),
    HubEntry(Routes.BATTERY, "Akku", "Stand, Watt, Schnelllad-Erkennung", Icons.Filled.BatteryFull,
        activityClass = BatteryStandaloneActivity::class.java),
    HubEntry(Routes.SENSORS, "Sensoren", "Liste + Klick = Detail-Graph", Icons.Filled.Sensors,
        activityClass = SensorsStandaloneActivity::class.java),
    HubEntry(Routes.GPU, "GPU", "Renderer, OpenGL, GLSL", Icons.Filled.DeveloperBoard),
    HubEntry(Routes.NETWORK, "Netzwerk", "WLAN, Signal, IP, Traffic", Icons.Filled.NetworkCheck),
    HubEntry(Routes.DISPLAY, "Display & Gerät", "Build, Uptime, Display", Icons.Filled.PhoneAndroid),
    HubEntry(Routes.HUD, "Floating HUD", "Live-Overlay über allen Apps", Icons.Filled.Layers),
    HubEntry(Routes.SYSTEM_TWEAKS, "Display-Tweaks", "DPI, Auflösung, Animationen", Icons.Filled.Tune)
)

@Composable
fun SystemHubScreen(onNavigate: (String) -> Unit) {
    HubGrid(entries = SYSTEM_ENTRIES, onClick = { onNavigate(it.route) })
}
