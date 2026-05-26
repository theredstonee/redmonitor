package com.tamerin.sysmonitor.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.tamerin.sysmonitor.MainActivity
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.data.BatteryReader
import com.tamerin.sysmonitor.data.CpuReader
import com.tamerin.sysmonitor.data.MemoryReader
import com.tamerin.sysmonitor.data.ThermalReader

class OverlayService : Service() {

    companion object {
        const val ACTION_START = "com.tamerin.sysmonitor.overlay.START"
        const val ACTION_STOP = "com.tamerin.sysmonitor.overlay.STOP"
        private const val CHANNEL_ID = "sysmonitor_overlay"
        private const val NOTIFICATION_ID = 4242

        fun start(context: Context) {
            val i = Intent(context, OverlayService::class.java).setAction(ACTION_START)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            val i = Intent(context, OverlayService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }
    }

    private var view: View? = null
    private var windowManager: WindowManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var textView: TextView

    private val tick = object : Runnable {
        override fun run() {
            updateText()
            handler.postDelayed(this, 1500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startOverlay()
        }
        return START_STICKY
    }

    private fun startOverlay() {
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification(), foregroundType())

        if (view != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val density = resources.displayMetrics.density
        fun Int.px(): Int = (this * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(AndroidColor.parseColor("#CC0B0F14"))
                cornerRadius = 14f * density
                setStroke(1, AndroidColor.parseColor("#4FC3F7"))
            }
            setPadding(10.px(), 8.px(), 10.px(), 8.px())
        }
        textView = TextView(this).apply {
            setTextColor(AndroidColor.parseColor("#E6ECF2"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = android.graphics.Typeface.MONOSPACE
            text = "starte…"
        }
        container.addView(textView)
        view = container

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16.px()
            y = 80.px()
        }

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var downTime = 0L
        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    downTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    runCatching { windowManager?.updateViewLayout(container, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = kotlin.math.abs(event.rawX - touchX)
                    val dy = kotlin.math.abs(event.rawY - touchY)
                    val held = System.currentTimeMillis() - downTime
                    if (dx < 10 && dy < 10 && held < 500) {
                        // Tap = open main app
                        val open = Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(open)
                    }
                    true
                }
                else -> false
            }
        }

        runCatching { windowManager?.addView(container, params) }
        CpuReader.read() // prime
        handler.post(tick)
    }

    private fun updateText() {
        val cpu = CpuReader.read()
        val ram = MemoryReader.readRam(this)
        val batt = BatteryReader.read(this)
        val zones = ThermalReader.read()
        val hot = ThermalReader.hottestCpuZone(zones)
        val tempText = if (hot != null && !hot.tempCelsius.isNaN())
            "${"%.0f".format(hot.tempCelsius)}°C" else "${"%.0f".format(batt.temperatureC)}°C"
        val line = buildString {
            append("CPU ").append("%2d".format(cpu.totalPercent.toInt())).append("%  ")
            append("RAM ").append("%2d".format(ram.percent.toInt())).append("%\n")
            append("Akku ").append(batt.percent.toInt()).append("%  ")
            append(tempText)
            if (batt.isCharging && batt.wattsNow > 0) {
                append("  ⚡").append("%.1f".format(batt.wattsNow)).append("W")
            }
        }
        textView.text = line
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        view?.let { v -> runCatching { windowManager?.removeView(v) } }
        view = null
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "System-Monitor-Overlay",
                    NotificationManager.IMPORTANCE_LOW
                )
                ch.description = "Hält das Live-Overlay aktiv"
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("SysMonitor-HUD aktiv")
            .setContentText("Tippen zum Öffnen, im HUD auf X für Stopp")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "HUD beenden", stopIntent).build())
        return builder.build()
    }

    private fun foregroundType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
    }

}
