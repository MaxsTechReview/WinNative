package com.winlator.cmod.runtime.display.xserver;

import android.util.Log;
import android.util.SparseArray;
import com.winlator.cmod.runtime.display.connector.XInputStream;
import com.winlator.cmod.runtime.display.xserver.errors.BadIdChoice;
import com.winlator.cmod.runtime.display.xserver.errors.BadMatch;
import com.winlator.cmod.runtime.display.xserver.errors.XRequestError;
import com.winlator.cmod.runtime.display.xserver.events.ConfigureNotify;
import com.winlator.cmod.runtime.display.xserver.events.ConfigureRequest;
import com.winlator.cmod.runtime.display.xserver.events.DestroyNotify;
import com.winlator.cmod.runtime.display.xserver.events.Event;
import com.winlator.cmod.runtime.display.xserver.events.Expose;
import com.winlator.cmod.runtime.display.xserver.events.MapNotify;
import com.winlator.cmod.runtime.display.xserver.events.MapRequest;
import com.winlator.cmod.runtime.display.xserver.events.ResizeRequest;
import com.winlator.cmod.runtime.display.xserver.events.UnmapNotify;
import java.util.ArrayList;
import java.util.List;

public class WindowManager extends XResourceManager {
  private static final String SGSR_RESIZE_TAG = "SGSRResize";

  public enum FocusRevertTo {
    NONE,
    POINTER_ROOT,
    PARENT
  }

  public enum FrameSource {
    UNKNOWN,
    PRESENT,
    PUT_IMAGE,
    MIT_SHM,
    DRI3_BUFFER
  }

  public final Window rootWindow;
  private final SparseArray<Window> windows = new SparseArray<>();
  public final DrawableManager drawableManager;
  private Window focusedWindow;
  private FocusRevertTo focusRevertTo = FocusRevertTo.NONE;
  private final ArrayList<OnWindowModificationListener> onWindowModificationListeners =
      new ArrayList<>();

  public interface OnWindowModificationListener {
    default void onMapWindow(Window window) {}

    default void onUnmapWindow(Window window) {}

    default void onDestroyWindow(Window window) {}

    default void onChangeWindowZOrder(Window window) {}

    default void onUpdateWindowContent(Window window) {}

    default void onUpdateWindowGeometry(Window window, boolean resized) {}

    default void onUpdateWindowAttributes(Window window, Bitmask mask) {}

    default void onModifyWindowProperty(Window window, Property property) {}

    default void onFramePresented(Window window) {}

    default void onFramePresented(Window window, FrameSource source, int serial) {
      onFramePresented(window);
    }
  }

  public WindowManager(ScreenInfo screenInfo, DrawableManager drawableManager) {
    this.drawableManager = drawableManager;
    int id = IDGenerator.generate();
    Drawable drawable =
        drawableManager.createDrawable(
            id, screenInfo.width, screenInfo.height, drawableManager.getVisual());
    rootWindow = new Window(id, drawable, 0, 0, screenInfo.width, screenInfo.height, null);
    rootWindow.attributes.setMapped(true);
    windows.put(id, rootWindow);
  }

  public Window getWindow(int id) {
    return windows.get(id);
  }

  public boolean resizeRootWindow(short width, short height) {
    if (width <= 0 || height <= 0) return false;
    short oldWidth = rootWindow.getWidth();
    short oldHeight = rootWindow.getHeight();
    if (oldWidth == width && oldHeight == height) return false;

    Log.i(SGSR_RESIZE_TAG, "resizeRootWindow: " + oldWidth + "x" + oldHeight +
        " -> " + width + "x" + height);
    resizeWindowForScreenChange(rootWindow, (short) 0, (short) 0, width, height);
    resizeFullscreenDescendants(rootWindow, oldWidth, oldHeight, width, height);
    return true;
  }

  public List<Window> getWindows() {
    ArrayList<Window> list = new ArrayList<>();
    for (int i = 0; i < windows.size(); i++) list.add(windows.valueAt(i));
    return list;
  }

  public Window findWindowWithProcessId(int processId) {
    for (int i = 0; i < windows.size(); i++) {
      Window window = windows.valueAt(i);
      if (window != null && window.getProcessId() == processId) return window;
    }
    return null;
  }

  public void destroyWindow(int id) {
    Window window = getWindow(id);
    if (window != null && rootWindow.id != id) {
      unmapWindow(window);
      removeAllSubwindowsAndWindow(window);
      triggerOnDestroyWindow(window);
    }
  }

