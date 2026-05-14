package com.winlator.cmod.runtime.display.xserver;

import android.util.SparseArray;
import android.util.Log;
import com.winlator.cmod.runtime.display.renderer.Texture;
import com.winlator.cmod.runtime.display.renderer.VulkanRenderer;
import com.winlator.cmod.shared.util.Callback;

public class DrawableManager extends XResourceManager
    implements XResourceManager.OnResourceLifecycleListener {
  private static final String TAG = "DrawableManager";
  private final XServer xServer;
  private final SparseArray<Drawable> drawables = new SparseArray<>();

  public DrawableManager(XServer xServer) {
    this.xServer = xServer;
    xServer.pixmapManager.addOnResourceLifecycleListener(this);
  }

  public Drawable getDrawable(int id) {
    Drawable drawable = drawables.get(id);
    if (drawable != null && drawable.getData() == null) {
      throw new IllegalStateException("Drawable with id " + id + " has null data when fetched.");
    }
    return drawable;
  }

  public Drawable createDrawable(int id, short width, short height, byte depth) {
    return createDrawable(id, width, height, xServer.pixmapManager.getVisualForDepth(depth));
  }

  public Drawable createDrawable(int id, short width, short height, Visual visual) {
    if (id == 0) {
      Drawable drawable = new Drawable(id, width, height, visual);
      if (drawable.getData() == null) {
        throw new IllegalStateException("Drawable with id 0 has null data at creation.");
      }
      return drawable;
    }
    if (drawables.indexOfKey(id) >= 0) return null;
    Drawable drawable = new Drawable(id, width, height, visual);
    if (drawable.getData() == null) {
      throw new IllegalStateException("Drawable with id " + id + " has null data at creation.");
    }
    drawables.put(id, drawable);
    return drawable;
  }

  public void removeDrawable(int id) {
    Drawable drawable = drawables.get(id);
    if (drawable == null) {
      Log.w(TAG, "Ignoring removal for missing Drawable with id " + id);
      return;
    }
    if (drawable.getData() == null) {
      Log.w(TAG, "Ignoring removal for Drawable with null data, id " + id);
      drawables.remove(id);
      return;
    }

    detachScanoutUsers(drawable);

    final Texture texture = drawable.getTexture();
    VulkanRenderer renderer = xServer.getRenderer();
    if (texture != null && renderer != null) renderer.xServerView.queueEvent(texture::destroy);

    Callback<Drawable> onDestroyListener = drawable.getOnDestroyListener();
    if (onDestroyListener != null) onDestroyListener.call(drawable);

    drawable.setOnDrawListener(null);
    drawables.remove(id);
  }

  @Override
  public void onFreeResource(XResource resource) {
    if (resource instanceof Pixmap) {
      Pixmap pixmap = (Pixmap) resource;
      Drawable drawable = pixmap.drawable;
      if (drawable.getData() == null) {
        throw new IllegalStateException(
            "Drawable for Pixmap with id " + pixmap.drawable.id + " has null data during free.");
      }
      removeDrawable(drawable.id);
    }
  }

  public Visual getVisual() {
    return xServer.pixmapManager.visual;
  }

  private void detachScanoutUsers(Drawable source) {
    for (Window window : xServer.windowManager.getWindows()) {
      if (!window.isInputOutput()) continue;

      Drawable content = window.getContent();
      if (content.getScanoutSource() != source) continue;

      synchronized (content.renderLock) {
        if (source.getData() != null
            && source.visual != null
            && content.visual.depth == source.visual.depth) {
          if (!copyScanoutRegion(content, source)) {
            content.clearScanoutSource();
            xServer.windowManager.triggerOnUpdateWindowContent(window);
          }
        } else {
          content.clearScanoutSource();
          xServer.windowManager.triggerOnUpdateWindowContent(window);
        }
      }
    }
  }

  private static boolean copyScanoutRegion(Drawable content, Drawable source) {
    int xOff = content.getScanoutX();
    int yOff = content.getScanoutY();
    int srcX = Math.max(0, -xOff);
    int srcY = Math.max(0, -yOff);
    int dstX = Math.max(0, xOff);
    int dstY = Math.max(0, yOff);
    int width = Math.min(source.width - srcX, content.width - dstX);
    int height = Math.min(source.height - srcY, content.height - dstY);
    if (width <= 0 || height <= 0) return false;

    content.copyArea(
        (short) srcX,
        (short) srcY,
        (short) dstX,
        (short) dstY,
        (short) width,
        (short) height,
        source);
    return true;
  }
}
