package com.tamerin.sysmonitor.ui.screens

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Size
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

private data class CamInfo(
    val id: String,
    val facing: String,
    val sensorMp: Float,
    val maxResolution: String,
    val focalLengthsMm: String,
    val apertures: String,
    val hasFlash: Boolean,
    val supportsRaw: Boolean,
    val supportsHwLevel: String,
    val maxZoom: Float
)

@Composable
fun CameraInfoScreen() {
    val context = LocalContext.current
    val cams = remember { readCameras(context) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StatCard("Kameras gesamt") {
                KeyValueRow("Anzahl", cams.size.toString())
                Text(
                    "Detaillierte Specs aller Kameras via Camera2-API.",
                    color = OnSurfaceMuted,
                    fontSize = 11.sp
                )
            }
        }
        items(cams, key = { it.id }) { c ->
            StatCard("${c.facing} (ID ${c.id})") {
                KeyValueRow("Auflösung max", c.maxResolution)
                KeyValueRow("Sensor (Megapixel)", "${"%.1f".format(c.sensorMp)} MP")
                KeyValueRow("Brennweiten", c.focalLengthsMm)
                KeyValueRow("Blende(n)", c.apertures)
                KeyValueRow("Max Zoom", "${"%.1f".format(c.maxZoom)}×")
                KeyValueRow("Blitz", if (c.hasFlash) "Ja" else "Nein")
                KeyValueRow("RAW", if (c.supportsRaw) "Ja" else "Nein")
                KeyValueRow("HW-Level", c.supportsHwLevel)
            }
        }
    }
}

private fun readCameras(context: Context): List<CamInfo> {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    return runCatching {
        cm.cameraIdList.map { id ->
            val ch = cm.getCameraCharacteristics(id)
            val facing = when (ch.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "Rückseite"
                CameraCharacteristics.LENS_FACING_FRONT -> "Vorderseite"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "Extern"
                else -> "Unbekannt"
            }
            val sensorSize = ch.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            val sensorMp = if (sensorSize != null)
                (sensorSize.width.toLong() * sensorSize.height) / 1_000_000f
            else 0f
            val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val maxRes = map?.getOutputSizes(android.graphics.ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height }
                ?.let { "${it.width} × ${it.height}" } ?: "?"
            val focals = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.joinToString(", ") { "${"%.1f".format(it)} mm" } ?: "?"
            val apertures = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                ?.joinToString(", ") { "f/${"%.1f".format(it)}" } ?: "?"
            val hasFlash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val caps = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            val raw = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
            val hwLevel = when (ch.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "External"
                else -> "?"
            }
            val maxZoom = ch.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
            CamInfo(id, facing, sensorMp, maxRes, focals, apertures, hasFlash, raw, hwLevel, maxZoom)
        }
    }.getOrDefault(emptyList())
}
