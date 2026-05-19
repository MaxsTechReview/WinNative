#pragma once

#include <atomic>
#include <chrono>
#include <functional>
#include <memory>
#include <string>
#include <vector>

#include "wn_steam/authenticator.h"
#include "wn_steam/cm_client.h"
#include "wn_steam/pb/cauthentication.h"

namespace wn_steam {

// Final result of an auth session — either the refresh + access token
// pair (on success) or an error.
struct AuthSessionResult {
    bool          success = false;
    int32_t       eresult = 2;  // Invalid
    std::string   error_message;
    std::string   account_name;
    std::string   refresh_token;
    std::string   access_token;
    std::string   new_guard_data;
    uint64_t      steamid = 0;
    bool          had_remote_interaction = false;
    std::string   agreement_session_url;
};

using AuthSessionResultCallback = std::function<void(AuthSessionResult)>;

// Optional UI-side QR notification: fires whenever the QR challenge URL
// changes. Implementations should re-render the QR code each time.
using QrUrlCallback = std::function<void(std::string url)>;

// CredentialsAuthSession runs the full modern login flow against a
// username + password:
//
//   1. Authentication.GetPasswordRSAPublicKey  →  publickey_mod/exp/timestamp
//   2. RSA-PKCS1v15(password, mod, exp), base64
//   3. Authentication.BeginAuthSessionViaCredentials  →  client_id, request_id,
//      allowed_confirmations, interval
//   4. For each non-None confirmation (Device/Email code), prompt the
//      Authenticator and call UpdateAuthSessionWithSteamGuardCode.
//   5. Poll PollAuthSessionStatus until refresh_token is set.
//   6. Deliver AuthSessionResult.
//
// Single-shot: construct, start(), and either receive the result via
// callback or cancel().
class CredentialsAuthSession final
    : public std::enable_shared_from_this<CredentialsAuthSession> {
public:
    struct Config {
        std::string username;
        std::string password;
        std::string device_friendly_name = "WN-Steam-Client";
        std::string guard_data;            // optional; suppress re-prompt
        bool        persistent_session = true;
    };

    CredentialsAuthSession(std::shared_ptr<CMClient> client,
                           std::shared_ptr<Authenticator> authenticator,
                           Config config);

    // Starts the flow. Result fires on the channel worker thread (the
    // continuations bounce through the CM I/O). Caller marshals to UI.
    void start(AuthSessionResultCallback result_cb);

    // Cancels any in-flight poll / continuation. The result callback
    // fires with success=false, eresult=-1 only if not already fired.
    void cancel();

private:
    void step_get_rsa_key();
    void step_begin_session(const pb::CAuthentication_GetPasswordRSAPublicKey_Response& key);
    void process_confirmations();
    void submit_steam_guard_code(pb::EAuthSessionGuardType type, std::string code);
    void poll_loop();
    void finish(AuthSessionResult r);

    std::shared_ptr<CMClient>                client_;
    std::shared_ptr<Authenticator>           authenticator_;
    Config                                   config_;

    AuthSessionResultCallback                result_cb_;
    std::atomic<bool>                        finished_{false};
    std::atomic<bool>                        cancelled_{false};

    // BeginAuthSession output, captured for polling.
    uint64_t                                 client_id_ = 0;
    std::vector<uint8_t>                     request_id_;
    float                                    poll_interval_seconds_ = 5.0f;
    std::vector<pb::CAuthentication_AllowedConfirmation> allowed_confirmations_;
    uint64_t                                 steamid_ = 0;
};

// QrAuthSession is the parallel flow for QR-code login:
//   1. Authentication.BeginAuthSessionViaQR  →  challenge_url, client_id, ...
//   2. Emit challenge_url to the UI.
//   3. Poll PollAuthSessionStatus until refresh_token is set; if
//      new_challenge_url is returned, re-emit it.
class QrAuthSession final
    : public std::enable_shared_from_this<QrAuthSession> {
public:
    struct Config {
        std::string device_friendly_name = "WN-Steam-Client";
    };

    QrAuthSession(std::shared_ptr<CMClient> client, Config config);

    void start(QrUrlCallback url_cb, AuthSessionResultCallback result_cb);
    void cancel();

private:
    void step_begin();
    void poll_loop();
    void finish(AuthSessionResult r);

    std::shared_ptr<CMClient>                client_;
    Config                                   config_;

    QrUrlCallback                            url_cb_;
    AuthSessionResultCallback                result_cb_;
    std::atomic<bool>                        finished_{false};
    std::atomic<bool>                        cancelled_{false};

    uint64_t                                 client_id_ = 0;
    std::vector<uint8_t>                     request_id_;
    float                                    poll_interval_seconds_ = 5.0f;
    std::string                              last_challenge_url_;
};

}  // namespace wn_steam
