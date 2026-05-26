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
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Choreographer
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OverlayService : Service() {

    companion object {
        const val ACTION_START = "com.tamerin.sysmonitor.overlay.START"
        const val ACTION_STOP = "com.tamerin.sysmonitor.overlay.STOP"
        const val ACTION_RELOAD = "com.tamerin.sysmonitor.overlay.RELOAD"
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

        fun reload(context: Context) {
            val i = Intent(context, OverlayService::class.java).setAction(ACTION_RELOAD)
            context.startService(i)
        }
    }

    private var view: View? = null
    private var windowManager: WindowManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var textView: TextView
    private lateinit var background: GradientDrawable
    private lateinit var params: WindowManager.LayoutParams
    private var config: HudConfig = HudConfig.DEFAULT

    // FPS counter
    private var frames = 0
    private var lastFpsSnapshot = 0L
    private var currentFps = 0
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frames++
            val now = SystemClock.elapsedRealtime()
            if (lastFpsSnapshot == 0L) lastFpsSnapshot = now
            if (now - lastFpsSnapshot >= 1000) {
                currentFps = (frames * 1000 / (now - lastFpsSnapshot)).toInt()
                frames = 0
                lastFpsSnapshot = now
            }
            if (view != null) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    // Network speed tracking
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastNetMs = 0L
    private var downKbps = 0
    private var upKbps = 0

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
            ACTION_RELOAD -> reloadConfig()
            else -> startOverlay()
        }
        return START_STICKY
    }

    private fun startOverlay() {
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification(), foregroundType())

        config = HudPrefs.load(this)
        if (view != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val density = resources.displayMetrics.density
        fun Int.px(): Int = (this * density).toInt()

        background = GradientDrawable().apply {
            setColor(AndroidColor.parseColor("#0A0A0A"))
            cornerRadius = 14f * density
            setStroke(1, config.color.hex.toInt())
            alpha = (config.opacity * 255).toInt()
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = this@OverlayService.background
            setPadding(10.px(), 8.px(), 10.px(), 8.px())
        }
        textView = TextView(this).apply {
            setTextColor(AndroidColor.parseColor("#F3F4F6"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * config.size.scale)
            typeface = android.graphics.Typeface.MONOSPACE
            text = "starte…"
            setLineSpacing(2f, 1f)
        }
        container.addView(textView)
        view = container

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = config.x.px()
            y = config.y.px()
        }

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var downTime = 0L
        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    touchX = event.rawX; touchY = event.rawY
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
                        val open = Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(open)
                    } else if (config.edgeSnap) {
                        snapToEdge()
                    }
                    // Persist position
                    HudPrefs.savePosition(this, (params.x / density).toInt(), (params.y / density).toInt())
                    true
                }
                else -> false
            }
        }

        runCatching { windowManager?.addView(container, params) }
        CpuReader.read()
        lastRx = TrafficStats.getTotalRxBytes()
        lastTx = TrafficStats.getTotalTxBytes()
        lastNetMs = SystemClock.elapsedRealtime()
        Choreographer.getInstance().postFrameCallback(frameCallback)
        handler.post(tick)
    }

    private fun reloadConfig() {
        if (view == null) return
        config = HudPrefs.load(this)
        background.alpha = (config.opacity * 255).toInt()
        background.setStroke(1, config.color.hex.toInt())
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * config.size.scale)
        updateText()
    }

    private fun snapToEdge() {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getMetrics(dm)
        val screenW = dm.widthPixels
        val viewW = view?.width ?: 0
        params.x = if (params.x + viewW / 2 < screenW / 2) {
            0
        } else {
            screenW - viewW
        }
        runCatching { view?.let { windowManager?.updateViewLayout(it, params) } }
    }

    private fun updateText() {
        val metrics = config.enabledMetrics
        val needsCpu = HudMetric.CPU_PERCENT in metrics ||
            HudMetric.PER_CORE in metrics ||
            HudMetric.PER_CORE_DETAIL in metrics ||
            HudMetric.CPU_FREQ_AVG in metrics
        val cpu = if (needsCpu) CpuReader.read(this) else null
        val ram = if (HudMetric.RAM_PERCENT in metrics) MemoryReader.readRam(this) else null
        val batt = if (HudMetric.BATTERY in metrics) BatteryReader.read(this) else null
        val cpuTemp = if (HudMetric.CPU_TEMP in metrics) {
            val zones = ThermalReader.read()
            ThermalReader.hottestCpuZone(zones)?.tempCelsius
        } else null

        if (HudMetric.NETWORK in metrics) {
            val now = SystemClock.elapsedRealtime()
            val rx = TrafficStats.getTotalRxBytes().coerceAtLeast(0)
            val tx = TrafficStats.getTotalTxBytes().coerceAtLeast(0)
            val dt = (now - lastNetMs).coerceAtLeast(1).toFloat() / 1000f
            downKbps = ((rx - lastRx) / 1024 / dt).toInt().coerceAtLeast(0)
            upKbps = ((tx - lastTx) / 1024 / dt).toInt().coerceAtLeast(0)
            lastRx = rx; lastTx = tx; lastNetMs = now
        }

        val accentInt = config.color.hex.toInt()
        val sb = SpannableStringBuilder()
        var first = true
        fun appendLine(label: String, value: String) {
            if (!first) sb.append("\n")
            first = false
            val labelStart = sb.length
            sb.append("$label ")
            sb.setSpan(ForegroundColorSpan(accentInt), labelStart, sb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(value)
        }

        if (cpu != null && HudMetric.CPU_PERCENT in metrics) {
            // When /proc/stat is locked (Samsung etc.) and fallback measures only own process,
            // the value is misleading — show "—" instead.
            val display = if (cpu.source == "process" && cpu.totalPercent < 1f) {
                "— (Shizuku?)"
            } else {
                "${"%2d".format(cpu.totalPercent.toInt())}%"
            }
            appendLine("CPU", display)
        }
        if (HudMetric.PER_CORE in metrics) {
            val cores = cpu?.perCorePercent
            if (!cores.isNullOrEmpty()) {
                val bars = cores.joinToString("") { p ->
                    val blocks = "▁▂▃▄▅▆▇█"
                    val idx = (p / 12.5f).toInt().coerceIn(0, 7)
                    blocks[idx].toString()
                }
                appendLine("•", bars)
            } else {
                appendLine("•", "— (gesperrt, Shizuku?)")
            }
        }
        if (HudMetric.PER_CORE_DETAIL in metrics) {
            val cores = cpu?.perCorePercent
            val freqs = cpu?.coreFrequenciesKHz.orEmpty()
            if (!cores.isNullOrEmpty()) {
                cores.forEachIndexed { idx, pct ->
                    val freq = freqs.getOrNull(idx) ?: 0L
                    val freqStr = if (freq > 0) " ${freq / 1000}MHz" else ""
                    appendLine("C$idx", "${"%2d".format(pct.toInt())}%$freqStr")
                }
            } else if (freqs.isNotEmpty()) {
                // No per-core %, but freqs work (sysfs partially open)
                freqs.forEachIndexed { idx, f ->
                    if (f > 0) appendLine("C$idx", "${f / 1000}MHz")
                }
            } else {
                appendLine("C", "— (gesperrt)")
            }
        }
        if (HudMetric.CPU_FREQ_AVG in metrics) {
            val active = cpu?.coreFrequenciesKHz?.filter { it > 0 }.orEmpty()
            if (active.isNotEmpty()) {
                val avgMhz = (active.average() / 1000).toInt()
                appendLine("F", "${avgMhz} MHz")
            } else {
                appendLine("F", "—")
            }
        }
        if (cpuTemp != null && !cpuTemp.isNaN()) {
            appendLine("T", "${"%.0f".format(cpuTemp)}°C")
        }
        if (ram != null) {
            appendLine("RAM", "${"%2d".format(ram.percent.toInt())}%")
        }
        if (batt != null) {
            // Show watts whenever the sensor reports a value, charging OR discharging
            val w = if (batt.wattsNow > 0.3f) {
                val arrow = if (batt.isCharging) "⚡" else "↓"
                "  $arrow${"%.1f".format(batt.wattsNow)}W"
            } else ""
            appendLine("Akku", "${batt.percent.toInt()}%$w")
        }
        if (HudMetric.NETWORK in metrics) {
            val downStr = if (downKbps >= 1024) "${"%.1f".format(downKbps / 1024f)}MB/s"
                          else "${downKbps}KB/s"
            val upStr = if (upKbps >= 1024) "${"%.1f".format(upKbps / 1024f)}MB/s"
                        else "${upKbps}KB/s"
            appendLine("↓", downStr)
            appendLine("↑", upStr)
        }
        if (HudMetric.FPS in metrics) {
            appendLine("FPS", currentFps.toString())
        }
        if (HudMetric.CLOCK in metrics) {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val upMin = SystemClock.elapsedRealtime() / 60_000L
            val upH = upMin / 60; val upM = upMin % 60
            appendLine("⌚", "$time  ${upH}h${upM}m")
        }

        if (sb.isEmpty()) sb.append("(keine Metriken aktiv)")
        textView.text = sb
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        Choreographer.getInstance().removeFrameCallback(frameCallback)
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
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("RedMonitor-HUD aktiv")
            .setContentText("Tippen zum Öffnen")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "HUD beenden", stopIntent).build())
            .build()
    }

    private fun foregroundType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
    }
}
