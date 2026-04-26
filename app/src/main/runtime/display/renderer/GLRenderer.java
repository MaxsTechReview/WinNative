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
  public int surfaceWidth;
  public int surfaceHeight;
  private final EffectComposer effectComposer;
  private boolean cpuSaverMode = false;
  private static final int MAX_FPS_LIMIT = 1000;
  private final Object fpsLimiterLock = new Object();
  private volatile int currentFpsLimit = 0;
  private boolean wasDirectMode = false;

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
    if (cpuSaverMode) {
      drawFrameOptimized();
    } else {
      drawFrame();
    }
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
      // Apply magnifier transformations if enabled
      float pointerX = 0;
      float pointerY = 0;
      float magnifierZoom = !screenOffsetYRelativeToCursor ? this.magnifierZoom : 1.0f;

      if (magnifierZoom != 1.0f) {
        pointerX =
            Mathf.clamp(
                xServer.pointer.getX() * magnifierZoom - xServer.screenInfo.width * 0.5f,
                0,
                xServer.screenInfo.width * Math.abs(1.0f - magnifierZoom));
      }

      if (screenOffsetYRelativeToCursor || magnifierZoom != 1.0f) {
        float scaleY = magnifierZoom != 1.0f ? Math.abs(1.0f - magnifierZoom) : 0.5f;
        float offsetY = xServer.screenInfo.height * (screenOffsetYRelativeToCursor ? 0.25f : 0.5f);
        pointerY =
            Mathf.clamp(
                xServer.pointer.getY() * magnifierZoom - offsetY,
                0,
                xServer.screenInfo.height * scaleY);
      }

      XForm.makeTransform(tmpXForm2, -pointerX, -pointerY, magnifierZoom, magnifierZoom, 0);
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
    xServerView.requestRender();
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
    xServerView.requestRender();
  }

  public boolean isCursorVisible() {
    return cursorVisible;
  }

  public boolean isScreenOffsetYRelativeToCursor() {
    return screenOffsetYRelativeToCursor;
  }

  public void setScreenOffsetYRelativeToCursor(boolean screenOffsetYRelativeToCursor) {
    this.screenOffsetYRelativeToCursor = screenOffsetYRelativeToCursor;
    xServerView.requestRender();
  }

  public boolean isFullscreen() {
    return fullscreen;
  }

  public float getMagnifierZoom() {
    return magnifierZoom;
  }

  public void setMagnifierZoom(float magnifierZoom) {
    this.magnifierZoom = magnifierZoom;
    xServerView.requestRender();
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

  public EffectComposer getEffectComposer() {
    return effectComposer;
  }

  public void setNativeMode(boolean enable) {
    if (cpuSaverMode != enable) {
      cpuSaverMode = enable;
      viewportNeedsUpdate = true;
      xServerView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
      xServerView.requestRender();
    }
  }

  public boolean isNativeMode() {
    return cpuSaverMode;
  }

  public void setFpsLimit(int fps) {
    int normalizedFps = Math.max(0, Math.min(fps, MAX_FPS_LIMIT));
    synchronized (fpsLimiterLock) {
      if (currentFpsLimit != normalizedFps) {
        currentFpsLimit = normalizedFps;
      }
    }
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
        float pointerX = 0;
        float pointerY = 0;
        float currentZoom = !screenOffsetYRelativeToCursor ? magnifierZoom : 1.0f;
        if (currentZoom != 1.0f) {
          pointerX =
              Mathf.clamp(
                  xServer.pointer.getX() * currentZoom - xServer.screenInfo.width * 0.5f,
                  0,
                  xServer.screenInfo.width * Math.abs(1.0f - currentZoom));
        }
        if (screenOffsetYRelativeToCursor || currentZoom != 1.0f) {
          float scaleY = currentZoom != 1.0f ? Math.abs(1.0f - currentZoom) : 0.5f;
          float offsetY =
              xServer.screenInfo.height * (screenOffsetYRelativeToCursor ? 0.25f : 0.5f);
          pointerY =
              Mathf.clamp(
                  xServer.pointer.getY() * currentZoom - offsetY,
                  0,
                  xServer.screenInfo.height * scaleY);
        }
        XForm.makeTransform(tmpXForm2, -pointerX, -pointerY, currentZoom, currentZoom, 0);
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
      quadVertices.bind(windowMaterial.programId);
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
      // No fullscreen candidate — fall back to normal rendering
      drawFrame();
    }
  }

  private boolean isDirectScanoutContent(Drawable drawable) {
    Drawable scanoutSource = drawable.getScanoutSource();
    return scanoutSource != null && scanoutSource.isDirectScanout();
  }

  private void renderWindowEffect(Drawable drawable, int x, int y, ShaderMaterial material) {
    // Implement the rendering effect logic here
    synchronized (drawable.renderLock) {
      Drawable textureDrawable =
          drawable.getScanoutSource() != null ? drawable.getScanoutSource() : drawable;
      Texture texture = textureDrawable.getTexture();
      if (texture == null) return;
      texture.updateFromDrawable(textureDrawable);

      XForm.set(tmpXForm1, x, y, drawable.width, drawable.height);
      XForm.multiply(tmpXForm1, tmpXForm1, tmpXForm2);

      GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture.getTextureId());
      if (GLES20.glIsTexture(texture.getTextureId()) == false) {
        Log.e("GLRenderer", "Invalid texture binding!");
      }

      GLES20.glUniform1i(material.getUniformLocation("texture"), 0);
      GLES20.glUniform1fv(material.getUniformLocation("xform"), tmpXForm1.length, tmpXForm1, 0);
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, quadVertices.count());
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }
  }

  public void setUnviewableWMClasses(String... unviewableWMNames) {
    this.unviewableWMClasses = unviewableWMNames;
  }

  @Override
  public void onFramePresented(com.winlator.cmod.runtime.display.xserver.Window window) {
    xServerView.requestRender();
  }
}
