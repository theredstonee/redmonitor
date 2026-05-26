package com.tamerin.sysmonitor.ui.screens

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Range
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlin.math.roundToInt
import kotlin.math.sqrt

private data class CamSpec(
    val id: String,
    val isPhysical: Boolean,
    val parentId: String?,
    val facing: String,
    val sensorMp: Float,
    val sensorWidthMm: Float,
    val sensorHeightMm: Float,
    val sensorDiagonal: Float,
    val pixelArrayW: Int,
    val pixelArrayH: Int,
    val maxJpegW: Int,
    val maxJpegH: Int,
    val maxVideo: String?,
    val maxRaw: String?,
    val outputFormats: List<String>,
    val focalLengthsMm: List<Float>,
    val equivFocal35mm: List<Int>,
    val apertures: List<Float>,
    val minFocusDistance: Float,
    val hyperfocalDistance: Float,
    val isoRange: Range<Int>?,
    val shutterRangeNs: Range<Long>?,
    val maxFpsRanges: List<Range<Int>>,
    val opticalStabilization: Boolean,
    val videoStabilization: Boolean,
    val maxZoom: Float,
    val maxDigitalZoom: Float,
    val hasFlash: Boolean,
    val supportsRaw: Boolean,
    val supports4kVideo: Boolean,
    val hwLevel: String,
    val capabilities: List<String>,
    val orientation: Int,
    val physicalIds: List<String>
)

@Composable
fun CameraInfoScreen() {
    val context = LocalContext.current
    val cams = remember { readAllCameras(context) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StatCard("Übersicht") {
                val logical = cams.count { !it.isPhysical }
                val physical = cams.count { it.isPhysical }
                KeyValueRow("Logische Kameras", logical.toString())
                if (physical > 0) KeyValueRow("Physische Sub-Kameras", physical.toString())
                Text(
                    "Tippe auf eine Karte, um Details zu sehen. Auf Multi-Kamera-Geräten zeigt Android meistens nur die „logische" Hauptkamera. Die physischen Sub-Sensoren (Ultraweitwinkel/Tele/Tiefe) findest du unter ihren Parent-IDs.",
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }
        }
        items(cams, key = { (if (it.isPhysical) "p" else "l") + it.id }) { c ->
            CamCard(c)
        }
    }
}

