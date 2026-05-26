package com.tamerin.sysmonitor.ui.screens

import android.content.Context
import android.hardware.camera2.CameraManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

@Composable
fun FlashlightScreen() {
    val context = LocalContext.current
    val cm = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val torchId = remember {
        runCatching {
            cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }
    var on by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val haptic = com.tamerin.sysmonitor.settings.rememberHaptic()

    DisposableEffect(Unit) {
        onDispose {
            torchId?.let { runCatching { cm.setTorchMode(it, false) } }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("Taschenlampe") {
            KeyValueRow("Kamera mit Blitz", torchId ?: "Keine gefunden")
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = androidx.compose.ui.graphics.Color.Red, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = torchId != null,
                onClick = {
                    haptic(com.tamerin.sysmonitor.settings.HapticType.TOGGLE)
                    val id = torchId ?: return@Button
                    try {
                        cm.setTorchMode(id, !on)
                        on = !on
                        error = null
                    } catch (e: Exception) {
                        error = "Fehler: ${e.message}"
                    }
                }
            ) {
                Text(if (on) "Aus" else "Ein")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Hinweis: Manche Geräte deaktivieren den Blitz, wenn der Akku zu niedrig oder das Gerät zu heiß ist.",
                color = OnSurfaceMuted,
                fontSize = 11.sp
            )
        }
    }
}
