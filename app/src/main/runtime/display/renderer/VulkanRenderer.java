package com.winlator.cmod.runtime.display.renderer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.Surface;
import com.winlator.cmod.R;
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
import java.util.ArrayList;

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

    static {
        System.loadLibrary("winlator");
    }

    public final XServerSurfaceView xServerView;
    private final XServer xServer;

    private long nativeHandle = 0;
    private boolean supportProbed = false;

    private final EffectComposer effectComposer;
    public final ViewTransformation viewTransformation = new ViewTransformation();

    private final Drawable rootCursorDrawable;
    private final ArrayList<RenderableWindow> renderableWindows = new ArrayList<>();
    private boolean fullscreen = false;
    public boolean viewportNeedsUpdate = true;
    private boolean cursorVisible = true;
    private boolean screenOffsetYRelativeToCursor = false;
    private String[] unviewableWMClasses = null;
    private float magnifierZoom = 1.0f;
    private boolean magnifierEnabled = true;
    public int surfaceWidth;
    public int surfaceHeight;
    private boolean cpuSaverMode = false;

    private static final int MAX_FPS_LIMIT = 1000;
    private volatile int currentFpsLimit = 0;

    // Must mirror VK_MAX_RENDERABLE_WINDOWS / VK_MAX_EFFECTS in vk_state.h.
    private static final int MAX_WINDOWS = 64;
    private static final int MAX_EFFECTS = 8;

    // Reusable scratch — sized once, refilled per frame, passed across JNI to avoid GC churn.
    private final float[] sceneXform = XForm.getInstance();
    private final int[] viewportArr = new int[4];
    private final int[] scissorArr = new int[4];
    private final long[] winHandlesScratch = new long[MAX_WINDOWS];
    private final int[] winGeomScratch = new int[MAX_WINDOWS * 4];
    private final int[] cursorGeomScratch = new int[4];
    private final int[] effectTypesScratch = new int[MAX_EFFECTS];
    private final float[] effectParamsScratch = new float[MAX_EFFECTS * 4];

    public VulkanRenderer(XServerSurfaceView view, XServer xServer) {
        this.xServerView = view;
        this.xServer = xServer;
        this.effectComposer = new EffectComposer(this);
        this.rootCursorDrawable = createRootCursorDrawable();

        xServer.windowManager.addOnWindowModificationListener(this);
        xServer.pointer.addOnPointerMotionListener(this);
    }

    private Drawable createRootCursorDrawable() {
        Context context = xServerView.getContext();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.cursor, options);
        return Drawable.fromBitmap(bitmap);
    }

    // ----- Surface lifecycle (called from XServerSurfaceView) ----------------

    public void attachSurface(Surface surface) {
        if (nativeHandle == 0) {
            nativeHandle = nativeCreate();
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
        }
        nativeSurfaceCreated(nativeHandle, surface);
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
        if (nativeHandle != 0) {
            nativeDestroy(nativeHandle);
            nativeHandle = 0;
            Texture.setRendererHandle(0);
        }
    }

    @Override
    public void onDrawFrame() {
        if (nativeHandle == 0) return;
        buildAndSubmitFrame();
    }

    // ----- Scene assembly ----------------------------------------------------

    private void buildAndSubmitFrame() {
        // Compute scene transform / viewport / scissor (mirrors GLRenderer.drawFrame logic).
        boolean useScissor = false;

        if (magnifierEnabled) {
            float pointerX = 0;
            float pointerY = 0;
            float zoom = !screenOffsetYRelativeToCursor ? magnifierZoom : 1.0f;
            if (zoom != 1.0f) {
                pointerX = Mathf.clamp(
                        xServer.pointer.getX() * zoom - xServer.screenInfo.width * 0.5f,
                        0, xServer.screenInfo.width * Math.abs(1.0f - zoom));
            }
            if (screenOffsetYRelativeToCursor || zoom != 1.0f) {
                float scaleY = zoom != 1.0f ? Math.abs(1.0f - zoom) : 0.5f;
                float offsetY = xServer.screenInfo.height * (screenOffsetYRelativeToCursor ? 0.25f : 0.5f);
                pointerY = Mathf.clamp(
                        xServer.pointer.getY() * zoom - offsetY,
                        0, xServer.screenInfo.height * scaleY);
            }
            XForm.makeTransform(sceneXform, -pointerX, -pointerY, zoom, zoom, 0);
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

        // Viewport
        if (fullscreen) {
            viewportArr[0] = 0;
            viewportArr[1] = 0;
            viewportArr[2] = surfaceWidth;
            viewportArr[3] = surfaceHeight;
        } else {
            viewportArr[0] = viewTransformation.viewOffsetX;
            viewportArr[1] = viewTransformation.viewOffsetY;
            viewportArr[2] = viewTransformation.viewWidth;
            viewportArr[3] = viewTransformation.viewHeight;
        }

        // Scissor (only in non-magnifier non-fullscreen mode)
        int[] scissor = null;
        if (useScissor) {
            scissorArr[0] = viewTransformation.viewOffsetX;
            scissorArr[1] = viewTransformation.viewOffsetY;
            scissorArr[2] = viewTransformation.viewWidth;
            scissorArr[3] = viewTransformation.viewHeight;
            scissor = scissorArr;
        }

        viewportNeedsUpdate = false;

        // Collect renderable windows (matches GLRenderer.renderWindows occlusion skipping).
        int winCount = 0;
        long cursorHandle = 0;
        boolean cursorOnscreen = false;

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
                Drawable textureSrc = drawable.getScanoutSource() != null ? drawable.getScanoutSource() : drawable;
                Texture tex;
                synchronized (drawable.renderLock) {
                    tex = textureSrc.getTexture();
                    if (tex != null) {
                        tex.updateFromDrawable(textureSrc);
                    }
                }
                if (tex == null || !tex.isAllocated()) continue;
                winHandlesScratch[winCount] = tex.getNativeHandle();
                winGeomScratch[winCount * 4 + 0] = rw.rootX;
                winGeomScratch[winCount * 4 + 1] = rw.rootY;
                winGeomScratch[winCount * 4 + 2] = drawable.width;
                winGeomScratch[winCount * 4 + 3] = drawable.height;
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
                        if (tex != null) tex.updateFromDrawable(cursorDrawable);
                    }
                    if (tex != null && tex.isAllocated()) {
                        cursorHandle = tex.getNativeHandle();
                        cursorGeomScratch[0] = x - hotX;
                        cursorGeomScratch[1] = y - hotY;
                        cursorGeomScratch[2] = cursorDrawable.width;
                        cursorGeomScratch[3] = cursorDrawable.height;
                        cursorOnscreen = true;
                    }
                }
            }
        }

        // Effects snapshot
        Effect[] active = effectComposer.snapshot();
        int effectCount = Math.min(active.length, MAX_EFFECTS);
        for (int i = 0; i < effectCount; i++) {
            effectTypesScratch[i] = active[i].getNativeType();
            float[] p = active[i].getParams();
            effectParamsScratch[i * 4 + 0] = p.length > 0 ? p[0] : 0f;
            effectParamsScratch[i * 4 + 1] = p.length > 1 ? p[1] : 0f;
            effectParamsScratch[i * 4 + 2] = p.length > 2 ? p[2] : 0f;
            effectParamsScratch[i * 4 + 3] = p.length > 3 ? p[3] : 0f;
        }

        nativeSetScene(nativeHandle,
                winHandlesScratch, winGeomScratch, winCount,
                cursorHandle, cursorGeomScratch, cursorOnscreen,
                sceneXform, viewportArr, scissor,
                xServer.screenInfo.width, xServer.screenInfo.height,
                effectTypesScratch, effectParamsScratch, effectCount);
        nativeSetFpsLimit(nativeHandle, currentFpsLimit);
        nativeRenderFrame(nativeHandle);
    }

    // ----- WindowManager / Pointer listeners --------------------------------

    @Override
    public void onMapWindow(Window window) {
        xServerView.queueEvent(this::updateScene);
        xServerView.requestRender();
    }

    @Override
    public void onUnmapWindow(Window window) {
        xServerView.queueEvent(this::updateScene);
        xServerView.requestRender();
    }

    @Override
    public void onChangeWindowZOrder(Window window) {
        xServerView.queueEvent(this::updateScene);
        xServerView.requestRender();
    }

    @Override
    public void onUpdateWindowContent(Window window) {
        xServerView.requestRender();
    }

    @Override
    public void onUpdateWindowGeometry(final Window window, boolean resized) {
        if (resized) {
            xServerView.queueEvent(this::updateScene);
        } else {
            xServerView.queueEvent(() -> updateWindowPosition(window));
        }
        xServerView.requestRender();
    }

    @Override
    public void onUpdateWindowAttributes(Window window, Bitmask mask) {
        if (mask.isSet(WindowAttributes.FLAG_CURSOR)) xServerView.requestRender();
    }

    @Override
    public void onPointerMove(short x, short y) {
        xServerView.requestRender();
    }

    @Override
    public void onFramePresented(Window window) {
        xServerView.requestRender();
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

    public void toggleFullscreen() {
        fullscreen = !fullscreen;
        viewportNeedsUpdate = true;
        xServerView.requestRender();
    }

    public boolean isFullscreen() { return fullscreen; }

    public void setCursorVisible(boolean v) {
        if (this.cursorVisible == v) return;
        this.cursorVisible = v;
        xServerView.requestRender();
    }

    public boolean isCursorVisible() { return cursorVisible; }

    public boolean isScreenOffsetYRelativeToCursor() { return screenOffsetYRelativeToCursor; }

    public void setScreenOffsetYRelativeToCursor(boolean v) {
        this.screenOffsetYRelativeToCursor = v;
        xServerView.requestRender();
    }

    public float getMagnifierZoom() { return magnifierZoom; }

    public void setMagnifierZoom(float v) {
        this.magnifierZoom = v;
        xServerView.requestRender();
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
            xServerView.requestRender();
        }
    }

    public boolean isNativeMode() { return cpuSaverMode; }

    public void setFpsLimit(int fps) {
        currentFpsLimit = Math.max(0, Math.min(fps, MAX_FPS_LIMIT));
    }

    public int getFpsLimit() { return currentFpsLimit; }

    // Compositor present-mode constants must mirror the switch in nativeSetPresentMode.
    public static final int PRESENT_MODE_FIFO         = 0;
    public static final int PRESENT_MODE_MAILBOX      = 1;
    public static final int PRESENT_MODE_IMMEDIATE    = 2;
    public static final int PRESENT_MODE_FIFO_RELAXED = 3;

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
            case "mailbox":      return PRESENT_MODE_MAILBOX;
            case "immediate":    return PRESENT_MODE_IMMEDIATE;
            case "fifo_relaxed": return PRESENT_MODE_FIFO_RELAXED;
            default:             return PRESENT_MODE_FIFO;
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

    private static native long nativeCreate();
    private static native void nativeDestroy(long handle);
    private static native void nativeSurfaceCreated(long handle, Surface surface);
    private static native void nativeSurfaceChanged(long handle, int w, int h);
    private static native void nativeSurfaceDestroyed(long handle);
    private static native boolean nativeRenderFrame(long handle);
    private static native void nativeSetScene(long handle,
                                              long[] windowTexHandles, int[] windowGeom, int windowCount,
                                              long cursorTexHandle, int[] cursorGeom, boolean cursorVisible,
                                              float[] xform, int[] viewport, int[] scissor,
                                              int screenW, int screenH,
                                              int[] effectTypes, float[] effectParams, int effectCount);
    private static native void nativeSetFpsLimit(long handle, int fps);
    private static native void nativeSetPresentMode(long handle, int mode);
}