@Composable
private fun CamCard(c: CamSpec) {
    val titlePrefix = if (c.isPhysical) "📷 Physische Cam #${c.id}" else "📸 Cam ID ${c.id}"
    StatCard("$titlePrefix · ${c.facing}") {
        if (c.isPhysical && c.parentId != null) {
            Text(
                "Sub-Sensor von logischer Cam #${c.parentId}",
                color = AccentSoft, fontSize = 11.sp, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
        }

        // --- Sensor ---
        SectionHeader("Sensor")
        KeyValueRow("Megapixel (real)", "${"%.1f".format(c.sensorMp)} MP")
        KeyValueRow("Pixel-Array", "${c.pixelArrayW} × ${c.pixelArrayH}")
        if (c.sensorWidthMm > 0 && c.sensorHeightMm > 0) {
            KeyValueRow(
                "Sensor-Fläche",
                "${"%.2f".format(c.sensorWidthMm)} × ${"%.2f".format(c.sensorHeightMm)} mm"
            )
            KeyValueRow("Sensor-Diagonale", "${"%.2f".format(c.sensorDiagonal)} mm")
            if (c.pixelArrayW > 0) {
                val pxSize = (c.sensorWidthMm * 1000) / c.pixelArrayW
                KeyValueRow("Pixel-Größe", "${"%.2f".format(pxSize)} µm")
            }
        }
        KeyValueRow("HW-Level", c.hwLevel)
        KeyValueRow("Orientierung", "${c.orientation}°")

        // --- Auflösungen ---
        Spacer(Modifier.height(8.dp))
        SectionHeader("Auflösungen")
        if (c.maxJpegW > 0) KeyValueRow("Max Foto (JPEG)", "${c.maxJpegW} × ${c.maxJpegH}")
        c.maxVideo?.let { KeyValueRow("Max Video", it) }
        c.maxRaw?.let { KeyValueRow("Max RAW", it) }
        KeyValueRow("4K-Video", if (c.supports4kVideo) "Ja" else "Nein")

        // --- Optik ---
        Spacer(Modifier.height(8.dp))
        SectionHeader("Optik")
        if (c.focalLengthsMm.isNotEmpty()) {
            KeyValueRow("Brennweiten (real)", c.focalLengthsMm.joinToString(", ") { "${"%.2f".format(it)} mm" })
        }
        if (c.equivFocal35mm.isNotEmpty()) {
            KeyValueRow("Brennweiten (KB-äquiv.)", c.equivFocal35mm.joinToString(", ") { "${it} mm" })
        }
        if (c.apertures.isNotEmpty()) {
            KeyValueRow("Blenden", c.apertures.joinToString(", ") { "f/${"%.1f".format(it)}" })
        }
        if (c.minFocusDistance > 0) {
            KeyValueRow("Min Fokus", "${"%.1f".format(100f / c.minFocusDistance)} cm (= 1/${"%.2f".format(c.minFocusDistance)} m⁻¹)")
        }
        if (c.hyperfocalDistance > 0) {
            KeyValueRow("Hyperfokal", "${"%.2f".format(1f / c.hyperfocalDistance)} m")
        }
        if (c.maxZoom > 1f) KeyValueRow("Max Zoom (optisch+digital)", "${"%.1f".format(c.maxZoom)}×")
        if (c.maxDigitalZoom > 1f) KeyValueRow("Max digitaler Zoom", "${"%.1f".format(c.maxDigitalZoom)}×")

        // --- Belichtung ---
        Spacer(Modifier.height(8.dp))
        SectionHeader("Belichtung")
        c.isoRange?.let { KeyValueRow("ISO-Range", "${it.lower} – ${it.upper}") }
        c.shutterRangeNs?.let { range ->
            val lo = formatShutter(range.lower)
            val hi = formatShutter(range.upper)
            KeyValueRow("Belichtungszeit", "$lo – $hi")
        }
        if (c.maxFpsRanges.isNotEmpty()) {
            val str = c.maxFpsRanges.joinToString(", ") { "${it.lower}-${it.upper}" }
            KeyValueRow("Max FPS-Ranges", str)
        }

        // --- Features ---
        Spacer(Modifier.height(8.dp))
        SectionHeader("Features")
        KeyValueRow("Blitz", if (c.hasFlash) "Ja" else "Nein")
        KeyValueRow("RAW", if (c.supportsRaw) "Ja (DNG-Capture)" else "Nein")
        KeyValueRow("OIS", if (c.opticalStabilization) "Ja (optisch)" else "Nein")
        KeyValueRow("EIS / Video-Stab.", if (c.videoStabilization) "Ja" else "Nein")
        if (c.capabilities.isNotEmpty()) {
            KeyValueRow("Capabilities", c.capabilities.size.toString())
            c.capabilities.forEach { cap ->
                Text("· $cap", color = OnSurfaceMuted, fontSize = 11.sp)
            }
        }

        // --- Formate ---
        if (c.outputFormats.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SectionHeader("Ausgabe-Formate")
            Text(c.outputFormats.joinToString(", "), color = OnSurfaceMuted, fontSize = 11.sp)
        }

        // --- Physical IDs ---
        if (c.physicalIds.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SectionHeader("Enthält physische Sub-Kameras")
            Text(c.physicalIds.joinToString(", "),
                color = AccentSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        color = Accent,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

private fun formatShutter(ns: Long): String {
    val seconds = ns / 1_000_000_000.0
    return when {
        seconds >= 1.0 -> "${"%.1f".format(seconds)} s"
        seconds >= 0.001 -> "1/${(1.0 / seconds).roundToInt()} s"
        else -> "${ns / 1000} µs"
    }
}

private fun readAllCameras(context: Context): List<CamSpec> {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val out = mutableListOf<CamSpec>()
    runCatching {
        for (id in cm.cameraIdList) {
            val main = readOne(cm, id, isPhysical = false, parentId = null)
            if (main != null) {
                out += main
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    for (physId in main.physicalIds) {
                        val sub = readOne(cm, physId, isPhysical = true, parentId = id)
                        if (sub != null) out += sub
                    }
                }
            }
        }
    }
    return out
}

private fun readOne(cm: CameraManager, id: String, isPhysical: Boolean, parentId: String?): CamSpec? {
    return runCatching {
        val ch = cm.getCameraCharacteristics(id)
        val facing = when (ch.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_BACK -> "Rückseite"
            CameraCharacteristics.LENS_FACING_FRONT -> "Vorderseite"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "Extern"
            else -> "Unbekannt"
        }
        val pixelArr = ch.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val pixelW = pixelArr?.width ?: 0
        val pixelH = pixelArr?.height ?: 0
        val sensorMp = if (pixelW > 0) (pixelW.toLong() * pixelH) / 1_000_000f else 0f

        val sensorPhys = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val sensorWMm = sensorPhys?.width ?: 0f
        val sensorHMm = sensorPhys?.height ?: 0f
        val diag = sqrt((sensorWMm * sensorWMm + sensorHMm * sensorHMm).toDouble()).toFloat()

        val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG)?.toList().orEmpty()
        val maxJpeg = jpegSizes.maxByOrNull { it.width.toLong() * it.height }
        val maxJpegW = maxJpeg?.width ?: 0
        val maxJpegH = maxJpeg?.height ?: 0

        val videoSizes = runCatching {
            map?.getOutputSizes(android.media.MediaRecorder::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())
        val maxVideo = videoSizes.maxByOrNull { it.width.toLong() * it.height }
            ?.let { "${it.width} × ${it.height}" }
        val supports4k = videoSizes.any { it.width >= 3840 || it.height >= 2160 }

        val rawSizes = runCatching {
            map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.toList().orEmpty()
        }.getOrDefault(emptyList())
        val maxRaw = rawSizes.maxByOrNull { it.width.toLong() * it.height }
            ?.let { "${it.width} × ${it.height}" }

        val outputFormats = map?.outputFormats?.mapNotNull { formatName(it) }
            ?.distinct()?.sorted().orEmpty()

        val focals = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty()
        // Compute 35mm-equivalent focal length: f_35 = f_real × (43.27 / sensor_diagonal)
        val equiv35 = if (diag > 0) {
            focals.map { ((it * 43.27f) / diag).roundToInt() }
        } else emptyList()
        val apertures = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList().orEmpty()
        val minFocus = ch.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val hyperfocal = ch.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE) ?: 0f
        val hasFlash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        val caps = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        val supportsRaw = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        val hwLevel = when (ch.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "External"
            else -> "?"
        }
        val maxDigitalZoom = ch.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
        val maxZoom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ch.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.upper ?: maxDigitalZoom
        } else maxDigitalZoom

        val isoRange = ch.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val shutterRange = ch.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val fpsRanges = ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.toList().orEmpty()

        val opticalStab = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            ?.any { it == CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON } == true
        val videoStab = ch.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            ?.any { it == CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_ON } == true

        val orientation = ch.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

        val capLabels = caps.mapNotNull { capabilityLabel(it) }

        val physicalIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ch.physicalCameraIds.toList()
        } else emptyList()

        CamSpec(
            id, isPhysical, parentId, facing,
            sensorMp, sensorWMm, sensorHMm, diag,
            pixelW, pixelH, maxJpegW, maxJpegH,
            maxVideo, maxRaw, outputFormats,
            focals, equiv35, apertures, minFocus, hyperfocal,
            isoRange, shutterRange, fpsRanges,
            opticalStab, videoStab, maxZoom, maxDigitalZoom,
            hasFlash, supportsRaw, supports4k, hwLevel, capLabels, orientation, physicalIds
        )
    }.getOrNull()
}

private fun formatName(format: Int): String? = when (format) {
    ImageFormat.JPEG -> "JPEG"
    ImageFormat.YUV_420_888 -> "YUV_420"
    ImageFormat.YUV_422_888 -> "YUV_422"
    ImageFormat.YUV_444_888 -> "YUV_444"
    ImageFormat.RAW_SENSOR -> "RAW"
    ImageFormat.RAW10 -> "RAW10"
    ImageFormat.RAW12 -> "RAW12"
    ImageFormat.RAW_PRIVATE -> "RAW_PRIV"
    ImageFormat.HEIC -> "HEIC"
    ImageFormat.DEPTH16 -> "DEPTH16"
    ImageFormat.DEPTH_POINT_CLOUD -> "DEPTH_CLOUD"
    ImageFormat.PRIVATE -> "PRIVATE"
    ImageFormat.NV21 -> "NV21"
    else -> null
}

private fun capabilityLabel(cap: Int): String? = when (cap) {
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE -> "Backward-Compatible"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> "Manual Sensor"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> "Manual Post-Processing"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW -> "RAW-Capture"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING -> "Private Reprocessing"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS -> "Read Sensor Settings"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE -> "Burst-Capture"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING -> "YUV Reprocessing"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT -> "Depth-Output"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO -> "High-Speed Video"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING -> "Motion Tracking"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> "Logical Multi-Camera"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME -> "Monochrome"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA -> "Secure Image"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA -> "System-Camera"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING -> "Offline-Processing"
    else -> null
}
