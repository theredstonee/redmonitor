package com.tamerin.sysmonitor.benchmark

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.graphics.Paint
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import java.math.BigInteger
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.URL
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class DrainEngine {
    private val stress = StressEngine()
    private var torchId: String? = null
    private var locListener: LocationListener? = null
    private var vibrator: Vibrator? = null
    private var audioTrack: AudioTrack? = null
    private var sensorListener: SensorEventListener? = null
    private var bleCallback: ScanCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var cameraThread: HandlerThread? = null
    private var bluetoothDiscoveryActive: Boolean = false
    private var sqliteDb: SQLiteDatabase? = null
    private var mediaCodec: MediaCodec? = null
    private val jobs = mutableListOf<Job>()
    var running: Boolean = false
        private set

    val activeChannels = mutableListOf<String>()

    fun start(context: Context, activity: Activity?, scope: CoroutineScope) {
        if (running) return
        running = true
        activeChannels.clear()

        startCpu(scope)
        startBrightness(activity)
        startTorch(context)
        startVibration(context, scope)
        startAudio(scope)
        startMultiAudio(scope)
        startGps(context)
        startSensors(context)
        startWifiScan(context, scope)
        startBleScan(context)
        startMemoryChurn(scope)
        startNetworkLoop(context, scope)
        startStorageLoop(context, scope)
        startCryptoLoop(scope)
        startMultiCryptoLoop(scope)
        startCameraPreview(context)
        startBluetoothDiscovery(context)
        startMediaCodecLoop(scope)
        startTcpFlood(scope)
        startBigIntegerLoop(scope)
        startSqliteThrash(context, scope)
        startBitmapChurn(scope)
        startWakeLock(context)
        startGpuLoop(scope)
    }

    // ---------- channels ----------

    private fun startCpu(scope: CoroutineScope) {
        stress.start(scope)
        activeChannels += "CPU 100 % alle Kerne"
    }

    private fun startBrightness(activity: Activity?) {
        activity?.runOnUiThread {
            val lp = activity.window.attributes
            lp.screenBrightness = 1.0f
            activity.window.attributes = lp
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (activity != null) activeChannels += "Bildschirm max-Helligkeit + always-on"
    }

    private fun startTorch(context: Context) {
        runCatching {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            torchId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            torchId?.let {
                cm.setTorchMode(it, true)
                activeChannels += "Taschenlampe AN"
            }
        }
    }

    private fun startVibration(context: Context, scope: CoroutineScope) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (vibrator?.hasVibrator() == true) {
            jobs += scope.launch(Dispatchers.Default) {
                while (isActive) {
                    runCatching {
                        vibrator?.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                    delay(400)
                }
            }
            activeChannels += "Dauer-Vibration"
        }
    }

    private fun startAudio(scope: CoroutineScope) {
        jobs += scope.launch(Dispatchers.Default) {
            val sampleRate = 44100
            val bufSize = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            t.setVolume(1.0f)
            audioTrack = t
            try {
                t.play()
                val chunk = ShortArray(2048)
                var phase = 0.0
                val freq = 1000.0
                val phaseStep = 2.0 * PI * freq / sampleRate
                while (isActive) {
                    var i = 0
                    while (i < chunk.size) {
                        val v = (sin(phase) * 28_000).toInt().toShort()
                        chunk[i] = v
                        chunk[i + 1] = v
                        phase += phaseStep
                        if (phase > 2 * PI) phase -= 2 * PI
                        i += 2
                    }
                    t.write(chunk, 0, chunk.size)
                }
            } finally {
                runCatching { t.stop() }
                runCatching { t.release() }
            }
        }
        activeChannels += "Lauter 1-kHz-Sinuston"
    }

    private fun startGps(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val listener = LocationListener { _: Location -> }
                locListener = listener
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    @Suppress("MissingPermission")
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100L, 0f, listener)
                    activeChannels += "GPS-Dauer-Tracking (100 ms)"
                }
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    @Suppress("MissingPermission")
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 500L, 0f, listener)
                }
            }
        }
    }

    private fun startSensors(context: Context) {
        runCatching {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val all = sm.getSensorList(Sensor.TYPE_ALL)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {}
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorListener = listener
            all.forEach { s -> sm.registerListener(listener, s, SensorManager.SENSOR_DELAY_FASTEST) }
            activeChannels += "Alle ${all.size} Sensoren @ Max-Rate"
        }
    }

    private fun startWifiScan(context: Context, scope: CoroutineScope) {
        runCatching {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            jobs += scope.launch(Dispatchers.Default) {
                while (isActive) {
                    runCatching {
                        @Suppress("DEPRECATION")
                        wm.startScan()
                    }
                    delay(2_000)
                }
            }
            activeChannels += "WLAN-Dauer-Scan (alle 2 s)"
        }
    }

    private fun startBleScan(context: Context) {
        val needsConnect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val scanPerm = if (needsConnect)
            Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.BLUETOOTH_ADMIN
        if (ContextCompat.checkSelfPermission(context, scanPerm) != PackageManager.PERMISSION_GRANTED) return
        runCatching {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter: BluetoothAdapter? = bm?.adapter
            if (adapter?.isEnabled != true) return
            val scanner = adapter.bluetoothLeScanner ?: return
            val cb = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {}
            }
            bleCallback = cb
            @Suppress("MissingPermission")
            scanner.startScan(cb)
            activeChannels += "Bluetooth-LE-Scan"
        }
    }

    private fun startMemoryChurn(scope: CoroutineScope) {
        jobs += scope.launch(Dispatchers.Default) {
            var sizeMb = 32
            while (isActive) {
                try {
                    val arr = ByteArray(sizeMb * 1024 * 1024)
                    Random.nextBytes(arr)
                    var sum = 0
                    var i = 0
                    while (i < arr.size) {
                        sum += arr[i].toInt()
                        i += 4096
                    }
                    // Use the sum so JIT can't dead-strip the loop
                    if (sum == Int.MAX_VALUE) throw IllegalStateException()
                } catch (_: OutOfMemoryError) {
                    // back off, try smaller blocks
                    sizeMb = (sizeMb / 2).coerceAtLeast(4)
                    delay(500)
                    continue
                }
                delay(50)
            }
        }
        activeChannels += "Memory-Churn (32 MB-Blöcke)"
    }

    private fun startNetworkLoop(context: Context, scope: CoroutineScope) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET)
            != PackageManager.PERMISSION_GRANTED) return
        jobs += scope.launch(Dispatchers.IO) {
            val hosts = listOf("1.1.1.1", "8.8.8.8", "google.com", "cloudflare.com", "apple.com")
            while (isActive) {
                for (h in hosts) {
                    if (!isActive) break
                    runCatching {
                        InetAddress.getByName(h)
                        val url = URL("https://$h/")
                        val conn = url.openConnection()
                        conn.connectTimeout = 3000
                        conn.readTimeout = 3000
                        conn.getInputStream().use { it.read(ByteArray(1024)) }
                    }
                }
                delay(500)
            }
        }
        activeChannels += "Netz-Loop (DNS + HTTPS-Requests)"
    }

    private fun startWakeLock(context: Context) {
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SysMonitor:DrainEngine"
            )
            wl.setReferenceCounted(false)
            wl.acquire(60 * 60 * 1000L) // max 1 h Sicherheitslimit
            wakeLock = wl
            activeChannels += "PARTIAL_WAKE_LOCK (CPU bleibt wach)"
        }
    }

    private fun startGpuLoop(scope: CoroutineScope) {
        // Marker so BatteryScreen knows to render the GPU-burner overlay
        activeChannels += "GPU-Render-Loop (Compose-Canvas Max-FPS)"
    }

    private fun startMultiAudio(scope: CoroutineScope) {
        // Layer 3 weitere Töne, dissonant für maximale DSP-Last
        val freqs = listOf(523.0, 740.0, 880.0)
        freqs.forEach { freq ->
            jobs += scope.launch(Dispatchers.Default) {
                val sampleRate = 44100
                val bufSize = AudioTrack.getMinBufferSize(
                    sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)
                val t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                t.setVolume(0.6f)
                try {
                    t.play()
                    val chunk = ShortArray(1024)
                    var phase = 0.0
                    val step = 2.0 * PI * freq / sampleRate
                    while (isActive) {
                        for (i in chunk.indices) {
                            chunk[i] = (sin(phase) * 16_000).toInt().toShort()
                            phase += step
                            if (phase > 2 * PI) phase -= 2 * PI
                        }
                        t.write(chunk, 0, chunk.size)
                    }
                } finally {
                    runCatching { t.stop() }
                    runCatching { t.release() }
                }
            }
        }
        activeChannels += "3 zusätzliche Audio-Layer (523/740/880 Hz)"
    }

    private fun startStorageLoop(context: Context, scope: CoroutineScope) {
        jobs += scope.launch(Dispatchers.IO) {
            val file = java.io.File(context.cacheDir, "drain_burst.bin")
            val chunk = ByteArray(2 * 1024 * 1024) // 2 MB
            Random.nextBytes(chunk)
            try {
                while (isActive) {
                    runCatching {
                        java.io.FileOutputStream(file).use { fos ->
                            repeat(8) { fos.write(chunk) } // 16 MB
                            fos.fd.sync()
                        }
                        // Random reads
                        java.io.RandomAccessFile(file, "r").use { raf ->
                            val len = raf.length()
                            val buf = ByteArray(4096)
                            repeat(200) {
                                if (!isActive) return@repeat
                                raf.seek((Random.nextLong() and Long.MAX_VALUE) % (len - buf.size))
                                raf.readFully(buf)
                            }
                        }
                    }
                    delay(150)
                }
            } finally {
                runCatching { file.delete() }
            }
        }
        activeChannels += "Storage-I/O-Burst (Schreiben + Random-Reads)"
    }

    private fun startCryptoLoop(scope: CoroutineScope) {
        jobs += scope.launch(Dispatchers.Default) {
            val key = SecretKeySpec(ByteArray(32) { (it * 17).toByte() }, "AES")
            val iv = IvParameterSpec(ByteArray(16) { (it * 31).toByte() })
            val data = ByteArray(1 * 1024 * 1024) // 1 MB pro Pass
            Random.nextBytes(data)
            try {
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                while (isActive) {
                    cipher.init(Cipher.ENCRYPT_MODE, key, iv)
                    val enc = cipher.doFinal(data)
                    cipher.init(Cipher.DECRYPT_MODE, key, iv)
                    val dec = cipher.doFinal(enc)
                    if (dec.size == Int.MIN_VALUE) error("x")
                }
            } catch (_: Exception) {
                // fall through — just stop the loop on weird crypto errors
            }
        }
        activeChannels += "AES-256-Crypto-Loop (1 MB pro Pass)"
    }

    private fun startMultiCryptoLoop(scope: CoroutineScope) {
        // SHA-256 hashing loop
        jobs += scope.launch(Dispatchers.Default) {
            val md = MessageDigest.getInstance("SHA-256")
            val data = ByteArray(512 * 1024)
            Random.nextBytes(data)
            while (isActive) {
                md.reset()
                val hash = md.digest(data)
                if (hash.size == Int.MIN_VALUE) error("x")
            }
        }
        // ChaCha20 (falls verfügbar — sonst AES-GCM Fallback)
        jobs += scope.launch(Dispatchers.Default) {
            val data = ByteArray(512 * 1024)
            Random.nextBytes(data)
            val (cipherName, keyAlg, keyBytes, params) = runCatching {
                val c = Cipher.getInstance("ChaCha20-Poly1305")
                Quad("ChaCha20-Poly1305", "ChaCha20", 32, IvParameterSpec(ByteArray(12).also { SecureRandom().nextBytes(it) }))
            }.getOrElse {
                Quad("AES/GCM/NoPadding", "AES", 32, javax.crypto.spec.GCMParameterSpec(128, ByteArray(12).also { SecureRandom().nextBytes(it) }))
            }
            try {
                val cipher = Cipher.getInstance(cipherName)
                val key = SecretKeySpec(ByteArray(keyBytes) { (it * 13).toByte() }, keyAlg)
                while (isActive) {
                    cipher.init(Cipher.ENCRYPT_MODE, key, params as java.security.spec.AlgorithmParameterSpec)
                    val out = cipher.doFinal(data)
                    if (out.size == Int.MIN_VALUE) error("x")
                }
            } catch (_: Exception) {}
        }
        activeChannels += "SHA-256 + Stream-Cipher Loops"
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private fun startBluetoothDiscovery(context: Context) {
        val needsConnect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val scanPerm = if (needsConnect)
            Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.BLUETOOTH_ADMIN
        if (ContextCompat.checkSelfPermission(context, scanPerm) != PackageManager.PERMISSION_GRANTED) return
        runCatching {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter: BluetoothAdapter? = bm?.adapter
            if (adapter?.isEnabled != true) return
            @Suppress("MissingPermission")
            if (adapter.startDiscovery()) {
                bluetoothDiscoveryActive = true
                activeChannels += "Bluetooth-Classic-Discovery"
            }
        }
    }

    private fun startMediaCodecLoop(scope: CoroutineScope) {
        jobs += scope.launch(Dispatchers.Default) {
            val codec = try {
                MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            } catch (_: Exception) { return@launch }
            mediaCodec = codec
            try {
                val w = 640; val h = 480
                val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h)
                fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                fmt.setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
                fmt.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()

                val bufInfo = MediaCodec.BufferInfo()
                val frameBuf = ByteArray(w * h * 3 / 2)
                var pts = 0L
                while (isActive) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val bb = codec.getInputBuffer(inIdx) ?: continue
                        bb.clear()
                        Random.nextBytes(frameBuf)
                        bb.put(frameBuf)
                        codec.queueInputBuffer(inIdx, 0, frameBuf.size, pts, 0)
                        pts += 33_333L
                    }
                    var outIdx = codec.dequeueOutputBuffer(bufInfo, 0)
                    while (outIdx >= 0) {
                        codec.releaseOutputBuffer(outIdx, false)
                        outIdx = codec.dequeueOutputBuffer(bufInfo, 0)
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching { codec.stop() }
                runCatching { codec.release() }
            }
        }
        activeChannels += "Video-Encoder H.264 (Hardware-Encoder)"
    }

    private fun startTcpFlood(scope: CoroutineScope) {
        val hosts = listOf(
            "1.1.1.1" to 443, "8.8.8.8" to 443, "9.9.9.9" to 443,
            "1.0.0.1" to 443, "8.8.4.4" to 443, "208.67.222.222" to 443
        )
        repeat(6) { workerIdx ->
            jobs += scope.launch(Dispatchers.IO) {
                while (isActive) {
                    val (host, port) = hosts[workerIdx % hosts.size]
                    runCatching {
                        val s = Socket()
                        s.connect(java.net.InetSocketAddress(host, port), 3000)
                        s.getOutputStream().write("GET / HTTP/1.0\r\n\r\n".toByteArray())
                        s.getInputStream().read(ByteArray(2048))
                        s.close()
                    }
                    delay(300)
                }
            }
        }
        activeChannels += "TCP-Socket-Flood (6 parallele Verbindungen)"
    }

    private fun startBigIntegerLoop(scope: CoroutineScope) {
        jobs += scope.launch(Dispatchers.Default) {
            val rnd = SecureRandom()
            while (isActive) {
                // RSA-ähnliches modPow (2048-bit)
                val base = BigInteger(2048, rnd)
                val exp = BigInteger(2048, rnd)
                val mod = BigInteger(2048, rnd).setBit(2047) // ensure non-zero
                val r = base.modPow(exp, mod)
                if (r.signum() == Int.MIN_VALUE) error("x")
            }
        }
        activeChannels += "BigInteger modPow (2048-bit RSA-Math)"
    }

    private fun startSqliteThrash(context: Context, scope: CoroutineScope) {
        jobs += scope.launch(Dispatchers.IO) {
            val dbFile = java.io.File(context.cacheDir, "drain.db")
            dbFile.delete()
            val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            sqliteDb = db
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS junk (id INTEGER PRIMARY KEY, blob BLOB, val TEXT)")
                val blob = ByteArray(4096)
                Random.nextBytes(blob)
                while (isActive) {
                    db.beginTransaction()
                    try {
                        repeat(200) { i ->
                            db.execSQL(
                                "INSERT INTO junk (blob, val) VALUES (?, ?)",
                                arrayOf<Any>(blob, "row-$i-${System.nanoTime()}")
                            )
                        }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                    db.execSQL("DELETE FROM junk")
                    delay(100)
                }
            } catch (_: Exception) {
            } finally {
                runCatching { db.close() }
                runCatching { dbFile.delete() }
            }
        }
        activeChannels += "SQLite-Thrash (200 INSERTs/Loop)"
    }

    private fun startBitmapChurn(scope: CoroutineScope) {
        jobs += scope.launch(Dispatchers.Default) {
            val paint = Paint().apply { isAntiAlias = true }
            while (isActive) {
                try {
                    val bm = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bm)
                    for (i in 0 until 50) {
                        paint.color = (0xFF000000.toInt() or Random.nextInt())
                        canvas.drawCircle(
                            Random.nextFloat() * 1024,
                            Random.nextFloat() * 1024,
                            20f + Random.nextFloat() * 80f,
                            paint
                        )
                    }
                    bm.recycle()
                } catch (_: OutOfMemoryError) {
                    delay(200)
                }
                delay(40)
            }
        }
        activeChannels += "Bitmap-Churn (1024² ARGB-Allokationen)"
    }

    private fun startCameraPreview(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) return
        runCatching {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val camId = cm.cameraIdList.firstOrNull() ?: return
            val thread = HandlerThread("DrainCam").also { it.start() }
            cameraThread = thread
            val handler = Handler(thread.looper)

            val reader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2)
            // Drain frames immediately so capture keeps flowing
            reader.setOnImageAvailableListener({ r ->
                runCatching { r.acquireLatestImage()?.close() }
            }, handler)
            imageReader = reader

            @Suppress("MissingPermission")
            cm.openCamera(camId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    request.addTarget(reader.surface)
                    camera.createCaptureSession(
                        listOf(reader.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                captureSession = session
                                runCatching {
                                    session.setRepeatingRequest(request.build(), null, handler)
                                }
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) {}
                        },
                        handler
                    )
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close() }
                override fun onError(camera: CameraDevice, error: Int) { camera.close() }
            }, handler)
            activeChannels += "Kamera-Preview (ISP läuft)"
        }
    }

    // ---------- stop ----------

    fun stop(context: Context, activity: Activity?) {
        if (!running) return
        running = false

        stress.stop()
        jobs.forEach { it.cancel() }
        jobs.clear()

        activity?.runOnUiThread {
            val lp = activity.window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity.window.attributes = lp
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        runCatching {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            torchId?.let { cm.setTorchMode(it, false) }
        }
        torchId = null

        locListener?.let { l ->
            runCatching {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                lm.removeUpdates(l)
            }
        }
        locListener = null

        sensorListener?.let { l ->
            runCatching {
                val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                sm.unregisterListener(l)
            }
        }
        sensorListener = null

        bleCallback?.let { cb ->
            runCatching {
                val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                @Suppress("MissingPermission")
                bm?.adapter?.bluetoothLeScanner?.stopScan(cb)
            }
        }
        bleCallback = null

        runCatching { vibrator?.cancel() }
        vibrator = null

        runCatching {
            audioTrack?.stop()
            audioTrack?.release()
        }
        audioTrack = null

        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null

        runCatching { captureSession?.close() }
        captureSession = null
        runCatching { cameraDevice?.close() }
        cameraDevice = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { cameraThread?.quitSafely() }
        cameraThread = null

        if (bluetoothDiscoveryActive) {
            runCatching {
                val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                @Suppress("MissingPermission")
                bm?.adapter?.cancelDiscovery()
            }
            bluetoothDiscoveryActive = false
        }

        runCatching { mediaCodec?.stop() }
        runCatching { mediaCodec?.release() }
        mediaCodec = null

        runCatching { sqliteDb?.close() }
        sqliteDb = null

        activeChannels.clear()
    }
}
