package com.winlator.cmod.runtime.input.rumble;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.InputDevice;
import java.util.ArrayList;
import java.util.List;

public class GamepadRumbleManager {
  /** GameSir / Guangzhou Chicken Run USB vendor id (0x3537). */
  public static final int GAMESIR_VENDOR_ID = 13623;

  private static final int MAX_CONTROLLERS = 4;

  private final Handler handler;
  private final List<GamepadRumbleDriver> drivers = new ArrayList<>();
  private final Runnable[] pendingStops = new Runnable[MAX_CONTROLLERS];
  private final GamepadRumbleDriver[] activeDrivers = new GamepadRumbleDriver[MAX_CONTROLLERS];

  private GcmRumbleMode mode = GcmRumbleMode.DISABLED;

  public GamepadRumbleManager(Context context, Handler handler) {
    this.handler = handler;
    // USB driver first so it wins over BLE for USB-rumble models in ALL mode.
    this.drivers.add(new GameSirG8UsbRumbleDriver(context, handler));
    this.drivers.add(new GameSirX5sBleRumbleDriver(context, handler));
  }

  public void setMode(GcmRumbleMode mode) {
    this.mode = mode;
    for (GamepadRumbleDriver driver : drivers) {
      driver.setMode(mode);
    }
    if (mode != GcmRumbleMode.DISABLED) {
      requestPermissionIfNeeded();
    } else {
      stopAll();
    }
  }

  public void requestPermissionIfNeeded() {
    if (mode == GcmRumbleMode.DISABLED) {
      return;
    }
    for (GamepadRumbleDriver driver : drivers) {
      driver.requestPermissionIfNeeded();
    }
  }

  public boolean handleRumble(
      int slot, InputDevice device, int strong, int weak, int durationMs) {
    if (mode == GcmRumbleMode.DISABLED || slot < 0 || slot >= MAX_CONTROLLERS || device == null) {
      return false;
    }

    GamepadRumbleDriver driver = findDriver(device, mode);
    if (driver == null) {
      return false;
    }

    boolean handled = driver.rumble(slot, strong, weak);
    if (!handled) {
      return false;
    }

    activeDrivers[slot] = driver;
    clearPendingStop(slot);

    if ((strong > 0 || weak > 0) && durationMs > 0 && durationMs < 0xffff) {
      Runnable stop =
          () -> {
            driver.rumble(slot, 0, 0);
            if (activeDrivers[slot] == driver) {
              activeDrivers[slot] = null;
            }
            pendingStops[slot] = null;
          };
      pendingStops[slot] = stop;
      handler.postDelayed(stop, durationMs);
    }
    return true;
  }

  public void stopAll() {
    for (int slot = 0; slot < MAX_CONTROLLERS; slot++) {
      clearPendingStop(slot);
      GamepadRumbleDriver driver = activeDrivers[slot];
      if (driver != null) {
        driver.rumble(slot, 0, 0);
        activeDrivers[slot] = null;
      }
    }
  }

  private GamepadRumbleDriver findDriver(InputDevice device, GcmRumbleMode currentMode) {
    for (GamepadRumbleDriver driver : drivers) {
      if (driver.supports(device, currentMode)) {
        return driver;
      }
    }
    Log.d(
        "WinHandler",
        "No vendor rumble driver for device="
            + device.getName()
            + " vendorId="
            + device.getVendorId()
            + " productId="
            + device.getProductId());
    return null;
  }

  private void clearPendingStop(int slot) {
    Runnable pendingStop = pendingStops[slot];
    if (pendingStop != null) {
      handler.removeCallbacks(pendingStop);
      pendingStops[slot] = null;
    }
  }
}
