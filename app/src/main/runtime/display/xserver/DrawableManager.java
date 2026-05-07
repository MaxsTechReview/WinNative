package com.winlator.cmod.runtime.display.xserver;

import android.util.SparseArray;
import android.util.Log;
import com.winlator.cmod.runtime.display.renderer.GLRenderer;
import com.winlator.cmod.runtime.display.renderer.Texture;
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
    GLRenderer renderer = xServer.getRenderer();
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
          content.copyArea(
              (short) 0,
              (short) 0,
              (short) 0,
              (short) 0,
              source.width,
              source.height,
              source);
        } else {
          content.clearScanoutSource();
          xServer.windowManager.triggerOnUpdateWindowContent(window);
        }
      }
    }
  }
}
