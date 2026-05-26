package com.tamerin.sysmonitor.data

import android.content.Context

data class TopProc(
    val cpuPercent: Float,
    val pid: Int,
    val processName: String,
    val userPct: Float,
    val kernelPct: Float
)

object TopCpuReader {

    /**
     * Reads top CPU consumers via `dumpsys cpuinfo`. Works on every Android via Shizuku shell.
     * Sample output format:
     *   Load: 1.50 / 1.20 / 0.80
     *   CPU usage from 12345ms to 5678ms ago:
     *     20% 1234/com.foo.bar: 10% user + 10% kernel / faults: 100 minor
     *     15% 5678/system_server: 8% user + 7% kernel
     */
    fun read(context: Context, limit: Int = 20): List<TopProc> {
        if (ShizukuHelper.state(context) != ShizukuHelper.State.Ready) return emptyList()
        val res = ShizukuHelper.runCommand(context, "dumpsys", "cpuinfo")
        if (!res.ok) return emptyList()

        // Line: "  20% 1234/com.foo.bar: 10% user + 10% kernel ..."
        // The whole percentage can be a decimal too: "1.5%"
        val regex = Regex("""\s*([\d.]+)%\s+(\d+)/(\S+?):\s+(?:([\d.]+)%\s*user)?\s*(?:\+\s*([\d.]+)%\s*kernel)?""")
        val out = mutableListOf<TopProc>()
        for (line in res.stdout.lineSequence()) {
            val m = regex.find(line) ?: continue
            val cpu = m.groupValues[1].toFloatOrNull() ?: continue
            val pid = m.groupValues[2].toIntOrNull() ?: continue
            val name = m.groupValues[3]
            val user = m.groupValues.getOrNull(4)?.toFloatOrNull() ?: 0f
            val kernel = m.groupValues.getOrNull(5)?.toFloatOrNull() ?: 0f
            out += TopProc(cpu, pid, name, user, kernel)
        }
        return out.sortedByDescending { it.cpuPercent }.take(limit)
    }
}
