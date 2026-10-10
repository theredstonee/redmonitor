package com.tamerin.sysmonitor.privileged.adb

import android.util.Log
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket

/**
 * Wireless-Debugging-Pairing (ohne PC): verbindet den Pairing-Port über TLS 1.3 und
 * führt den SPAKE2-Austausch mit dem 6-stelligen Code durch, danach den verschlüsselten
 * Peer-Info-Austausch (unser ADB-Public-Key → wird in `adbd` vertraut). Danach kann die
 * App dauerhaft über den Connect-Port verbinden — „immer offline verfügbar", ohne PC.
 *
 * DEVICE-VERIFY: Der TLS-Aufbau unten ist deterministisch; der SPAKE2-Kern ([Spake2])
 * ist noch nicht portiert, daher bricht [pair] bis dahin mit klarer Meldung ab.
 *
 * Wire-Format (AOSP `pairing_connection.cpp`), für den späteren Abschluss dokumentiert:
 *  - Header pro Paket: [version:1][type:1 (SPAKE2_MSG=0 / PEER_INFO=1)][payload_size:4 BE]
 *  - 1. SPAKE2-Nachrichten tauschen → Schlüssel ableiten
 *  - 2. PEER_INFO (unser `android_pubkey`) mit AES-128-GCM unter dem abgeleiteten Key
 */
class AdbPairingClient(
    private val host: String,
    private val port: Int,
    private val pairingCode: String,
    private val key: AdbKey
) {
    fun pair(connectTimeoutMs: Int = 8000): Result<Unit> = runCatching {
        if (!Spake2.ed25519Available) {
            throw IOException(
                "In-App-Pairing noch nicht verfügbar: SPAKE2-Kern muss portiert werden. " +
                    "Nutze bis dahin den tcpip-Weg (adb tcpip 5555) oder paire einmal per PC."
            )
        }

        // --- Ab hier erst nutzbar, wenn Spake2.ed25519Available = true ---
        val base = Socket().apply { connect(InetSocketAddress(host, port), connectTimeoutMs) }
        val ctx = AdbSsl.sslContext(key)
        val ssl = (ctx.socketFactory.createSocket(base, host, port, true) as SSLSocket).apply {
            useClientMode = true
            runCatching { enabledProtocols = arrayOf("TLSv1.3") }
            startHandshake()
        }
        try {
            val spake = Spake2(Spake2.Role.CLIENT, pairingCode.toByteArray(Charsets.UTF_8))
            val myMsg = spake.generateMessage()
            // TODO(device): SPAKE2_MSG senden/empfangen, Key ableiten, PEER_INFO (AES-GCM)
            //               austauschen — Framing siehe KDoc oben.
            Log.i(TAG, "SPAKE2-Nachricht erzeugt (${myMsg.size} B) — Austausch folgt nach Port.")
            throw IOException("Pairing-Abschluss noch nicht implementiert (SPAKE2-Austausch).")
        } finally {
            runCatching { ssl.close() }
        }
    }

    companion object {
        private const val TAG = "AdbPairing"
    }
}
