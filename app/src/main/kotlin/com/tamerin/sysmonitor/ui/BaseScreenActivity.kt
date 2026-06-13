package com.tamerin.sysmonitor.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tamerin.sysmonitor.LocalImmersive
import com.tamerin.sysmonitor.ui.theme.BgDark
import com.tamerin.sysmonitor.ui.theme.SysMonitorTheme

/**
 * Base class for every screen that runs in its own Activity (Akku, Stresstest,
 * Benchmarks, Tests, ...). Each subclass overrides title + content; the base
 * provides a Scaffold with back navigation and full LocalImmersive plumbing so
 * fullscreen tests (Display, Multi-Touch) work the same way they do inside
 * MainActivity.
 *
 * Running heavy tests in their own Activity gives the OS more freedom to
 * pause/kill them without dragging the rest of the app down, and lets the
 * user pin them as separate home-screen shortcuts.
 */
abstract class BaseScreenActivity : ComponentActivity() {

    abstract val screenTitle: String

    @Composable
    abstract fun ScreenContent()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SysMonitorTheme {
                val immersive = remember { mutableStateOf(false) }
                val view = LocalView.current

                DisposableEffect(immersive.value) {
                    val controller = WindowCompat.getInsetsController(window, view)
                    if (immersive.value) {
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                        controller.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else {
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                    onDispose { }
                }

                CompositionLocalProvider(LocalImmersive provides immersive) {
                    Scaffold(
                        topBar = {
                            if (immersive.value) return@Scaffold
                            TopAppBar(
                                title = { Text(screenTitle, fontSize = 16.sp) },
                                navigationIcon = {
                                    IconButton(onClick = { finish() }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Zurück"
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = BgDark,
                                    titleContentColor = Color.White,
                                    navigationIconContentColor = Color.White
                                )
                            )
                        }
                    ) { padding ->
                        Box(modifier = Modifier.padding(padding)) {
                            ScreenContent()
                        }
                    }
                }
            }
        }
    }
}
