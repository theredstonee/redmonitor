package com.tamerin.sysmonitor.ui.screens.tests

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.Routes
import com.tamerin.sysmonitor.ui.BarometerStandaloneActivity
import com.tamerin.sysmonitor.ui.BrightnessTestStandaloneActivity
import com.tamerin.sysmonitor.ui.CompassStandaloneActivity
import com.tamerin.sysmonitor.ui.DisplayTestStandaloneActivity
import com.tamerin.sysmonitor.ui.EarpieceTestStandaloneActivity
import com.tamerin.sysmonitor.ui.EdgeRejectionStandaloneActivity
import com.tamerin.sysmonitor.ui.FlashlightStandaloneActivity
import com.tamerin.sysmonitor.ui.IrBlasterStandaloneActivity
import com.tamerin.sysmonitor.ui.MicTestStandaloneActivity
import com.tamerin.sysmonitor.ui.MultiTouchStandaloneActivity
import com.tamerin.sysmonitor.ui.NfcTapStandaloneActivity
import com.tamerin.sysmonitor.ui.SpeakerTestStandaloneActivity
import com.tamerin.sysmonitor.ui.components.HubEntry
import com.tamerin.sysmonitor.ui.components.HubGrid

@Composable
fun TestsHubScreen(onNavigate: (String) -> Unit) {
    val new = stringResource(R.string.bench_badge_new)
    val entries = listOf(
        HubEntry(Routes.TEST_TOUCH, stringResource(R.string.bench_test_touch_title), stringResource(R.string.bench_test_touch_sub), Icons.Filled.TouchApp,
            activityClass = MultiTouchStandaloneActivity::class.java),
        HubEntry(Routes.TEST_DISPLAY, stringResource(R.string.bench_test_display_title), stringResource(R.string.bench_test_display_sub), Icons.Filled.Palette,
            activityClass = DisplayTestStandaloneActivity::class.java),
        HubEntry(Routes.TEST_VIBRATE, stringResource(R.string.bench_test_vibrate_title), stringResource(R.string.bench_test_vibrate_sub), Icons.Filled.Vibration),
        HubEntry(Routes.TEST_FLASH, stringResource(R.string.bench_test_flash_title), stringResource(R.string.bench_test_flash_sub), Icons.Filled.FlashlightOn,
            activityClass = FlashlightStandaloneActivity::class.java),
        HubEntry(Routes.TEST_SPEAKER, stringResource(R.string.bench_test_speaker_title), stringResource(R.string.bench_test_speaker_sub), Icons.Filled.VolumeUp,
            activityClass = SpeakerTestStandaloneActivity::class.java),
        HubEntry(Routes.TEST_MIC, stringResource(R.string.bench_test_mic_title), stringResource(R.string.bench_test_mic_sub), Icons.Filled.Mic,
            activityClass = MicTestStandaloneActivity::class.java),
        HubEntry(Routes.TEST_GPS, stringResource(R.string.bench_test_gps_title), stringResource(R.string.bench_test_gps_sub), Icons.Filled.GpsFixed),
        HubEntry(Routes.TEST_PROXIMITY, stringResource(R.string.bench_test_proximity_title), stringResource(R.string.bench_test_proximity_sub), Icons.Filled.RadioButtonChecked),
        HubEntry("test/earpiece", stringResource(R.string.bench_test_earpiece_title), stringResource(R.string.bench_test_earpiece_sub), Icons.Filled.Headset,
            activityClass = EarpieceTestStandaloneActivity::class.java, badge = new),
        HubEntry("test/edges", stringResource(R.string.bench_test_edges_title), stringResource(R.string.bench_test_edges_sub), Icons.Filled.LineWeight,
            activityClass = EdgeRejectionStandaloneActivity::class.java, badge = new),
        HubEntry("test/brightness", stringResource(R.string.bench_test_brightness_title), stringResource(R.string.bench_test_brightness_sub), Icons.Filled.Brightness6,
            activityClass = BrightnessTestStandaloneActivity::class.java, badge = new),
        HubEntry("test/nfc-tap", stringResource(R.string.bench_test_nfc_title), stringResource(R.string.bench_test_nfc_sub), Icons.Filled.Nfc,
            activityClass = NfcTapStandaloneActivity::class.java, badge = new),
        HubEntry("test/ir", stringResource(R.string.bench_test_ir_title), stringResource(R.string.bench_test_ir_sub), Icons.Filled.SettingsRemote,
            activityClass = IrBlasterStandaloneActivity::class.java, badge = new),
        HubEntry("test/compass", stringResource(R.string.bench_test_compass_title), stringResource(R.string.bench_test_compass_sub), Icons.Filled.Explore,
            activityClass = CompassStandaloneActivity::class.java, badge = new),
        HubEntry("test/barometer", stringResource(R.string.bench_test_barometer_title), stringResource(R.string.bench_test_barometer_sub), Icons.Filled.Compress,
            activityClass = BarometerStandaloneActivity::class.java, badge = new)
    )
    HubGrid(entries = entries, onClick = { onNavigate(it.route) })
}
