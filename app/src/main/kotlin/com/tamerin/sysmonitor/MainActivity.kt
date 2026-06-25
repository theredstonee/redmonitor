package com.tamerin.sysmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.BgDark
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tamerin.sysmonitor.ui.screens.BatteryScreen
import com.tamerin.sysmonitor.ui.screens.BluetoothScreen
import com.tamerin.sysmonitor.ui.screens.CameraInfoScreen
import com.tamerin.sysmonitor.ui.screens.CodecsScreen
import com.tamerin.sysmonitor.ui.screens.ColorSpaceScreen
import com.tamerin.sysmonitor.ui.screens.CpuBenchmarkScreen
import com.tamerin.sysmonitor.ui.screens.CpuScreen
import com.tamerin.sysmonitor.ui.screens.DisplayScreen
import com.tamerin.sysmonitor.ui.screens.DisplayTestScreen
import com.tamerin.sysmonitor.ui.screens.DisplayTweaksScreen
import com.tamerin.sysmonitor.ui.screens.DozeWhitelistScreen
import com.tamerin.sysmonitor.ui.screens.FeaturesScreen
import com.tamerin.sysmonitor.ui.screens.FlashlightScreen
import com.tamerin.sysmonitor.ui.screens.FullscreenDisplayTestScreen
import com.tamerin.sysmonitor.ui.screens.GpsTestScreen
import com.tamerin.sysmonitor.ui.screens.GpuBenchmarkScreen
import com.tamerin.sysmonitor.ui.screens.GpuScreen
import com.tamerin.sysmonitor.ui.screens.HudSettingsScreen
import com.tamerin.sysmonitor.ui.screens.ImageBenchScreen
import com.tamerin.sysmonitor.ui.screens.InstalledAppsScreen
import com.tamerin.sysmonitor.ui.screens.LogcatScreen
import com.tamerin.sysmonitor.ui.screens.MicTestScreen
import com.tamerin.sysmonitor.ui.screens.MultiTouchScreen
import com.tamerin.sysmonitor.ui.screens.NetworkScreen
import com.tamerin.sysmonitor.ui.screens.NetworkSpeedScreen
import com.tamerin.sysmonitor.ui.screens.NfcScreen
import com.tamerin.sysmonitor.ui.screens.OemOnboardingScreen
import com.tamerin.sysmonitor.ui.screens.OverlayHudScreen
import com.tamerin.sysmonitor.ui.screens.OverviewScreen
import com.tamerin.sysmonitor.ui.screens.ProximityLightScreen
import com.tamerin.sysmonitor.ui.screens.RamBenchmarkScreen
import com.tamerin.sysmonitor.ui.screens.RamScreen
import com.tamerin.sysmonitor.ui.screens.RandomIOScreen
import com.tamerin.sysmonitor.ui.screens.RunningAppsScreen
import com.tamerin.sysmonitor.ui.screens.SensorDetailScreen
import com.tamerin.sysmonitor.ui.screens.SensorsScreen
import com.tamerin.sysmonitor.ui.screens.SettingsScreen
import com.tamerin.sysmonitor.ui.screens.SpeakerTestScreen
import com.tamerin.sysmonitor.ui.screens.StorageBenchmarkScreen
import com.tamerin.sysmonitor.ui.screens.StressTestScreen
import com.tamerin.sysmonitor.ui.screens.SystemHubScreen
import com.tamerin.sysmonitor.ui.screens.SystemPropertiesScreen
import com.tamerin.sysmonitor.ui.screens.TaskDetailScreen
import com.tamerin.sysmonitor.ui.screens.TaskManagerScreen
import com.tamerin.sysmonitor.ui.screens.TelephonyScreen
import com.tamerin.sysmonitor.ui.screens.ThermalScreen
import com.tamerin.sysmonitor.ui.screens.UpdateScreen
import com.tamerin.sysmonitor.ui.screens.VibrationScreen
import com.tamerin.sysmonitor.ui.screens.WifiScanScreen
import com.tamerin.sysmonitor.ui.screens.benchmark.BenchmarkHubScreen
import com.tamerin.sysmonitor.ui.screens.info.InfoHubScreen
import com.tamerin.sysmonitor.ui.screens.tests.TestsHubScreen
import com.tamerin.sysmonitor.ui.theme.SysMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.tamerin.sysmonitor.update.UpdateWorker.schedulePeriodic(this)
        com.tamerin.sysmonitor.update.UpdateNotifier.ensureChannel(this)
        com.tamerin.sysmonitor.data.battery.BatterySamplerWorker.schedule(this)
        com.tamerin.sysmonitor.cloud.CloudSyncWorker.schedule(this)
        // Plus one sample now so first run already has data
        lifecycleScope.launch {
            com.tamerin.sysmonitor.data.battery.BatteryHistoryTracker.sample(this@MainActivity)
        }
        // Wenn Shizuku schon ready: einmal alle App-Permissions automatisch
        // freischalten — Runtime-Perms via pm grant, Special-Ops via appops,
        // Notification-Listener via secure-settings. User muss nichts mehr
        // durch die System-Settings klicken.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                com.tamerin.sysmonitor.data.ShizukuHelper.autoGrantAllPermissions(this@MainActivity)
            }
        }
        com.tamerin.sysmonitor.settings.AppPrefs.incrementLaunchCount(this)
        val initialRoute = routeFromAlias(intent?.component?.className)
        setContent {
            SysMonitorTheme {
                SysMonitorApp(initialRoute = initialRoute)
            }
        }
    }
}

