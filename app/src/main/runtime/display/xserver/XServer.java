package com.winlator.cmod.runtime.display.xserver;

import android.util.Log;
import android.util.SparseArray;
import com.winlator.cmod.shared.math.Mathf;
import com.winlator.cmod.runtime.display.renderer.VulkanRenderer;
import com.winlator.cmod.runtime.display.winhandler.MouseEventFlags;
import com.winlator.cmod.runtime.display.winhandler.WinHandler;
import com.winlator.cmod.runtime.display.xserver.extensions.BigReqExtension;
import com.winlator.cmod.runtime.display.xserver.extensions.DRI3Extension;
import com.winlator.cmod.runtime.display.xserver.extensions.Extension;
import com.winlator.cmod.runtime.display.xserver.extensions.MITSHMExtension;
import com.winlator.cmod.runtime.display.xserver.extensions.PresentExtension;
import com.winlator.cmod.runtime.display.xserver.extensions.SyncExtension;
import com.winlator.cmod.runtime.display.xserver.extensions.XInput2Extension;
import com.winlator.cmod.shared.android.CursorLocker;
import java.nio.charset.Charset;
import java.util.EnumMap;
import java.util.concurrent.locks.ReentrantLock;

public class XServer {
  private static final String SGSR_RESIZE_TAG = "SGSRResize";

  public enum Lockable {
    WINDOW_MANAGER,
    PIXMAP_MANAGER,
    DRAWABLE_MANAGER,
    GRAPHIC_CONTEXT_MANAGER,
    INPUT_DEVICE,
    CURSOR_MANAGER,
    SHMSEGMENT_MANAGER
  }

  public static final short VERSION = 11;
  public static final String VENDOR_NAME = "Elbrus Technologies, LLC";
  public static final Charset LATIN1_CHARSET = Charset.forName("latin1");
  public final SparseArray<Extension> extensions = new SparseArray<>();
  public final ScreenInfo screenInfo;
  public final PixmapManager pixmapManager;
  public final ResourceIDs resourceIDs = new ResourceIDs(128);
  public final GraphicsContextManager graphicsContextManager = new GraphicsContextManager();
  public final SelectionManager selectionManager;
  public final DrawableManager drawableManager;
  public final WindowManager windowManager;
  public final CursorManager cursorManager;
  public final Keyboard keyboard = Keyboard.createKeyboard(this);
  public final Pointer pointer = new Pointer(this);
  public final InputDeviceManager inputDeviceManager;
  public final GrabManager grabManager;
  public final CursorLocker cursorLocker;
  private SHMSegmentManager shmSegmentManager;
  private VulkanRenderer renderer;
  private WinHandler winHandler;
  private final EnumMap<Lockable, ReentrantLock> locks = new EnumMap<>(Lockable.class);
  private boolean relativeMouseMovement = false;
  private boolean pointerCaptureActive = false;
  private boolean simulateTouchScreen = false;
  private boolean isGrabbed = false;
  private XClient grabbingClient = null;
  private final boolean dri3Enabled;

  public XServer(ScreenInfo screenInfo) {
    this(screenInfo, true);
  }

  public XServer(ScreenInfo screenInfo, boolean dri3Enabled) {
    this.screenInfo = screenInfo;
    this.dri3Enabled = dri3Enabled;
    cursorLocker = new CursorLocker(this);
    cursorLocker.setEnabled(!relativeMouseMovement && !pointerCaptureActive);
    for (Lockable lockable : Lockable.values()) locks.put(lockable, new ReentrantLock());

    pixmapManager = new PixmapManager();
    drawableManager = new DrawableManager(this);
    cursorManager = new CursorManager(drawableManager);
    windowManager = new WindowManager(screenInfo, drawableManager);
    selectionManager = new SelectionManager(windowManager);
    inputDeviceManager = new InputDeviceManager(this);
    grabManager = new GrabManager(this);

    DesktopHelper.attachTo(this);
    setupExtensions();
  }

  public boolean isDri3Enabled() {
    return dri3Enabled;
  }

  public boolean isRelativeMouseMovement() {
    return relativeMouseMovement;
  }

  public boolean isPointerCaptureActive() {
    return pointerCaptureActive;
  }

