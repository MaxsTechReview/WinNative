package com.winlator.cmod.runtime.display.xserver;

import androidx.collection.ArrayMap;
import com.winlator.cmod.runtime.display.connector.XInputStream;
import com.winlator.cmod.runtime.display.connector.XOutputStream;
import com.winlator.cmod.runtime.display.renderer.GLRenderer;
import com.winlator.cmod.runtime.display.xserver.events.Event;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.locks.LockSupport;

public class XClient implements XResourceManager.OnResourceLifecycleListener {
  private static final long FPS_LIMIT_SPIN_THRESHOLD_NS = 1_500_000L;
  private static final long NANOS_PER_SECOND = 1_000_000_000L;
  public final XServer xServer;
  private boolean authenticated = false;
  public final Integer resourceIDBase;
  private short sequenceNumber = 0;
  private int requestLength;
  private byte requestData;
  private int initialLength;
  private final XInputStream inputStream;
  private final XOutputStream outputStream;
  private final ArrayMap<Window, EventListener> eventListeners = new ArrayMap<>();
  private final ArrayList<XResource> resources = new ArrayList<>();
  private long nextFrameTimeNanos = 0;
  private long frameTimeRemainderNanos = 0;

  public XClient(XServer xServer, XInputStream inputStream, XOutputStream outputStream) {

    this.xServer = xServer;
    this.inputStream = inputStream;
    this.outputStream = outputStream;

    try (XLock lock = xServer.lockAll()) {
      resourceIDBase = xServer.resourceIDs.get();
      xServer.windowManager.addOnResourceLifecycleListener(this);
      xServer.pixmapManager.addOnResourceLifecycleListener(this);
      xServer.graphicsContextManager.addOnResourceLifecycleListener(this);
      xServer.cursorManager.addOnResourceLifecycleListener(this);
    }
  }

  public void registerAsOwnerOfResource(XResource resource) {
    resources.add(resource);
  }

  public void setEventListenerForWindow(Window window, Bitmask eventMask) {
    EventListener eventListener = eventListeners.get(window);
    if (eventListener != null) window.removeEventListener(eventListener);
    if (eventMask.isEmpty()) return;
    eventListener = new EventListener(this, eventMask);
    eventListeners.put(window, eventListener);
    window.addEventListener(eventListener);
  }

  public void sendEvent(Event event) {
    try {
      event.send(sequenceNumber, outputStream);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public boolean isInterestedIn(int eventId, Window window) {
    EventListener eventListener = eventListeners.get(window);
    return eventListener != null && eventListener.isInterestedIn(eventId);
  }

  public boolean isAuthenticated() {
    return authenticated;
  }

  public void setAuthenticated(boolean authenticated) {
    this.authenticated = authenticated;
  }

  public void freeResources() {
    try (XLock lock = xServer.lockAll()) {
      while (!resources.isEmpty()) {
        XResource resource = resources.remove(resources.size() - 1);
        if (resource instanceof Window) {
          xServer.windowManager.destroyWindow(resource.id);
        } else if (resource instanceof Pixmap) {
          xServer.pixmapManager.freePixmap(resource.id);
        } else if (resource instanceof GraphicsContext) {
          xServer.graphicsContextManager.freeGraphicsContext(resource.id);
        } else if (resource instanceof Cursor) {
          xServer.cursorManager.freeCursor(resource.id);
        }
      }

      while (!eventListeners.isEmpty()) {
        int i = eventListeners.size() - 1;
        eventListeners.keyAt(i).removeEventListener(eventListeners.removeAt(i));
      }

      xServer.windowManager.removeOnResourceLifecycleListener(this);
      xServer.pixmapManager.removeOnResourceLifecycleListener(this);
      xServer.graphicsContextManager.removeOnResourceLifecycleListener(this);
      xServer.cursorManager.removeOnResourceLifecycleListener(this);
      xServer.resourceIDs.free(resourceIDBase);
    }
  }

  public void generateSequenceNumber() {
    sequenceNumber++;
  }

  public short getSequenceNumber() {
    return sequenceNumber;
  }

  public int getRequestLength() {
    return requestLength;
  }

  public void setRequestLength(int requestLength) {
    this.requestLength = requestLength;
    initialLength = inputStream.available();
  }

  public byte getRequestData() {
    return requestData;
  }

  public void setRequestData(byte requestData) {
    this.requestData = requestData;
  }

  public int getRemainingRequestLength() {
    int actualLength = initialLength - inputStream.available();
    return requestLength - actualLength;
  }

  public void skipRequest() {
    inputStream.skip(getRemainingRequestLength());
  }

  public XInputStream getInputStream() {
    return inputStream;
  }

  public XOutputStream getOutputStream() {
    return outputStream;
  }

  public Bitmask getEventMaskForWindow(Window window) {
    EventListener eventListener = eventListeners.get(window);
    return eventListener != null ? eventListener.eventMask : new Bitmask();
  }

  @Override
  public void onFreeResource(XResource resource) {
    if (resource instanceof Window) eventListeners.remove(resource);
    resources.remove(resource);
  }

  public boolean isValidResourceId(int id) {
    return xServer.resourceIDs.isInInterval(id, resourceIDBase);
  }

  public void enforceAbsoluteFramerate() {
    GLRenderer renderer = xServer.getRenderer();
    if (renderer == null) return;

    int targetFps = renderer.getFpsLimit();
    if (targetFps <= 0) {
      nextFrameTimeNanos = 0;
      frameTimeRemainderNanos = 0;
      return;
    }

    long now = System.nanoTime();
    if (nextFrameTimeNanos == 0 || now > nextFrameTimeNanos) {
      nextFrameTimeNanos = now;
      frameTimeRemainderNanos = 0;
    }

    long deadlineNanos = nextFrameTimeNanos;
    long targetFrameTime = NANOS_PER_SECOND / targetFps;
    frameTimeRemainderNanos += NANOS_PER_SECOND % targetFps;
    if (frameTimeRemainderNanos >= targetFps) {
      targetFrameTime++;
      frameTimeRemainderNanos -= targetFps;
    }
    nextFrameTimeNanos += targetFrameTime;

    waitUntilFrameDeadline(deadlineNanos);
  }

  private void waitUntilFrameDeadline(long deadlineNanos) {
    long sleepTime = deadlineNanos - System.nanoTime();
    while (sleepTime > FPS_LIMIT_SPIN_THRESHOLD_NS) {
      LockSupport.parkNanos(sleepTime - FPS_LIMIT_SPIN_THRESHOLD_NS);
      sleepTime = deadlineNanos - System.nanoTime();
    }

    while (System.nanoTime() < deadlineNanos) {
      // Busy-wait during the final margin for sub-millisecond pacing precision.
    }
  }
}
