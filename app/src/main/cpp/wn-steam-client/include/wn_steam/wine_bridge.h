#pragma once

#include <atomic>
#include <cstdint>
#include <functional>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

// WineBridge — the Linux-side endpoint that Wine's lsteamclient.dll talks
// to when running a Steam game inside our Wine prefix. Provides two TCP
// listeners:
//
//   Steam3Master         (default 127.0.0.1:57343)
//     The "Steam client process" RPC that lsteamclient.dll connects to
//     immediately on init. Carries the bulk of the SteamWorks API surface
//     (ISteamApps, ISteamUser, ISteamFriends, ISteamUserStats, etc.).
//
//   SteamClientService   (default 127.0.0.1:57344)
//     Secondary RPC used for a small set of operations (notifications,
//     overlay, screenshot, etc.). Less load-bearing for offline-capable
//     games, but lsteamclient.dll opens both anyway.
//
// Wine learns the endpoint addresses via env vars (see WnWineEnvVars.kt).
//
// PHASE 8b.1 (this commit): the listeners accept connections, log the
// first 64 bytes of each frame so we can observe what lsteamclient.dll
// sends, and close. No real protocol responses yet — that comes in 8b.2
// when we either:
//   (a) load Valve's libsteamclient.so in-process and proxy requests to
//       it (the GameNative-style path), or
//   (b) hand-implement the subset of the protocol Wine actually needs
//       for offline launch + DLC ownership (much harder).
//
// Either way, we'll be able to read traffic immediately by pointing
// Proton at this bridge and capturing the dump.

namespace wn_steam {

class WineBridge {
public:
    using ClientObserver = std::function<void(int port,
                                              std::string peer,
                                              std::vector<uint8_t> first_bytes)>;

    struct Config {
        std::string bind_host        = "127.0.0.1";
        uint16_t    steam3_port      = 57343;   // SteamWorks main RPC
        uint16_t    client_svc_port  = 57344;   // SteamClient secondary RPC
        // Captured peek bytes per connection (truncates the snoop log).
        size_t      snoop_bytes      = 64;
    };

    WineBridge();
    ~WineBridge();

    WineBridge(const WineBridge&)            = delete;
    WineBridge& operator=(const WineBridge&) = delete;

    // Start both listeners. Returns false if either socket() / bind() /
    // listen() fails — caller can read last_error(). Idempotent: calling
    // start() while already running returns true without rebinding.
    [[nodiscard]] bool start(const Config& cfg);
    [[nodiscard]] bool start() { return start(Config{}); }

    // Stop both listeners; closes listening sockets and joins accept
    // threads. Idempotent.
    void stop();

    [[nodiscard]] bool        running()    const noexcept { return running_.load(); }
    [[nodiscard]] std::string last_error() const;

    // Per-connection observer fired from the accept thread.
    void set_observer(ClientObserver obs);

private:
    struct Listener {
        int          fd     = -1;
        std::thread  thread;
        std::atomic<bool> stop{false};
    };

    [[nodiscard]] bool bind_listener_(Listener& l, uint16_t port,
                                      const std::string& host);
    void               accept_loop_(Listener* l, uint16_t port);
    void               handle_connection_(int fd, uint16_t port);

    Config                config_;
    Listener              steam3_;
    Listener              client_svc_;
    std::atomic<bool>     running_{false};
    size_t                snoop_bytes_ = 64;

    mutable std::mutex    err_mu_;
    std::string           last_error_;

    mutable std::mutex    obs_mu_;
    ClientObserver        observer_;
};

}  // namespace wn_steam