private fun routeFromAlias(className: String?): String {
    if (className == null) return Routes.LIVE
    return when {
        className.endsWith(".SystemActivity") -> Routes.SYSTEM
        className.endsWith(".TasksActivity") -> Routes.TASKS
        className.endsWith(".BenchmarkActivity") -> Routes.BENCHMARK
        className.endsWith(".TestsActivity") -> Routes.TESTS
        className.endsWith(".InfoActivity") -> Routes.INFO
        className.endsWith(".SettingsActivity") -> Routes.SETTINGS
        className.endsWith(".LogcatActivity") -> Routes.INFO_LOGCAT
        className.endsWith(".HudActivity") -> Routes.HUD
        className.endsWith(".OemSetupActivity") -> Routes.OEM_SETUP
        else -> Routes.LIVE
    }
}

object Routes {
    const val LIVE = "live"
    const val SYSTEM = "system"
    const val BENCHMARK = "benchmark"
    const val TESTS = "tests"
    const val INFO = "info"
    const val TASKS = "tasks"
    const val TASK_DETAIL = "tasks/{pkg}"
    fun taskDetail(pkg: String) = "tasks/$pkg"

    const val CPU = "system/cpu"
    const val RAM = "system/ram"
    const val BATTERY = "system/battery"
    const val SENSORS = "system/sensors"
    const val SENSOR_DETAIL = "system/sensors/{type}"
    fun sensorDetail(type: Int) = "system/sensors/$type"
    const val GPU = "system/gpu"
    const val NETWORK = "system/network"
    const val DISPLAY = "system/display"
    const val HUD = "system/hud"
    const val HUD_SETTINGS = "system/hud/settings"

    const val BM_CPU = "bench/cpu"
    const val BM_RAM = "bench/ram"
    const val BM_STORAGE = "bench/storage"
    const val BM_RANDOM = "bench/random"
    const val BM_GPU = "bench/gpu"
    const val BM_STRESS = "bench/stress"
    const val BM_COLOR = "bench/color"
    const val BM_DISPLAY = "bench/display"
    const val BM_NETWORK = "bench/network"
    const val BM_IMAGE = "bench/image"

