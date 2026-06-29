package com.winlator.cmod.runtime.display.composition;

import android.util.Log;
import android.view.Surface;

/**
 * Instance class wrapping a single {@code ASurfaceControl} child layer bound to
 * the XServerSurfaceView's Surface. All public methods are {@code synchronized}
 * so the UI-thread {@link #release()} and the render-thread {@link #pushBuffer}
 * can't race the native pointer.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #attach(Surface)} — creates the ASurfaceControl, hides it,
 *       sets z=1. Called from the UI thread when the SurfaceView's surface
 *       is created.</li>
 *   <li>{@link #pushBuffer(long, int, int, int, int, int)} — per-frame hot
 *       path. Hands an AHardwareBuffer to the SC layer. Called from the render
 *       thread. Returns false on any failure; the caller self-detaches after
 *       {@code DC_FAIL_LIMIT} consecutive failures.</li>
 *   <li>{@link #hide()} — hides the SC layer (visibility=HIDE) when the current
 *       frame doesn't qualify for direct scanout. Idempotent.</li>
 *   <li>{@link #release()} — reparents to null, waits for in-flight
 *       transactions, releases the SC. Called from the UI thread on
 *       surfaceDestroyed / activity destroy.</li>
 * </ol>
 *
 * <h3>Soft-boot hardening (vs original PR #380)</h3>
 * <ul>
 *   <li><b>Smoke-test buffer removed.</b> The original allocated a 256x256
 *       magenta AHB with CPU_WRITE_RARELY | COMPOSER_OVERLAY on every
 *       surfaceCreated and pushed it as a proof-of-life. That combo crashes
 *       gralloc on Adreno 6xx / MediaTek → soft boot. Removed entirely; real
 *       game frames prove the path works.</li>
 *   <li><b>Wait-for-in-flight on release.</b> The native side tracks in-flight
 *       ASurfaceTransaction_apply calls and waits for them to complete before
 *       ASurfaceControl_release. Prevents the Xiaomi/HyperOS SF crash.</li>
 *   <li><b>No nativeAllocateTestBuffer / nativeReleaseBuffer.</b> Removed.</li>
 * </ul>
 */
public final class DirectCompositionLayer {

    private static final String TAG = "DirectCompositionLayer";

    /** Native ASurfaceControl* pointer. 0 = not attached / released. */
    private long nativeSc = 0;
    private boolean attached = false;

    /**
     * Create the ASurfaceControl child layer bound to the given Surface.
     * The layer is hidden initially; it becomes visible on the first
     * successful {@link #pushBuffer}.
     *
     * @param surface The SurfaceView's Surface (from SurfaceHolder.getSurface()).
     * @return true on success, false if native creation failed (caller should
     *         not call pushBuffer; the VulkanRenderer composition path will
     *         be used instead).
     */
    public synchronized boolean attach(Surface surface) {
        if (attached) {
            Log.w(TAG, "attach: already attached, ignoring");
            return true;
        }
        if (surface == null || !surface.isValid()) {
            Log.w(TAG, "attach: surface is null or invalid");
            return false;
        }
        nativeSc = nativeCreateFromWindow(surface, "winnative-direct-composition");
        if (nativeSc == 0) {
            Log.e(TAG, "attach: nativeCreateFromWindow returned 0");
            return false;
        }
        attached = true;
        Log.i(TAG, "Direct Composition layer attached: sc=" + nativeSc);
        return true;
    }

    /**
     * Push an AHardwareBuffer to the SC layer. The layer is shown atomically
     * with the buffer set (avoids the blank-frame race).
     *
     * @param ahbPtr          The raw AHardwareBuffer* pointer (from
     *                        GPUImage.getHardwareBufferPtr()). Must be non-zero.
     * @param dstX            Destination X in SurfaceView coordinate space.
     *                        Must be >= 0 (negative values crash SF on some
     *                        OEM ROMs).
     * @param dstY            Destination Y. Must be >= 0.
     * @param dstW            Destination width. Must be > 0.
     * @param dstH            Destination height. Must be > 0.
     * @param acquireFenceFd  Producer-side acquire fence (-1 = no fence, buffer
     *                        is ready immediately). The framework takes
     *                        ownership and closes the fd on success; on
     *                        failure this method closes it.
     * @param opaque          true if the buffer is fully opaque (alpha=1.0
     *                        throughout). Lets HWC skip alpha blending and
     *                        bypass the Snapdragon DPU SDR-on-HDR brightness
     *                        boost. false for translucent content.
     * @return true on success, false on any failure (caller should count
     *         consecutive failures and self-detach after DC_FAIL_LIMIT).
     */
    public synchronized boolean pushBuffer(long ahbPtr, int dstX, int dstY,
                                            int dstW, int dstH,
                                            int acquireFenceFd, boolean opaque, boolean pace) {
        if (!attached || nativeSc == 0) {
            if (acquireFenceFd >= 0) {
                try { android.os.ParcelFileDescriptor.adoptFd(acquireFenceFd).close(); }
                catch (java.io.IOException ignored) {}
            }
            return false;
        }
        return nativePushBuffer(nativeSc, ahbPtr, dstX, dstY, dstW, dstH,
                                acquireFenceFd, opaque, pace);
    }

    /**
     * Hide the SC layer (visibility=HIDE). Idempotent — safe to call when
     * already hidden. Used when the current frame doesn't qualify for direct
     * scanout (windowed app, multi-drawable, cursor visible over non-fullscreen
     * scene, magnifier overlay active, etc.).
     */
    public synchronized void hide() {
        if (!attached || nativeSc == 0) return;
        nativeHide(nativeSc);
    }

    /**
     * Release the SC layer. Reparents to null (removes from display), waits
     * for all in-flight transactions to complete, then releases the native
     * ASurfaceControl. Safe to call from the UI thread while the render thread
     * might be in pushBuffer — the synchronized keyword serializes the two.
     *
     * After release, this instance is unusable; create a new one to re-attach.
     */
    public synchronized void release() {
        if (!attached) return;
        nativeDetachAndRelease(nativeSc);
        nativeSc = 0;
        attached = false;
        Log.i(TAG, "Direct Composition layer released");
    }

    public synchronized boolean isAttached() {
        return attached;
    }

    // --- Native methods ---

    private native long nativeCreateFromWindow(Surface surface, String debugName);

    private native void nativeDetachAndRelease(long scPtr);

    private native void nativeHide(long scPtr);

    private native boolean nativePushBuffer(long scPtr, long ahbPtr,
                                             int dstX, int dstY,
                                             int dstW, int dstH,
                                             int acquireFenceFd, boolean opaque, boolean pace);

    // Blocks until SF finishes the previous frame (hardware signal, no CPU polling).
    public native boolean nativeWaitForPreviousFrame(long timeoutMs);
}
