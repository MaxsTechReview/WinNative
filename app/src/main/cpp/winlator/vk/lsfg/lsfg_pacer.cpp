// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

#include "lsfg_pacer.hpp"

#include <algorithm>
#include <cmath>
#include <utility>

namespace lsfg {

namespace {

using Clock = std::chrono::steady_clock;

constexpr float INTERVAL_SMOOTHING = 0.25f;
constexpr float MINIMUM_BASE_RATE = 10.0f;
constexpr float FIXED_DISCONTINUITY_SECONDS = 0.25f;
constexpr float BURST_CADENCE_RATIO = 3.0f;
constexpr float BURST_TARGET_RATIO = 2.0f;
constexpr float PROBE_THROUGHPUT_TOLERANCE = 0.95f;
constexpr float PROBE_BASE_COLLAPSE_RATIO = 0.70f;
constexpr float PROBE_MARGINAL_GAIN = 1.15f;
constexpr float TARGET_SATISFIED_RATIO = 0.95f;
constexpr float UNLOADED_BASE_RETENTION = 0.75f;
constexpr float CREDIT_EPSILON = 1.0e-4f;
constexpr float SOURCE_SMOOTHING = 0.15f;
constexpr float SOURCE_STALE_SECONDS = 0.5f;
constexpr float HEADROOM_EPSILON = 0.02f;
constexpr float HEADROOM_HYSTERESIS = 0.20f;
constexpr float REGRESSION_RATIO = 1.20f;
constexpr uint32_t MIN_RATE_SAMPLES = 20;
constexpr uint32_t MAX_PROBE_FAILURES = 4;

constexpr float COST_PROBE_GAIN = 0.10f;

constexpr auto RAISE_SETTLE_DURATION = std::chrono::milliseconds(600);
constexpr uint32_t MAX_COST_FAILURES = 4;

[[nodiscard]] std::chrono::steady_clock::duration CostBackoff(uint32_t failures) {
    switch (failures) {
    case 0:
    case 1:
        return std::chrono::seconds(8);
    case 2:
        return std::chrono::seconds(20);
    case 3:
        return std::chrono::seconds(45);
    default:
        return std::chrono::seconds(90);
    }
}
constexpr auto COST_PROBE_INTERVAL = std::chrono::seconds(15);
constexpr auto COST_PROBE_WINDOW = std::chrono::milliseconds(700);

constexpr auto STABILIZATION_DURATION = std::chrono::seconds(1);
constexpr auto PROBE_DURATION = std::chrono::seconds(1);
constexpr auto DEFICIT_DURATION = std::chrono::seconds(1);
constexpr auto PROBE_STEP_DELAY = std::chrono::milliseconds(250);

[[nodiscard]] Clock::duration ProbeBackoff(uint32_t failures) {
    switch (failures) {
    case 1:
        return std::chrono::seconds(5);
    case 2:
        return std::chrono::seconds(15);
    case 3:
        return std::chrono::seconds(30);
    default:
        return std::chrono::seconds(60);
    }
}

}

size_t LsfgPacer::MaxGenerations() const {
    if (config.multiplier < 2) return 0;
    if (config.target_rate != 0) return LSFG_MAX_MULTIPLIER - 1;
    return std::min<size_t>(config.multiplier, LSFG_MAX_MULTIPLIER) - 1;
}

void LsfgPacer::TrackSourceRate(Clock::time_point now, uint64_t source_frames) {
    if (!last_source_sample) {
        last_source_sample = now;
        last_source_frames = source_frames;
        return;
    }

    const float elapsed = std::chrono::duration<float>(now - *last_source_sample).count();
    if (source_frames <= last_source_frames) {
        if (elapsed > SOURCE_STALE_SECONDS) {
            source_interval = 0.0f;
            last_source_sample = now;
            last_source_frames = source_frames;
        }
        return;
    }

    last_source_sample = now;
    const uint64_t drawn = source_frames - last_source_frames;
    last_source_frames = source_frames;
    if (elapsed <= 0.0f || elapsed > SOURCE_STALE_SECONDS) {
        source_interval = 0.0f;
        return;
    }

    last_drawn = drawn;
    last_elapsed = elapsed;
    const float measured = elapsed / static_cast<float>(drawn);
    source_interval = source_interval > 0.0f
                          ? source_interval + (measured - source_interval) * SOURCE_SMOOTHING
                          : measured;
    if (source_samples < MIN_RATE_SAMPLES) ++source_samples;
}

void LsfgPacer::TrackLoopRate(float interval_seconds) {
    loop_interval = loop_interval > 0.0f
                        ? loop_interval + (interval_seconds - loop_interval) * INTERVAL_SMOOTHING
                        : interval_seconds;
    if (loop_samples < MIN_RATE_SAMPLES) ++loop_samples;
}

bool LsfgPacer::RatesSettled() const {
    return source_samples >= MIN_RATE_SAMPLES && loop_samples >= MIN_RATE_SAMPLES;
}

size_t LsfgPacer::CostLimit(Clock::time_point now, size_t current) {
    if (cost_probe_until) {
        const size_t probe_limit = cost_probe_from > 0 ? cost_probe_from - 1 : 0;
        if (now < *cost_probe_until) {
            cost_probe_active = true;
            return probe_limit;
        }
        cost_probe_until.reset();
        cost_probe_active = true;
        if (cost_probe_baseline > 0.0f && loop_interval > 0.0f &&
            loop_interval < cost_probe_baseline * (1.0f - COST_PROBE_GAIN)) {
            cost_failures = std::min(cost_failures + 1, MAX_COST_FAILURES);
            cost_ceiling = probe_limit;
            cost_backoff_until = now + CostBackoff(cost_failures);
            return cost_ceiling;
        }
    } else {
        cost_probe_active = false;
    }

    if (settle_until) {
        if (now < *settle_until) {
            return current;
        }
        settle_until.reset();

        const bool source_regressed =
            pre_raise_source_interval > 0.0f && source_interval > 0.0f &&
            source_interval > pre_raise_source_interval * REGRESSION_RATIO;
        const bool loop_regressed = pre_raise_loop_interval > 0.0f && loop_interval > 0.0f &&
                                    loop_interval > pre_raise_loop_interval * REGRESSION_RATIO;

        if (source_regressed || loop_regressed) {
            cost_failures = std::min(cost_failures + 1, MAX_COST_FAILURES);
            cost_ceiling = pre_raise_limit;
            cost_backoff_until = now + CostBackoff(cost_failures);
            return cost_ceiling;
        }
        cost_failures = 0;
    }

    if (cost_backoff_until) {
        if (now < *cost_backoff_until) {
            return cost_ceiling;
        }
        cost_backoff_until.reset();
        cost_ceiling = LSFG_MAX_MULTIPLIER - 1;
    }

    if (current > 0 && !settle_until && RatesSettled()) {
        if (!next_cost_probe) {
            next_cost_probe = now + COST_PROBE_INTERVAL;
        } else if (now >= *next_cost_probe) {
            cost_probe_baseline = loop_interval;
            cost_probe_from = current;
            cost_probe_until = now + COST_PROBE_WINDOW;
            next_cost_probe = now + COST_PROBE_INTERVAL;
            cost_probe_active = true;
            return current - 1;
        }
    }

    return cost_ceiling;
}

void LsfgPacer::NoteLimitChange(Clock::time_point now, size_t previous_limit) {
    if (cost_probe_active) {
        settle_until.reset();
        return;
    }
    if (limit > previous_limit) {
        if (!RatesSettled()) {
            settle_until.reset();
            return;
        }
        pre_raise_source_interval = source_interval;
        pre_raise_loop_interval = loop_interval;
        pre_raise_limit = previous_limit;
        settle_until = now + RAISE_SETTLE_DURATION;
    } else if (limit < previous_limit) {
        settle_until.reset();
    }
}

size_t LsfgPacer::HeadroomLimit(size_t current, bool allow_fractional) const {
    if (config.refresh_rate <= 0.0f || source_interval <= 0.0f ||
        source_samples < MIN_RATE_SAMPLES) {
        return 0;
    }

    const float slots = config.refresh_rate * source_interval;

    if (allow_fractional) {
        const float budget = std::ceil(slots - HEADROOM_EPSILON);
        return budget < 2.0f ? 0 : static_cast<size_t>(budget) - 1;
    }

    float budget = slots + HEADROOM_EPSILON;
    if (current > 0 && slots + HEADROOM_HYSTERESIS >= static_cast<float>(current + 1)) {
        budget = std::max(budget, static_cast<float>(current + 1));
    }
    if (budget < 2.0f) {
        return 0;
    }
    return static_cast<size_t>(std::floor(budget)) - 1;
}

LsfgPlan LsfgPacer::Plan(size_t capacity, uint64_t source_frames) {
    const size_t ceiling = std::min(capacity, MaxGenerations());
    if (ceiling == 0) {
        Reset();
        return {};
    }

    const Clock::time_point now = Clock::now();
    TrackSourceRate(now, source_frames);
    const size_t previous_generations = std::exchange(issued_generations, 0);
    if (!last_frame) {
        last_frame = now;
        return {};
    }

    const Clock::duration interval = now - *last_frame;
    const float interval_seconds = std::chrono::duration<float>(interval).count();
    last_frame = now;

    if (interval_seconds <= 0.0f) {
        Stabilize(now);
        return {};
    }

    float target_rate = static_cast<float>(config.target_rate);
    if (target_rate > 0.0f && config.refresh_rate > 0.0f) {
        target_rate = std::min(target_rate, config.refresh_rate);
    }

    if (interval_seconds <= FIXED_DISCONTINUITY_SECONDS) {
        TrackLoopRate(interval_seconds);
    }

    if (target_rate == 0.0f) {
        output_credit = 0.0f;
        if (interval_seconds > FIXED_DISCONTINUITY_SECONDS) {
            issued_generations = 0;
            return {};
        }
        const size_t previous_limit = limit;
        limit = std::min({ceiling, HeadroomLimit(limit, false), CostLimit(now, limit)});
        NoteLimitChange(now, previous_limit);
        issued_generations = limit;
        return LsfgPlan{limit, limit > 0};
    }

    if (smoothed_interval > 0.0f) {
        float burst_threshold = BURST_CADENCE_RATIO / smoothed_interval;
        if (target_rate > 0.0f) {
            burst_threshold = std::max(burst_threshold, target_rate * BURST_TARGET_RATIO);
        }
        if (1.0f / interval_seconds > burst_threshold) {
            DeferEvaluations(interval);
            output_credit = 0.0f;
            return {};
        }
    }

    if (interval_seconds > 1.0f / MINIMUM_BASE_RATE) {
        Stabilize(now);
        return {};
    }

    smoothed_interval = smoothed_interval > 0.0f
                            ? smoothed_interval +
                                  (interval_seconds - smoothed_interval) * INTERVAL_SMOOTHING
                            : interval_seconds;

    if (previous_generations == 0) {
        const float measured = 1.0f / smoothed_interval;
        unloaded_base_rate =
            unloaded_base_rate > 0.0f
                ? unloaded_base_rate + (measured - unloaded_base_rate) * INTERVAL_SMOOTHING
                : measured;
    }

    if (stable_until) {
        if (now < *stable_until) {
            return {};
        }
        stable_until.reset();
    }

    UpdateLimit(now, 1.0f / smoothed_interval, target_rate, ceiling);

    const size_t allowed = std::min({limit, ceiling, HeadroomLimit(limit, true)});
    const float desired_outputs = smoothed_interval * target_rate;
    if (allowed == 0 || desired_outputs <= 1.0f) {
        output_credit = 0.0f;
        return {};
    }

    output_credit += desired_outputs;
    const size_t outputs =
        std::max<size_t>(1, static_cast<size_t>(std::floor(output_credit + CREDIT_EPSILON)));
    const size_t generations = std::min(outputs - 1, allowed);

    output_credit -= static_cast<float>(generations + 1);
    if (output_credit < 0.0f) {
        output_credit = 0.0f;
    } else if (generations == allowed && output_credit >= 1.0f) {
        output_credit = std::fmod(output_credit, 1.0f);
    }

    issued_generations = generations;
    return LsfgPlan{generations, true};
}

void LsfgPacer::UpdateLimit(Clock::time_point now, float base_rate, float target_rate,
                            size_t ceiling) {
    limit = std::min(limit, ceiling);

    if (probe_until) {
        if (now < *probe_until) {
            return;
        }
        probe_until.reset();
        output_credit = 0.0f;

        const float previous_output =
            std::min(target_rate, probe_base_rate * static_cast<float>(probe_previous_limit + 1));
        const float current_output =
            std::min(target_rate, base_rate * static_cast<float>(limit + 1));

        const bool throughput_regressed =
            current_output < previous_output * PROBE_THROUGHPUT_TOLERANCE;
        const bool collapsed_for_marginal_gain =
            base_rate < probe_base_rate * PROBE_BASE_COLLAPSE_RATIO &&
            current_output < previous_output * PROBE_MARGINAL_GAIN;
        const bool emulation_slowed = unloaded_base_rate > 0.0f &&
                                      base_rate < unloaded_base_rate * UNLOADED_BASE_RETENTION;

        if (throughput_regressed || collapsed_for_marginal_gain || emulation_slowed) {
            limit = probe_previous_limit;
            probe_failures = std::min(probe_failures + 1, MAX_PROBE_FAILURES);
            next_probe = now + ProbeBackoff(probe_failures);
            deficit_since.reset();
            return;
        }

        probe_failures = 0;
        next_probe = now + PROBE_STEP_DELAY;
    }

    if (base_rate * static_cast<float>(limit + 1) >= target_rate * TARGET_SATISFIED_RATIO ||
        limit >= ceiling) {
        deficit_since.reset();
        return;
    }

    if (!deficit_since) {
        deficit_since = now;
        return;
    }
    if (now - *deficit_since < DEFICIT_DURATION) {
        return;
    }
    if (next_probe && now < *next_probe) {
        return;
    }

    probe_previous_limit = limit;
    probe_base_rate = base_rate;
    ++limit;
    probe_until = now + PROBE_DURATION;
    deficit_since.reset();
    output_credit = 0.0f;
}

void LsfgPacer::DeferEvaluations(Clock::duration amount) {
    const auto defer = [amount](std::optional<Clock::time_point>& deadline) {
        if (deadline) {
            *deadline += amount;
        }
    };
    defer(stable_until);
    defer(probe_until);
    defer(next_probe);
    deficit_since.reset();
}

void LsfgPacer::Stabilize(Clock::time_point now) {
    stable_until = now + STABILIZATION_DURATION;
    probe_until.reset();
    deficit_since.reset();
    smoothed_interval = 0.0f;
    output_credit = 0.0f;
}

LsfgPacerStats LsfgPacer::Stats() const {
    LsfgPacerStats stats;
    stats.source_rate = source_interval > 0.0f ? 1.0f / source_interval : 0.0f;
    stats.loop_rate = loop_interval > 0.0f ? 1.0f / loop_interval : 0.0f;
    stats.refresh_rate = config.refresh_rate;
    stats.slots = config.refresh_rate * source_interval;
    stats.target_rate = static_cast<float>(config.target_rate);
    stats.limit = limit;
    stats.cost_ceiling = cost_ceiling;
    stats.settling = settle_until.has_value();
    stats.backing_off = cost_backoff_until.has_value();
    stats.rates_settled = RatesSettled();
    stats.probing = cost_probe_until.has_value();
    stats.cost_failures = cost_failures;
    stats.last_drawn = last_drawn;
    stats.last_elapsed = last_elapsed;
    stats.source_frames = last_source_frames;
    return stats;
}

void LsfgPacer::ResetCostState() {
    settle_until.reset();
    cost_backoff_until.reset();
    cost_probe_until.reset();
    next_cost_probe.reset();
    cost_probe_baseline = 0.0f;
    cost_probe_from = 0;
    pre_raise_limit = 0;
    cost_probe_active = false;
    cost_failures = 0;
    pre_raise_source_interval = 0.0f;
    pre_raise_loop_interval = 0.0f;
    cost_ceiling = LSFG_MAX_MULTIPLIER - 1;
}

void LsfgPacer::Reset() {
    last_frame.reset();
    last_source_sample.reset();
    settle_until.reset();
    cost_backoff_until.reset();
    cost_probe_until.reset();
    next_cost_probe.reset();
    cost_probe_baseline = 0.0f;
    cost_probe_from = 0;
    pre_raise_limit = 0;
    cost_probe_active = false;
    cost_failures = 0;
    last_source_frames = 0;
    source_interval = 0.0f;
    loop_interval = 0.0f;
    pre_raise_source_interval = 0.0f;
    pre_raise_loop_interval = 0.0f;
    source_samples = 0;
    loop_samples = 0;
    cost_ceiling = LSFG_MAX_MULTIPLIER - 1;
    stable_until.reset();
    probe_until.reset();
    next_probe.reset();
    deficit_since.reset();
    smoothed_interval = 0.0f;
    output_credit = 0.0f;
    probe_base_rate = 0.0f;
    unloaded_base_rate = 0.0f;
    issued_generations = 0;
    probe_previous_limit = 0;
    limit = 0;
    probe_failures = 0;
}

}