    const val TEST_TOUCH = "test/touch"
    const val TEST_DISPLAY = "test/display"
    const val TEST_VIBRATE = "test/vibrate"
    const val TEST_FLASH = "test/flashlight"
    const val TEST_SPEAKER = "test/speaker"
    const val TEST_MIC = "test/mic"
    const val TEST_GPS = "test/gps"
    const val TEST_PROXIMITY = "test/proximity"

    const val INFO_THERMAL = "info/thermal"
    const val INFO_PROPS = "info/props"
    const val INFO_FEATURES = "info/features"
    const val INFO_TELEPHONY = "info/telephony"
    const val INFO_CAMERAS = "info/cameras"
    const val INFO_CODECS = "info/codecs"
    const val INFO_RUNNING = "info/running"
    const val INFO_INSTALLED = "info/installed"
    const val INFO_WIFI = "info/wifi"
    const val INFO_BLUETOOTH = "info/bluetooth"
    const val INFO_NFC = "info/nfc"
    const val INFO_LOGCAT = "info/logcat"
    const val INFO_DOZE = "info/doze"
    const val SYSTEM_TWEAKS = "system/tweaks"
    const val UPDATE = "update"
    const val SETTINGS = "settings"
    const val OEM_SETUP = "oem/setup"
}

private data class TopTab(val route: String, val label: String, val icon: ImageVector)

private val TOP_TABS = listOf(
    TopTab(Routes.LIVE, "Live", Icons.Filled.Dashboard),
    TopTab(Routes.SYSTEM, "System", Icons.Filled.Memory),
    TopTab(Routes.TASKS, "Tasks", Icons.Filled.TaskAlt),
    TopTab(Routes.BENCHMARK, "Bench", Icons.Filled.Speed),
    TopTab(Routes.TESTS, "Tests", Icons.Filled.Build),
    TopTab(Routes.INFO, "Info", Icons.Filled.Info),
    TopTab(Routes.SETTINGS, "Mehr", Icons.Filled.Settings)
)

private val TOP_LEVEL_ROUTES = setOf(
    Routes.LIVE, Routes.SYSTEM, Routes.TASKS, Routes.BENCHMARK, Routes.TESTS, Routes.INFO, Routes.SETTINGS
)