  private void removeAllSubwindowsAndWindow(Window window) {
    List<Window> children = new ArrayList<>(window.getChildren());
    for (Window child : children) removeAllSubwindowsAndWindow(child);

    Window parent = window.getParent();
    window.sendEvent(Event.STRUCTURE_NOTIFY, new DestroyNotify(window, window));
    parent.sendEvent(Event.SUBSTRUCTURE_NOTIFY, new DestroyNotify(parent, window));
    windows.remove(window.id);
    if (window.isInputOutput()) drawableManager.removeDrawable(window.getContent().id);
    triggerOnFreeResourceListener(window);
    if (window == focusedWindow) revertFocus();
    parent.removeChild(window);
  }

  public void mapWindow(Window window) {
    if (!window.attributes.isMapped()) {
      Window parent = window.getParent();
      if (!parent.hasEventListenerFor(Event.SUBSTRUCTURE_REDIRECT)
          || window.attributes.isOverrideRedirect()) {
        window.attributes.setMapped(true);
        window.sendEvent(Event.STRUCTURE_NOTIFY, new MapNotify(window, window));
        parent.sendEvent(Event.SUBSTRUCTURE_NOTIFY, new MapNotify(parent, window));
        window.sendEvent(Event.EXPOSURE, new Expose(window));
        triggerOnMapWindow(window);
      } else parent.sendEvent(Event.SUBSTRUCTURE_REDIRECT, new MapRequest(parent, window));
    }
  }

  public void unmapWindow(Window window) {
    if (rootWindow.id != window.id && window.attributes.isMapped()) {
      window.attributes.setMapped(false);
      Window parent = window.getParent();
      window.sendEvent(Event.STRUCTURE_NOTIFY, new UnmapNotify(window, window));
      parent.sendEvent(Event.SUBSTRUCTURE_NOTIFY, new UnmapNotify(parent, window));
      if (window == focusedWindow) revertFocus();
      triggerOnUnmapWindow(window);
    }
  }

  public Window getFocusedWindow() {
    return focusedWindow;
  }

  public void revertFocus() {
    switch (focusRevertTo) {
      case NONE:
        focusedWindow = null;
        break;
      case POINTER_ROOT:
        focusedWindow = rootWindow;
        break;
      case PARENT:
        if (focusedWindow != null && focusedWindow.getParent() != null)
          focusedWindow = focusedWindow.getParent();
        break;
    }
  }

  public void setFocus(Window focusedWindow, FocusRevertTo focusRevertTo) {
    this.focusedWindow = focusedWindow;
    this.focusRevertTo = focusRevertTo;
  }

  public FocusRevertTo getFocusRevertTo() {
    return focusRevertTo;
  }

  public Window createWindow(
      int id,
      Window parent,
      short x,
      short y,
      short width,
      short height,
      WindowAttributes.WindowClass windowClass,
      Visual visual,
      byte depth,
      XClient client)
      throws XRequestError {
    if (windows.indexOfKey(id) >= 0) throw new BadIdChoice(id);

    boolean isInputOutput = false;
    switch (windowClass) {
      case COPY_FROM_PARENT:
        depth = (depth != 0 || !parent.isInputOutput()) ? depth : parent.getContent().visual.depth;
        isInputOutput = parent.isInputOutput();
        break;
      case INPUT_OUTPUT:
        if (parent.isInputOutput()) {
          depth = depth == 0 ? parent.getContent().visual.depth : depth;
          isInputOutput = true;
        } else throw new BadMatch();
        break;
      case INPUT_ONLY:
        isInputOutput = false;
        break;
    }

    if (isInputOutput) {
      visual = visual == null ? parent.getContent().visual : visual;
      if (depth != visual.depth) throw new BadMatch();
    }

    Drawable drawable = null;
    if (isInputOutput) {
      drawable = drawableManager.createDrawable(id, width, height, visual);
      if (drawable == null) throw new BadIdChoice(id);
    }

    final Window window = new Window(id, drawable, x, y, width, height, client);
    window.attributes.setWindowClass(windowClass);
    if (drawable != null) drawable.setOnDrawListener(() -> triggerOnUpdateWindowContent(window));
    windows.put(id, window);
    parent.addChild(window);
    triggerOnCreateResourceListener(window);
    return window;
  }

  private void changeWindowGeometry(Window window, short x, short y, short width, short height) {
    boolean resized = window.getWidth() != width || window.getHeight() != height;
    if (resized && window.hasEventListenerFor(Event.RESIZE_REDIRECT)) {
      window.sendEvent(Event.SUBSTRUCTURE_REDIRECT, new ResizeRequest(window, width, height));
      width = window.getWidth();
      height = window.getHeight();
      resized = false;
    }

    if (resized && window.isInputOutput()) {
      Drawable oldContent = window.getContent();
      drawableManager.removeDrawable(oldContent.id);
      Drawable newContent =
          drawableManager.createDrawable(oldContent.id, width, height, oldContent.visual);
      newContent.setOnDrawListener(() -> triggerOnUpdateWindowContent(window));
      window.setContent(newContent);
    }

    if (resized || window.getX() != x || window.getY() != y) {
      window.setX(x);
      window.setY(y);
      window.setWidth(width);
      window.setHeight(height);
      triggerOnUpdateWindowGeometry(window, resized);
    }

    if (resized && window.isInputOutput() && window.attributes.isMapped()) {
      window.sendEvent(new Expose(window));
    }
  }

