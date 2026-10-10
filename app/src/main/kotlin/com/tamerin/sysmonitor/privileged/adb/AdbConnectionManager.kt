package com.tamerin.sysmonitor.privileged.adb

import android.content.Context
import android.os.IBinder
import android.util.Log
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.data.ShizukuHelper.CmdResult
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Quelle der Wahrheit für den eigenen ADB-Zugang. Primärer Weg ist der eingebettete
 * [AdbClient] (klassisch über TCP, z. B. nach `adb tcpip 5555`) — die App verbindet
 * sich selbst mit dem geräteeigenen `adbd` und führt Kommandos direkt über ADB aus.
 *
 * Der frühere PC-/Binder-Weg (app_process-Starter → [IPrivilegedService]) wird als
 * Fallback weiter unterstützt: kommt ein Binder rein, wird er ebenfalls genutzt.
 */
object AdbConnectionManager {

    private const val TAG = "AdbConn"

    @Volatile private var client: AdbClient? = null
    @Volatile private var service: IPrivilegedService? = null

    /** Für lokalisierte Fehlertexte in [exec] (kein Context-Parameter dort). */
    @Volatile private var appContext: Context? = null

    private fun str(resId: Int): String =
        appContext?.getString(resId) ?: fallback(resId)

    /** Falls noch kein Context gecacht wurde (sollte selten sein): neutraler Text. */
    private fun fallback(resId: Int): String = when (resId) {
        R.string.scr2_adb_err_exec -> "ADB error"
        R.string.scr2_adb_err_binder -> "Binder error"
        R.string.scr2_adb_err_not_connected -> "ADB not connected"
        else -> "ADB error"
    }

    private val deathRecipient = IBinder.DeathRecipient {
        Log.w(TAG, "Privilegierter Binder-Dienst gestorben")
        service = null
    }

    // ---- ADB-Client-Weg (tcpip) ----------------------------------------------------

    /**
     * Verbindet den eingebetteten ADB-Client. Blockiert ggf. bis der Nutzer am Gerät
     * „USB-Debugging zulassen" bestätigt. Merkt Erfolg in [AdbPrefs] für den Auto-Connect.
     */
    fun connect(
        context: Context,
        host: String = AdbPrefs.host(context),
        port: Int = AdbPrefs.port(context)
    ): Result<Unit> = runCatching {
        appContext = context.applicationContext
        val key = AdbKey.getOrCreate(context)
        val c = AdbClient(host, port, key)
        if (!c.connect()) throw IOException(context.getString(R.string.scr2_adb_err_auth))
        runCatching { client?.close() }
        client = c
        AdbPrefs.setEndpoint(context, host, port)
        AdbPrefs.setEnabled(context, true)
        Log.i(TAG, "ADB-Client verbunden ($host:$port)")
    }

    /** Auto-Connect beim App-Start: still, wirft nicht. true bei Erfolg. */
    fun autoConnect(context: Context): Boolean {
        if (isConnected()) return true
        // Erst tcpip (falls konfiguriert), dann Wireless-Connect-Port probieren.
        if (connect(context).isSuccess) return true
        return connectWireless(context).isSuccess
    }

    // ---- Wireless-Debugging-Weg (ohne PC) ------------------------------------------

    /**
     * Verbindet über den per mDNS beworbenen Wireless-Debugging-CONNECT-Port (TLS). Setzt
     * voraus, dass unser Key bereits vertraut ist (einmal via tcpip-„Zulassen" oder Pairing).
     * Dann „immer offline verfügbar", sobald Wireless Debugging an ist — ganz ohne PC.
     */
    fun connectWireless(context: Context, discoverTimeoutMs: Long = 8000): Result<Unit> = runCatching {
        appContext = context.applicationContext
        val (addr, port) = discover(context, AdbMdns.SERVICE_CONNECT, discoverTimeoutMs)
            ?: throw IOException(context.getString(R.string.scr2_adb_err_no_connect_svc))
        val key = AdbKey.getOrCreate(context)
        val c = AdbClient(addr.hostAddress ?: "127.0.0.1", port, key)
        if (!c.connect()) throw IOException(context.getString(R.string.scr2_adb_err_wireless_fail))
        runCatching { client?.close() }
        client = c
        AdbPrefs.setEndpoint(context, addr.hostAddress ?: "127.0.0.1", port)
        AdbPrefs.setEnabled(context, true)
        Log.i(TAG, "Wireless-ADB verbunden (${addr.hostAddress}:$port)")
    }

