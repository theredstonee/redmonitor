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

    data class CmdResult(val exitCode: Int, val stdout: String, val stderr: String) {
        val ok: Boolean get() = exitCode == 0
    }

    /**
     * Run an arbitrary command via Shizuku as `shell` user.
     * Returns stdout, stderr and exit code.
     */
    fun runCommand(context: Context, vararg cmd: String): CmdResult {
        if (state(context) != State.Ready) {
            return CmdResult(-1, "", "Shizuku not ready")
        }
        val process = newProcessReflected(arrayOf(*cmd))
            ?: return CmdResult(-1, "", "newProcess failed")
        return runCatching {
            val out = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val err = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            val exit = process.waitFor()
            CmdResult(exit, out.trim(), err.trim())
        }.getOrElse { CmdResult(-1, "", it.message ?: "unknown error") }
            .also { runCatching { process.destroy() } }
    }

    /** Shortcut: run `sh -c "command"` */
    fun runShell(context: Context, command: String): CmdResult =
        runCommand(context, "sh", "-c", command)

    data class DumpsysBattery(
        val maxChargingCurrentUa: Long,
        val maxChargingVoltageUv: Long,
        val acPowered: Boolean,
        val usbPowered: Boolean,
        val wirelessPowered: Boolean
    )

    /**
     * Runs `dumpsys battery` via Shizuku. Works on Samsung where USB-sysfs is SELinux-locked.
     * The dumpsys output contains the negotiated PD voltage/current.
     */
    fun dumpsysBattery(context: Context): DumpsysBattery? {
        if (state(context) != State.Ready) return null
        val process = newProcessReflected(arrayOf("dumpsys", "battery")) ?: return null
        return runCatching {
            val text = try {
                BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            } finally {
                runCatching { process.waitFor() }
                runCatching { process.destroy() }
            }
            parseDumpsys(text)
        }.getOrNull()
    }

    private fun parseDumpsys(out: String): DumpsysBattery {
        var maxI = 0L
        var maxV = 0L
        var ac = false
        var usb = false
        var wireless = false
        for (raw in out.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("Max charging current:") ->
                    maxI = line.substringAfter(":").trim().toLongOrNull() ?: 0L
                line.startsWith("Max charging voltage:") ->
                    maxV = line.substringAfter(":").trim().toLongOrNull() ?: 0L
                line.startsWith("AC powered:") ->
                    ac = line.endsWith("true")
                line.startsWith("USB powered:") ->
                    usb = line.endsWith("true")
                line.startsWith("Wireless powered:") ->
                    wireless = line.endsWith("true")
            }
        }
        return DumpsysBattery(maxI, maxV, ac, usb, wireless)
    }

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
