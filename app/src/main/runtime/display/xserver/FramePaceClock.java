package com.winlator.cmod.runtime.display.xserver;

import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;

/**
 * Tracks the display's vsync phase and refresh rate so the FPS-cap pacer
 * ({@link XClient#enforceAbsoluteFramerate()}) can align frame release to the live vsync grid
 * instead of a free-running wall clock. This removes the beat between the software cap timer and
 * the FIFO present clock that caused microstutter at low caps (e.g. 30 fps on 90/120 Hz panels).
 *
 * <p>Shared fields are written on the UI thread (Choreographer callback + activity) and read on
 * the X-dispatch thread; they are {@code volatile} and never read-modify-written across each
 * other, so the pacer's hot path needs no lock. The Choreographer callback only reposts while a
 * cap is active, so there is no per-vsync UI wakeup when the cap is off.
 */
public class FramePaceClock {
    private volatile long lastVsyncNanos = 0;
    private volatile float displayRefreshHz = 0f;
    private volatile boolean capActive = false;
    // Touched only on the UI thread (startTracking body + onVsync), so it needs no synchronization.
    private boolean tracking = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Choreographer.FrameCallback frameCallback = this::onVsync;

    /** Latest vsync timestamp in the {@link System#nanoTime()} timebase (0 until the first frame). */
    public long getLastVsyncNanos() {
        return lastVsyncNanos;
    }

    /** Effective display refresh rate in Hz (0 if unknown). */
    public float getDisplayRefreshHz() {
        return displayRefreshHz;
    }

    public void setDisplayRefreshHz(float hz) {
        displayRefreshHz = hz > 0f ? hz : 0f;
    }

    /** Arms vsync tracking while a cap is set; disarms it (stopping UI wakeups) when the cap clears. */
    public void setCapActive(boolean on) {
        capActive = on;
        if (on) startTracking();
    }

    private void startTracking() {
        mainHandler.post(() -> {
            if (tracking) return;
            tracking = true;
            Choreographer.getInstance().postFrameCallback(frameCallback);
        });
    }

    private void onVsync(long frameTimeNanos) {
        lastVsyncNanos = frameTimeNanos;
        if (capActive) {
            Choreographer.getInstance().postFrameCallback(frameCallback);
        } else {
            tracking = false;
        }
    }
}
