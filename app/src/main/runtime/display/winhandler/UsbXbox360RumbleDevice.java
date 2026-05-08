package com.winlator.cmod.runtime.display.winhandler;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.util.Log;

final class UsbXbox360RumbleDevice implements UsbRumbleDevice {
  private static final String TAG = "UsbXbox360Rumble";
  private static final int XBOX360_IFACE_SUBCLASS = 93;
  private static final int XBOX360_IFACE_PROTOCOL = 1;

  private final UsbDevice device;
  private final UsbDeviceConnection connection;
  private UsbInterface claimedInterface;
  private UsbEndpoint outEndpoint;

  UsbXbox360RumbleDevice(UsbDevice device, UsbDeviceConnection connection) {
    this.device = device;
    this.connection = connection;
  }

  static boolean canClaimDevice(UsbDevice device) {
    if (device == null) {
      return false;
    }

    for (int i = 0; i < device.getInterfaceCount(); i++) {
      UsbInterface usbInterface = device.getInterface(i);
      if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_VENDOR_SPEC
          && usbInterface.getInterfaceSubclass() == XBOX360_IFACE_SUBCLASS
          && usbInterface.getInterfaceProtocol() == XBOX360_IFACE_PROTOCOL
          && findOutEndpoint(usbInterface) != null) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean start() {
    if (this.claimedInterface != null && this.outEndpoint != null) {
      return true;
    }

    for (int i = 0; i < this.device.getInterfaceCount(); i++) {
      UsbInterface usbInterface = this.device.getInterface(i);
      if (usbInterface.getInterfaceClass() != UsbConstants.USB_CLASS_VENDOR_SPEC
          || usbInterface.getInterfaceSubclass() != XBOX360_IFACE_SUBCLASS
          || usbInterface.getInterfaceProtocol() != XBOX360_IFACE_PROTOCOL) {
        continue;
      }

      UsbEndpoint endpoint = findOutEndpoint(usbInterface);
      if (endpoint == null) {
        continue;
      }

      if (!this.connection.claimInterface(usbInterface, true)) {
        Log.w(TAG, "Failed to claim interface " + usbInterface.getId() + " for " + this.device.getDeviceName());
        continue;
      }

      this.claimedInterface = usbInterface;
      this.outEndpoint = endpoint;
      Log.i(
          TAG,
          "Claimed Xbox360-style USB rumble path for "
              + this.device.getDeviceName()
              + " iface="
              + usbInterface.getId()
              + " endpoint=0x"
              + Integer.toHexString(endpoint.getAddress()));
      return true;
    }

    Log.w(TAG, "No Xbox360-style rumble interface found for " + this.device.getDeviceName());
    return false;
  }

  @Override
  public void rumble(int strong, int weak) {
    if (this.outEndpoint == null || this.claimedInterface == null) {
      return;
    }

    byte[] data = {
      0x00,
      0x08,
      0x00,
      (byte) ((strong & 0xFFFF) >> 8),
      (byte) ((weak & 0xFFFF) >> 8),
      0x00,
      0x00,
      0x00
    };
    int transferred = this.connection.bulkTransfer(this.outEndpoint, data, data.length, 100);
    if (transferred != data.length) {
      Log.w(
          TAG,
          "Rumble transfer failed for "
              + this.device.getDeviceName()
              + ": transferred="
              + transferred
              + " expected="
              + data.length);
    }
  }

  @Override
  public void stop() {
    if (this.claimedInterface != null) {
      try {
        rumble(0, 0);
      } catch (Exception ignored) {
      }
      try {
        this.connection.releaseInterface(this.claimedInterface);
      } catch (Exception ignored) {
      }
    }

    try {
      this.connection.close();
    } catch (Exception ignored) {
    }

    this.claimedInterface = null;
    this.outEndpoint = null;
  }

  private static UsbEndpoint findOutEndpoint(UsbInterface usbInterface) {
    for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
      UsbEndpoint endpoint = usbInterface.getEndpoint(i);
      if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
        return endpoint;
      }
    }
    return null;
  }
}