  public void setPointerCaptureActive(boolean pointerCaptureActive) {
    this.pointerCaptureActive = pointerCaptureActive;
    cursorLocker.setEnabled(!relativeMouseMovement && !pointerCaptureActive);
  }

  public void setRelativeMouseMovement(boolean relativeMouseMovement) {
    cursorLocker.setEnabled(!relativeMouseMovement && !pointerCaptureActive);
    this.relativeMouseMovement = relativeMouseMovement;
  }

  public boolean isSimulateTouchScreen() {
    return simulateTouchScreen;
  }

  public void setSimulateTouchScreen(boolean simulateTouchScreen) {
    this.simulateTouchScreen = simulateTouchScreen;
  }

  public VulkanRenderer getRenderer() {
    return renderer;
  }

  public GrabManager getGrabManager() {
    return grabManager;
  }

  public void setRenderer(VulkanRenderer renderer) {
    this.renderer = renderer;
  }

  public WinHandler getWinHandler() {
    return winHandler;
  }

  public void setWinHandler(WinHandler winHandler) {
    this.winHandler = winHandler;
  }

  public SHMSegmentManager getSHMSegmentManager() {
    return shmSegmentManager;
  }

  public void setSHMSegmentManager(SHMSegmentManager shmSegmentManager) {
    this.shmSegmentManager = shmSegmentManager;
  }

  public boolean resizeScreen(ScreenInfo newScreenInfo) {
    if (newScreenInfo == null) return false;
    try (XLock lock = lock(Lockable.WINDOW_MANAGER, Lockable.DRAWABLE_MANAGER, Lockable.INPUT_DEVICE)) {
      String oldScreenInfo = screenInfo.toString();
      Log.i(SGSR_RESIZE_TAG, "resizeScreen requested: current='" + oldScreenInfo +
          "' target='" + newScreenInfo + "'");
      if (screenInfo.width == newScreenInfo.width && screenInfo.height == newScreenInfo.height) {
        Log.i(SGSR_RESIZE_TAG, "resizeScreen no-op: screen already " + oldScreenInfo);
        return false;
      }
      screenInfo.setSize(newScreenInfo);
      windowManager.resizeRootWindow(screenInfo.width, screenInfo.height);
      pointer.setPosition(pointer.getClampedX(), pointer.getClampedY());
      Log.i(SGSR_RESIZE_TAG, "resizeScreen applied: '" + oldScreenInfo + "' -> '" +
          screenInfo + "' pointer=" + pointer.getX() + "," + pointer.getY());
      return true;
    }
  }

  public void stop() {
    cursorLocker.stop();
    renderer = null;
    shmSegmentManager = null;
  }

  private class SingleXLock implements XLock {
    private final ReentrantLock lock;

    private SingleXLock(Lockable lockable) {
      this.lock = locks.get(lockable);
      lock.lock();
    }

    @Override
    public void close() {
      lock.unlock();
    }
  }

  private class MultiXLock implements XLock {
    private final Lockable[] lockables;

    private MultiXLock(Lockable[] lockables) {
      this.lockables = lockables;
      for (Lockable lockable : lockables) locks.get(lockable).lock();
    }

    @Override
    public void close() {
      for (int i = lockables.length - 1; i >= 0; i--) {
        locks.get(lockables[i]).unlock();
      }
    }
  }

  public XLock lock(Lockable lockable) {
    return new SingleXLock(lockable);
  }

  public XLock lock(Lockable... lockables) {
    return new MultiXLock(lockables);
  }

  public XLock lockAll() {
    return new MultiXLock(Lockable.values());
  }

  public Extension getExtensionByName(String name) {
    for (int i = 0; i < extensions.size(); i++) {
      Extension extension = extensions.valueAt(i);
      if (extension.getName().equals(name)) return extension;
    }
    return null;
  }

  public void injectPointerMove(int x, int y) {
    try (XLock lock = lock(Lockable.WINDOW_MANAGER, Lockable.INPUT_DEVICE)) {
      int beforeX = pointer.getX();
      int beforeY = pointer.getY();
      pointer.setPosition(x, y);
      logMoveAbs(beforeX, beforeY, pointer.getX(), pointer.getY());
    }
  }

