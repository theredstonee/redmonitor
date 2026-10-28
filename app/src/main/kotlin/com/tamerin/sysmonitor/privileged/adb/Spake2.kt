package com.tamerin.sysmonitor.privileged.adb

import android.util.Log
import java.io.Closeable
import java.io.IOException

/**
 * SPAKE2 über Ed25519 — der Krypto-Kern des Wireless-Debugging-Pairings. Läuft nativ über
 * BoringSSL (`libadbpair.so`, siehe `src/main/cpp/`), also exakt die Implementierung, die
 * auch `adbd` nutzt — keine Handportierung der Kurvenarithmetik.
 *
 * Eine Instanz = ein Protokolllauf. [message] ist sofort nach dem Konstruktor verfügbar,
 * [process] darf genau einmal aufgerufen werden; danach [close].
 */
class Spake2(role: Role, password: ByteArray) : Closeable {
    enum class Role { CLIENT, SERVER }

    private var handle: Long = Spake2Native.create(role == Role.CLIENT, password)
        .also { if (it == 0L) throw IOException("SPAKE2 init failed") }

    /** Unsere öffentliche SPAKE2-Nachricht (32 Byte). */
    val message: ByteArray = Spake2Native.message(handle)
        ?: throw IOException("SPAKE2 message unavailable")

    /** Peer-Nachricht verarbeiten → gemeinsames Schlüsselmaterial (64 Byte). */
    fun process(peerMessage: ByteArray): ByteArray {
        check(handle != 0L) { "SPAKE2 session closed" }
        return Spake2Native.process(handle, peerMessage)
            ?: throw IOException("SPAKE2 key exchange failed")
    }

    override fun close() {
        if (handle != 0L) {
            Spake2Native.destroy(handle)
            handle = 0L
        }
    }

    companion object {
        /** true, wenn die native Bibliothek für diese ABI geladen werden konnte. */
        val isAvailable: Boolean by lazy {
            runCatching { System.loadLibrary("adbpair") }
                .onFailure { Log.w("Spake2", "libadbpair not loadable", it) }
                .isSuccess
        }
    }
}

/** JNI-Einstiegspunkte, implementiert in `src/main/cpp/adb_spake2.cpp`. */
internal object Spake2Native {
    @JvmStatic external fun create(isClient: Boolean, password: ByteArray): Long
    @JvmStatic external fun message(handle: Long): ByteArray?
    @JvmStatic external fun process(handle: Long, theirMsg: ByteArray): ByteArray?
    @JvmStatic external fun destroy(handle: Long)
}
