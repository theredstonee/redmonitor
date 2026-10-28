package com.tamerin.sysmonitor.privileged.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.data.ShizukuHelper
import java.util.concurrent.Executors

/**
 * Wireless-Debugging-Pairing per Benachrichtigung — das Problem dabei: der System-Dialog
 * „Gerät mit Kopplungscode koppeln" schließt sich, sobald man zu einer anderen App wechselt,
 * und mit ihm verschwindet der Pairing-Dienst. Deshalb wird der Code direkt in der
 * Benachrichtigung eingegeben (RemoteInput), während der Dialog offen bleibt.
 *
 * Ablauf: Dienst beobachtet per mDNS `_adb-tls-pairing._tcp`. Taucht er auf, ist der Dialog
 * offen → Benachrichtigung wechselt auf „Code eingeben". Antwort → Pairing gegen genau
 * diesen Port, danach Connect. Erfolg beendet den Dienst; Fehler lässt erneut eingeben.
 */
class AdbPairingService : Service() {

    private enum class Phase { SEARCHING, READY, PAIRING }

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var mdns: AdbMdns? = null

    @Volatile private var phase = Phase.SEARCHING
    @Volatile private var endpoint: Pair<String, Int>? = null
    @Volatile private var lastError: String? = null

    private val timeout = Runnable {
        Log.i(TAG, "Kein Pairing innerhalb des Zeitfensters — Dienst beendet")
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startInForeground(build())
        mdns = AdbMdns(this, AdbMdns.SERVICE_PAIRING, onLost = {
            main.post {
                if (phase != Phase.PAIRING) {
                    endpoint = null
                    phase = Phase.SEARCHING
                    update()
                }
            }
        }) { host, port ->
            main.post {
                endpoint = (host.hostAddress ?: "127.0.0.1") to port
                if (phase == Phase.SEARCHING) {
                    phase = Phase.READY
                    update()
                }
            }
        }.also { it.start() }
        main.postDelayed(timeout, TIMEOUT_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_REPLY -> {
                val code = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_CODE)?.toString()?.filter { it.isDigit() }
                if (!code.isNullOrEmpty()) pair(code)
            }
        }
        return START_NOT_STICKY
    }

    private fun pair(code: String) {
        val ep = endpoint
        if (ep == null || phase == Phase.PAIRING) {
            update()
            return
        }
        phase = Phase.PAIRING
        lastError = null
        update()
        worker.execute {
            val res = AdbConnectionManager.pairAt(this, ep.first, ep.second, code)
            main.post {
                if (res.isSuccess) {
                    onPaired()
                } else {
                    lastError = res.exceptionOrNull()?.message ?: getString(R.string.scr2_shz_failed_word)
                    // Nach einem Fehlversuch erzeugt adbd meist einen neuen Port/Code; mDNS
                    // meldet ihn neu. Bis dahin bleibt der bekannte Endpunkt gültig.
                    phase = if (endpoint != null) Phase.READY else Phase.SEARCHING
                    update()
                }
            }
        }
    }

    private fun onPaired() {
        Log.i(TAG, "Pairing über Benachrichtigung erfolgreich")
        // Mit dem frisch vertrauten Key gleich die Berechtigungen setzen — wie nach einem
        // normalen Connect in MainActivity.
        worker.execute {
            if (AdbConnectionManager.isConnected()) {
                runCatching { ShizukuHelper.autoGrantAllPermissions(this) }
            }
        }
        val nm = getSystemService(NotificationManager::class.java)
        val done = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.adbpair_notif_done_title))
            .setContentText(
                getString(
                    if (AdbConnectionManager.isConnected()) R.string.adbpair_notif_done_connected
                    else R.string.adbpair_notif_done_paired
                )
            )
            .setAutoCancel(true)
            .build()
        nm.notify(RESULT_NOTIFICATION_ID, done)
        stopSelf()
    }

    private fun update() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build())
    }

    private fun build(): Notification {
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setOnlyAlertOnce(phase != Phase.READY)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.adbpair_notif_stop), servicePending(ACTION_STOP, 1))

        when (phase) {
            Phase.SEARCHING -> b
                .setContentTitle(getString(R.string.adbpair_notif_search_title))
                .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.adbpair_notif_search_text)))
                .setContentText(getString(R.string.adbpair_notif_search_text))
                .addAction(0, getString(R.string.adbpair_notif_open_settings), settingsPending())

            Phase.READY -> {
                val input = RemoteInput.Builder(KEY_CODE)
                    .setLabel(getString(R.string.adbpair_notif_code_hint))
                    .build()
                val reply = NotificationCompat.Action.Builder(
                    0, getString(R.string.adbpair_notif_enter_code), replyPending()
                ).addRemoteInput(input).setAllowGeneratedReplies(false).build()
                val text = lastError?.let { getString(R.string.adbpair_notif_retry, it) }
                    ?: getString(R.string.adbpair_notif_ready_text)
                b.setContentTitle(getString(R.string.adbpair_notif_ready_title))
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .addAction(reply)
            }

            Phase.PAIRING -> b
                .setContentTitle(getString(R.string.adbpair_notif_pairing_title))
                .setProgress(0, 0, true)
        }
        return b.build()
    }

    private fun servicePending(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, AdbPairingService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    // RemoteInput schreibt das Ergebnis in den Intent → muss MUTABLE sein.
    private fun replyPending(): PendingIntent =
        PendingIntent.getService(
            this, 2,
            Intent(this, AdbPairingService::class.java).setAction(ACTION_REPLY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

    private fun settingsPending(): PendingIntent =
        PendingIntent.getActivity(
            this, 3, developerSettingsIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun startInForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        // HIGH, damit die Benachrichtigung über dem Einstellungs-Dialog als Heads-up erscheint.
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.adbpair_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.adbpair_channel_desc)
                setShowBadge(false)
                setSound(null, null)
            }
        )
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        mdns?.stop()
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AdbPairingSvc"
        private const val CHANNEL_ID = "adb_pairing"
        private const val NOTIFICATION_ID = 7410
        private const val RESULT_NOTIFICATION_ID = 7411
        private const val TIMEOUT_MS = 10 * 60 * 1000L

        private const val ACTION_STOP = "com.tamerin.sysmonitor.adbpair.STOP"
        private const val ACTION_REPLY = "com.tamerin.sysmonitor.adbpair.REPLY"
        private const val KEY_CODE = "pairing_code"

        /** Startet die Pairing-Benachrichtigung und öffnet die Wireless-Debugging-Seite. */
        fun start(context: Context) {
            val i = Intent(context, AdbPairingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
            runCatching {
                context.startActivity(developerSettingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        /** Wireless-Debugging direkt (ab Android 11), sonst Entwickleroptionen. */
        private fun developerSettingsIntent(context: Context): Intent {
            val wireless = Intent("android.settings.ADB_WIRELESS_SETTINGS")
            return if (wireless.resolveActivity(context.packageManager) != null) wireless
            else Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        }
    }
}
