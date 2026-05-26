#pragma once

#include <atomic>
#include <cstdint>
#include <functional>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace wn_steam {

// Local TCP endpoints used by Wine's lsteamclient.dll. Endpoint addresses are
// exported through env vars; see WnWineEnvVars.kt.
class WineBridge {
public:
    using ClientObserver = std::function<void(int port,
                                              std::string peer,
                                              std::vector<uint8_t> first_bytes)>;

    struct Config {
        std::string bind_host        = "127.0.0.1";
        uint16_t    steam3_port      = 57343;   // SteamWorks main RPC.
        uint16_t    client_svc_port  = 57344;   // SteamClient secondary RPC.
        // Captured peek bytes per connection.
        size_t      snoop_bytes      = 64;
    };

    WineBridge();
    ~WineBridge();

    WineBridge(const WineBridge&)            = delete;
    WineBridge& operator=(const WineBridge&) = delete;

    // Start both listeners. Idempotent; failure details are in last_error().
    [[nodiscard]] bool start(const Config& cfg);
    [[nodiscard]] bool start() { return start(Config{}); }

    // Stop both listeners and join accept threads. Idempotent.
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
