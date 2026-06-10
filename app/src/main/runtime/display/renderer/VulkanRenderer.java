package com.winlator.cmod.runtime.display.renderer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
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

/**
 * Native Vulkan compositor.
 *
 * <p>Owns the C-side renderer handle and pushes a scene snapshot every frame. Replaces the
 * previous GLES2 {@code GLRenderer}; preserves the same public API so callers do not change.
 */
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
    // Must be set before attachSurface — nativeCreate reads it once at instance creation.
    private volatile String graphicsDriverName = null;

    // ---- Frame generation ----
    // Display-cadence pump that inserts interpolated frames between real ones; never starves the real frame.
    private volatile boolean frameGenEnabled = false;
    private volatile int fgMultiplier = 2;   // target display:engine ratio (2, 3, 4)
    private final AtomicBoolean fgNewScene = new AtomicBoolean(false);
    private final AtomicBoolean fgSceneDirty = new AtomicBoolean(false);  // cursor/window change awaiting a recomposite
    private final AtomicBoolean fgPumpScheduled = new AtomicBoolean(false);
    private boolean fgPendingReal = false;   // a held real frame awaits its display tick
    private int fgPendingInterps = 0;        // interpolated frames still owed before the held real
    private int fgInterpTotal = 0;           // interps planned for the current engine frame (phase divisor)
    private long fgEngineFrames = 0;         // count of held real frames since FG was enabled
    // EMAs of the pump (=panel) tick interval and the real game-frame interval.
    private volatile long fgDisplayPeriodNs = 0;
    private volatile long fgGamePeriodNs = 0;
    private long fgLastPumpNs = 0;
    private volatile long fgLastGameNs = 0;
    private volatile long fgPrevGameNs = 0;
    private volatile int fgActivePresentMode = PRESENT_MODE_FIFO;  // resolved native mode (see nativeGetActivePresentMode)
    private volatile int fgDisplayCapHz = 0;  // panel-max ceiling for the target post rate; 0 = uncapped
    // Quality/smoothness, mapped to native shader knobs (motion search floor + interp consistency).
    private volatile int fgQuality = 1;      // 0 performance, 1 balanced, 2 quality
    private volatile float fgSmoothness = 0.5f;

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
    public int surfaceWidth;
    public int surfaceHeight;
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
    private final AtomicBoolean renderRequested = new AtomicBoolean(false);

    // Reusable scratch — sized once, refilled per frame.
    private final float[] sceneXform = XForm.getInstance();
    // Effect.writeParams writes into a float[]; we copy into the ByteBuffer afterwards.
    private final float[] effectParamsScratch = new float[MAX_EFFECTS * 4];

    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    public VulkanRenderer(XServerSurfaceView view, XServer xServer) {
        this.xServerView = view;
        this.xServer = xServer;
        this.effectComposer = new EffectComposer(this);
        this.rootCursorDrawable = createRootCursorDrawable();
    }

    public void destroy() {
        if (destroyed.compareAndSet(false, true)) {
            // LEAK FIX: Unregister from the persistent XServer to prevent "zombie" listeners
            xServer.windowManager.removeOnWindowModificationListener(this);
            xServer.pointer.removeOnPointerMotionListener(this);

            if (nativeHandle != 0) {
                // If we are on the UI thread, nativeDestroy (which might block on vkDeviceWaitIdle)
                // should run on a background thread to avoid freezing the UI.
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

    public void requestRenderCoalesced() {
        if (frameGenEnabled) {
            // Non-game change (cursor/window/geometry): mark dirty so the pump recomposites it.
            fgSceneDirty.set(true);
            scheduleFgPump();
            return;
        }
        if (renderRequested.compareAndSet(false, true)) {
            mainHandler.post(() ->
                    Choreographer.getInstance().postFrameCallback(frameTimeNanos -> {
                        renderRequested.set(false);
                        xServerView.requestRender();
                    }));
        }
    }

    // ---- Frame generation driver -------------------------------------------

    /** Toggle native frame generation. Safe to call from any thread. */
    public void setFrameGeneration(boolean enabled) {
        frameGenEnabled = enabled;
        synchronized (this) {
            if (nativeHandle != 0) {
                nativeSetFrameGeneration(nativeHandle, enabled);
                // MAILBOX over-post holds the adaptive panel high; the native clock_nanosleep pacer spaces presents evenly.
                nativeSetPresentMode(nativeHandle, enabled ? PRESENT_MODE_MAILBOX : requestedPresentMode);
                fgActivePresentMode = nativeGetActivePresentMode(nativeHandle);
            }
        }
        if (enabled) {
            pushFrameGenParams();
            fgPendingReal = false;
            fgPendingInterps = 0;
            fgInterpTotal = 0;
            fgEngineFrames = 0;
            fgNewScene.set(true);   // re-render current content as the first held frame
            scheduleFgPump();
        }
        // When disabled, the pump self-stops (fgPumpTick checks frameGenEnabled) and onDrawFrame
        // reverts to the coalesced real-present path.
    }

    public boolean isFrameGenerationEnabled() { return frameGenEnabled; }

    /** Target display:engine ratio (2, 3, 4). Snapped to a supported value. Live; safe from any thread. */
    public void setFrameGenerationMultiplier(int multiplier) {
        fgMultiplier = multiplier <= 2 ? 2 : (multiplier >= 4 ? 4 : 3);
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

    /** Interpolation smoothness in [0,1] (higher trusts motion more — smoother, more ghosting). Live. */
    public void setFrameGenerationSmoothness(float smoothness) {
        fgSmoothness = smoothness < 0f ? 0f : (smoothness > 1f ? 1f : smoothness);
        pushFrameGenParams();
    }

    public float getFrameGenerationSmoothness() { return fgSmoothness; }

    // Map quality preset + smoothness to the native interpolate.frag / motion.comp knobs.
    private void pushFrameGenParams() {
        float occHi = 0.12f + 0.28f * fgSmoothness;   // consistency window: wider == trusts motion more
        float occLo = occHi * 0.25f;
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

    private void scheduleFgPump() {
        if (!frameGenEnabled) return;
        if (fgPumpScheduled.compareAndSet(false, true)) {
            mainHandler.post(() -> Choreographer.getInstance().postFrameCallback(this::fgPumpTick));
        }
    }

    // Free-running display-cadence pump: each tick wakes the render thread (onDrawFrame ->
    // fgDrawFrame) and re-arms itself while FG is enabled.
    private void fgPumpTick(long frameTimeNanos) {
        fgPumpScheduled.set(false);
        if (!frameGenEnabled || nativeHandle == 0) return;
        // The swapchain may still be FIFO right after enable (surface not attached yet); re-read until
        // it resolves so the bootstrap engages at launch without a manual toggle.
        if (fgActivePresentMode == PRESENT_MODE_FIFO) {
            fgActivePresentMode = nativeGetActivePresentMode(nativeHandle);
        }
        if (fgLastPumpNs != 0L) {
            long d = frameTimeNanos - fgLastPumpNs;
            if (d > 0L && d < 100_000_000L) {  // ignore stalls / outliers
                fgDisplayPeriodNs = fgDisplayPeriodNs == 0L ? d : fgDisplayPeriodNs + (d - fgDisplayPeriodNs) / 8L;
                if (nativeHandle != 0) {
                    double th = fgTargetHz();
                    nativeSetVsyncTiming(nativeHandle, th > 0.0 ? (long) (1.0e9 / th) : fgDisplayPeriodNs, frameTimeNanos);
                }
            }
        }
        fgLastPumpNs = frameTimeNanos;
        xServerView.requestRender();
        scheduleFgPump();
    }

    // Render-thread scheduler (DESIGN.md §2): emit enough presents per tick to sustain the target rate.
    private void fgDrawFrame() {
        int perTick = fgComputePerTick();
        for (int i = 0; i < perTick; i++) fgEmitOne();
    }

    private void fgEmitOne() {
        if (fgPendingInterps == 0 && !fgPendingReal) {
            boolean newGame = fgNewScene.getAndSet(false);
            boolean dirty = fgSceneDirty.getAndSet(false);
            if (!newGame && !dirty) return;              // nothing changed — idle tick
            buildAndSubmitFrame();                       // HOLD -> history[curr] (no present)
            fgEngineFrames++;
            // Interpolate only between real game frames; a cursor/UI-only change just recomposites.
            int interps = (newGame && fgEngineFrames >= 2) ? fgComputeInterps() : 0;
            fgInterpTotal = interps;
            fgPendingInterps = interps;
            fgPendingReal = true;
        }
        if (fgPendingInterps > 0) {
            int k = fgInterpTotal - fgPendingInterps + 1;            // 1..fgInterpTotal
            float phase = (float) k / (float) (fgInterpTotal + 1);   // even fallback; native refines from arrival times
            nativeRenderInterp(nativeHandle, phase, fgPrevGameNs, fgLastGameNs);
            fgPendingInterps--;
        } else if (fgPendingReal) {
            nativePresentLast(nativeHandle);                         // the held real frame
            fgPendingReal = false;
        }
    }

    // Target FG post rate (Hz): multiplier × game rate, capped to the panel max. 0 if not measured.
    private double fgTargetHz() {
        long game = fgGamePeriodNs;
        if (game <= 0L) return 0.0;
        double target = Math.max(1, fgMultiplier) * (1.0e9 / (double) game);
        if (fgDisplayCapHz > 0) target = Math.min(target, (double) fgDisplayCapHz);
        return target;
    }

    // Interpolated frames to insert between this engine frame and the previous one.
    private int fgComputeInterps() {
        int maxInterps = Math.max(1, fgMultiplier) - 1;             // 2x->1, 3x->2, 4x->3
        long disp = fgDisplayPeriodNs, game = fgGamePeriodNs;
        if (disp <= 0L || game <= 0L) return 0;
        if (fgActivePresentMode == PRESENT_MODE_FIFO) {
            // Vsync-locked: insert what the current refresh affords. Epsilon absorbs EMA jitter so an
            // exact integer ratio (e.g. 120/30) doesn't floor to one slot short. Never below native.
            int slots = (int) Math.floor((double) game / (double) disp + 1e-3);
            return Math.max(0, Math.min(maxInterps, slots - 1));
        }
        // Non-blocking: post at the target rate so an adaptive-refresh panel ramps up to it.
        double gameHz = 1.0e9 / (double) game;
        int interps = (int) Math.round(fgTargetHz() / gameHz) - 1;
        return Math.max(0, Math.min(maxInterps, interps));
    }

    // Presents per pump tick: enough to sustain the target rate from the current refresh (1 under FIFO).
    private int fgComputePerTick() {
        if (fgActivePresentMode == PRESENT_MODE_FIFO) return 1;
        long disp = fgDisplayPeriodNs;
        double target = fgTargetHz();
        if (disp <= 0L || target <= 0.0) return 1;
        double panelHz = 1.0e9 / (double) disp;
        int n = (int) Math.round(target / Math.max(1.0, panelHz));
        return Math.max(1, Math.min(n, 8));
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
        if (nativeHandle == 0) {
            nativeHandle = nativeCreate(shouldEnableValidationLayers(),
                    graphicsDriverName, xServerView.getContext().getApplicationContext());
            if (nativeHandle == 0) {
                Log.e(TAG, "nativeCreate failed");
                return;
            }
            Texture.setRendererHandle(nativeHandle);
            // Apply the cached present-mode request now that the native renderer exists.
            // No-op if the requested mode equals the native default (FIFO).
            if (requestedPresentMode != PRESENT_MODE_FIFO) {
                nativeSetPresentMode(nativeHandle, requestedPresentMode);
            }
            if (frameGenEnabled) {
                nativeSetFrameGeneration(nativeHandle, true);
                nativeSetPresentMode(nativeHandle, PRESENT_MODE_MAILBOX);  // over-post hold + native pacer
                fgActivePresentMode = nativeGetActivePresentMode(nativeHandle);
                pushFrameGenParams();
                fgPendingReal = false;
                fgPendingInterps = 0;
                fgInterpTotal = 0;
                fgEngineFrames = 0;
                fgNewScene.set(true);
                scheduleFgPump();
            }
            destroyed.set(false);
            xServer.windowManager.addOnWindowModificationListener(this);
            xServer.pointer.addOnPointerMotionListener(this);
        }
        nativeSurfaceCreated(nativeHandle, surface);
    }

    private boolean shouldEnableValidationLayers() {
        Context context = xServerView.getContext();
        return BuildConfig.DEBUG
                && PreferenceManager.getDefaultSharedPreferences(context)
                        .getBoolean(PREF_VULKAN_VALIDATION_LAYERS, false);
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
        if (nativeHandle != 0) nativeSurfaceDestroyed(nativeHandle);
    }

    @Override
    public void onSurfaceCreated() {
        // Surface is already attached in attachSurface(). Nothing else to do here.
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
        // Compute scene transform / viewport / scissor (mirrors GLRenderer.drawFrame logic).
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

        // Viewport
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

        // Scissor (only in non-magnifier non-fullscreen mode)
        if (useScissor) {
            buf.putInt(OFF_SCISSOR_ENABLED, 1);
            buf.putInt(OFF_SCISSOR,      viewTransformation.viewOffsetX);
            buf.putInt(OFF_SCISSOR + 4,  viewTransformation.viewOffsetY);
            buf.putInt(OFF_SCISSOR + 8,  viewTransformation.viewWidth);
            buf.putInt(OFF_SCISSOR + 12, viewTransformation.viewHeight);
        } else {
            buf.putInt(OFF_SCISSOR_ENABLED, 0);
            // Native side gates on scissor_enabled regardless, but zero the rect for cleanliness.
            buf.putInt(OFF_SCISSOR,      0);
            buf.putInt(OFF_SCISSOR + 4,  0);
            buf.putInt(OFF_SCISSOR + 8,  0);
            buf.putInt(OFF_SCISSOR + 12, 0);
        }

        // XForm
        buf.putFloat(OFF_XFORM,      sceneXform[0]);
        buf.putFloat(OFF_XFORM + 4,  sceneXform[1]);
        buf.putFloat(OFF_XFORM + 8,  sceneXform[2]);
        buf.putFloat(OFF_XFORM + 12, sceneXform[3]);
        buf.putFloat(OFF_XFORM + 16, sceneXform[4]);
        buf.putFloat(OFF_XFORM + 20, sceneXform[5]);

        viewportNeedsUpdate = false;

        // Collect renderable windows (matches GLRenderer.renderWindows occlusion skipping).
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

        // Effects snapshot
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
        // nativeSetFpsLimit is a native no-op (pacing is done elsewhere); not called per frame.
        if (frameGenEnabled) {
            // FG: render the composited scene into the history ring without presenting; the
            // interpolated + held-real presents are issued by fgEmitOne() at display cadence.
            nativeRenderHold(nativeHandle);
        } else {
            nativeRenderFrame(nativeHandle);
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
        if (frameGenEnabled) fgSceneDirty.set(true);
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
        // DRI3_BUFFER fires at pixmap allocation, not a visible change; the real present already wakes us. Skip it.
        if (source == WindowManager.FrameSource.DRI3_BUFFER) return;
        if (frameGenEnabled) {
            // This is an actual game-window frame (X11 Present / PutImage / MIT-SHM) — the only
            // signal that drives FG's hold+interpolate cadence. Cursor/Controls go through the
            // generic requestRenderCoalesced path and deliberately do not get counted here.
            long now = System.nanoTime();
            if (fgLastGameNs != 0L) {
                long d = now - fgLastGameNs;
                if (d > 0L && d < 500_000_000L) {
                    fgGamePeriodNs = fgGamePeriodNs == 0L ? d : fgGamePeriodNs + (d - fgGamePeriodNs) / 8L;
                }
            }
            fgPrevGameNs = fgLastGameNs;
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

    // ----- Public API (matches the previous GLRenderer) ---------------------

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

    // Cached so callers can set a mode before the native renderer exists. Applied during
    // attachSurface() right after nativeCreate. Updates after init forward straight to the
    // native side and trigger a swapchain rebuild.
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

    public void setUnviewableWMClasses(String... names) {
        this.unviewableWMClasses = names;
    }

    public void enforceFpsLimit() {
        // FPS limiting is now performed in native (after queue submit/present), so this
        // method is a no-op kept for source compatibility with any external callers.
    }

    // ---- JNI ---------------------------------------------------------------

    private static native long nativeCreate(boolean enableValidationLayers,
                                            String driverName,
                                            android.content.Context context);
    private static native void nativeDestroy(long handle);
    private static native void nativeSurfaceCreated(long handle, Surface surface);
    private static native void nativeSurfaceChanged(long handle, int w, int h);
    private static native void nativeSurfaceDestroyed(long handle);
    private static native boolean nativeRenderFrame(long handle);
    private static native void nativeSetScene(long handle, ByteBuffer sceneBuf);
    private static native void nativeSetFpsLimit(long handle, int fps);
    private static native void nativeSetPresentMode(long handle, int mode);

    // ---- Frame generation ----
    private static native void nativeSetFrameGeneration(long handle, boolean enabled);
    private static native boolean nativeFrameGenerationSupported(long handle);
    private static native long nativeGetDisplayFrameCount(long handle);
    private static native boolean nativeRenderHold(long handle);
    private static native boolean nativeRenderInterp(long handle, float phase, long prevNs, long currNs);
    private static native boolean nativePresentLast(long handle);
    private static native void nativeSetFrameGenParams(long handle, float occLo, float occHi, int minStep);
    private static native int nativeGetActivePresentMode(long handle);
    private static native void nativeSetVsyncTiming(long handle, long periodNs, long vsyncNs);
}
