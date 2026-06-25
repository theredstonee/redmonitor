package com.tamerin.sysmonitor.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

private data class Sat(val svid: Int, val cnoDbHz: Float, val used: Boolean,
                       val constellation: String)

@Composable
fun GnssLiveScreen() {
    val context = LocalContext.current
    val lm = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    var sats by remember { mutableStateOf<List<Sat>>(emptyList()) }
    var hasPerm by remember { mutableStateOf(checkLocPerm(context)) }

    DisposableEffect(hasPerm) {
        if (!hasPerm) return@DisposableEffect onDispose { }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return@DisposableEffect onDispose { }
        val cb = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                val list = mutableListOf<Sat>()
                for (i in 0 until status.satelliteCount) {
                    val cName = constellationName(status.getConstellationType(i))
                    list += Sat(
                        svid = status.getSvid(i),
                        cnoDbHz = status.getCn0DbHz(i),
                        used = status.usedInFix(i),
                        constellation = cName
                    )
                }
                sats = list.sortedWith(compareBy({ it.constellation }, { -it.cnoDbHz }))
            }
        }
        runCatching { lm.registerGnssStatusCallback(cb, null) }
        // Provider muss aktiv "anfragen" damit der Callback feuert
        val locListener = android.location.LocationListener { }
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locListener)
        }
        onDispose {
            runCatching { lm.unregisterGnssStatusCallback(cb) }
            runCatching { lm.removeUpdates(locListener) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        if (!hasPerm) {
            StatCard("Location-Permission nötig") {
                Text(
                    "GNSS-Sat-Status braucht ACCESS_FINE_LOCATION. " +
                        "Erteile sie in den App-Settings oder via Shizuku-Auto-Grant.",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
            }
            return@Column
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            StatCard("Zu altes Android") {
                Text("Brauche Android 7+ (GnssStatus.Callback API).",
                    color = OnSurfaceMuted, fontSize = 12.sp)
            }
            return@Column
        }

        val byConst = sats.groupBy { it.constellation }
        val total = sats.size
        val used = sats.count { it.used }
        val avgCno = sats.filter { it.cnoDbHz > 0 }.map { it.cnoDbHz }
            .average().let { if (it.isNaN()) 0.0 else it }

        StatCard("GNSS Live") {
            KeyValueRow("Sichtbar", "$total")
            KeyValueRow("Im Fix", "$used")
            KeyValueRow("Ø C/N0", "%.1f dBHz".format(avgCno))
            byConst.forEach { (c, list) ->
                KeyValueRow(c, "${list.size} (${list.count { it.used }} im Fix)")
            }
            if (sats.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Warte auf GPS-Fix… (kann im Gebäude dauern)",
                    color = OnSurfaceMuted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(sats, key = { it.constellation + "_" + it.svid }) { sat ->
                SatRow(sat)
            }
        }
    }
}

@Composable
private fun SatRow(sat: Sat) {
    val color = when {
        sat.cnoDbHz >= 40 -> GaugeGreen
        sat.cnoDbHz >= 25 -> AccentSoft
        sat.cnoDbHz >= 15 -> GaugeOrange
        else -> GaugeRed
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
            .background(Color(0x14FFFFFF)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(sat.constellation.take(3), color = AccentSoft, fontSize = 10.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.width(32.dp))
        Text("#${sat.svid}", color = OnSurfaceMuted, fontSize = 10.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.width(40.dp))
        Box(modifier = Modifier.weight(1f).height(8.dp).background(Color(0x22FFFFFF))) {
            Box(modifier = Modifier
                .fillMaxWidth((sat.cnoDbHz.coerceIn(0f, 50f) / 50f))
                .fillMaxHeight().background(color))
        }
        Spacer(Modifier.width(6.dp))
        Text("%.0f".format(sat.cnoDbHz), color = color, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp))
        Text(if (sat.used) "✓" else " ", color = GaugeGreen, fontSize = 11.sp,
            fontWeight = FontWeight.Bold)
    }
}

private fun constellationName(t: Int): String = when (t) {
    GnssStatus.CONSTELLATION_GPS -> "GPS"
    GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
    GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
    GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
    GnssStatus.CONSTELLATION_QZSS -> "QZSS"
    GnssStatus.CONSTELLATION_SBAS -> "SBAS"
    GnssStatus.CONSTELLATION_IRNSS -> "IRNSS"
    else -> "Andere"
}

private fun checkLocPerm(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
