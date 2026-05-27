// JNI surface for wn-steam-client. Phase 1 exposes:
//   - version()
//   - connection lifecycle (create/destroy/connect/disconnect/send)
//   - observer callbacks (onConnected/onDisconnected/onMessage)
//
// Auth methods land in Phase 2 in a sibling JNI file. We keep package paths
// in sync with com.winlator.cmod.feature.stores.steam.wnsteam.WnSteamClient
// and WnConnection on the Kotlin side.

#include <jni.h>
#include <android/log.h>

#include <memory>
#include <mutex>
#include <string>

#include "wn_steam/version.h"
#include "wn_steam/encrypted_channel.h"
#include "wn_steam/ws_connection.h"

#define WN_LOG_TAG "WnSteamJNI"
#define WN_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  WN_LOG_TAG, __VA_ARGS__)
#define WN_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, WN_LOG_TAG, __VA_ARGS__)

// Shared across all JNI translation units. wn_session_jni.cpp references
// this directly via `extern JavaVM* g_vm;`.
JavaVM* g_vm = nullptr;

namespace {

// Cached observer interface class and method IDs (only used by WnConnection
// here; WnSteamSession has its own table in wn_session_jni.cpp).
jclass   g_observer_cls = nullptr;   // global ref
jmethodID g_on_connected   = nullptr;
jmethodID g_on_disconnected = nullptr;
jmethodID g_on_message     = nullptr;

constexpr const char* kObserverClassName =
    "com/winlator/cmod/feature/stores/steam/wnsteam/WnConnectionObserver";

// Connection wrapper. Holds the EncryptedChannel + a global ref to the
// observer object. Allocated by nativeConnectionCreate; freed by Destroy.
struct WnConnectionHandle {
    std::unique_ptr<wn_steam::EncryptedChannel> channel;
    jobject  observer = nullptr;   // global ref
    std::mutex mu;                 // guards observer pointer mutations

    WnConnectionHandle() {
        auto ws = std::make_unique<wn_steam::WsConnection>();
        channel = std::make_unique<wn_steam::EncryptedChannel>(std::move(ws));
    }

    ~WnConnectionHandle() {
        if (observer) {
            JNIEnv* env = nullptr;
            bool attached = false;
            if (g_vm) {
                jint rc = g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
                if (rc == JNI_EDETACHED) {
                    g_vm->AttachCurrentThreadAsDaemon(&env, nullptr);
                    attached = true;
                }
                if (env) env->DeleteGlobalRef(observer);
                if (attached) g_vm->DetachCurrentThread();
            }
            observer = nullptr;
        }
    }
};

// Helper: attach the caller's native thread to the JVM if needed and
// return a JNIEnv*. We use AsDaemon so the attached thread does not block
// VM exit, and detach again when this scope leaves.
struct AttachScope {
    JNIEnv* env = nullptr;
    bool    attached = false;
    JavaVM* vm = nullptr;

    explicit AttachScope(JavaVM* vm) {
        this->vm = vm;
        if (!vm) return;
        jint rc = vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (rc == JNI_EDETACHED) {
            if (vm->AttachCurrentThreadAsDaemon(&env, nullptr) == JNI_OK) {
                attached = true;
            } else {
                env = nullptr;
            }
        }
    }
    ~AttachScope() {
        if (attached && vm) vm->DetachCurrentThread();
    }
};

inline WnConnectionHandle* from_handle(jlong h) noexcept {
    return reinterpret_cast<WnConnectionHandle*>(static_cast<uintptr_t>(h));
}

inline jlong to_handle(WnConnectionHandle* p) noexcept {
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(p));
}

void dispatch_on_connected(WnConnectionHandle* conn) {
    if (!conn) return;
    jobject observer_local = nullptr;
    {
        std::lock_guard<std::mutex> lk(conn->mu);
        observer_local = conn->observer;
    }
    if (!observer_local || !g_vm) return;
    AttachScope a(g_vm);
    if (!a.env) return;
    a.env->CallVoidMethod(observer_local, g_on_connected);
    if (a.env->ExceptionCheck()) a.env->ExceptionClear();
}

void dispatch_on_disconnected(WnConnectionHandle* conn,
                              wn_steam::ChannelDisconnectReason r,
                              const std::string& detail) {
    if (!conn) return;
    jobject observer_local = nullptr;
    {
        std::lock_guard<std::mutex> lk(conn->mu);
        observer_local = conn->observer;
    }
    if (!observer_local || !g_vm) return;
    AttachScope a(g_vm);
    if (!a.env) return;
    jstring jdetail = a.env->NewStringUTF(detail.c_str());
    a.env->CallVoidMethod(observer_local, g_on_disconnected,
                          static_cast<jint>(r), jdetail);
    if (a.env->ExceptionCheck()) a.env->ExceptionClear();
    if (jdetail) a.env->DeleteLocalRef(jdetail);
}

void dispatch_on_message(WnConnectionHandle* conn,
                         std::span<const uint8_t> bytes) {
    if (!conn) return;
    jobject observer_local = nullptr;
    {
        std::lock_guard<std::mutex> lk(conn->mu);
        observer_local = conn->observer;
    }
    if (!observer_local || !g_vm) return;
    AttachScope a(g_vm);
    if (!a.env) return;
    jbyteArray jarr = a.env->NewByteArray(static_cast<jsize>(bytes.size()));
    if (!jarr) {
        if (a.env->ExceptionCheck()) a.env->ExceptionClear();
        return;
    }
    a.env->SetByteArrayRegion(jarr, 0, static_cast<jsize>(bytes.size()),
                              reinterpret_cast<const jbyte*>(bytes.data()));
    a.env->CallVoidMethod(observer_local, g_on_message, jarr);
    if (a.env->ExceptionCheck()) a.env->ExceptionClear();
    a.env->DeleteLocalRef(jarr);
}

}  // namespace

