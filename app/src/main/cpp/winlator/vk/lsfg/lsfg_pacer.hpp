// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

#pragma once

#include <chrono>
#include <cstddef>
#include <cstdint>
#include <optional>

namespace lsfg {

constexpr size_t LSFG_MAX_MULTIPLIER = 4;

struct LsfgPacerConfig {
    uint32_t multiplier{2};
    uint32_t target_rate{};
    float refresh_rate{};
};

struct LsfgPlan {
    size_t generations{};
    bool warm{};
};

struct LsfgPacerStats {
    float source_rate{};
    float loop_rate{};
    float refresh_rate{};
    float target_rate{};
    float slots{};
    size_t limit{};
    size_t cost_ceiling{};
    bool rates_settled{};
    bool settling{};
    bool backing_off{};
    bool probing{};
    uint32_t cost_failures{};
    uint64_t last_drawn{};
    float last_elapsed{};
    uint64_t source_frames{};
};

class LsfgPacer {
public:
    void SetConfig(const LsfgPacerConfig& config_) {
        config = config_;
    }

    [[nodiscard]] const LsfgPacerConfig& Config() const {
        return config;
    }

    [[nodiscard]] size_t MaxGenerations() const;

    [[nodiscard]] LsfgPlan Plan(size_t capacity, uint64_t source_frames);

    [[nodiscard]] LsfgPacerStats Stats() const;

    void Reset();

private:
    using Clock = std::chrono::steady_clock;

    void TrackSourceRate(Clock::time_point now, uint64_t source_frames);
    void TrackLoopRate(float interval_seconds);
    [[nodiscard]] bool RatesSettled() const;
    [[nodiscard]] size_t HeadroomLimit(size_t current, bool allow_fractional) const;
    [[nodiscard]] size_t CostLimit(Clock::time_point now, size_t current);
    void NoteLimitChange(Clock::time_point now, size_t previous_limit);
    [[nodiscard]] size_t Govern(Clock::time_point now, size_t ceiling, bool allow_fractional);

    LsfgPacerConfig config;

    std::optional<Clock::time_point> last_frame;
    std::optional<Clock::time_point> last_source_sample;
    std::optional<Clock::time_point> settle_until;
    std::optional<Clock::time_point> cost_backoff_until;
    std::optional<Clock::time_point> cost_probe_until;
    std::optional<Clock::time_point> next_cost_probe;
    uint64_t last_source_frames{};
    float source_interval{};
    float source_frame_accum{};
    float source_time_accum{};
    float loop_interval{};
    float cost_probe_baseline{};
    float pre_raise_source_interval{};
    float pre_raise_loop_interval{};
    uint32_t source_samples{};
    uint32_t loop_samples{};
    uint32_t cost_failures{};
    uint64_t last_drawn{};
    float last_elapsed{};
    float output_credit{};
    size_t limit{};
    size_t governed_limit{LSFG_MAX_MULTIPLIER - 1};
    size_t cost_probe_from{};
    size_t pre_raise_limit{};
    size_t cost_ceiling{LSFG_MAX_MULTIPLIER - 1};
    bool cost_probe_active{};
};

}
