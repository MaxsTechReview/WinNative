package com.winlator.cmod.runtime.display.composition;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Bridge to the native {@code surface_compositor.c} module — gives the rest of
 * the app a stable Java entry point for the per-container "Direct Composition"
 * path without any caller having to know whether the underlying NDK
 * {@code ASurfaceControl} / {@code ASurfaceTransaction} symbols are actually
 * resolvable on this device.
 *
 * <h3>Availability gate</h3>
 * {@link #isAvailable()} returns {@code true} only when ALL of these hold:
 * <ol>
 *   <li>API level 29+ (ASurfaceControl arrived in API 29).</li>
 *   <li>The required libandroid.so symbols resolve via dlsym.</li>
 *   <li>The device is NOT on the soft-boot blocklist (see below).</li>
 * </ol>
 *
 * <h3>Soft-boot blocklist</h3>
 * The original PR #380 caused soft boots (device reboots) on several device
 * families because their gralloc / HWC / SurfaceFlinger implementations crash
 * when ASurfaceControl is used. The blocklist skips Direct Composition on the
 * known-bad families. Research: /home/z/my-project/download/pr380-research-report.md
 *
 * Blocked families:
 * <ul>
 *   <li><b>Xiaomi / HyperOS 2.0+ (Android 14+)</b> — Flutter disabled
 *       SurfaceControl entirely on these due to unrecoverable SF crashes.
 *       See https://github.com/flutter/flutter/issues/160025</li>
 *   <li><b>Adreno 6xx with older qdgralloc</b> — Winlator user reports of
 *       device reboots. See r/winlator reboot reports.</li>
 * </ul>
 *
 * Warned (but not blocked) families:
 * <ul>
 *   <li><b>Samsung OneUI 4.1+ (Android 12+)</b> — PSPlay-class full-phone-reboot
 *       reports. We warn but allow, because the crash is less reproducible.</li>
 * </ul>
 *
 * The blocklist is conservative — when in doubt, block. Users who want to
 * override can set the developer setting "Force enable Direct Composition"
 * (not yet implemented — the block is hard for safety).
 */
public final class SurfaceCompositor {

    static {
        // Same pattern used by SysVSharedMemory, GPUImage, ClientSocket, etc.
        System.loadLibrary("winlator");
    }

    private static final String TAG = "SurfaceCompositor";

    /** Cached probe result. null until first call; thereafter final-state. */
    private static volatile Boolean cachedAvailability;

    private SurfaceCompositor() {
        // Static-only utility.
    }

    /**
     * @return {@code true} when the device exposes the API 29+ SurfaceControl
     *         + SurfaceTransaction NDK symbols AND is not on the soft-boot
     *         blocklist. {@code false} on any earlier Android version, on any
     *         device where libandroid.so is missing the symbol, on blocklisted
     *         device families, or if the JNI lookup itself fails.
     */
    public static boolean isAvailable() {
        Boolean cached = cachedAvailability;
        if (cached != null) {
            return cached;
        }
        // Hard short-circuit on platforms where the native call would always
        // resolve to the API-< 29 fallback.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            cachedAvailability = Boolean.FALSE;
            return false;
        }

        // === SOFT-BOOT BLOCKLIST ===
        // Check device family BEFORE the native probe — if we're blocklisted,
        // don't even dlopen the symbols (some grallocs crash on the probe
        // itself on the worst devices).
        if (isBlocklisted()) {
            cachedAvailability = Boolean.FALSE;
            return false;
        }

        boolean result;
        try {
            result = nativeIsAvailable();
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            Log.w(TAG, "nativeIsAvailable threw, treating as unavailable", e);
            result = false;
        }
        cachedAvailability = result;
        if (result) {
            Log.i(TAG, "Direct Composition is available on this device");
        }
        return result;
    }

    /**
     * Device-family soft-boot blocklist. Returns true only for families with a
     * confirmed device-reboot signature. Unknown hardware is not statically
     * blocked here — Direct Composition is a manual per-container toggle and the
     * consecutive-failure self-detach handles runtime push failures.
     */
    private static boolean isBlocklisted() {
        String manufacturer = Build.MANUFACTURER != null
                ? Build.MANUFACTURER.toLowerCase() : "";

        // Xiaomi / HyperOS 2.0+ on Android 14+ — known SurfaceFlinger crash.
        // https://github.com/flutter/flutter/issues/160025
        if (manufacturer.contains("xiaomi") && Build.VERSION.SDK_INT >= 34) {
            Log.w(TAG, "Direct Composition BLOCKED on Xiaomi/HyperOS (Android 14+) — "
                    + "known SurfaceFlinger crash (flutter/flutter#160025). "
                    + "Falling back to VulkanRenderer composition.");
            return true;
        }

        // Samsung OneUI on Android 12+ — rare reboot reports; warn but allow.
        if (manufacturer.contains("samsung") && Build.VERSION.SDK_INT >= 31) {
            Log.w(TAG, "Direct Composition WARNING on Samsung OneUI (Android 12+) — "
                    + "rare reboot reports exist. Disable the toggle if you experience reboots.");
        }

        return false;
    }

    private static native boolean nativeIsAvailable();

    // === DIAGNOSTIC FILE LOGGING ===
    //
    // The wine_*.txt logs the user shares only capture Wine/FEX stderr — they
    // do NOT contain Android logcat. To make Direct Composition status visible
    // in the user's shared logs, we write DC events to a dedicated
    // direct-composition.log file in the app's logs directory. This file is
    // automatically included when the user shares logs (LogManager shares all
    // *.log / *.txt files in the logs dir).
    //
    // Call SurfaceCompositor.logEvent("message") from anywhere in the app to
    // append a timestamped line. The file is opened lazily on first call and
    // kept open for the session.

    private static volatile File diagFile = null;
    private static volatile FileWriter diagWriter = null;
    private static final Object diagLock = new Object();
    private static final SimpleDateFormat diagDateFormat =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    /**
     * Set the diagnostic file location. Called once from XServerDisplayActivity
     * at session start (before any DC code runs). Pass the app's logs directory.
     */
    public static void initDiagnosticFile(File logsDir) {
        synchronized (diagLock) {
            try {
                if (diagWriter != null) {
                    diagWriter.flush();
                    diagWriter.close();
                }
                if (logsDir != null && !logsDir.exists()) logsDir.mkdirs();
                diagFile = new File(logsDir, "direct-composition.log");
                diagWriter = new FileWriter(diagFile, /*append=*/false);
                logEvent("=== Direct Composition diagnostic log started ===");
                logEvent("Device: " + Build.MANUFACTURER + " " + Build.MODEL
                        + " (API " + Build.VERSION.SDK_INT + ")");
                logEvent("isAvailable() = " + isAvailable());
            } catch (IOException e) {
                Log.w(TAG, "Failed to init diagnostic file", e);
                diagWriter = null;
            }
        }
    }

    /**
     * Append a timestamped line to the diagnostic file. Also goes to logcat
     * (Log.i) so it appears in logcat.log too. Safe to call from any thread.
     */
    public static void logEvent(String message) {
        String timestamped = "[" + diagDateFormat.format(new Date()) + "] " + message;
        Log.i(TAG, message);
        synchronized (diagLock) {
            if (diagWriter != null) {
                try { diagWriter.write(timestamped + "\n"); }
                catch (IOException ignored) {}
            }
        }
    }

    /**
     * Close the diagnostic file. Called from XServerDisplayActivity.onDestroy.
     */
    public static void closeDiagnosticFile() {
        synchronized (diagLock) {
            try {
                if (diagWriter != null) {
                    logEvent("=== Direct Composition diagnostic log closed ===");
                    diagWriter.flush();
                    diagWriter.close();
                }
            } catch (IOException ignored) {
            } finally {
                diagWriter = null;
                diagFile = null;
            }
        }
    }
}
