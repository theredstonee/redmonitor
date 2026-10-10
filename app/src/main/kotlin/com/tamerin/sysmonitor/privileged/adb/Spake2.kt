package com.tamerin.sysmonitor.privileged.adb

/**
 * SPAKE2 über Ed25519 — der Krypto-Kern des Wireless-Debugging-Pairings. `adbd` nutzt
 * exakt BoringSSLs `spake25519.c`: feste Punkte M/N auf edwards25519, Passwort = der
 * 6-stellige Pairing-Code, daraus ein gemeinsamer Schlüssel + Confirmation-MACs.
 *
 * ⚠️ DEVICE-VERIFY / NOCH NICHT FUNKTIONSFÄHIG:
 * Es gibt keine nicht-copyleft Java-Bibliothek für dieses exakte Verfahren. Die
 * edwards25519-Gruppenarithmetik mit den FESTEN M/N-Punkten sowie das exakte
 * Transcript-/Key-Schedule müssen 1:1 aus AOSP/BoringSSL portiert werden. Bis dahin
 * meldet [ed25519Available] = false; der Pairing-Client bricht dann sauber mit einer
 * erklärenden Meldung ab, statt falsche Kryptografie zu verwenden.
 *
 * Unabhängig davon funktionieren der tcpip-Weg und der Wireless-CONNECT mit einem
 * bereits vertrauten Key — nur das ERSTMALIGE In-App-Pairing hängt an diesem Port.
 */
class Spake2(
    private val role: Role,
    @Suppress("unused") private val password: ByteArray
) {
    enum class Role { CLIENT, SERVER }

    /** Unsere öffentliche SPAKE2-Nachricht (32-Byte edwards-Punkt). */
    fun generateMessage(): ByteArray =
        throw NotImplementedError("SPAKE2-ed25519-Kern: aus AOSP spake25519.c portieren (M/N, Transcript)")

    /** Peer-Nachricht verarbeiten → gemeinsames Schlüsselmaterial. */
    fun processMessage(@Suppress("unused") peerMessage: ByteArray): ByteArray =
        throw NotImplementedError("SPAKE2-ed25519-Kern: aus AOSP spake25519.c portieren (M/N, Transcript)")

    companion object {
        /** true, sobald der ed25519-Kern portiert ist. Bis dahin ist Pairing deaktiviert. */
        const val ed25519Available: Boolean = false

        // REQUIRED-FILL aus BoringSSL spake25519.c (je 32-Byte compressed edwards-Punkt):
        //   val M = byteArrayOf( ... )
        //   val N = byteArrayOf( ... )
    }
}
