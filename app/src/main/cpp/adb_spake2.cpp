// Duenne JNI-Bruecke zu BoringSSLs SPAKE2 (curve25519.h) — exakt die Implementierung,
// gegen die adbd beim Wireless-Debugging-Pairing arbeitet. Hier passiert bewusst nur
// SPAKE2 selbst; TLS, HKDF und AES-GCM laufen auf der Kotlin-Seite (AdbPairingClient).

#include <jni.h>
#include <openssl/curve25519.h>
#include <openssl/mem.h>

#include <cstdint>
#include <cstring>

namespace {

// AOSP adb/pairing_auth/pairing_auth.cpp: die Namen gehen MIT Nullterminator ein
// (dort sizeof(kClientName)).
constexpr uint8_t kClientName[] = "adb pair client";
constexpr uint8_t kServerName[] = "adb pair server";

struct Spake2Session {
    SPAKE2_CTX *ctx = nullptr;
    uint8_t msg[SPAKE2_MAX_MSG_SIZE] = {};
    size_t msg_len = 0;
};

jbyteArray toJava(JNIEnv *env, const uint8_t *data, size_t len) {
    jbyteArray out = env->NewByteArray(static_cast<jsize>(len));
    if (out != nullptr) {
        env->SetByteArrayRegion(out, 0, static_cast<jsize>(len),
                                reinterpret_cast<const jbyte *>(data));
    }
    return out;
}

}  // namespace

extern "C" {

// Legt eine Sitzung an und erzeugt sofort unsere Nachricht. 0 = Fehler.
JNIEXPORT jlong JNICALL
Java_com_tamerin_sysmonitor_privileged_adb_Spake2Native_create(
        JNIEnv *env, jclass, jboolean isClient, jbyteArray password) {
    const bool client = isClient == JNI_TRUE;
    auto *session = new Spake2Session();
    session->ctx = SPAKE2_CTX_new(
            client ? spake2_role_alice : spake2_role_bob,
            client ? kClientName : kServerName, sizeof(kClientName),
            client ? kServerName : kClientName, sizeof(kServerName));
    if (session->ctx == nullptr) {
        delete session;
        return 0;
    }

    const jsize pwLen = env->GetArrayLength(password);
    jbyte *pw = env->GetByteArrayElements(password, nullptr);
    const int ok = SPAKE2_generate_msg(session->ctx, session->msg, &session->msg_len,
                                       sizeof(session->msg),
                                       reinterpret_cast<const uint8_t *>(pw),
                                       static_cast<size_t>(pwLen));
    env->ReleaseByteArrayElements(password, pw, JNI_ABORT);

    if (ok != 1) {
        SPAKE2_CTX_free(session->ctx);
        delete session;
        return 0;
    }
    return reinterpret_cast<jlong>(session);
}

JNIEXPORT jbyteArray JNICALL
Java_com_tamerin_sysmonitor_privileged_adb_Spake2Native_message(
        JNIEnv *env, jclass, jlong handle) {
    auto *session = reinterpret_cast<Spake2Session *>(handle);
    if (session == nullptr) return nullptr;
    return toJava(env, session->msg, session->msg_len);
}

// Verarbeitet die Peer-Nachricht → bis zu 64 Byte Schluesselmaterial, null bei Fehler
// (falscher Code faellt hier NICHT auf — das zeigt erst die AES-GCM-Entschluesselung).
JNIEXPORT jbyteArray JNICALL
Java_com_tamerin_sysmonitor_privileged_adb_Spake2Native_process(
        JNIEnv *env, jclass, jlong handle, jbyteArray theirMsg) {
    auto *session = reinterpret_cast<Spake2Session *>(handle);
    if (session == nullptr) return nullptr;

    uint8_t key[SPAKE2_MAX_KEY_SIZE];
    size_t keyLen = 0;
    const jsize msgLen = env->GetArrayLength(theirMsg);
    jbyte *msg = env->GetByteArrayElements(theirMsg, nullptr);
    const int ok = SPAKE2_process_msg(session->ctx, key, &keyLen, sizeof(key),
                                      reinterpret_cast<const uint8_t *>(msg),
                                      static_cast<size_t>(msgLen));
    env->ReleaseByteArrayElements(theirMsg, msg, JNI_ABORT);
    if (ok != 1) return nullptr;

    jbyteArray out = toJava(env, key, keyLen);
    OPENSSL_cleanse(key, sizeof(key));
    return out;
}

JNIEXPORT void JNICALL
Java_com_tamerin_sysmonitor_privileged_adb_Spake2Native_destroy(
        JNIEnv *, jclass, jlong handle) {
    auto *session = reinterpret_cast<Spake2Session *>(handle);
    if (session == nullptr) return;
    SPAKE2_CTX_free(session->ctx);
    delete session;
}

}  // extern "C"
