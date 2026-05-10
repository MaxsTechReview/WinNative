package com.winlator.cmod.runtime.display.composition;

import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Per-activity wrapper around a single {@code ASurfaceControl} child layer
 * that's bound to an {@link XServerView}'s underlying {@link SurfaceView}.
 *
 * <h3>Lifecycle</h3>
 * The layer is allocated when the host SurfaceView reports
 * {@link SurfaceHolder.Callback#surfaceCreated} (or in Phase 2.1's case, the
 * activity attaches once after {@code rootView.addView(xServerView)} as a
 * one-shot probe — see {@code XServerDisplayActivity.setupDirectComposition})
 * and freed in {@link SurfaceHolder.Callback#surfaceDestroyed}. Operating on a
 * released layer is a no-op (defensive: the native side guards against
 * {@code sc == 0}).
 *
 * <h3>Phase 2.1 capabilities</h3>
 * Lifecycle only — attach, set a solid color (proof-of-life), hide, release.
 * No buffer push, no fence handling, no game-frame routing yet. The toggle is
 * still consumed by {@code XServerDisplayActivity} only for logging.
 *
 * <h3>Threading</h3>
 * All public methods serialise on {@code this} so concurrent calls from the
 * UI thread (lifecycle) and the renderer thread (future buffer pushes) can't
 * race the native pointer. The native pointer is read inside the lock and
 * passed to JNI, where libandroid.so's own synchronisation takes over.
 */
public final class DirectCompositionLayer {

    static {
        // Same pattern as SurfaceCompositor / GPUImage / etc. Idempotent.
        System.loadLibrary("winlator");
    }

    private static final String TAG = "DirectCompositionLayer";

    /** Native {@code ASurfaceControl*} reinterpreted as a {@code jlong}. 0 == released. */
    private long nativeSc;

    private DirectCompositionLayer(long nativeSc) {
        this.nativeSc = nativeSc;
    }

    /**
     * Attach a hidden child SurfaceControl above the given SurfaceView's primary
     * buffer queue. Caller must invoke {@link #release()} when the host surface
     * is destroyed.
     *
     * @return a layer handle, or {@code null} if the underlying NDK call failed
     *         (caller should fall back to the GLRenderer composition path).
     */
    public static DirectCompositionLayer attach(SurfaceView host) {
        if (host == null) return null;
        if (!SurfaceCompositor.isAvailable()) {
            // Shouldn't normally happen — the activity is supposed to call
            // SurfaceCompositor.isAvailable() before instantiating this class —
            // but be defensive.
            Log.w(TAG, "attach() called but SurfaceCompositor is unavailable");
            return null;
        }
        SurfaceHolder holder = host.getHolder();
        if (holder == null) {
            Log.w(TAG, "attach() — SurfaceView has no holder");
            return null;
        }
        Surface surface = holder.getSurface();
        if (surface == null || !surface.isValid()) {
            Log.w(TAG, "attach() — SurfaceView's Surface is not valid yet (surfaceCreated not fired)");
            return null;
        }
        long sc;
        try {
            sc = nativeAttachToSurface(surface);
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            Log.w(TAG, "nativeAttachToSurface threw", e);
            return null;
        }
        if (sc == 0L) {
            return null;
        }
        Log.i(TAG, "Direct Composition layer attached (sc=" + Long.toHexString(sc) + ")");
        return new DirectCompositionLayer(sc);
    }

    /**
     * Phase 2.1 proof-of-life: paint a solid color and unhide. Used by the
     * {@code DirectCompositionTestPattern} smoke test in Phase 2.2 only —
     * production code paths never call this.
     */
    public synchronized void setColor(float r, float g, float b, float a) {
        if (nativeSc == 0L) return;
        try {
            nativeSetColor(nativeSc, r, g, b, a);
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            Log.w(TAG, "nativeSetColor threw", e);
        }
    }

    /**
     * Phase 2.2: hand an AHardwareBuffer to the layer in one transaction.
     *
     * @param ahbPtr      raw {@code AHardwareBuffer*} (typically obtained from
     *                    {@code GPUImage.getHardwareBufferPtr()} in Phase 2.3
     *                    or from {@link #allocateTestBuffer} in Phase 2.2's
     *                    smoke test).
     * @param dstX/dstY   layer position in the SurfaceView's coordinate space.
     * @param dstW/dstH   destination size; if it differs from the buffer's
     *                    native extents the layer is scaled (modern API path)
     *                    or stretched (deprecated setGeometry fallback).
     * @param fenceFd     POSIX sync_file FD that signals when GPU writes are
     *                    complete; pass -1 if no fence is needed (the framework
     *                    will read the buffer immediately). The framework
     *                    <em>takes ownership</em> of this FD per
     *                    surface_control.h:343-348 — the caller MUST NOT close
     *                    it after this call. Phase 2.2 always passes -1; real
     *                    fence threading lives in Phase 2.4.
     * @param opaque      Whether the buffer's pixels are fully opaque
     *                    (alpha=1.0 throughout). Game swap-chain frames are
     *                    by convention; pass {@code true} to let HWC mark the
     *                    layer OPAQUE and skip per-pixel alpha blending,
     *                    which on Snapdragon DPUs avoids the SDR-on-HDR
     *                    panel routing that boosts layer brightness vs the
     *                    legacy GL composition path. Pass {@code false} when
     *                    the buffer may contain translucency (overlays, UI).
     * @return true if the transaction was queued; false on any failure (in
     *         which case the caller should fall back to the GL composition
     *         path for this frame).
     */
    public synchronized boolean pushBuffer(long ahbPtr,
                                           int dstX, int dstY, int dstW, int dstH,
                                           int fenceFd,
                                           boolean opaque) {
        // Always enter JNI so the native side has a single, consistent place
        // to consume / close the fence FD. The native code's first action is
        // to validate sc / ahb / extents and close the fence on any error
        // path before returning JNI_FALSE — so callers never have to worry
        // about FD leaks regardless of which check trips. Phase 2.2 always
        // passes -1, so this is a no-op today, but the invariant matters
        // for Phase 2.4 when real fences arrive.
        try {
            return nativePushBuffer(nativeSc, ahbPtr, dstX, dstY, dstW, dstH, fenceFd, opaque);
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            Log.w(TAG, "nativePushBuffer threw", e);
            // FD ownership on a thrown JNI call is undefined — best-effort:
            // leak rather than risk a double-close. Phase 2.4 callers should
            // wrap pushBuffer in a try/finally that owns the FD lifecycle
            // explicitly when this matters.
            return false;
        }
    }

    /**
     * Phase 2.2 smoke-test helper: allocate an AHardwareBuffer and CPU-fill it
     * with a single ARGB color. Returns a raw {@code AHardwareBuffer*} (held
     * by the JVM as a {@code long}) — caller is responsible for eventually
     * calling {@link #releaseBuffer}.
     *
     * @param argb 0xAARRGGBB packed colour.
     * @return native pointer, or 0 on failure.
     */
    public static long allocateTestBuffer(int width, int height, int argb) {
        if (width <= 0 || height <= 0) return 0L;
        if (!SurfaceCompositor.isAvailable()) return 0L;
        try {
            return nativeAllocateTestBuffer(width, height, argb);
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            Log.w(TAG, "nativeAllocateTestBuffer threw", e);
            return 0L;
        }
    }

    /** Drop our refcount on a test AHardwareBuffer allocated with {@link #allocateTestBuffer}. */
    public static void releaseBuffer(long ahbPtr) {
        if (ahbPtr == 0L) return;
        try {
            nativeReleaseBuffer(ahbPtr);
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            Log.w(TAG, "nativeReleaseBuffer threw", e);
        }
    }

    /**
     * Hide the layer — used when the current frame doesn't qualify for the
     * direct-scanout fast path and the GLRenderer is going to composite
     * normally. Idempotent.
     */
    public synchronized void hide() {
        if (nativeSc == 0L) return;
        try {
            nativeHide(nativeSc);
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            Log.w(TAG, "nativeHide threw", e);
        }
    }

    /**
     * Reparent the layer to null and release the underlying ASurfaceControl.
     * Safe to call multiple times. After this returns, every other method on
     * the layer is a no-op.
     */
    public synchronized void release() {
        if (nativeSc == 0L) return;
        long sc = nativeSc;
        nativeSc = 0L;
        try {
            nativeDetachAndRelease(sc);
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            Log.w(TAG, "nativeDetachAndRelease threw", e);
        }
        Log.i(TAG, "Direct Composition layer released (sc=" + Long.toHexString(sc) + ")");
    }

    /** True if this handle still references a live native ASurfaceControl. */
    public synchronized boolean isAttached() {
        return nativeSc != 0L;
    }

    private static native long nativeAttachToSurface(Surface surface);

    private static native void nativeDetachAndRelease(long sc);

    private static native void nativeSetColor(long sc, float r, float g, float b, float a);

    private static native void nativeHide(long sc);

    private static native boolean nativePushBuffer(long sc, long ahbPtr,
                                                   int dstX, int dstY, int dstW, int dstH,
                                                   int fenceFd, boolean opaque);

    private static native long nativeAllocateTestBuffer(int width, int height, int argb);

    private static native void nativeReleaseBuffer(long ahbPtr);
}