    /**
     * Erstmaliges Pairing über Wireless Debugging mit dem 6-stelligen Code (ohne PC).
     * DEVICE-VERIFY: hängt am SPAKE2-Kern ([Spake2]) — bricht bis zu dessen Portierung
     * mit erklärender Meldung ab.
     */
    fun pairWireless(context: Context, code: String, discoverTimeoutMs: Long = 8000): Result<Unit> = runCatching {
        appContext = context.applicationContext
        val (addr, port) = discover(context, AdbMdns.SERVICE_PAIRING, discoverTimeoutMs)
            ?: throw IOException(context.getString(R.string.scr2_adb_err_no_pairing_svc))
        val key = AdbKey.getOrCreate(context)
        AdbPairingClient(addr.hostAddress ?: "127.0.0.1", port, code, key).pair().getOrThrow()
    }

    /** Wartet (blockierend) bis der mDNS-Dienst gefunden ist oder das Timeout greift. */
    private fun discover(context: Context, type: String, timeoutMs: Long): Pair<InetAddress, Int>? {
        val latch = CountDownLatch(1)
        val result = AtomicReference<Pair<InetAddress, Int>?>()
        val mdns = AdbMdns(context, type) { host, port ->
            if (result.compareAndSet(null, host to port)) latch.countDown()
        }
        mdns.start()
        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } finally {
            mdns.stop()
        }
        return result.get()
    }

    // ---- PC-/Binder-Weg (optionaler Fallback) --------------------------------------

    fun onBinderReceived(binder: IBinder) {
        val svc = IPrivilegedService.Stub.asInterface(binder) ?: return
        val uid = runCatching { svc.uid }.getOrDefault(-1)
        if (uid != 2000) {
            Log.w(TAG, "Binder abgelehnt: UID=$uid (erwartet 2000)")
            return
        }
        runCatching { binder.linkToDeath(deathRecipient, 0) }
        service = svc
        Log.i(TAG, "Privilegierter Binder-Dienst verbunden")
    }

    // ---- Gemeinsame API ------------------------------------------------------------

    fun isConnected(): Boolean =
        (client?.isConnected() == true) ||
            runCatching { service?.asBinder()?.pingBinder() == true }.getOrDefault(false)

    fun exec(cmd: Array<String>): CmdResult {
        client?.let { c ->
            if (c.isConnected()) {
                return runCatching { c.shellExec(toCommandLine(cmd)).toCmdResult() }
                    .getOrElse { CmdResult(-1, "", it.message ?: str(R.string.scr2_adb_err_exec)) }
            }
        }
        service?.let { s ->
            return runCatching { s.exec(cmd).toCmdResult() }
                .getOrElse { CmdResult(-1, "", it.message ?: str(R.string.scr2_adb_err_binder)) }
        }
        return CmdResult(-1, "", str(R.string.scr2_adb_err_not_connected))
    }

    fun disconnect(context: Context? = null) {
        runCatching { client?.close() }
        client = null
        runCatching { service?.destroy() }
        service = null
        context?.let { AdbPrefs.setEnabled(it, false) }
    }

    /** argv → shell-sichere Kommandozeile: jedes Argument einzeln in Single-Quotes. */
    private fun toCommandLine(cmd: Array<String>): String =
        cmd.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
}
