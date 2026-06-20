package com.tamerin.sysmonitor.data

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class WakeLockEntry(
    val type: String,
    val tag: String,
    val uid: Int,
    val pid: Int,
    val packageName: String?,
    val displayName: String?,
    val isSystem: Boolean,
    val flags: List<String>,
    val workSource: String?
)

@Immutable
data class WakelockResult(
    val ok: Boolean,
    val message: String,
    val entries: List<WakeLockEntry>,
    val rawSection: String
)

/**
 * Parst die "Wake Locks:"-Sektion aus `dumpsys power` (via Shizuku).
 *
 * Format-Varianten je Android-Version:
 *   PARTIAL_WAKE_LOCK            'tag' ACQ_CAUSE_WAKEUP ON_AFTER_RELEASE (uid=10182 pid=22336 ws=WorkSource{10182})
 *   SCREEN_BRIGHT_WAKE_LOCK      'window' (uid=1000 pid=2222)
 */
object WakelockReader {

    private val UID_RE = Regex("""uid=(\d+)""")
    private val PID_RE = Regex("""pid=(\d+)""")
    private val WS_RE = Regex("""ws=([^)\s]+(?:\{[^}]*\})?)""")

    suspend fun read(context: Context): WakelockResult = withContext(Dispatchers.IO) {
        if (ShizukuHelper.state(context) != ShizukuHelper.State.Ready) {
            return@withContext WakelockResult(false, "Shizuku nicht bereit.", emptyList(), "")
        }
        val res = ShizukuHelper.runCommand(context, "dumpsys", "power")
        if (!res.ok || res.stdout.isBlank()) {
            return@withContext WakelockResult(
                false,
                "dumpsys power Exit ${res.exitCode}: ${res.stderr.ifBlank { "leere Ausgabe" }}",
                emptyList(),
                ""
            )
        }
        parse(context.packageManager, res.stdout)
    }

    private fun parse(pm: PackageManager, output: String): WakelockResult {
        val lines = output.lines()
        var sectionStart = -1
        var headerIndent = 0
        for ((i, raw) in lines.withIndex()) {
            val trimmed = raw.trimStart()
            if (trimmed.startsWith("Wake Locks:") || trimmed.startsWith("Wake Locks (")) {
                sectionStart = i
                headerIndent = raw.length - trimmed.length
                break
            }
        }
        if (sectionStart < 0) {
            return WakelockResult(true, "Keine 'Wake Locks:'-Sektion gefunden.", emptyList(), "")
        }
        val collected = mutableListOf<String>()
        for (j in (sectionStart + 1) until lines.size) {
            val raw = lines[j]
            if (raw.isBlank()) break
            val indent = raw.indexOfFirst { !it.isWhitespace() }
            if (indent == -1 || indent <= headerIndent) break
            collected += raw
        }
        if (collected.isEmpty()) {
            return WakelockResult(true, "Keine Wake-Locks aktiv.", emptyList(), lines[sectionStart])
        }

        val entries = collected.mapNotNull { parseEntry(it.trim(), pm) }
        val raw = (listOf(lines[sectionStart]) + collected).joinToString("\n")
        return WakelockResult(
            true,
            "${entries.size} Wake-Locks (${collected.size} Zeilen geparst)",
            entries.sortedWith(compareBy({ it.isSystem }, { it.displayName ?: it.packageName ?: "zz_${it.uid}" })),
            raw
        )
    }

    private fun parseEntry(line: String, pm: PackageManager): WakeLockEntry? {
        val firstQuote = line.indexOf('\'')
        if (firstQuote < 0) return null
        val type = line.substring(0, firstQuote).trim()
        if (!type.endsWith("WAKE_LOCK") && !type.endsWith("LOCK")) return null
        val secondQuote = line.indexOf('\'', firstQuote + 1)
        if (secondQuote < 0) return null
        val tag = line.substring(firstQuote + 1, secondQuote)
        val rest = line.substring(secondQuote + 1)
        val parenStart = rest.indexOf('(')
        val flagsRaw = if (parenStart < 0) rest else rest.substring(0, parenStart)
        val flags = flagsRaw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val parenContent = if (parenStart < 0) "" else rest.substring(parenStart + 1)

        val uid = UID_RE.find(parenContent)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        val pid = PID_RE.find(parenContent)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        val ws = WS_RE.find(parenContent)?.groupValues?.get(1)

        val pkg = if (uid > 0) {
            runCatching { pm.getPackagesForUid(uid)?.firstOrNull() }.getOrNull()
        } else null
        val (display, system) = if (pkg != null) {
            runCatching {
                val ai = pm.getApplicationInfo(pkg, 0)
                val name = ai.loadLabel(pm).toString()
                val sys = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                name to sys
            }.getOrDefault(pkg to true)
        } else {
            val sysName = systemUidLabel(uid)
            (sysName ?: "uid:$uid") to true
        }

        return WakeLockEntry(
            type = type,
            tag = tag,
            uid = uid,
            pid = pid,
            packageName = pkg,
            displayName = display,
            isSystem = system,
            flags = flags,
            workSource = ws
        )
    }

    private fun systemUidLabel(uid: Int): String? = when (uid) {
        0 -> "root"
        1000 -> "system_server"
        1001 -> "radio"
        1013 -> "media"
        1027 -> "nfc"
        else -> null
    }
}
