package com.tamerin.sysmonitor.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Build
import java.net.NetworkInterface

@androidx.compose.runtime.Immutable
data class NetworkSnapshot(
    val transportLabel: String,
    val ssid: String?,
    val linkSpeedMbps: Int?,
    val signalLevelPercent: Float?,
    val downstreamKbps: Int,
    val upstreamKbps: Int,
    val ipv4: String?,
    val ipv6: String?,
    val totalRxBytes: Long,
    val totalTxBytes: Long,
    val isMetered: Boolean
)

object NetworkReader {
    fun read(context: Context): NetworkSnapshot {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }

        val transport = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WLAN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobilfunk"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Unbekannt"
        }

        val down = caps?.linkDownstreamBandwidthKbps ?: 0
        val up = caps?.linkUpstreamBandwidthKbps ?: 0

        var ssid: String? = null
        var linkSpeed: Int? = null
        var signal: Float? = null
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val info = wm.connectionInfo
            @Suppress("DEPRECATION")
            ssid = info.ssid?.removePrefix("\"")?.removeSuffix("\"")
            @Suppress("DEPRECATION")
            linkSpeed = info.linkSpeed
            @Suppress("DEPRECATION")
            val rssi = info.rssi
            signal = WifiManager.calculateSignalLevel(rssi, 101).toFloat()
        }

        val (ipv4, ipv6) = collectIps()

        return NetworkSnapshot(
            transportLabel = transport,
            ssid = ssid,
            linkSpeedMbps = linkSpeed,
            signalLevelPercent = signal,
            downstreamKbps = down,
            upstreamKbps = up,
            ipv4 = ipv4,
            ipv6 = ipv6,
            totalRxBytes = TrafficStats.getTotalRxBytes().coerceAtLeast(0),
            totalTxBytes = TrafficStats.getTotalTxBytes().coerceAtLeast(0),
            isMetered = cm.isActiveNetworkMetered
        )
    }

    private fun collectIps(): Pair<String?, String?> {
        var v4: String? = null
        var v6: String? = null
        runCatching {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
            for (iface in ifaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr.isLoopbackAddress) continue
                    val host = addr.hostAddress ?: continue
                    if (host.contains(':')) {
                        if (v6 == null) v6 = host.substringBefore('%')
                    } else {
                        if (v4 == null) v4 = host
                    }
                }
            }
        }
        return v4 to v6
    }
}
