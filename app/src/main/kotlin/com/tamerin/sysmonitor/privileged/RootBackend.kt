package com.tamerin.sysmonitor.privileged

import android.content.Context
import android.util.Log
import com.tamerin.sysmonitor.data.ShizukuHelper.CmdResult
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * [PrivilegedShell] über `su` auf gerooteten Geräten — ganz ohne Shizuku oder ADB.
 *
 * Statt pro Kommando ein neues `su` zu starten (Magisk & Co. zeigen dann bei JEDEM Aufruf
 * einen Toast/Log-Eintrag, und die ~45 Call-Sites pollen teils sekündlich), hält das
 * Backend einen kleinen Pool dauerhafter Root-Shells. Kommandos werden hineingeschrieben,
 * das Ende erkennt ein zufälliger Marker samt Exit-Code.
 *
 * Root ist Opt-in ([RootPrefs]): `su` aufzurufen löst beim Root-Manager einen Dialog aus,
 * das darf nicht ungefragt beim App-Start passieren. [isReady] blockiert nie — es spiegelt
 * nur, ob [start] schon erfolgreich war, weil der Gate-Status auch vom Main-Thread kommt.
 */
object RootBackend : PrivilegedShell {

    override val kind = PrivilegedBackend.ROOT

    private const val TAG = "RootBackend"
    private const val POOL_SIZE = 3
    private const val CMD_TIMEOUT_SEC = 30L

    @Volatile private var granted = false

    private val idle = ArrayBlockingQueue<RootShell>(POOL_SIZE)
    private val created = AtomicInteger(0)
    private val readers = Executors.newCachedThreadPool { r ->
        Thread(r, "root-shell-io").apply { isDaemon = true }
    }

    @Volatile private var autoStartTried = false

    override fun isReady(context: Context): Boolean {
        if (!RootPrefs.isEnabled(context)) return false
        // Einstiege ohne MainActivity (Boot → Overlay, Widget, Worker): die Shell einmal im
        // Hintergrund nachziehen. Der Root-Manager kennt die Entscheidung schon.
        if (!granted && !autoStartTried) {
            autoStartTried = true
            val app = context.applicationContext
            readers.execute { start(app) }
        }
        return granted
    }

    /** true, wenn ein `su`-Binary existiert (ohne es aufzurufen → kein Root-Dialog). */
    fun isSuPresent(): Boolean =
        System.getenv("PATH").orEmpty().split(':').plus(SU_FALLBACK_DIRS)
            .any { File(it, "su").exists() }

    /**
     * Fordert Root an (Root-Manager-Dialog) und hält die Shell offen. Blockiert bis der
     * Nutzer entschieden hat — nur off-main aufrufen. true = Root gewährt (UID 0).
     */
    @Synchronized
    fun start(@Suppress("UNUSED_PARAMETER") context: Context): Boolean {
        if (granted) return true
        val shell = RootShell.open() ?: return false.also { Log.w(TAG, "su nicht startbar") }
        val res = shell.run(arrayOf("id", "-u"))
        if (res.exitCode != 0 || res.stdout.trim() != "0") {
            Log.w(TAG, "Root verweigert: exit=${res.exitCode} out=${res.stdout} err=${res.stderr}")
            shell.close()
            return false
        }
        created.incrementAndGet()
        idle.offer(shell)
        granted = true
        Log.i(TAG, "Root-Shell bereit")
        return true
    }

    /** Schließt alle Shells; danach ist das Backend nicht mehr bereit. */
    @Synchronized
    fun stop() {
        granted = false
        while (true) {
            val s = idle.poll() ?: break
            s.close()
            created.decrementAndGet()
        }
    }

    override fun exec(context: Context, cmd: Array<String>): CmdResult {
        if (!granted) return CmdResult(-1, "", "Root not granted")
        val shell = acquire() ?: return CmdResult(-1, "", "Root shell unavailable")
        val res = shell.run(cmd)
        if (shell.isAlive) {
            idle.offer(shell)
        } else {
            created.decrementAndGet()
        }
        return res
    }

    /** Freie Shell holen; bei Bedarf bis [POOL_SIZE] neue öffnen, sonst warten. */
    private fun acquire(): RootShell? {
        idle.poll()?.let { return it }
        if (created.incrementAndGet() <= POOL_SIZE) {
            RootShell.open()?.let { return it }
            created.decrementAndGet()
            // `su` geht nicht mehr (Root entzogen?) → Backend abschalten statt zu hängen.
            if (created.get() == 0) granted = false
            return null
        }
        created.decrementAndGet()
        return idle.poll(CMD_TIMEOUT_SEC, TimeUnit.SECONDS)
    }

    /** Eine dauerhafte `su`-Shell. Nicht thread-safe — gehört immer genau einem Aufrufer. */
    private class RootShell private constructor(private val process: Process) {
        private val stdin = OutputStreamWriter(process.outputStream, Charsets.UTF_8)
        private val stdout = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
        private val stderr = BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8))

        val isAlive: Boolean get() = process.isAlive

        fun run(cmd: Array<String>): CmdResult = runCatching {
            val mark = "__RM_" + UUID.randomUUID().toString().replace("-", "")
            val line = cmd.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
            // stdin von /dev/null: ein Kommando, das von stdin liest, darf nicht die
            // nachfolgenden Marker-Zeilen „verschlucken".
            stdin.write("$line </dev/null; printf '\\n$mark %d\\n' \$?; printf '\\n$mark\\n' >&2\n")
            stdin.flush()

            val err = readers.submit<String> { readUntil(stderr, mark).first }
            val (out, exit) = readers.submit<Pair<String, Int>> { readUntil(stdout, mark) }
                .get(CMD_TIMEOUT_SEC, TimeUnit.SECONDS)
            CmdResult(exit, out.trim(), err.get(CMD_TIMEOUT_SEC, TimeUnit.SECONDS).trim())
        }.getOrElse {
            // Timeout oder Stream-Fehler: Shell ist in unbekanntem Zustand → verwerfen.
            close()
            CmdResult(-1, "", it.message ?: it.javaClass.simpleName)
        }

        /** Liest bis zur Marker-Zeile; liefert Ausgabe + Exit-Code (stderr: -1). */
        private fun readUntil(reader: BufferedReader, mark: String): Pair<String, Int> {
            val sb = StringBuilder()
            while (true) {
                val l = reader.readLine() ?: throw java.io.EOFException("root shell closed")
                if (l.startsWith(mark)) {
                    return sb.toString() to (l.substringAfter(' ', "").trim().toIntOrNull() ?: -1)
                }
                sb.append(l).append('\n')
            }
        }

        fun close() {
            runCatching { stdin.write("exit\n"); stdin.flush() }
            runCatching { process.destroy() }
        }

        companion object {
            fun open(): RootShell? = runCatching {
                RootShell(ProcessBuilder("su").start())
            }.getOrNull()
        }
    }

    private val SU_FALLBACK_DIRS = listOf(
        "/system/bin", "/system/xbin", "/sbin", "/su/bin", "/debug_ramdisk", "/data/adb/ksu/bin"
    )
}
