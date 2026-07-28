/*
 *     Copyright (C) 2019  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include <cmath>
#include "fpssync.h"
#include "log.h"

namespace libretrodroid {

unsigned FPSSync::advanceFrames() {
    if (useVSync) {
        if (vsyncMultiple <= 1) return 1;
        tickCounter = (tickCounter + 1) % vsyncMultiple;
        return tickCounter == 0 ? 1 : 0;
    }

    if (lastFrame == MIN_TIME) {
        start();
    }

    auto now = std::chrono::steady_clock::now();
    auto frames = std::max((now - lastFrame) / sampleInterval, (long long) 1);
    lastFrame = lastFrame + sampleInterval * frames;

    return frames;
}

FPSSync::FPSSync(double contentRefreshRate, double screenRefreshRate) {
    this->contentRefreshRate = contentRefreshRate;
    this->screenRefreshRate = screenRefreshRate;
    this->useVSync = std::abs(contentRefreshRate - screenRefreshRate) < FPS_TOLERANCE;
    if (!useVSync && contentRefreshRate > 0) {
        auto multiple = (unsigned) std::lround(screenRefreshRate / contentRefreshRate);
        if (multiple >= 2 &&
            std::abs(screenRefreshRate - multiple * contentRefreshRate) < FPS_TOLERANCE * multiple) {
            this->useVSync = true;
            this->vsyncMultiple = multiple;
        }
    }
    this->sampleInterval = std::chrono::microseconds((long) ((1000000L / contentRefreshRate)));
    LOGI("FPS sync: content %f screen %f vsync: %d multiple: %d", contentRefreshRate, screenRefreshRate, useVSync, vsyncMultiple);
    reset();
}

void FPSSync::start() {
    LOGI("Starting game with fps %f on a screen with refresh rate %f. Using vsync: %d multiple: %d", contentRefreshRate, screenRefreshRate, useVSync, vsyncMultiple);
    lastFrame = std::chrono::steady_clock::now();
}

void FPSSync::reset() {
    lastFrame = MIN_TIME;
}

double FPSSync::getTimeStretchFactor() {
    return useVSync ? (contentRefreshRate * vsyncMultiple) / screenRefreshRate : 1.0;
}

void FPSSync::wait() {
    if (useVSync) return;
    std::this_thread::sleep_until(lastFrame);
}

} //namespace libretrodroid
