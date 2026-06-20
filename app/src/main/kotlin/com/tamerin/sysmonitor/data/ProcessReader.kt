package com.tamerin.sysmonitor.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable

@Immutable
data class RunningApp(
    val pkg: String,
    val displayName: String,
    val isSystem: Boolean,
    val pid: Int,
    val uid: Int,
    val rssKb: Long,
    val processName: String,
    val importance: Int,
    val importanceLabel: String
)

@Immutable
data class ProcessReadResult(
    val apps: List<RunningApp>,
    val source: String,
    val rawCount: Int,
    val debugSnippet: String
)

object ProcessReader {

    fun read(context: Context): ProcessReadResult {
        val pm = context.packageManager
        if (ShizukuHelper.state(context) == ShizukuHelper.State.Ready) {
            // Try multiple ps variants
            for (cmd in listOf(
                listOf("ps", "-A"),
                listOf("ps", "-A", "-o", "PID,USER,RSS,NAME"),
                listOf("ps", "-ef")
            )) {
                val res = ShizukuHelper.runCommand(context, *cmd.toTypedArray())
                if (res.ok && res.stdout.isNotBlank()) {
                    val raw = res.stdout.lineSequence().toList()
                    val apps = parsePs(res.stdout, pm)
                    if (apps.isNotEmpty()) {
                        return ProcessReadResult(
                            apps = apps,
                            source = "Shizuku: ${cmd.joinToString(" ")} (${apps.size}/${raw.size - 1})",
                            rawCount = raw.size,
                            debugSnippet = raw.take(3).joinToString("\n")
                        )
                    }
                }
            }
            // dumpsys fallback
            val dumpRes = ShizukuHelper.runCommand(context, "dumpsys", "activity", "processes")
            if (dumpRes.ok) {
                val apps = parseDumpsys(dumpRes.stdout, pm)
                if (apps.isNotEmpty()) {
                    return ProcessReadResult(
                        apps = apps,
                        source = "Shizuku: dumpsys activity processes",
                        rawCount = dumpRes.stdout.count { it == '\n' },
                        debugSnippet = dumpRes.stdout.lineSequence().take(3).joinToString("\n")
                    )
                }
            }
        }
        // ActivityManager fallback (only own process on Android 7+)
        val amApps = readViaActivityManager(context, pm)
        return ProcessReadResult(
            apps = amApps,
            source = if (ShizukuHelper.state(context) == ShizukuHelper.State.Ready)
                "Shizuku liefert nichts — Fallback ActivityManager"
            else "Ohne Shizuku: nur eigener Prozess",
            rawCount = amApps.size,
            debugSnippet = ""
        )
    }

