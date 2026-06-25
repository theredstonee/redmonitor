package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.ShizukuCard
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DpiAnimScreen() {
    val context = LocalContext.current
    val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
    val scope = rememberCoroutineScope()
    var currentDpi by remember { mutableStateOf("…") }
    var currentAnim by remember { mutableStateOf("…") }
    var customDpi by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            withContext(Dispatchers.IO) {
                currentDpi = ShizukuHelper.runShell(context, "wm density")
                    .stdout.lines().firstOrNull { it.contains("density") }
                    ?.substringAfterLast(':')?.trim() ?: "?"
                val anim = ShizukuHelper.runShell(context,
                    "settings get global window_animation_scale").stdout.trim()
                val tran = ShizukuHelper.runShell(context,
                    "settings get global transition_animation_scale").stdout.trim()
                val dur = ShizukuHelper.runShell(context,
                    "settings get global animator_duration_scale").stdout.trim()
                currentAnim = "win=$anim · tran=$tran · dur=$dur"
            }
        }
    }

    fun setDpi(d: Int) {
        scope.launch {
            withContext(Dispatchers.IO) {
                ShizukuHelper.runShell(context,
                    if (d <= 0) "wm density reset" else "wm density $d")
            }
            refresh()
        }
    }

    fun setAnim(scale: Float) {
        scope.launch {
            withContext(Dispatchers.IO) {
                listOf("window_animation_scale", "transition_animation_scale",
                    "animator_duration_scale").forEach {
                    ShizukuHelper.runShell(context, "settings put global $it $scale")
                }
            }
            refresh()
        }
    }

    LaunchedEffect(shizukuReady) { if (shizukuReady) refresh() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!shizukuReady) {
            ShizukuCard(
                title = "Shizuku für DPI/Animation",
                description = "`wm density` und `settings put global animator_*` setzen DPI " +
                    "und Animationsdauer system-weit. Nur shell darf das."
            )
            return@Column
        }

        StatCard("Aktuell") {
            KeyValueRow("DPI", currentDpi)
            KeyValueRow("Anim-Scales", currentAnim)
        }

        StatCard("DPI Quick-Set") {
            Text(
                "Niedriger DPI = mehr passt auf den Schirm. Stock-Wert ist meist 420 oder 480.",
                color = OnSurfaceMuted, fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(320, 360, 400, 420, 480).forEach { dpi ->
                    OutlinedButton(onClick = { setDpi(dpi) },
                        modifier = Modifier.weight(1f)) {
                        Text("$dpi", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Button(onClick = { setDpi(0) }, modifier = Modifier.fillMaxWidth()) {
                Text("Auf Default zurücksetzen")
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(value = customDpi, onValueChange = { customDpi = it },
                    label = { Text("Custom") }, singleLine = true,
                    modifier = Modifier.weight(1f))
                Button(onClick = { customDpi.toIntOrNull()?.let { setDpi(it) } }) {
                    Text("Setzen")
                }
            }
        }

        StatCard("Animation Speed") {
            Text(
                "Wert 0 = sofort (kein Animation), 0.5 = doppelt so schnell, 1.0 = Stock.",
                color = OnSurfaceMuted, fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0f, 0.5f, 1f).forEach { s ->
                    OutlinedButton(onClick = { setAnim(s) }, modifier = Modifier.weight(1f)) {
                        Text("$s", fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
