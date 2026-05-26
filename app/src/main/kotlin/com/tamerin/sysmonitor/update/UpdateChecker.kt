package com.tamerin.sysmonitor.update

import android.content.Context
import com.tamerin.sysmonitor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val versionName: String,        // "1.2.0" (no v prefix)
    val tagName: String,            // "v1.2.0"
    val name: String,               // "RedMonitor v1.2.0"
    val body: String,               // markdown changelog
    val isPrerelease: Boolean,
    val htmlUrl: String,            // GitHub release page URL
    val apkUrl: String?,            // direct .apk asset URL
    val apkSizeBytes: Long
)

data class UpdateState(
    val current: String,
    val latest: ReleaseInfo?,
    val hasUpdate: Boolean,
    val error: String? = null
)

object UpdateChecker {
    private const val REPO = "theredstonee/redmonitor"

    suspend fun check(context: Context, includePrerelease: Boolean): UpdateState = withContext(Dispatchers.IO) {
        val current = BuildConfig.VERSION_NAME
        try {
            val release = fetchLatestRelease(includePrerelease)
                ?: return@withContext UpdateState(current, null, false, "Keine Releases gefunden")
            val newer = compareVersions(release.versionName, current) > 0
            UpdateState(current, release, newer, null)
        } catch (e: Exception) {
            UpdateState(current, null, false, e.message ?: "Unbekannter Fehler")
        }
    }

    private fun fetchLatestRelease(includePrerelease: Boolean): ReleaseInfo? {
        val url = if (includePrerelease) {
            URL("https://api.github.com/repos/$REPO/releases?per_page=10")
        } else {
            URL("https://api.github.com/repos/$REPO/releases/latest")
        }
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "RedMonitor")
        try {
            if (conn.responseCode != 200) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return if (includePrerelease) {
                val arr = JSONArray(text)
                if (arr.length() == 0) null else parseJson(arr.getJSONObject(0))
            } else {
                parseJson(JSONObject(text))
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseJson(o: JSONObject): ReleaseInfo {
        val tag = o.optString("tag_name", "")
        val version = tag.removePrefix("v").removePrefix("V")
        val assets = o.optJSONArray("assets")
        var apkUrl: String? = null
        var apkSize = 0L
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.optString("browser_download_url")
                    apkSize = a.optLong("size", 0)
                    break
                }
            }
        }
        return ReleaseInfo(
            versionName = version,
            tagName = tag,
            name = o.optString("name").ifBlank { tag },
            body = o.optString("body", ""),
            isPrerelease = o.optBoolean("prerelease", false),
            htmlUrl = o.optString("html_url", "https://github.com/$REPO/releases"),
            apkUrl = apkUrl,
            apkSizeBytes = apkSize
        )
    }

    /**
     * Compare semver-like strings.
     * Returns >0 if a > b, 0 if equal, <0 if a < b.
     */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.trim().split(".", "-").mapNotNull { it.toIntOrNull() }
        val pb = b.trim().split(".", "-").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(pa.size, pb.size)
        for (i in 0 until maxLen) {
            val ai = pa.getOrNull(i) ?: 0
            val bi = pb.getOrNull(i) ?: 0
            if (ai != bi) return ai - bi
        }
        return 0
    }
}
