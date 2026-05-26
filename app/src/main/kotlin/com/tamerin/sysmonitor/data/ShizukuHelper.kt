package com.tamerin.sysmonitor.data

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuHelper {

    enum class State { NotInstalled, NotRunning, NeedsPermission, Ready }

    private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

    fun state(context: Context): State {
        val installed = isInstalled(context)
        if (!installed) return State.NotInstalled
        return try {
            if (!Shizuku.pingBinder()) {
                State.NotRunning
            } else if (Shizuku.isPreV11()) {
                State.NotRunning
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                State.Ready
            } else {
                State.NeedsPermission
            }
        } catch (_: Throwable) {
            State.NotRunning
        }
    }

    private fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        true
    }.getOrDefault(false)

    fun requestPermission(requestCode: Int = 6700) {
        runCatching { Shizuku.requestPermission(requestCode) }
    }

    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
    }

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    /**
     * Reads a sysfs file via Shizuku as the `shell` user.
     * Uses reflection because newProcess is marked @hide/private in Shizuku 13.x
     * but still exists in the bytecode and works at runtime.
     */
    fun readFile(context: Context, path: String): String? {
        if (state(context) != State.Ready) return null
        return runCatching {
            val process = newProcessReflected(arrayOf("cat", path)) ?: return@runCatching null
            try {
                val text = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
                val exit = process.waitFor()
                if (exit == 0 && text.isNotBlank()) text.trim() else null
            } finally {
                runCatching { process.destroy() }
            }
        }.getOrNull()
    }

    fun readLong(context: Context, path: String): Long? = readFile(context, path)?.toLongOrNull()

    private fun newProcessReflected(
        cmd: Array<String>,
        env: Array<String>? = null,
        dir: String? = null
    ): Process? {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            method.invoke(null, cmd, env, dir) as? Process
        } catch (_: Throwable) {
            null
        }
    }
}
