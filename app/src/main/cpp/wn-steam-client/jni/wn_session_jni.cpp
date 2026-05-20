// JNI bridge for WnSteamSession — the production-facing handle that wraps
// CMClient + CredentialsAuthSession + QrAuthSession. Kotlin
// SteamLoginViewModel drives this in Phase 2E.

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <cstdio>
#include <memory>
#include <mutex>
#include <string>
#include <future>
#include <thread>
#include <vector>

#include "wn_steam/auth_session.h"
#include "wn_steam/authenticator.h"
#include "wn_steam/cdn_client.h"
#include "wn_steam/cm_client.h"
#include "wn_steam/cm_server_list.h"
#include "wn_steam/depot_downloader.h"
#include "wn_steam/steam_directory.h"
#include "wn_steam/vdf.h"

#include <nlohmann/json.hpp>

#define WN_LOG_TAG "WnSteamSessJNI"
#define WN_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  WN_LOG_TAG, __VA_ARGS__)
#define WN_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, WN_LOG_TAG, __VA_ARGS__)

// g_vm is defined (no anonymous namespace) in wn_steam_jni.cpp, captured
// at JNI_OnLoad. Must stay outside our anonymous namespace below — if it
// were inside, the extern declaration would have internal linkage and
// would never resolve to the symbol in the other TU.
extern JavaVM* g_vm;

namespace {

// ---------------------------------------------------------------------------
// Cached class refs and method IDs for the Kotlin types this file touches.
// Initialized lazily by init_jni_session_globals() on first WnSteamSession
// creation. Globals here (rather than at JNI_OnLoad) so that adding this
// file doesn't churn the existing JNI_OnLoad symbol management.
// ---------------------------------------------------------------------------

std::once_flag g_session_init_once;
struct SessionGlobals {
    jclass    auth_result_cls    = nullptr;
    jmethodID auth_result_ctor   = nullptr;

    jclass    auth_callback_cls  = nullptr;   // WnAuthCallback
    jmethodID auth_callback_on   = nullptr;

    jclass    qr_callback_cls    = nullptr;   // WnQrCallback
    jmethodID qr_callback_on     = nullptr;

    jclass    state_observer_cls = nullptr;   // WnSteamStateObserver
    jmethodID state_obs_changed  = nullptr;
    jmethodID state_obs_message  = nullptr;

    jclass    authenticator_cls  = nullptr;   // WnAuthenticator
    jmethodID auth_dev_confirm   = nullptr;   // acceptDeviceConfirmation() : CompletableFuture<Boolean>
    jmethodID auth_dev_code      = nullptr;   // getDeviceCode(Boolean) : CompletableFuture<String>
    jmethodID auth_email_code    = nullptr;   // getEmailCode(String, Boolean) : CompletableFuture<String>

    jclass    prepare_cb_cls     = nullptr;   // WnPrepareAppCallback
    jmethodID prepare_cb_on      = nullptr;   // onPrepareResult(boolean, String)

    jclass    download_lis_cls   = nullptr;   // WnDownloadListener
    jmethodID download_progress  = nullptr;   // onProgress(IJJIIZ)V
    jmethodID download_complete  = nullptr;   // onComplete(ZLjava/lang/String;JII)V

    jclass    library_obs_cls    = nullptr;   // WnLibraryObserver
    jmethodID library_obs_changed = nullptr;  // onLibraryChanged()

    jclass    future_cls         = nullptr;   // java/util/concurrent/CompletableFuture
    jmethodID future_get         = nullptr;   // get() : Object

    jclass    boolean_cls        = nullptr;
    jmethodID boolean_value      = nullptr;   // booleanValue()
};
SessionGlobals g_sess;

jclass new_global_class(JNIEnv* env, const char* name) {
    jclass local = env->FindClass(name);
    if (!local) {
        WN_LOGE("FindClass(%s) failed", name);
        if (env->ExceptionCheck()) env->ExceptionClear();
        return nullptr;
    }
    jclass g = static_cast<jclass>(env->NewGlobalRef(local));
    env->DeleteLocalRef(local);
    return g;
}

void init_jni_session_globals_locked(JNIEnv* env) {
    constexpr const char* AUTH_RESULT =
        "com/winlator/cmod/feature/stores/steam/wnsteam/WnAuthResult";
    constexpr const char* AUTH_CB =
        "com/winlator/cmod/feature/stores/steam/wnsteam/WnAuthCallback";
    constexpr const char* QR_CB =
        "com/winlator/cmod/feature/stores/steam/wnsteam/WnQrCallback";
    constexpr const char* STATE_OBS =
        "com/winlator/cmod/feature/stores/steam/wnsteam/WnSteamStateObserver";
    constexpr const char* AUTHENTICATOR =
        "com/winlator/cmod/feature/stores/steam/wnsteam/WnAuthenticator";
    constexpr const char* PREPARE_CB =
        "com/winlator/cmod/feature/stores/steam/wnsteam/WnPrepareAppCallback";
    constexpr const char* LIBRARY_OBS =
        "com/winlator/cmod/feature/stores/steam/wnsteam/WnLibraryObserver";
    constexpr const char* DOWNLOAD_LIS =
        "com/winlator/cmod/feature/stores/steam/wnsteam/WnDownloadListener";

    g_sess.auth_result_cls = new_global_class(env, AUTH_RESULT);
    if (g_sess.auth_result_cls) {
        // ctor signature: (ZILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JZLjava/lang/String;)V
        g_sess.auth_result_ctor = env->GetMethodID(
            g_sess.auth_result_cls, "<init>",
            "(ZILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JZLjava/lang/String;)V");
    }

    g_sess.auth_callback_cls = new_global_class(env, AUTH_CB);
    if (g_sess.auth_callback_cls) {
        g_sess.auth_callback_on = env->GetMethodID(
            g_sess.auth_callback_cls, "onAuthResult",
            "(Lcom/winlator/cmod/feature/stores/steam/wnsteam/WnAuthResult;)V");
    }

    g_sess.qr_callback_cls = new_global_class(env, QR_CB);
    if (g_sess.qr_callback_cls) {
        g_sess.qr_callback_on = env->GetMethodID(
            g_sess.qr_callback_cls, "onQrChallengeUrl", "(Ljava/lang/String;)V");
    }

    g_sess.state_observer_cls = new_global_class(env, STATE_OBS);
    if (g_sess.state_observer_cls) {
        g_sess.state_obs_changed = env->GetMethodID(
            g_sess.state_observer_cls, "onStateChanged", "(I)V");
        g_sess.state_obs_message = env->GetMethodID(
            g_sess.state_observer_cls, "onClientMessage", "(II[B)V");
    }

    g_sess.authenticator_cls = new_global_class(env, AUTHENTICATOR);
    if (g_sess.authenticator_cls) {
        g_sess.auth_dev_confirm = env->GetMethodID(
            g_sess.authenticator_cls, "acceptDeviceConfirmation",
            "()Ljava/util/concurrent/CompletableFuture;");
        g_sess.auth_dev_code = env->GetMethodID(
            g_sess.authenticator_cls, "getDeviceCode",
            "(Z)Ljava/util/concurrent/CompletableFuture;");
        g_sess.auth_email_code = env->GetMethodID(
            g_sess.authenticator_cls, "getEmailCode",
            "(Ljava/lang/String;Z)Ljava/util/concurrent/CompletableFuture;");
    }

    g_sess.prepare_cb_cls = new_global_class(env, PREPARE_CB);
    if (g_sess.prepare_cb_cls) {
        g_sess.prepare_cb_on = env->GetMethodID(
            g_sess.prepare_cb_cls, "onPrepareResult", "(ZLjava/lang/String;)V");
    }

    g_sess.download_lis_cls = new_global_class(env, DOWNLOAD_LIS);
    if (g_sess.download_lis_cls) {
        g_sess.download_progress = env->GetMethodID(
            g_sess.download_lis_cls, "onProgress", "(IJJIIZ)V");
        g_sess.download_complete = env->GetMethodID(
            g_sess.download_lis_cls, "onComplete", "(ZLjava/lang/String;JII)V");
    }

    g_sess.library_obs_cls = new_global_class(env, LIBRARY_OBS);
    if (g_sess.library_obs_cls) {
        g_sess.library_obs_changed = env->GetMethodID(
            g_sess.library_obs_cls, "onLibraryChanged", "()V");
    }

    g_sess.future_cls = new_global_class(env, "java/util/concurrent/CompletableFuture");
    if (g_sess.future_cls) {
        g_sess.future_get = env->GetMethodID(g_sess.future_cls, "get", "()Ljava/lang/Object;");
    }
    g_sess.boolean_cls = new_global_class(env, "java/lang/Boolean");
    if (g_sess.boolean_cls) {
        g_sess.boolean_value = env->GetMethodID(g_sess.boolean_cls, "booleanValue", "()Z");
    }
}

void init_jni_session_globals(JNIEnv* env) {
    std::call_once(g_session_init_once, init_jni_session_globals_locked, env);
}

// AttachScope mirrors the one in wn_steam_jni.cpp. Duplicated here to keep
// the file self-contained.
struct AttachScope {
    JNIEnv* env = nullptr;
    bool    attached = false;
    explicit AttachScope(JavaVM* vm) {
        if (!vm) return;
        jint rc = vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (rc == JNI_EDETACHED) {
            vm->AttachCurrentThreadAsDaemon(&env, nullptr);
            attached = true;
        }
    }
};

// ---------------------------------------------------------------------------
// JNIAuthenticator — bridges native Authenticator to a Kotlin WnAuthenticator.
// All callbacks block in a detached worker thread so the channel-worker
// thread never waits on a Kotlin coroutine.
// ---------------------------------------------------------------------------

class JNIAuthenticator : public wn_steam::Authenticator {
public:
    explicit JNIAuthenticator(jobject global_auth) : global_auth_(global_auth) {}
    ~JNIAuthenticator() override {
        if (global_auth_ && g_vm) {
            AttachScope a(g_vm);
            if (a.env) a.env->DeleteGlobalRef(global_auth_);
        }
        global_auth_ = nullptr;
    }

    void accept_device_confirmation(std::function<void(bool)> cb) override {
        if (!global_auth_) { cb(false); return; }
        std::thread([this, cb = std::move(cb)]() {
            AttachScope a(g_vm);
            if (!a.env) { cb(false); return; }
            jobject future = a.env->CallObjectMethod(global_auth_, g_sess.auth_dev_confirm);
            if (a.env->ExceptionCheck()) {
                a.env->ExceptionClear();
                cb(false); return;
            }
            jobject result = future ? a.env->CallObjectMethod(future, g_sess.future_get) : nullptr;
            if (a.env->ExceptionCheck()) {
                a.env->ExceptionClear();
                if (future) a.env->DeleteLocalRef(future);
                cb(false); return;
            }
            bool ok = false;
            if (result) {
                ok = a.env->CallBooleanMethod(result, g_sess.boolean_value) == JNI_TRUE;
                a.env->DeleteLocalRef(result);
            }
            if (future) a.env->DeleteLocalRef(future);
            cb(ok);
        }).detach();
    }

    void get_device_code(bool prev,
                          std::function<void(std::string)> cb) override {
        if (!global_auth_) { cb({}); return; }
        std::thread([this, prev, cb = std::move(cb)]() {
            AttachScope a(g_vm);
            if (!a.env) { cb({}); return; }
            jobject future = a.env->CallObjectMethod(
                global_auth_, g_sess.auth_dev_code,
                prev ? JNI_TRUE : JNI_FALSE);
            if (a.env->ExceptionCheck()) { a.env->ExceptionClear(); cb({}); return; }
            jobject result = future ? a.env->CallObjectMethod(future, g_sess.future_get) : nullptr;
            if (a.env->ExceptionCheck()) { a.env->ExceptionClear(); cb({}); return; }
            std::string code;
            if (result) {
                const char* c = a.env->GetStringUTFChars(static_cast<jstring>(result), nullptr);
                if (c) {
                    code = c;
                    a.env->ReleaseStringUTFChars(static_cast<jstring>(result), c);
                }
                a.env->DeleteLocalRef(result);
            }
            if (future) a.env->DeleteLocalRef(future);
            cb(std::move(code));
        }).detach();
    }

