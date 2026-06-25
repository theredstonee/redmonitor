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

    /**
     * Android 13+ blockt sideloaded Apps mit „Restricted setting" — bestimmte
     * Permissions (Accessibility, Usage-Stats, Notification-Listener) sind
     * in den App-Settings ausgegraut bis ACCESS_RESTRICTED_SETTINGS auf allow
     * steht. Mit Shizuku können wir das selbst freischalten.
     *
     * Idempotent — Aufruf bei jedem Ready-Übergang ist OK, der Befehl ist
     * billig und überschreibt einfach den aktuellen Wert.
     */
    fun unblockRestrictedSettings(context: Context): Boolean {
        if (state(context) != State.Ready) return false
        val res = runCommand(
            context, "appops", "set", context.packageName,
            "ACCESS_RESTRICTED_SETTINGS", "allow"
        )
        return res.ok
    }

    data class AutoGrantReport(
        val granted: List<String>,
        val failed: List<Pair<String, String>>,
        val skipped: List<String>
    ) {
        val total: Int get() = granted.size + failed.size + skipped.size
    }

    /**
     * Vergibt automatisch alle Runtime- und Special-Permissions die RedMonitor
     * deklariert — der User muss damit nichts mehr durch die Settings klicken.
     *
     * Drei Pfade:
     *   1. `pm grant <pkg> <perm>` für klassische Runtime-Permissions
     *   2. `appops set <pkg> <op> allow` für Special-Ops (Overlay, Usage-Stats,
     *      Install-Packages, Restricted-Settings)
     *   3. `settings put secure enabled_notification_listeners` für den
     *      Notification-Listener-Service (additiv, vorhandene Liste bleibt erhalten)
     *
     * Idempotent. Wenn eine Permission nicht im Manifest steht, wird sie
     * still übersprungen (pm grant failed → kategorisiert als skipped).
     */
    fun autoGrantAllPermissions(context: Context): AutoGrantReport {
        if (state(context) != State.Ready) {
            return AutoGrantReport(emptyList(), emptyList(),
                listOf("Shizuku nicht bereit"))
        }
        val pkg = context.packageName
        val granted = mutableListOf<String>()
        val failed = mutableListOf<Pair<String, String>>()
        val skipped = mutableListOf<String>()

        // 1. Runtime-Permissions via pm grant
        val runtimePerms = listOf(
            "android.permission.READ_PHONE_STATE",
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.POST_NOTIFICATIONS"
        )
        for (perm in runtimePerms) {
            val res = runCommand(context, "pm", "grant", pkg, perm)
            when {
                res.ok -> granted += perm.substringAfterLast(".")
                res.stderr.contains("has not requested permission", true) ||
                    res.stderr.contains("not declared", true) ->
                    skipped += perm.substringAfterLast(".")
                else -> failed += perm.substringAfterLast(".") to res.stderr.take(80)
            }
        }

        // 2. App-Ops für Special-Permissions
        val appOps = mutableListOf(
            "ACCESS_RESTRICTED_SETTINGS",
            "SYSTEM_ALERT_WINDOW",
            "GET_USAGE_STATS",
            "REQUEST_INSTALL_PACKAGES",
            "RUN_ANY_IN_BACKGROUND",
            "RUN_IN_BACKGROUND",
            "SCHEDULE_EXACT_ALARM"
        )
        // MIUI/HyperOS hat extra Hersteller-Ops für Autostart und Pop-up — die meisten
        // sind im Vanilla-AOSP-appops nicht definiert, aber Xiaomi hat sie hinzugefügt.
        // Wir probieren sie blind — bei Nicht-Xiaomi failed das einzelne Op silent.
        val mfg = (android.os.Build.MANUFACTURER ?: "").lowercase()
        val brand = (android.os.Build.BRAND ?: "").lowercase()
        val isXiaomi = mfg.contains("xiaomi") || brand.contains("xiaomi") ||
            brand.contains("redmi") || brand.contains("poco")
        if (isXiaomi) {
            appOps += listOf(
                "AUTO_START",                 // MIUI: in Autostart-Liste aufnehmen
                "BACKGROUND_START_ACTIVITY",  // MIUI: Aktivität aus Hintergrund starten
                "SHOW_WHEN_LOCKED",           // MIUI: über Sperrbildschirm anzeigen
                "POPUP_BACKGROUND"            // MIUI: Pop-up aus Hintergrund
            )
            // Zusätzlich: MIUI Security Center "Permission Manager" Content-Provider
            // — der ECHTE Autostart-Switch hängt dort drin, nicht im normalen appops.
            runCatching {
                val r = runShell(context,
                    "content insert --uri content://com.lbe.security.miui.permissionmanager." +
                        "AutoStartContentProvider --bind packageName:s:$pkg --bind isAutoStart:i:1")
                if (r.ok) granted += "miui:AutoStart-Provider"
            }
        }
        for (op in appOps) {
            val res = runCommand(context, "appops", "set", pkg, op, "allow")
            when {
                res.ok -> granted += "op:$op"
                res.stderr.contains("Unknown operation", true) -> skipped += "op:$op"
                else -> failed += "op:$op" to res.stderr.take(80)
            }
        }

        // 3. Notification-Listener-Service additiv in Secure-Settings eintragen
        val nlComponent = "$pkg/$pkg.data.NotificationLoggerService"
        val nlExisting = runShell(
            context,
            "settings get secure enabled_notification_listeners"
        ).stdout.trim()
        val needsAdd = nlExisting == "null" ||
            nlExisting.isEmpty() ||
            !nlExisting.split(':').any { it == nlComponent }
        if (needsAdd) {
            val merged = if (nlExisting.isEmpty() || nlExisting == "null") {
                nlComponent
            } else {
                "$nlExisting:$nlComponent"
            }
            val res = runShell(
                context,
                "settings put secure enabled_notification_listeners '$merged'"
            )
            if (res.ok) granted += "NotificationListener" else {
                failed += "NotificationListener" to res.stderr.take(80)
            }
        } else {
            skipped += "NotificationListener (bereits aktiv)"
        }

        return AutoGrantReport(granted, failed, skipped)
    }

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
