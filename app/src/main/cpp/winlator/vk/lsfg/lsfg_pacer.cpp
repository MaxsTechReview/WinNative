// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

#include "lsfg_pacer.hpp"

#include <algorithm>
#include <cmath>

namespace lsfg {

namespace {

using Clock = std::chrono::steady_clock;

constexpr float INTERVAL_SMOOTHING = 0.25f;
constexpr float SOURCE_SMOOTHING = 0.15f;
constexpr float SOURCE_STALE_SECONDS = 0.5f;
constexpr float DISCONTINUITY_SECONDS = 0.25f;
constexpr float HEADROOM_EPSILON = 0.02f;
constexpr float HEADROOM_HYSTERESIS = 0.20f;
constexpr float CREDIT_EPSILON = 1.0e-4f;
constexpr float SOURCE_ACCUM_FLOOR = 0.01f;
constexpr uint32_t MIN_RATE_SAMPLES = 12;

constexpr float REGRESSION_RATIO = 1.20f;
constexpr float COST_PROBE_GAIN = 0.10f;
constexpr uint32_t MAX_COST_FAILURES = 4;

constexpr auto RAISE_SETTLE_DURATION = std::chrono::milliseconds(600);
constexpr auto COST_PROBE_INTERVAL = std::chrono::seconds(15);
constexpr auto COST_PROBE_WINDOW = std::chrono::milliseconds(700);

[[nodiscard]] Clock::duration CostBackoff(uint32_t failures) {
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
    if (elapsed <= 0.0f) {
        return;
    }

    last_source_sample = now;
    const uint64_t drawn =
        source_frames > last_source_frames ? source_frames - last_source_frames : 0;
    last_source_frames = source_frames;

    if (elapsed > SOURCE_STALE_SECONDS) {
        source_frame_accum = 0.0f;
        source_time_accum = 0.0f;
        source_interval = 0.0f;
        source_samples = 0;
        return;
    }

    last_drawn = drawn;
    last_elapsed = elapsed;
    source_frame_accum += (static_cast<float>(drawn) - source_frame_accum) * SOURCE_SMOOTHING;
    source_time_accum += (elapsed - source_time_accum) * SOURCE_SMOOTHING;
    source_interval =
        source_frame_accum > SOURCE_ACCUM_FLOOR ? source_time_accum / source_frame_accum : 0.0f;
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

size_t LsfgPacer::HeadroomLimit(size_t current, bool allow_fractional) const {
    if (config.refresh_rate <= 0.0f || source_interval <= 0.0f ||
        source_samples < MIN_RATE_SAMPLES) {
        return LSFG_MAX_MULTIPLIER - 1;
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
    if (governed_limit > previous_limit) {
        if (!RatesSettled()) {
            settle_until.reset();
            return;
        }
        pre_raise_source_interval = source_interval;
        pre_raise_loop_interval = loop_interval;
        pre_raise_limit = previous_limit;
        settle_until = now + RAISE_SETTLE_DURATION;
    } else if (governed_limit < previous_limit) {
        settle_until.reset();
    }
}

size_t LsfgPacer::Govern(Clock::time_point now, size_t ceiling, bool allow_fractional) {
    const size_t previous = governed_limit;
    governed_limit = std::min({ceiling, HeadroomLimit(limit, allow_fractional),
                               CostLimit(now, governed_limit)});
    NoteLimitChange(now, previous);
    return governed_limit;
}

LsfgPlan LsfgPacer::Plan(size_t capacity, uint64_t source_frames) {
    const size_t ceiling = std::min(capacity, MaxGenerations());
    if (ceiling == 0) {
        Reset();
        return {};
    }

    const Clock::time_point now = Clock::now();
    TrackSourceRate(now, source_frames);
    if (!last_frame) {
        last_frame = now;
        return {};
    }

    const float interval_seconds = std::chrono::duration<float>(now - *last_frame).count();
    last_frame = now;

    if (interval_seconds <= 0.0f || interval_seconds > DISCONTINUITY_SECONDS) {
        output_credit = 0.0f;
        return LsfgPlan{0, true};
    }

    TrackLoopRate(interval_seconds);

    float target_rate = static_cast<float>(config.target_rate);
    if (target_rate > 0.0f && config.refresh_rate > 0.0f) {
        target_rate = std::min(target_rate, config.refresh_rate);
    }

    if (target_rate == 0.0f) {
        output_credit = 0.0f;
        limit = Govern(now, ceiling, false);
        return LsfgPlan{limit, limit > 0};
    }

    const size_t allowed = Govern(now, ceiling, true);
    const float desired_outputs = loop_interval * target_rate;
    if (allowed == 0 || desired_outputs <= 1.0f) {
        output_credit = 0.0f;
        limit = 0;
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

    limit = generations;
    return LsfgPlan{generations, true};
}

LsfgPacerStats LsfgPacer::Stats() const {
    LsfgPacerStats stats;
    stats.source_rate = source_interval > 0.0f ? 1.0f / source_interval : 0.0f;
    stats.loop_rate = loop_interval > 0.0f ? 1.0f / loop_interval : 0.0f;
    stats.refresh_rate = config.refresh_rate;
    stats.target_rate = static_cast<float>(config.target_rate);
    stats.slots = config.refresh_rate * source_interval;
    stats.limit = limit;
    stats.cost_ceiling = cost_ceiling;
    stats.rates_settled = RatesSettled();
    stats.settling = settle_until.has_value();
    stats.backing_off = cost_backoff_until.has_value();
    stats.probing = cost_probe_until.has_value();
    stats.cost_failures = cost_failures;
    stats.last_drawn = last_drawn;
    stats.last_elapsed = last_elapsed;
    stats.source_frames = last_source_frames;
    return stats;
}

void LsfgPacer::Reset() {
    last_frame.reset();
    last_source_sample.reset();
    settle_until.reset();
    cost_backoff_until.reset();
    cost_probe_until.reset();
    next_cost_probe.reset();
    last_source_frames = 0;
    source_interval = 0.0f;
    source_frame_accum = 0.0f;
    source_time_accum = 0.0f;
    loop_interval = 0.0f;
    cost_probe_baseline = 0.0f;
    pre_raise_source_interval = 0.0f;
    pre_raise_loop_interval = 0.0f;
    source_samples = 0;
    loop_samples = 0;
    cost_failures = 0;
    last_drawn = 0;
    last_elapsed = 0.0f;
    output_credit = 0.0f;
    limit = 0;
    governed_limit = LSFG_MAX_MULTIPLIER - 1;
    cost_probe_from = 0;
    pre_raise_limit = 0;
    cost_ceiling = LSFG_MAX_MULTIPLIER - 1;
    cost_probe_active = false;
}

}