// ---------------------------------------------------------------------------
// JNI_OnLoad — cache JavaVM* and observer method IDs.
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_vm = vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass local_cls = env->FindClass(kObserverClassName);
    if (!local_cls) {
        WN_LOGE("FindClass(%s) failed", kObserverClassName);
        if (env->ExceptionCheck()) env->ExceptionClear();
        return JNI_ERR;
    }
    g_observer_cls = static_cast<jclass>(env->NewGlobalRef(local_cls));
    env->DeleteLocalRef(local_cls);

    g_on_connected    = env->GetMethodID(g_observer_cls, "onConnected", "()V");
    g_on_disconnected = env->GetMethodID(g_observer_cls, "onDisconnected", "(ILjava/lang/String;)V");
    g_on_message      = env->GetMethodID(g_observer_cls, "onMessage",  "([B)V");
    if (!g_on_connected || !g_on_disconnected || !g_on_message) {
        WN_LOGE("GetMethodID failed for WnConnectionObserver methods");
        if (env->ExceptionCheck()) env->ExceptionClear();
        return JNI_ERR;
    }

    WN_LOGI("wn-steam-client v%s JNI loaded", wn_steam::version().string);
    return JNI_VERSION_1_6;
}

// ---------------------------------------------------------------------------
// WnSteamClient.nativeVersion
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamClient_nativeVersion(
        JNIEnv* env, jclass /*cls*/) {
    return env->NewStringUTF(wn_steam::version().string);
}

// ---------------------------------------------------------------------------
// WnConnection.native* — handle is a Long carrying a WnConnectionHandle*.
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnConnection_nativeCreate(
        JNIEnv* /*env*/, jclass /*cls*/) {
    auto* h = new (std::nothrow) WnConnectionHandle();
    if (!h) return 0;
    return to_handle(h);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnConnection_nativeDestroy(
        JNIEnv* /*env*/, jclass /*cls*/, jlong h) {
    auto* p = from_handle(h);
    if (!p) return;
    // The EncryptedChannel destructor calls disconnect() which joins the
    // transport worker thread, so by the time we delete `p` the worker is
    // gone and no further callbacks can fire.
    delete p;
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnConnection_nativeSetCaBundlePath(
        JNIEnv* env, jclass /*cls*/, jlong h, jstring jpath) {
    auto* conn = from_handle(h);
    if (!conn || !conn->channel) return;
    const char* path_c = jpath ? env->GetStringUTFChars(jpath, nullptr) : nullptr;
    std::string path = path_c ? std::string(path_c) : std::string();
    if (path_c) env->ReleaseStringUTFChars(jpath, path_c);

    conn->channel->set_ca_bundle_path(path);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnConnection_nativeSetObserver(
        JNIEnv* env, jclass /*cls*/, jlong h, jobject jobserver) {
    auto* conn = from_handle(h);
    if (!conn || !conn->channel) return;

    {
        std::lock_guard<std::mutex> lk(conn->mu);
        if (conn->observer) env->DeleteGlobalRef(conn->observer);
        conn->observer = jobserver ? env->NewGlobalRef(jobserver) : nullptr;
    }

    if (!jobserver) {
        // Detach all channel callbacks if observer cleared.
        conn->channel->set_on_connected({});
        conn->channel->set_on_disconnected({});
        conn->channel->set_on_message({});
        return;
    }

    // Wire channel callbacks to our dispatch_* helpers. We capture the raw
    // conn* (not a shared_ptr) because the JNI handle is the single owner;
    // Destroy() runs after disconnect joins the worker thread.
    auto* conn_raw = conn;
    conn->channel->set_on_connected([conn_raw]() {
        dispatch_on_connected(conn_raw);
    });
    conn->channel->set_on_disconnected(
        [conn_raw](wn_steam::ChannelDisconnectReason r, const std::string& d) {
            dispatch_on_disconnected(conn_raw, r, d);
        });
    conn->channel->set_on_message(
        [conn_raw](std::span<const uint8_t> bytes) {
            dispatch_on_message(conn_raw, bytes);
        });
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnConnection_nativeConnect(
        JNIEnv* env, jclass /*cls*/, jlong h, jstring jurl) {
    auto* conn = from_handle(h);
    if (!conn || !conn->channel) return JNI_FALSE;
    const char* url_c = jurl ? env->GetStringUTFChars(jurl, nullptr) : nullptr;
    std::string url = url_c ? std::string(url_c) : std::string();
    if (url_c) env->ReleaseStringUTFChars(jurl, url_c);
    if (url.empty()) return JNI_FALSE;
    return conn->channel->connect(url) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnConnection_nativeDisconnect(
        JNIEnv* /*env*/, jclass /*cls*/, jlong h) {
    auto* conn = from_handle(h);
    if (!conn || !conn->channel) return;
    conn->channel->disconnect();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnConnection_nativeSend(
        JNIEnv* env, jclass /*cls*/, jlong h, jbyteArray jdata) {
    auto* conn = from_handle(h);
    if (!conn || !conn->channel || !jdata) return JNI_FALSE;
    jsize n = env->GetArrayLength(jdata);
    if (n <= 0) return JNI_FALSE;

    std::vector<uint8_t> buf(static_cast<size_t>(n));
    env->GetByteArrayRegion(jdata, 0, n, reinterpret_cast<jbyte*>(buf.data()));
    return conn->channel->send(std::span<const uint8_t>(buf.data(), buf.size()))
        ? JNI_TRUE : JNI_FALSE;
}
