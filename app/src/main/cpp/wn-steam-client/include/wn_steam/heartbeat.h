#pragma once

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <functional>
#include <mutex>
#include <thread>

namespace wn_steam {

// Periodic callback runner with cancellable sleep. Used to drive
// `CMsgClientHeartBeat` at the interval the server hands back in
// `CMsgClientLogonResponse.heartbeat_seconds` (~9 s typical).
//
// Thread model:
//   - start()/stop() are called from the I/O thread.
//   - The callback fires on the heartbeat's own thread; it must not
//     block more than a single tick.
//   - stop() blocks until the worker thread joins (synchronous).
class Heartbeat {
public:
    using TickCallback = std::function<void()>;

    Heartbeat() = default;
    ~Heartbeat();

    Heartbeat(const Heartbeat&)            = delete;
    Heartbeat& operator=(const Heartbeat&) = delete;

    // Begin firing `cb` every `interval`. Replaces any prior schedule.
    // Returns false if `interval` is zero (treated as misconfiguration).
    [[nodiscard]] bool start(std::chrono::seconds interval, TickCallback cb);

    // Stops the worker. Safe to call multiple times. Returns immediately
    // if not started.
    void stop();

    [[nodiscard]] bool running() const noexcept { return running_.load(); }

private:
    void run(std::chrono::seconds interval, TickCallback cb);

    std::atomic<bool>          running_{false};
    std::thread                worker_;
    std::mutex                 mu_;
    std::condition_variable    cv_;
    bool                       stop_requested_ = false;
};

}  // namespace wn_steam