private fun titleFor(route: String?): String = when {
    route == null -> "SysMonitor"
    route == Routes.LIVE -> "Live"
    route == Routes.SYSTEM -> "System"
    route == Routes.BENCHMARK -> "Benchmark"
    route == Routes.TESTS -> "Tests"
    route == Routes.INFO -> "Info"
    route == Routes.TASKS -> "Tasks"
    route?.startsWith("tasks/") == true -> "App-Details"
    route == Routes.CPU -> "CPU"
    route == Routes.RAM -> "RAM & Speicher"
    route == Routes.BATTERY -> "Akku"
    route == Routes.SENSORS -> "Sensoren"
    route.startsWith("system/sensors/") -> "Sensor-Detail"
    route == Routes.GPU -> "GPU"
    route == Routes.NETWORK -> "Netzwerk"
    route == Routes.DISPLAY -> "Display & Gerät"
    route == Routes.HUD -> "Floating HUD"
    route == Routes.HUD_SETTINGS -> "HUD anpassen"
    route == Routes.BM_CPU -> "CPU-Benchmark"
    route == Routes.BM_RAM -> "RAM-Speed"
    route == Routes.BM_STORAGE -> "Storage Sequenziell"
    route == Routes.BM_RANDOM -> "Storage Random 4K"
    route == Routes.BM_GPU -> "GPU-FPS"
    route == Routes.BM_STRESS -> "Stresstest"
    route == Routes.BM_COLOR -> "Farbraum & Gamut"
    route == Routes.BM_DISPLAY -> "Display-Test"
    route == Routes.BM_NETWORK -> "Netz-Speed"
    route == Routes.BM_IMAGE -> "Bild-Verarbeitung"
    route == Routes.TEST_TOUCH -> "Multi-Touch"
    route == Routes.TEST_DISPLAY -> "Display-Farben"
    route == Routes.TEST_VIBRATE -> "Vibration"
    route == Routes.TEST_FLASH -> "Taschenlampe"
    route == Routes.TEST_SPEAKER -> "Speaker-Ton"
    route == Routes.TEST_MIC -> "Mikrofon"
    route == Routes.TEST_GPS -> "GPS & Satelliten"
    route == Routes.TEST_PROXIMITY -> "Proximity & Licht"
    route == Routes.INFO_THERMAL -> "Thermalzonen"
    route == Routes.INFO_PROPS -> "System-Properties"
    route == Routes.INFO_FEATURES -> "Hardware-Features"
    route == Routes.INFO_TELEPHONY -> "SIM & Telefonie"
    route == Routes.INFO_CAMERAS -> "Kameras"
    route == Routes.INFO_CODECS -> "Codecs"
    route == Routes.INFO_RUNNING -> "Laufende Apps"
    route == Routes.INFO_INSTALLED -> "Installierte Apps"
    route == Routes.INFO_WIFI -> "WLAN-Scan"
    route == Routes.INFO_BLUETOOTH -> "Bluetooth"
    route == Routes.INFO_NFC -> "NFC"
    route == Routes.INFO_LOGCAT -> "Logcat"
    route == Routes.INFO_DOZE -> "Doze-Whitelist"
    route == Routes.SYSTEM_TWEAKS -> "Display-Tweaks"
    route == Routes.UPDATE -> "Update"
    route == Routes.SETTINGS -> "Einstellungen"
    route == Routes.OEM_SETUP -> "Geräte-Setup"
    else -> "SysMonitor"
}

