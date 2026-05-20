#include "wn_steam/auth_session.h"

#include <android/log.h>

#include <chrono>
#include <thread>

#include "wn_steam/base64.h"
#include "wn_steam/rsa_password.h"

namespace wn_steam {

namespace {
constexpr const char* kLogTag = "WnSteamAuth";
#define WN_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)
#define WN_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  kLogTag, __VA_ARGS__)

// Sleep that can be cut short by the cancel flag.
void interruptible_sleep_seconds(float seconds,
                                 const std::atomic<bool>& cancel_flag) {
    const auto total = std::chrono::milliseconds(
        static_cast<int>(seconds * 1000.0f));
    const auto tick = std::chrono::milliseconds(100);
    auto elapsed = std::chrono::milliseconds(0);
    while (elapsed < total) {
        if (cancel_flag.load()) return;
        std::this_thread::sleep_for(tick);
        elapsed += tick;
    }
}
}  // namespace

// ---------------------------------------------------------------------------
// CredentialsAuthSession
// ---------------------------------------------------------------------------

CredentialsAuthSession::CredentialsAuthSession(
        std::shared_ptr<CMClient> client,
        std::shared_ptr<Authenticator> authenticator,
        Config config)
    : client_(std::move(client)),
      authenticator_(std::move(authenticator)),
      config_(std::move(config)) {}

void CredentialsAuthSession::start(AuthSessionResultCallback result_cb) {
    result_cb_ = std::move(result_cb);
    step_get_rsa_key();
}

void CredentialsAuthSession::cancel() {
    cancelled_.store(true);
}

void CredentialsAuthSession::step_get_rsa_key() {
    pb::CAuthentication_GetPasswordRSAPublicKey_Request req;
    req.account_name = config_.username;

    auto self = shared_from_this();
    client_->call_service_method(
        "Authentication.GetPasswordRSAPublicKey#1",
        /*authed=*/false,
        req.serialize(),
        [self](JobResult r) {
            if (self->cancelled_.load() || self->finished_.load()) return;
            if (r.synthetic_failure || r.eresult != 1) {
                AuthSessionResult err;
                err.eresult       = r.eresult;
                err.error_message = r.error_message.empty()
                    ? "GetPasswordRSAPublicKey failed"
                    : r.error_message;
                self->finish(std::move(err));
                return;
            }
            auto resp = pb::CAuthentication_GetPasswordRSAPublicKey_Response::deserialize(r.body);
            if (!resp) {
                AuthSessionResult err;
                err.error_message = "RSA key response parse failed";
                self->finish(std::move(err));
                return;
            }
            self->step_begin_session(*resp);
        });
}

void CredentialsAuthSession::step_begin_session(
        const pb::CAuthentication_GetPasswordRSAPublicKey_Response& key) {

    auto encrypted = rsa_pkcs1v15_encrypt_password_with_hex_key(
        config_.password, key.publickey_mod, key.publickey_exp);
    if (!encrypted) {
        AuthSessionResult err;
        err.error_message = "password RSA encryption failed";
        finish(std::move(err));
        return;
    }
    const std::string encrypted_b64 = base64_encode(*encrypted);

    pb::CAuthentication_BeginAuthSessionViaCredentials_Request req;
    req.account_name         = config_.username;
    req.encrypted_password   = encrypted_b64;
    req.encryption_timestamp = key.timestamp;
    req.persistence          = config_.persistent_session
        ? pb::ESessionPersistence::Persistent
        : pb::ESessionPersistence::Ephemeral;
    // Impersonate the Steam desktop client — Steam silently rejects
    // credentials submitted with platform_type=MobileApp + website_id=Mobile
    // unless we also supply a real mobile device fingerprint (machine_id
    // etc.) which we don't have. SteamKit2 and JavaSteam both default to
    // these "Client"/SteamClient/Windows values.
    req.website_id           = "Client";
    req.device_details.device_friendly_name = config_.device_friendly_name;
    req.device_details.platform_type        = pb::EAuthTokenPlatformType::SteamClient;
    req.device_details.os_type              = 16;  // EOSType.Windows
    req.guard_data           = config_.guard_data;

    auto self = shared_from_this();
    client_->call_service_method(
        "Authentication.BeginAuthSessionViaCredentials#1",
        /*authed=*/false,
        req.serialize(),
        [self](JobResult r) {
            if (self->cancelled_.load() || self->finished_.load()) return;
            if (r.synthetic_failure || r.eresult != 1) {
                AuthSessionResult err;
                err.eresult       = r.eresult;
                err.error_message = r.error_message.empty()
                    ? "BeginAuthSessionViaCredentials failed"
                    : r.error_message;
                self->finish(std::move(err));
                return;
            }
            auto resp =
                pb::CAuthentication_BeginAuthSessionViaCredentials_Response::deserialize(r.body);
            if (!resp) {
                AuthSessionResult err;
                err.error_message = "BeginAuthSession response parse failed";
                self->finish(std::move(err));
                return;
            }
            // If Steam silently rejected the credentials (e.g. RSA
            // ciphertext didn't decrypt, or some other validation
            // failure), it returns a minimal response with no client_id /
            // no request_id / no allowed_confirmations — just the default
            // interval and an empty extended_error_message. Detect that
            // here and fail fast instead of entering the poll loop, which
            // would spin forever because no auth session exists.
            if (resp->client_id == 0 || resp->request_id.empty()) {
                AuthSessionResult err;
                err.eresult       = 5;  // EResult.InvalidPassword (best guess; Steam doesn't say)
                err.error_message = resp->extended_error_message.empty()
                    ? "Steam rejected the credentials (no auth session created — likely bad password or unrecognized device)"
                    : resp->extended_error_message;
                self->finish(std::move(err));
                return;
            }
            self->client_id_              = resp->client_id;
            self->request_id_             = std::move(resp->request_id);
            self->poll_interval_seconds_  = resp->interval;
            self->allowed_confirmations_  = std::move(resp->allowed_confirmations);
            self->steamid_                = resp->steamid;
            self->process_confirmations();
        });
}

void CredentialsAuthSession::process_confirmations() {
    using pb::EAuthSessionGuardType;
    auto self = shared_from_this();

    // Find the first confirmation type the user can act on. Order of
    // preference matches SteamKit: DeviceConfirmation > DeviceCode >
    // EmailCode > EmailConfirmation > None.
    EAuthSessionGuardType chosen = EAuthSessionGuardType::None;
    for (const auto& c : allowed_confirmations_) {
        if (c.confirmation_type == EAuthSessionGuardType::DeviceConfirmation) {
            chosen = c.confirmation_type;
            break;
        }
    }
    if (chosen == EAuthSessionGuardType::None) {
        for (const auto& c : allowed_confirmations_) {
            if (c.confirmation_type == EAuthSessionGuardType::DeviceCode ||
                c.confirmation_type == EAuthSessionGuardType::EmailCode) {
                chosen = c.confirmation_type;
                break;
            }
        }
    }
    if (chosen == EAuthSessionGuardType::None) {
        for (const auto& c : allowed_confirmations_) {
            if (c.confirmation_type == EAuthSessionGuardType::None) {
                chosen = c.confirmation_type;
                break;
            }
        }
    }

    if (chosen == EAuthSessionGuardType::DeviceConfirmation) {
        // Tell the UI to display the "approve on phone" affordance. The
        // user taps the mobile app; we discover it via the poll loop.
        if (authenticator_) {
            authenticator_->accept_device_confirmation([self](bool /*ok*/) {
                self->poll_loop();
            });
        } else {
            poll_loop();
        }
        return;
    }

    if (chosen == EAuthSessionGuardType::DeviceCode) {
        if (!authenticator_) {
            AuthSessionResult err;
            err.error_message = "DeviceCode required but no authenticator";
            finish(std::move(err));
            return;
        }
        authenticator_->get_device_code(
            /*previous_was_incorrect=*/false,
            [self](std::string code) {
                if (self->cancelled_.load()) return;
                self->submit_steam_guard_code(
                    pb::EAuthSessionGuardType::DeviceCode, std::move(code));
            });
        return;
    }

    if (chosen == EAuthSessionGuardType::EmailCode) {
        if (!authenticator_) {
            AuthSessionResult err;
            err.error_message = "EmailCode required but no authenticator";
            finish(std::move(err));
            return;
        }
        // We don't have the email address from BeginAuthSession; pass an
        // empty string and let the UI label generically.
        authenticator_->get_email_code(
            /*email=*/std::string{},
            /*previous_was_incorrect=*/false,
            [self](std::string code) {
                if (self->cancelled_.load()) return;
                self->submit_steam_guard_code(
                    pb::EAuthSessionGuardType::EmailCode, std::move(code));
            });
        return;
    }

    // No guard needed — go straight to polling.
    poll_loop();
}

void CredentialsAuthSession::submit_steam_guard_code(
        pb::EAuthSessionGuardType type, std::string code) {
    pb::CAuthentication_UpdateAuthSessionWithSteamGuardCode_Request req;
    req.client_id = client_id_;
    req.steamid   = steamid_;
    req.code      = std::move(code);
    req.code_type = type;

    auto self = shared_from_this();
    client_->call_service_method(
        "Authentication.UpdateAuthSessionWithSteamGuardCode#1",
        /*authed=*/false,
        req.serialize(),
        [self](JobResult r) {
            if (self->cancelled_.load() || self->finished_.load()) return;
            // EResult.DuplicateRequest (29) is fine — proceed to poll.
            if (r.synthetic_failure ||
                (r.eresult != 1 && r.eresult != 29)) {
                AuthSessionResult err;
                err.eresult       = r.eresult;
                err.error_message = r.error_message.empty()
                    ? "UpdateAuthSessionWithSteamGuardCode failed"
                    : r.error_message;
                self->finish(std::move(err));
                return;
            }
            self->poll_loop();
        });
}

void CredentialsAuthSession::poll_loop() {
    auto self = shared_from_this();

    // Sleep first (interruptible) so we don't immediately re-hit the
    // server right after BeginAuthSession's response.
    std::thread([self]() {
        interruptible_sleep_seconds(self->poll_interval_seconds_, self->cancelled_);
        if (self->cancelled_.load() || self->finished_.load()) return;

        pb::CAuthentication_PollAuthSessionStatus_Request req;
        req.client_id  = self->client_id_;
        req.request_id = self->request_id_;

        self->client_->call_service_method(
            "Authentication.PollAuthSessionStatus#1",
            /*authed=*/false,
            req.serialize(),
            [self](JobResult r) {
                if (self->cancelled_.load() || self->finished_.load()) return;
                if (r.synthetic_failure || r.eresult != 1) {
                    AuthSessionResult err;
                    err.eresult       = r.eresult;
                    err.error_message = r.error_message.empty()
                        ? "PollAuthSessionStatus failed"
                        : r.error_message;
                    self->finish(std::move(err));
                    return;
                }
                auto resp = pb::CAuthentication_PollAuthSessionStatus_Response::deserialize(r.body);
                if (!resp) {
                    AuthSessionResult err;
                    err.error_message = "Poll response parse failed";
                    self->finish(std::move(err));
                    return;
                }
                if (resp->new_client_id != 0) self->client_id_ = resp->new_client_id;

                if (!resp->refresh_token.empty()) {
                    AuthSessionResult ok;
                    ok.success        = true;
                    ok.eresult        = 1;
                    ok.account_name   = std::move(resp->account_name);
                    ok.refresh_token  = std::move(resp->refresh_token);
                    ok.access_token   = std::move(resp->access_token);
                    ok.new_guard_data = std::move(resp->new_guard_data);
                    ok.steamid        = self->steamid_;
                    ok.had_remote_interaction = resp->had_remote_interaction;
                    ok.agreement_session_url  = std::move(resp->agreement_session_url);
                    self->finish(std::move(ok));
                    return;
                }
                // Not yet ready — loop.
                self->poll_loop();
            });
    }).detach();
}

void CredentialsAuthSession::finish(AuthSessionResult r) {
    bool expected = false;
    if (!finished_.compare_exchange_strong(expected, true)) return;
    if (result_cb_) {
        try { result_cb_(std::move(r)); } catch (...) {}
    }
}

// ---------------------------------------------------------------------------
// QrAuthSession
// ---------------------------------------------------------------------------

QrAuthSession::QrAuthSession(std::shared_ptr<CMClient> client, Config config)
    : client_(std::move(client)), config_(std::move(config)) {}

void QrAuthSession::start(QrUrlCallback url_cb,
                          AuthSessionResultCallback result_cb) {
    url_cb_    = std::move(url_cb);
    result_cb_ = std::move(result_cb);
    step_begin();
}

void QrAuthSession::cancel() { cancelled_.store(true); }

void QrAuthSession::step_begin() {
    pb::CAuthentication_BeginAuthSessionViaQR_Request req;
    req.device_friendly_name = config_.device_friendly_name;
    // platform_type MUST be SteamClient: it determines the audience of the
    // refresh token the QR flow issues. A MobileApp token can browse the
    // library (PICS / licenses) but Steam boots any session that uses it
    // for a client/content operation such as ClientGetDepotDecryptionKey
    // — which breaks depot downloads. We act as a Steam client, so the QR
    // session must mint a SteamClient-scoped token. The phone scanning the
    // QR just approves it; it does not dictate the token audience.
    req.platform_type        = pb::EAuthTokenPlatformType::SteamClient;
    req.device_details.device_friendly_name = config_.device_friendly_name;
    req.device_details.platform_type        = pb::EAuthTokenPlatformType::SteamClient;
    req.device_details.os_type              = 16;   // EOSType.Windows
    req.website_id           = "Client";

    auto self = shared_from_this();
    client_->call_service_method(
        "Authentication.BeginAuthSessionViaQR#1",
        /*authed=*/false,
        req.serialize(),
        [self](JobResult r) {
            if (self->cancelled_.load() || self->finished_.load()) return;
            if (r.synthetic_failure || r.eresult != 1) {
                AuthSessionResult err;
                err.eresult       = r.eresult;
                err.error_message = r.error_message.empty()
                    ? "BeginAuthSessionViaQR failed"
                    : r.error_message;
                self->finish(std::move(err));
                return;
            }
            auto resp =
                pb::CAuthentication_BeginAuthSessionViaQR_Response::deserialize(r.body);
            if (!resp) {
                AuthSessionResult err;
                err.error_message = "QR response parse failed";
                self->finish(std::move(err));
                return;
            }
            self->client_id_             = resp->client_id;
            self->request_id_            = std::move(resp->request_id);
            self->poll_interval_seconds_ = resp->interval;
            self->last_challenge_url_    = resp->challenge_url;
            if (self->url_cb_) {
                try { self->url_cb_(self->last_challenge_url_); } catch (...) {}
            }
            self->poll_loop();
        });
}

void QrAuthSession::poll_loop() {
    auto self = shared_from_this();
    std::thread([self]() {
        interruptible_sleep_seconds(self->poll_interval_seconds_, self->cancelled_);
        if (self->cancelled_.load() || self->finished_.load()) return;

        pb::CAuthentication_PollAuthSessionStatus_Request req;
        req.client_id  = self->client_id_;
        req.request_id = self->request_id_;

        self->client_->call_service_method(
            "Authentication.PollAuthSessionStatus#1",
            /*authed=*/false,
            req.serialize(),
            [self](JobResult r) {
                if (self->cancelled_.load() || self->finished_.load()) return;
                if (r.synthetic_failure || r.eresult != 1) {
                    AuthSessionResult err;
                    err.eresult       = r.eresult;
                    err.error_message = r.error_message.empty()
                        ? "PollAuthSessionStatus failed"
                        : r.error_message;
                    self->finish(std::move(err));
                    return;
                }
                auto resp = pb::CAuthentication_PollAuthSessionStatus_Response::deserialize(r.body);
                if (!resp) {
                    AuthSessionResult err;
                    err.error_message = "Poll response parse failed";
                    self->finish(std::move(err));
                    return;
                }
                if (resp->new_client_id != 0) self->client_id_ = resp->new_client_id;

                if (!resp->new_challenge_url.empty() &&
                    resp->new_challenge_url != self->last_challenge_url_) {
                    self->last_challenge_url_ = resp->new_challenge_url;
                    if (self->url_cb_) {
                        try { self->url_cb_(self->last_challenge_url_); } catch (...) {}
                    }
                }

                if (!resp->refresh_token.empty()) {
                    AuthSessionResult ok;
                    ok.success        = true;
                    ok.eresult        = 1;
                    ok.account_name   = std::move(resp->account_name);
                    ok.refresh_token  = std::move(resp->refresh_token);
                    ok.access_token   = std::move(resp->access_token);
                    ok.new_guard_data = std::move(resp->new_guard_data);
                    ok.had_remote_interaction = resp->had_remote_interaction;
                    ok.agreement_session_url  = std::move(resp->agreement_session_url);
                    self->finish(std::move(ok));
                    return;
                }
                self->poll_loop();
            });
    }).detach();
}

void QrAuthSession::finish(AuthSessionResult r) {
    bool expected = false;
    if (!finished_.compare_exchange_strong(expected, true)) return;
    if (result_cb_) {
        try { result_cb_(std::move(r)); } catch (...) {}
    }
}

}  // namespace wn_steam
