package com.tamerin.sysmonitor.glyph

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy

/**
 * Der Glyph-Toy: zeigt live CPU / RAM / Akku / Temperatur auf der Matrix-Rueckseite
 * des Nothing Phone (3).
 *
 * Lebenszyklus laut Nothing-GDK: Nothing OS bindet den Service, sobald der User den
 * Toy per Glyph-Button auswaehlt, und loest die Bindung beim Weiterblaettern wieder.
 * Deshalb passiert alles in onBind/onUnbind statt in onCreate/onDestroy.
 *
 * Interaktion:
 *   - Langer Druck (EVENT_CHANGE) schaltet auf die naechste Metrik weiter
 *   - EVENT_AOD kommt im Always-On-Betrieb einmal pro Minute -> ein dunkler Frame
 *
 * Die Messwerte kommen aus sysfs/dumpsys und blockieren, laufen deshalb auf einem
 * eigenen HandlerThread. Gerendert wird mit [GlyphMatrixRenderer] — derselbe reine
 * Renderer, den auch die In-App-Vorschau benutzt.
 */
class RedMonitorGlyphToyService : Service() {

    private companion object {
        /** 2 s statt 1 s: die Matrix ist klein, haeufiger waere nur Akkuverbrauch. */
        const val TICK_MS = 2_000L
    }

    private var gm: GlyphMatrixManager? = null
    private var callback: GlyphMatrixManager.Callback? = null
    private var worker: HandlerThread? = null
    private var workerHandler: Handler? = null

    @Volatile private var metric = GlyphMetric.CPU
    @Volatile private var ticking = false

    private val tick = object : Runnable {
        override fun run() {
            if (!ticking) return
            pushFrame(GlyphPrefs.brightness(this@RedMonitorGlyphToyService))
            workerHandler?.postDelayed(this, TICK_MS)
        }
    }

    private val eventHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) {
                super.handleMessage(msg)
                return
            }
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE -> {
                    metric = metric.next()
                    GlyphPrefs.setMetric(this@RedMonitorGlyphToyService, metric)
                    // Sofort neu zeichnen, nicht bis zum naechsten Tick warten
                    workerHandler?.post {
                        pushFrame(GlyphPrefs.brightness(this@RedMonitorGlyphToyService))
                    }
                }
                GlyphToy.EVENT_AOD -> workerHandler?.post {
                    pushFrame(GlyphPrefs.aodBrightness(this@RedMonitorGlyphToyService))
                }
            }
        }
    }

    private val messenger = Messenger(eventHandler)

    override fun onBind(intent: Intent?): IBinder {
        metric = GlyphPrefs.metric(this)
        startWorker()
        initManager()
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        ticking = false
        workerHandler?.removeCallbacksAndMessages(null)
        runCatching { gm?.turnOff() }
        runCatching { gm?.unInit() }
        gm = null
        callback = null
        stopWorker()
        return false
    }

    private fun initManager() {
        val manager = runCatching {
            GlyphMatrixManager.getInstance(applicationContext)
        }.getOrNull() ?: return

        val cb = object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(name: ComponentName?) {
                // DEVICE_23112 = Phone (3). Ohne register() nimmt die Matrix nichts an.
                runCatching { manager.register(Glyph.DEVICE_23112) }
                ticking = true
                workerHandler?.post(tick)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                ticking = false
            }
        }
        gm = manager
        callback = cb
        runCatching { manager.init(cb) }
    }

    private fun pushFrame(brightness: Int) {
        val manager = gm ?: return
        val sample = GlyphMetricReader.read(this, metric)
        val frame = GlyphMatrixRenderer.render(
            value = sample.value,
            fraction = sample.fraction,
            marker = metric.marker,
            brightness = brightness
        )
        // setMatrixFrame wirft GlyphException, wenn der Dienst gerade weg ist
        runCatching { manager.setMatrixFrame(frame) }
    }

    private fun startWorker() {
        if (worker != null) return
        val t = HandlerThread("RedMonitor-Glyph").apply { start() }
        worker = t
        workerHandler = Handler(t.looper)
    }

    private fun stopWorker() {
        worker?.quitSafely()
        worker = null
        workerHandler = null
    }
}
