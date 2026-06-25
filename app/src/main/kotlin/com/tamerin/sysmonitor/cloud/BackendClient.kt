package com.tamerin.sysmonitor.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64

object BackendClient {

    private const val UA_PREFIX = "RedMonitor/"
    private const val TIMEOUT_MS = 15_000

    suspend fun heartbeat(context: Context): Result = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("deviceId", DeviceIdProvider.deviceId(context))
            put("appVersion", appVersion(context))
            put("appVersionCode", appVersionCode(context))
            put("androidSdk", Build.VERSION.SDK_INT)
            put("androidRelease", Build.VERSION.RELEASE.orEmpty())
            put("brand", Build.BRAND.orEmpty())
            put("model", Build.MODEL.orEmpty())
            put("soc", (Build.HARDWARE.orEmpty() + " " + Build.BOARD.orEmpty()).trim())
            put("networkType", networkType(context))
            put("batteryPct", batteryPct(context))
            put("uptimeSeconds", SystemClock.elapsedRealtime() / 1000L)
        }
        postJson("/api/heartbeat", body.toString().toByteArray(Charsets.UTF_8), context)
    }

    suspend fun uploadBackup(context: Context, encryptedBlob: ByteArray): Result = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("deviceId", DeviceIdProvider.deviceId(context))
            put("blobVersion", CloudConfig.BACKUP_BLOB_VERSION)
            put("payloadB64", Base64.encodeToString(encryptedBlob, Base64.NO_WRAP))
        }
        postJson("/api/backup", body.toString().toByteArray(Charsets.UTF_8), context)
    }

    suspend fun fetchBackup(context: Context): BackupResponse? = withContext(Dispatchers.IO) {
        val url = URL(CloudConfig.BACKEND_BASE + "/api/backup/" + DeviceIdProvider.deviceId(context))
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", UA_PREFIX + appVersion(context))
        }
        try {
            val code = c.responseCode
            if (code !in 200..299) return@withContext null
            val text = c.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(text)
            if (!o.optBoolean("ok", false)) return@withContext null
            val payload = Base64.decode(o.optString("payloadB64"), Base64.NO_WRAP)
            BackupResponse(
                blobVersion = o.optInt("blobVersion", 1),
                size = o.optInt("size", payload.size),
                createdAt = o.optString("createdAt"),
                encryptedBlob = payload
            )
        } finally {
            c.disconnect()
        }
    }

    private fun postJson(path: String, body: ByteArray, context: Context): Result {
        val url = URL(CloudConfig.BACKEND_BASE + path)
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("User-Agent", UA_PREFIX + appVersion(context))
            setFixedLengthStreamingMode(body.size)
        }
        return try {
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            val msg = if (code in 200..299) {
                c.inputStream.bufferedReader().use { it.readText() }
            } else {
                runCatching { c.errorStream.bufferedReader().use { it.readText() } }.getOrDefault("")
            }
            Result(code in 200..299, code, msg)
        } catch (t: Throwable) {
            Result(false, -1, t.message.orEmpty())
        } finally {
            c.disconnect()
        }
    }

    private fun networkType(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "none"
        val nw = cm.activeNetwork ?: return "none"
        val caps = cm.getNetworkCapabilities(nw) ?: return "none"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            else -> "none"
        }
    }

    private fun batteryPct(context: Context): Int? {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
        return runCatching { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }.getOrNull()
    }

    private fun appVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    private fun appVersionCode(context: Context): Long = runCatching {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode
        else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }.getOrDefault(0L)

    data class Result(val ok: Boolean, val httpCode: Int, val body: String)

    data class BackupResponse(
        val blobVersion: Int,
        val size: Int,
        val createdAt: String,
        val encryptedBlob: ByteArray
    )
}
