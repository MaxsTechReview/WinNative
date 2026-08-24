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
    bool settling{};
    bool backing_off{};
    bool rates_settled{};
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

    void ResetCostState();

    void Reset();

private:
    using Clock = std::chrono::steady_clock;

    void Stabilize(Clock::time_point now);
    void DeferEvaluations(Clock::duration amount);
    void UpdateLimit(Clock::time_point now, float base_rate, float target_rate, size_t ceiling);
    void TrackSourceRate(Clock::time_point now, uint64_t source_frames);
    void TrackLoopRate(float interval_seconds);
    [[nodiscard]] bool RatesSettled() const;
    [[nodiscard]] size_t HeadroomLimit(size_t current, bool allow_fractional) const;
    [[nodiscard]] size_t CostLimit(Clock::time_point now, size_t current);
    void NoteLimitChange(Clock::time_point now, size_t previous_limit);

    LsfgPacerConfig config;

    std::optional<Clock::time_point> last_frame;
    std::optional<Clock::time_point> last_source_sample;
    std::optional<Clock::time_point> settle_until;
    std::optional<Clock::time_point> cost_backoff_until;
    std::optional<Clock::time_point> cost_probe_until;
    std::optional<Clock::time_point> next_cost_probe;
    float cost_probe_baseline{};
    size_t cost_probe_from{};
    size_t pre_raise_limit{};
    uint32_t cost_failures{};
    bool cost_probe_active{};
    uint64_t last_source_frames{};
    float source_interval{};
    float loop_interval{};
    float pre_raise_source_interval{};
    float pre_raise_loop_interval{};
    uint32_t source_samples{};
    uint32_t loop_samples{};
    uint64_t last_drawn{};
    float last_elapsed{};
    size_t cost_ceiling{LSFG_MAX_MULTIPLIER - 1};
    std::optional<Clock::time_point> stable_until;
    std::optional<Clock::time_point> probe_until;
    std::optional<Clock::time_point> next_probe;
    std::optional<Clock::time_point> deficit_since;
    float smoothed_interval{};
    float output_credit{};
    float probe_base_rate{};
    float unloaded_base_rate{};
    size_t issued_generations{};
    size_t probe_previous_limit{};
    size_t limit{};
    uint32_t probe_failures{};
};

}