    void get_email_code(std::string email, bool prev,
                         std::function<void(std::string)> cb) override {
        if (!global_auth_) { cb({}); return; }
        std::thread([this, email, prev, cb = std::move(cb)]() {
            AttachScope a(g_vm);
            if (!a.env) { cb({}); return; }
            jstring jemail = a.env->NewStringUTF(email.c_str());
            jobject future = a.env->CallObjectMethod(
                global_auth_, g_sess.auth_email_code,
                jemail, prev ? JNI_TRUE : JNI_FALSE);
            if (jemail) a.env->DeleteLocalRef(jemail);
            if (a.env->ExceptionCheck()) { a.env->ExceptionClear(); cb({}); return; }
            jobject result = future ? a.env->CallObjectMethod(future, g_sess.future_get) : nullptr;
            if (a.env->ExceptionCheck()) { a.env->ExceptionClear(); cb({}); return; }
            std::string code;
            if (result) {
                const char* c = a.env->GetStringUTFChars(static_cast<jstring>(result), nullptr);
                if (c) {
                    code = c;
                    a.env->ReleaseStringUTFChars(static_cast<jstring>(result), c);
                }
                a.env->DeleteLocalRef(result);
            }
            if (future) a.env->DeleteLocalRef(future);
            cb(std::move(code));
        }).detach();
    }

private:
    jobject global_auth_ = nullptr;
};

// ---------------------------------------------------------------------------
// SessionHandle — owns the CMClient + auth/QR session pair + observer refs.
// ---------------------------------------------------------------------------

struct SessionHandle {
    // shared_ptr — not unique_ptr — so detached poll threads in
    // CredentialsAuthSession / QrAuthSession can keep CMClient alive past
    // the handle's destruction. The previous unique_ptr design caused
    // use-after-free in nativeDestroy when an in-flight poll thread
    // dereferenced a dangling raw pointer.
    std::shared_ptr<wn_steam::CMClient>                       client;
    std::shared_ptr<wn_steam::CredentialsAuthSession>         creds_session;
    std::shared_ptr<wn_steam::QrAuthSession>                  qr_session;
    jobject                                                   state_observer = nullptr;  // global ref
    std::mutex                                                mu;
    // Cancel flag for an in-flight depot download (nativeDownloadApp).
    // shared_ptr so the detached download worker keeps it alive even if the
    // session handle is destroyed mid-download (mirrors `client`).
    std::shared_ptr<std::atomic<bool>>                        download_cancel =
        std::make_shared<std::atomic<bool>>(false);

    SessionHandle() {
        client = std::make_shared<wn_steam::CMClient>();
    }

    ~SessionHandle() {
        // ORDER MATTERS — see the destroyed-mutex SIGABRT we caught the
        // first time around.
        //
        // The state/message callbacks set on `client` capture a raw `this`
        // pointer and access `mu` + `state_observer`. Members destruct in
        // REVERSE declaration order, which means `mu` is destroyed BEFORE
        // `client`. If a transport-worker callback fires after `mu` is
        // gone but before `client` is gone (or as part of ~CMClient calling
        // disconnect() which re-fires the state callback), it locks a
        // destroyed mutex → FORTIFY SIGABRT.
        //
        // Fix: synchronously disconnect the client FIRST inside this body
        // (joins the transport worker, waiting for any in-flight callback),
        // then clear the callbacks so the eventual ~CMClient triggered by
        // shared_ptr refcount-drop fires no further callbacks. After that
        // the member destructors can run safely.

        // 1. Synchronously disconnect — joins the transport worker thread.
        //    After this, no transport callback can be in flight.
        if (client) client->disconnect();

        // 2. Clear callbacks so any later ~CMClient teardown (when the
        //    last poll-thread ref releases) emits no further state/message
        //    callbacks that would access our (about-to-be-destroyed)
        //    state_observer / mu members.
        if (client) {
            client->set_on_state({});
            client->set_on_client_message({});
        }

        // 3. Cancel auth sessions so detached poll threads see cancelled_
        //    and exit. shared_ptr<CMClient> in those threads keeps the
        //    client alive until they unwind.
        std::shared_ptr<wn_steam::CredentialsAuthSession> cs;
        std::shared_ptr<wn_steam::QrAuthSession>          qs;
        {
            std::lock_guard<std::mutex> lk(mu);
            cs = std::move(creds_session);
            qs = std::move(qr_session);
        }
        if (cs) cs->cancel();
        if (qs) qs->cancel();

        // 4. Drop the Kotlin state-observer ref. Step 1 already disconnected
        //    the client, so it can no longer be invoked. (The library
        //    observer owns its global ref via a shared_ptr deleter — see
        //    nativeSetLibraryObserver — so it needs no teardown here.)
        if (state_observer && g_vm) {
            AttachScope a(g_vm);
            if (a.env) a.env->DeleteGlobalRef(state_observer);
            state_observer = nullptr;
        }
    }
};

SessionHandle* from_handle(jlong h) noexcept {
    return reinterpret_cast<SessionHandle*>(static_cast<uintptr_t>(h));
}
jlong to_handle(SessionHandle* p) noexcept {
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(p));
}

// Build a Kotlin WnAuthResult from the C++ AuthSessionResult.
jobject build_auth_result(JNIEnv* env, const wn_steam::AuthSessionResult& r) {
    auto make_str = [&](const std::string& s) -> jstring {
        return env->NewStringUTF(s.c_str());
    };
    jstring jerr      = make_str(r.error_message);
    jstring jaccount  = make_str(r.account_name);
    jstring jrefresh  = make_str(r.refresh_token);
    jstring jaccess   = make_str(r.access_token);
    jstring jguard    = make_str(r.new_guard_data);
    jstring jagree    = make_str(r.agreement_session_url);

    // A NewStringUTF OOM leaves a pending exception; calling NewObject (or any
    // further JNI call) with one pending is undefined behaviour. Clear it —
    // the null jstrings simply become null fields on the result object.
    if (env->ExceptionCheck()) env->ExceptionClear();

    jobject obj = env->NewObject(
        g_sess.auth_result_cls, g_sess.auth_result_ctor,
        r.success ? JNI_TRUE : JNI_FALSE,
        static_cast<jint>(r.eresult),
        jerr, jaccount, jrefresh, jaccess, jguard,
        static_cast<jlong>(r.steamid),
        r.had_remote_interaction ? JNI_TRUE : JNI_FALSE,
        jagree);
    if (env->ExceptionCheck()) env->ExceptionClear();

    if (jerr)     env->DeleteLocalRef(jerr);
    if (jaccount) env->DeleteLocalRef(jaccount);
    if (jrefresh) env->DeleteLocalRef(jrefresh);
    if (jaccess)  env->DeleteLocalRef(jaccess);
    if (jguard)   env->DeleteLocalRef(jguard);
    if (jagree)   env->DeleteLocalRef(jagree);

    return obj;
}

}  // namespace

