package com.winlator.cmod.runtime.display.xserver.extensions;

import static com.winlator.cmod.runtime.display.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import android.util.SparseArray;
import com.winlator.cmod.runtime.display.connector.XInputStream;
import com.winlator.cmod.runtime.display.connector.XOutputStream;
import com.winlator.cmod.runtime.display.connector.XStreamLock;
import com.winlator.cmod.runtime.display.renderer.GPUImage;
import com.winlator.cmod.runtime.display.renderer.Texture;
import com.winlator.cmod.runtime.display.xserver.Bitmask;
import com.winlator.cmod.runtime.display.xserver.Drawable;
import com.winlator.cmod.runtime.display.xserver.Pixmap;
import com.winlator.cmod.runtime.display.xserver.Window;
import com.winlator.cmod.runtime.display.xserver.WindowManager;
import com.winlator.cmod.runtime.display.xserver.XClient;
import com.winlator.cmod.runtime.display.xserver.XLock;
import com.winlator.cmod.runtime.display.xserver.XResource;
import com.winlator.cmod.runtime.display.xserver.XResourceManager;
import com.winlator.cmod.runtime.display.xserver.XServer;
import com.winlator.cmod.runtime.display.xserver.errors.BadImplementation;
import com.winlator.cmod.runtime.display.xserver.errors.BadMatch;
import com.winlator.cmod.runtime.display.xserver.errors.BadPixmap;
import com.winlator.cmod.runtime.display.xserver.errors.BadWindow;
import com.winlator.cmod.runtime.display.xserver.errors.XRequestError;
import com.winlator.cmod.runtime.display.xserver.events.PresentCompleteNotify;
import com.winlator.cmod.runtime.display.xserver.events.PresentIdleNotify;
import java.io.IOException;

