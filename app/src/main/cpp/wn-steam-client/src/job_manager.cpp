#include "wn_steam/job_manager.h"

#include <android/log.h>

#include <chrono>
#include <vector>

namespace wn_steam {

namespace {
constexpr const char* kLogTag = "WnSteamJM";
#define WN_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)
#define WN_LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, kLogTag, __VA_ARGS__)

// Seconds since UNIX epoch at process start. Used to encode an
// approximately-unique upper bits portion of the JobID so jobids
// across multiple client sessions don't collide if the server happens
// to remember any.
uint64_t make_process_epoch() {
    using namespace std::chrono;
    return static_cast<uint64_t>(
        duration_cast<seconds>(system_clock::now().time_since_epoch()).count());
}
}  // namespace

JobManager::JobManager(std::chrono::seconds default_timeout)
    : next_counter_(1),
      process_epoch_(make_process_epoch()),
      default_timeout_(default_timeout) {
    timeout_thread_ = std::thread(&JobManager::timeout_loop, this);
}

JobManager::~JobManager() {
    stop_.store(true);
    cv_.notify_all();
    if (timeout_thread_.joinable()) timeout_thread_.join();
    // Fail any remaining outstanding jobs so callers don't leak waits.
    fail_all("JobManager shutting down");
}

uint64_t JobManager::next_job_id() noexcept {
    const uint64_t lo = next_counter_.fetch_add(1, std::memory_order_relaxed);
    // Top 24 bits = process epoch, low 40 bits = counter. 40 bits gives
    // ~1 trillion jobids — practically unlimited for a session lifetime.
    return ((process_epoch_ & 0xFFFFFFull) << 40) | (lo & 0xFFFFFFFFFFull);
}

void JobManager::track(uint64_t job_id,
                       JobContinuation cb,
                       std::chrono::seconds timeout) {
    const auto t = (timeout.count() > 0) ? timeout : default_timeout_;
    Entry e{std::move(cb), Clock::now() + t};
    {
        std::lock_guard<std::mutex> lk(mu_);
        pending_[job_id] = std::move(e);
    }
    cv_.notify_all();
}

void JobManager::deliver(uint64_t job_id_target,
                         int32_t eresult,
                         std::string error_message,
                         std::span<const uint8_t> body) {
    JobContinuation cb;
    {
        std::lock_guard<std::mutex> lk(mu_);
        auto it = pending_.find(job_id_target);
        if (it == pending_.end()) {
            __android_log_print(ANDROID_LOG_INFO, kLogTag,
                "deliver: no pending job for jobid_target=0x%llx (eresult=%d, "
                "body=%zu bytes); known pending count=%zu",
                static_cast<unsigned long long>(job_id_target),
                eresult, body.size(), pending_.size());
            return;
        }
        cb = std::move(it->second.cb);
        pending_.erase(it);
    }

    // Steam OMITS the eresult field on successful unified-method responses
    // — the proto default in our deserializer (-1) means "not present on
    // the wire," and we treat that as OK. Real failures will carry a
    // positive EResult value (Fail=2, InvalidPassword=5, etc.).
    if (eresult == -1) eresult = 1;  // EResult.OK
    JobResult r;
    r.eresult       = eresult;
    r.error_message = std::move(error_message);
    r.body.assign(body.begin(), body.end());
    if (cb) {
        try {
            cb(std::move(r));
        } catch (const std::exception& e) {
            WN_LOGE("job continuation threw: %s", e.what());
        } catch (...) {
            WN_LOGE("job continuation threw unknown exception");
        }
    }
}

void JobManager::fail_all(const std::string& reason) {
    std::unordered_map<uint64_t, Entry> drained;
    {
        std::lock_guard<std::mutex> lk(mu_);
        drained.swap(pending_);
    }
    for (auto& [_, e] : drained) {
        if (!e.cb) continue;
        JobResult r;
        r.eresult           = -1;
        r.error_message     = reason;
        r.synthetic_failure = true;
        try { e.cb(std::move(r)); } catch (...) {}
    }
}

void JobManager::timeout_loop() {
    while (!stop_.load()) {
        std::vector<std::pair<uint64_t, JobContinuation>> expired;

        {
            std::unique_lock<std::mutex> lk(mu_);
            // Sleep until the earliest job deadline, or indefinitely when no
            // job is pending. A fixed 1s tick here woke the CPU ~86,400x/day
            // for nothing — an idle session must not wake at all. track()
            // notifies cv_ whenever a job is added, so a fresh job is picked
            // up immediately even out of the indefinite wait.
            if (pending_.empty()) {
                cv_.wait(lk, [this]() {
                    return stop_.load() || !pending_.empty();
                });
            } else {
                Clock::time_point earliest = Clock::time_point::max();
                for (const auto& kv : pending_) {
                    if (kv.second.deadline < earliest) earliest = kv.second.deadline;
                }
                cv_.wait_until(lk, earliest, [this]() { return stop_.load(); });
            }
            if (stop_.load()) return;

            const auto now = Clock::now();
            for (auto it = pending_.begin(); it != pending_.end(); ) {
                if (it->second.deadline <= now) {
                    expired.emplace_back(it->first, std::move(it->second.cb));
                    it = pending_.erase(it);
                } else {
                    ++it;
                }
            }
        }

        for (auto& [job_id, cb] : expired) {
            if (!cb) continue;
            JobResult r;
            r.eresult           = -1;
            r.error_message     = "job timeout";
            r.synthetic_failure = true;
            try {
                cb(std::move(r));
            } catch (const std::exception& e) {
                WN_LOGE("timeout continuation threw: %s", e.what());
            } catch (...) {}
        }
    }
}

}  // namespace wn_steam
