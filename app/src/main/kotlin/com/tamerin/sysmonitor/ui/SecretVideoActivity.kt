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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tamerin.sysmonitor.R

/**
 * Fullscreen-immersive easter egg video player.
 * Triggered by 7 taps on the version row in Settings → Über.
 * Tap anywhere on the video to exit.
 */
class SecretVideoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Fullscreen immersive — no status bar, no nav bar.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Keep screen on while video plays
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { finish() },
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val uri = Uri.parse(
                                "android.resource://${ctx.packageName}/${R.raw.secret_video}"
                            )
                            setVideoURI(uri)
                            setOnPreparedListener { mp: MediaPlayer ->
                                mp.isLooping = true
                                // Match playback area to video aspect via VideoView's
                                // built-in scaling (letterbox/pillarbox, no stretching).
                                start()
                            }
                            setOnErrorListener { _, _, _ ->
                                finish()
                                true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        finish()
    }
}
