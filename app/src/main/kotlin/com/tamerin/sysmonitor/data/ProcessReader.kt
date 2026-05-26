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
        // Try Shizuku first — gives us all processes, not just our own
        val viaShizuku = if (ShizukuHelper.state(context) == ShizukuHelper.State.Ready) {
            readViaDumpsys(context, pm)
        } else emptyList()
        if (viaShizuku.isNotEmpty()) return viaShizuku

        // Fallback: ActivityManager.getRunningAppProcesses returns only our own process since Android 7
        return readViaActivityManager(context, pm)
    }

    private fun readViaDumpsys(context: Context, pm: PackageManager): List<RunningApp> {
        val res = ShizukuHelper.runCommand(context, "dumpsys", "activity", "processes")
        if (!res.ok) return emptyList()
        return parseDumpsys(res.stdout, pm)
    }

    /**
     * Parses the relevant lines from `dumpsys activity processes`.
     * Example block we look for:
     *   *APP* UID 10245 ProcessRecord{abc123 12345:com.foo.bar/u0a245}
     *     adj=cch+1 procState=15 ...
     *     pss= ... uss=... rss= 234560kB
     */
    private fun parseDumpsys(output: String, pm: PackageManager): List<RunningApp> {
        val apps = mutableListOf<RunningApp>()
        val procRegex = Regex("""\d+:([\w.]+)/u?\d*a?(\d+)""")
        val rssRegex = Regex("""rss=\s*(\d+)kB""")
        var currentLines = mutableListOf<String>()
        val blocks = mutableListOf<List<String>>()
        for (line in output.lineSequence()) {
            if (line.contains("ProcessRecord{")) {
                if (currentLines.isNotEmpty()) blocks += currentLines
                currentLines = mutableListOf(line)
            } else if (currentLines.isNotEmpty()) {
                currentLines += line
                if (line.isBlank()) {
                    blocks += currentLines
                    currentLines = mutableListOf()
                }
            }
        }
        if (currentLines.isNotEmpty()) blocks += currentLines

        val seen = mutableSetOf<String>()
        for (block in blocks) {
            val headerLine = block.firstOrNull { it.contains("ProcessRecord{") } ?: continue
            val matchHeader = Regex("""(\d+):([\w.:]+)/""").find(headerLine) ?: continue
            val pid = matchHeader.groupValues[1].toIntOrNull() ?: continue
            val processName = matchHeader.groupValues[2]
            val pkg = processName.substringBefore(":")
            if (pkg in seen) continue
            seen += pkg

            val uidMatch = Regex("""UID (\d+)""").find(headerLine)
            val uid = uidMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

            val rssLine = block.firstOrNull { it.contains("rss=") }
            val rssKb = rssLine?.let { rssRegex.find(it)?.groupValues?.get(1)?.toLongOrNull() } ?: 0L

            val importance = parseImportance(block)

            val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
            val name = ai?.loadLabel(pm)?.toString() ?: pkg
            val isSystem = ai != null && (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            apps += RunningApp(
                pkg = pkg,
                displayName = name,
                isSystem = isSystem,
                pid = pid,
                uid = uid,
                rssKb = rssKb,
                processName = processName,
                importance = importance,
                importanceLabel = importanceLabel(importance)
            )
        }
        return apps.sortedByDescending { it.rssKb }
    }

    private fun parseImportance(block: List<String>): Int {
        for (line in block) {
            // procState=NN or oom: prev=NN curr=NN  — pick the foreground/visible state when possible
            val ps = Regex("""procState=(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull()
            if (ps != null) return ps
        }
        return -1
    }

    private fun importanceLabel(ps: Int): String = when (ps) {
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
        return list.map { info ->
            val pkg = info.pkgList.firstOrNull() ?: info.processName.substringBefore(":")
            val memInfo = am.getProcessMemoryInfo(intArrayOf(info.pid)).firstOrNull()
            val rss = memInfo?.totalPss?.toLong() ?: 0L
            val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
            RunningApp(
                pkg = pkg,
                displayName = ai?.loadLabel(pm)?.toString() ?: pkg,
                isSystem = ai != null && (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
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
