package com.tamerin.sysmonitor.data

import java.io.BufferedReader
import java.io.InputStreamReader

object SystemProps {
    /**
     * Runs `getprop` and returns a sorted map of all system properties.
     * Works without root on most devices but may be restricted on some Android 14+ builds.
     */
    fun readAll(): Map<String, String> {
        return runCatching {
            val proc = Runtime.getRuntime().exec("getprop")
            val out = BufferedReader(InputStreamReader(proc.inputStream)).readText()
            val map = mutableMapOf<String, String>()
            val regex = Regex("""\[(.+?)]:\s*\[(.*?)]""")
            regex.findAll(out).forEach { m ->
                map[m.groupValues[1]] = m.groupValues[2]
            }
            map.toSortedMap()
        }.getOrDefault(emptyMap())
    }
}
