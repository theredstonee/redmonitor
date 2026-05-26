package com.tamerin.sysmonitor.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

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

object ProcessReader {

    fun read(context: Context): List<RunningApp> {
        val pm = context.packageManager
        if (ShizukuHelper.state(context) == ShizukuHelper.State.Ready) {
            val viaPs = readViaPs(context, pm)
            if (viaPs.isNotEmpty()) return viaPs
            val viaDump = readViaDumpsys(context, pm)
            if (viaDump.isNotEmpty()) return viaDump
        }
        return readViaActivityManager(context, pm)
    }

    /**
     * Most reliable: `ps -A -o PID,RSS,USER,NAME` works on every Android via Shizuku.
     * Output is one process per line.
     */
    private fun readViaPs(context: Context, pm: PackageManager): List<RunningApp> {
        val res = ShizukuHelper.runCommand(context, "ps", "-A", "-o", "PID,RSS,USER,NAME")
        if (!res.ok || res.stdout.isBlank()) return emptyList()

        val apps = mutableListOf<RunningApp>()
        val seen = mutableSetOf<String>()
        var firstLine = true
        for (raw in res.stdout.lineSequence()) {
            if (firstLine) { firstLine = false; continue } // skip header
            val line = raw.trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 4) continue
            val pid = parts[0].toIntOrNull() ?: continue
            val rssKb = parts[1].toLongOrNull() ?: 0L
            val user = parts[2]
            // Process name may contain spaces only if quoted — usually it's the last token
            val processName = parts.drop(3).joinToString(" ")
            val pkg = processName.substringBefore(":")
            if (!isAndroidPkg(pkg)) continue
            if (pkg in seen) continue
            val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: continue
            seen += pkg
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            apps += RunningApp(
                pkg = pkg,
                displayName = ai.loadLabel(pm).toString(),
                isSystem = isSystem,
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
        // Heuristic: must contain at least one dot, no leading "[" (kernel threads)
        if (name.isEmpty() || name.startsWith("[")) return false
        return name.contains(".")
    }

    private fun uidFromUserString(user: String): Int {
        // "u0_a234" → 10234 ; "system" → 1000 ; "root" → 0
        if (user.startsWith("u") && user.contains("_a")) {
            val appPart = user.substringAfter("_a").toIntOrNull() ?: return 0
            return 10000 + appPart
        }
        return when (user) {
            "root" -> 0
            "system" -> 1000
            "radio" -> 1001
            "bluetooth" -> 1002
            else -> 0
        }
    }

    /**
     * Fallback: parse `dumpsys activity processes`. Less reliable across OEMs.
     */
    private fun readViaDumpsys(context: Context, pm: PackageManager): List<RunningApp> {
        val res = ShizukuHelper.runCommand(context, "dumpsys", "activity", "processes")
        if (!res.ok) return emptyList()
        val apps = mutableListOf<RunningApp>()
        val seen = mutableSetOf<String>()
        val procLineRegex = Regex("""(\d+):([\w.:]+)/u?\d*a?(\d*)""")
        val rssRegex = Regex("""rss=\s*(\d+)""")
        val procStateRegex = Regex("""procState=(\d+)""")

        var currentLines = mutableListOf<String>()
        for (line in res.stdout.lineSequence()) {
            if (line.contains("ProcessRecord{") && currentLines.isNotEmpty()) {
                processBlock(currentLines, procLineRegex, rssRegex, procStateRegex, apps, seen, pm)
                currentLines = mutableListOf()
            }
            currentLines += line
        }
        if (currentLines.isNotEmpty()) {
            processBlock(currentLines, procLineRegex, rssRegex, procStateRegex, apps, seen, pm)
        }
        return apps.sortedByDescending { it.rssKb }
    }

    private fun processBlock(
        block: List<String>,
        procLineRegex: Regex,
        rssRegex: Regex,
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

        val rssKb = block.firstNotNullOfOrNull { line ->
            rssRegex.find(line)?.groupValues?.get(1)?.toLongOrNull()
        } ?: 0L
        val procState = block.firstNotNullOfOrNull { line ->
            procStateRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
        } ?: -1

        val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
        val name = ai?.loadLabel(pm)?.toString() ?: pkg
        val isSystem = ai != null && (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        seen += pkg

        apps += RunningApp(
            pkg = pkg, displayName = name, isSystem = isSystem,
            pid = pid, uid = 0, rssKb = rssKb,
            processName = processName,
            importance = procState,
            importanceLabel = procStateLabel(procState)
        )
    }

    private fun procStateLabel(ps: Int): String = when (ps) {
        0, 1, 2 -> "Persistent"
        3, 4 -> "Vordergrund"
        5, 6 -> "Top sichtbar"
        7, 8 -> "Sichtbar"
        9, 10 -> "Service"
        11, 12 -> "Wahrnehmbar"
        13, 14 -> "Cached"
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
