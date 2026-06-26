package com.tamerin.sysmonitor.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.tamerin.sysmonitor.R

/**
 * Vollbild-System-Overlay für den 3-Sek-Russian-Roulette-Shutdown-Countdown.
 *
 * Läuft als Foreground-Service mit TYPE_APPLICATION_OVERLAY-View — bleibt auf
 * dem Bildschirm sichtbar selbst wenn der User die RedMonitor-Activity wegswiped.
 *
 * Macht KEINEN Shutdown selbst — der wird parallel via detached Shell-Job
 * (`setsid sh -c 'sleep 3 && svc power shutdown' &`) gestartet, der unabhängig
 * vom App-Prozess weiterläuft. Dieses Overlay ist nur die Visualisierung.
 */
class ShutdownOverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "sysmonitor_shutdown_overlay"
        private const val NOTIFICATION_ID = 4243
        private const val COUNTDOWN_MS = 3

        fun start(context: Context) {
            val i = Intent(context, ShutdownOverlayService::class.java)
            context.startForegroundService(i)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var root: FrameLayout? = null
    private var bigNumber: TextView? = null
    private var msgLabel: TextView? = null
    private var emojiLabel: TextView? = null
    private var stepsLeft = COUNTDOWN_MS

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification(), foregroundType())
        attachOverlay()
        tick()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Shutdown-Countdown",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("☠ Shutdown in $stepsLeft …")
            .setOngoing(true)
            .build()

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        else 0

    private fun attachOverlay() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#EE000000"))
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        emojiLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 96f)
            gravity = Gravity.CENTER
            text = "👋"
        }
        msgLabel = TextView(this).apply {
            setTextColor(Color.parseColor("#DC2626"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            text = "Tschüss in"
        }
        bigNumber = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 160f)
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE,
                android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            text = "$stepsLeft"
        }
        val sub = TextView(this).apply {
            setTextColor(Color.parseColor("#888888"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            text = "RedMonitor sagt: Es war schön mit dir"
        }
        column.addView(emojiLabel)
        column.addView(msgLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })
        column.addView(bigNumber, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(24) })
        column.addView(sub, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
        container.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        root = container

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        runCatching { wm?.addView(container, params) }
    }

    private fun tick() {
        updateText()
        pulseHaptic()
        handler.postDelayed({
            stepsLeft -= 1
            if (stepsLeft >= 0) tick() else fin()
        }, 1000)
    }

    private fun updateText() {
        val (emoji, msg, number) = when (stepsLeft) {
            3 -> Triple("👋", "Tschüss in", "3")
            2 -> Triple("😬", "Letzte Worte?", "2")
            1 -> Triple("💣", "Gute Reise", "1")
            else -> Triple("💀", "BYE", "💀")
        }
        emojiLabel?.text = emoji
        msgLabel?.text = msg
        bigNumber?.text = number
    }

    private fun fin() {
        // Overlay so lange dranlassen bis der Kernel-shutdown unter uns wegzieht.
        // (Die detached Shell-Job hat parallel `svc power shutdown` getriggert.)
    }

    private fun pulseHaptic() {
        runCatching {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") vib?.vibrate(120)
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        root?.let { v -> runCatching { wm?.removeView(v) } }
        root = null
        super.onDestroy()
    }
}
