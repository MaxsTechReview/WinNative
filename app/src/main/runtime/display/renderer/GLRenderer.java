package com.winlator.cmod.runtime.display.renderer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import com.winlator.cmod.R;
import com.winlator.cmod.runtime.display.renderer.material.CursorMaterial;
import com.winlator.cmod.runtime.display.renderer.material.ShaderMaterial;
import com.winlator.cmod.runtime.display.renderer.material.WindowMaterial;
import com.winlator.cmod.runtime.display.ui.XServerView;
import com.winlator.cmod.runtime.display.xserver.Bitmask;
import com.winlator.cmod.runtime.display.xserver.Cursor;
import com.winlator.cmod.runtime.display.xserver.Drawable;
import com.winlator.cmod.runtime.display.xserver.Pointer;
import com.winlator.cmod.runtime.display.xserver.Window;
import com.winlator.cmod.runtime.display.xserver.WindowAttributes;
import com.winlator.cmod.runtime.display.xserver.WindowManager;
import com.winlator.cmod.runtime.display.xserver.XLock;
import com.winlator.cmod.runtime.display.xserver.XServer;
import com.winlator.cmod.shared.android.AppUtils;
import com.winlator.cmod.shared.math.Mathf;
import com.winlator.cmod.shared.math.XForm;
import java.util.ArrayList;
import java.util.concurrent.locks.LockSupport;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLRenderer
    implements GLSurfaceView.Renderer,
        WindowManager.OnWindowModificationListener,
        Pointer.OnPointerMotionListener {
  public final XServerView xServerView;
  private final XServer xServer;
  public final VertexAttribute quadVertices = new VertexAttribute("position", 2);
  private final float[] tmpXForm1 = XForm.getInstance();
  private final float[] tmpXForm2 = XForm.getInstance();
  private final CursorMaterial cursorMaterial = new CursorMaterial();
  private final WindowMaterial windowMaterial = new WindowMaterial();
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
  private boolean magnifierUIActive = false;
  private float magnifierPanX = 0f;
  private float magnifierPanY = 0f;
  private boolean magnifierPanInitialized = false;
  private static final float MAGNIFIER_DEADZONE_FRACTION = 0.6f;
  public int surfaceWidth;
  public int surfaceHeight;
  public boolean swapRB = false;
  private boolean cpuSaverMode = false;
  private static final int MAX_FPS_LIMIT = 1000;
  public static final long FPS_LIMIT_SPIN_THRESHOLD_NS = 2_000_000L;
  private volatile int currentFpsLimit = 0;
  private boolean wasDirectMode = false;
  private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
  private final java.util.concurrent.atomic.AtomicBoolean renderRequested = new java.util.concurrent.atomic.AtomicBoolean(false);

  private final EffectComposer effectComposer;

  /**
   * Phase 2.3: when non-null and the current frame qualifies as a fullscreen
   * direct-scanout candidate, the AHardwareBuffer backing that drawable is
   * pushed to this layer in addition to (Phase 2.3) or instead of (Phase
   * 2.5+) the GL composition. Set/cleared by the activity from the UI thread
   * via {@link #setDirectCompositionTarget(com.winlator.cmod.runtime.display.composition.DirectCompositionLayer)};
   * read here on the GLThread, hence volatile.
   *
   * <p><b>Cross-thread safety</b> doesn't come from the volatile alone — the
   * volatile only suppresses NEW frames from entering the SC push after the
   * UI thread writes null. In-flight frames already past that read are
   * protected by the layer's own {@code synchronized} methods: when the UI
   * thread's {@code release()} executes, it serializes on the same monitor
   * as {@code pushBuffer}, then zeroes the native pointer. A
   * {@code pushBuffer} that won the race sees the live SC; a later one sees
   * {@code nativeSc == 0} and short-circuits in JNI.
   */
  private volatile com.winlator.cmod.runtime.display.composition.DirectCompositionLayer
          directCompositionTarget;

  /**
   * Last AHardwareBuffer pointer + geometry pushed to {@code directCompositionTarget}.
   * Per-frame `pushBuffer` calls allocate a SurfaceFlinger transaction, which
   * is wasted work when neither the buffer pointer nor the destination geometry
   * has changed since the previous frame. DRI3 in this project allocates a
   * fresh GPUImage per Present cycle, so AHB-pointer identity is a sufficient
   * "dirty" check; if a future codepath recycles AHBs in place this cache
   * misses harmlessly (correctness preserved, just reverts to per-frame push).
   * GLThread-only — no synchronization needed.
   */
  private long dcLastPushedAhb = 0L;
  private int dcLastPushedW = 0;
  private int dcLastPushedH = 0;

  /** Consecutive {@code pushBuffer == false} returns. After enough failures
   *  the renderer detaches itself from the SC layer to avoid wasting JNI
   *  calls every frame on a permanent failure (e.g. SC was reparented away).
   *  GLThread-only. */
  private int dcConsecutiveFailures = 0;
  private static final int DC_FAIL_LIMIT = 8;

  /** Phase 2.5 — true when the most recent frame successfully pushed an AHB
   *  to the SurfaceControl, so the SC layer is currently visible and showing
   *  game content. Used to (a) skip the GL render of the direct candidate
   *  for the perf win, (b) detect transitions to the windowed/multi-drawable
   *  case so we can hide the SC layer cleanly. GLThread-only. */
  private boolean dcLayerActive = false;

  public GLRenderer(XServerView xServerView, XServer xServer) {
    this.xServerView = xServerView;
    this.xServer = xServer;
    this.effectComposer = new EffectComposer(this);
    rootCursorDrawable = createRootCursorDrawable();

    quadVertices.put(
        new float[] {
          0.0f, 0.0f,
          0.0f, 1.0f,
          1.0f, 0.0f,
          1.0f, 1.0f
        });

    xServer.windowManager.addOnWindowModificationListener(this);
    xServer.pointer.addOnPointerMotionListener(this);
  }

  public void requestRenderCoalesced() {
    if (renderRequested.compareAndSet(false, true)) {
      mainHandler.post(
          () -> {
            android.view.Choreographer.getInstance()
                .postFrameCallback(
                    frameTimeNanos -> {
                      renderRequested.set(false);
                      xServerView.requestRender();
                    });
          });
    }
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    if (xServer.isDri3Enabled()) {
      GPUImage.checkIsSupported();
    }

    GLES20.glFrontFace(GLES20.GL_CCW);
    GLES20.glDisable(GLES20.GL_CULL_FACE);

    GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    GLES20.glDepthMask(false);

    GLES20.glEnable(GLES20.GL_BLEND);
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
  }

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    surfaceWidth = width;
    surfaceHeight = height;
    viewTransformation.update(width, height, xServer.screenInfo.width, xServer.screenInfo.height);
    viewportNeedsUpdate = true;
  }

  @Override
  public void onDrawFrame(GL10 gl) {
    if (effectComposer.hasEffects()) {
      effectComposer.render();
    } else if (cpuSaverMode) {
      drawFrameOptimized();
    } else {
      drawFrame();
    }
  }

  public EffectComposer getEffectComposer() {
    return effectComposer;
  }

  public void drawFrame() {
    resetFrameState();

    // Update the viewport if necessary
    if (viewportNeedsUpdate && magnifierEnabled) {
      if (fullscreen) {
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
      } else {
        GLES20.glViewport(
            viewTransformation.viewOffsetX,
            viewTransformation.viewOffsetY,
            viewTransformation.viewWidth,
            viewTransformation.viewHeight);
      }
      viewportNeedsUpdate = false;
    }

    // Clear the screen before drawing
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

    // Apply basic transformations and draw windows
    if (magnifierEnabled) {
      computeMagnifierPan(tmpXForm2);
    } else {
      if (!fullscreen) {
        int pointerY = 0;
        if (screenOffsetYRelativeToCursor) {
          short halfScreenHeight = (short) (xServer.screenInfo.height / 2);
          pointerY =
              Mathf.clamp(xServer.pointer.getY() - halfScreenHeight / 2, 0, halfScreenHeight);
        }

        XForm.makeTransform(
            tmpXForm2,
            viewTransformation.sceneOffsetX,
            viewTransformation.sceneOffsetY - pointerY,
            viewTransformation.sceneScaleX,
            viewTransformation.sceneScaleY,
            0);

        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(
            viewTransformation.viewOffsetX,
            viewTransformation.viewOffsetY,
            viewTransformation.viewWidth,
            viewTransformation.viewHeight);
      } else {
        XForm.identity(tmpXForm2);
      }
    }

    renderWindows();

    // Render cursor if enabled
    if (cursorVisible) {
      GLES20.glEnable(GLES20.GL_BLEND);
      GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
      renderCursor();
    }

    // Disable scissor test if magnifier is disabled and not in fullscreen mode
    if (!magnifierEnabled && !fullscreen) {
      GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }
  }

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
    }
    requestRenderCoalesced();
  }

  @Override
  public void onUpdateWindowAttributes(Window window, Bitmask mask) {
    if (mask.isSet(WindowAttributes.FLAG_CURSOR)) requestRenderCoalesced();
  }

  @Override
  public void onPointerMove(short x, short y) {
    requestRenderCoalesced();
  }

  private void renderDrawable(Drawable drawable, int x, int y, ShaderMaterial material) {
    if (drawable == null) return;
    synchronized (drawable.renderLock) {
      Drawable textureDrawable =
          drawable.getScanoutSource() != null ? drawable.getScanoutSource() : drawable;
      Texture texture = textureDrawable.getTexture();
      if (texture == null) return;
      texture.updateFromDrawable(textureDrawable);
      if (!texture.isAllocated()) return;

      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture.getTextureId());
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

      XForm.set(tmpXForm1, x, y, drawable.width, drawable.height);
      XForm.multiply(tmpXForm1, tmpXForm1, tmpXForm2);

      GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture.getTextureId());
      GLES20.glUniform1i(material.getUniformLocation("texture"), 0);
      GLES20.glUniform1fv(material.getUniformLocation("xform"), tmpXForm1.length, tmpXForm1, 0);
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, quadVertices.count());
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }
  }

  private void renderWindows() {
    windowMaterial.use();
    GLES20.glUniform2f(
        windowMaterial.getUniformLocation("viewSize"),
        xServer.screenInfo.width,
        xServer.screenInfo.height);
    windowMaterial.setUniformInt("swapRB", swapRB ? 1 : 0);
    quadVertices.bind(windowMaterial.programId);

    try (XLock lock = xServer.lock(XServer.Lockable.DRAWABLE_MANAGER)) {
      int startIndex = 0;
      int screenWidth = xServer.screenInfo.width;
      int screenHeight = xServer.screenInfo.height;

      // Skip occluded windows behind a fullscreen one
      for (int i = renderableWindows.size() - 1; i >= 0; i--) {
        RenderableWindow rWin = renderableWindows.get(i);
        if (rWin.content != null
            && rWin.content.width >= screenWidth
            && rWin.content.height >= screenHeight) {
          startIndex = i;
          break;
        }
      }

      for (int i = startIndex; i < renderableWindows.size(); i++) {
        RenderableWindow window = renderableWindows.get(i);
        renderDrawable(window.content, window.rootX, window.rootY, windowMaterial);
      }
    }

    quadVertices.disable();
  }

  private void renderCursor() {
    cursorMaterial.use();
    GLES20.glUniform2f(
        cursorMaterial.getUniformLocation("viewSize"),
        xServer.screenInfo.width,
        xServer.screenInfo.height);
    quadVertices.bind(cursorMaterial.programId);

    try (XLock lock = xServer.lock(XServer.Lockable.DRAWABLE_MANAGER)) {
      Window pointWindow = xServer.inputDeviceManager.getPointWindow();
      Cursor cursor = pointWindow != null ? pointWindow.attributes.getCursor() : null;
      short x = xServer.pointer.getClampedX();
      short y = xServer.pointer.getClampedY();

      if (cursor != null) {
        if (cursor.isVisible())
          renderDrawable(
              cursor.cursorImage, x - cursor.hotSpotX, y - cursor.hotSpotY, cursorMaterial);
      } else renderDrawable(rootCursorDrawable, x, y, cursorMaterial);
    }

    quadVertices.disable();
  }

  public void toggleFullscreen() {
    fullscreen = !fullscreen;
    viewportNeedsUpdate = true;
    requestRenderCoalesced();
  }

  private Drawable createRootCursorDrawable() {
    Context context = xServerView.getContext();
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inScaled = false;
    Bitmap bitmap =
        BitmapFactory.decodeResource(context.getResources(), R.drawable.cursor, options);
    return Drawable.fromBitmap(bitmap);
  }

  private void updateScene() {
    try (XLock lock =
        xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.DRAWABLE_MANAGER)) {
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
        for (String unviewableWMClass : unviewableWMClasses) {
          if (wmClass.contains(unviewableWMClass)) {
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

  private void removeRenderableWindow(Window window) {
    for (int i = 0; i < renderableWindows.size(); i++) {
      if (renderableWindows.get(i).content == window.getContent()) {
        renderableWindows.remove(i);
        break;
      }
    }
  }

  private void updateWindowPosition(Window window) {
    for (RenderableWindow renderableWindow : renderableWindows) {
      if (renderableWindow.content == window.getContent()) {
        renderableWindow.rootX = window.getRootX();
        renderableWindow.rootY = window.getRootY();
        break;
      }
    }
  }

  public void setCursorVisible(boolean cursorVisible) {
    if (this.cursorVisible == cursorVisible) {
      return;
    }
    this.cursorVisible = cursorVisible;
    requestRenderCoalesced();
  }

  public boolean isCursorVisible() {
    return cursorVisible;
  }

  public boolean isScreenOffsetYRelativeToCursor() {
    return screenOffsetYRelativeToCursor;
  }

  public void setScreenOffsetYRelativeToCursor(boolean screenOffsetYRelativeToCursor) {
    this.screenOffsetYRelativeToCursor = screenOffsetYRelativeToCursor;
    requestRenderCoalesced();
  }

  public boolean isFullscreen() {
    return fullscreen;
  }

  public float getMagnifierZoom() {
    return magnifierZoom;
  }

  public void setMagnifierZoom(float magnifierZoom) {
    if (this.magnifierZoom != magnifierZoom) {
      this.magnifierZoom = magnifierZoom;
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
      panY =
          Mathf.clamp(
              xServer.pointer.getY() * 1.0f - screenH * 0.25f,
              0,
              screenH * 0.5f);
    }

    XForm.makeTransform(outXForm, -magnifierPanX, -panY, currentZoom, currentZoom, 0);
  }

  public int getSurfaceWidth() {
    return surfaceWidth;
  }

  public int getSurfaceHeight() {
    return surfaceHeight;
  }

  public boolean isViewportNeedsUpdate() {
    return viewportNeedsUpdate;
  }

  public void setViewportNeedsUpdate(boolean viewportNeedsUpdate) {
    this.viewportNeedsUpdate = viewportNeedsUpdate;
  }

  public VertexAttribute getQuadVertices() {
    return quadVertices;
  }

  public void setNativeMode(boolean enable) {
    if (cpuSaverMode != enable) {
      cpuSaverMode = enable;
      viewportNeedsUpdate = true;
      applyRenderMode();
      requestRenderCoalesced();
    }
  }

  public boolean isNativeMode() {
    return cpuSaverMode;
  }

  public void setMagnifierUIActive(boolean active) {
    if (magnifierUIActive == active) return;
    magnifierUIActive = active;
    magnifierPanInitialized = false;
    viewportNeedsUpdate = true;
    applyRenderMode();
    requestRenderCoalesced();
  }

  public boolean isMagnifierUIActive() {
    return magnifierUIActive;
  }

  private void applyRenderMode() {
    xServerView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
  }

  public void setFpsLimit(int fps) {
    currentFpsLimit = Math.max(0, Math.min(fps, MAX_FPS_LIMIT));
  }

  public int getFpsLimit() {
    return currentFpsLimit;
  }

  private void resetFrameState() {
    GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    GLES20.glEnable(GLES20.GL_BLEND);
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
  }

  private void drawFrameOptimized() {
    resetFrameState();

    RenderableWindow directCandidate = null;
    int screenW = xServer.screenInfo.width;
    int screenH = xServer.screenInfo.height;

    try (XLock lock = xServer.lock(XServer.Lockable.DRAWABLE_MANAGER)) {
      for (int i = renderableWindows.size() - 1; i >= 0; i--) {
        RenderableWindow rWin = renderableWindows.get(i);
        if (rWin.content != null
            && isDirectScanoutContent(rWin.content)
            && rWin.content.width >= screenW * 0.95f
            && rWin.content.height >= screenH * 0.95f) {
          directCandidate = rWin;
          break;
        }
      }
    }

    boolean isDirect = directCandidate != null;
    if (isDirect != wasDirectMode) {
      viewportNeedsUpdate = true;
      wasDirectMode = isDirect;
    }

    if (isDirect) {
      // Phase 2.3/2.5 — Direct Composition push + GL skip.
      //
      // When the activity has wired up a SurfaceControl target AND the
      // candidate's scanoutSource is a GPUImage (AHardwareBuffer-backed),
      // hand the buffer directly to the SC layer. If the push succeeded
      // (or the cache hit), the SC layer at z=1 covers the SurfaceView's
      // primary BufferQueue at z=0, so we can SKIP the GL render of the
      // direct candidate entirely — that's the actual perf win this whole
      // path is for. The GL clear + cursor render still run so the
      // fallback for the next non-eligible frame is a clean state.
      //
      // If the push failed (returned false), we keep the GL render as
      // defence in depth — the SC layer was hidden by maybePushDirectComposition's
      // fail-out path, so the GL composition is what the user sees.
      boolean dcOwnsFrame = maybePushDirectComposition(directCandidate);
      dcLayerActive = dcOwnsFrame;

      if (viewportNeedsUpdate) {
        if (fullscreen) {
          GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
        } else {
          GLES20.glViewport(
              viewTransformation.viewOffsetX,
              viewTransformation.viewOffsetY,
              viewTransformation.viewWidth,
              viewTransformation.viewHeight);
        }
        viewportNeedsUpdate = false;
      }

      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
      GLES20.glDisable(GLES20.GL_BLEND);

      if (magnifierEnabled) {
        computeMagnifierPan(tmpXForm2);
      } else if (!fullscreen) {
        int pointerY = 0;
        if (screenOffsetYRelativeToCursor) {
          short halfScreenHeight = (short) (xServer.screenInfo.height / 2);
          pointerY =
              Mathf.clamp(xServer.pointer.getY() - halfScreenHeight / 2, 0, halfScreenHeight);
        }
        XForm.makeTransform(
            tmpXForm2,
            viewTransformation.sceneOffsetX,
            viewTransformation.sceneOffsetY - pointerY,
            viewTransformation.sceneScaleX,
            viewTransformation.sceneScaleY,
            0);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(
            viewTransformation.viewOffsetX,
            viewTransformation.viewOffsetY,
            viewTransformation.viewWidth,
            viewTransformation.viewHeight);
      } else {
        XForm.identity(tmpXForm2);
      }

      windowMaterial.use();
      GLES20.glUniform2f(
          windowMaterial.getUniformLocation("viewSize"),
          xServer.screenInfo.width,
          xServer.screenInfo.height);
      windowMaterial.setUniformInt("swapRB", swapRB ? 1 : 0);
      quadVertices.bind(windowMaterial.programId);
      // Phase 3.3 (replaces 2.5's GL-skip): always render the GL
      // composition, even when the SurfaceControl layer is showing the
      // same content. Reasoning:
      //
      //  * The actual Direct Composition perf win is HWC promoting the SC
      //    layer to a DPU overlay plane (zero GPU compositing cost) —
      //    NOT the GLThread skipping its draw. The GL work was already
      //    happening pre-DC and the GPU handles it without measurable
      //    impact on frame time.
      //  * Skipping the GL render produced a black GLSurfaceView backing
      //    buffer underneath the SC layer. On any direct→fallback
      //    transition where SF applied the SC-hide before the next GL
      //    composition (one-frame async race), the user briefly saw the
      //    black backbuffer — visible flicker.
      //  * Always-render keeps the GL backbuffer in lockstep with the SC
      //    content, so a stale-frame reveal is never possible: whatever
      //    SF reveals when SC hides is the same frame the user is
      //    already seeing through SC.
      //
      // Cost: one extra SF transaction per frame plus the GL composition
      // (which was already paid pre-DC). The transaction is microseconds;
      // the GL composition is offloaded to Adreno and runs concurrently
      // with HWC's DPU plane composition.
      renderDrawable(
          directCandidate.content, directCandidate.rootX, directCandidate.rootY, windowMaterial);

      if (cursorVisible) {
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        renderCursor();
      }
      if (!magnifierEnabled && !fullscreen) {
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
      }
      GLES20.glEnable(GLES20.GL_BLEND);
      GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
      quadVertices.disable();
    } else {
      // No fullscreen direct-scanout candidate — fall back to normal GL
      // composition AND hide the SC layer (it might still be showing a
      // stale frame from when we WERE in direct mode). maybeHideDirectComposition
      // is idempotent, so this is cheap on subsequent fallback frames.
      maybeHideDirectComposition();
      drawFrame();
    }
  }

  private boolean isDirectScanoutContent(Drawable drawable) {
    Drawable scanoutSource = drawable.getScanoutSource();
    return scanoutSource != null && scanoutSource.isDirectScanout();
  }

  /**
   * Phase 2.3 hot path: extract the AHardwareBuffer for the candidate's
   * scanoutSource and hand it to the per-activity {@code DirectCompositionLayer}.
   *
   * <p>Holds {@code candidate.content.renderLock} for the lookup so we can't
   * race against {@code DRI3Extension.tryPixmapFromHardwareBuffer} replacing
   * the texture or {@code GPUImage.destroy()} releasing the underlying AHB
   * mid-read. The JNI {@code pushBuffer} runs INSIDE the lock too — short
   * call, SurfaceFlinger takes its own ref on the AHB inside
   * {@code ASurfaceTransaction_setBuffer apply}, so the buffer is safe to
   * release on the X-server thread the moment we exit the lock.
   *
   * <p>Per-frame waste suppression: caches the last successfully-pushed
   * (ahbPtr, dstW, dstH) and skips the JNI call when nothing has changed.
   * DRI3 allocates a fresh GPUImage each Present, so AHB-pointer identity
   * is a sufficient "buffer changed" signal.
   *
   * <p>Failure counter: after {@code DC_FAIL_LIMIT} consecutive {@code false}
   * returns from pushBuffer (e.g. SurfaceFlinger reparented the layer for
   * us, or libandroid is mis-resolved on this build), nulls
   * {@code directCompositionTarget} so subsequent frames don't keep paying
   * the JNI cost for a permanent failure.
   */
  /**
   * @return true if a fresh AHB was pushed to the SC layer this frame OR if
   *         the cache hit (SC is still showing a valid prior frame). Used by
   *         the caller to skip the redundant GL composition of the direct
   *         candidate (Phase 2.5 perf win).
   */
  private boolean maybePushDirectComposition(RenderableWindow directCandidate) {
    final com.winlator.cmod.runtime.display.composition.DirectCompositionLayer dcTarget =
            directCompositionTarget;
    if (dcTarget == null) return false;
    if (surfaceWidth <= 0 || surfaceHeight <= 0) return false;
    // Force fallback to GL composition when an in-process overlay needs to be
    // visible on top of the game frame. The SC layer at z=1 covers the
    // GLSurfaceView's primary BQ at z=0, so anything we render via GL
    // (magnifier UI, debug HUDs, picker dialogs that draw into the GL
    // surface) would otherwise be invisible. Hide the SC NOW (not later)
    // so the next vsync shows the GL overlay instead of the stale buffer.
    if (magnifierUIActive) {
      if (dcLayerActive) {
        dcTarget.hide();
        dcLayerActive = false;
        dcLastPushedAhb = 0L;
        dcLastPushedW = 0;
        dcLastPushedH = 0;
      }
      return false;
    }

    final Drawable content = directCandidate.content;
    synchronized (content.renderLock) {
      Drawable scanoutSource = content.getScanoutSource();
      if (scanoutSource == null) return false;
      com.winlator.cmod.runtime.display.renderer.Texture tex = scanoutSource.getTexture();
      if (!(tex instanceof GPUImage)) return false;
      long ahbPtr = ((GPUImage) tex).getHardwareBufferPtr();
      if (ahbPtr == 0L) return false;
      // Skip JNI when nothing has changed since the last push. SurfaceFlinger
      // is still showing the layer; no point queueing a no-op transaction.
      if (ahbPtr == dcLastPushedAhb
              && surfaceWidth == dcLastPushedW
              && surfaceHeight == dcLastPushedH) {
        return true;
      }
      // Producer-acquire fence: TAKE the FD from the scanout source under
      // the renderLock, atomically clearing it on the Drawable. We are now
      // the single owner; if pushBuffer succeeds, the framework closes
      // the FD via setBuffer; if pushBuffer fails, the JNI layer closes
      // the FD on its own error paths.
      //
      // Today this is always -1 because the X server's PRESENT/DRI3 parser
      // doesn't yet extract a `wait_fence` from the wire request. We
      // empirically observe no tearing without it: the in-process Java
      // X server processes PIXMAP_FROM_BUFFERS only after the Wine client
      // has already submitted its Vulkan commands, and that CPU-side
      // handoff latency exceeds GPU-write completion time on the SoCs
      // we've tested. This is an observation about this specific pipeline,
      // not a Vulkan or Mesa contract — when the parser gains real
      // wait_fence support, it'll call Drawable.setAcquireFenceFd and the
      // following takeAcquireFenceFd() will return a real FD.
      int fenceFd = scanoutSource.takeAcquireFenceFd();
      // Game swap-chain frames from DXVK / vkd3d-proton are RGBA8888 with
      // alpha=1.0 throughout — declaring OPAQUE lets HWC skip the alpha-blend
      // stage that engages the SDR-on-HDR-panel routing on Snapdragon DPUs
      // (the visible-brightness mismatch vs the GL composition path).
      boolean ok = dcTarget.pushBuffer(ahbPtr, 0, 0, surfaceWidth, surfaceHeight, fenceFd,
              /* opaque */ true);
      if (ok) {
        dcLastPushedAhb = ahbPtr;
        dcLastPushedW = surfaceWidth;
        dcLastPushedH = surfaceHeight;
        dcConsecutiveFailures = 0;
        return true;
      } else {
        dcConsecutiveFailures++;
        if (dcConsecutiveFailures >= DC_FAIL_LIMIT) {
          android.util.Log.w(
                  "GLRenderer",
                  "DirectComposition push failed " + dcConsecutiveFailures
                          + " frames in a row — disabling target for this session");
          // Hide the SC layer BEFORE nulling the field — once the field is
          // null, maybeHideDirectComposition has nothing to call hide() on,
          // and SurfaceFlinger would keep showing the last successfully-pushed
          // buffer over the GL output forever (until activity teardown
          // released the SC). Use the local dcTarget — still live in this
          // scope even after we null the field.
          dcTarget.hide();
          dcLayerActive = false;
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
   * Phase 2.5 — hide the Direct Composition layer when the current frame
   * doesn't qualify for the SC fast path (windowed app, multi-drawable,
   * cursor visible over a non-fullscreen scene, etc.). Idempotent and cheap
   * after the first call: tracks {@link #dcLayerActive} so we only queue
   * a hide-transaction once per direct→fallback transition.
   */
  private void maybeHideDirectComposition() {
    if (!dcLayerActive) return;
    com.winlator.cmod.runtime.display.composition.DirectCompositionLayer dcTarget =
            directCompositionTarget;
    if (dcTarget != null) {
      dcTarget.hide();
    }
    dcLayerActive = false;
    // Invalidate the cache so the next pushBuffer re-shows with a fresh
    // setBuffer + setVisibility(SHOW) transaction, even if the same AHB
    // pointer happens to be active.
    dcLastPushedAhb = 0L;
    dcLastPushedW = 0;
    dcLastPushedH = 0;
  }

  public void setUnviewableWMClasses(String... unviewableWMNames) {
    this.unviewableWMClasses = unviewableWMNames;
  }

  /**
   * Hand the renderer the per-activity Direct Composition layer (or null to
   * detach). Safe to call from the UI thread; the GLThread reads the field
   * volatile-ly each frame inside {@link #drawFrameOptimized()}.
   *
   * <p>When the layer is set AND the frame is a fullscreen direct-scanout
   * candidate, the drawable's underlying AHardwareBuffer is also pushed to
   * the SurfaceControl. The GLRenderer keeps drawing the same frame for
   * Phase 2.3 (defence-in-depth: if the SurfaceControl path fails for any
   * reason, the GL output is still visible underneath the SC layer).
   * Phase 2.5 will skip the GL render to capture the actual perf win.
   */
  public void setDirectCompositionTarget(
          com.winlator.cmod.runtime.display.composition.DirectCompositionLayer layer) {
    this.directCompositionTarget = layer;
  }

  @Override
  public void onFramePresented(com.winlator.cmod.runtime.display.xserver.Window window) {
  }
}
