package com.winlator.cmod.runtime.display.winhandler;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Log;
import android.view.InputDevice;
import com.winlator.cmod.runtime.display.XServerDisplayActivity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class UsbControllerManager {
  private static final String TAG = "UsbControllerManager";
  private static final int RAZER_VENDOR_ID = 0x1532;
  private static final Set<Integer> KNOWN_RAZER_PRODUCTS =
      new LinkedHashSet<>(Arrays.asList(0x0037, 0x0718, 0x0719, 0x071A, 0x0721, 0x0724, 0x0727));

  private final XServerDisplayActivity activity;
  private final UsbManager usbManager;
  private final String permissionAction;
  private final Set<String> requestedKeys = new LinkedHashSet<>();
  private final Map<String, UsbRumbleDevice> rumbleDevices = new LinkedHashMap<>();
  private final BroadcastReceiver receiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          String action = intent.getAction();
          if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (isTargetController(device)) {
              logDeviceSummary(device, "attached");
              ensurePermission(device, "attached");
            }
            return;
          }

          if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device != null) {
              requestedKeys.remove(buildDeviceKey(device));
              removeRumbleDevice(device);
              Log.i(TAG, "USB device detached: " + describeDevice(device));
            }
            return;
          }

          if (!permissionAction.equals(action)) {
            return;
          }

          UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
          if (device == null) {
            return;
          }

          requestedKeys.remove(buildDeviceKey(device));
          boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
          if (!granted) {
            Log.w(TAG, "USB permission denied for " + describeDevice(device));
            return;
          }

          Log.i(TAG, "USB permission granted for " + describeDevice(device));
          dumpInterfaces(device, "permission-granted");
          attachRumbleDevice(device, "permission-granted");
        }
      };

  private boolean receiverRegistered = false;

  UsbControllerManager(XServerDisplayActivity activity) {
    this.activity = activity;
    this.usbManager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
    this.permissionAction = activity.getPackageName() + ".USB_PERMISSION";
  }

  void start() {
    if (this.usbManager == null || this.receiverRegistered) {
      return;
    }

    IntentFilter filter = new IntentFilter(permissionAction);
    filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
    filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      this.activity.registerReceiver(this.receiver, filter, Context.RECEIVER_NOT_EXPORTED);
    } else {
      this.activity.registerReceiver(this.receiver, filter);
    }
    this.receiverRegistered = true;
    scanAndRequestPermissions("startup");
  }

  void stop() {
    if (!this.receiverRegistered) {
      return;
    }
    try {
      this.activity.unregisterReceiver(this.receiver);
    } catch (IllegalArgumentException ignored) {
    }
    this.receiverRegistered = false;
    this.requestedKeys.clear();
    clearRumbleDevices();
  }

  void scanAndRequestPermissions(String reason) {
    if (this.usbManager == null) {
      Log.w(TAG, "UsbManager unavailable, skipping scan for " + reason);
      return;
    }

    for (UsbDevice device : this.usbManager.getDeviceList().values()) {
      if (!isTargetController(device)) {
        continue;
      }
      logDeviceSummary(device, reason);
      ensurePermission(device, reason);
    }
  }

  void ensurePermissionForInputDevice(int inputDeviceId, String reason) {
    InputDevice inputDevice = InputDevice.getDevice(inputDeviceId);
    if (inputDevice == null) {
      scanAndRequestPermissions(reason + "-missing-input");
      return;
    }

    List<UsbDevice> devices = findMatchingUsbDevices(inputDevice);
    if (devices.isEmpty()) {
      Log.d(
          TAG,
          "No matching USB device for input device "
              + inputDeviceId
              + " ("
              + inputDevice.getName()
              + "), falling back to broad scan.");
      scanAndRequestPermissions(reason + "-fallback");
      return;
    }

    for (UsbDevice device : devices) {
      ensurePermission(device, reason + "-input-" + inputDeviceId);
    }
  }

  boolean hasPermissionForInputDevice(int inputDeviceId) {
    InputDevice inputDevice = InputDevice.getDevice(inputDeviceId);
    if (inputDevice == null || this.usbManager == null) {
      return false;
    }
    for (UsbDevice device : findMatchingUsbDevices(inputDevice)) {
      if (this.usbManager.hasPermission(device)) {
        return true;
      }
    }
    return false;
  }

  boolean sendRumbleForInputDevice(int inputDeviceId, int strong, int weak) {
    InputDevice inputDevice = InputDevice.getDevice(inputDeviceId);
    if (inputDevice == null || this.usbManager == null) {
      return false;
    }

    for (UsbDevice device : findMatchingUsbDevices(inputDevice)) {
      if (!this.usbManager.hasPermission(device)) {
        continue;
      }

      UsbRumbleDevice rumbleDevice = attachRumbleDevice(device, "rumble-" + inputDeviceId);
      if (rumbleDevice == null) {
        continue;
      }

      rumbleDevice.rumble(strong, weak);
      return true;
    }

    return false;
  }

  private void ensurePermission(UsbDevice device, String reason) {
    if (this.usbManager == null || device == null) {
      return;
    }

    String key = buildDeviceKey(device);
    if (this.usbManager.hasPermission(device)) {
      Log.d(TAG, "USB permission already available for " + describeDevice(device) + " via " + reason);
      attachRumbleDevice(device, reason + "-already-granted");
      return;
    }

    if (!this.requestedKeys.add(key)) {
      Log.d(TAG, "USB permission request already pending for " + describeDevice(device));
      return;
    }

    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      flags |= PendingIntent.FLAG_IMMUTABLE;
    }
    PendingIntent pendingIntent =
        PendingIntent.getBroadcast(this.activity, 0, new Intent(this.permissionAction), flags);
    Log.i(TAG, "Requesting USB permission for " + describeDevice(device) + " via " + reason);
    this.usbManager.requestPermission(device, pendingIntent);
  }

  private List<UsbDevice> findMatchingUsbDevices(InputDevice inputDevice) {
    ArrayList<UsbDevice> matches = new ArrayList<>();
    if (this.usbManager == null || inputDevice == null) {
      return matches;
    }

    int vendorId = inputDevice.getVendorId();
    int productId = inputDevice.getProductId();
    String inputName = normalize(inputDevice.getName());

    for (UsbDevice device : this.usbManager.getDeviceList().values()) {
      if (!isTargetController(device)) {
        continue;
      }

      boolean idMatches =
          vendorId != 0 && vendorId == device.getVendorId() && productId == device.getProductId();
      boolean nameMatches =
          !inputName.isEmpty()
              && (normalize(device.getProductName()).contains(inputName)
                  || inputName.contains(normalize(device.getProductName()))
                  || normalize(device.getDeviceName()).contains(inputName));
      if (idMatches || nameMatches || (vendorId == RAZER_VENDOR_ID && device.getVendorId() == vendorId)) {
        matches.add(device);
      }
    }
    return matches;
  }

  private boolean isTargetController(UsbDevice device) {
    if (device == null) {
      return false;
    }
    if (device.getVendorId() == RAZER_VENDOR_ID && KNOWN_RAZER_PRODUCTS.contains(device.getProductId())) {
      return true;
    }
    String productName = normalize(device.getProductName());
    String deviceName = normalize(device.getDeviceName());
    return device.getVendorId() == RAZER_VENDOR_ID
        || productName.contains("kishi")
        || productName.contains("razer")
        || deviceName.contains("razer");
  }

  private void logDeviceSummary(UsbDevice device, String reason) {
    Log.i(TAG, "Discovered USB controller via " + reason + ": " + describeDevice(device));
  }

  private UsbRumbleDevice attachRumbleDevice(UsbDevice device, String reason) {
    if (this.usbManager == null || device == null || !this.usbManager.hasPermission(device)) {
      return null;
    }

    String key = buildDeviceKey(device);
    UsbRumbleDevice existing = this.rumbleDevices.get(key);
    if (existing != null) {
      return existing;
    }

    if (!UsbXbox360RumbleDevice.canClaimDevice(device)) {
      return null;
    }

    UsbDeviceConnection connection = this.usbManager.openDevice(device);
    if (connection == null) {
      Log.w(TAG, "Failed to open USB device for rumble: " + describeDevice(device));
      return null;
    }

    UsbRumbleDevice rumbleDevice = new UsbXbox360RumbleDevice(device, connection);
    if (!rumbleDevice.start()) {
      rumbleDevice.stop();
      return null;
    }

    this.rumbleDevices.put(key, rumbleDevice);
    Log.i(TAG, "Attached Xbox360-style rumble device via " + reason + ": " + describeDevice(device));
    return rumbleDevice;
  }

  private void removeRumbleDevice(UsbDevice device) {
    UsbRumbleDevice rumbleDevice = this.rumbleDevices.remove(buildDeviceKey(device));
    if (rumbleDevice != null) {
      rumbleDevice.stop();
    }
  }

  private void clearRumbleDevices() {
    for (UsbRumbleDevice rumbleDevice : this.rumbleDevices.values()) {
      rumbleDevice.stop();
    }
    this.rumbleDevices.clear();
  }

  private void dumpInterfaces(UsbDevice device, String reason) {
    if (this.usbManager == null) {
      return;
    }

    UsbDeviceConnection connection = this.usbManager.openDevice(device);
    if (connection == null) {
      Log.w(TAG, "Unable to open USB device for interface dump: " + describeDevice(device));
      return;
    }

    try {
      Log.i(
          TAG,
          "USB interface dump for "
              + describeDevice(device)
              + " via "
              + reason
              + ": interfaces="
              + device.getInterfaceCount());
      for (int i = 0; i < device.getInterfaceCount(); i++) {
        UsbInterface usbInterface = device.getInterface(i);
        Log.i(
            TAG,
            "  interface["
                + i
                + "] id="
                + usbInterface.getId()
                + " class="
                + usbInterface.getInterfaceClass()
                + " subclass="
                + usbInterface.getInterfaceSubclass()
                + " protocol="
                + usbInterface.getInterfaceProtocol()
                + " endpoints="
                + usbInterface.getEndpointCount());
        for (int j = 0; j < usbInterface.getEndpointCount(); j++) {
          UsbEndpoint endpoint = usbInterface.getEndpoint(j);
          Log.i(
              TAG,
              "    endpoint["
                  + j
                  + "] address="
                  + endpoint.getAddress()
                  + " direction="
                  + endpoint.getDirection()
                  + " type="
                  + endpoint.getType()
                  + " maxPacket="
                  + endpoint.getMaxPacketSize()
                  + " interval="
                  + endpoint.getInterval());
        }
      }
    } finally {
      connection.close();
    }
  }

  private String buildDeviceKey(UsbDevice device) {
    return device.getVendorId() + ":" + device.getProductId() + ":" + device.getDeviceName();
  }

  private String describeDevice(UsbDevice device) {
    return "vendor=0x"
        + Integer.toHexString(device.getVendorId())
        + ", product=0x"
        + Integer.toHexString(device.getProductId())
        + ", productName="
        + safeValue(device.getProductName())
        + ", manufacturer="
        + safeValue(device.getManufacturerName())
        + ", deviceName="
        + safeValue(device.getDeviceName());
  }

  private String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value.toLowerCase(Locale.US);
  }

  private String safeValue(String value) {
    return value != null ? value : "";
  }
}