    /**
     * Parses ps output. Supports both `-o PID,USER,RSS,NAME` and default `ps -A` Android format.
     * Default Android ps -A output:
     *   USER     PID  PPID     VSZ    RSS WCHAN            ADDR S NAME
     *   root       1     0   12345    678 0                  0 S init
     *
     * Or with -ef:
     *   UID            PID  PPID  C STIME TTY          TIME CMD
     */
    private fun parsePs(output: String, pm: PackageManager): List<RunningApp> {
        val lines = output.lineSequence().toList()
        if (lines.isEmpty()) return emptyList()
        val header = lines[0]
        val cols = header.trim().split(Regex("\\s+"))
        val pidIdx = cols.indexOfFirst { it.equals("PID", true) }
        val userIdx = cols.indexOfFirst { it.equals("USER", true) || it.equals("UID", true) }
        val rssIdx = cols.indexOfFirst { it.equals("RSS", true) }
        val nameIdx = cols.indexOfFirst { it.equals("NAME", true) || it.equals("CMD", true) || it.equals("COMM", true) }
        if (pidIdx < 0 || nameIdx < 0) return emptyList()

        val apps = mutableListOf<RunningApp>()
        val seen = mutableSetOf<String>()
        for (line in lines.drop(1)) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split(Regex("\\s+"))
            if (parts.size <= nameIdx) continue
            val pid = parts.getOrNull(pidIdx)?.toIntOrNull() ?: continue
            val user = if (userIdx >= 0) parts[userIdx] else ""
            val rssKb = if (rssIdx >= 0) parts.getOrNull(rssIdx)?.toLongOrNull() ?: 0L else 0L
            val processName = if (nameIdx >= parts.size - 1) parts[nameIdx]
                else parts.drop(nameIdx).joinToString(" ")
            val pkg = processName.substringBefore(":")
            if (!isAndroidPkg(pkg)) continue
            if (pkg in seen) continue
            val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: continue
            seen += pkg
            apps += RunningApp(
                pkg = pkg,
                displayName = ai.loadLabel(pm).toString(),
                isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                pid = pid,
                uid = uidFromUserString(user),
                rssKb = rssKb,
                processName = processName,
                importance = -1,
                importanceLabel = "—"
            )
        }
        return apps.sortedByDescending { it.rssKb }
    }

    private fun isAndroidPkg(name: String): Boolean {
        if (name.isEmpty() || name.startsWith("[")) return false
        return name.contains(".")
    }

    private fun uidFromUserString(user: String): Int {
        if (user.startsWith("u") && user.contains("_a")) {
            val n = user.substringAfter("_a").toIntOrNull() ?: return 0
            return 10000 + n
        }
        return when (user) {
            "root" -> 0
            "system" -> 1000
            "radio" -> 1001
            "bluetooth" -> 1002
            else -> user.toIntOrNull() ?: 0
        }
    }

    private fun parseDumpsys(output: String, pm: PackageManager): List<RunningApp> {
        val apps = mutableListOf<RunningApp>()
        val seen = mutableSetOf<String>()
        val procLineRegex = Regex("""(\d+):([\w.:]+)/""")
        val rssRegex = Regex("""rss=\s*(\d+)""")
        val pssRegex = Regex("""pss=\s*(\d+)""")
        val procStateRegex = Regex("""procState=(\d+)""")

        var currentLines = mutableListOf<String>()
        for (line in output.lineSequence()) {
            if (line.contains("ProcessRecord{") && currentLines.isNotEmpty()) {
                handleBlock(currentLines, procLineRegex, rssRegex, pssRegex, procStateRegex, apps, seen, pm)
                currentLines = mutableListOf()
            }
            currentLines += line
        }
        if (currentLines.isNotEmpty()) {
            handleBlock(currentLines, procLineRegex, rssRegex, pssRegex, procStateRegex, apps, seen, pm)
        }
        return apps.sortedByDescending { it.rssKb }
    }

    private fun handleBlock(
        block: List<String>,
        procLineRegex: Regex,
        rssRegex: Regex,
        pssRegex: Regex,
        procStateRegex: Regex,
        apps: MutableList<RunningApp>,
        seen: MutableSet<String>,
        pm: PackageManager
    ) {
        val header = block.firstOrNull { it.contains("ProcessRecord{") } ?: return
        val m = procLineRegex.find(header) ?: return
        val pid = m.groupValues[1].toIntOrNull() ?: return
        val processName = m.groupValues[2]
        val pkg = processName.substringBefore(":")
        if (pkg in seen) return

        val rssKb = block.firstNotNullOfOrNull { rssRegex.find(it)?.groupValues?.get(1)?.toLongOrNull() }
            ?: block.firstNotNullOfOrNull { pssRegex.find(it)?.groupValues?.get(1)?.toLongOrNull() }
            ?: 0L
        val procState = block.firstNotNullOfOrNull { procStateRegex.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: -1

        val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
        seen += pkg
        apps += RunningApp(
            pkg = pkg,
            displayName = ai?.loadLabel(pm)?.toString() ?: pkg,
            isSystem = ai != null && (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            pid = pid, uid = 0, rssKb = rssKb,
            processName = processName,
            importance = procState,
            importanceLabel = procStateLabel(procState)
        )
    }

    private fun procStateLabel(ps: Int): String = when (ps) {
        in 0..2 -> "Persistent"
        in 3..4 -> "Vordergrund"
        in 5..6 -> "Top sichtbar"
        in 7..8 -> "Sichtbar"
        in 9..10 -> "Service"
        in 11..12 -> "Wahrnehmbar"
        in 13..14 -> "Cached"
        15 -> "Leerer Cache"
        else -> "—"
    }

    private fun readViaActivityManager(context: Context, pm: PackageManager): List<RunningApp> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val list = am.runningAppProcesses ?: return emptyList()
        return list.mapNotNull { info ->
            val pkg = info.pkgList.firstOrNull() ?: info.processName.substringBefore(":")
            val memInfo = am.getProcessMemoryInfo(intArrayOf(info.pid)).firstOrNull()
            val rss = memInfo?.totalPss?.toLong() ?: 0L
            val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@mapNotNull null
            RunningApp(
                pkg = pkg,
                displayName = ai.loadLabel(pm).toString(),
                isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                pid = info.pid,
                uid = info.uid,
                rssKb = rss,
                processName = info.processName,
                importance = info.importance,
                importanceLabel = amImportanceLabel(info.importance)
            )
        }.sortedByDescending { it.rssKb }
    }

    private fun amImportanceLabel(imp: Int): String = when (imp) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "Vordergrund"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "Vordergrund-Service"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "Sichtbar"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "Wahrnehmbar"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "Service"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "Cached"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE -> "Beendet"
        else -> "—"
    }
}