  private void resizeWindowForScreenChange(Window window, short x, short y, short width, short height) {
    short oldX = window.getX();
    short oldY = window.getY();
    short oldWidth = window.getWidth();
    short oldHeight = window.getHeight();
    boolean resized = window.getWidth() != width || window.getHeight() != height;
    boolean moved = window.getX() != x || window.getY() != y;
    if (!resized && !moved) return;

    Log.i(SGSR_RESIZE_TAG, "resizeWindow id=" + window.id +
        (window == rootWindow ? " root" : "") + ": " +
        oldWidth + "x" + oldHeight + "@" + oldX + "," + oldY + " -> " +
        width + "x" + height + "@" + x + "," + y +
        " mapped=" + window.attributes.isMapped());

    if (resized && window.isInputOutput()) {
      Drawable oldContent = window.getContent();
      drawableManager.removeDrawable(oldContent.id);
      Drawable newContent =
          drawableManager.createDrawable(oldContent.id, width, height, oldContent.visual);
      newContent.setOnDrawListener(() -> triggerOnUpdateWindowContent(window));
      window.setContent(newContent);
    }

    window.setX(x);
    window.setY(y);
    window.setWidth(width);
    window.setHeight(height);

    Window parent = window.getParent();
    Window previousSibling = window.previousSibling();
    window.sendEvent(
        Event.STRUCTURE_NOTIFY,
        new ConfigureNotify(
            window, window, previousSibling, x, y, width, height, window.getBorderWidth(),
            window.attributes.isOverrideRedirect()));
    if (parent != null) {
      parent.sendEvent(
          Event.SUBSTRUCTURE_NOTIFY,
          new ConfigureNotify(
              parent, window, previousSibling, x, y, width, height, window.getBorderWidth(),
              window.attributes.isOverrideRedirect()));
    }

    triggerOnUpdateWindowGeometry(window, resized);
    if (resized && window.isInputOutput() && window.attributes.isMapped()) {
      window.sendEvent(new Expose(window));
    }
  }

  private void resizeFullscreenDescendants(Window parent, short oldWidth, short oldHeight,
                                           short width, short height) {
    List<Window> children = new ArrayList<>(parent.getChildren());
    for (Window child : children) {
      boolean coveredOldScreen =
          child.getX() <= 0
              && child.getY() <= 0
              && child.getX() + child.getWidth() >= oldWidth
              && child.getY() + child.getHeight() >= oldHeight;
      if (coveredOldScreen) {
        Log.i(SGSR_RESIZE_TAG, "resizeFullscreenDescendant id=" + child.id +
            ": covered old screen " + oldWidth + "x" + oldHeight);
        resizeWindowForScreenChange(child, (short) 0, (short) 0, width, height);
      }
      resizeFullscreenDescendants(child, oldWidth, oldHeight, width, height);
    }
  }

  private void changeWindowZOrder(Window.StackMode stackMode, Window window, Window sibling) {
    Window parent = window.getParent();
    switch (stackMode) {
      case ABOVE:
        parent.moveChildAbove(window, sibling);
        break;
      case BELOW:
        parent.moveChildBelow(window, sibling);
        break;
    }
    triggerOnChangeWindowZOrder(window);
  }

  public void configureWindow(Window window, Bitmask valueMask, XInputStream inputStream) {
    short x = window.getX();
    short y = window.getY();
    short width = window.getWidth();
    short height = window.getHeight();
    short borderWidth = window.getBorderWidth();
    Window sibling = null;
    Window.StackMode stackMode = null;

    for (long index : valueMask) {
      switch ((int) index) {
        case Window.FLAG_X:
          x = (short) inputStream.readInt();
          break;
        case Window.FLAG_Y:
          y = (short) inputStream.readInt();
          break;
        case Window.FLAG_WIDTH:
          width = (short) inputStream.readInt();
          break;
        case Window.FLAG_HEIGHT:
          height = (short) inputStream.readInt();
          break;
        case Window.FLAG_BORDER_WIDTH:
          borderWidth = (short) inputStream.readInt();
          break;
        case Window.FLAG_SIBLING:
          sibling = getWindow(inputStream.readInt());
          break;
        case Window.FLAG_STACK_MODE:
          stackMode = Window.StackMode.values()[inputStream.readInt()];
          break;
      }
    }

    Window parent = window.getParent();
    boolean overrideRedirect = window.attributes.isOverrideRedirect();
    if (!parent.hasEventListenerFor(Event.SUBSTRUCTURE_REDIRECT) || overrideRedirect) {
      changeWindowGeometry(window, x, y, width, height);

      window.setBorderWidth(borderWidth);
      if (stackMode != null) changeWindowZOrder(stackMode, window, sibling);

      Window previousSibling = window.previousSibling();
      window.sendEvent(
          Event.STRUCTURE_NOTIFY,
          new ConfigureNotify(
              window, window, previousSibling, x, y, width, height, borderWidth, overrideRedirect));
      parent.sendEvent(
          Event.SUBSTRUCTURE_NOTIFY,
          new ConfigureNotify(
              parent, window, previousSibling, x, y, width, height, borderWidth, overrideRedirect));
    } else
      parent.sendEvent(
          Event.SUBSTRUCTURE_REDIRECT,
          new ConfigureRequest(
              parent,
              window,
              window.previousSibling(),
              x,
              y,
              width,
              height,
              borderWidth,
              stackMode,
              valueMask));
  }

