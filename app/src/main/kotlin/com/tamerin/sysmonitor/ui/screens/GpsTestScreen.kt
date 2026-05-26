package com.tamerin.sysmonitor.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

@Composable
fun GpsTestScreen() {
    val context = LocalContext.current
    var hasPerm by remember { mutableStateOf(checkLocPerm(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPerm = it }

    var loc by remember { mutableStateOf<Location?>(null) }
    var provider by remember { mutableStateOf<String?>(null) }
    var totalSats by remember { mutableIntStateOf(0) }
    var usedInFix by remember { mutableIntStateOf(0) }
    var maxSnr by remember { mutableFloatStateOf(0f) }
    var gpsEnabled by remember { mutableStateOf(false) }

    val lm = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }

    DisposableEffect(hasPerm) {
        if (!hasPerm) return@DisposableEffect onDispose { }
        gpsEnabled = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)

        val locListener = LocationListener { l ->
            loc = l
            provider = l.provider
        }
        val gnssCb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    var inFix = 0
                    var best = 0f
                    for (i in 0 until status.satelliteCount) {
                        if (status.usedInFix(i)) inFix++
                        val snr = status.getCn0DbHz(i)
                        if (snr > best) best = snr
                    }
                    totalSats = status.satelliteCount
                    usedInFix = inFix
                    maxSnr = best
                }
            }
        } else null

        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locListener)
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 0f, locListener)
            }
            if (gnssCb != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                lm.registerGnssStatusCallback(gnssCb, null)
            }
        } catch (_: SecurityException) {}

        onDispose {
            runCatching { lm.removeUpdates(locListener) }
            if (gnssCb != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching { lm.unregisterGnssStatusCallback(gnssCb) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!hasPerm) {
            StatCard("Standort-Berechtigung") {
                Text(
                    "Für GPS-Test brauchen wir die Berechtigung „Genauer Standort“. Außerdem muss GPS systemweit eingeschaltet sein.",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                    Text("Berechtigung erteilen")
                }
            }
            return@Column
        }

        StatCard("Status") {
            KeyValueRow("GPS-Provider aktiv", if (gpsEnabled) "Ja" else "Nein")
            KeyValueRow("Aktiver Provider", provider ?: "—")
            KeyValueRow("Satelliten gesamt", totalSats.toString())
            KeyValueRow("Im Fix verwendet", usedInFix.toString())
            KeyValueRow("Beste Signalstärke", if (maxSnr > 0) "${"%.1f".format(maxSnr)} dB-Hz" else "—")
        }

        StatCard("Position") {
            val l = loc
            if (l == null) {
                Text(
                    "Warte auf Fix… Stell dich am besten ans Fenster oder ins Freie.",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
            } else {
                KeyValueRow("Breitengrad", "${"%.6f".format(l.latitude)}")
                KeyValueRow("Längengrad", "${"%.6f".format(l.longitude)}")
                KeyValueRow("Genauigkeit", "${"%.1f".format(l.accuracy)} m")
                if (l.hasAltitude()) KeyValueRow("Höhe", "${"%.1f".format(l.altitude)} m")
                if (l.hasSpeed()) KeyValueRow("Geschwindigkeit", "${"%.2f".format(l.speed)} m/s")
                if (l.hasBearing()) KeyValueRow("Richtung", "${"%.0f".format(l.bearing)}°")
            }
        }
    }
}

private fun checkLocPerm(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
