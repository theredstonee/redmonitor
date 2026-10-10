package com.tamerin.sysmonitor.privileged.adb

import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLSocket

/**
 * Eingebetteter ADB-Client (klassisch, über TCP — z. B. nach `adb tcpip 5555`). Spricht
 * das ADB-Protokoll direkt gegen den geräteeigenen `adbd` auf 127.0.0.1:5555, macht die
 * RSA-Auth (Signatur; bei unbekanntem Key sendet er den Public Key, worauf `adbd` den
 * On-Device „USB-Debugging zulassen"-Dialog zeigt) und führt danach Kommandos über den
 * Shell-Protokoll-v2-Stream aus.
 *
 * DEVICE-VERIFY: gegen echtes `adbd` noch nicht getestet (Auth-Ablauf, shell,v2-Framing).
 */
class AdbClient(
    private val host: String,
    private val port: Int,
    private val key: AdbKey
) {
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var localIdCounter = 1
    private val lock = Any()

    fun isConnected(): Boolean {
        val s = socket
        return s != null && s.isConnected && !s.isClosed
    }

    /**
     * Baut die Verbindung auf und führt die Auth durch. Blockiert ggf. bis der Nutzer
     * den „Zulassen"-Dialog bestätigt (bis [authTimeoutMs]). true bei Erfolg.
     */
    fun connect(connectTimeoutMs: Int = 8000, authTimeoutMs: Int = 60000): Boolean {
        close()
        val s = Socket()
        try {
            s.connect(InetSocketAddress(host, port), connectTimeoutMs)
            s.soTimeout = authTimeoutMs
            socket = s
            input = BufferedInputStream(s.getInputStream())
            output = s.getOutputStream()

            send(AdbProtocol.connect())

            var sentSignature = false
            while (true) {
                val msg = readMessage()
                when (msg.command) {
                    AdbProtocol.A_CNXN -> return true // authentifiziert & verbunden
                    AdbProtocol.A_STLS -> {
                        // Wireless-Connect-Port verlangt TLS: bestätigen, Socket upgraden,
                        // danach schickt adbd CNXN (Auth = Client-Cert, kein A_AUTH).
                        send(AdbProtocol.stls())
                        upgradeToTls()
                    }
                    AdbProtocol.A_AUTH -> {
                        if (msg.arg0 == AdbProtocol.AUTH_TYPE_TOKEN) {
                            if (!sentSignature) {
                                val sig = key.signAdbToken(msg.payload)
                                send(AdbProtocol.Message(AdbProtocol.A_AUTH, AdbProtocol.AUTH_TYPE_SIGNATURE, 0, sig))
                                sentSignature = true
                            } else {
                                // Signatur nicht anerkannt ⇒ Public Key schicken → Dialog am Gerät.
                                send(AdbProtocol.Message(AdbProtocol.A_AUTH, AdbProtocol.AUTH_TYPE_RSA_PUBLIC, 0, key.adbPublicKeyBytes()))
                            }
                        }
                    }
                    AdbProtocol.A_CLSE -> { /* Gegenseite hat abgebrochen */ }
                    else -> { /* ignorieren */ }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "connect fehlgeschlagen: ${t.message}")
            close()
            return false
        }
    }

    /** Führt ein bereits shell-fertiges Kommando aus und liefert stdout/stderr/exit. */
    fun shellExec(commandLine: String, execTimeoutMs: Int = 60000): ShellResult = synchronized(lock) {
        val out = output ?: return ShellResult(-1, "", "not connected")
        socket?.soTimeout = execTimeoutMs
        val localId = nextLocalId()
        send(AdbProtocol.open(localId, "shell,v2,raw:$commandLine"))

        val stream = ByteArrayOutputStream()
        try {
            loop@ while (true) {
                val msg = readMessage()
                if (msg.arg1 != localId && msg.command != AdbProtocol.A_CLSE) continue
                when (msg.command) {
                    AdbProtocol.A_OKAY -> { /* Stream bereit */ }
                    AdbProtocol.A_WRTE -> {
                        stream.write(msg.payload)
                        // Empfang bestätigen, sonst sendet adbd nicht weiter.
                        send(AdbProtocol.okay(localId, msg.arg0))
                    }
                    AdbProtocol.A_CLSE -> {
                        if (msg.arg1 == localId) {
                            send(AdbProtocol.close(localId, msg.arg0))
                            break@loop
                        }
                    }
                    else -> {}
                }
            }
        } catch (t: Throwable) {
            return ShellResult(-1, stream.toByteArray().decodeToString().trim(), t.message ?: "stream error")
        }
        return parseShellV2(stream.toByteArray())
    }

    /** Hebt die bestehende Klartext-Verbindung auf TLS 1.3 an (STLS, Wireless-Connect). */
    private fun upgradeToTls() {
        val base = socket ?: throw IOException("not connected")
        val ctx = AdbSsl.sslContext(key)
        val ssl = (ctx.socketFactory.createSocket(base, host, port, true) as SSLSocket).apply {
            useClientMode = true
            runCatching { enabledProtocols = arrayOf("TLSv1.3") }
            startHandshake()
        }
        socket = ssl
        input = BufferedInputStream(ssl.inputStream)
        output = ssl.outputStream
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null; input = null; output = null
    }

    private fun nextLocalId(): Int = synchronized(this) { ++localIdCounter }

    private fun send(m: AdbProtocol.Message) {
        val o = output ?: throw IOException("not connected")
        o.write(m.encode()); o.flush()
    }

    private fun readMessage(): AdbProtocol.Message {
        val header = ByteArray(AdbProtocol.HEADER_LENGTH)
        readFully(header)
        val h = AdbProtocol.parseHeader(header)
        val payload = if (h.dataLength > 0) ByteArray(h.dataLength).also { readFully(it) } else ByteArray(0)
        return AdbProtocol.Message(h.command, h.arg0, h.arg1, payload)
    }

    private fun readFully(buf: ByteArray) {
        val i = input ?: throw IOException("not connected")
        var off = 0
        while (off < buf.size) {
            val r = i.read(buf, off, buf.size - off)
            if (r < 0) throw IOException("EOF")
            off += r
        }
    }

    /**
     * Shell-Protokoll v2: Folge von Paketen `[id:1][len:4 LE][data:len]`.
     * id 1 = stdout, 2 = stderr, 3 = exit (1 Byte Code).
     */
    private fun parseShellV2(bytes: ByteArray): ShellResult {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        var exit = 0
        var i = 0
        while (i + 5 <= bytes.size) {
            val id = bytes[i].toInt()
            val len = ByteBuffer.wrap(bytes, i + 1, 4).order(ByteOrder.LITTLE_ENDIAN).int
            i += 5
            if (len < 0 || i + len > bytes.size) break
            when (id) {
                1 -> out.write(bytes, i, len)
                2 -> err.write(bytes, i, len)
                3 -> if (len >= 1) exit = bytes[i].toInt() and 0xff
            }
            i += len
        }
        return ShellResult(exit, out.toByteArray().decodeToString().trim(), err.toByteArray().decodeToString().trim())
    }

    companion object {
        private const val TAG = "AdbClient"
    }
}