  public void reparentWindow(Window window, Window newParent) {
    Window oldParent = window.getParent();
    if (oldParent != null) oldParent.removeChild(window);
    newParent.addChild(window);
  }

  public Window findPointWindow(short rootX, short rootY) {
    return findPointWindow(rootWindow, rootX, rootY);
  }

  private Window findPointWindow(Window window, short rootX, short rootY) {
    if (!(window.attributes.isMapped() && window.containsPoint(rootX, rootY))) return null;
    Window child = window.getChildByCoords(rootX, rootY);
    return child != null ? findPointWindow(child, rootX, rootY) : window;
  }

  public void addOnWindowModificationListener(
      OnWindowModificationListener onWindowModificationListener) {
    synchronized (onWindowModificationListeners) {
      onWindowModificationListeners.add(onWindowModificationListener);
    }
  }

  public void removeOnWindowModificationListener(
      OnWindowModificationListener onWindowModificationListener) {
    synchronized (onWindowModificationListeners) {
      onWindowModificationListeners.remove(onWindowModificationListener);
    }
  }

  private void triggerOnMapWindow(Window window) {
    synchronized (onWindowModificationListeners) {
      for (int i = onWindowModificationListeners.size() - 1; i >= 0; i--) {
        onWindowModificationListeners.get(i).onMapWindow(window);
      }
    }
  }

  private void triggerOnUnmapWindow(Window window) {
    synchronized (onWindowModificationListeners) {
      for (int i = onWindowModificationListeners.size() - 1; i >= 0; i--) {
        onWindowModificationListeners.get(i).onUnmapWindow(window);
      }
    }
  }

  public void triggerOnDestroyWindow(Window window) {
    synchronized (onWindowModificationListeners) {
      for (int i = onWindowModificationListeners.size() - 1; i >= 0; i--) {
        onWindowModificationListeners.get(i).onDestroyWindow(window);
      }
    }
  }

  private void triggerOnChangeWindowZOrder(Window window) {
    synchronized (onWindowModificationListeners) {
      for (int i = onWindowModificationListeners.size() - 1; i >= 0; i--) {
        onWindowModificationListeners.get(i).onChangeWindowZOrder(window);
      }
    }
  }

  protected void triggerOnUpdateWindowContent(Window window) {
    synchronized (onWindowModificationListeners) {
      for (int i = onWindowModificationListeners.size() - 1; i >= 0; i--) {
        onWindowModificationListeners.get(i).onUpdateWindowContent(window);
      }
    }
  }

  protected void triggerOnUpdateWindowGeometry(Window window, boolean resized) {
    synchronized (onWindowModificationListeners) {
      for (int i = onWindowModificationListeners.size() - 1; i >= 0; i--) {
        onWindowModificationListeners.get(i).onUpdateWindowGeometry(window, resized);
      }
    }
  }

  public void triggerOnUpdateWindowAttributes(Window window, Bitmask mask) {
    synchronized (onWindowModificationListeners) {
      for (int i = onWindowModificationListeners.size() - 1; i >= 0; i--) {
        onWindowModificationListeners.get(i).onUpdateWindowAttributes(window, mask);
      }
    }
  }

  public void triggerOnModifyWindowProperty(Window window, Property property) {
    synchronized (onWindowModificationListeners) {
      for (int i = onWindowModificationListeners.size() - 1; i >= 0; i--) {
        onWindowModificationListeners.get(i).onModifyWindowProperty(window, property);
      }
    }
  }

  public void triggerOnFramePresented(Window window) {
    triggerOnFramePresented(window, FrameSource.UNKNOWN, 0);
  }

  public void triggerOnFramePresented(Window window, FrameSource source, int serial) {
    synchronized (onWindowModificationListeners) {
      for (int i = onWindowModificationListeners.size() - 1; i >= 0; i--) {
        onWindowModificationListeners.get(i).onFramePresented(window, source, serial);
      }
    }
  }
}
