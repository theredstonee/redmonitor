package com.tamerin.sysmonitor.privileged.adb

import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import org.conscrypt.Conscrypt

/**
 * TLS-1.3-Kontext für die ADB-Verbindungen (Pairing wie Connect). Nutzt Conscrypt
 * (gleiche TLS-Stack-Basis wie `adbd`) und präsentiert unser [AdbKey]-Client-Cert.
 *
 * Der Server-Cert von `adbd` ist selbstsigniert; die eigentliche Vertrauensbildung
 * passiert über den SPAKE2-/Key-Austausch (Trust-on-first-use), nicht über PKI —
 * daher akzeptiert der TrustManager hier bewusst jeden Server-Cert. Das ist der in
 * ADB-Clients übliche Ansatz und KEIN Loch: ohne gültigen Key kommt keine Session
 * zustande.
 */
object AdbSsl {

    fun sslContext(key: AdbKey): SSLContext {
        val ks = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("adb", key.keyPair.private, CHARS_EMPTY, arrayOf(key.certificate))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(ks, CHARS_EMPTY)
        }
        val ctx = SSLContext.getInstance("TLSv1.3", Conscrypt.newProvider())
        ctx.init(kmf.keyManagers, arrayOf(trustAll), SecureRandom())
        return ctx
    }

    private val CHARS_EMPTY = CharArray(0)

    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
