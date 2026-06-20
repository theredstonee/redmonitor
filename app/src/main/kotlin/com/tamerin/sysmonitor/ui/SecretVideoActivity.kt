package com.tamerin.sysmonitor.ui

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fullscreen-immersive easter-egg video player.
 *
 * The video is NOT bundled in the APK anymore (it added 47 MB of fixed APK
 * weight). Instead, on first easter-egg trigger we lazily download it into
 * the app's private filesDir and cache it forever — subsequent triggers
 * play instantly.
 *
 * Replace [VIDEO_URL] with your own host if the placeholder isn't reachable.
 */
class SecretVideoActivity : ComponentActivity() {

    companion object {
        private const val VIDEO_URL = "https://cdn.redst.de/raw/73573486753486756834908509437345.mp4"
        private const val LOCAL_FILE = "secret_video.mp4"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent { SecretVideoContent(onExit = { finish() }) }
    }

    override fun onPause() {
        super.onPause()
        finish()
    }
}

@androidx.compose.runtime.Composable
private fun SecretVideoContent(onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cachedFile = remember { File(context.filesDir, "secret_video.mp4") }
    var ready by remember { mutableStateOf(cachedFile.exists() && cachedFile.length() > 0) }
    var downloading by remember { mutableStateOf(false) }
    var progressPct by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (ready) return@LaunchedEffect
        downloading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            downloadVideo(
                urlStr = "https://cdn.redst.de/raw/73573486753486756834908509437345.mp4",
                dest = cachedFile,
                onProgress = { pct -> progressPct = pct }
            )
        }
        downloading = false
        if (result == null) {
            ready = true
        } else {
            error = result
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = ready) { onExit() },
        contentAlignment = Alignment.Center
    ) {
        when {
            error != null -> Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Easter Egg konnte nicht geladen werden",
                    color = Color(0xFFF87171),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    error!!,
                    color = Color(0xFF9CA3AF),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Tap zum Schließen",
                    color = Color(0xFF6B7280),
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onExit() }
                )
            }

            downloading -> Column(
                modifier = Modifier.padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Easter Egg wird geladen…",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progressPct / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = Color(0xFFDC2626)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "$progressPct %",
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            ready -> AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(Uri.fromFile(cachedFile))
                        setOnPreparedListener { mp: MediaPlayer ->
                            mp.isLooping = true
                            start()
                        }
                        setOnErrorListener { _, _, _ -> onExit(); true }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Downloads to a temp file then atomically renames. Returns null on success, error message on failure. */
private fun downloadVideo(
    urlStr: String,
    dest: File,
    onProgress: (Int) -> Unit
): String? {
    val tmp = File(dest.parentFile, dest.name + ".part")
    return try {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        conn.connect()
        if (conn.responseCode !in 200..299) {
            return "HTTP ${conn.responseCode}"
        }
        val total = conn.contentLengthLong.coerceAtLeast(1L)
        conn.inputStream.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(8 * 1024)
                var read: Int
                var done = 0L
                var lastReportPct = -1
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    done += read
                    val pct = ((done * 100) / total).toInt()
                    if (pct != lastReportPct) {
                        lastReportPct = pct
                        onProgress(pct)
                    }
                }
            }
        }
        if (!tmp.renameTo(dest)) {
            tmp.delete()
            return "rename failed"
        }
        null
    } catch (e: Exception) {
        tmp.delete()
        e.message ?: "unbekannter Fehler"
    }
}
