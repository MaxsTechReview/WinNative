package com.winlator.cmod.runtime.input.rumble;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.InputDevice;

public class GameSirG8UsbRumbleDriver implements GamepadRumbleDriver {
  private static final int VENDOR_ID = 13623;
  private static final int G8_MFI_PRODUCT_ID = 274;
  // X3 Pro enumerates over USB but rumbles over BLE, so the BLE driver must own it.
  private static final int X3_PRO_PRODUCT_ID = 0x0106;
  private static final int VENDOR_INTERFACE_ID = 1;
  private static final int USB_WRITE_TIMEOUT_MS = 2000;
  private static final int RUMBLE_DEADZONE_RAW = 1;
  private static final int MAX_CONTROLLERS = 4;

  private final Context context;
  private final Handler handler;
  private final int[] lastLeftPower = new int[] {-1, -1, -1, -1};
  private final int[] lastRightPower = new int[] {-1, -1, -1, -1};

  private boolean permissionRequested;
  private String requestedDeviceName;
  private GcmRumbleMode mode = GcmRumbleMode.DISABLED;

  public GameSirG8UsbRumbleDriver(Context context, Handler handler) {
    this.context = context;
    this.handler = handler;
  }

  @Override
  public String getName() {
    return "GameSir G8+ USB";
  }

  @Override
  public void setMode(GcmRumbleMode mode) {
    this.mode = mode;
  }

  @Override
  public boolean supports(InputDevice device, GcmRumbleMode mode) {
    if (device == null || mode == GcmRumbleMode.DISABLED) {
      return false;
    }
    if (device.getVendorId() != VENDOR_ID) {
      return false;
    }
    if (device.getProductId() == X3_PRO_PRODUCT_ID) {
      return false; // X3 Pro rumbles over BLE; let the BLE driver handle it
    }
    if (mode == GcmRumbleMode.ALL) {
      // Only claim if a USB device with this VID/PID is present (don't steal BLE-only models).
      UsbManager usbManager = getUsbManager();
      if (usbManager == null) return false;
      for (UsbDevice usbDevice : usbManager.getDeviceList().values()) {
        if (usbDevice.getVendorId() == device.getVendorId()
            && usbDevice.getProductId() == device.getProductId()) {
          return true;
        }
      }
      return false;
    }
    // KNOWN: G8+ MFi by PID + name
    String name = device.getName();
    return device.getProductId() == G8_MFI_PRODUCT_ID && name != null && name.contains("GameSir");
  }

  @Override
  public void requestPermissionIfNeeded() {
    UsbManager usbManager = getUsbManager();
    if (usbManager == null) {
      return;
    }
    UsbDevice device = findDevice(usbManager);
    if (device != null && !usbManager.hasPermission(device)) {
      requestPermission(usbManager, device);
    }
  }

  @Override
  public boolean rumble(int slot, int strong, int weak) {
    if (slot < 0 || slot >= MAX_CONTROLLERS) {
      return false;
    }

    int leftPower = scaleMotor(strong);
    int rightPower = scaleMotor(weak);
    if (lastLeftPower[slot] == leftPower && lastRightPower[slot] == rightPower) {
      Log.d(
          "WinHandler",
          "GameSir USB rumble unchanged slot="
              + slot
              + " rawStrong="
              + strong
              + " rawWeak="
              + weak
              + " left="
              + leftPower
              + " right="
              + rightPower);
      return true;
    }

    boolean sent = sendRumble(slot, strong, weak, leftPower, rightPower);
    if (sent) {
      lastLeftPower[slot] = leftPower;
      lastRightPower[slot] = rightPower;
    }
    return sent;
  }

  private int scaleMotor(int value) {
    if (value <= RUMBLE_DEADZONE_RAW) {
      return 0;
    }
    return Math.min(255, Math.max(1, Math.round((value / 65535.0f) * 255.0f)));
  }

