package com.winlator.cmod.runtime.display.composition;

import android.os.Build;

/**
 * Bridge to the native {@code surface_compositor.c} module — gives the rest of
 * the app a stable Java entry point for the per-container "Direct Composition"
 * path without any caller having to know whether the underlying NDK
 * {@code ASurfaceControl} / {@code ASurfaceTransaction} symbols are actually
 * resolvable on this device.
 *
 * <h3>Phase 1 (current)</h3>
 * Only {@link #isAvailable()} is wired up. The result is cached after the first
 * call so subsequent checks are free. Code that wants to use the Direct
 * Composition fast path should:
 * <ol>
 *   <li>Read the per-container toggle ({@code Container#isDirectCompositionEnabled()}).</li>
 *   <li>Confirm runtime support with {@link #isAvailable()}.</li>
 *   <li>Otherwise fall back to the existing GLRenderer composition path.</li>
 * </ol>
 *
 * <h3>Why dlopen rather than direct linking</h3>
 * {@code minSdk} is 26 but {@code ASurfaceControl_*} arrived in API 29; linking
 * statically would fail to resolve at library-load time on Android 8/9. The
 * native side resolves symbols via {@code dlopen("libandroid.so")} +
 * {@code dlsym} so the shared library still loads everywhere and we degrade to
 * the GLRenderer path when the symbols are missing.
 */
public final class SurfaceCompositor {

    static {
        // Same pattern used by SysVSharedMemory, GPUImage, ClientSocket, etc.
        // — every class that calls into the `winlator` shared lib loads it in
        // its static init. Repeated System.loadLibrary calls are no-ops once
        // the library is already mapped into the process.
        System.loadLibrary("winlator");
    }

    private static final String TAG = "SurfaceCompositor";

    /**
     * Cached probe result. {@code null} until {@link #isAvailable()} is first
     * called; thereafter the boxed Boolean is final-state. Read-after-write is
     * safe because {@code Boolean} writes are atomic on every supported VM and
     * the cache is intentionally racy: if two threads probe simultaneously they
     * will both call into the JNI layer once, which is itself idempotent and
     * mutex-guarded.
     */
    private static volatile Boolean cachedAvailability;

    private SurfaceCompositor() {
        // Static-only utility.
    }

    /**
     * @return {@code true} when the device exposes the API 29+ SurfaceControl
     *         + SurfaceTransaction NDK symbols and the {@code winlator} native
     *         library was loaded successfully. {@code false} on any earlier
     *         Android version, on any device where libandroid.so is missing
     *         the symbol, or if the JNI lookup itself fails.
     */
    public static boolean isAvailable() {
        Boolean cached = cachedAvailability;
        if (cached != null) {
            return cached;
        }
        // Hard short-circuit on platforms where the native call would always
        // resolve to the API-< 29 fallback. Keeps logcat noise off old devices.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            cachedAvailability = Boolean.FALSE;
            return false;
        }
        boolean result;
        try {
            result = nativeIsAvailable();
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            // Bridge load failure (e.g. winlator native lib not yet loaded
            // from this classloader) — treat as "not available" rather than
            // crashing the activity. The caller falls back to the GL path.
            android.util.Log.w(TAG, "nativeIsAvailable threw, treating as unavailable", e);
            result = false;
        }
        cachedAvailability = result;
        return result;
    }

    private static native boolean nativeIsAvailable();
}