public class PresentExtension
    implements Extension,
        XResourceManager.OnResourceLifecycleListener,
        WindowManager.OnWindowModificationListener {
  public static final byte MAJOR_OPCODE = -103;
  private static final int FAKE_INTERVAL = 1000000 / 60;

  public enum Kind {
    PIXMAP,
    MSC_NOTIFY
  }

  public enum Mode {
    COPY,
    FLIP,
    SKIP
  }

  private final SparseArray<Event> events = new SparseArray<>();
  private final SparseArray<PendingScanout> pendingScanouts = new SparseArray<>();
  private SyncExtension syncExtension;
  private boolean lifecycleListenersRegistered = false;

  private abstract static class ClientOpcodes {
    private static final byte QUERY_VERSION = 0;
    private static final byte PRESENT_PIXMAP = 1;
    private static final byte SELECT_INPUT = 3;
  }

  private static class Event {
    private Window window;
    private XClient client;
    private int id;
    private Bitmask mask;
  }

  private static class PendingScanout {
    private Window window;
    private Pixmap pixmap;
    private int serial;
    private int idleFence;
  }

  @Override
  public String getName() {
    return "Present";
  }

  @Override
  public byte getMajorOpcode() {
    return MAJOR_OPCODE;
  }

  @Override
  public byte getFirstErrorId() {
    return 0;
  }

  @Override
  public byte getFirstEventId() {
    return 0;
  }

  private void sendIdleNotify(Window window, Pixmap pixmap, int serial, int idleFence) {
    if (idleFence != 0) syncExtension.setTriggered(idleFence);

    synchronized (events) {
      for (int i = 0; i < events.size(); i++) {
        Event event = events.valueAt(i);
        if (event.window == window && event.mask.isSet(PresentIdleNotify.getEventMask())) {
          event.client.sendEvent(
              new PresentIdleNotify(event.id, window, pixmap, serial, idleFence));
        }
      }
    }
  }

  private void sendCompleteNotify(
      Window window, int serial, Kind kind, Mode mode, long ust, long msc) {
    synchronized (events) {
      for (int i = 0; i < events.size(); i++) {
        Event event = events.valueAt(i);
        if (event.window == window && event.mask.isSet(PresentCompleteNotify.getEventMask())) {
          event.client.sendEvent(
              new PresentCompleteNotify(event.id, window, serial, kind, mode, ust, msc));
        }
      }
    }
  }

  private static void queryVersion(
      XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    inputStream.skip(8);

    try (XStreamLock lock = outputStream.lock()) {
      outputStream.writeByte(RESPONSE_CODE_SUCCESS);
      outputStream.writeByte((byte) 0);
      outputStream.writeShort(client.getSequenceNumber());
      outputStream.writeInt(0);
      outputStream.writeInt(1);
      outputStream.writeInt(0);
      outputStream.writePad(16);
    }
  }

  private void presentPixmap(XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    int windowId = inputStream.readInt();
    int pixmapId = inputStream.readInt();
    int serial = inputStream.readInt();
    inputStream.skip(8);
    short xOff = inputStream.readShort();
    short yOff = inputStream.readShort();
    inputStream.skip(8);
    int idleFence = inputStream.readInt();
    inputStream.skip(client.getRemainingRequestLength());

    final Window window = client.xServer.windowManager.getWindow(windowId);
    if (window == null) throw new BadWindow(windowId);

    final Pixmap pixmap = client.xServer.pixmapManager.getPixmap(pixmapId);
    if (pixmap == null) throw new BadPixmap(pixmapId);

    Drawable content = window.getContent();
    if (content.visual.depth != pixmap.drawable.visual.depth) throw new BadMatch();

    long ust = System.nanoTime() / 1000;
    long msc = ust / FAKE_INTERVAL;

    synchronized (content.renderLock) {
      Mode mode;
      if (canDirectScanout(content, pixmap.drawable, xOff, yOff)) {
        releasePendingScanout(window);
        content.setScanoutSource(pixmap.drawable);
        PendingScanout pendingScanout = new PendingScanout();
        pendingScanout.window = window;
        pendingScanout.pixmap = pixmap;
        pendingScanout.serial = serial;
        pendingScanout.idleFence = idleFence;
        pendingScanouts.put(window.id, pendingScanout);
        mode = Mode.FLIP;
      } else {
        releasePendingScanout(window);
        content.copyArea(
            (short) 0,
            (short) 0,
            xOff,
            yOff,
            pixmap.drawable.width,
            pixmap.drawable.height,
            pixmap.drawable);
        sendIdleNotify(window, pixmap, serial, idleFence);
        mode = Mode.COPY;
      }
      sendCompleteNotify(window, serial, Kind.PIXMAP, mode, ust, msc);
      client.xServer.windowManager.triggerOnFramePresented(window);
    }
  }

  private void releasePendingScanout(Window window) {
    PendingScanout pendingScanout = pendingScanouts.get(window.id);
    if (pendingScanout == null) return;

    pendingScanouts.remove(window.id);
    Drawable content = window.getContent();
    if (content != null) {
      synchronized (content.renderLock) {
        if (content.getScanoutSource() == pendingScanout.pixmap.drawable) {
          content.clearScanoutSource();
        }
      }
    }
    sendIdleNotify(
        pendingScanout.window,
        pendingScanout.pixmap,
        pendingScanout.serial,
        pendingScanout.idleFence);
  }

  private void releasePendingScanoutsForPixmap(Pixmap pixmap) {
    for (int i = pendingScanouts.size() - 1; i >= 0; i--) {
      PendingScanout pendingScanout = pendingScanouts.valueAt(i);
      if (pendingScanout.pixmap == pixmap) {
        releasePendingScanout(pendingScanout.window);
      }
    }
  }

  private void removeEventsForWindow(Window window) {
    synchronized (events) {
      for (int i = events.size() - 1; i >= 0; i--) {
        if (events.valueAt(i).window == window) events.removeAt(i);
      }
    }
  }

  private void registerLifecycleListeners(XServer xServer) {
    if (lifecycleListenersRegistered) return;
    synchronized (this) {
      if (lifecycleListenersRegistered) return;
      xServer.pixmapManager.addOnResourceLifecycleListener(this);
      xServer.windowManager.addOnWindowModificationListener(this);
      lifecycleListenersRegistered = true;
    }
  }

  @Override
  public void onFreeResource(XResource resource) {
    if (resource instanceof Pixmap) {
      releasePendingScanoutsForPixmap((Pixmap) resource);
    }
  }

  @Override
  public void onDestroyWindow(Window window) {
    releasePendingScanout(window);
    removeEventsForWindow(window);
  }

  private boolean canDirectScanout(Drawable content, Drawable pixmap, short xOff, short yOff) {
    Texture texture = pixmap.getTexture();
    if (texture instanceof GPUImage) {
      GPUImage gpuImage = (GPUImage) texture;
      if (!gpuImage.isValid() || gpuImage.hasSamplingFailed()) return false;
    }

    return xOff == 0
        && yOff == 0
        && pixmap.isDirectScanout()
        && texture != null
        && pixmap.width >= content.width
        && pixmap.height >= content.height;
  }

  private void selectInput(XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    int eventId = inputStream.readInt();
    int windowId = inputStream.readInt();
    Bitmask mask = new Bitmask(inputStream.readInt());

    Window window = client.xServer.windowManager.getWindow(windowId);
    if (window == null) throw new BadWindow(windowId);

    if (client.xServer.isDri3Enabled() && GPUImage.isSupported() && !mask.isEmpty()) {
      Drawable content = window.getContent();
      if (content != null) {
        GPUImage gpuImage = new GPUImage(content.width, content.height);
        synchronized (content.renderLock) {
          if (gpuImage.isValid()) {
            final Texture oldTexture = content.getTexture();
            if (oldTexture != null && client.xServer.getRenderer() != null)
              client.xServer.getRenderer().xServerView.queueEvent(oldTexture::destroy);
            content.setTexture(gpuImage);
          } else {
            gpuImage.destroy();
          }
        }
      }
    }

    synchronized (events) {
      Event event = events.get(eventId);
      if (event != null) {
        if (event.window != window || event.client != client) throw new BadMatch();

        if (!mask.isEmpty()) {
          event.mask = mask;
        } else events.remove(eventId);
      } else {
        event = new Event();
        event.id = eventId;
        event.window = window;
        event.client = client;
        event.mask = mask;
        events.put(eventId, event);
      }
    }
  }

  @Override
  public void handleRequest(XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    registerLifecycleListeners(client.xServer);
    int opcode = client.getRequestData();
    if (syncExtension == null)
      syncExtension = client.xServer.getExtension(SyncExtension.MAJOR_OPCODE);

    switch (opcode) {
      case ClientOpcodes.QUERY_VERSION:
        queryVersion(client, inputStream, outputStream);
        break;
      case ClientOpcodes.PRESENT_PIXMAP:
        int pixmapId;
        boolean isLargeFrame = false;
        try (XLock lock =
            client.xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.PIXMAP_MANAGER)) {
          int windowId = inputStream.readInt();
          pixmapId = inputStream.readInt();
          int serial = inputStream.readInt();
          inputStream.skip(8);
          short xOff = inputStream.readShort();
          short yOff = inputStream.readShort();
          inputStream.skip(8);
          int idleFence = inputStream.readInt();
          inputStream.skip(client.getRemainingRequestLength());

          final Window window = client.xServer.windowManager.getWindow(windowId);
          if (window == null) throw new BadWindow(windowId);

          final Pixmap pixmap = client.xServer.pixmapManager.getPixmap(pixmapId);
          if (pixmap == null) throw new BadPixmap(pixmapId);

          Drawable content = window.getContent();
          if (content.visual.depth != pixmap.drawable.visual.depth) throw new BadMatch();

          long ust = System.nanoTime() / 1000;
          long msc = ust / FAKE_INTERVAL;

          synchronized (content.renderLock) {
            Mode mode;
            if (canDirectScanout(content, pixmap.drawable, xOff, yOff)) {
              releasePendingScanout(window);
              content.setScanoutSource(pixmap.drawable);
              PendingScanout pendingScanout = new PendingScanout();
              pendingScanout.window = window;
              pendingScanout.pixmap = pixmap;
              pendingScanout.serial = serial;
              pendingScanout.idleFence = idleFence;
              pendingScanouts.put(window.id, pendingScanout);
              mode = Mode.FLIP;
            } else {
              releasePendingScanout(window);
              content.copyArea(
                  (short) 0,
                  (short) 0,
                  xOff,
                  yOff,
                  pixmap.drawable.width,
                  pixmap.drawable.height,
                  pixmap.drawable);
              sendIdleNotify(window, pixmap, serial, idleFence);
              mode = Mode.COPY;
            }
            sendCompleteNotify(window, serial, Kind.PIXMAP, mode, ust, msc);
            client.xServer.windowManager.triggerOnFramePresented(window);
          }

          if (pixmap.drawable.width > client.xServer.screenInfo.width / 2) {
            isLargeFrame = true;
          }
        }

        if (client.xServer.getRenderer() != null)
          client.xServer.getRenderer().requestRenderCoalesced();

        if (isLargeFrame) {
          client.enforceAbsoluteFramerate();
        }
        break;
      case ClientOpcodes.SELECT_INPUT:
        try (XLock lock = client.xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
          selectInput(client, inputStream, outputStream);
        }
        break;
      default:
        throw new BadImplementation();
    }
  }
}