  private boolean sendRumble(int slot, int rawStrong, int rawWeak, int leftPower, int rightPower) {
    UsbManager usbManager = getUsbManager();
    if (usbManager == null) {
      return false;
    }

    UsbDevice device = findDevice(usbManager);
    if (device == null) {
      resetPermissionRequest();
      return false;
    }
    if (!usbManager.hasPermission(device)) {
      requestPermission(usbManager, device);
      Log.d(
          "WinHandler",
          "GameSir USB rumble waiting for permission slot="
              + slot
              + " rawStrong="
              + rawStrong
              + " rawWeak="
              + rawWeak);
      return true;
    }

    UsbInterface usbInterface = findInterfaceById(device, VENDOR_INTERFACE_ID);
    UsbEndpoint outEndpoint = usbInterface != null ? findInterruptOutEndpoint(usbInterface) : null;
    if (usbInterface == null || outEndpoint == null) {
      return false;
    }

    UsbDeviceConnection connection = usbManager.openDevice(device);
    if (connection == null) {
      return false;
    }

    byte[] command = buildInstantVibrationCommand(leftPower, rightPower);
    try {
      boolean claimed = connection.claimInterface(usbInterface, true);
      if (!claimed) {
        return false;
      }
      int written =
          connection.bulkTransfer(outEndpoint, command, command.length, USB_WRITE_TIMEOUT_MS);
      Log.d(
          "WinHandler",
          "GameSir USB rumble written="
              + written
              + " slot="
              + slot
              + " rawStrong="
              + rawStrong
              + " rawWeak="
              + rawWeak
              + " left="
              + leftPower
              + " right="
              + rightPower);
      return written == command.length;
    } finally {
      try {
        connection.releaseInterface(usbInterface);
      } catch (Exception ignored) {
      }
      connection.close();
    }
  }

  private UsbManager getUsbManager() {
    return (UsbManager) context.getSystemService(Context.USB_SERVICE);
  }

  private UsbDevice findDevice(UsbManager usbManager) {
    for (UsbDevice device : usbManager.getDeviceList().values()) {
      if (device.getVendorId() != VENDOR_ID) continue;
      if (mode == GcmRumbleMode.ALL || device.getProductId() == G8_MFI_PRODUCT_ID) {
        return device;
      }
    }
    return null;
  }

  private void requestPermission(UsbManager usbManager, UsbDevice device) {
    if (device == null || usbManager.hasPermission(device)) {
      resetPermissionRequest();
      return;
    }
    if (permissionRequested && device.getDeviceName().equals(requestedDeviceName)) {
      return;
    }

    permissionRequested = true;
    requestedDeviceName = device.getDeviceName();
    String action = context.getPackageName() + ".GAMESIR_USB_PERMISSION";
    BroadcastReceiver receiver =
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context receiverContext, Intent intent) {
            if (!action.equals(intent.getAction())) {
              return;
            }
            UsbDevice grantedDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            boolean granted =
                intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            boolean hasPermissionNow =
                grantedDevice != null
                    ? usbManager.hasPermission(grantedDevice)
                    : hasPermission(usbManager);
            Log.d(
                "WinHandler",
                "GameSir USB permission result granted="
                    + granted
                    + " hasPermissionNow="
                    + hasPermissionNow);
            // Always reset so a re-enumerated device (new /dev/bus/usb address) gets a fresh request.
            resetPermissionRequest();
            try {
              context.unregisterReceiver(this);
            } catch (IllegalArgumentException ignored) {
            }
          }
        };

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.registerReceiver(receiver, new IntentFilter(action), Context.RECEIVER_NOT_EXPORTED);
    } else {
      context.registerReceiver(receiver, new IntentFilter(action));
    }

    int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0;
    PendingIntent pendingIntent =
        PendingIntent.getBroadcast(
            context, 1, new Intent(action).setPackage(context.getPackageName()), flags);
    handler.post(() -> usbManager.requestPermission(device, pendingIntent));
    Log.d(
        "WinHandler",
        "GameSir USB permission requested for "
            + device.getDeviceName()
            + " vendorId="
            + device.getVendorId()
            + " productId="
            + device.getProductId());
  }

  private boolean hasPermission(UsbManager usbManager) {
    UsbDevice device = findDevice(usbManager);
    return device != null && usbManager.hasPermission(device);
  }

  private void resetPermissionRequest() {
    permissionRequested = false;
    requestedDeviceName = null;
  }

  private UsbInterface findInterfaceById(UsbDevice device, int interfaceId) {
    for (int i = 0; i < device.getInterfaceCount(); i++) {
      UsbInterface usbInterface = device.getInterface(i);
      if (usbInterface.getId() == interfaceId) {
        return usbInterface;
      }
    }
    return null;
  }

  private UsbEndpoint findInterruptOutEndpoint(UsbInterface usbInterface) {
    for (int e = 0; e < usbInterface.getEndpointCount(); e++) {
      UsbEndpoint endpoint = usbInterface.getEndpoint(e);
      if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT
          && endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_INT) {
        return endpoint;
      }
    }
    return null;
  }

  private byte[] buildInstantVibrationCommand(int leftPower, int rightPower) {
    byte[] command = new byte[9];
    command[0] = 0x04;
    command[1] = (byte) Math.min(255, Math.max(0, leftPower));
    command[2] = (byte) (leftPower > 0 ? 4 : 0);
    command[3] = (byte) Math.min(255, Math.max(0, rightPower));
    command[4] = (byte) (rightPower > 0 ? 4 : 0);
    command[5] = 0;
    command[6] = 0;
    command[7] = 0;
    command[8] = 0;
    return command;
  }
}