/** Set to true by full-screen tests to hide top/bottom bars + system bars. */
val LocalImmersive = androidx.compose.runtime.compositionLocalOf<androidx.compose.runtime.MutableState<Boolean>> {
    error("LocalImmersive not provided")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SysMonitorApp(initialRoute: String = Routes.LIVE) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isTopLevel = currentRoute in TOP_LEVEL_ROUTES || currentRoute == null
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val immersive = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.runtime.DisposableEffect(immersive.value) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, view)
        }
        if (immersive.value) {
            controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose { /* leave bars in current state */ }
    }
    var startupUpdate by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<com.tamerin.sysmonitor.update.ReleaseInfo?>(null)
    }
    var showRatePrompt by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    var showOnboarding by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            !com.tamerin.sysmonitor.settings.AppPrefs.isFirstLaunchOnboardingDone(context)
        )
    }
    var restoreAvailable by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<com.tamerin.sysmonitor.cloud.RestoreChecker.Available?>(null)
    }
    var restoreSummary by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<com.tamerin.sysmonitor.cloud.BackupSerializer.RestoreSummary?>(null)
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // First-Launch-Restore-Probe — feuert nur einmal, danach CloudPrefs.restoreChecked=true
        restoreAvailable = runCatching {
            com.tamerin.sysmonitor.cloud.RestoreChecker.probe(context)
        }.getOrNull()
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // First-launch OEM onboarding for restrictive ROMs — feuert NUR beim allerersten
        // App-Start. Nach diesem einen Versuch wird isOemOnboardingDone gesetzt — auch
        // wenn der User den Screen wegswiped. Er findet den OEM-Setup-Screen jederzeit
        // über Info-Hub → 'Geräte-Setup' wieder.
        if (com.tamerin.sysmonitor.settings.AppPrefs.launchCount(context) <= 1 &&
            !com.tamerin.sysmonitor.settings.AppPrefs.isOemOnboardingDone(context)) {
            val spec = com.tamerin.sysmonitor.data.OemDetect.detect()
            if (spec.restrictionLevel == com.tamerin.sysmonitor.data.OemRestrictionLevel.HIGH ||
                spec.restrictionLevel == com.tamerin.sysmonitor.data.OemRestrictionLevel.MEDIUM) {
                navController.navigate(Routes.OEM_SETUP)
            }
            // In jedem Fall flaggen — kein zweiter Auto-Navigate, egal wie der User reagiert
            com.tamerin.sysmonitor.settings.AppPrefs.setOemOnboardingDone(context, true)
        }
        val state = com.tamerin.sysmonitor.update.UpdateChecker.check(
            context,
            com.tamerin.sysmonitor.update.UpdatePrefs.includePrerelease(context)
        )
        if (state.hasUpdate && state.latest != null) {
            com.tamerin.sysmonitor.update.UpdatePrefs.setLatestSeenVersion(context, state.latest.versionName)
            if (com.tamerin.sysmonitor.update.UpdatePrefs.dismissedVersion(context) != state.latest.versionName) {
                startupUpdate = state.latest
            }
        }
        com.tamerin.sysmonitor.update.UpdatePrefs.setLastCheckMs(context, System.currentTimeMillis())
        // Don't stack dialogs: only show rate prompt if no update dialog is queued
        if (startupUpdate == null && com.tamerin.sysmonitor.settings.AppPrefs.shouldShowRatePrompt(context)) {
            showRatePrompt = true
        }
    }

    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val useRail = config.screenWidthDp >= 600

    androidx.compose.runtime.CompositionLocalProvider(LocalImmersive provides immersive) {
    Scaffold(
        topBar = {
            if (immersive.value) return@Scaffold
            TopAppBar(
                title = {
                    if (isTopLevel) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = Accent, fontWeight = FontWeight.Bold)) {
                                    append("Red")
                                }
                                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                                    append("Monitor")
                                }
                            },
                            fontSize = 18.sp
                        )
                    } else {
                        Text(titleFor(currentRoute))
                    }
                },
                navigationIcon = {
                    if (!isTopLevel) {
                        IconButton(onClick = {
                            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                            navController.popBackStack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgDark,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            if (immersive.value || useRail) return@Scaffold
            NavigationBar(
                containerColor = BgDark,
                tonalElevation = 0.dp
            ) {
                TOP_TABS.forEach { tab ->
                    val selected = currentRoute?.startsWith(tab.route) == true ||
                        (currentRoute == null && tab.route == Routes.LIVE)
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                            if (selected && currentRoute != tab.route) {
                                navController.popBackStack(tab.route, inclusive = false)
                            } else {
                                navController.navigateTopLevel(tab.route)
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, maxLines = 1, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Accent,
                            unselectedIconColor = Color(0xFF6B7280),
                            unselectedTextColor = Color(0xFF6B7280),
                            indicatorColor = Color(0x33DC2626)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        androidx.compose.foundation.layout.Row(modifier = Modifier.padding(innerPadding)) {
            if (useRail && !immersive.value) {
                NavigationRail(
                    containerColor = BgDark,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    TOP_TABS.forEach { tab ->
                        val selected = currentRoute?.startsWith(tab.route) == true ||
                            (currentRoute == null && tab.route == Routes.LIVE)
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                                if (selected && currentRoute != tab.route) {
                                    navController.popBackStack(tab.route, inclusive = false)
                                } else {
                                    navController.navigateTopLevel(tab.route)
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, maxLines = 1, fontSize = 11.sp) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Accent,
                                unselectedIconColor = Color(0xFF6B7280),
                                unselectedTextColor = Color(0xFF6B7280),
                                indicatorColor = Color(0x33DC2626)
                            )
                        )
                    }
                }
            }
        NavHost(
            navController = navController,
            startDestination = initialRoute,
            modifier = Modifier
        ) {
            composable(Routes.LIVE) {
                OverviewScreen(
                    onOpenUpdate = { navController.navigate(Routes.UPDATE) },
                    onOpenOemSetup = { navController.navigate(Routes.OEM_SETUP) }
                )
            }
            composable(Routes.SYSTEM) { SystemHubScreen { navController.navigate(it) } }
            composable(Routes.BENCHMARK) { BenchmarkHubScreen { navController.navigate(it) } }
            composable(Routes.TESTS) { TestsHubScreen { navController.navigate(it) } }
            composable(Routes.INFO) { InfoHubScreen { navController.navigate(it) } }
            composable(Routes.TASKS) {
                TaskManagerScreen(onSelect = { pkg -> navController.navigate(Routes.taskDetail(pkg)) })
            }
            composable(
                Routes.TASK_DETAIL,
                arguments = listOf(navArgument("pkg") { type = NavType.StringType })
            ) { entry ->
                val pkg = entry.arguments?.getString("pkg") ?: ""
                TaskDetailScreen(pkg = pkg)
            }

            composable(Routes.CPU) { CpuScreen() }
            composable(Routes.RAM) { RamScreen() }
            composable(Routes.BATTERY) { BatteryScreen() }
            composable(Routes.SENSORS) {
                SensorsScreen(onSelect = { type ->
                    navController.navigate(Routes.sensorDetail(type))
                })
            }
            composable(
                Routes.SENSOR_DETAIL,
                arguments = listOf(navArgument("type") { type = NavType.IntType })
            ) { backStackEntry ->
                val t = backStackEntry.arguments?.getInt("type") ?: 1
                SensorDetailScreen(sensorType = t)
            }
            composable(Routes.GPU) { GpuScreen() }
            composable(Routes.NETWORK) { NetworkScreen() }
            composable(Routes.DISPLAY) { DisplayScreen() }
            composable(Routes.HUD) { OverlayHudScreen { navController.navigate(Routes.HUD_SETTINGS) } }
            composable(Routes.HUD_SETTINGS) { HudSettingsScreen() }

            composable(Routes.BM_CPU) { CpuBenchmarkScreen() }
            composable(Routes.BM_RAM) { RamBenchmarkScreen() }
            composable(Routes.BM_STORAGE) { StorageBenchmarkScreen() }
            composable(Routes.BM_RANDOM) { RandomIOScreen() }
            composable(Routes.BM_GPU) { GpuBenchmarkScreen() }
            composable(Routes.BM_STRESS) { StressTestScreen() }
            composable(Routes.BM_COLOR) { ColorSpaceScreen() }
            composable(Routes.BM_DISPLAY) { FullscreenDisplayTestScreen() }
            composable(Routes.BM_NETWORK) { NetworkSpeedScreen() }
            composable(Routes.BM_IMAGE) { ImageBenchScreen() }

            composable(Routes.TEST_TOUCH) { MultiTouchScreen() }
            composable(Routes.TEST_DISPLAY) { DisplayTestScreen() }
            composable(Routes.TEST_VIBRATE) { VibrationScreen() }
            composable(Routes.TEST_FLASH) { FlashlightScreen() }
            composable(Routes.TEST_SPEAKER) { SpeakerTestScreen() }
            composable(Routes.TEST_MIC) { MicTestScreen() }
            composable(Routes.TEST_GPS) { GpsTestScreen() }
            composable(Routes.TEST_PROXIMITY) { ProximityLightScreen() }

            composable(Routes.INFO_THERMAL) { ThermalScreen() }
            composable(Routes.INFO_PROPS) { SystemPropertiesScreen() }
            composable(Routes.INFO_FEATURES) { FeaturesScreen() }
            composable(Routes.INFO_TELEPHONY) { TelephonyScreen() }
            composable(Routes.INFO_CAMERAS) { CameraInfoScreen() }
            composable(Routes.INFO_CODECS) { CodecsScreen() }
            composable(Routes.INFO_RUNNING) { RunningAppsScreen() }
            composable(Routes.INFO_INSTALLED) { InstalledAppsScreen() }
            composable(Routes.INFO_WIFI) { WifiScanScreen() }
            composable(Routes.INFO_BLUETOOTH) { BluetoothScreen() }
            composable(Routes.INFO_NFC) { NfcScreen() }
            composable(Routes.INFO_LOGCAT) { LogcatScreen() }
            composable(Routes.INFO_DOZE) { DozeWhitelistScreen() }
            composable(Routes.SYSTEM_TWEAKS) { DisplayTweaksScreen() }
            composable(Routes.UPDATE) { UpdateScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.OEM_SETUP) { OemOnboardingScreen(onDone = { navController.popBackStack() }) }
        }
        } // Row (NavigationRail wrapper)
    }

    // Startup update dialog
    val pending = startupUpdate
    if (pending != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { startupUpdate = null },
            title = { Text("Update verfügbar") },
            text = {
                Text(
                    "${pending.name}\n\n" +
                        "Aktuell: ${com.tamerin.sysmonitor.BuildConfig.VERSION_NAME}\n" +
                        "Neu: ${pending.versionName}" +
                        if (pending.isPrerelease) "  (Pre-Release)" else ""
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                    startupUpdate = null
                    navController.navigate(Routes.UPDATE)
                }) { Text("Details / Installieren") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    com.tamerin.sysmonitor.update.UpdatePrefs.dismissVersion(context, pending.versionName)
                    startupUpdate = null
                }) { Text("Ignorieren") }
            }
        )
    }

    // Rate prompt — only when no update dialog is showing
    if (showRatePrompt && startupUpdate == null) {
        com.tamerin.sysmonitor.ui.components.RateDialog(onDismiss = { showRatePrompt = false })
    }

    // First-launch onboarding — only when no other dialog is queued
    if (showOnboarding && startupUpdate == null && !showRatePrompt) {
        com.tamerin.sysmonitor.ui.components.OnboardingDialog(onDismiss = {
            com.tamerin.sysmonitor.settings.AppPrefs.setFirstLaunchOnboardingDone(context, true)
            showOnboarding = false
        })
    }

    // Cloud-Backup-Restore-Prompt (only if a fresh install found a matching backup)
    val avail = restoreAvailable
    if (avail != null && startupUpdate == null && !showRatePrompt && !showOnboarding) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                com.tamerin.sysmonitor.cloud.RestoreChecker.decline(context, avail)
                restoreAvailable = null
            },
            title = { androidx.compose.material3.Text("Cloud-Backup gefunden") },
            text = {
                val date = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(avail.createdAt))
                androidx.compose.material3.Text(
                    "Für dieses Gerät liegt ein verschlüsseltes Backup vom $date " +
                        "(App ${avail.sourceVersion}) auf dem Server. Wiederherstellen?"
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.CONFIRM)
                    val a = avail
                    restoreAvailable = null
                    kotlinx.coroutines.MainScope().launch {
                        restoreSummary = com.tamerin.sysmonitor.cloud.RestoreChecker
                            .applyAndMark(context, a)
                    }
                }) { androidx.compose.material3.Text("Wiederherstellen") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TAP)
                    com.tamerin.sysmonitor.cloud.RestoreChecker.decline(context, avail)
                    restoreAvailable = null
                }) { androidx.compose.material3.Text("Nein, neu starten") }
            }
        )
    }

    val summary = restoreSummary
    if (summary != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { restoreSummary = null },
            title = { androidx.compose.material3.Text("Wiederherstellung fertig") },
            text = {
                androidx.compose.material3.Text(
                    "${summary.prefsApplied} Settings + ${summary.batterySamplesInserted} " +
                        "Akku-Samples übernommen."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { restoreSummary = null }) {
                    androidx.compose.material3.Text("OK")
                }
            }
        )
    }
    } // CompositionLocalProvider
}

private fun NavController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
