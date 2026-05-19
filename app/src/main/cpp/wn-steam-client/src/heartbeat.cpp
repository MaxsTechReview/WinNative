#include "wn_steam/heartbeat.h"

#include <android/log.h>

#include <exception>

namespace wn_steam {

namespace {
constexpr const char* kLogTag = "WnSteamHB";
#define WN_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)
}

Heartbeat::~Heartbeat() {
    stop();
}

bool Heartbeat::start(std::chrono::seconds interval, TickCallback cb) {
    if (interval.count() <= 0) return false;
    // If already running, stop first so we replace cleanly.
    stop();

    {
        std::lock_guard<std::mutex> lk(mu_);
        stop_requested_ = false;
    }
    running_.store(true);
    worker_ = std::thread(&Heartbeat::run, this, interval, std::move(cb));
    return true;
}

void Heartbeat::stop() {
    if (!running_.load()) return;
    {
        std::lock_guard<std::mutex> lk(mu_);
        stop_requested_ = true;
    }
    cv_.notify_all();
    if (worker_.joinable()) worker_.join();
    running_.store(false);
}

void Heartbeat::run(std::chrono::seconds interval, TickCallback cb) {
    for (;;) {
        std::unique_lock<std::mutex> lk(mu_);
        // wait_for returns true if cv was notified OR predicate is true.
        cv_.wait_for(lk, interval, [this]() { return stop_requested_; });
        if (stop_requested_) return;
        lk.unlock();

        if (cb) {
            try {
                cb();
            } catch (const std::exception& e) {
                WN_LOGE("heartbeat callback threw: %s", e.what());
            } catch (...) {
                WN_LOGE("heartbeat callback threw unknown exception");
            }
        }
    }
}

}  // namespace wn_steam
