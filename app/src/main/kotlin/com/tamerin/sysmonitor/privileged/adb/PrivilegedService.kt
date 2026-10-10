package com.tamerin.sysmonitor.privileged.adb

import android.os.Process

/**
 * Implementierung des privilegierten Binder-Dienstes. Läuft im `app_process`-Prozess
 * als `shell`-User (UID 2000); von diesem geerbt bekommen alle per [exec] gestarteten
 * Kindprozesse ebenfalls UID 2000 — genau das gewünschte Privileg-Level.
 */
class PrivilegedService : IPrivilegedService.Stub() {

    override fun exec(cmd: Array<String>): ShellResult {
        return try {
            val proc = ProcessBuilder(*cmd).start()
            // stderr nebenläufig lesen, sonst kann bei viel Ausgabe der Pipe-Puffer
            // volllaufen und exec() deadlocken.
            val errBuf = StringBuilder()
            val errThread = Thread {
                runCatching { proc.errorStream.bufferedReader().use { errBuf.append(it.readText()) } }
            }.apply { start() }

            val out = proc.inputStream.bufferedReader().use { it.readText() }
            val exit = proc.waitFor()
            errThread.join(2000)

            ShellResult(exit, out.trim(), errBuf.toString().trim())
        } catch (t: Throwable) {
            ShellResult(-1, "", t.message ?: "exec failed")
        }
    }

    override fun getPid(): Int = Process.myPid()

    override fun getUid(): Int = Process.myUid()

    override fun destroy() {
        Process.killProcess(Process.myPid())
    }
}
