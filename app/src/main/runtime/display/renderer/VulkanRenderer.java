package com.winlator.cmod.runtime.display.renderer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.view.Choreographer;
import android.view.Surface;
import androidx.preference.PreferenceManager;
import com.winlator.cmod.BuildConfig;
import com.winlator.cmod.R;
import com.winlator.cmod.runtime.system.ApplicationLogGate;
import com.winlator.cmod.runtime.display.renderer.effects.Effect;
import com.winlator.cmod.runtime.display.ui.XServerSurfaceView;
import com.winlator.cmod.runtime.display.xserver.Bitmask;
import com.winlator.cmod.runtime.display.xserver.Cursor;
import com.winlator.cmod.runtime.display.xserver.Drawable;
import com.winlator.cmod.runtime.display.xserver.Pointer;
import com.winlator.cmod.runtime.display.xserver.Window;
import com.winlator.cmod.runtime.display.xserver.WindowAttributes;
import com.winlator.cmod.runtime.display.xserver.WindowManager;
import com.winlator.cmod.runtime.display.xserver.XLock;
import com.winlator.cmod.runtime.display.xserver.XServer;
import com.winlator.cmod.shared.math.Mathf;
import com.winlator.cmod.shared.math.XForm;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native Vulkan compositor: owns the C-side renderer handle and pushes a scene snapshot per frame. */
public class VulkanRenderer
        implements RenderCallback,
                   WindowManager.OnWindowModificationListener,
                   Pointer.OnPointerMotionListener {

    private static final String TAG = "VulkanRenderer";
    private static final String PREF_VULKAN_VALIDATION_LAYERS =
            "enable_vulkan_validation_layers";

    static {
        System.loadLibrary("winlator");
    }

    public final XServerSurfaceView xServerView;
    private final XServer xServer;

    private long nativeHandle = 0;
    private boolean supportProbed = false;
    private boolean loggedAhbSceneUse = false;
    // Must be set before attachSurface.
    private volatile String graphicsDriverName = null;

    // ---- Frame generation ----
    private volatile boolean frameGenEnabled = false;
    private volatile int fgMultiplier = 2;   // target display:engine ratio (2, 3, 4) — the user ceiling
    private volatile int fgEffectiveMultiplier = 2;  // adaptive working multiplier (2..ceiling)
    private volatile int fgBoundSecs = 0;
    private volatile int fgRecoverSecs = 0;          // consecutive healthy seconds -> step the multiplier back up
    private final AtomicBoolean fgNewScene = new AtomicBoolean(false);
    private final AtomicBoolean fgSceneDirty = new AtomicBoolean(false);
    private final AtomicBoolean fgPumpScheduled = new AtomicBoolean(false);
    private long fgEngineFrames = 0;
    private volatile long fgDisplayPeriodNs = 0;
    private volatile double fgLockedGameHz = 0.0;
    private long fgLastPumpNs = 0;
    private volatile long fgLastGameNs = 0;
    private volatile long fgPrevGameNs = 0;
    private volatile long fgCurrentVsyncNs = 0;
    private long fgLastEmitVsyncNs = 0;
    private long fgPromoteVsyncNs = 0;
    private int fgSlotsLocked = 0;
    private boolean fgForcePromote = false;
    private Drawable fgLastScanoutSrc = null;
    private Drawable fgFirstScanoutSrc = null;
    private boolean fgMultiBuffer = false;
    private long fgLastAcceptNs = 0L;
    private static final long FG_DEDUP_FREEZE_NS = 100_000_000L;
    private boolean fgEmitWasHold = false;
    private long fgInstHoldN, fgInstInterpN, fgInstLongN, fgInstTotalN;
    private double fgInstHoldSum, fgInstInterpSum, fgInstHoldMax, fgInstInterpMax;
    private boolean fgRenderPrioritySet = false;
    private volatile int fgActivePresentMode = PRESENT_MODE_FIFO;
    private volatile int fgDisplayCapHz = 0;  // panel-max ceiling for the target post rate; 0 = uncapped
    private volatile int fgQuality = 1;      // 0 performance, 1 balanced, 2 quality
    private volatile float fgSmoothness = 0.75f;
    private volatile boolean fgDeepMode = false;
    private volatile boolean fgExtrapolate = false;   // false = interpolate, true = extrapolate
    private volatile int fgModel = 0;                  // 0 = standard, 1 = steadier
    private volatile float fgFlowScale = 0.15f;
    private volatile int fgFramesInFlight = 3;         // compositor buffering depth (1..3)
    private volatile Surface fgSurface;
    private float fgFrameRateHint = -1f;
    private long fgFrameRateHintNs = 0L;
    private volatile Runnable fgRateChangedListener;

    private final EffectComposer effectComposer;
    public final ViewTransformation viewTransformation = new ViewTransformation();

    private final Drawable rootCursorDrawable;
    private final ArrayList<RenderableWindow> renderableWindows = new ArrayList<>();
    private final Texture.UploadBatch textureUploadBatch =
            new Texture.UploadBatch((64 + 1) * Texture.MAX_UPLOAD_RECTS);
    private boolean fullscreen = false;
    public boolean viewportNeedsUpdate = true;
    private boolean cursorVisible = true;
    public boolean swapRB = false;

    public void setSwapRB(boolean v) {
        this.swapRB = v;
        requestRenderCoalesced();
    }
    private boolean screenOffsetYRelativeToCursor = false;
    private String[] unviewableWMClasses = null;
    private float magnifierZoom = 1.0f;
    private boolean magnifierEnabled = true;
    private boolean magnifierUIActive = false;
    private float magnifierPanX = 0f;
    private float magnifierPanY = 0f;
    private boolean magnifierPanInitialized = false;
    private static final float MAGNIFIER_DEADZONE_FRACTION = 0.6f;
    // volatile: written on the main thread, read on the render thread (buildAndSubmitFrame self-heal).
    public volatile int surfaceWidth;
    public volatile int surfaceHeight;
    private boolean cpuSaverMode = false;
    private static final long CURSOR_ACTIVE_NS = 100_000_000L;
    private volatile long cursorActiveUntilNs = 0L;

    private static final int MAX_FPS_LIMIT = 1000;
    private volatile int currentFpsLimit = 0;

    // Must mirror VK_MAX_RENDERABLE_WINDOWS / VK_MAX_EFFECTS in vk_state.h.
    private static final int MAX_WINDOWS = 64;
    private static final int MAX_EFFECTS = 8;

    private static final int OFF_CURSOR_HANDLE   = 0;
    private static final int OFF_WINDOW_HANDLES  = 8;
    private static final int OFF_WINDOW_COUNT    = 520;
    private static final int OFF_CURSOR_VISIBLE  = 524;
    private static final int OFF_CURSOR_GEOM     = 528;
    private static final int OFF_XFORM           = 544;
    private static final int OFF_VIEWPORT        = 568;
    private static final int OFF_SCISSOR_ENABLED = 584;
    private static final int OFF_SCISSOR         = 588;
    private static final int OFF_SCREEN_W        = 604;
    private static final int OFF_SCREEN_H        = 608;
    private static final int OFF_EFFECT_COUNT    = 612;
    private static final int OFF_EFFECT_TYPES    = 616;
    private static final int OFF_EFFECT_PARAMS   = 648;
    private static final int OFF_WINDOW_GEOM     = 776;
    private static final int OFF_WINDOW_UV       = 1800;
    private static final int OFF_SWAP_RB         = 2824;
    private static final int OFF_SOURCE_W        = 2828;
    private static final int OFF_SOURCE_H        = 2832;
    private static final int SCENE_BUF_SIZE      = 2836;

    private final ByteBuffer sceneBuf =
            ByteBuffer.allocateDirect(SCENE_BUF_SIZE).order(ByteOrder.nativeOrder());
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean fgPumpStarted = false;
    private final AtomicBoolean renderRequested = new AtomicBoolean(false);

    // Reusable scratch — sized once, refilled per frame.
    private final float[] sceneXform = XForm.getInstance();
    private final float[] effectParamsScratch = new float[MAX_EFFECTS * 4];

    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    public VulkanRenderer(XServerSurfaceView view, XServer xServer) {
        this.xServerView = view;
        this.xServer = xServer;
        this.effectComposer = new EffectComposer(this);
        this.rootCursorDrawable = createRootCursorDrawable();
        this.coalescedRenderCallback = frameTimeNanos -> {
            renderRequested.set(false);
            xServerView.requestRender();
        };
    }

    public void destroy() {
        if (destroyed.compareAndSet(false, true)) {
            // Unregister from the persistent XServer to avoid leaking listeners.
            xServer.windowManager.removeOnWindowModificationListener(this);
            xServer.pointer.removeOnPointerMotionListener(this);
            stopFgPumpThread();

            if (nativeHandle != 0) {
                // On the UI thread, run nativeDestroy off-thread — it may block on vkDeviceWaitIdle.
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    new Thread(() -> {
                        synchronized (this) {
                            if (nativeHandle != 0) {
                                nativeDestroy(nativeHandle);
                                nativeHandle = 0;
                                Texture.setRendererHandle(0);
                            }
                        }
                    }, "Vulkan-Cleanup").start();
                } else {
                    synchronized (this) {
                        if (nativeHandle != 0) {
                            nativeDestroy(nativeHandle);
                            nativeHandle = 0;
                            Texture.setRendererHandle(0);
                        }
                    }
                }
            }
        }
    }

    private volatile Choreographer mainChoreographer;
    private final Choreographer.FrameCallback coalescedRenderCallback;

    public void requestRenderCoalesced() {
        if (frameGenEnabled) {
            fgSceneDirty.set(true);
            scheduleFgPump();
            return;
        }
        if (renderRequested.compareAndSet(false, true)) {
            // Post directly (thread-safe): a handler hop arms past the next doFrame and halves the visible cursor rate.
            Choreographer choreographer = mainChoreographer;
            if (choreographer != null) {
                choreographer.postFrameCallback(coalescedRenderCallback);
            } else {
                mainHandler.post(() -> {
                    mainChoreographer = Choreographer.getInstance();
                    mainChoreographer.postFrameCallback(coalescedRenderCallback);
                });
            }
        }
    }

    // ---- Frame generation driver -------------------------------------------

    /** Toggle native frame generation. Safe to call from any thread. */
    public void setFrameGeneration(boolean enabled) {
        frameGenEnabled = enabled;
        synchronized (this) {
            if (nativeHandle != 0) {
                nativeSetFrameGeneration(nativeHandle, enabled);
                nativeSetPresentMode(nativeHandle, enabled ? PRESENT_MODE_MAILBOX : requestedPresentMode);
                fgActivePresentMode = nativeGetActivePresentMode(nativeHandle);
            }
        }
        if (enabled) {
            pushFrameGenParams();
            synchronized (this) {
                if (nativeHandle != 0) nativeSetFrameGenDeepMode(nativeHandle, fgDeepMode);
            }
            fgEngineFrames = 0;
            fgCurrentVsyncNs = 0;
            fgLastScanoutSrc = null;
            fgFirstScanoutSrc = null;
            fgMultiBuffer = false;
            fgLastAcceptNs = 0L;
            fgRenderPrioritySet = false;
            fgLockedGameHz = 0.0;
            fgNewScene.set(true);
            startFgPumpThread();
            scheduleFgPump();
        }
        if (!enabled) { fgLockedGameHz = 0.0; fgApplyFrameRateHint(0.0, System.nanoTime()); stopFgPumpThread(); }
    }

    public boolean isFrameGenerationEnabled() { return frameGenEnabled; }

    /** Target display:engine ratio (2, 3, 4). Snapped to a supported value. Live; safe from any thread. */
    public void setFrameGenerationMultiplier(int multiplier) {
        fgMultiplier = multiplier <= 2 ? 2 : (multiplier >= 4 ? 4 : 3);
        fgEffectiveMultiplier = fgMultiplier;
        fgBoundSecs = 0;
    }

    public int getFrameGenMultiplier() { return fgMultiplier; }

    /** Panel-max refresh (Hz) — the scheduler won't target a post rate above this. 0 = uncapped. */
    public void setFrameGenDisplayCap(int hz) { fgDisplayCapHz = Math.max(0, hz); }

    /** Quality preset: 0 performance, 1 balanced, 2 quality. Live; safe from any thread. */
    public void setFrameGenerationQuality(int quality) {
        fgQuality = quality < 0 ? 0 : (quality > 2 ? 2 : quality);
        pushFrameGenParams();
    }

    public int getFrameGenerationQuality() { return fgQuality; }

    /** Apply a preset's flow quality + model + flowScale. Live. */
    public void setFrameGenerationPreset(int quality, int model, float flowScale) {
        fgQuality = quality < 0 ? 0 : (quality > 2 ? 2 : quality);
        fgModel = model <= 0 ? 0 : 1;
        float fs = flowScale < 0.05f ? 0.05f : (flowScale > 1.0f ? 1.0f : flowScale);
        boolean flowChanged = Math.abs(fs - fgFlowScale) > 1e-4f;
        fgFlowScale = fs;
        pushFrameGenParams();
        if (flowChanged) {
            synchronized (this) {
                if (nativeHandle != 0) nativeSetFrameGenFlowScale(nativeHandle, fs);
            }
        }
    }

    /** Interpolation smoothness in [0,1]. Live. */
    public void setFrameGenerationSmoothness(float smoothness) {
        fgSmoothness = smoothness < 0f ? 0f : (smoothness > 1f ? 1f : smoothness);
        pushFrameGenParams();
    }

    public float getFrameGenerationSmoothness() { return fgSmoothness; }

    /** Pipeline mode. false = standard (single backward flow). true = quality (bidirectional warp). Live. */
    public void setFrameGenerationDeepMode(boolean deep) {
        fgDeepMode = deep;
        synchronized (this) {
            if (nativeHandle != 0) nativeSetFrameGenDeepMode(nativeHandle, deep);
        }
    }

    public boolean isFrameGenerationDeepMode() { return fgDeepMode; }

    /** Generation method. false = interpolation (+1 frame latency). true = extrapolation (no added latency). Live. */
    public void setFrameGenerationExtrapolate(boolean extrapolate) {
        fgExtrapolate = extrapolate;
        synchronized (this) {
            if (nativeHandle != 0) nativeSetFrameGenExtrapolate(nativeHandle, extrapolate);
        }
    }

    public boolean isFrameGenerationExtrapolate() { return fgExtrapolate; }

    /** Compositor frames-in-flight (1..3): the latency/smoothness dial. Live. */
    public void setFrameGenerationFramesInFlight(int framesInFlight) {
        fgFramesInFlight = framesInFlight < 1 ? 1 : (framesInFlight > 3 ? 3 : framesInFlight);
        synchronized (this) {
            if (nativeHandle != 0) nativeSetFrameGenFramesInFlight(nativeHandle, fgFramesInFlight);
        }
    }

    public int getFrameGenerationFramesInFlight() { return fgFramesInFlight; }

    private void pushFrameGenParams() {
        float occHi = 0.12f + 0.28f * fgSmoothness;
        float occLo = (float) fgModel;                // 0 standard, 1 steadier
        int minStep = fgQuality == 0 ? 4 : (fgQuality == 2 ? 1 : 2);
        synchronized (this) {
            if (nativeHandle != 0) nativeSetFrameGenParams(nativeHandle, occLo, occHi, minStep);
        }
    }

    /** Actual vkQueuePresentKHR count (real + interpolated). HUD derives Display FPS from this. */
    public long getDisplayFrameCount() {
        synchronized (this) {
            return nativeHandle != 0 ? nativeGetDisplayFrameCount(nativeHandle) : 0L;
        }
    }

    private synchronized void startFgPumpThread() {
        if (fgPumpStarted) return;
        nativeFgPumpStart(this);
        fgPumpStarted = true;
    }

    private synchronized void stopFgPumpThread() {
        if (!fgPumpStarted) return;
        fgPumpStarted = false;
        fgLastPumpNs = 0L;
        nativeFgPumpStop();
    }

    private void scheduleFgPump() {
        if (frameGenEnabled && !fgPumpStarted) startFgPumpThread();
    }

    // Invoked from the native pump thread once per vsync (frameTimeNanos = the vsync time).
    private void fgPumpTickFromNative(long frameTimeNanos) {
        if (!frameGenEnabled || nativeHandle == 0) return;
        if (fgActivePresentMode == PRESENT_MODE_FIFO) {
            fgActivePresentMode = nativeGetActivePresentMode(nativeHandle);
        }
        if (fgLastPumpNs != 0L) {
            long d = frameTimeNanos - fgLastPumpNs;
            if (d > 0L && d < 100_000_000L) {  // ignore stalls / outliers
                fgDisplayPeriodNs = fgDisplayPeriodNs == 0L ? d : fgDisplayPeriodNs + (d - fgDisplayPeriodNs) / 8L;
                if (nativeHandle != 0) {
                    double th = fgTargetHz();
                    nativeSetVsyncTiming(nativeHandle, th > 0.0 ? (long) (1.0e9 / th) : fgDisplayPeriodNs,
                            fgDisplayPeriodNs, fgContentPeriodNs, frameTimeNanos);
                    fgApplyFrameRateHint(th, frameTimeNanos);
                }
            } else if (d >= 100_000_000L) {
                fgResyncPending = true;
            }
        }
        fgLastPumpNs = frameTimeNanos;
        fgCurrentVsyncNs = frameTimeNanos;
        xServerView.requestRender();
    }

    private void fgDrawFrame() {
        if (!fgRenderPrioritySet) {
            try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY); }
            catch (Throwable t) { /* best-effort */ }
            fgRenderPrioritySet = true;
        }
        int perTick = fgComputePerTick();
        for (int i = 0; i < perTick; i++) {
            long t0 = System.nanoTime();
            int kind = fgEmitOne();
            if (kind == 1) fgDiagInterp++;
            else if (kind == 2) fgDiagReal++;
            else fgDiagNone++;
            if (kind != 0) fgInstrument((System.nanoTime() - t0) / 1000L, fgEmitWasHold);
        }
        fgCadenceDiag();
    }

    private long fgDiagInterp, fgDiagReal, fgDiagNone, fgDiagLastNs;
    private void fgCadenceDiag() {
        long now = System.nanoTime();
        if (fgDiagLastNs == 0L) { fgDiagLastNs = now; return; }
        if (now - fgDiagLastNs < 1_000_000_000L) return;
        long game = fgContentPeriodNs, disp = fgDisplayPeriodNs;
        double gameHz = game > 0 ? 1e9 / game : 0, dispHz = disp > 0 ? 1e9 / disp : 0;
        double ratio = disp > 0 ? (double) game / disp : 0;
        long dupDrop = 0, distinct = 0;
        if (nativeHandle != 0) { nativeFgPromoteInfo(nativeHandle, fgPromoteInfo); dupDrop = fgPromoteInfo[2]; distinct = fgPromoteInfo[3]; }
        long dDup = dupDrop - fgDiagPrevDup, dDist = distinct - fgDiagPrevDist;
        fgDiagPrevDup = dupDrop; fgDiagPrevDist = distinct;
        double secs = (double) (now - fgDiagLastNs) / 1e9;
        if (fgLockedGameHz > 0.0 && secs > 0.0 && secs <= 1.5) {
            double deliveredHz = (double) (fgDiagInterp + fgDiagReal) / secs;
            double targetEff = Math.max(1, fgCadenceM) * fgLockedGameHz;
            if (fgDisplayCapHz > 0) targetEff = Math.min(targetEff, (double) fgDisplayCapHz);
            if (deliveredHz > 0.0 && deliveredHz < 0.85 * targetEff && !fgOverlayActive) {
                fgRecoverSecs = 0;
                if (++fgBoundSecs >= 4 && fgEffectiveMultiplier > 2) { fgEffectiveMultiplier--; fgBoundSecs = 0; }
            } else if (deliveredHz >= 0.95 * targetEff && fgEffectiveMultiplier < fgMultiplier && !fgOverlayActive) {
                fgBoundSecs = 0;
                if (++fgRecoverSecs >= 3) { fgEffectiveMultiplier++; fgRecoverSecs = 0; }
            } else {
                fgBoundSecs = 0;
                fgRecoverSecs = 0;
            }
        }
        Log.i(TAG, String.format(java.util.Locale.US,
            "FG diag: content=%.0fHz (locked=%.0f) panel=%.0fHz slots=%d mult=%dx(eff=%dx cad=%dx) | present interp=%d real=%d none=%d | "
          + "content-dedup distinct=%d/s dup-dropped=%d/s | id-dedup-dropped=%d accepted=%d",
            gameHz, fgLockedGameHz, dispHz, (int) Math.round(ratio), fgMultiplier, fgEffectiveMultiplier, fgCadenceM,
            fgDiagInterp, fgDiagReal, fgDiagNone, dDist, dDup, fgDiagDedupDropped, fgDiagAccepted));
        fgDiagInterp = fgDiagReal = fgDiagNone = fgDiagDedupDropped = fgDiagAccepted = 0;
        fgDiagLastNs = now;
    }
    private long fgDiagDedupDropped, fgDiagAccepted, fgDiagPrevDup, fgDiagPrevDist;

    private void fgInstrument(long usCpu, boolean wasHold) {
        double ms = usCpu / 1000.0;
        if (wasHold) { fgInstHoldN++; fgInstHoldSum += ms; if (ms > fgInstHoldMax) fgInstHoldMax = ms; }
        else         { fgInstInterpN++; fgInstInterpSum += ms; if (ms > fgInstInterpMax) fgInstInterpMax = ms; }
        if (ms > 8.31) fgInstLongN++;
        if (++fgInstTotalN >= 240) {
            android.util.Log.i(TAG, String.format(java.util.Locale.US,
                "FG cpu/present: composite n=%d mean=%.2f max=%.2f | interp n=%d mean=%.2f max=%.2f | over-budget=%d/%d",
                fgInstHoldN, fgInstHoldN > 0 ? fgInstHoldSum / fgInstHoldN : 0.0, fgInstHoldMax,
                fgInstInterpN, fgInstInterpN > 0 ? fgInstInterpSum / fgInstInterpN : 0.0, fgInstInterpMax,
                fgInstLongN, fgInstTotalN));
            fgInstHoldN = fgInstInterpN = fgInstLongN = fgInstTotalN = 0;
            fgInstHoldSum = fgInstInterpSum = fgInstHoldMax = fgInstInterpMax = 0.0;
        }
    }

    private final long[] fgPromoteInfo = new long[4];
    private long fgPromoteSeen = 0;
    private long fgLastPromoteNs = 0, fgPrevPromoteNs = 0;  // times of the last two distinct content frames
    private long fgContentPeriodNs = 0;      // EMA of the interval between distinct content frames
    private int  fgVblankSincePromote = 0;   // vblanks since the last real frame — drives the steady output gate
    private volatile int fgCadenceM = 2;     // divisor-snapped multiplier actually used by the cadence
    private volatile boolean fgResyncPending = false;
    private volatile boolean fgOverlayActive = false;

    // Re-anchor the FG content clock + working multiplier after a pause/overlay/focus gap.
    private void doFgResync() {
        fgLockedGameHz = 0.0;
        fgContentPeriodNs = 0L;
        fgPrevPromoteNs = 0L;
        fgLastPromoteNs = 0L;
        fgEngineFrames = 0;
        fgVblankSincePromote = 0;
        fgLastEmitVsyncNs = 0;
        fgPromoteVsyncNs = 0;
        fgSlotsLocked = 0;
        fgEffectiveMultiplier = fgMultiplier;
        fgBoundSecs = 0;
        fgNewScene.set(true);
    }

    /** Mark a UI overlay (drawer) active; clearing it re-anchors the FG clock fresh. Thread-safe. */
    public void fgSetOverlayActive(boolean active) {
        if (fgOverlayActive == active) return;
        fgOverlayActive = active;
        if (!active) fgResyncPending = true;
    }

    /** Idempotently resume FG if it is in overlay-pause. Does nothing when not paused. */
    public void fgClearOverlayIfActive() {
        if (fgOverlayActive) { fgOverlayActive = false; fgResyncPending = true; }
    }

    private int fgEmitOne() {
        if (fgResyncPending) { fgResyncPending = false; doFgResync(); }
        boolean newGame = fgNewScene.getAndSet(false);
        boolean dirty   = fgSceneDirty.getAndSet(false);

        long vsync = fgCurrentVsyncNs;
        boolean tick = vsync == 0L || vsync != fgLastEmitVsyncNs;
        if (tick) fgLastEmitVsyncNs = vsync;

        int eff = Math.max(2, fgEffectiveMultiplier);
        long disp = fgDisplayPeriodNs;
        long period = fgContentPeriodNs;
        int slots = fgSlotsLocked;
        if (disp > 0L && period > 0L) {
            double ratio = (double) period / (double) disp;
            if (slots < 1 || Math.abs(ratio - slots) > 0.65) {
                slots = Math.max(1, (int) Math.round(ratio));
            }
            fgSlotsLocked = slots;
        }
        if (slots < 1) slots = eff;
        int emits = Math.max(1, Math.min(eff, slots));
        fgCadenceM = emits;
        int lastSlot = fgSlotVblank(emits - 1, slots, emits);

        boolean staticContent = period <= 0L || fgLastPromoteNs == 0L
                || (System.nanoTime() - fgLastPromoteNs) > 2L * period;
        boolean uiHold = dirty && !newGame && staticContent;
        if (dirty && !newGame && !staticContent) fgSceneDirty.set(true);
        boolean hold = newGame || uiHold;
        fgEmitWasHold = hold;

        boolean promoted = false, uiPromote = false;
        if (nativeHandle != 0) {
            if (hold) fgBuildAndStage(uiHold);
            else nativeFgResolveStage(nativeHandle);
            nativeFgPromoteInfo(nativeHandle, fgPromoteInfo);
            if (fgPromoteInfo[0] != fgPromoteSeen) {
                fgPromoteSeen = fgPromoteInfo[0];
                long pNs = fgLastGameNs != 0L ? fgLastGameNs
                        : (fgPromoteInfo[1] != 0L ? fgPromoteInfo[1] : System.nanoTime());
                if (pNs != fgLastPromoteNs) {
                    promoted = true;
                    if (fgLastPromoteNs != 0L) {
                        long d = pNs - fgLastPromoteNs;
                        if (d > 0L && d < 500_000_000L) {
                            fgContentPeriodNs = fgContentPeriodNs == 0L ? d
                                    : fgContentPeriodNs + (d - fgContentPeriodNs) / 8L;
                            double inst = 1.0e9 / (double) fgContentPeriodNs;
                            fgLockedGameHz = fgLockedGameHz <= 0.0 ? inst
                                    : fgLockedGameHz + (inst - fgLockedGameHz) * 0.25;
                        }
                    }
                    fgPrevPromoteNs = fgLastPromoteNs;
                    fgLastPromoteNs = pNs;
                    fgEngineFrames++;
                    fgVblankSincePromote = 0;
                    fgPromoteVsyncNs = vsync;
                } else {
                    uiPromote = true;
                }
            }
        }
        if (!promoted) {
            if (vsync > 0L && disp > 0L && fgPromoteVsyncNs > 0L) {
                long d = vsync - fgPromoteVsyncNs;
                fgVblankSincePromote = d <= 0L ? 0 : (int) ((d + disp / 2L) / disp);
            } else if (tick) {
                fgVblankSincePromote++;
            }
        }

        long target = fgPresentTargetNs();
        boolean canInterp = fgMultiplier > 1 && fgEngineFrames >= 2 && period > 0L
                            && fgLastPromoteNs != 0L && fgPrevPromoteNs != 0L;
        if (!canInterp) {
            if (hold || uiPromote) {
                nativePresentLast(nativeHandle, 0f, fgPrevPromoteNs, fgLastPromoteNs, target);
                return 2;
            }
            return 0;
        }

        int vi = fgVblankSincePromote;
        if (vi > lastSlot) {
            if (uiPromote) {
                nativePresentLast(nativeHandle, 0f, fgPrevPromoteNs, fgLastPromoteNs, target);
                return 2;
            }
            return 0;
        }
        if (!tick && !promoted) return 0;

        int k = -1;
        for (int i = 0; i < emits; i++) {
            if (fgSlotVblank(i, slots, emits) == vi) { k = i; break; }
        }
        if (k < 0) return 0;

        int realSlot = fgExtrapolate ? 0 : emits - 1;
        if (k == realSlot) {
            nativePresentLast(nativeHandle, 0f, fgPrevPromoteNs, fgLastPromoteNs, target);
            return 2;
        }
        int v = fgSlotVblank(k, slots, emits);
        double phase = fgExtrapolate
                ? (double) v / (double) slots
                : 1.0 - (double) (lastSlot - v) / (double) slots;
        nativeRenderInterp(nativeHandle, (float) phase, fgPrevPromoteNs, fgLastPromoteNs, target);
        return 1;
    }

    private static int fgSlotVblank(int k, int slots, int emits) {
        return (int) (((long) k * (long) slots + (long) (emits / 2)) / (long) emits);
    }

    private long fgPresentTargetNs() {
        long v = fgCurrentVsyncNs, d = fgDisplayPeriodNs;
        return (v > 0L && d > 0L) ? v + d : 0L;
    }

    // Target FG post rate (Hz): multiplier × locked game rate, capped to the panel max. 0 if not measured.
    private double fgTargetHz() {
        double g = fgLockedGameHz;
        if (g <= 0.0) return 0.0;
        double target = Math.max(1, fgCadenceM) * g;
        if (fgDisplayCapHz > 0) target = Math.min(target, (double) fgDisplayCapHz);
        return target;
    }

    private int fgComputePerTick() {
        return 1;
    }

    private void fgApplyFrameRateHint(double targetHz, long nowNs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        float rate = frameGenEnabled && targetHz > 0.0 ? (float) Math.round(targetHz) : 0f;
        if (rate == fgFrameRateHint) return;
        if (rate != 0f && fgFrameRateHint > 0f && Math.abs(rate - fgFrameRateHint) <= 5f) return; // EMA jitter
        if (rate != 0f && nowNs - fgFrameRateHintNs < 500_000_000L) return;
        // DEFAULT (exact-or-multiple), not FIXED_SOURCE (video pulldown semantics).
        Surface s = fgSurface;
        if (s != null && s.isValid()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    s.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                            Surface.CHANGE_FRAME_RATE_ALWAYS);
                } else {
                    s.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
                }
            } catch (IllegalStateException | IllegalArgumentException ignored) {}
        }
        fgFrameRateHint = rate;
        fgFrameRateHintNs = nowNs;
        Log.i(TAG, "FG target display rate: " + (int) rate + "Hz (game=" + Math.round(fgLockedGameHz)
                + " x" + fgMultiplier + (fgDeepMode ? " quality" : " standard") + ")");
        Runnable l = fgRateChangedListener;
        if (l != null) l.run();
    }

    /** Live FG display target (multiplier × measured game fps, rounded), or 0 if unknown/off. */
    public int getFrameGenTargetHz() {
        float rate = fgFrameRateHint;
        return rate > 0f ? Math.round(rate) : 0;
    }

    /** Invoked (any thread) whenever the FG display target changes; 0-target means FG off. */
    public void setFrameGenRateChangedListener(Runnable listener) {
        fgRateChangedListener = listener;
    }

    private Drawable createRootCursorDrawable() {
        Context context = xServerView.getContext();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.cursor, options);
        return Drawable.fromBitmap(bitmap);
    }

    // ----- Surface lifecycle (called from XServerSurfaceView) ----------------

    public void setGraphicsDriver(String driverName) {
        this.graphicsDriverName = driverName;
    }

    public void attachSurface(Surface surface) {
        fgSurface = surface;
        fgFrameRateHint = -1f;
        // Serialize with detachSurface()/destroy() so a re-attach can't overlap a native teardown.
        synchronized (this) {
            if (nativeHandle == 0) {
                nativeHandle = nativeCreate(shouldEnableValidationLayers(),
                        graphicsDriverName, xServerView.getContext().getApplicationContext());
                if (nativeHandle == 0) {
                    Log.e(TAG, "nativeCreate failed");
                    return;
                }
                Texture.setRendererHandle(nativeHandle);
                // Apply the cached present-mode request (no-op if it equals the native default FIFO).
                if (requestedPresentMode != PRESENT_MODE_FIFO) {
                    nativeSetPresentMode(nativeHandle, requestedPresentMode);
                }
                if (requestedScaleFilter != SCALE_FILTER_OFF) {
                    nativeSetScaleFilter(nativeHandle, requestedScaleFilter);
                }
                destroyed.set(false);
                xServer.windowManager.addOnWindowModificationListener(this);
                xServer.pointer.addOnPointerMotionListener(this);
            }
            if (frameGenEnabled) {
                nativeSetFrameGeneration(nativeHandle, true);
                nativeSetPresentMode(nativeHandle, PRESENT_MODE_MAILBOX);
                fgActivePresentMode = nativeGetActivePresentMode(nativeHandle);
                pushFrameGenParams();
                nativeSetFrameGenDeepMode(nativeHandle, fgDeepMode);
                            fgEngineFrames = 0;
                fgNewScene.set(true);
                startFgPumpThread();
                scheduleFgPump();
            }
            nativeSurfaceCreated(nativeHandle, surface);
        }
    }

    private boolean shouldEnableValidationLayers() {
        // Force validation layers off and clear any stale pref (the UI toggle is disabled).
        Context context = xServerView.getContext();
        android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(PREF_VULKAN_VALIDATION_LAYERS, false)) {
            prefs.edit().putBoolean(PREF_VULKAN_VALIDATION_LAYERS, false).apply();
        }
        return false;
    }

    public void notifySurfaceChanged(int w, int h) {
        if (nativeHandle == 0) return;
        nativeSurfaceChanged(nativeHandle, w, h);
        if (!supportProbed && xServer.isDri3Enabled()) {
            GPUImage.checkIsSupported();
            supportProbed = true;
        }
    }

    public void detachSurface() {
        // Same monitor as destroy()/attachSurface; re-check the handle under the lock.
        synchronized (this) {
            if (nativeHandle != 0) nativeSurfaceDestroyed(nativeHandle);
        }
    }

    /** Start mirroring the composited output into {@code encoderSurface}; false if the native setup failed. */
    public boolean startRecording(Surface encoderSurface, int fps, boolean recordUI) {
        synchronized (this) {
            if (nativeHandle == 0 || encoderSurface == null) return false;
            return nativeStartRecording(nativeHandle, encoderSurface, fps, recordUI);
        }
    }

    /** Upload the latest overlay snapshot (direct ByteBuffer of BGRA pixels) for the Record-UI composite. */
    public void updateRecordUITexture(java.nio.ByteBuffer bgra, int width, int height) {
        long handle = nativeHandle;
        if (handle != 0 && bgra != null && bgra.isDirect()) {
            nativeUpdateRecordUITexture(handle, bgra, width, height);
        }
    }

    public void stopRecording() {
        synchronized (this) {
            if (nativeHandle != 0) nativeStopRecording(nativeHandle);
        }
    }

    /** Width of the actual composited image (may differ from the SurfaceView size under rotation). */
    public int getRecordWidth() {
        synchronized (this) {
            return nativeHandle != 0 ? nativeGetRecordWidth(nativeHandle) : 0;
        }
    }

    public int getRecordHeight() {
        synchronized (this) {
            return nativeHandle != 0 ? nativeGetRecordHeight(nativeHandle) : 0;
        }
    }

    /** Clockwise degrees to rotate captured frames to appear upright (undoes the display rotation). */
    public int getRecordOrientationHint() {
        synchronized (this) {
            return nativeHandle != 0 ? nativeGetRecordOrientationHint(nativeHandle) : 0;
        }
    }

    @Override
    public void onSurfaceCreated() {
        // Surface already attached in attachSurface().
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        surfaceWidth = width;
        surfaceHeight = height;
        viewTransformation.update(width, height,
                xServer.screenInfo.width, xServer.screenInfo.height);
        viewportNeedsUpdate = true;
    }

    @Override
    public void onSurfaceDestroyed() {
        destroy();
    }

    @Override
    public void onDrawFrame() {
        if (nativeHandle == 0) return;
        if (frameGenEnabled) {
            fgDrawFrame();
        } else {
            buildAndSubmitFrame();
        }
    }

    // ----- Scene assembly ----------------------------------------------------

    private void buildAndSubmitFrame() {
        // Self-heal: if the real surface size differs from our cache (display reparent), recompute the viewport.
        if (xServerView != null) {
            int actualW = xServerView.getSurfaceWidth();
            int actualH = xServerView.getSurfaceHeight();
            if (actualW > 0 && actualH > 0 && (actualW != surfaceWidth || actualH != surfaceHeight)) {
                surfaceWidth = actualW;
                surfaceHeight = actualH;
                viewTransformation.update(actualW, actualH,
                        xServer.screenInfo.width, xServer.screenInfo.height);
                viewportNeedsUpdate = true;
            }
        }

        textureUploadBatch.reset();
        boolean useScissor = false;

        if (magnifierEnabled) {
            computeMagnifierPan(sceneXform);
        } else if (!fullscreen) {
            int pointerY = 0;
            if (screenOffsetYRelativeToCursor) {
                short halfScreenHeight = (short) (xServer.screenInfo.height / 2);
                pointerY = Mathf.clamp(xServer.pointer.getY() - halfScreenHeight / 2, 0, halfScreenHeight);
            }
            XForm.makeTransform(sceneXform,
                    viewTransformation.sceneOffsetX,
                    viewTransformation.sceneOffsetY - pointerY,
                    viewTransformation.sceneScaleX,
                    viewTransformation.sceneScaleY,
                    0);
            useScissor = true;
        } else {
            XForm.identity(sceneXform);
        }

        final ByteBuffer buf = sceneBuf;

        int viewX, viewY, viewW, viewH;
        if (fullscreen) {
            viewX = 0;
            viewY = 0;
            viewW = surfaceWidth;
            viewH = surfaceHeight;
        } else {
            viewX = viewTransformation.viewOffsetX;
            viewY = viewTransformation.viewOffsetY;
            viewW = viewTransformation.viewWidth;
            viewH = viewTransformation.viewHeight;
        }
        buf.putInt(OFF_VIEWPORT,      viewX);
        buf.putInt(OFF_VIEWPORT + 4,  viewY);
        buf.putInt(OFF_VIEWPORT + 8,  viewW);
        buf.putInt(OFF_VIEWPORT + 12, viewH);

        // Scissor (non-magnifier non-fullscreen): clamp to the framebuffer so a ZOOM/crop viewport overflow never yields an out-of-bounds scissor.
        if (useScissor) {
            int sX = Math.max(0, viewTransformation.viewOffsetX);
            int sY = Math.max(0, viewTransformation.viewOffsetY);
            int sRight = Math.min(surfaceWidth, viewTransformation.viewOffsetX + viewTransformation.viewWidth);
            int sBottom = Math.min(surfaceHeight, viewTransformation.viewOffsetY + viewTransformation.viewHeight);
            int sW = Math.max(0, sRight - sX);
            int sH = Math.max(0, sBottom - sY);
            buf.putInt(OFF_SCISSOR_ENABLED, 1);
            buf.putInt(OFF_SCISSOR,      sX);
            buf.putInt(OFF_SCISSOR + 4,  sY);
            buf.putInt(OFF_SCISSOR + 8,  sW);
            buf.putInt(OFF_SCISSOR + 12, sH);
        } else {
            buf.putInt(OFF_SCISSOR_ENABLED, 0);
            // Native gates on scissor_enabled anyway; zero the rect for cleanliness.
            buf.putInt(OFF_SCISSOR,      0);
            buf.putInt(OFF_SCISSOR + 4,  0);
            buf.putInt(OFF_SCISSOR + 8,  0);
            buf.putInt(OFF_SCISSOR + 12, 0);
        }

        buf.putFloat(OFF_XFORM,      sceneXform[0]);
        buf.putFloat(OFF_XFORM + 4,  sceneXform[1]);
        buf.putFloat(OFF_XFORM + 8,  sceneXform[2]);
        buf.putFloat(OFF_XFORM + 12, sceneXform[3]);
        buf.putFloat(OFF_XFORM + 16, sceneXform[4]);
        buf.putFloat(OFF_XFORM + 20, sceneXform[5]);

        viewportNeedsUpdate = false;

        // Collect renderable windows (occlusion skipping).
        int winCount = 0;
        long cursorHandle = 0;
        boolean cursorOnscreen = false;
        int cursorPosX = 0, cursorPosY = 0, cursorW = 0, cursorH = 0;
        int sourceW = 0;
        int sourceH = 0;
        int sourceArea = 0;

        try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.DRAWABLE_MANAGER)) {
            int screenW = xServer.screenInfo.width;
            int screenH = xServer.screenInfo.height;
            int startIndex = 0;
            for (int i = renderableWindows.size() - 1; i >= 0; i--) {
                RenderableWindow rWin = renderableWindows.get(i);
                if (rWin.content != null
                        && rWin.content.width >= screenW
                        && rWin.content.height >= screenH) {
                    startIndex = i;
                    break;
                }
            }

            for (int i = startIndex; i < renderableWindows.size() && winCount < MAX_WINDOWS; i++) {
                RenderableWindow rw = renderableWindows.get(i);
                if (rw.content == null) continue;
                Drawable drawable = rw.content;
                Drawable textureSrc;
                int scanoutX;
                int scanoutY;
                Texture tex;
                synchronized (drawable.renderLock) {
                    textureSrc = drawable.getScanoutSource();
                    if (textureSrc != null) {
                        scanoutX = drawable.getScanoutX();
                        scanoutY = drawable.getScanoutY();
                    } else {
                        textureSrc = drawable;
                        scanoutX = 0;
                        scanoutY = 0;
                    }
                    if (textureSrc == drawable && !drawable.hasContent()) continue;
                    tex = textureSrc.getTexture();
                    if (tex != null) {
                        tex.appendUploadFromDrawable(textureSrc, textureUploadBatch);
                    }
                }
                if (tex == null || !tex.isAllocated()) continue;
                int candidateW = 0;
                int candidateH = 0;
                if (drawable.hasPresentedSourceSize()) {
                    candidateW = Short.toUnsignedInt(drawable.getPresentedSourceWidth());
                    candidateH = Short.toUnsignedInt(drawable.getPresentedSourceHeight());
                } else {
                    int drawableW = Short.toUnsignedInt(drawable.width);
                    int drawableH = Short.toUnsignedInt(drawable.height);
                    if ((long)drawableW * (long)drawableH >= ((long)screenW * (long)screenH) / 4L) {
                        candidateW = drawableW;
                        candidateH = drawableH;
                    }
                }
                int candidateArea = candidateW * candidateH;
                if (candidateW > 0 && candidateH > 0 && candidateArea > sourceArea) {
                    sourceW = candidateW;
                    sourceH = candidateH;
                    sourceArea = candidateArea;
                }
                if (!loggedAhbSceneUse && tex instanceof GPUImage && ApplicationLogGate.isEnabled()) {
                    Log.i(TAG, "Submitting AHB-backed texture in Vulkan scene: windowCount="
                            + (winCount + 1)
                            + " tex=0x"
                            + Long.toHexString(tex.getNativeHandle()));
                    loggedAhbSceneUse = true;
                }
                buf.putLong(OFF_WINDOW_HANDLES + winCount * 8, tex.getNativeHandle());
                int gOff = OFF_WINDOW_GEOM + winCount * 16;
                buf.putInt(gOff,      rw.rootX);
                buf.putInt(gOff + 4,  rw.rootY);
                buf.putInt(gOff + 8,  drawable.width);
                buf.putInt(gOff + 12, drawable.height);
                int uvOff = OFF_WINDOW_UV + winCount * 16;
                if (textureSrc != drawable) {
                    float invW = 1.0f / Math.max(1, textureSrc.width);
                    float invH = 1.0f / Math.max(1, textureSrc.height);
                    buf.putFloat(uvOff,      -scanoutX * invW);
                    buf.putFloat(uvOff + 4,  -scanoutY * invH);
                    buf.putFloat(uvOff + 8,  (drawable.width - scanoutX) * invW);
                    buf.putFloat(uvOff + 12, (drawable.height - scanoutY) * invH);
                } else {
                    buf.putFloat(uvOff,      0.0f);
                    buf.putFloat(uvOff + 4,  0.0f);
                    buf.putFloat(uvOff + 8,  1.0f);
                    buf.putFloat(uvOff + 12, 1.0f);
                }
                winCount++;
            }

            if (cursorVisible) {
                Window pointWindow = xServer.inputDeviceManager.getPointWindow();
                Cursor cursor = pointWindow != null ? pointWindow.attributes.getCursor() : null;
                short x = xServer.pointer.getClampedX();
                short y = xServer.pointer.getClampedY();

                Drawable cursorDrawable = null;
                int hotX = 0, hotY = 0;
                if (cursor != null) {
                    if (cursor.isVisible()) {
                        cursorDrawable = cursor.cursorImage;
                        hotX = cursor.hotSpotX;
                        hotY = cursor.hotSpotY;
                    }
                } else {
                    cursorDrawable = rootCursorDrawable;
                }

                if (cursorDrawable != null) {
                    Texture tex = cursorDrawable.getTexture();
                    synchronized (cursorDrawable.renderLock) {
                        if (tex != null) tex.appendUploadFromDrawable(cursorDrawable, textureUploadBatch);
                    }
                    if (tex != null && tex.isAllocated()) {
                        cursorHandle = tex.getNativeHandle();
                        cursorPosX = x - hotX;
                        cursorPosY = y - hotY;
                        cursorW = cursorDrawable.width;
                        cursorH = cursorDrawable.height;
                        cursorOnscreen = true;
                    }
                }
            }

        }

        textureUploadBatch.flush(nativeHandle);

        buf.putInt(OFF_WINDOW_COUNT, winCount);
        buf.putLong(OFF_CURSOR_HANDLE, cursorHandle);
        buf.putInt(OFF_CURSOR_VISIBLE, cursorOnscreen ? 1 : 0);
        buf.putInt(OFF_CURSOR_GEOM,      cursorPosX);
        buf.putInt(OFF_CURSOR_GEOM + 4,  cursorPosY);
        buf.putInt(OFF_CURSOR_GEOM + 8,  cursorW);
        buf.putInt(OFF_CURSOR_GEOM + 12, cursorH);

        buf.putInt(OFF_SCREEN_W, xServer.screenInfo.width);
        buf.putInt(OFF_SCREEN_H, xServer.screenInfo.height);
        buf.putInt(OFF_SWAP_RB, swapRB ? 1 : 0);
        buf.putInt(OFF_SOURCE_W, sourceW);
        buf.putInt(OFF_SOURCE_H, sourceH);

        Effect[] active = effectComposer.snapshot();
        int effectCount = Math.min(active.length, MAX_EFFECTS);
        buf.putInt(OFF_EFFECT_COUNT, effectCount);
        for (int i = 0; i < effectCount; i++) {
            buf.putInt(OFF_EFFECT_TYPES + i * 4, active[i].getNativeType());
            active[i].writeParams(effectParamsScratch, i * 4);
            int pOff = OFF_EFFECT_PARAMS + i * 16;
            buf.putFloat(pOff,      effectParamsScratch[i * 4]);
            buf.putFloat(pOff + 4,  effectParamsScratch[i * 4 + 1]);
            buf.putFloat(pOff + 8,  effectParamsScratch[i * 4 + 2]);
            buf.putFloat(pOff + 12, effectParamsScratch[i * 4 + 3]);
        }

        nativeSetScene(nativeHandle, buf);
        if (frameGenEnabled) {
            // FG: render into the history ring without presenting; presents are issued by fgEmitOne().
            nativeRenderHold(nativeHandle, fgForcePromote);
        } else {
            nativeRenderFrame(nativeHandle);
        }
    }

    private void fgBuildAndStage(boolean uiOnly) {
        fgForcePromote = uiOnly;
        try {
            buildAndSubmitFrame();
        } finally {
            fgForcePromote = false;
        }
    }

    // ----- WindowManager / Pointer listeners --------------------------------

    @Override
    public void onMapWindow(Window window) {
        xServerView.queueEvent(this::updateScene);
        requestRenderCoalesced();
    }

    @Override
    public void onUnmapWindow(Window window) {
        xServerView.queueEvent(this::updateScene);
        requestRenderCoalesced();
    }

    @Override
    public void onChangeWindowZOrder(Window window) {
        xServerView.queueEvent(this::updateScene);
        requestRenderCoalesced();
    }

    @Override
    public void onUpdateWindowContent(Window window) {
        requestRenderCoalesced();
    }

    @Override
    public void onUpdateWindowGeometry(final Window window, boolean resized) {
        if (resized) {
            xServerView.queueEvent(this::updateScene);
        } else {
            xServerView.queueEvent(() -> updateWindowPosition(window));
            xServerView.queueEvent(this::updateScene);
        }
        requestRenderCoalesced();
    }

    @Override
    public void onUpdateWindowAttributes(Window window, Bitmask mask) {
        if (mask.isSet(WindowAttributes.FLAG_CURSOR)) requestRenderCoalesced();
    }

    public void requestCursorRender() {
        cursorActiveUntilNs = System.nanoTime() + CURSOR_ACTIVE_NS;
        if (frameGenEnabled) {
            fgSceneDirty.set(true);
            scheduleFgPump();
            return;
        }
        xServerView.requestTransientRender(100);
    }

    public void updateVisualCursorPosition(int x, int y) {
        requestCursorRender();
    }

    @Override
    public void onPointerMove(short x, short y) {
        requestCursorRender();
    }

    @Override
    public void onFramePresented(Window window, WindowManager.FrameSource source, int serial) {
        if (source == WindowManager.FrameSource.DRI3_BUFFER) return;
        if (frameGenEnabled) {
            Drawable scanoutNow = (window != null && window.getContent() != null)
                    ? window.getContent().getScanoutSource() : null;
            if (scanoutNow != null) {
                if (fgFirstScanoutSrc == null) fgFirstScanoutSrc = scanoutNow;
                else if (scanoutNow != fgFirstScanoutSrc) fgMultiBuffer = true;
            }
            long now = System.nanoTime();
            if (fgMultiBuffer && scanoutNow != null && scanoutNow == fgLastScanoutSrc
                    && (now - fgLastAcceptNs) < FG_DEDUP_FREEZE_NS) {
                fgDiagDedupDropped++;
                return;   // duplicate buffer — ignore for the FG cadence
            }
            fgDiagAccepted++;
            fgLastScanoutSrc = scanoutNow;
            fgLastAcceptNs = now;
            fgLastGameNs = now;
            fgNewScene.set(true);
            scheduleFgPump();
            return;
        }
        requestRenderCoalesced();
    }

    private void updateScene() {
        try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.DRAWABLE_MANAGER)) {
            renderableWindows.clear();
            collectRenderableWindows(
                    xServer.windowManager.rootWindow,
                    xServer.windowManager.rootWindow.getX(),
                    xServer.windowManager.rootWindow.getY());
        }
    }

    private void collectRenderableWindows(Window window, int x, int y) {
        if (!window.attributes.isMapped()) return;
        if (window != xServer.windowManager.rootWindow) {
            boolean viewable = true;
            if (unviewableWMClasses != null) {
                String wmClass = window.getClassName();
                for (String unviewable : unviewableWMClasses) {
                    if (wmClass.contains(unviewable)) {
                        if (window.attributes.isEnabled()) window.disableAllDescendants();
                        viewable = false;
                        break;
                    }
                }
            }
            if (viewable) renderableWindows.add(new RenderableWindow(window.getContent(), x, y));
        }
        for (Window child : window.getChildren()) {
            collectRenderableWindows(child, child.getX() + x, child.getY() + y);
        }
    }

    private void updateWindowPosition(Window window) {
        for (RenderableWindow rw : renderableWindows) {
            if (rw.content == window.getContent()) {
                rw.rootX = (short) window.getRootX();
                rw.rootY = (short) window.getRootY();
                break;
            }
        }
    }

    // ----- Public API -------------------------------------------------------

    public EffectComposer getEffectComposer() { return effectComposer; }

    public void onXServerScreenChanged() {
        int oldViewWidth = viewTransformation.viewWidth;
        int oldViewHeight = viewTransformation.viewHeight;
        int oldViewOffsetX = viewTransformation.viewOffsetX;
        int oldViewOffsetY = viewTransformation.viewOffsetY;
        if (surfaceWidth > 0 && surfaceHeight > 0) {
            viewTransformation.update(surfaceWidth, surfaceHeight,
                    xServer.screenInfo.width, xServer.screenInfo.height);
        }
        viewportNeedsUpdate = true;
        magnifierPanInitialized = false;
        updateScene();
        if (ApplicationLogGate.isEnabled()) {
            Log.i(TAG, "XServer screen changed: screen=" + xServer.screenInfo +
                    " surface=" + surfaceWidth + "x" + surfaceHeight +
                    " view=" + oldViewWidth + "x" + oldViewHeight + "@" +
                    oldViewOffsetX + "," + oldViewOffsetY + " -> " +
                    viewTransformation.viewWidth + "x" + viewTransformation.viewHeight +
                    "@" + viewTransformation.viewOffsetX + "," + viewTransformation.viewOffsetY);
        }
        requestRenderCoalesced();
    }

    public void toggleFullscreen() {
        fullscreen = !fullscreen;
        viewportNeedsUpdate = true;
        requestRenderCoalesced();
    }

    public boolean isFullscreen() { return fullscreen; }

    public void setCursorVisible(boolean v) {
        if (this.cursorVisible == v) return;
        this.cursorVisible = v;
        requestRenderCoalesced();
    }

    public boolean isCursorVisible() { return cursorVisible; }

    public boolean isScreenOffsetYRelativeToCursor() { return screenOffsetYRelativeToCursor; }

    public void setScreenOffsetYRelativeToCursor(boolean v) {
        this.screenOffsetYRelativeToCursor = v;
        requestRenderCoalesced();
    }

    public float getMagnifierZoom() { return magnifierZoom; }

    public void setMagnifierZoom(float v) {
        if (this.magnifierZoom != v) {
            this.magnifierZoom = v;
            magnifierPanInitialized = false;
        }
        requestRenderCoalesced();
    }

    private void computeMagnifierPan(float[] outXForm) {
        float currentZoom = !screenOffsetYRelativeToCursor ? this.magnifierZoom : 1.0f;
        if (currentZoom <= 1.0f && !screenOffsetYRelativeToCursor) {
            magnifierPanX = 0;
            magnifierPanY = 0;
            magnifierPanInitialized = false;
            XForm.identity(outXForm);
            return;
        }

        int screenW = xServer.screenInfo.width;
        int screenH = xServer.screenInfo.height;
        float cursorX = xServer.pointer.getX();
        float cursorY = xServer.pointer.getY();

        if (currentZoom > 1.0f) {
            float maxPanX = screenW * (currentZoom - 1.0f);
            float maxPanY = screenH * (currentZoom - 1.0f);

            if (!magnifierPanInitialized) {
                magnifierPanX = Mathf.clamp(cursorX * currentZoom - screenW * 0.5f, 0, maxPanX);
                magnifierPanY = Mathf.clamp(cursorY * currentZoom - screenH * 0.5f, 0, maxPanY);
                magnifierPanInitialized = true;
            }

            float visibleW = screenW / currentZoom;
            float visibleH = screenH / currentZoom;
            float marginX = visibleW * (1.0f - MAGNIFIER_DEADZONE_FRACTION) * 0.5f;
            float marginY = visibleH * (1.0f - MAGNIFIER_DEADZONE_FRACTION) * 0.5f;

            float visibleLeft = magnifierPanX / currentZoom;
            float visibleTop = magnifierPanY / currentZoom;
            float visibleRight = visibleLeft + visibleW;
            float visibleBottom = visibleTop + visibleH;

            if (cursorX < visibleLeft + marginX) {
                magnifierPanX = (cursorX - marginX) * currentZoom;
            } else if (cursorX > visibleRight - marginX) {
                magnifierPanX = (cursorX - visibleW + marginX) * currentZoom;
            }
            if (cursorY < visibleTop + marginY) {
                magnifierPanY = (cursorY - marginY) * currentZoom;
            } else if (cursorY > visibleBottom - marginY) {
                magnifierPanY = (cursorY - visibleH + marginY) * currentZoom;
            }

            magnifierPanX = Mathf.clamp(magnifierPanX, 0, maxPanX);
            magnifierPanY = Mathf.clamp(magnifierPanY, 0, maxPanY);
        } else {
            magnifierPanX = 0;
            magnifierPanY = 0;
            magnifierPanInitialized = false;
        }

        float panY = magnifierPanY;
        if (currentZoom == 1.0f && screenOffsetYRelativeToCursor) {
            panY = Mathf.clamp(
                    xServer.pointer.getY() * 1.0f - screenH * 0.25f,
                    0,
                    screenH * 0.5f);
        }

        XForm.makeTransform(outXForm, -magnifierPanX, -panY, currentZoom, currentZoom, 0);
    }

    public int getSurfaceWidth() { return surfaceWidth; }
    public int getSurfaceHeight() { return surfaceHeight; }

    public boolean isViewportNeedsUpdate() { return viewportNeedsUpdate; }
    public void setViewportNeedsUpdate(boolean v) { this.viewportNeedsUpdate = v; }

    // Fill mode (FIT/STRETCH/ZOOM), applied live: recompute the viewport and request a frame.
    public void setFillMode(int mode) {
        if (viewTransformation.mode == mode) return;
        viewTransformation.mode = mode;
        if (surfaceWidth > 0 && surfaceHeight > 0) {
            viewTransformation.update(surfaceWidth, surfaceHeight,
                    xServer.screenInfo.width, xServer.screenInfo.height);
        }
        viewportNeedsUpdate = true;
        if (xServerView != null) xServerView.requestRender();
    }

    public int getFillMode() { return viewTransformation.mode; }

    // Set the fill mode without recomputing the viewport (cached size may be stale mid-reparent).
    public void setFillModeQuiet(int mode) {
        viewTransformation.mode = mode;
        viewportNeedsUpdate = true;
    }

    public int getPresentMode() { return requestedPresentMode; }

    // Wipe the cached surface size so the next surfaceChanged/self-heal recomputes from scratch.
    public void invalidateSurfaceSize() {
        surfaceWidth = 0;
        surfaceHeight = 0;
        viewportNeedsUpdate = true;
    }

    /** Force the viewport to recompute against a known surface size (used after a display reparent). */
    public void forceViewportRecompute(int w, int h) {
        if (w <= 0 || h <= 0) return;
        surfaceWidth = w;
        surfaceHeight = h;
        viewTransformation.update(w, h, xServer.screenInfo.width, xServer.screenInfo.height);
        viewportNeedsUpdate = true;
        if (xServerView != null) xServerView.requestRender();
    }

    public void setNativeMode(boolean enable) {
        if (cpuSaverMode != enable) {
            cpuSaverMode = enable;
            viewportNeedsUpdate = true;
            xServerView.setRenderMode(XServerSurfaceView.RENDERMODE_WHEN_DIRTY);
            requestRenderCoalesced();
        }
    }

    public boolean isNativeMode() { return cpuSaverMode; }

    public void setMagnifierUIActive(boolean active) {
        if (magnifierUIActive == active) return;
        magnifierUIActive = active;
        magnifierPanInitialized = false;
        viewportNeedsUpdate = true;
        xServerView.setRenderMode(XServerSurfaceView.RENDERMODE_WHEN_DIRTY);
        requestRenderCoalesced();
    }

    public boolean isMagnifierUIActive() { return magnifierUIActive; }

    public void setFpsLimit(int fps) {
        currentFpsLimit = Math.max(0, Math.min(fps, MAX_FPS_LIMIT));
    }

    public int getFpsLimit() { return currentFpsLimit; }

    // Compositor present-mode constants must mirror the switch in nativeSetPresentMode.
    public static final int PRESENT_MODE_FIFO      = 0;
    public static final int PRESENT_MODE_MAILBOX   = 1;
    public static final int PRESENT_MODE_IMMEDIATE = 2;

    // Cached so a mode can be set before the native renderer exists (applied in attachSurface).
    private int requestedPresentMode = PRESENT_MODE_FIFO;

    public void setPresentMode(int mode) {
        requestedPresentMode = mode;
        if (nativeHandle != 0) nativeSetPresentMode(nativeHandle, mode);
    }

    public static int parsePresentMode(String name) {
        if (name == null) return PRESENT_MODE_FIFO;
        switch (name.trim().toLowerCase()) {
            case "mailbox":   return PRESENT_MODE_MAILBOX;
            case "immediate": return PRESENT_MODE_IMMEDIATE;
            default:          return PRESENT_MODE_FIFO;
        }
    }

    // Scale-filter constants must mirror the switch in nativeSetScaleFilter.
    public static final int SCALE_FILTER_OFF     = 0;
    public static final int SCALE_FILTER_NEAREST = 1;
    public static final int SCALE_FILTER_LINEAR  = 2;
    public static final int SCALE_FILTER_BICUBIC = 3;

    private int requestedScaleFilter = SCALE_FILTER_OFF;

    public void setScaleFilter(int mode) {
        requestedScaleFilter = mode;
        if (nativeHandle != 0) {
            nativeSetScaleFilter(nativeHandle, mode);
            if (xServerView != null) xServerView.requestRender();
        }
    }

    public void setUnviewableWMClasses(String... names) {
        this.unviewableWMClasses = names;
    }

    public void enforceFpsLimit() {
        // No-op: FPS limiting now runs in native (after submit/present); kept for source compatibility.
    }

    // ---- JNI ---------------------------------------------------------------

    private static native long nativeCreate(boolean enableValidationLayers,
                                            String driverName,
                                            android.content.Context context);
    private static native void nativeDestroy(long handle);
    private static native void nativeSurfaceCreated(long handle, Surface surface);
    private static native void nativeSurfaceChanged(long handle, int w, int h);
    private static native void nativeSurfaceDestroyed(long handle);
    private static native boolean nativeStartRecording(long handle, Surface encoderSurface, int fps, boolean recordUI);
    private static native void nativeStopRecording(long handle);
    private static native void nativeUpdateRecordUITexture(long handle, java.nio.ByteBuffer bgra, int width, int height);
    private static native int nativeGetRecordWidth(long handle);
    private static native int nativeGetRecordHeight(long handle);
    private static native int nativeGetRecordOrientationHint(long handle);
    private static native boolean nativeRenderFrame(long handle);
    private static native void nativeSetScene(long handle, ByteBuffer sceneBuf);
    private static native void nativeSetFpsLimit(long handle, int fps);
    private static native void nativeSetPresentMode(long handle, int mode);
    private static native void nativeSetScaleFilter(long handle, int mode);

    // ---- Frame generation ----
    private static native void nativeSetFrameGeneration(long handle, boolean enabled);
    private static native boolean nativeFrameGenerationSupported(long handle);
    private static native long nativeGetDisplayFrameCount(long handle);
    private static native boolean nativeRenderHold(long handle, boolean forcePromote);
    private static native boolean nativeFgResolveStage(long handle);
    private static native boolean nativeRenderInterp(long handle, float phase, long prevNs, long currNs, long targetNs);
    private static native void nativeFgPromoteInfo(long handle, long[] out);
    private static native boolean nativePresentLast(long handle, float phase, long prevNs, long currNs, long targetNs);
    private static native void nativeSetFrameGenParams(long handle, float occLo, float occHi, int minStep);
    private static native void nativeSetFrameGenFlowScale(long handle, float flowScale);
    private static native void nativeSetFrameGenDeepMode(long handle, boolean deep);
    private static native void nativeSetFrameGenExtrapolate(long handle, boolean extrapolate);
    private static native void nativeSetFrameGenFramesInFlight(long handle, int framesInFlight);
    private static native int nativeGetActivePresentMode(long handle);
    private static native void nativeSetVsyncTiming(long handle, long periodNs, long displayPeriodNs, long contentPeriodNs, long vsyncNs);
    private static native void nativeFgPumpStart(Object renderer);
    private static native void nativeFgPumpStop();
}