// ---------------------------------------------------------------------------
// JNI exports
// ---------------------------------------------------------------------------

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeCreate(
        JNIEnv* env, jclass /*cls*/) {
    init_jni_session_globals(env);
    auto* h = new (std::nothrow) SessionHandle();
    if (!h) return 0;
    return to_handle(h);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeDestroy(
        JNIEnv* /*env*/, jclass /*cls*/, jlong h) {
    auto* s = from_handle(h);
    if (!s) return;
    delete s;
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeSetCaBundlePath(
        JNIEnv* env, jclass /*cls*/, jlong h, jstring jpath) {
    auto* s = from_handle(h);
    if (!s || !s->client) return;
    const char* p = jpath ? env->GetStringUTFChars(jpath, nullptr) : nullptr;
    s->client->set_ca_bundle_path(p ? std::string(p) : std::string());
    if (p) env->ReleaseStringUTFChars(jpath, p);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeSetAutoPopulateLibrary(
        JNIEnv* /*env*/, jclass /*cls*/, jlong h, jboolean enabled) {
    auto* s = from_handle(h);
    if (!s || !s->client) return;
    s->client->set_auto_populate_library(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeSetStateObserver(
        JNIEnv* env, jclass /*cls*/, jlong h, jobject jobs) {
    auto* s = from_handle(h);
    if (!s || !s->client) return;
    {
        std::lock_guard<std::mutex> lk(s->mu);
        if (s->state_observer) env->DeleteGlobalRef(s->state_observer);
        s->state_observer = jobs ? env->NewGlobalRef(jobs) : nullptr;
    }

    auto* raw = s;
    if (!jobs) {
        s->client->set_on_state({});
        s->client->set_on_client_message({});
        return;
    }
    s->client->set_on_state([raw](wn_steam::ClientState st) {
        jobject obs = nullptr;
        { std::lock_guard<std::mutex> lk(raw->mu); obs = raw->state_observer; }
        if (!obs) return;
        AttachScope a(g_vm);
        if (!a.env) return;
        a.env->CallVoidMethod(obs, g_sess.state_obs_changed, static_cast<jint>(st));
        if (a.env->ExceptionCheck()) a.env->ExceptionClear();
    });
    s->client->set_on_client_message(
        [raw](wn_steam::EMsg emsg,
              const wn_steam::CMsgProtoBufHeader& hdr,
              std::span<const uint8_t> body) {
            jobject obs = nullptr;
            { std::lock_guard<std::mutex> lk(raw->mu); obs = raw->state_observer; }
            if (!obs) return;
            AttachScope a(g_vm);
            if (!a.env) return;
            jbyteArray jbody = a.env->NewByteArray(static_cast<jsize>(body.size()));
            if (!jbody) { if (a.env->ExceptionCheck()) a.env->ExceptionClear(); return; }
            a.env->SetByteArrayRegion(jbody, 0, static_cast<jsize>(body.size()),
                                      reinterpret_cast<const jbyte*>(body.data()));
            a.env->CallVoidMethod(obs, g_sess.state_obs_message,
                                  static_cast<jint>(emsg),
                                  static_cast<jint>(hdr.eresult),
                                  jbody);
            if (a.env->ExceptionCheck()) a.env->ExceptionClear();
            a.env->DeleteLocalRef(jbody);
        });
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeConnect(
        JNIEnv* env, jclass /*cls*/, jlong h, jstring jurl) {
    auto* s = from_handle(h);
    if (!s || !s->client || !jurl) return JNI_FALSE;
    const char* u = env->GetStringUTFChars(jurl, nullptr);
    bool ok = u ? s->client->connect(u) : false;
    if (u) env->ReleaseStringUTFChars(jurl, u);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeDisconnect(
        JNIEnv* /*env*/, jclass /*cls*/, jlong h) {
    auto* s = from_handle(h);
    if (!s || !s->client) return;
    s->client->disconnect();
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeStartLoginWithCredentials(
        JNIEnv* env, jclass /*cls*/, jlong h,
        jstring juser, jstring jpass, jboolean jpersistent,
        jobject jauthenticator, jobject jresult_cb) {
    auto* s = from_handle(h);
    if (!s || !s->client) return;

    const char* u = juser ? env->GetStringUTFChars(juser, nullptr) : nullptr;
    const char* p = jpass ? env->GetStringUTFChars(jpass, nullptr) : nullptr;
    wn_steam::CredentialsAuthSession::Config cfg;
    if (u) cfg.username = u;
    if (p) cfg.password = p;
    cfg.persistent_session = jpersistent == JNI_TRUE;
    if (u) env->ReleaseStringUTFChars(juser, u);
    if (p) env->ReleaseStringUTFChars(jpass, p);

    jobject auth_global = jauthenticator ? env->NewGlobalRef(jauthenticator) : nullptr;
    jobject cb_global   = jresult_cb     ? env->NewGlobalRef(jresult_cb)     : nullptr;

    auto authenticator = auth_global
        ? std::make_shared<JNIAuthenticator>(auth_global)
        : nullptr;

    auto session = std::make_shared<wn_steam::CredentialsAuthSession>(
        s->client, authenticator, std::move(cfg));
    {
        std::lock_guard<std::mutex> lk(s->mu);
        s->creds_session = session;
    }

    session->start([cb_global](wn_steam::AuthSessionResult r) {
        AttachScope a(g_vm);
        if (!a.env) return;
        if (cb_global) {
            jobject result = build_auth_result(a.env, r);
            if (result) {
                a.env->CallVoidMethod(cb_global, g_sess.auth_callback_on, result);
                if (a.env->ExceptionCheck()) a.env->ExceptionClear();
                a.env->DeleteLocalRef(result);
            }
            a.env->DeleteGlobalRef(cb_global);
        }
    });
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeStartLoginWithQr(
        JNIEnv* env, jclass /*cls*/, jlong h,
        jobject jqr_cb, jobject jresult_cb) {
    auto* s = from_handle(h);
    if (!s || !s->client) return;

    jobject qr_global = jqr_cb     ? env->NewGlobalRef(jqr_cb)     : nullptr;
    jobject cb_global = jresult_cb ? env->NewGlobalRef(jresult_cb) : nullptr;

    wn_steam::QrAuthSession::Config cfg;
    auto session = std::make_shared<wn_steam::QrAuthSession>(s->client, cfg);
    {
        std::lock_guard<std::mutex> lk(s->mu);
        s->qr_session = session;
    }

    session->start(
        [qr_global](std::string url) {
            AttachScope a(g_vm);
            if (!a.env || !qr_global) return;
            jstring jurl = a.env->NewStringUTF(url.c_str());
            a.env->CallVoidMethod(qr_global, g_sess.qr_callback_on, jurl);
            if (a.env->ExceptionCheck()) a.env->ExceptionClear();
            if (jurl) a.env->DeleteLocalRef(jurl);
        },
        [qr_global, cb_global](wn_steam::AuthSessionResult r) {
            AttachScope a(g_vm);
            if (!a.env) return;
            if (cb_global) {
                jobject result = build_auth_result(a.env, r);
                if (result) {
                    a.env->CallVoidMethod(cb_global, g_sess.auth_callback_on, result);
                    if (a.env->ExceptionCheck()) a.env->ExceptionClear();
                    a.env->DeleteLocalRef(result);
                }
                a.env->DeleteGlobalRef(cb_global);
            }
            if (qr_global) a.env->DeleteGlobalRef(qr_global);
        });
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeCancelLogin(
        JNIEnv* /*env*/, jclass /*cls*/, jlong h) {
    auto* s = from_handle(h);
    if (!s) return;
    std::shared_ptr<wn_steam::CredentialsAuthSession> cs;
    std::shared_ptr<wn_steam::QrAuthSession>          qs;
    {
        std::lock_guard<std::mutex> lk(s->mu);
        cs = s->creds_session;
        qs = s->qr_session;
        s->creds_session.reset();
        s->qr_session.reset();
    }
    if (cs) cs->cancel();
    if (qs) qs->cancel();
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeLogonWithRefreshToken(
        JNIEnv* env, jclass /*cls*/, jlong h, jstring jtoken, jstring jaccount, jlong jsteamid) {
    auto* s = from_handle(h);
    if (!s || !s->client || !jtoken) return JNI_FALSE;
    const char* t = env->GetStringUTFChars(jtoken, nullptr);
    const char* a = jaccount ? env->GetStringUTFChars(jaccount, nullptr) : nullptr;
    bool ok = false;
    if (t) {
        ok = s->client->logon_with_refresh_token(
            t,
            a ? std::string{a} : std::string{},
            static_cast<uint64_t>(jsteamid));
        env->ReleaseStringUTFChars(jtoken, t);
    }
    if (a) env->ReleaseStringUTFChars(jaccount, a);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativePrepareApp(
        JNIEnv* env, jclass /*cls*/, jlong h,
        jint app_id, jintArray dlc_array, jobject cb) {
    auto* s = from_handle(h);
    init_jni_session_globals(env);
    if (!s || !s->client) {
        if (cb && g_sess.prepare_cb_on) {
            jstring err = env->NewStringUTF("session closed");
            env->CallVoidMethod(cb, g_sess.prepare_cb_on, JNI_FALSE, err);
            // Clear a Kotlin-thrown exception so it doesn't propagate into an
            // unrelated frame when this JNI call returns.
            if (env->ExceptionCheck()) env->ExceptionClear();
            if (err) env->DeleteLocalRef(err);
        }
        return;
    }

    std::vector<uint32_t> dlc;
    if (dlc_array) {
        jsize n = env->GetArrayLength(dlc_array);
        jint* elems = env->GetIntArrayElements(dlc_array, nullptr);
        if (elems) {
            dlc.reserve(static_cast<size_t>(n));
            for (jsize i = 0; i < n; ++i) {
                dlc.push_back(static_cast<uint32_t>(elems[i]));
            }
            env->ReleaseIntArrayElements(dlc_array, elems, JNI_ABORT);
        }
    }

    // Promote the callback to a global ref so it outlives this JNI frame —
    // the C++ continuation fires on the channel's worker thread potentially
    // seconds from now.
    jobject cb_global = cb ? env->NewGlobalRef(cb) : nullptr;
    JavaVM* vm = nullptr;
    env->GetJavaVM(&vm);

    s->client->prepare_app(
        static_cast<uint32_t>(app_id), std::move(dlc),
        [vm, cb_global](bool ok, std::string error) {
            if (!cb_global) return;
            AttachScope scope(vm);
            if (!scope.env) return;
            jstring jerr = scope.env->NewStringUTF(error.c_str());
            scope.env->CallVoidMethod(cb_global, g_sess.prepare_cb_on,
                                       ok ? JNI_TRUE : JNI_FALSE, jerr);
            scope.env->DeleteLocalRef(jerr);
            scope.env->DeleteGlobalRef(cb_global);
        });
}

// Phase 5.5c — kick off a depot download. Runs the whole DepotDownloader on
// a detached worker thread; progress + completion arrive on the supplied
// WnDownloadListener (also on that worker thread — Kotlin must marshal).
JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeDownloadApp(
        JNIEnv* env, jclass /*cls*/, jlong h,
        jint app_id, jintArray depot_ids, jlongArray manifest_ids,
        jstring jbranch, jstring jinstall_dir, jboolean fresh,
        jstring jca_bundle, jint max_workers, jobject listener) {
    auto* s = from_handle(h);
    init_jni_session_globals(env);

    // Fire onComplete(false, msg, ...) for a pre-flight failure.
    auto fail_now = [&](const char* msg) {
        if (listener && g_sess.download_complete) {
            jstring jerr = env->NewStringUTF(msg);
            env->CallVoidMethod(listener, g_sess.download_complete,
                                JNI_FALSE, jerr, static_cast<jlong>(0),
                                static_cast<jint>(0), static_cast<jint>(0));
            // Clear a Kotlin-thrown exception so it doesn't propagate into an
            // unrelated frame when this JNI call returns.
            if (env->ExceptionCheck()) env->ExceptionClear();
            if (jerr) env->DeleteLocalRef(jerr);
        }
    };
    if (!s || !s->client) { fail_now("session closed"); return; }
    if (!listener)        { return; }

    std::vector<wn_steam::DepotSpec> depots;
    if (depot_ids && manifest_ids) {
        jsize n  = env->GetArrayLength(depot_ids);
        jsize nm = env->GetArrayLength(manifest_ids);
        if (n != nm) { fail_now("depot/manifest array length mismatch"); return; }
        jint*  dz = env->GetIntArrayElements(depot_ids, nullptr);
        jlong* mz = env->GetLongArrayElements(manifest_ids, nullptr);
        if (dz && mz) {
            depots.reserve(static_cast<size_t>(n));
            for (jsize i = 0; i < n; ++i) {
                wn_steam::DepotSpec d;
                d.depot_id    = static_cast<uint32_t>(dz[i]);
                d.manifest_id = static_cast<uint64_t>(mz[i]);
                depots.push_back(d);
            }
        }
        if (dz) env->ReleaseIntArrayElements(depot_ids, dz, JNI_ABORT);
        if (mz) env->ReleaseLongArrayElements(manifest_ids, mz, JNI_ABORT);
    }
    if (depots.empty()) { fail_now("no depots"); return; }

    auto jstr = [&](jstring js) -> std::string {
        if (!js) return {};
        const char* c = env->GetStringUTFChars(js, nullptr);
        std::string out = c ? c : std::string();
        if (c) env->ReleaseStringUTFChars(js, c);
        return out;
    };
    std::string branch      = jstr(jbranch);
    std::string install_dir = jstr(jinstall_dir);
    std::string ca_bundle   = jstr(jca_bundle);

    // shared_ptr copy keeps the CMClient alive even if the session handle
    // is destroyed mid-download (mirrors the auth-session design).
    std::shared_ptr<wn_steam::CMClient> client = s->client;
    // Reset + capture the per-session download cancel flag. The worker holds
    // its own shared_ptr so it survives a nativeDestroy mid-download.
    std::shared_ptr<std::atomic<bool>> cancel_flag = s->download_cancel;
    cancel_flag->store(false);
    jobject lis_global = env->NewGlobalRef(listener);
    JavaVM* vm = nullptr;
    env->GetJavaVM(&vm);
    const uint32_t appid    = static_cast<uint32_t>(app_id);
    const bool     is_fresh = (fresh == JNI_TRUE);
    // Worker count from the "Download Speed" setting. Guard against a
    // nonsense value; write_depot re-clamps to [1, 64] anyway.
    const unsigned workers =
        max_workers > 0 ? static_cast<unsigned>(max_workers) : 8u;

    std::thread([vm, lis_global, client, cancel_flag, depots = std::move(depots),
                 branch = std::move(branch), install_dir = std::move(install_dir),
                 ca_bundle = std::move(ca_bundle), appid, is_fresh, workers]() mutable {
        AttachScope scope(vm);
        if (!scope.env) {
            // Can't attach — nothing we can safely do; leak the global ref
            // rather than risk a crash. Should never happen in practice.
            return;
        }
        wn_steam::DepotDownloader dl(*client, ca_bundle);
        auto result = dl.download(
            appid, std::move(depots), branch, install_dir, is_fresh,
            [&scope, lis_global](const wn_steam::DepotDownloadProgress& p) {
                scope.env->CallVoidMethod(
                    lis_global, g_sess.download_progress,
                    static_cast<jint>(p.depot_id),
                    static_cast<jlong>(p.depot_done),
                    static_cast<jlong>(p.depot_total),
                    static_cast<jint>(p.depots_done),
                    static_cast<jint>(p.depots_total),
                    p.verifying ? JNI_TRUE : JNI_FALSE);
                if (scope.env->ExceptionCheck()) {
                    WN_LOGE("onProgress: Java listener threw — clearing");
                    scope.env->ExceptionClear();
                }
            },
            cancel_flag.get(), workers);

        jstring jerr = scope.env->NewStringUTF(result.error.c_str());
        scope.env->CallVoidMethod(
            lis_global, g_sess.download_complete,
            result.success ? JNI_TRUE : JNI_FALSE, jerr,
            static_cast<jlong>(result.bytes_written),
            static_cast<jint>(result.depots_completed),
            static_cast<jint>(result.depots_skipped));
        scope.env->DeleteLocalRef(jerr);
        scope.env->DeleteGlobalRef(lis_global);
    }).detach();
}

// Aborts the in-flight depot download started by nativeDownloadApp. Sets the
// session's cancel flag; the download worker polls it before each depot and
// each chunk fetch and unwinds promptly (returning a "cancelled" result).
// Safe to call at any time — a no-op when no download is running.
JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeCancelDownload(
        JNIEnv* /*env*/, jclass /*cls*/, jlong h) {
    auto* s = from_handle(h);
    if (!s) return;
    s->download_cancel->store(true);
}

// Returns the cached app ownership ticket as a byte[] (or null if not
// cached). Wine's libsteamclient.so bridge will call this on every
// SteamUser()->GetAppOwnershipTicket(appid) IPC request, so it MUST be
// cheap and non-blocking — no network round-trip here. Pre-warm via
// prepareApp() before launching the game.
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeStartWineBridge(
        JNIEnv* /*env*/, jclass /*cls*/, jlong h,
        jint steam3_port, jint client_svc_port) {
    auto* s = from_handle(h);
    if (!s || !s->client) return JNI_FALSE;
    wn_steam::WineBridge::Config cfg;
    if (steam3_port      > 0 && steam3_port      <= 0xFFFF) cfg.steam3_port     = static_cast<uint16_t>(steam3_port);
    if (client_svc_port  > 0 && client_svc_port  <= 0xFFFF) cfg.client_svc_port = static_cast<uint16_t>(client_svc_port);
    return s->client->wine_bridge().start(cfg) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeStopWineBridge(
        JNIEnv* /*env*/, jclass /*cls*/, jlong h) {
    auto* s = from_handle(h);
    if (!s || !s->client) return;
    s->client->wine_bridge().stop();
}

JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeWineBridgeLastError(
        JNIEnv* env, jclass /*cls*/, jlong h) {
    auto* s = from_handle(h);
    if (!s || !s->client) return env->NewStringUTF("");
    return env->NewStringUTF(s->client->wine_bridge().last_error().c_str());
}

JNIEXPORT jbyteArray JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetAppOwnershipTicket(
        JNIEnv* env, jclass /*cls*/, jlong h, jint app_id) {
    auto* s = from_handle(h);
    if (!s || !s->client) return nullptr;
    auto t = s->client->tickets().get(static_cast<uint32_t>(app_id));
    if (!t || t->ticket.empty()) return nullptr;
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(t->ticket.size()));
    if (!arr) return nullptr;
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(t->ticket.size()),
                             reinterpret_cast<const jbyte*>(t->ticket.data()));
    return arr;
}

// Blocking RequestEncryptedAppTicket. Returns the serialized
// EncryptedAppTicket sub-message as a byte[] (base64 of it is what
// Goldberg's configs.user.ini `ticket=` expects), or null on failure.
// The C++ job has its own 30s timeout that always fires the callback
// (real response or synthetic failure), so fut.get() cannot hang.
JNIEXPORT jbyteArray JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeRequestEncryptedAppTicket(
        JNIEnv* env, jclass /*cls*/, jlong h, jint app_id) {
    auto* s = from_handle(h);
    if (!s || !s->client) return nullptr;
    // Copy the shared_ptr so the CMClient outlives a concurrent close() /
    // nativeDestroy during the (up to ~30s) blocking request — same guard
    // nativeDownloadApp uses. A non-empty ticket is itself the success
    // signal (do not gate on eresult — some Steam responses carry the
    // EResult only in the message header).
    std::shared_ptr<wn_steam::CMClient> client = s->client;

    std::promise<std::vector<uint8_t>> p;
    auto fut = p.get_future();
    client->request_encrypted_app_ticket(
        static_cast<uint32_t>(app_id),
        [&p](std::optional<wn_steam::pb::CMsgClientRequestEncryptedAppTicketResponse> r) {
            if (r && !r->encrypted_app_ticket.empty()) {
                p.set_value(std::move(r->encrypted_app_ticket));
            } else {
                p.set_value({});
            }
        });
    std::vector<uint8_t> ticket = fut.get();
    if (ticket.empty()) return nullptr;

    jbyteArray arr = env->NewByteArray(static_cast<jsize>(ticket.size()));
    if (!arr) return nullptr;
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(ticket.size()),
                            reinterpret_cast<const jbyte*>(ticket.data()));
    return arr;
}

// Blocking CMsgClientGetUserStats. Returns the binary-VDF UserGameStatsSchema
// for the app as a byte[] (Kotlin's StatsAchievementsGenerator turns it into
// Goldberg's achievements.json + stats.json), or null when the app has no
// stats schema / on transport failure. Like nativeRequestEncryptedAppTicket,
// the C++ job carries its own 30s timeout so fut.get() cannot hang.
JNIEXPORT jbyteArray JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetUserStatsSchema(
        JNIEnv* env, jclass /*cls*/, jlong h, jint app_id) {
    auto* s = from_handle(h);
    if (!s || !s->client) return nullptr;
    // Copy the shared_ptr so the CMClient outlives a concurrent close() /
    // nativeDestroy during the (up to ~30s) blocking request.
    std::shared_ptr<wn_steam::CMClient> client = s->client;

    std::promise<std::vector<uint8_t>> p;
    auto fut = p.get_future();
    client->get_user_stats(
        static_cast<uint32_t>(app_id),
        [&p](std::optional<wn_steam::pb::CMsgClientGetUserStatsResponse> r) {
            if (r && !r->schema.empty()) {
                p.set_value(std::move(r->schema));
            } else {
                p.set_value({});
            }
        });
    std::vector<uint8_t> schema = fut.get();
    if (schema.empty()) return nullptr;

    jbyteArray arr = env->NewByteArray(static_cast<jsize>(schema.size()));
    if (!arr) return nullptr;
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(schema.size()),
                            reinterpret_cast<const jbyte*>(schema.data()));
    return arr;
}

// Blocking 2-step Steam Inventory item-def fetch:
//   1. Inventory.GetItemDefMeta#1 (CM unified service) -> digest
//   2. HTTPS GET IGameInventory/GetItemDefArchive/v1  -> raw item-def JSON
// Returns the raw archive bytes as a byte[] (Kotlin's InventoryItemsGenerator
// pivots it into Goldberg's steam_settings/items.json). null on transport
// failure, not logged on, or when the app exposes no item definitions.
// `jca_bundle` is the PEM trust-bundle path for the HTTPS GET. The CM job
// carries its own 30s timeout so fut.get() cannot hang.
JNIEXPORT jbyteArray JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetItemDefArchive(
        JNIEnv* env, jclass /*cls*/, jlong h, jint app_id, jstring jca_bundle) {
    auto* s = from_handle(h);
    if (!s || !s->client) return nullptr;
    // Copy the shared_ptr so the CMClient outlives a concurrent close().
    std::shared_ptr<wn_steam::CMClient> client = s->client;

    std::string ca_bundle;
    if (jca_bundle) {
        const char* p = env->GetStringUTFChars(jca_bundle, nullptr);
        if (p) { ca_bundle = p; env->ReleaseStringUTFChars(jca_bundle, p); }
    }

    // Step 1: Inventory.GetItemDefMeta#1 -> digest.
    std::promise<std::optional<std::string>> prom;
    auto fut = prom.get_future();
    client->inventory_get_item_def_meta(
        static_cast<uint32_t>(app_id),
        [&prom](std::optional<wn_steam::pb::CInventory_GetItemDefMeta_Response> r) {
            if (r && !r->digest.empty()) prom.set_value(r->digest);
            else prom.set_value(std::nullopt);
        });
    std::optional<std::string> digest = fut.get();
    if (!digest) return nullptr;  // not logged on / no inventory / failure

    // Step 2: download the item-def archive over HTTPS.
    wn_steam::CdnClient cdn(ca_bundle);
    auto body = cdn.fetch_item_def_archive(static_cast<uint32_t>(app_id), *digest);
    if (!body) return nullptr;

    jbyteArray arr = env->NewByteArray(static_cast<jsize>(body->size()));
    if (!arr) return nullptr;
    if (!body->empty()) {
        env->SetByteArrayRegion(arr, 0, static_cast<jsize>(body->size()),
                                reinterpret_cast<const jbyte*>(body->data()));
    }
    return arr;
}

// Minimal JSON string escaper — matches the hand-rolled style in
// wn_library_store.cpp (we do not pull nlohmann::json into the JNI layer).
static std::string cloud_json_escape(const std::string& s);
static std::string cloud_to_hex(const std::vector<uint8_t>& b);

// Blocking CMsgClientGetUserStats — full response as a JSON object string
// {"eresult","crcStats","schema":"<hex>","achievementBlocks":[{"achievementId",
// "unlockTimes":[...]}]}, or null on transport failure. Unlike
// nativeGetUserStatsSchema this also surfaces crc_stats + achievement_blocks,
// which storeAchievementUnlocks needs to write unlocks back. 30s job timeout.
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetUserStatsFull(
        JNIEnv* env, jclass /*cls*/, jlong h, jint app_id) {
    auto* s = from_handle(h);
    if (!s || !s->client) return nullptr;
    std::shared_ptr<wn_steam::CMClient> client = s->client;

    std::promise<std::string> p;
    auto fut = p.get_future();
    bool delivered = false;
    client->get_user_stats(
        static_cast<uint32_t>(app_id),
        [&p, &delivered](
                std::optional<wn_steam::pb::CMsgClientGetUserStatsResponse> r) {
            if (!r) { p.set_value({}); return; }
            std::string j = "{\"eresult\":";
            j += std::to_string(r->eresult);
            j += ",\"crcStats\":";
            j += std::to_string(r->crc_stats);
            j += ",\"schema\":\"";
            j += cloud_to_hex(r->schema);
            j += "\",\"achievementBlocks\":[";
            for (size_t i = 0; i < r->achievement_blocks.size(); ++i) {
                const auto& b = r->achievement_blocks[i];
                if (i) j += ',';
                j += "{\"achievementId\":";
                j += std::to_string(b.achievement_id);
                j += ",\"unlockTimes\":[";
                for (size_t k = 0; k < b.unlock_time.size(); ++k) {
                    if (k) j += ',';
                    j += std::to_string(b.unlock_time[k]);
                }
                j += "]}";
            }
            j += "]}";
            delivered = true;
            p.set_value(std::move(j));
        });
    std::string json = fut.get();
    if (!delivered) return nullptr;
    return env->NewStringUTF(json.c_str());
}

// Fire-and-forget CMsgClientStoreUserStats2 — write stat/achievement values
// back to Steam. statIds[i] / statValues[i] are paired; arrays must be the
// same length (mismatched lengths are dropped). No-op if not logged on.
JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeStoreUserStats(
        JNIEnv* env, jclass /*cls*/, jlong h, jint app_id, jlong steam_id,
        jint crc_stats, jintArray j_stat_ids, jintArray j_stat_values) {
    auto* s = from_handle(h);
    if (!s || !s->client || !j_stat_ids || !j_stat_values) return;
    std::shared_ptr<wn_steam::CMClient> client = s->client;

    jsize n = env->GetArrayLength(j_stat_ids);
    if (n != env->GetArrayLength(j_stat_values)) return;

    std::vector<std::pair<uint32_t, uint32_t>> stats;
    jint* ids  = env->GetIntArrayElements(j_stat_ids, nullptr);
    jint* vals = env->GetIntArrayElements(j_stat_values, nullptr);
    if (ids && vals) {
        stats.reserve(static_cast<size_t>(n));
        for (jsize i = 0; i < n; ++i) {
            stats.emplace_back(static_cast<uint32_t>(ids[i]),
                               static_cast<uint32_t>(vals[i]));
        }
    }
    if (ids)  env->ReleaseIntArrayElements(j_stat_ids, ids, JNI_ABORT);
    if (vals) env->ReleaseIntArrayElements(j_stat_values, vals, JNI_ABORT);

    client->store_user_stats(static_cast<uint32_t>(app_id),
                             static_cast<uint64_t>(steam_id),
                             static_cast<uint32_t>(crc_stats),
                             stats);
}

// Minimal JSON string escaper — matches the hand-rolled style in
// wn_library_store.cpp (we do not pull nlohmann::json into the JNI layer).
static std::string cloud_json_escape(const std::string& s) {
    std::string o;
    o.reserve(s.size() + 8);
    for (unsigned char c : s) {
        switch (c) {
            case '"':  o += "\\\""; break;
            case '\\': o += "\\\\"; break;
            case '\b': o += "\\b";  break;
            case '\f': o += "\\f";  break;
            case '\n': o += "\\n";  break;
            case '\r': o += "\\r";  break;
            case '\t': o += "\\t";  break;
            default:
                if (c < 0x20) {
                    char buf[8];
                    std::snprintf(buf, sizeof(buf), "\\u%04x", c);
                    o += buf;
                } else {
                    o += static_cast<char>(c);
                }
        }
    }
    return o;
}

static std::string cloud_to_hex(const std::vector<uint8_t>& b) {
    static const char* kHex = "0123456789abcdef";
    std::string o;
    o.reserve(b.size() * 2);
    for (uint8_t x : b) { o += kHex[x >> 4]; o += kHex[x & 0x0f]; }
    return o;
}

// Blocking PublishedFile.GetUserFiles#1 (type="mysubscriptions"). Walks every
// page and returns the caller's subscribed Steam Workshop items for `app_id` as
// a JSON array string — [{"publishedFileId","appId","title","fileName",
// "fileUrl","previewUrl","fileSizeBytes","hcontentFile","timeUpdated"}, ...].
// Kotlin parses it into the Workshop browser list. Returns "[]" when the
// account has no subscriptions, or null on transport failure / not logged on.
// Each page is a separate CM job with its own 30s timeout so fut.get() cannot
// hang; pagination is capped so a misbehaving server cannot loop forever.
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetSubscribedWorkshopItems(
        JNIEnv* env, jclass /*cls*/, jlong h, jint app_id) {
    auto* s = from_handle(h);
    if (!s || !s->client) return nullptr;
    // Copy the shared_ptr so the CMClient outlives a concurrent close().
    std::shared_ptr<wn_steam::CMClient> client = s->client;

    constexpr uint32_t kPageSize = 100;
    constexpr uint32_t kMaxPages = 50;

    std::vector<wn_steam::pb::PublishedFileDetails> all;
    uint32_t total = 0;  // latched from page 1 — a stable stop bound
    for (uint32_t page = 1; page <= kMaxPages; ++page) {
        std::promise<std::optional<wn_steam::pb::CPublishedFile_GetUserFiles_Response>> prom;
        auto fut = prom.get_future();
        // published_file_get_subscribed always invokes the callback — on
        // success, parse failure, or the job's 30s timeout — so fut.get()
        // cannot hang.
        client->published_file_get_subscribed(
            static_cast<uint32_t>(app_id), page, kPageSize,
            [&prom](std::optional<wn_steam::pb::CPublishedFile_GetUserFiles_Response> r) {
                prom.set_value(std::move(r));
            });
        auto resp = fut.get();
        // A failure mid-paging fails the whole load: returning the pages
        // collected so far would present a truncated list as complete.
        if (!resp) return nullptr;
        if (page == 1) total = resp->total;
        for (auto& d : resp->publishedfiledetails) all.push_back(std::move(d));
        if (resp->publishedfiledetails.empty() ||
            (total != 0 && all.size() >= total)) {
            break;
        }
    }

    std::string j = "[";
    for (size_t i = 0; i < all.size(); ++i) {
        const auto& d = all[i];
        if (i) j += ',';
        j += "{\"publishedFileId\":";
        j += std::to_string(d.publishedfileid);
        j += ",\"appId\":";
        j += std::to_string(d.consumer_appid != 0
                                ? d.consumer_appid
                                : static_cast<uint32_t>(app_id));
        j += ",\"title\":\"";
        j += cloud_json_escape(d.title);
        j += "\",\"fileName\":\"";
        j += cloud_json_escape(d.filename);
        j += "\",\"fileUrl\":\"";
        j += cloud_json_escape(d.file_url);
        j += "\",\"previewUrl\":\"";
        j += cloud_json_escape(d.preview_url);
        j += "\",\"fileSizeBytes\":";
        j += std::to_string(d.file_size);
        j += ",\"hcontentFile\":";
        j += std::to_string(d.hcontent_file);
        j += ",\"timeUpdated\":";
        j += std::to_string(d.time_updated);
        j += '}';
    }
    j += ']';
    return env->NewStringUTF(j.c_str());
}

// Blocking depot download of one Steam Workshop item's content. A workshop
// item's content lives in the consumer app's SteamPipe depot — the depot id
// is the consumer app id and the manifest id is the item's hcontent_file —
// so the existing DepotDownloader pipeline (manifest fetch, depot key, CDN,
// chunk writes) is reused unchanged. Returns the decompressed bytes written,
// or -1 on failure / not logged on. `install_dir` receives the item's files
// (flat layout, plus a .DepotDownloader resume marker). Blocking — the caller
// runs this on a background thread (never the CM network thread).
JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeDownloadWorkshopItem(
        JNIEnv* env, jclass /*cls*/, jlong h, jint app_id, jlong manifest_id,
        jstring jinstall_dir, jstring jca_bundle, jint max_workers) {
    auto* s = from_handle(h);
    if (!s || !s->client) return -1;
    // Copy the shared_ptr so the CMClient outlives a concurrent close().
    std::shared_ptr<wn_steam::CMClient> client = s->client;

    auto jstr = [&](jstring js) -> std::string {
        if (!js) return {};
        const char* c = env->GetStringUTFChars(js, nullptr);
        std::string out = c ? c : std::string();
        if (c) env->ReleaseStringUTFChars(js, c);
        return out;
    };
    const std::string install_dir = jstr(jinstall_dir);
    const std::string ca_bundle   = jstr(jca_bundle);
    if (install_dir.empty()) return -1;

    const unsigned workers =
        max_workers > 0 ? static_cast<unsigned>(max_workers) : 8u;

    wn_steam::DepotSpec spec;
    spec.depot_id    = static_cast<uint32_t>(app_id);          // workshop content depot
    spec.manifest_id = static_cast<uint64_t>(manifest_id);     // hcontent_file

    wn_steam::DepotDownloader dl(*client, ca_bundle);
    auto result = dl.download(
        static_cast<uint32_t>(app_id), {spec}, /*branch=*/"public",
        install_dir, /*fresh=*/true, /*progress=*/{}, /*cancel=*/nullptr,
        workers);
    if (!result.success) {
        WN_LOGE("workshop download: app %d manifest %llu failed: %s",
                app_id, static_cast<unsigned long long>(manifest_id),
                result.error.c_str());
        return -1;
    }
    WN_LOGI("workshop download: app %d manifest %llu ok — %llu bytes",
            app_id, static_cast<unsigned long long>(manifest_id),
            static_cast<unsigned long long>(result.bytes_written));
    return static_cast<jlong>(result.bytes_written);
}

// Blocking Cloud.GetAppFileChangelist#1. Returns the app's remote cloud-save
// file list as a JSON object string (currentChangeNumber, pathPrefixes,
// machineNames, files[]), or null on failure / not logged on. Like the other
// blocking calls the C++ job carries its own 30s timeout so fut.get() cannot
// hang. synced_change_number is fixed at 0 here — the restore path always
// wants the full list.
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetCloudFileList(
        JNIEnv* env, jclass /*cls*/, jlong h, jint app_id) {
    auto* s = from_handle(h);
    if (!s || !s->client) return nullptr;
    // Copy the shared_ptr so the CMClient outlives a concurrent close().
    std::shared_ptr<wn_steam::CMClient> client = s->client;

    std::promise<std::string> p;
    auto fut = p.get_future();
    client->cloud_get_app_file_changelist(
        static_cast<uint32_t>(app_id), /*synced_change_number=*/0,
        [&p](std::optional<wn_steam::pb::CCloud_GetAppFileChangelist_Response> r) {
            if (!r) { p.set_value({}); return; }
            std::string j = "{\"currentChangeNumber\":";
            j += std::to_string(r->current_change_number);
            j += ",\"pathPrefixes\":[";
            for (size_t i = 0; i < r->path_prefixes.size(); ++i) {
                if (i) j += ',';
                j += '"'; j += cloud_json_escape(r->path_prefixes[i]); j += '"';
            }
            j += "],\"machineNames\":[";
            for (size_t i = 0; i < r->machine_names.size(); ++i) {
                if (i) j += ',';
                j += '"'; j += cloud_json_escape(r->machine_names[i]); j += '"';
            }
            j += "],\"files\":[";
            for (size_t i = 0; i < r->files.size(); ++i) {
                const auto& f = r->files[i];
                if (i) j += ',';
                j += "{\"fileName\":\"";   j += cloud_json_escape(f.file_name); j += '"';
                j += ",\"sha\":\"";        j += cloud_to_hex(f.sha_file);       j += '"';
                j += ",\"timestamp\":";    j += std::to_string(f.time_stamp);
                j += ",\"size\":";         j += std::to_string(f.raw_file_size);
                j += ",\"persistState\":"; j += std::to_string(f.persist_state);
                j += ",\"pathPrefixIndex\":";  j += std::to_string(f.path_prefix_index);
                j += ",\"machineNameIndex\":"; j += std::to_string(f.machine_name_index);
                j += '}';
            }
            j += "]}";
            p.set_value(std::move(j));
        });
    std::string json = fut.get();
    if (json.empty()) return nullptr;
    return env->NewStringUTF(json.c_str());
}

// Blocking Cloud.ClientFileDownload#1. Returns a JSON object string with the
// HTTP(S) URL + headers needed to fetch the cloud file's body
// (urlHost/urlPath/useHttps/headers[]/encrypted/rawFileSize), or null on
// failure. The caller does the actual HTTP GET. 30s job timeout — no hang.
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetCloudDownloadInfo(
        JNIEnv* env, jclass /*cls*/, jlong h, jint app_id, jstring j_filename) {
    auto* s = from_handle(h);
    if (!s || !s->client || !j_filename) return nullptr;
    std::shared_ptr<wn_steam::CMClient> client = s->client;

    const char* fn_c = env->GetStringUTFChars(j_filename, nullptr);
    if (!fn_c) return nullptr;
    std::string filename(fn_c);
    env->ReleaseStringUTFChars(j_filename, fn_c);

    std::promise<std::string> p;
    auto fut = p.get_future();
    client->cloud_get_file_download_info(
        static_cast<uint32_t>(app_id), filename,
        [&p](std::optional<wn_steam::pb::CCloud_ClientFileDownload_Response> r) {
            if (!r || r->url_host.empty()) { p.set_value({}); return; }
            std::string j = "{\"fileSize\":";
            j += std::to_string(r->file_size);
            j += ",\"rawFileSize\":";  j += std::to_string(r->raw_file_size);
            j += ",\"sha\":\"";        j += cloud_to_hex(r->sha_file); j += '"';
            j += ",\"timestamp\":";    j += std::to_string(r->time_stamp);
            j += ",\"urlHost\":\"";    j += cloud_json_escape(r->url_host); j += '"';
            j += ",\"urlPath\":\"";    j += cloud_json_escape(r->url_path); j += '"';
            j += ",\"useHttps\":";     j += (r->use_https ? "true" : "false");
            j += ",\"encrypted\":";    j += (r->encrypted ? "true" : "false");
            j += ",\"headers\":[";
            for (size_t i = 0; i < r->request_headers.size(); ++i) {
                if (i) j += ',';
                j += "{\"name\":\"";  j += cloud_json_escape(r->request_headers[i].name);  j += '"';
                j += ",\"value\":\""; j += cloud_json_escape(r->request_headers[i].value); j += "\"}";
            }
            j += "]}";
            p.set_value(std::move(j));
        });
    std::string json = fut.get();
    if (json.empty()) return nullptr;
    return env->NewStringUTF(json.c_str());
}

// jstring → std::string (empty for null). Frees the UTF chars.
static std::string cloud_jstr(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    if (!c) return {};
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

// Lowercase hex string → bytes. Returns empty on odd length / bad digit.
static std::vector<uint8_t> cloud_hex_to_bytes(const std::string& hex) {
    if (hex.size() % 2 != 0) return {};
    auto nyb = [](char c) -> int {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    };
    std::vector<uint8_t> out;
    out.reserve(hex.size() / 2);
    for (size_t i = 0; i < hex.size(); i += 2) {
        int hi = nyb(hex[i]), lo = nyb(hex[i + 1]);
        if (hi < 0 || lo < 0) return {};
        out.push_back(static_cast<uint8_t>((hi << 4) | lo));
    }
    return out;
}

// Splits a newline-joined string into non-empty items.
static std::vector<std::string> cloud_split_lines(const std::string& joined) {
    std::vector<std::string> out;
    size_t start = 0;
    while (start <= joined.size()) {
        size_t nl = joined.find('\n', start);
        std::string item = joined.substr(start, nl == std::string::npos ? std::string::npos : nl - start);
        if (!item.empty()) out.push_back(item);
        if (nl == std::string::npos) break;
        start = nl + 1;
    }
    return out;
}

// Cloud.BeginAppUploadBatch#1. files / filesToDelete = newline-joined remote
// names. Returns JSON {"batchId":N,"appChangeNumber":N} or null on failure.
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeCloudBeginUploadBatch(
        JNIEnv* env, jclass /*cls*/, jlong h, jint app_id, jstring j_files,
        jstring j_files_to_delete, jlong client_id) {
    auto* s = from_handle(h);
    if (!s || !s->client) return nullptr;
    std::shared_ptr<wn_steam::CMClient> client = s->client;

    std::vector<std::string> files        = cloud_split_lines(cloud_jstr(env, j_files));
    std::vector<std::string> files_delete = cloud_split_lines(cloud_jstr(env, j_files_to_delete));

    std::promise<std::string> p;
    auto fut = p.get_future();
    client->cloud_begin_app_upload_batch(
        static_cast<uint32_t>(app_id), /*machine_name=*/"", std::move(files),
        std::move(files_delete), static_cast<uint64_t>(client_id),
        [&p](std::optional<wn_steam::pb::CCloud_BeginAppUploadBatch_Response> r) {
            if (!r) { p.set_value({}); return; }
            std::string j = "{\"batchId\":";
            j += std::to_string(r->batch_id);
            j += ",\"appChangeNumber\":";
            j += std::to_string(r->app_change_number);
            j += '}';
            p.set_value(std::move(j));
        });
    std::string json = fut.get();
    if (json.empty()) return nullptr;
    return env->NewStringUTF(json.c_str());
}

// Cloud.ClientBeginFileUpload#1. file_sha is a lowercase hex string. Returns
// JSON {"encryptFile":bool,"blocks":[{urlHost,urlPath,useHttps,httpMethod,
// blockOffset,blockLength,mayParallelize,headers:[{name,value}]}]} or null.
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeCloudBeginFileUpload(
        JNIEnv* env, jclass /*cls*/, jlong h, jint app_id, jstring j_filename,
        jint file_size, jint raw_file_size, jstring j_sha, jlong time_stamp,
        jlong batch_id) {
    auto* s = from_handle(h);
    if (!s || !s->client || !j_filename) return nullptr;
    std::shared_ptr<wn_steam::CMClient> client = s->client;

    std::string filename = cloud_jstr(env, j_filename);
    std::vector<uint8_t> sha = cloud_hex_to_bytes(cloud_jstr(env, j_sha));

    std::promise<std::string> p;
    auto fut = p.get_future();
    client->cloud_begin_file_upload(
        static_cast<uint32_t>(app_id), filename,
        static_cast<uint32_t>(file_size), static_cast<uint32_t>(raw_file_size),
        std::move(sha), static_cast<uint64_t>(time_stamp),
        static_cast<uint64_t>(batch_id),
        [&p](std::optional<wn_steam::pb::CCloud_ClientBeginFileUpload_Response> r) {
            if (!r) { p.set_value({}); return; }
            std::string j = "{\"encryptFile\":";
            j += (r->encrypt_file ? "true" : "false");
            j += ",\"blocks\":[";
            for (size_t i = 0; i < r->block_requests.size(); ++i) {
                const auto& b = r->block_requests[i];
                if (i) j += ',';
                j += "{\"urlHost\":\"";  j += cloud_json_escape(b.url_host); j += '"';
                j += ",\"urlPath\":\"";  j += cloud_json_escape(b.url_path); j += '"';
                j += ",\"useHttps\":";   j += (b.use_https ? "true" : "false");
                j += ",\"httpMethod\":"; j += std::to_string(b.http_method);
                j += ",\"blockOffset\":";j += std::to_string(b.block_offset);
                j += ",\"blockLength\":";j += std::to_string(b.block_length);
                j += ",\"mayParallelize\":"; j += (b.may_parallelize ? "true" : "false");
                j += ",\"headers\":[";
                for (size_t k = 0; k < b.request_headers.size(); ++k) {
                    if (k) j += ',';
                    j += "{\"name\":\"";  j += cloud_json_escape(b.request_headers[k].name);  j += '"';
                    j += ",\"value\":\""; j += cloud_json_escape(b.request_headers[k].value); j += "\"}";
                }
                j += "]}";
            }
            j += "]}";
            p.set_value(std::move(j));
        });
    std::string json = fut.get();
    if (json.empty()) return nullptr;
    return env->NewStringUTF(json.c_str());
}

// Cloud.ClientCommitFileUpload#1 — returns true if the server committed.
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeCloudCommitFileUpload(
        JNIEnv* env, jclass /*cls*/, jlong h, jboolean transfer_succeeded,
        jint app_id, jstring j_sha, jstring j_filename) {
    auto* s = from_handle(h);
    if (!s || !s->client || !j_filename) return JNI_FALSE;
    std::shared_ptr<wn_steam::CMClient> client = s->client;

    std::string filename = cloud_jstr(env, j_filename);
    std::vector<uint8_t> sha = cloud_hex_to_bytes(cloud_jstr(env, j_sha));

    std::promise<bool> p;
    auto fut = p.get_future();
    client->cloud_commit_file_upload(
        transfer_succeeded == JNI_TRUE, static_cast<uint32_t>(app_id),
        std::move(sha), filename,
        [&p](std::optional<wn_steam::pb::CCloud_ClientCommitFileUpload_Response> r) {
            p.set_value(r && r->file_committed);
        });
    return fut.get() ? JNI_TRUE : JNI_FALSE;
}

// Cloud.CompleteAppUploadBatchBlocking#1 — returns true on success.
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeCloudCompleteUploadBatch(
        JNIEnv* /*env*/, jclass /*cls*/, jlong h, jint app_id, jlong batch_id,
        jint batch_eresult) {
    auto* s = from_handle(h);
    if (!s || !s->client) return JNI_FALSE;
    std::shared_ptr<wn_steam::CMClient> client = s->client;

    std::promise<bool> p;
    auto fut = p.get_future();
    client->cloud_complete_app_upload_batch(
        static_cast<uint32_t>(app_id), static_cast<uint64_t>(batch_id),
        static_cast<uint32_t>(batch_eresult),
        [&p](bool ok) { p.set_value(ok); });
    return fut.get() ? JNI_TRUE : JNI_FALSE;
}

// Blocking PICS changes-since poll. Returns JSON
// {"currentChangeNumber":N,"forceFullUpdate":bool,
//  "apps":[{"appid","changeNumber","needsToken"}],"packages":[...]}
// or null on failure. since_change_number is a jlong because PICS global
// change numbers can exceed the signed-32-bit range.
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetPicsChangesSince(
        JNIEnv* env, jclass /*cls*/, jlong h, jlong since_change_number) {
    auto* s = from_handle(h);
    if (!s || !s->client) return nullptr;
    std::shared_ptr<wn_steam::CMClient> client = s->client;

    std::promise<std::string> p;
    auto fut = p.get_future();
    client->pics_get_changes_since(
        static_cast<uint32_t>(since_change_number),
        [&p](std::optional<wn_steam::pb::CMsgClientPICSChangesSinceResponse> r) {
            if (!r) { p.set_value({}); return; }
            std::string j = "{\"currentChangeNumber\":";
            j += std::to_string(r->current_change_number);
            j += ",\"forceFullUpdate\":";
            j += (r->force_full_update ? "true" : "false");
            j += ",\"apps\":[";
            for (size_t i = 0; i < r->app_changes.size(); ++i) {
                const auto& a = r->app_changes[i];
                if (i) j += ',';
                j += "{\"appid\":";       j += std::to_string(a.appid);
                j += ",\"changeNumber\":";j += std::to_string(a.change_number);
                j += ",\"needsToken\":";  j += (a.needs_token ? "true" : "false");
                j += '}';
            }
            j += "],\"packages\":[";
            for (size_t i = 0; i < r->package_changes.size(); ++i) {
                const auto& pc = r->package_changes[i];
                if (i) j += ',';
                j += "{\"packageid\":";   j += std::to_string(pc.packageid);
                j += ",\"changeNumber\":";j += std::to_string(pc.change_number);
                j += ",\"needsToken\":";  j += (pc.needs_token ? "true" : "false");
                j += '}';
            }
            j += "]}";
            p.set_value(std::move(j));
        });
    std::string json = fut.get();
    if (json.empty()) return nullptr;
    return env->NewStringUTF(json.c_str());
}

// Serializes a parsed VDF KVNode tree to JSON: subobjects → JSON objects,
// leaves → JSON strings (every value stringified — Kotlin's WnKeyValue
// coerces). Used to hand a PICS appinfo tree to the Kotlin decoder.
static void kvnode_to_json(const wn_steam::vdf::KVNode& node, std::string& out) {
    if (node.is_object()) {
        out += '{';
        bool first = true;
        for (const auto& child : node.children()) {
            if (!child) continue;
            if (!first) out += ',';
            first = false;
            out += '"';
            out += cloud_json_escape(child->name());
            out += "\":";
            kvnode_to_json(*child, out);
        }
        out += '}';
    } else {
        out += '"';
        out += cloud_json_escape(node.as_string());
        out += '"';
    }
}

// Blocking PICS product-info fetch for one app. The app `buffer` is VDF
// (text for modern appinfo, binary for legacy) — vdf::parse_auto handles
// both; we serialize the parsed appinfo tree to JSON. Returns
// {"changeNumber":N,"appinfo":{...}} or null when the app is unknown /
// needs an access token we weren't given / on failure. access_token is 0
// for the common public-appinfo case. Kotlin's WnKeyValue.fromJsonObject
// turns the "appinfo" object into a SteamApp.
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetPicsAppInfo(
        JNIEnv* env, jclass /*cls*/, jlong h, jint app_id, jlong access_token) {
    auto* s = from_handle(h);
    if (!s || !s->client) return nullptr;
    std::shared_ptr<wn_steam::CMClient> client = s->client;

    wn_steam::pb::PicsAppInfoReq appreq;
    appreq.appid        = static_cast<uint32_t>(app_id);
    appreq.access_token = static_cast<uint64_t>(access_token);

    std::promise<std::string> p;
    auto fut = p.get_future();
    client->pics_get_product_info(
        /*packages=*/{}, /*apps=*/{appreq}, /*meta_data_only=*/false,
        [&p, app_id](std::optional<wn_steam::pb::CMsgClientPICSProductInfoResponse> r) {
            if (!r) { p.set_value({}); return; }
            for (const auto& a : r->apps) {
                if (a.appid != static_cast<uint32_t>(app_id)) continue;
                if (a.buffer.empty()) { p.set_value({}); return; }
                auto root = wn_steam::vdf::parse_auto(a.buffer);
                if (!root) { p.set_value({}); return; }
                // Modern appinfo's root node IS "appinfo"; legacy binary form
                // wraps it under the appid. Handle both (mirrors WnLibraryStore).
                const wn_steam::vdf::KVNode* appinfo =
                    (root->name() == "appinfo") ? root.get() : root->child("appinfo");
                if (!appinfo) appinfo = root.get();
                std::string j = "{\"changeNumber\":";
                j += std::to_string(a.change_number);
                j += ",\"appinfo\":";
                kvnode_to_json(*appinfo, j);
                j += '}';
                p.set_value(std::move(j));
                return;
            }
            p.set_value({});
        });
    std::string json = fut.get();
    if (json.empty()) return nullptr;
    return env->NewStringUTF(json.c_str());
}

// Newline-joined string → uint32 / uint64 vectors (reuses cloud_split_lines).
static std::vector<uint32_t> jni_lines_u32(JNIEnv* env, jstring s) {
    std::vector<uint32_t> out;
    for (const auto& tok : cloud_split_lines(cloud_jstr(env, s))) {
        try { out.push_back(static_cast<uint32_t>(std::stoul(tok))); }
        catch (...) { /* skip bad token */ }
    }
    return out;
}
static std::vector<uint64_t> jni_lines_u64(JNIEnv* env, jstring s) {
    std::vector<uint64_t> out;
    for (const auto& tok : cloud_split_lines(cloud_jstr(env, s))) {
        try { out.push_back(std::stoull(tok)); }
        catch (...) { /* skip bad token */ }
    }
    return out;
}

// Blocking PICS access-token request. appIds/pkgIds are newline-joined.
// Returns JSON {"appTokens":{"<id>":"<token>"},"packageTokens":{...}} (tokens
// are uint64 → emitted as strings), or null on failure.
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetPicsAccessTokens(
        JNIEnv* env, jclass /*cls*/, jlong h, jstring j_app_ids, jstring j_pkg_ids) {
    auto* s = from_handle(h);
    if (!s || !s->client) return nullptr;
    std::shared_ptr<wn_steam::CMClient> client = s->client;
    std::vector<uint32_t> appids = jni_lines_u32(env, j_app_ids);
    std::vector<uint32_t> pkgids = jni_lines_u32(env, j_pkg_ids);

    std::promise<std::string> p;
    auto fut = p.get_future();
    client->pics_get_access_tokens(
        pkgids, appids,
        [&p](std::optional<wn_steam::pb::CMsgClientPICSAccessTokenResponse> r) {
            if (!r) { p.set_value({}); return; }
            std::string j = "{\"appTokens\":{";
            for (size_t i = 0; i < r->app_access_tokens.size(); ++i) {
                if (i) j += ',';
                const auto& t = r->app_access_tokens[i];
                j += '"'; j += std::to_string(t.appid); j += "\":\"";
                j += std::to_string(t.access_token); j += '"';
            }
            j += "},\"packageTokens\":{";
            for (size_t i = 0; i < r->package_access_tokens.size(); ++i) {
                if (i) j += ',';
                const auto& t = r->package_access_tokens[i];
                j += '"'; j += std::to_string(t.packageid); j += "\":\"";
                j += std::to_string(t.access_token); j += '"';
            }
            j += "}}";
            p.set_value(std::move(j));
        });
    std::string json = fut.get();
    if (json.empty()) return nullptr;
    return env->NewStringUTF(json.c_str());
}

// Blocking batch PICS app product-info. appIds + parallel appTokens are
// newline-joined. Returns a JSON array
// [{"appid":N,"changeNumber":N,"appinfo":{...}}, ...] or null on failure.
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetPicsAppProductInfo(
        JNIEnv* env, jclass /*cls*/, jlong h, jstring j_app_ids, jstring j_tokens) {
    auto* s = from_handle(h);
    if (!s || !s->client) return nullptr;
    std::shared_ptr<wn_steam::CMClient> client = s->client;
    std::vector<uint32_t> appids = jni_lines_u32(env, j_app_ids);
    std::vector<uint64_t> tokens = jni_lines_u64(env, j_tokens);

    std::vector<wn_steam::pb::PicsAppInfoReq> apps;
    apps.reserve(appids.size());
    for (size_t i = 0; i < appids.size(); ++i) {
        wn_steam::pb::PicsAppInfoReq req;
        req.appid        = appids[i];
        req.access_token = (i < tokens.size()) ? tokens[i] : 0;
        apps.push_back(req);
    }

    std::promise<std::string> p;
    auto fut = p.get_future();
    client->pics_get_product_info(
        /*packages=*/{}, apps, /*meta_data_only=*/false,
        [&p](std::optional<wn_steam::pb::CMsgClientPICSProductInfoResponse> r) {
            if (!r) { p.set_value({}); return; }
            std::string j = "[";
            bool first = true;
            for (const auto& a : r->apps) {
                if (a.buffer.empty()) {
                    // size>0 with an empty buffer ⇒ Steam HTTP-hosted the
                    // KeyValues on a CDN (payload >= http_min_size). The C++
                    // pics_get_product_info does not yet fetch those — such
                    // an app keeps re-requesting until that's implemented.
                    if (a.size > 0)
                        WN_LOGE("PICS app %u: HTTP-hosted appinfo (%u bytes) not fetched",
                                a.appid, a.size);
                    continue;
                }
                auto root = wn_steam::vdf::parse_auto(a.buffer);
                if (!root) continue;
                const wn_steam::vdf::KVNode* appinfo =
                    (root->name() == "appinfo") ? root.get() : root->child("appinfo");
                if (!appinfo) appinfo = root.get();
                if (!first) j += ',';
                first = false;
                j += "{\"appid\":";        j += std::to_string(a.appid);
                j += ",\"changeNumber\":"; j += std::to_string(a.change_number);
                j += ",\"appinfo\":";
                kvnode_to_json(*appinfo, j);
                j += '}';
            }
            j += ']';
            p.set_value(std::move(j));
        });
    std::string json = fut.get();
    if (json.empty()) return nullptr;
    return env->NewStringUTF(json.c_str());
}

// Blocking batch PICS package product-info. packageIds + parallel tokens are
// newline-joined. Returns a JSON array
// [{"packageid":N,"changeNumber":N,"appids":[...],"depotids":[...]}, ...]
// or null on failure.
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetPicsPackageInfo(
        JNIEnv* env, jclass /*cls*/, jlong h, jstring j_pkg_ids, jstring j_tokens) {
    auto* s = from_handle(h);
    if (!s || !s->client) return nullptr;
    std::shared_ptr<wn_steam::CMClient> client = s->client;
    std::vector<uint32_t> pkgids = jni_lines_u32(env, j_pkg_ids);
    std::vector<uint64_t> tokens = jni_lines_u64(env, j_tokens);

    std::vector<wn_steam::pb::PicsPackageInfoReq> packages;
    packages.reserve(pkgids.size());
    for (size_t i = 0; i < pkgids.size(); ++i) {
        wn_steam::pb::PicsPackageInfoReq req;
        req.packageid    = pkgids[i];
        req.access_token = (i < tokens.size()) ? tokens[i] : 0;
        packages.push_back(req);
    }

    std::promise<std::string> p;
    auto fut = p.get_future();
    client->pics_get_product_info(
        packages, /*apps=*/{}, /*meta_data_only=*/false,
        [&p](std::optional<wn_steam::pb::CMsgClientPICSProductInfoResponse> r) {
            if (!r) { p.set_value({}); return; }
            std::string j = "[";
            bool first = true;
            for (const auto& pkg : r->packages) {
                if (pkg.buffer.empty()) {
                    if (pkg.size > 0)
                        WN_LOGE("PICS package %u: HTTP-hosted info (%u bytes) not fetched",
                                pkg.packageid, pkg.size);
                    continue;
                }
                auto root = wn_steam::vdf::parse_binary_package(pkg.buffer, nullptr);
                if (!root) continue;
                if (!first) j += ',';
                first = false;
                j += "{\"packageid\":";    j += std::to_string(pkg.packageid);
                j += ",\"changeNumber\":"; j += std::to_string(pkg.change_number);
                j += ",\"appids\":[";
                if (const auto* appids = root->child("appids")) {
                    bool f2 = true;
                    for (const auto& c : appids->children()) {
                        if (!c) continue;
                        if (!f2) j += ',';
                        f2 = false;
                        j += std::to_string(c->as_uint(0));
                    }
                }
                j += "],\"depotids\":[";
                if (const auto* depotids = root->child("depotids")) {
                    bool f3 = true;
                    for (const auto& c : depotids->children()) {
                        if (!c) continue;
                        if (!f3) j += ',';
                        f3 = false;
                        j += std::to_string(c->as_uint(0));
                    }
                }
                j += "]}";
            }
            j += ']';
            p.set_value(std::move(j));
        });
    std::string json = fut.get();
    if (json.empty()) return nullptr;
    return env->NewStringUTF(json.c_str());
}

// Fire-and-forget CMsgClientGamesPlayed. gamesJson is a JSON array
// [{"gameId","processId","ownerId","launchSource","gameBuildId",
//   "processes":[{"pid","ppid","isSteam"}]}]. Reports running games for
// presence / playtime.
JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeNotifyGamesPlayed(
        JNIEnv* env, jclass /*cls*/, jlong h, jstring j_games, jint client_os_type) {
    auto* s = from_handle(h);
    if (!s || !s->client) return;
    std::shared_ptr<wn_steam::CMClient> client = s->client;

    auto root = nlohmann::json::parse(cloud_jstr(env, j_games), nullptr, /*allow_exceptions=*/false);
    if (root.is_discarded() || !root.is_array()) return;

    wn_steam::pb::CMsgClientGamesPlayed msg;
    msg.client_os_type = static_cast<uint32_t>(client_os_type);
    for (const auto& g : root) {
        if (!g.is_object()) continue;
        wn_steam::pb::GamePlayedEntry e;
        e.game_id       = g.value("gameId", 0ULL);
        e.process_id    = g.value("processId", 0u);
        e.owner_id      = g.value("ownerId", 0u);
        e.launch_source = g.value("launchSource", 0u);
        e.game_build_id = g.value("gameBuildId", 0u);
        if (auto it = g.find("processes"); it != g.end() && it->is_array()) {
            for (const auto& p : *it) {
                if (!p.is_object()) continue;
                wn_steam::pb::GamePlayedProcessInfo pi;
                pi.process_id        = p.value("pid", 0u);
                pi.process_id_parent = p.value("ppid", 0u);
                pi.parent_is_steam   = p.value("isSteam", false);
                e.process_id_list.push_back(pi);
            }
        }
        msg.games_played.push_back(std::move(e));
    }
    client->notify_games_played(msg);
}

// Fire-and-forget CMsgClientKickPlayingSession — release this account's
// other active playing session.
JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeKickPlayingSession(
        JNIEnv* /*env*/, jclass /*cls*/, jlong h, jboolean only_stop_game) {
    auto* s = from_handle(h);
    if (!s || !s->client) return;
    std::shared_ptr<wn_steam::CMClient> client = s->client;
    client->kick_playing_session(only_stop_game == JNI_TRUE);
}

// Whether playing is currently blocked for this account — reads the cached
// server-pushed CMsgClientPlayingSessionState. Non-blocking.
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeIsPlayingBlocked(
        JNIEnv* /*env*/, jclass /*cls*/, jlong h) {
    auto* s = from_handle(h);
    if (!s || !s->client) return JNI_FALSE;
    return s->client->is_playing_blocked() ? JNI_TRUE : JNI_FALSE;
}

// Force the playing-blocked cache to true (call before kickPlayingSession so
// the wait-loop only observes a post-kick server push).
JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeMarkPlayingBlocked(
        JNIEnv* /*env*/, jclass /*cls*/, jlong h) {
    auto* s = from_handle(h);
    if (!s || !s->client) return;
    s->client->mark_playing_blocked();
}

// Blocking Cloud.SignalAppLaunchIntent#1. Returns JSON
// {"pendingOps":[<code>,...]} (empty array = clear to launch) or null on
// failure. Pending-op codes: 1 AppSessionActive, 2 UploadInProgress, etc.
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeSignalAppLaunchIntent(
        JNIEnv* env, jclass /*cls*/, jlong h, jint app_id, jlong client_id,
        jstring j_machine_name, jboolean ignore_pending, jint os_type) {
    auto* s = from_handle(h);
    if (!s || !s->client) return nullptr;
    std::shared_ptr<wn_steam::CMClient> client = s->client;
    std::string machine_name = cloud_jstr(env, j_machine_name);

    std::promise<std::string> p;
    auto fut = p.get_future();
    client->cloud_signal_app_launch_intent(
        static_cast<uint32_t>(app_id), static_cast<uint64_t>(client_id),
        machine_name, ignore_pending == JNI_TRUE, static_cast<int32_t>(os_type),
        [&p](std::optional<wn_steam::pb::CCloud_AppLaunchIntent_Response> r) {
            if (!r) { p.set_value({}); return; }
            std::string j = "{\"pendingOps\":[";
            for (size_t i = 0; i < r->pending_operation_codes.size(); ++i) {
                if (i) j += ',';
                j += std::to_string(r->pending_operation_codes[i]);
            }
            j += "]}";
            p.set_value(std::move(j));
        });
    std::string json = fut.get();
    if (json.empty()) return nullptr;
    return env->NewStringUTF(json.c_str());
}

// Fire-and-forget Cloud.SignalAppExitSyncDone#1.
JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeSignalAppExitSyncDone(
        JNIEnv* /*env*/, jclass /*cls*/, jlong h, jint app_id, jlong client_id,
        jboolean uploads_completed, jboolean uploads_required) {
    auto* s = from_handle(h);
    if (!s || !s->client) return;
    std::shared_ptr<wn_steam::CMClient> client = s->client;
    client->cloud_signal_app_exit_sync_done(
        static_cast<uint32_t>(app_id), static_cast<uint64_t>(client_id),
        uploads_completed == JNI_TRUE, uploads_required == JNI_TRUE);
}

// Fire-and-forget CMsgClientChangeStatus — publish persona state.
JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeSetPersonaState(
        JNIEnv* /*env*/, jclass /*cls*/, jlong h, jint persona_state) {
    auto* s = from_handle(h);
    if (!s || !s->client) return;
    std::shared_ptr<wn_steam::CMClient> client = s->client;
    client->set_persona_state(static_cast<uint32_t>(persona_state));
}

// Fire-and-forget CMsgClientRequestFriendData for the local user — the
// CMsgClientPersonaState reply is cached; read it via nativeGetSelfPersona.
JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeRequestUserPersona(
        JNIEnv* /*env*/, jclass /*cls*/, jlong h) {
    auto* s = from_handle(h);
    if (!s || !s->client) return;
    std::shared_ptr<wn_steam::CMClient> client = s->client;
    client->request_user_persona();
}

// Returns the local user's cached persona as JSON
// {"personaState","gameAppId","playerName","avatarHash","gameName","gameId"}
// or null if no CMsgClientPersonaState for our SteamID has arrived yet.
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetSelfPersona(
        JNIEnv* env, jclass /*cls*/, jlong h) {
    auto* s = from_handle(h);
    if (!s || !s->client) return nullptr;
    auto persona = s->client->self_persona();
    if (!persona) return nullptr;
    std::string j = "{\"personaState\":";
    j += std::to_string(persona->persona_state);
    j += ",\"gameAppId\":";   j += std::to_string(persona->game_played_app_id);
    j += ",\"playerName\":\"";j += cloud_json_escape(persona->player_name); j += '"';
    j += ",\"avatarHash\":\"";j += cloud_to_hex(persona->avatar_hash);      j += '"';
    j += ",\"gameName\":\"";  j += cloud_json_escape(persona->game_name);   j += '"';
    j += ",\"gameId\":";      j += std::to_string(persona->gameid);
    j += '}';
    return env->NewStringUTF(j.c_str());
}

// Blocking FamilyGroups.GetFamilyGroup#1. Returns the family group as a JSON
// object string {"name","members":[steamid64,...]} or null on failure / not
// logged on. The C++ job carries its own 30s timeout so fut.get() can't hang.
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetFamilyGroup(
        JNIEnv* env, jclass /*cls*/, jlong h, jlong family_group_id) {
    auto* s = from_handle(h);
    if (!s || !s->client) return nullptr;
    std::shared_ptr<wn_steam::CMClient> client = s->client;

    std::promise<std::string> p;
    auto fut = p.get_future();
    bool delivered = false;
    client->get_family_group(
        static_cast<uint64_t>(family_group_id),
        [&p, &delivered](
                std::optional<wn_steam::pb::CFamilyGroups_GetFamilyGroup_Response> r) {
            if (!r) { p.set_value({}); return; }
            std::string j = "{\"name\":\"";
            j += cloud_json_escape(r->name);
            j += "\",\"members\":[";
            for (size_t i = 0; i < r->members.size(); ++i) {
                if (i) j += ',';
                j += std::to_string(r->members[i].steamid);
            }
            j += "]}";
            delivered = true;
            p.set_value(std::move(j));
        });
    std::string json = fut.get();
    // An empty result is the failure sentinel; a successful empty-member
    // group still carries {"name":...,"members":[]}.
    if (!delivered || json.empty()) return nullptr;
    return env->NewStringUTF(json.c_str());
}

// Blocking Player.GetOwnedGames#1. Returns the user's owned games as a JSON
// array string [{appId,name,playtimeTwoWeeks,playtimeForever,imgIconUrl,
// sortAs,rtimeLastPlayed},...], or null on failure / not logged on. A private
// library is a successful empty array "[]". 30s native job timeout — no hang.
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetOwnedGames(
        JNIEnv* env, jclass /*cls*/, jlong h, jlong steam_id) {
    auto* s = from_handle(h);
    if (!s || !s->client) return nullptr;
    std::shared_ptr<wn_steam::CMClient> client = s->client;

    std::promise<std::string> p;
    auto fut = p.get_future();
    bool delivered = false;
    client->get_owned_games(
        static_cast<uint64_t>(steam_id),
        [&p, &delivered](
                std::optional<wn_steam::pb::CPlayer_GetOwnedGames_Response> r) {
            if (!r) { p.set_value({}); return; }
            std::string j = "[";
            for (size_t i = 0; i < r->games.size(); ++i) {
                const auto& g = r->games[i];
                if (i) j += ',';
                j += "{\"appId\":";          j += std::to_string(g.appid);
                j += ",\"name\":\"";         j += cloud_json_escape(g.name); j += '"';
                j += ",\"playtimeTwoWeeks\":";  j += std::to_string(g.playtime_2weeks);
                j += ",\"playtimeForever\":";   j += std::to_string(g.playtime_forever);
                j += ",\"imgIconUrl\":\"";   j += cloud_json_escape(g.img_icon_url); j += '"';
                j += ",\"sortAs\":\"";       j += cloud_json_escape(g.sort_as);      j += '"';
                j += ",\"rtimeLastPlayed\":"; j += std::to_string(g.rtime_last_played);
                j += '}';
            }
            j += ']';
            delivered = true;
            p.set_value(std::move(j));
        });
    std::string json = fut.get();
    if (!delivered) return nullptr;
    return env->NewStringUTF(json.c_str());
}

// Returns the cached CMsgClientLicenseList as a JSON array string
// [{packageId,changeNumber,timeCreated,timeNextProcess,minuteLimit,
//   minutesUsed,paymentMethod,flags,purchaseCountryCode,licenseType,
//   territoryCode,accessToken,ownerId,masterPackageId},...]. Empty array
// "[]" until the post-logon license push has arrived. Non-blocking.
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetLicenseList(
        JNIEnv* env, jclass /*cls*/, jlong h) {
    auto* s = from_handle(h);
    if (!s || !s->client) return nullptr;
    auto licenses = s->client->license_list();
    std::string j = "[";
    for (size_t i = 0; i < licenses.size(); ++i) {
        const auto& l = licenses[i];
        if (i) j += ',';
        j += "{\"packageId\":";        j += std::to_string(l.package_id);
        j += ",\"changeNumber\":";     j += std::to_string(l.change_number);
        j += ",\"timeCreated\":";      j += std::to_string(l.time_created);
        j += ",\"timeNextProcess\":";  j += std::to_string(l.time_next_process);
        j += ",\"minuteLimit\":";      j += std::to_string(l.minute_limit);
        j += ",\"minutesUsed\":";      j += std::to_string(l.minutes_used);
        j += ",\"paymentMethod\":";    j += std::to_string(l.payment_method);
        j += ",\"flags\":";            j += std::to_string(l.flags);
        j += ",\"purchaseCountryCode\":\"";
        j += cloud_json_escape(l.purchase_country_code); j += '"';
        j += ",\"licenseType\":";      j += std::to_string(l.license_type);
        j += ",\"territoryCode\":";    j += std::to_string(l.territory_code);
        // access_token is a uint64; emit as signed so it round-trips through
        // Kotlin's Long (matches how JavaSteam stored it).
        j += ",\"accessToken\":";
        j += std::to_string(static_cast<int64_t>(l.access_token));
        j += ",\"ownerId\":";          j += std::to_string(l.owner_id);
        j += ",\"masterPackageId\":";  j += std::to_string(l.master_package_id);
        j += '}';
    }
    j += ']';
    return env->NewStringUTF(j.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeGetLibrarySnapshot(
        JNIEnv* env, jclass /*cls*/, jlong h) {
    auto* s = from_handle(h);
    if (!s || !s->client) return env->NewStringUTF("{}");
    return env->NewStringUTF(s->client->library().snapshot_json().c_str());
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeSetLibraryObserver(
        JNIEnv* env, jclass /*cls*/, jlong h, jobject observer) {
    auto* s = from_handle(h);
    if (!s || !s->client) return;
    init_jni_session_globals(env);

    JavaVM* vm = nullptr;
    env->GetJavaVM(&vm);

    if (!observer) {
        s->client->library().set_observer(nullptr);
        return;
    }

    // The observer global ref is owned by a shared_ptr whose deleter runs
    // DeleteGlobalRef. The installed lambda holds one share; WnLibraryStore::
    // notify_() copies the lambda (and thus the share) under its lock before
    // invoking, so an in-flight callback keeps the ref alive even if the
    // observer is swapped or cleared concurrently. The ref is freed exactly
    // once — when the last share drops (the store replacing/destroying the
    // observer, after any in-flight notify finishes). No leak, no UAF.
    std::shared_ptr<_jobject> obs_ref(
        env->NewGlobalRef(observer),
        [vm](jobject ref) {
            if (!ref || !vm) return;
            AttachScope scope(vm);
            if (scope.env) scope.env->DeleteGlobalRef(ref);
        });
    s->client->library().set_observer([vm, obs_ref]() {
        if (!obs_ref) return;
        AttachScope scope(vm);
        if (!scope.env) return;
        scope.env->CallVoidMethod(obs_ref.get(), g_sess.library_obs_changed);
        if (scope.env->ExceptionCheck()) scope.env->ExceptionClear();
    });
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeState(
        JNIEnv* /*env*/, jclass /*cls*/, jlong h) {
    auto* s = from_handle(h);
    if (!s || !s->client) return 0;
    return static_cast<jint>(s->client->state());
}

JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeSteamId(
        JNIEnv* /*env*/, jclass /*cls*/, jlong h) {
    auto* s = from_handle(h);
    if (!s || !s->client) return 0;
    return static_cast<jlong>(s->client->steam_id());
}

JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativeFamilyGroupId(
        JNIEnv* /*env*/, jclass /*cls*/, jlong h) {
    auto* s = from_handle(h);
    if (!s || !s->client) return 0;
    return static_cast<jlong>(s->client->family_group_id());
}

// ---------------------------------------------------------------------------
// Static helper: pick a CM URL via Steam Directory with hardcoded fallback.
// Synchronous (calls libcurl) — caller MUST be on a background thread.
// Returns empty string if no usable WebSocket CM is reachable.
// `ca_bundle_path` is the absolute path to a single-file PEM trust bundle
// (typically produced by CaBundleExtractor). Empty string disables TLS
// verification source — the call will fail because verifypeer is on.
// ---------------------------------------------------------------------------
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_nativePickCmUrl(
        JNIEnv* env, jclass /*cls*/, jstring jca_bundle) {
    std::string ca_path;
    if (jca_bundle) {
        const char* p = env->GetStringUTFChars(jca_bundle, nullptr);
        if (p) {
            ca_path = p;
            env->ReleaseStringUTFChars(jca_bundle, p);
        }
    }

    std::vector<wn_steam::CmServer> candidates;
    try {
        wn_steam::SteamDirectoryClient dir;
        auto res = dir.fetch(/*cell_id*/ 0,
                             /*timeout*/ std::chrono::seconds{10},
                             wn_steam::SteamDirectoryClient::kDefaultUserAgent,
                             ca_path);
        candidates = std::move(res.servers);
    } catch (...) {
        // ignore, fall through to hardcoded list
    }
    if (candidates.empty()) {
        candidates = wn_steam::hardcoded_fallback_servers();
    }
    for (const auto& s : candidates) {
        const auto url = s.websocket_url();
        if (!url.empty()) {
            return env->NewStringUTF(url.c_str());
        }
    }
    return env->NewStringUTF("");
}

}  // extern "C"
