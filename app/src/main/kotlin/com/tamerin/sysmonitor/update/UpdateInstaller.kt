package com.tamerin.sysmonitor.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.tamerin.sysmonitor.data.ShizukuHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateInstaller {

    sealed class DownloadResult {
        data class Success(val file: File) : DownloadResult()
        data class Error(val message: String) : DownloadResult()
    }

    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (downloadedMb: Double, totalMb: Double) -> Unit = { _, _ -> }
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val target = File(context.externalCacheDir ?: context.cacheDir, "update.apk")
            target.delete()
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", "RedMonitor")
            conn.connect()
            val total = conn.contentLengthLong.coerceAtLeast(0L)
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        written += n
                        if (written and 0xFFFFF == 0L) {
                            onProgress(written / 1024.0 / 1024.0, total / 1024.0 / 1024.0)
                        }
                    }
                    onProgress(written / 1024.0 / 1024.0, total / 1024.0 / 1024.0)
                }
            }
            DownloadResult.Success(target)
        } catch (e: Exception) {
            DownloadResult.Error(e.message ?: "Download fehlgeschlagen")
        }
    }

    /**
     * Installs an APK file via Shizuku `pm install`. Requires Shizuku Ready.
     * Returns true if installation succeeded.
     */
    suspend fun installViaShizuku(context: Context, apk: File): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (ShizukuHelper.state(context) != ShizukuHelper.State.Ready) {
            return@withContext false to "Shizuku nicht bereit"
        }
        val res = ShizukuHelper.runCommand(
            context, "pm", "install", "-r", "-i", context.packageName, apk.absolutePath
        )
        val success = res.ok && res.stdout.contains("Success", ignoreCase = true)
        success to (if (success) "Installation erfolgreich" else res.stderr.ifBlank { res.stdout.ifBlank { "Exit ${res.exitCode}" } })
    }

    /**
     * Opens the system package installer to install the APK.
     * User has to confirm and may need to enable "Install unknown apps" once.
     */
    fun installViaSystem(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun openGithubRelease(context: Context, htmlUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