  // WinMFix: temporary diagnostic — remove before commit.
  private static long lastAbsLogMs = 0;

  private void logMoveAbs(int beforeX, int beforeY, int afterX, int afterY) {
    boolean jump = Math.abs(afterX - beforeX) > 40 || Math.abs(afterY - beforeY) > 40;
    long now = System.currentTimeMillis();
    if (!jump && now - lastAbsLogMs < 300) return;
    lastAbsLogMs = now;
    android.util.Log.i("WinMFix", "ABS ptr=" + beforeX + "," + beforeY + "->" + afterX + "," + afterY
        + " scr=" + screenInfo.width + "x" + screenInfo.height + (jump ? " JUMP" : ""));
  }

  public void injectPointerMoveDelta(int dx, int dy) {
    try (XLock lock = lock(Lockable.WINDOW_MANAGER, Lockable.INPUT_DEVICE)) {
      int beforeX = pointer.getX();
      int beforeY = pointer.getY();
      int x = beforeX + dx;
      int y = beforeY + dy;

      int maxX = screenInfo.width - 1;
      int maxY = screenInfo.height - 1;
      android.graphics.Rect confinement = grabManager.getConfinementBounds();
      if (confinement != null) {
        int minX = Math.max(0, confinement.left);
        int minY = Math.max(0, confinement.top);
        int maxX2 = Math.min(maxX, confinement.right - 1);
        int maxY2 = Math.min(maxY, confinement.bottom - 1);
        x = (int)Mathf.clamp((double)x, (double)minX, (double)maxX2);
        y = (int)Mathf.clamp((double)y, (double)minY, (double)maxY2);
        pointer.setPosition(x, y);
      } else {
        short softMarginX = (short) (screenInfo.width * 0.05f);
        short softMarginY = (short) (screenInfo.height * 0.05f);
        x = (int)Mathf.clamp((double)x, (double)-softMarginX, (double)(screenInfo.width - 1) + softMarginX);
        y = (int)Mathf.clamp((double)y, (double)-softMarginY, (double)(screenInfo.height - 1) + softMarginY);
        pointer.setPosition(x, y);

        int clampedX = x;
        int clampedY = y;
        if (x < 0) clampedX = 0;
        else if (x > screenInfo.width - 1) clampedX = screenInfo.width - 1;
        if (y < 0) clampedY = 0;
        else if (y > screenInfo.height - 1) clampedY = screenInfo.height - 1;
        pointer.setX(clampedX);
        pointer.setY(clampedY);
      }

      logMoveDelta(dx, dy, beforeX, beforeY, confinement);

      XInput2Extension xi = getExtension(XInput2Extension.MAJOR_OPCODE);
      if (xi != null) xi.emitRawMotion(2, (double)dx, (double)dy);
    }
    if (renderer != null) renderer.requestCursorRender();
  }

  // WinMFix: temporary diagnostic — remove before commit.
  private static long lastMoveLogMs = 0;

  private void logMoveDelta(int dx, int dy, int beforeX, int beforeY, android.graphics.Rect confinement) {
    int afterX = pointer.getX();
    int afterY = pointer.getY();
    boolean clipped = afterX != beforeX + dx || afterY != beforeY + dy;
    long now = System.currentTimeMillis();
    long minGap = clipped ? 100 : 300;
    if (now - lastMoveLogMs < minGap) return;
    lastMoveLogMs = now;
    android.util.Log.i("WinMFix", "MOVE d=" + dx + "," + dy
        + " ptr=" + beforeX + "," + beforeY + "->" + afterX + "," + afterY
        + " confine=" + (confinement != null ? confinement.toShortString() : "none")
        + " scr=" + screenInfo.width + "x" + screenInfo.height
        + (clipped ? " CLIP" : ""));
  }

  public void updatePointerForDisplay(int x, int y) {
    try (XLock lock = lock(Lockable.WINDOW_MANAGER, Lockable.INPUT_DEVICE)) {
      pointer.setX(x);
      pointer.setY(y);
    }
    if (renderer != null) renderer.requestCursorRender();
  }

