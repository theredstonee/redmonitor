package com.tamerin.sysmonitor.privileged.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.net.InetAddress

/**
 * Findet die vom Gerät selbst per mDNS beworbenen ADB-Dienste auf localhost:
 *  - [SERVICE_PAIRING] `_adb-tls-pairing._tcp` — nur beworben, solange der System-Dialog
 *    „Gerät mit Pairing-Code koppeln" offen ist.
 *  - [SERVICE_CONNECT] `_adb-tls-connect._tcp` — beworben, solange Wireless Debugging an ist.
 *
 * Liefert Host+Port per [onFound]. Der Rest (TLS/Pairing/Connect) baut darauf auf.
 */
class AdbMdns(
    private val context: Context,
    private val serviceType: String,
    /** Dienst verschwunden (z.B. Pairing-Dialog geschlossen). Optional. */
    private val onLost: (() -> Unit)? = null,
    private val onFound: (host: InetAddress, port: Int) -> Unit
) {
    companion object {
        const val SERVICE_PAIRING = "_adb-tls-pairing._tcp"
        const val SERVICE_CONNECT = "_adb-tls-connect._tcp"
        private const val TAG = "AdbMdns"
    }

    private val nsd by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }
    private var started = false

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {}
        override fun onDiscoveryStopped(serviceType: String) {}
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "Discovery-Start fehlgeschlagen: $errorCode")
        }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

        override fun onServiceFound(info: NsdServiceInfo) {
            runCatching { nsd.resolveService(info, resolveListener()) }
        }

        override fun onServiceLost(info: NsdServiceInfo) {
            onLost?.invoke()
        }
    }

    // Für jeden Fund ein frischer ResolveListener (NsdManager erlaubt kein paralleles Resolve
    // mit demselben Listener) — bewusst pro Aufruf neu.
    private fun resolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
            Log.w(TAG, "Resolve fehlgeschlagen: $errorCode")
        }
        override fun onServiceResolved(info: NsdServiceInfo) {
            val host = info.host ?: return
            // Nur localhost/loopback interessiert uns (eigenes Gerät).
            onFound(host, info.port)
        }
    }

    fun start() {
        if (started) return
        started = true
        runCatching {
            nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { nsd.stopServiceDiscovery(discoveryListener) }
    }
}
