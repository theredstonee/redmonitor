package com.tamerin.sysmonitor.data

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class OpAccess(
    val op: String,                 // z.B. "CAMERA", "RECORD_AUDIO", "FINE_LOCATION"
    val mode: String,               // "allow" / "ignore" / "deny" / "foreground"
    val lastAccessAgoMs: Long,      // 0 = nie / unbekannt
    val lastDurationMs: Long,
    val rejectCount: Int            // wie oft das System die Op blockiert hat
)

@Immutable
data class AppPermissionUsage(
    val packageName: String,
    val displayName: String,
    val isSystem: Boolean,
    val uid: Int,
    val ops: List<OpAccess>
) {
    /** "Risk-Score" — höher = mehr sensitive Ops mit kürzlichem Access. */
    val activityScore: Long get() = ops.sumOf {
        if (it.lastAccessAgoMs <= 0L) 0L
        // Inverse Frequenz: kürzerer Access = höhere Punkte (max 100k pro Op = letzte Sekunde)
        else maxOf(0L, 100_000L - it.lastAccessAgoMs / 60_000L)
    }
}

/**
 * Liest reale Permission-Nutzung via `dumpsys appops` (Shizuku-shell).
 *
 * Ohne Shizuku ginge das nicht — AppOpsManager.getPackagesForOps braucht
 * GET_APP_OPS_STATS (signature|privileged).
 *
 * dumpsys-Output-Format (Android 10+):
 *   Uid u0a123:
 *     Package com.example.app:
 *       CAMERA: mode=allow; time=+2m43s ago
 *       RECORD_AUDIO: mode=ignore; rejectTime=+1d3h ago (reject)
 *       FINE_LOCATION: mode=foreground; time=+5h2m387ms ago duration=+12s
 *
 * Manche OEMs (MIUI/HyperOS, Samsung) haben leicht abweichende Formate.
 * Wir parsen tolerant — was nicht passt wird einfach übersprungen.
 */
object PermissionUsageReader {

    /** Diese Ops zeigen wir — die wirklich privacy-sensitiv sind. */
    val TRACKED_OPS = setOf(
        "CAMERA",
        "RECORD_AUDIO",
        "FINE_LOCATION",
        "COARSE_LOCATION",
        "MONITOR_LOCATION",
        "MONITOR_HIGH_POWER_LOCATION",
        "READ_CONTACTS",
        "WRITE_CONTACTS",
        "READ_CALL_LOG",
        "WRITE_CALL_LOG",
        "READ_SMS",
        "SEND_SMS",
        "RECEIVE_SMS",
        "READ_CALENDAR",
        "WRITE_CALENDAR",
        "READ_PHONE_STATE",
        "CALL_PHONE",
        "READ_EXTERNAL_STORAGE",
        "WRITE_EXTERNAL_STORAGE",
        "READ_MEDIA_IMAGES",
        "READ_MEDIA_VIDEO",
        "READ_MEDIA_AUDIO",
        "BODY_SENSORS",
        "GET_USAGE_STATS",
        "SYSTEM_ALERT_WINDOW",
        "POST_NOTIFICATION",
        "ACTIVATE_VPN",
        "BLUETOOTH_SCAN",
        "BLUETOOTH_CONNECT",
        "NEARBY_WIFI_DEVICES"
    )

    suspend fun read(context: Context): List<AppPermissionUsage> = withContext(Dispatchers.IO) {
        if (ShizukuHelper.state(context) != ShizukuHelper.State.Ready) {
            return@withContext emptyList()
        }
        val res = ShizukuHelper.runShell(context, "dumpsys appops 2>/dev/null")
        if (!res.ok || res.stdout.isBlank()) return@withContext emptyList()
        parse(context, res.stdout)
    }

