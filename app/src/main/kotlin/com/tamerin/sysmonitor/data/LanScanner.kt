package com.tamerin.sysmonitor.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

@androidx.compose.runtime.Immutable
data class LanDevice(
    val ip: String,
    val mac: String?,
    val hostname: String?,
    val openPorts: List<Int>,
    val isSelf: Boolean,
    val isGateway: Boolean
)

object LanScanner {

    /** Common TCP ports that almost any LAN host has at least one of. */
    private val PROBE_PORTS = listOf(80, 443, 22, 445, 139, 8080, 23, 21, 53, 7)
    private const val PROBE_TIMEOUT_MS = 350

    suspend fun scan(
        context: Context,
        progress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): List<LanDevice> = coroutineScope {
        val ifaceInfo = withContext(Dispatchers.IO) { detectActiveSubnet(context) }
            ?: return@coroutineScope emptyList()

        val (ownIp, gateway, prefixLen) = ifaceInfo
        // /24 is the realistic LAN cap; /16 has 65k hosts which we won't scan
        val effectivePrefix = maxOf(prefixLen, 24)
        val hosts = hostsInSubnet(ownIp, effectivePrefix)

        val arpMap = withContext(Dispatchers.IO) { readArpTable() }

        var done = 0
        val total = hosts.size
        val results = hosts.map { ip ->
            async(Dispatchers.IO) {
                val openPorts = if (ip == ownIp) emptyList() else probeHost(ip)
                val seen = ip == ownIp || ip in arpMap || openPorts.isNotEmpty()
                synchronized(this) {
                    done++
                    progress(done, total)
                }
                if (!seen) return@async null
                val hostname = runCatching {
                    withTimeoutOrNull(400) {
                        InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip }
                    }
                }.getOrNull()
                LanDevice(
                    ip = ip,
                    mac = arpMap[ip],
                    hostname = hostname,
                    openPorts = openPorts,
                    isSelf = ip == ownIp,
                    isGateway = ip == gateway
                )
            }
        }.awaitAll().filterNotNull()

        results.sortedWith(compareByDescending<LanDevice> { it.isSelf }
            .thenByDescending { it.isGateway }
            .thenBy { ipToLong(it.ip) })
    }

    private data class IfaceInfo(val ownIp: String, val gateway: String?, val prefixLen: Int)

    private fun detectActiveSubnet(context: Context): IfaceInfo? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val network = cm.activeNetwork ?: return null
        val props: LinkProperties = cm.getLinkProperties(network) ?: return null
        val ownV4 = props.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        val gateway = props.routes
            .firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress
        return IfaceInfo(
            ownIp = ownV4.address.hostAddress ?: return null,
            gateway = gateway,
            prefixLen = ownV4.prefixLength
        )
    }

    private fun hostsInSubnet(ownIp: String, prefixLen: Int): List<String> {
        val ownLong = ipToLong(ownIp)
        val mask = if (prefixLen == 0) 0L else (-1L shl (32 - prefixLen)) and 0xFFFFFFFFL
        val net = ownLong and mask
        val hostBits = 32 - prefixLen
        val hostCount = (1L shl hostBits)
        // Cap to /24 (256) for safety
        val safeCount = minOf(hostCount, 256L).toInt()
        val out = ArrayList<String>(safeCount)
        for (i in 1 until safeCount - 1) {
            val ip = net or i.toLong()
            out += longToIp(ip)
        }
        return out
    }

    private fun probeHost(ip: String): List<Int> {
        val open = mutableListOf<Int>()
        for (port in PROBE_PORTS) {
            val ok = runCatching {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ip, port), PROBE_TIMEOUT_MS)
                    true
                }
            }.getOrDefault(false)
            if (ok) {
                open += port
                if (open.size >= 2) break // 2 is enough confirmation
            }
        }
        return open
    }

    /**
     * /proc/net/arp format:
     * IP address       HW type     Flags       HW address            Mask     Device
     * 192.168.1.1      0x1         0x2         aa:bb:cc:dd:ee:ff     *        wlan0
     * Flags 0x0 = invalid; only return entries with 0x2 (complete).
     */
    fun readArpTable(): Map<String, String> {
        val out = mutableMapOf<String, String>()
        runCatching {
            File("/proc/net/arp").useLines { lines ->
                for ((idx, line) in lines.withIndex()) {
                    if (idx == 0) continue
                    val cols = line.trim().split(Regex("\\s+"))
                    if (cols.size < 4) continue
                    val ip = cols[0]
                    val flags = cols[2]
                    val mac = cols[3]
                    if (flags != "0x0" && mac != "00:00:00:00:00:00" && mac.contains(':')) {
                        out[ip] = mac
                    }
                }
            }
        }
        return out
    }

    private fun ipToLong(ip: String): Long {
        val p = ip.split(".")
        if (p.size != 4) return 0
        return (p[0].toLong() shl 24) or
            (p[1].toLong() shl 16) or
            (p[2].toLong() shl 8) or
            p[3].toLong()
    }

    private fun longToIp(v: Long): String {
        return "${(v shr 24) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 8) and 0xFF}.${v and 0xFF}"
    }

    fun ownInterfaces(): List<String> {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .map { iface ->
                    val addrs = iface.inetAddresses.toList()
                        .filterIsInstance<Inet4Address>()
                        .joinToString(", ") { it.hostAddress ?: "?" }
                    "${iface.displayName}: $addrs"
                }
        }.getOrDefault(emptyList())
    }
}