  public void updatePointerForDisplayDelta(int dx, int dy) {
    try (XLock lock = lock(Lockable.WINDOW_MANAGER, Lockable.INPUT_DEVICE)) {
      short softMarginX = (short) (screenInfo.width * 0.05f);
      short softMarginY = (short) (screenInfo.height * 0.05f);
      int x = (int) Mathf.clamp((double)pointer.getX() + dx, (double)-softMarginX, (double)(screenInfo.width - 1) + softMarginX);
      int y = (int) Mathf.clamp((double)pointer.getY() + dy, (double)-softMarginY, (double)(screenInfo.height - 1) + softMarginY);
      pointer.setPosition(x, y);

      int clampedX = x;
      int clampedY = y;
      if (x < 0) clampedX = 0;
      else if (x > screenInfo.width - 1) clampedX = screenInfo.width - 1;
      if (y < 0) clampedY = 0;
      else if (y > screenInfo.height - 1) clampedY = screenInfo.height - 1;
      pointer.setX(clampedX);
      pointer.setY(clampedY);
    }
    if (renderer != null) renderer.requestCursorRender();
  }

  public void injectPointerButtonPress(Pointer.Button buttonCode) {
    try (XLock lock = lock(Lockable.WINDOW_MANAGER, Lockable.INPUT_DEVICE)) {
      pointer.setButton(buttonCode, true);

      XInput2Extension xInput2Extension = getExtension(XInput2Extension.MAJOR_OPCODE);
      if (xInput2Extension != null) xInput2Extension.emitRawButton(2, buttonCode.ordinal() + 1, true);
    }
  }

  public void injectPointerButtonRelease(Pointer.Button buttonCode) {
    try (XLock lock = lock(Lockable.WINDOW_MANAGER, Lockable.INPUT_DEVICE)) {
      pointer.setButton(buttonCode, false);

      XInput2Extension xInput2Extension = getExtension(XInput2Extension.MAJOR_OPCODE);
      if (xInput2Extension != null) xInput2Extension.emitRawButton(2, buttonCode.ordinal() + 1, false);
    }
  }

  public void injectKeyPress(XKeycode xKeycode) {
    injectKeyPress(xKeycode, 0);
  }

  public void injectKeyPress(XKeycode xKeycode, int keysym) {
    try (XLock lock = lock(Lockable.WINDOW_MANAGER, Lockable.INPUT_DEVICE)) {
      keyboard.setKeyPress(xKeycode.id, keysym);
    }
  }

  public void injectKeyRelease(XKeycode xKeycode) {
    try (XLock lock = lock(Lockable.WINDOW_MANAGER, Lockable.INPUT_DEVICE)) {
      keyboard.setKeyRelease(xKeycode.id);
    }
  }

  private void registerExtension(Extension ext, int[] nextEventId, int[] nextErrorId) {
    if (ext.getNumEvents() > 0) {
      ext.setFirstEventId((byte) nextEventId[0]);
      nextEventId[0] += ext.getNumEvents();
    }
    if (ext.getNumErrors() > 0) {
      ext.setFirstErrorId((byte) nextErrorId[0]);
      nextErrorId[0] += ext.getNumErrors();
    }
    extensions.put(ext.getMajorOpcode(), ext);
  }

  private void setupExtensions() {
    int[] nextEventId = {64};
    int[] nextErrorId = {128};
    registerExtension(new BigReqExtension(), nextEventId, nextErrorId);
    registerExtension(new MITSHMExtension(), nextEventId, nextErrorId);
    if (dri3Enabled) {
      registerExtension(new DRI3Extension(), nextEventId, nextErrorId);
    }
    registerExtension(new PresentExtension(), nextEventId, nextErrorId);
    registerExtension(new SyncExtension(), nextEventId, nextErrorId);
    registerExtension(new XInput2Extension(), nextEventId, nextErrorId);
  }

  public <T extends Extension> T getExtension(int opcode) {
    return (T) extensions.get(opcode);
  }

  public synchronized void setGrabbed(boolean grabbed, XClient client) {
    this.isGrabbed = grabbed;
    this.grabbingClient = client;
  }

  public synchronized boolean isGrabbedBy(XClient client) {
    return isGrabbed && grabbingClient == client;
  }
}