    private fun parse(context: Context, output: String): List<AppPermissionUsage> {
        val pm = context.packageManager
        val byPkg = LinkedHashMap<String, MutableList<OpAccess>>()
        val uidByPkg = HashMap<String, Int>()
        var currentPkg: String? = null
        var currentUid = -1
        val uidRegex = Regex("""^\s*Uid (?:u0a)?(\d+)""")
        val pkgRegex = Regex("""^\s*Package (\S+):""")
        // Beispiele:
        //   CAMERA: mode=allow; time=+2m43s387ms ago duration=+12s
        //   FINE_LOCATION: mode=foreground; time=+5h ago
        //   RECORD_AUDIO: mode=ignore; rejectTime=+1d3h ago
        val opRegex = Regex(
            """^\s+([A-Z_]+)\s*:\s*mode=(\w+)(?:\s*;\s*(.*))?$"""
        )

        for (raw in output.lineSequence()) {
            uidRegex.find(raw)?.let { m ->
                val uidNum = m.groupValues[1].toIntOrNull() ?: -1
                // u0a-Prefix → app-uid = 10000 + N
                currentUid = if (raw.contains("u0a")) 10_000 + uidNum else uidNum
                return@let
            }
            pkgRegex.find(raw)?.let { m ->
                currentPkg = m.groupValues[1]
                uidByPkg[currentPkg!!] = currentUid
                return@let
            }
            val pkg = currentPkg ?: continue
            val om = opRegex.find(raw) ?: continue
            val op = om.groupValues[1]
            if (op !in TRACKED_OPS) continue
            val mode = om.groupValues[2]
            val rest = om.groupValues.getOrNull(3).orEmpty()
            val accessAgo = parseTimeAfter(rest, "time=")
            val rejectAgo = parseTimeAfter(rest, "rejectTime=")
            val duration = parseTimeAfter(rest, "duration=")
            val ageMs = when {
                accessAgo > 0 -> accessAgo
                rejectAgo > 0 -> rejectAgo
                else -> 0L
            }
            byPkg.getOrPut(pkg) { mutableListOf() } +=
                OpAccess(op, mode, ageMs, duration, if (rejectAgo > 0) 1 else 0)
        }

        return byPkg.entries.mapNotNull { (pkg, ops) ->
            if (ops.isEmpty()) return@mapNotNull null
            val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
            val label = runCatching { ai?.loadLabel(pm)?.toString() }.getOrNull() ?: pkg
            val isSys = ai?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: true
            AppPermissionUsage(
                packageName = pkg,
                displayName = label,
                isSystem = isSys,
                uid = uidByPkg[pkg] ?: -1,
                ops = ops.distinctBy { it.op }.sortedBy { it.lastAccessAgoMs }
            )
        }.sortedByDescending { it.activityScore }
    }

    /**
     * Parst "+5h2m387ms" → ms (relativ "vor X").
     * Akzeptiert Suffixe d/h/m/s/ms.
     */
    private fun parseTimeAfter(text: String, key: String): Long {
        val idx = text.indexOf(key)
        if (idx < 0) return 0L
        var s = text.substring(idx + key.length).trim()
        if (s.startsWith("+")) s = s.substring(1)
        val end = s.indexOfFirst { c -> c == ' ' || c == ';' || c == ',' }
        val token = if (end > 0) s.substring(0, end) else s
        return parseDurationToken(token)
    }

    private fun parseDurationToken(token: String): Long {
        // "5h2m387ms" → number+unit Paare
        val re = Regex("""(\d+)(ms|s|m|h|d)""")
        var ms = 0L
        for (m in re.findAll(token)) {
            val n = m.groupValues[1].toLongOrNull() ?: continue
            ms += when (m.groupValues[2]) {
                "ms" -> n
                "s"  -> n * 1_000L
                "m"  -> n * 60_000L
                "h"  -> n * 3_600_000L
                "d"  -> n * 86_400_000L
                else -> 0L
            }
        }
        return ms
    }

    fun formatAgo(ageMs: Long): String {
        if (ageMs <= 0L) return "nie"
        val s = ageMs / 1000
        return when {
            s < 60 -> "vor ${s}s"
            s < 3600 -> "vor ${s / 60}m"
            s < 86400 -> "vor ${s / 3600}h"
            else -> "vor ${s / 86400}d"
        }
    }
}
