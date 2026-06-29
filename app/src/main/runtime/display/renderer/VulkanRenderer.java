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

    // === DIRECT COMPOSITION (zero-copy AHB → SurfaceControl) ===
    //
    // When non-null and the current frame qualifies as a fullscreen
    // direct-scanout candidate, the AHardwareBuffer backing that drawable is
    // pushed to this layer in addition to the VulkanRenderer composition. The
    // SC layer at z=1 covers the SurfaceView's primary layer at z=0, so HWC
    // can promote it to a DPU overlay plane — zero GPU compositing cost, zero
    // buffer copy. This is the true zero-copy path.
    //
    // Set/cleared by the activity from the UI thread via
    // {@link #setDirectCompositionTarget}; read here on the render thread,
    // hence volatile. The volatile only suppresses NEW frames from entering
    // the SC push after the UI thread writes null — in-flight frames are
    // protected by DirectCompositionLayer's own synchronized methods.
    private volatile com.winlator.cmod.runtime.display.composition.DirectCompositionLayer
            directCompositionTarget;

    // Last (ahbPtr, dstW, dstH) pushed to directCompositionTarget. Per-frame
    // pushBuffer calls allocate a SurfaceFlinger transaction, which is wasted
    // work when nothing changed. DRI3 allocates a fresh GPUImage per Present
    // cycle, so AHB-pointer identity is a sufficient "dirty" check.
    // Render-thread-only — no synchronization needed.
    private long dcLastPushedAhb = 0L;
    private int dcLastPushedW = 0;
    private int dcLastPushedH = 0;

    // Consecutive pushBuffer == false returns. After enough failures the
    // renderer detaches itself from the SC layer to avoid wasting JNI calls
    // every frame on a permanent failure. Render-thread-only.
    private int dcConsecutiveFailures = 0;
    private static final int DC_FAIL_LIMIT = 8;

    // True when the most recent frame successfully pushed an AHB to the SC,
    // so the SC layer is currently visible. Used to detect transitions to
    // the windowed/multi-drawable case so we can hide the SC cleanly.
    private volatile boolean dcLayerActive = false;

    // Last skip reason logged for the DC candidate (diagnostic throttling —
    // only log when the reason CHANGES, to avoid per-frame spam). Values:
    //   "no-texture", "texture-not-gpuimage(Texture)", "gpuimage-ahb-null",
    //   "ok". Empty string = nothing logged yet.
    private String dcLastSkipReason = "";

    // Last candidate-state logged (diagnostic throttling for the
    // directCandidate null/present transition). Empty = nothing logged yet.
    private String dcLastCandidateState = "";

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
        if (renderRequested.compareAndSet(false, true)) {
            mainHandler.post(() ->
                    Choreographer.getInstance().postFrameCallback(frameTimeNanos -> {
                        renderRequested.set(false);
                        xServerView.requestRender();
                    }));
        }
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
            if (requestedScaleFilter != SCALE_FILTER_OFF) {
                nativeSetScaleFilter(nativeHandle, requestedScaleFilter);
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
        buildAndSubmitFrame();
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
        // Track the direct-scanout candidate Drawable (the largest window
        // matching screen size) so we can push its AHB to the SurfaceControl
        // after the VulkanRenderer composition. Render-thread-only.
        Drawable directCandidate = null;

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
                    // Track the Drawable for the Direct Composition push.
                    directCandidate = drawable;
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
        nativeRenderFrame(nativeHandle);

        // === DIRECT COMPOSITION per-frame hook ===
        // After the VulkanRenderer composition, push the fullscreen candidate's
        // AHardwareBuffer to the SurfaceControl layer (if attached and the
        // candidate qualifies). The SC layer at z=1 covers the VulkanRenderer's
        // output at z=0; HWC promotes it to a DPU overlay plane — zero GPU
        // compositing cost, zero buffer copy. If no candidate qualifies, hide
        // the SC layer (transition back to VulkanRenderer composition).
        if (directCompositionTarget != null) {
            // Throttled diagnostic: log when the candidate-null state changes,
            // so we can see whether any window ever qualifies.
            String candidateState = (directCandidate != null) ? "present" : "null";
            if (!candidateState.equals(dcLastCandidateState)) {
                dcLastCandidateState = candidateState;
                if (directCandidate == null) {
                    com.winlator.cmod.runtime.display.composition.SurfaceCompositor.logEvent(
                            "DC: no fullscreen direct-scanout candidate this frame "
                                    + "(winCount=" + winCount + " sourceW=" + sourceW
                                    + " sourceH=" + sourceH + " screenW="
                                    + xServer.screenInfo.width + " screenH="
                                    + xServer.screenInfo.height + ")");
                } else {
                    com.winlator.cmod.runtime.display.composition.SurfaceCompositor.logEvent(
                            "DC: fullscreen direct-scanout candidate detected "
                                    + "(drawable=" + directCandidate.width + "x"
                                    + directCandidate.height + " sourceW=" + sourceW
                                    + " sourceH=" + sourceH + ")");
                }
            }
            if (!maybePushDirectComposition(directCandidate)) {
                maybeHideDirectComposition();
            }
        }
    }

    /**
     * Direct Composition hot path: extract the AHardwareBuffer for the
     * candidate's scanoutSource and hand it to the per-activity
     * DirectCompositionLayer.
     *
     * <p>Holds {@code candidate.renderLock} for the lookup so we can't race
     * against DRI3 replacing the texture or GPUImage.destroy() releasing the
     * underlying AHB mid-read. The JNI pushBuffer runs INSIDE the lock too —
     * short call, SurfaceFlinger takes its own ref on the AHB inside
     * ASurfaceTransaction_setBuffer apply, so the buffer is safe to release
     * on the X-server thread the moment we exit the lock.
     *
     * <p>Per-frame waste suppression: caches the last successfully-pushed
     * (ahbPtr, dstW, dstH) and skips the JNI call when nothing has changed.
     * DRI3 allocates a fresh GPUImage each Present, so AHB-pointer identity is
     * a sufficient "buffer changed" signal.
     *
     * <p>Failure counter: after {@code DC_FAIL_LIMIT} consecutive false
     * returns from pushBuffer, nulls directCompositionTarget so subsequent
     * frames don't keep paying the JNI cost for a permanent failure.
     *
     * @return true if a fresh AHB was pushed OR the cache hit (SC is still
     *         showing a valid prior frame). false = no qualifying candidate
     *         (caller should hide the SC layer).
     */
    private boolean maybePushDirectComposition(Drawable directCandidate) {
        final com.winlator.cmod.runtime.display.composition.DirectCompositionLayer dcTarget =
                directCompositionTarget;
        if (dcTarget == null) return false;
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return false;

        // Force fallback to VulkanRenderer composition when an in-process
        // overlay needs to be visible on top of the game frame. The SC layer
        // at z=1 covers the VulkanRenderer's output at z=0, so anything we
        // composite via VulkanRenderer (magnifier UI, debug HUDs, cursor)
        // would otherwise be invisible.
        if (magnifierUIActive) {
            return false;
        }
        // No fullscreen candidate — fall back to VulkanRenderer.
        if (directCandidate == null) {
            return false;
        }

        final Drawable content = directCandidate;
        synchronized (content.renderLock) {
            Drawable scanoutSource = content.getScanoutSource();
            if (scanoutSource == null) {
                // No scanout source — the drawable itself is the source.
                scanoutSource = content;
            }
            Texture tex = scanoutSource.getTexture();

            // === DIAGNOSTIC: log why we might skip this candidate ===
            // Throttle: only log when the situation CHANGES, to avoid per-frame
            // spam. We use a simple "last logged state" tracker.
            String currentState;
            if (tex == null) {
                currentState = "no-texture";
            } else if (!(tex instanceof GPUImage)) {
                currentState = "texture-not-gpuimage(" + tex.getClass().getSimpleName() + ")";
            } else if (((GPUImage) tex).getHardwareBufferPtr() == 0L) {
                currentState = "gpuimage-ahb-null";
            } else {
                currentState = "ok";
            }
            if (!currentState.equals(dcLastSkipReason)) {
                dcLastSkipReason = currentState;
                if (!currentState.equals("ok")) {
                    com.winlator.cmod.runtime.display.composition.SurfaceCompositor.logEvent(
                            "DC skip: candidate " + currentState
                                    + " (drawable=" + content.width + "x" + content.height
                                    + " scanoutSource=" + (content.getScanoutSource() != null
                                            ? (scanoutSource.width + "x" + scanoutSource.height)
                                            : "self")
                                    + " directScanout=" + content.isDirectScanout() + ")");
                }
            }

            if (!(tex instanceof GPUImage)) {
                drainFenceFd(scanoutSource);
                return false;
            }
            long ahbPtr = ((GPUImage) tex).getHardwareBufferPtr();
            if (ahbPtr == 0L) {
                drainFenceFd(scanoutSource);
                return false;
            }

            // Skip JNI when nothing has changed since the last push.
            // SurfaceFlinger is still showing the layer; no point queueing a
            // no-op transaction — this is the primary CPU/battery optimization.
            if (ahbPtr == dcLastPushedAhb
                    && surfaceWidth == dcLastPushedW
                    && surfaceHeight == dcLastPushedH) {
                return true;
            }

            int fenceFd = scanoutSource.takeAcquireFenceFd();
            boolean ok = dcTarget.pushBuffer(ahbPtr, 0, 0,
                    surfaceWidth, surfaceHeight, fenceFd, /*opaque=*/true);
            if (ok) {
                dcLastPushedAhb = ahbPtr;
                dcLastPushedW = surfaceWidth;
                dcLastPushedH = surfaceHeight;
                dcConsecutiveFailures = 0;
                if (!dcLayerActive) {
                    dcLayerActive = true;
                    com.winlator.cmod.runtime.display.composition.SurfaceCompositor.logEvent(
                            "DC ACTIVE — first frame pushed to SurfaceControl (ahb=0x"
                                    + Long.toHexString(ahbPtr) + " " + surfaceWidth + "x" + surfaceHeight
                                    + " drawable=" + content.width + "x" + content.height + ")");
                    notifyDirectCompositionStateListener();
                }
                return true;
            } else {
                dcConsecutiveFailures++;
                com.winlator.cmod.runtime.display.composition.SurfaceCompositor.logEvent(
                        "DC pushBuffer FAILED (#" + dcConsecutiveFailures + ") — ahb=0x"
                                + Long.toHexString(ahbPtr));
                if (dcConsecutiveFailures >= DC_FAIL_LIMIT) {
                    Log.w(TAG, "DirectComposition push failed " + dcConsecutiveFailures
                            + " frames in a row — disabling target for this session");
                    com.winlator.cmod.runtime.display.composition.SurfaceCompositor.logEvent(
                            "DC DISABLED — " + DC_FAIL_LIMIT + " consecutive failures, self-detaching");
                    // Hide the SC layer BEFORE nulling the field — once the
                    // field is null, maybeHideDirectComposition has nothing to
                    // call hide() on, and SurfaceFlinger would keep showing
                    // the last successfully-pushed buffer over the
                    // VulkanRenderer output forever.
                    dcTarget.hide();
                    if (dcLayerActive) {
                        dcLayerActive = false;
                        notifyDirectCompositionStateListener();
                    }
                    directCompositionTarget = null;
                    dcLastPushedAhb = 0L;
                    dcLastPushedW = 0;
                    dcLastPushedH = 0;
                    dcConsecutiveFailures = 0;
                }
                return false;
            }
        }
    }

    /**
     * Hide the Direct Composition layer when the current frame doesn't
     * qualify for the SC fast path (windowed app, multi-drawable, cursor
     * visible over a non-fullscreen scene, magnifier overlay, etc.).
     * Idempotent and cheap after the first call: tracks dcLayerActive so we
     * only queue a hide-transaction once per direct→fallback transition.
     */
    // Drain unconsumed fence FD to prevent FD leak when DC can't handle a frame.
    private void drainFenceFd(Drawable scanoutSource) {
        if (scanoutSource == null) return;
        int fd = scanoutSource.takeAcquireFenceFd();
        if (fd >= 0) {
            try { android.os.ParcelFileDescriptor.adoptFd(fd).close(); }
            catch (java.io.IOException ignored) {}
        }
    }

    private void maybeHideDirectComposition() {
        if (!dcLayerActive) return;
        com.winlator.cmod.runtime.display.composition.DirectCompositionLayer dcTarget =
                directCompositionTarget;
        if (dcTarget != null) {
            dcTarget.hide();
        }
        dcLayerActive = false;
        notifyDirectCompositionStateListener();
        // Invalidate the cache so the next pushBuffer re-shows with a fresh
        // setBuffer + setVisibility(SHOW) transaction, even if the same AHB
        // pointer happens to be active.
        dcLastPushedAhb = 0L;
        dcLastPushedW = 0;
        dcLastPushedH = 0;
    }

    /**
     * Hand the renderer the per-activity Direct Composition layer (or null to
     * detach). Safe to call from the UI thread; the render thread reads the
     * field volatile-ly each frame inside buildAndSubmitFrame().
     */
    public void setDirectCompositionTarget(
            com.winlator.cmod.runtime.display.composition.DirectCompositionLayer layer) {
        // Hide old layer before swapping to prevent stale frame on screen.
        if (dcLayerActive && directCompositionTarget != null) {
            directCompositionTarget.hide();
        }
        this.directCompositionTarget = layer;
        dcLastPushedAhb = 0L;
        dcLastPushedW = 0;
        dcLastPushedH = 0;
        dcConsecutiveFailures = 0;
        dcLayerActive = false;
        dcLastSkipReason = "";
        notifyDirectCompositionStateListener();
    }

    // === DC STATE LISTENER (for HUD indicator) ===
    // Called when the DC layer goes active/inactive. The activity registers a
    // listener to update the FrameRating HUD (" + DC" suffix on the renderer
    // label). Null by default; set by XServerDisplayActivity.
    public interface DirectCompositionStateListener {
        void onDirectCompositionStateChanged(boolean active);
    }
    private volatile DirectCompositionStateListener dcStateListener;
    public void setDirectCompositionStateListener(DirectCompositionStateListener listener) {
        this.dcStateListener = listener;
    }
    private void notifyDirectCompositionStateListener() {
        DirectCompositionStateListener l = dcStateListener;
        if (l != null) l.onDirectCompositionStateChanged(dcLayerActive);
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
            // Graphics preset change: flush DC state, invalidate cache, force re-evaluation.
            if (dcLayerActive && directCompositionTarget != null) {
                directCompositionTarget.hide();
                dcLayerActive = false;
                notifyDirectCompositionStateListener();
            }
            dcLastPushedAhb = 0L;
            dcLastPushedW = 0;
            dcLastPushedH = 0;
            dcLastSkipReason = "";
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
    private static native void nativeSetScaleFilter(long handle, int mode);
}
