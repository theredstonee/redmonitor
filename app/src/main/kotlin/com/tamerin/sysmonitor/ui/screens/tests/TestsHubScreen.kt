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

private val TEST_ENTRIES = listOf(
    HubEntry(Routes.TEST_TOUCH, "Multi-Touch", "Alle Finger gleichzeitig", Icons.Filled.TouchApp,
        activityClass = MultiTouchStandaloneActivity::class.java),
    HubEntry(Routes.TEST_DISPLAY, "Display-Farben", "Dead-Pixel-Check", Icons.Filled.Palette,
        activityClass = DisplayTestStandaloneActivity::class.java),
    HubEntry(Routes.TEST_VIBRATE, "Vibration", "Muster testen", Icons.Filled.Vibration),
    HubEntry(Routes.TEST_FLASH, "Taschenlampe", "Kamera-LED an/aus", Icons.Filled.FlashlightOn,
        activityClass = FlashlightStandaloneActivity::class.java),
    HubEntry(Routes.TEST_SPEAKER, "Speaker-Ton", "Tongenerator L/R", Icons.Filled.VolumeUp,
        activityClass = SpeakerTestStandaloneActivity::class.java),
    HubEntry(Routes.TEST_MIC, "Mikrofon", "Live-Pegelmeter", Icons.Filled.Mic,
        activityClass = MicTestStandaloneActivity::class.java),
    HubEntry(Routes.TEST_GPS, "GPS / Satelliten", "Live Position + Sats", Icons.Filled.GpsFixed),
    HubEntry(Routes.TEST_PROXIMITY, "Proximity & Licht", "Beide Sensoren visuell", Icons.Filled.RadioButtonChecked),
    HubEntry("test/earpiece", "Earpiece", "Hörmuschel-Ton", Icons.Filled.Headset,
        activityClass = EarpieceTestStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("test/edges", "Edge-Rejection", "Curved-Screen Palm-Test", Icons.Filled.LineWeight,
        activityClass = EdgeRejectionStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("test/brightness", "Brightness", "PWM + Banding-Check", Icons.Filled.Brightness6,
        activityClass = BrightnessTestStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("test/nfc-tap", "NFC-Tap", "ReaderMode + NDEF", Icons.Filled.Nfc,
        activityClass = NfcTapStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("test/ir", "IR-Blaster", "Carrier + Test-Burst", Icons.Filled.SettingsRemote,
        activityClass = IrBlasterStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("test/compass", "Kompass", "Magnetfeld + Heading", Icons.Filled.Explore,
        activityClass = CompassStandaloneActivity::class.java, badge = "NEU"),
    HubEntry("test/barometer", "Barometer", "hPa + Höhe", Icons.Filled.Compress,
        activityClass = BarometerStandaloneActivity::class.java, badge = "NEU")
)

@Composable
fun TestsHubScreen(onNavigate: (String) -> Unit) {
    HubGrid(entries = TEST_ENTRIES, onClick = { onNavigate(it.route) })
}
