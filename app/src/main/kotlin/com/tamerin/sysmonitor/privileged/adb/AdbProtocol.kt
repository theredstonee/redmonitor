package com.tamerin.sysmonitor.privileged.adb

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reines ADB-Wire-Protokoll (Message-Framing), ohne Android-/Netz-Bezug — damit ohne
 * Gerät testbar. Ein ADB-Paket ist ein 24-Byte-Header (little-endian) plus Payload:
 *
 * ```
 * struct message { u32 command; u32 arg0; u32 arg1; u32 data_length;
 *                  u32 data_crc32; u32 magic = command ^ 0xffffffff; }
 * ```
 *
 * Referenz: AOSP `system/core/adb/protocol.txt`. Nur Framing hier — TLS/Handshake/Auth
 * bauen darauf auf.
 */
object AdbProtocol {

    const val A_CNXN = 0x4e584e43
    const val A_OPEN = 0x4e45504f
    const val A_OKAY = 0x59414b4f
    const val A_CLSE = 0x45534c43
    const val A_WRTE = 0x45545257
    const val A_AUTH = 0x48545541
    const val A_STLS = 0x534c5453

    /** Protokoll-Version, die TLS/STLS unterstützt (Android 11+). */
    const val A_VERSION = 0x01000001
    const val A_STLS_VERSION = 0x01000001

    /** Max. Payload pro Paket bei moderner Version. */
    const val MAX_PAYLOAD = 0x100000

    const val AUTH_TYPE_TOKEN = 1
    const val AUTH_TYPE_SIGNATURE = 2
    const val AUTH_TYPE_RSA_PUBLIC = 3

    const val HEADER_LENGTH = 24

    data class Message(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val payload: ByteArray = ByteArray(0)
    ) {
        fun encode(): ByteArray {
            val buf = ByteBuffer.allocate(HEADER_LENGTH + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(command)
            buf.putInt(arg0)
            buf.putInt(arg1)
            buf.putInt(payload.size)
            buf.putInt(checksum(payload))
            buf.putInt(command.inv())
            buf.put(payload)
            return buf.array()
        }
    }

    /** ADB-Payload-Prüfsumme: einfache Summe aller Bytes (unsigned). */
    fun checksum(payload: ByteArray): Int {
        var sum = 0
        for (b in payload) sum += (b.toInt() and 0xff)
        return sum
    }

    fun connect(systemType: String = "host", banner: String = "features=shell_v2,cmd"): Message {
        // Identitäts-String, NUL-terminiert: "<type>:<serial>:<banner>\0"
        val ident = "$systemType::$banner\u0000".toByteArray(Charsets.UTF_8)
        return Message(A_CNXN, A_VERSION, MAX_PAYLOAD, ident)
    }

    fun stls(): Message = Message(A_STLS, A_STLS_VERSION, 0)

    fun open(localId: Int, destination: String): Message {
        val dst = "$destination\u0000".toByteArray(Charsets.UTF_8)
        return Message(A_OPEN, localId, 0, dst)
    }

    fun okay(localId: Int, remoteId: Int): Message = Message(A_OKAY, localId, remoteId)

    fun write(localId: Int, remoteId: Int, data: ByteArray): Message =
        Message(A_WRTE, localId, remoteId, data)

    fun close(localId: Int, remoteId: Int): Message = Message(A_CLSE, localId, remoteId)

    /** Header aus 24 Bytes lesen; Payload wird separat gemäß data_length nachgelesen. */
    data class Header(val command: Int, val arg0: Int, val arg1: Int, val dataLength: Int, val dataCrc: Int)

    fun parseHeader(bytes: ByteArray): Header {
        require(bytes.size >= HEADER_LENGTH) { "Header zu kurz" }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val command = buf.int
        val arg0 = buf.int
        val arg1 = buf.int
        val dataLength = buf.int
        val dataCrc = buf.int
        // magic (command ^ -1) übersprungen/optional prüfbar
        return Header(command, arg0, arg1, dataLength, dataCrc)
    }
}
