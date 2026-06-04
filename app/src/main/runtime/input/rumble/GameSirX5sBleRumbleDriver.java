package com.winlator.cmod.runtime.input.rumble;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.InputDevice;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class GameSirX5sBleRumbleDriver implements GamepadRumbleDriver {
  private static final String TAG = "WinHandler";
  private static final int VENDOR_ID = 0x3537;
  private static final int PRODUCT_ID = 0x1119; // X5s
  // X3 Pro: wired for input, but rumbles over this same GameSir BLE protocol (char 0x865f).
  private static final int PRODUCT_ID_X3_PRO = 0x0106;
  private static final int RUMBLE_DEADZONE_RAW = 1;
  private static final int MAX_CONTROLLERS = 4;
  private static final long SCAN_TIMEOUT_MS = 8000;
  // Matches the official app: duration=1 + ~200ms heartbeat so the motor stops promptly.
  private static final int RUMBLE_DURATION = 1;
  private static final long HEARTBEAT_INTERVAL_MS = 150;

  private static final UUID GAMESIR_SERVICE_UUID =
      UUID.fromString("0000ff10-0000-1000-8000-00805f9b34fb");
  private static final UUID GAMESIR_NOTIFY_UUID =
      UUID.fromString("0000ff11-0000-1000-8000-00805f9b34fb");
  private static final UUID GAMESIR_WRITE_UUID =
      UUID.fromString("0000ff12-0000-1000-8000-00805f9b34fb");
  private static final UUID GAMESIR_OTA_WRITE_UUID =
      UUID.fromString("0000ff15-0000-1000-8000-00805f9b34fb");
  private static final UUID GAMESIR_BLE_SERVICE_UUID =
      UUID.fromString("00008650-0000-1000-8000-00805f9b34fb");
  private static final UUID GAMESIR_BLE_WRITE_UUID =
      UUID.fromString("0000865f-0000-1000-8000-00805f9b34fb");
  private static final UUID GAMESIR_BLE_NOTIFY_UUID =
      UUID.fromString("00008655-0000-1000-8000-00805f9b34fb");
  private static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID =
      UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

  private final Context context;
  private final Context permissionContext;
  private final Handler handler;
  private final int[] lastLeftPower = new int[] {-1, -1, -1, -1};
  private final int[] lastRightPower = new int[] {-1, -1, -1, -1};

  private BluetoothGatt gatt;
  private BluetoothGattCharacteristic writeCharacteristic;
  private BluetoothLeScanner scanner;
  private ScanCallback scanCallback;
  private byte[] pendingCommand;
  private boolean connecting;
  private boolean autoConnectPending;
  private boolean scanning;
  private boolean discoveringServices;
  private boolean notificationsEnabled;
  private int scanGeneration;
  private Runnable heartbeatRunnable;
  private byte[] heartbeatCommand;

  public GameSirX5sBleRumbleDriver(Context context, Handler handler) {
    this.context = context.getApplicationContext();
    this.permissionContext = context;
    this.handler = handler;
  }

  @Override
  public String getName() {
    return "GameSir X5s BLE";
  }

  @Override
  public boolean supports(InputDevice device, GcmRumbleMode mode) {
    if (device == null || mode == GcmRumbleMode.DISABLED) {
      return false;
    }
    if (device.getVendorId() != VENDOR_ID) {
      return false;
    }
    if (mode == GcmRumbleMode.ALL) {
      // Any GameSir BLE device (VID 0x3537)
      return true;
    }
    // KNOWN: X5s or X3 Pro by PID + name (both rumble over the GameSir BLE GATT protocol).
    String name = device.getName();
    if (name == null) {
      return false;
    }
    int pid = device.getProductId();
    return (pid == PRODUCT_ID && name.contains("GameSir-X5s"))
        || (pid == PRODUCT_ID_X3_PRO && name.contains("GameSir-X3 Pro"));
  }

  @Override
  public void requestPermissionIfNeeded() {
    if (!hasBluetoothPermission(true)) {
      requestBluetoothPermissionIfPossible();
      return;
    }
    // Proactively start scanning so the BLE connection is ready before the first rumble request.
    handler.post(this::ensureConnected);
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
          TAG,
          "GameSir X5s BLE rumble unchanged slot="
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

    boolean stopping = leftPower == 0 && rightPower == 0;
    byte[] command = buildRumbleCommand(leftPower, stopping ? 0 : RUMBLE_DURATION,
        rightPower, stopping ? 0 : RUMBLE_DURATION);
    boolean handled = sendCommand(command, "slot=" + slot + " rawStrong=" + strong + " rawWeak=" + weak);
    if (handled) {
      lastLeftPower[slot] = leftPower;
      lastRightPower[slot] = rightPower;
      if (stopping) {
        stopHeartbeat();
      } else {
        startHeartbeat(command);
      }
    }
    return handled;
  }

  // 9-byte GCM immediate-vibration: [0x04, lgStr, lgDur, rgStr, rgDur, ltStr, ltDur, rtStr, rtDur].
  private static byte[] buildRumbleCommand(
      int leftGrip, int lgDuration, int rightGrip, int rgDuration) {
    return new byte[] {
      0x04,
      (byte) clampByte(leftGrip),
      (byte) clampByte(lgDuration),
      (byte) clampByte(rightGrip),
      (byte) clampByte(rgDuration),
      0, 0, 0, 0  // ltStrength, ltDuration, rtStrength, rtDuration (triggers unused)
    };
  }

  private int scaleMotor(int value) {
    if (value <= RUMBLE_DEADZONE_RAW) {
      return 0;
    }
    return Math.min(255, Math.max(1, Math.round((value / 65535.0f) * 255.0f)));
  }

  private void startHeartbeat(byte[] command) {
    stopHeartbeat();
    heartbeatCommand = Arrays.copyOf(command, command.length);
    heartbeatRunnable = new Runnable() {
      @Override
      public void run() {
        byte[] cmd = heartbeatCommand;
        if (cmd != null) {
          sendCommand(cmd, "heartbeat");
          handler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
        }
      }
    };
    handler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS);
  }

  private void stopHeartbeat() {
    if (heartbeatRunnable != null) {
      handler.removeCallbacks(heartbeatRunnable);
      heartbeatRunnable = null;
    }
    heartbeatCommand = null;
  }

  private boolean sendCommand(byte[] command, String reason) {
    if (!hasBluetoothPermission(true)) {
      requestBluetoothPermissionIfPossible();
      Log.d(TAG, "GameSir X5s BLE missing bluetooth permission " + reason);
      return true;
    }

    if (writeCharacteristic != null && gatt != null) {
      return writeNow(command, reason);
    }

    pendingCommand = Arrays.copyOf(command, command.length);
    ensureConnected();
    Log.d(TAG, "GameSir X5s BLE queued command " + reason + " command=" + bytesToHex(command));
    return true;
  }

  private void ensureConnected() {
    if (connecting || discoveringServices || writeCharacteristic != null) {
      return;
    }

    BluetoothAdapter adapter = getAdapter();
    if (adapter == null || !adapter.isEnabled()) {
      Log.d(TAG, "GameSir X5s BLE unavailable: adapter disabled");
      return;
    }

    if (!autoConnectPending && gatt == null) {
      tryRegisterAutoConnect(adapter);
    }

    if (!scanning) {
      startScan(adapter);
    }
  }

  private void tryRegisterAutoConnect(BluetoothAdapter adapter) {
    Set<BluetoothDevice> bonded;
    try {
      bonded = adapter.getBondedDevices();
    } catch (SecurityException e) {
      Log.d(TAG, "GameSir X5s BLE bonded list security error: " + e.getMessage());
      return;
    }
    if (bonded == null) {
      return;
    }
    for (BluetoothDevice device : bonded) {
      if (isCandidate(device)) {
        Log.d(TAG, "GameSir X5s BLE auto-connecting bonded " + describeDevice(device));
        try {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            gatt = device.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE);
          } else {
            gatt = device.connectGatt(context, true, gattCallback);
          }
          autoConnectPending = true;
        } catch (SecurityException e) {
          Log.d(TAG, "GameSir X5s BLE auto-connect security error: " + e.getMessage());
        }
        return;
      }
    }
  }

  private void startScan(BluetoothAdapter adapter) {
    scanner = adapter.getBluetoothLeScanner();
    if (scanner == null) {
      Log.d(TAG, "GameSir X5s BLE scanner unavailable");
      return;
    }

    scanning = true;
    int generation = ++scanGeneration;
    scanCallback =
        new ScanCallback() {
          @Override
          public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (shouldLogScanResult(device)) {
              Log.d(TAG, "GameSir X5s BLE scan result " + describeDevice(device));
            }
            // Match device.getName() or the advertised name (getName() can be null on first sight).
            String advertisedName =
                result.getScanRecord() != null ? result.getScanRecord().getDeviceName() : null;
            if (isCandidate(device) || matchesKnownName(advertisedName)) {
              stopScan();
              connect(device);
            }
          }

          @Override
          public void onScanFailed(int errorCode) {
            scanning = false;
            Log.d(TAG, "GameSir X5s BLE scan failed error=" + errorCode);
          }
        };

    try {
      scanner.startScan(scanCallback);
      Log.d(TAG, "GameSir X5s BLE scan started");
      handler.postDelayed(
          () -> {
            if (scanGeneration == generation) {
              stopScan();
            }
          },
          SCAN_TIMEOUT_MS);
    } catch (SecurityException e) {
      scanning = false;
      Log.d(TAG, "GameSir X5s BLE scan security error: " + e.getMessage());
    }
  }

  private void connect(BluetoothDevice device) {
    if (device == null || connecting) {
      return;
    }
    autoConnectPending = false;
    connecting = true;
    closeGatt();
    Log.d(TAG, "GameSir X5s BLE connecting to " + describeDevice(device));
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
      } else {
        gatt = device.connectGatt(context, false, gattCallback);
      }
    } catch (SecurityException e) {
      connecting = false;
      Log.d(TAG, "GameSir X5s BLE connect security error: " + e.getMessage());
    }
  }

  private final BluetoothGattCallback gattCallback =
      new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt callbackGatt, int status, int newState) {
          Log.d(
              TAG,
              "GameSir X5s BLE connection status="
                  + status
                  + " newState="
                  + newState);
          if (newState == BluetoothProfile.STATE_CONNECTED) {
            autoConnectPending = false;
            connecting = false;
            discoveringServices = true;
            try {
              callbackGatt.discoverServices();
            } catch (SecurityException e) {
              discoveringServices = false;
              Log.d(TAG, "GameSir X5s BLE discover security error: " + e.getMessage());
            }
          } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            autoConnectPending = false;
            connecting = false;
            discoveringServices = false;
            stopHeartbeat();
            closeGatt();
            if (currentMode != GcmRumbleMode.DISABLED) {
              handler.postDelayed(GameSirX5sBleRumbleDriver.this::ensureConnected, 1000);
            }
          }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt callbackGatt, int status) {
          discoveringServices = false;
          Log.d(TAG, "GameSir X5s BLE services discovered status=" + status);
          logServices(callbackGatt);
          writeCharacteristic = findWriteCharacteristic(callbackGatt);
          if (writeCharacteristic == null) {
            Log.d(TAG, "GameSir X5s BLE no writable characteristic found");
            return;
          }
          Log.d(
              TAG,
              "GameSir X5s BLE selected write characteristic="
                  + writeCharacteristic.getUuid()
                  + " properties=0x"
                  + Integer.toHexString(writeCharacteristic.getProperties()));

          byte[] command = pendingCommand;
          if (command != null) {
            pendingCommand = Arrays.copyOf(command, command.length);
            BluetoothGattCharacteristic notifyCharacteristic = findNotifyCharacteristic(callbackGatt);
            if (!enableNotifications(callbackGatt, notifyCharacteristic)) {
              flushPendingCommand("pending");
            } else {
              handler.postDelayed(() -> flushPendingCommand("pending-after-notify-delay"), 150);
            }
          }
        }

        @Override
        public void onDescriptorWrite(
            BluetoothGatt callbackGatt, BluetoothGattDescriptor descriptor, int status) {
          Log.d(
              TAG,
              "GameSir X5s BLE descriptor write callback descriptor="
                  + descriptor.getUuid()
                  + " characteristic="
                  + descriptor.getCharacteristic().getUuid()
                  + " status="
                  + status);
          if (status == BluetoothGatt.GATT_SUCCESS) {
            notificationsEnabled = true;
          }
          flushPendingCommand("pending-after-notify");
        }

        @Override
        public void onCharacteristicWrite(
            BluetoothGatt callbackGatt, BluetoothGattCharacteristic characteristic, int status) {
          Log.d(
              TAG,
              "GameSir X5s BLE write callback characteristic="
                  + characteristic.getUuid()
                  + " status="
                  + status);
        }
      };

  private void flushPendingCommand(String reason) {
    byte[] command = pendingCommand;
    pendingCommand = null;
    if (command != null) {
      writeNow(command, reason);
    }
  }

  private boolean writeNow(byte[] command, String reason) {
    BluetoothGatt localGatt = gatt;
    BluetoothGattCharacteristic characteristic = writeCharacteristic;
    if (localGatt == null || characteristic == null) {
      pendingCommand = Arrays.copyOf(command, command.length);
      ensureConnected();
      return true;
    }

    try {
      int writeType;
      if (GAMESIR_BLE_WRITE_UUID.equals(characteristic.getUuid())) {
        writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
      } else if ((characteristic.getProperties()
              & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)
          != 0) {
        writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
      } else {
        writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
      }
      try {
        characteristic.setWriteType(writeType);
      } catch (IllegalArgumentException e) {
        Log.d(
            TAG,
            "GameSir X5s BLE write type rejected writeType="
                + writeType
                + " characteristic="
                + characteristic.getUuid()
                + " message="
                + e.getMessage());
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
      }
      characteristic.setValue(command);
      boolean started = localGatt.writeCharacteristic(characteristic);
      Log.d(
          TAG,
          "GameSir X5s BLE rumble write started="
              + started
              + " "
              + reason
              + " characteristic="
              + characteristic.getUuid()
              + " writeType="
              + characteristic.getWriteType()
              + " command="
              + bytesToHex(command));
      return started;
    } catch (SecurityException e) {
      Log.d(TAG, "GameSir X5s BLE write security error: " + e.getMessage());
      return true;
    }
  }

  private boolean enableNotifications(
      BluetoothGatt localGatt, BluetoothGattCharacteristic characteristic) {
    if (notificationsEnabled
        || localGatt == null
        || characteristic == null
        || (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
      return false;
    }

    try {
      boolean localEnabled = localGatt.setCharacteristicNotification(characteristic, true);
      BluetoothGattDescriptor descriptor =
          characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID);
      if (descriptor == null) {
        Log.d(
            TAG,
            "GameSir X5s BLE notify set="
                + localEnabled
                + " descriptor missing characteristic="
                + characteristic.getUuid());
        notificationsEnabled = localEnabled;
        return false;
      }
      descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
      boolean started = localGatt.writeDescriptor(descriptor);
      Log.d(
          TAG,
          "GameSir X5s BLE notify set="
              + localEnabled
              + " descriptorWriteStarted="
              + started
              + " characteristic="
              + characteristic.getUuid());
      return started;
    } catch (SecurityException e) {
      Log.d(TAG, "GameSir X5s BLE notify security error: " + e.getMessage());
      return false;
    }
  }

  private BluetoothGattCharacteristic findWriteCharacteristic(BluetoothGatt localGatt) {
    BluetoothGattService bleService = localGatt.getService(GAMESIR_BLE_SERVICE_UUID);
    if (bleService != null) {
      BluetoothGattCharacteristic preferred = findWritableByUuid(bleService, GAMESIR_BLE_WRITE_UUID);
      if (preferred != null) {
        return preferred;
      }
    }

    BluetoothGattService service = localGatt.getService(GAMESIR_SERVICE_UUID);
    if (service != null) {
      BluetoothGattCharacteristic preferred = findWritableByUuid(service, GAMESIR_WRITE_UUID);
      if (preferred != null) {
        return preferred;
      }
      preferred = findWritableByUuid(service, GAMESIR_NOTIFY_UUID);
      if (preferred != null) {
        return preferred;
      }
      preferred = findWritableByUuid(service, GAMESIR_OTA_WRITE_UUID);
      if (preferred != null) {
        return preferred;
      }
    }

    for (BluetoothGattService candidateService : localGatt.getServices()) {
      BluetoothGattCharacteristic writable = findFirstWritable(candidateService);
      if (writable != null) {
        return writable;
      }
    }
    return null;
  }

  private BluetoothGattCharacteristic findNotifyCharacteristic(BluetoothGatt localGatt) {
    BluetoothGattService bleService = localGatt.getService(GAMESIR_BLE_SERVICE_UUID);
    if (bleService != null) {
      BluetoothGattCharacteristic preferred = bleService.getCharacteristic(GAMESIR_BLE_NOTIFY_UUID);
      if (preferred != null
          && (preferred.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
        return preferred;
      }
    }
    return writeCharacteristic;
  }

  private BluetoothGattCharacteristic findWritableByUuid(
      BluetoothGattService service, UUID uuid) {
    BluetoothGattCharacteristic characteristic = service.getCharacteristic(uuid);
    if (characteristic != null && isWritable(characteristic)) {
      return characteristic;
    }
    return null;
  }

  private BluetoothGattCharacteristic findFirstWritable(BluetoothGattService service) {
    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
      if (isWritable(characteristic)) {
        return characteristic;
      }
    }
    return null;
  }

  private boolean isWritable(BluetoothGattCharacteristic characteristic) {
    int properties = characteristic.getProperties();
    return (properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
        || (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
  }

  private void logServices(BluetoothGatt localGatt) {
    List<BluetoothGattService> services = localGatt.getServices();
    Log.d(TAG, "GameSir X5s BLE service count=" + services.size());
    for (BluetoothGattService service : services) {
      Log.d(TAG, "GameSir X5s BLE service=" + service.getUuid());
      for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
        Log.d(
            TAG,
            "GameSir X5s BLE characteristic="
                + characteristic.getUuid()
                + " properties=0x"
                + Integer.toHexString(characteristic.getProperties()));
      }
    }
  }

  private GcmRumbleMode currentMode = GcmRumbleMode.DISABLED;

  @Override
  public void setMode(GcmRumbleMode mode) {
    this.currentMode = mode;
  }

  private boolean isCandidate(BluetoothDevice device) {
    if (device == null) {
      return false;
    }
    String name = null;
    try {
      name = device.getName();
    } catch (SecurityException ignored) {
    }
    return matchesKnownName(name);
  }

  /** Name-based match (no MAC); works for any unit since all advertise the same local name. */
  private boolean matchesKnownName(String name) {
    if (name == null) return false;
    if (currentMode == GcmRumbleMode.ALL) {
      // In ALL mode accept any GameSir BLE device
      return name.contains("GameSir") || name.contains("Gamesir");
    }
    // KNOWN mode: X5s or X3 Pro BLE names
    return name.contains("GamePad05")
        || name.contains("GameSir-X5s")
        || name.contains("X5s")
        || name.contains("GameSir-X3 Pro")
        || name.contains("X3 Pro");
  }

  private boolean shouldLogScanResult(BluetoothDevice device) {
    if (device == null) {
      return false;
    }
    String name = null;
    try {
      name = device.getName();
    } catch (SecurityException ignored) {
    }
    if (name == null) {
      return false;
    }
    return name.contains("Game")
        || name.contains("game")
        || name.contains("X5")
        || name.contains("x5")
        || name.contains("05");
  }

  private BluetoothAdapter getAdapter() {
    BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    return manager != null ? manager.getAdapter() : BluetoothAdapter.getDefaultAdapter();
  }

  private void stopScan() {
    if (!scanning) {
      return;
    }
    scanning = false;
    if (scanner != null && scanCallback != null) {
      try {
        scanner.stopScan(scanCallback);
      } catch (SecurityException e) {
        Log.d(TAG, "GameSir X5s BLE stop scan security error: " + e.getMessage());
      }
    }
    scanCallback = null;
    scanGeneration++;
    Log.d(TAG, "GameSir X5s BLE scan stopped");
  }

  void closeGatt() {
    if (gatt != null) {
      try {
        gatt.close();
      } catch (Exception ignored) {
      }
      gatt = null;
    }
    writeCharacteristic = null;
    notificationsEnabled = false;
    discoveringServices = false;
  }

  private boolean hasBluetoothPermission(boolean includeScan) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      return true;
    }
    boolean connect =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED;
    boolean scan =
        !includeScan
            || context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED;
    return connect && scan;
  }

  private void requestBluetoothPermissionIfPossible() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !(permissionContext instanceof Activity)) {
      return;
    }
    ((Activity) permissionContext)
        .requestPermissions(
            new String[] {Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
            5305);
  }

  private String describeDevice(BluetoothDevice device) {
    String name = null;
    String address = null;
    try {
      name = device.getName();
      address = device.getAddress();
    } catch (SecurityException ignored) {
    }
    return String.format(Locale.US, "name=%s address=%s", name, address);
  }

  private static int clampByte(int value) {
    return Math.max(0, Math.min(255, value));
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < bytes.length; i++) {
      if (i > 0) {
        builder.append(' ');
      }
      builder.append(String.format(Locale.US, "%02x", bytes[i] & 0xff));
    }
    return builder.toString();
  }
}
