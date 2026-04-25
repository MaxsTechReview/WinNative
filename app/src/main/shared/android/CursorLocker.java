package com.winlator.cmod.shared.android;

import com.winlator.cmod.runtime.display.xserver.XServer;
import com.winlator.cmod.shared.math.Mathf;
import java.util.Timer;
import java.util.TimerTask;

public class CursorLocker extends TimerTask {
  private final XServer xServer;
  private final Timer timer;
  private float damping = 0.25f;
  private short maxDistance;
  private boolean enabled = true;
  private boolean stopped = false;
  private final Object pauseLock = new Object();

  public CursorLocker(XServer xServer) {
    this.xServer = xServer;
    maxDistance = (short) (xServer.screenInfo.width * 0.05f);
    timer = new Timer("CursorLocker", true);
    timer.scheduleAtFixedRate(this, 0, 1000 / 60);
  }

  public short getMaxDistance() {
    return maxDistance;
  }

  public void setMaxDistance(short maxDistance) {
    this.maxDistance = maxDistance;
  }

  public float getDamping() {
    return damping;
  }

  public void setDamping(float damping) {
    this.damping = damping;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    if (stopped) return;
    if (enabled) {
      synchronized (pauseLock) {
        this.enabled = true;
        pauseLock.notifyAll();
      }
    } else this.enabled = enabled;
  }

  public void stop() {
    synchronized (pauseLock) {
      stopped = true;
      enabled = true;
      pauseLock.notifyAll();
    }
    cancel();
    timer.cancel();
    timer.purge();
  }

  @Override
  public void run() {
    synchronized (pauseLock) {
      while (!enabled && !stopped) {
        try {
          pauseLock.wait();
        } catch (InterruptedException e) {
        }
      }
      if (stopped) return;
    }

    short x =
        (short)
            Mathf.clamp(
                xServer.pointer.getX(), -maxDistance, xServer.screenInfo.width + maxDistance);
    short y =
        (short)
            Mathf.clamp(
                xServer.pointer.getY(), -maxDistance, xServer.screenInfo.height + maxDistance);

    if (x < 0) {
      xServer.pointer.setX((short) Math.ceil(x * damping));
    } else if (x >= xServer.screenInfo.width) {
      xServer.pointer.setX(
          (short) Math.floor(xServer.screenInfo.width + (x - xServer.screenInfo.width) * damping));
    }
    if (y < 0) {
      xServer.pointer.setY((short) Math.ceil(y * damping));
    } else if (y >= xServer.screenInfo.height) {
      xServer.pointer.setY(
          (short)
              Math.floor(xServer.screenInfo.height + (y - xServer.screenInfo.height) * damping));
    }
  }
}
