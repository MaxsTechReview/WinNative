#pragma once

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <functional>
#include <memory>
#include <mutex>
#include <optional>
#include <span>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

namespace wn_steam {

// Result delivered to a job continuation. Carries the EResult from the
// CMsgProtoBufHeader (or the AsyncJobFailed sentinel on timeout/disconnect)
// plus the raw response body for the caller to decode as the specific
// service-method response proto.
struct JobResult {
    int32_t                  eresult = 2;  // Invalid
    std::string              error_message;
    std::vector<uint8_t>     body;
    // Set true if this is a synthetic failure (timeout or disconnect)
    // rather than an actual server response.
    bool                     synthetic_failure = false;
};

using JobContinuation = std::function<void(JobResult)>;

// Generates and tracks job IDs for service-method calls and other
// request/response correlation. Mirrors SteamKit2's `JobID` + `AsyncJobManager`.
//
// JobID layout (per SteamKit2 source-of-truth):
//   bits 63    sequence-marker flag
//   bits 62..56 process-start timestamp encoded as 7 bits
//   bits 55..0  monotonic counter
//
// We use a simpler scheme that still avoids collisions: process-start
// epoch time in the upper bits, atomic counter in the lower bits. The
// only invariant Steam relies on is that the same jobid never repeats
// within a session.
class JobManager {
public:
    using Clock = std::chrono::steady_clock;

    explicit JobManager(std::chrono::seconds default_timeout = std::chrono::seconds{30});
    ~JobManager();

    JobManager(const JobManager&)            = delete;
    JobManager& operator=(const JobManager&) = delete;

    // Allocate a fresh jobid_source for a new outbound request.
    [[nodiscard]] uint64_t next_job_id() noexcept;

    // Register a continuation to fire when the response arrives. If the
    // server does not respond within `timeout`, the continuation is fired
    // with `synthetic_failure = true`. `timeout` of 0 uses default.
    void track(uint64_t job_id,
               JobContinuation cb,
               std::chrono::seconds timeout = std::chrono::seconds{0});

    // Called when a response arrives. Locates the continuation by jobid,
    // removes it from the tracking map, and fires it. No-op if unknown.
    void deliver(uint64_t job_id_target,
                 int32_t eresult,
                 std::string error_message,
                 std::span<const uint8_t> body);

    // Fail all outstanding jobs synthetically. Called on disconnect.
    void fail_all(const std::string& reason);

private:
    struct Entry {
        JobContinuation     cb;
        Clock::time_point   deadline;
    };

    void timeout_loop();

    std::atomic<uint64_t>                       next_counter_;
    const uint64_t                              process_epoch_;
    std::chrono::seconds                        default_timeout_;

    mutable std::mutex                          mu_;
    std::unordered_map<uint64_t, Entry>         pending_;

    std::atomic<bool>                           stop_{false};
    std::thread                                 timeout_thread_;
    std::condition_variable                     cv_;
};

}  // namespace wn_steam
