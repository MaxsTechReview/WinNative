// JNI bridge for WnSteamSession — the production-facing handle that wraps
// CMClient + CredentialsAuthSession + QrAuthSession. Kotlin
// SteamLoginViewModel drives this in Phase 2E.

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <thread>

#include "wn_steam/auth_session.h"
#include "wn_steam/authenticator.h"
#include "wn_steam/cm_client.h"
#include "wn_steam/cm_server_list.h"
#include "wn_steam/steam_directory.h"

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

        // 4. Drop the Kotlin observer ref.
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

    jobject obj = env->NewObject(
        g_sess.auth_result_cls, g_sess.auth_result_ctor,
        r.success ? JNI_TRUE : JNI_FALSE,
        static_cast<jint>(r.eresult),
        jerr, jaccount, jrefresh, jaccess, jguard,
        static_cast<jlong>(r.steamid),
        r.had_remote_interaction ? JNI_TRUE : JNI_FALSE,
        jagree);

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
