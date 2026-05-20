#pragma once

#include <functional>
#include <memory>
#include <string>

namespace wn_steam {

// C++-side mirror of JavaSteam's IAuthenticator (and our Kotlin
// WnAuthenticator). Implementations bridge to the user-facing UI to
// prompt for Steam Guard codes during the modern auth flow.
//
// All methods are asynchronous: the implementation invokes the supplied
// callback with the user-entered value when ready. The caller (the
// AuthSession) is responsible for not invoking another request until the
// previous callback has fired.
class Authenticator {
public:
    virtual ~Authenticator() = default;

    // The server pushed a "tap to approve" prompt to the user's mobile
    // authenticator. Invoke the callback with `true` once the UI has
    // shown the user; the session continues polling.
    virtual void accept_device_confirmation(
        std::function<void(bool)> cb) = 0;

    // The server wants a TOTP code from the mobile authenticator. Invoke
    // the callback with the user-entered code.
    virtual void get_device_code(
        bool previous_was_incorrect,
        std::function<void(std::string)> cb) = 0;

    // The server wants an email Steam Guard code. `email` is the user's
    // email address as Steam reports it (may be empty).
    virtual void get_email_code(
        std::string email,
        bool previous_was_incorrect,
        std::function<void(std::string)> cb) = 0;
};

}  // namespace wn_steam
