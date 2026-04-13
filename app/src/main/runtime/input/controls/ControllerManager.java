package com.winlator.cmod.runtime.input.controls;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.util.SparseArray;
import android.view.InputDevice;
import androidx.preference.PreferenceManager;
import java.util.ArrayList;
import java.util.List;

public class ControllerManager {

  @SuppressLint("StaticFieldLeak")
  private static ControllerManager instance;

  public static synchronized ControllerManager getInstance() {
    if (instance == null) {
      instance = new ControllerManager();
    }
    return instance;
  }

  private ControllerManager() {}

  private Context context;
  private SharedPreferences preferences;
  private InputManager inputManager;
  private final List<InputDevice> detectedDevices = new ArrayList<>();
  private final SparseArray<String> slotAssignments = new SparseArray<>();
  private final boolean[] enabledSlots = new boolean[4];
  private final boolean[] vibrationEnabled = new boolean[] {true, true, true, true};

  public static final String PREF_PLAYER_SLOT_PREFIX = "controller_slot_";
  public static final String PREF_ENABLED_SLOTS_PREFIX = "enabled_slot_";
  public static final String PREF_VIBRATE_SLOT_PREFIX = "vibrate_slot_";

  public void init(Context context) {
    this.context = context.getApplicationContext();
    this.preferences = PreferenceManager.getDefaultSharedPreferences(this.context);
    this.inputManager = (InputManager) this.context.getSystemService(Context.INPUT_SERVICE);
    loadAssignments();
    scanForDevices();
  }

  public void scanForDevices() {
    detectedDevices.clear();
    java.util.LinkedHashMap<String, InputDevice> uniqueDevices = new java.util.LinkedHashMap<>();
    int[] deviceIds = inputManager.getInputDeviceIds();
    for (int deviceId : deviceIds) {
      InputDevice device = inputManager.getInputDevice(deviceId);
      if (device != null && !device.isVirtual() && isGameController(device)) {
        String identifier = getDeviceIdentifier(device);
        InputDevice current = uniqueDevices.get(identifier);
        if (current == null || device.getMotionRanges().size() > current.getMotionRanges().size()) {
          uniqueDevices.put(identifier, device);
        }
      }
    }
    detectedDevices.addAll(uniqueDevices.values());
  }

  private void loadAssignments() {
    slotAssignments.clear();
    for (int i = 0; i < 4; i++) {
      String deviceIdentifier = preferences.getString(PREF_PLAYER_SLOT_PREFIX + i, null);
      if (deviceIdentifier != null) slotAssignments.put(i, deviceIdentifier);
      enabledSlots[i] = preferences.getBoolean(PREF_ENABLED_SLOTS_PREFIX + i, i == 0);
      vibrationEnabled[i] = preferences.getBoolean(PREF_VIBRATE_SLOT_PREFIX + i, i == 0);
    }
  }

  public void saveAssignments() {
    SharedPreferences.Editor editor = preferences.edit();
    for (int i = 0; i < 4; i++) {
      String deviceIdentifier = slotAssignments.get(i);
      if (deviceIdentifier != null) {
        editor.putString(PREF_PLAYER_SLOT_PREFIX + i, deviceIdentifier);
      } else {
        editor.remove(PREF_PLAYER_SLOT_PREFIX + i);
      }
      editor.putBoolean(PREF_ENABLED_SLOTS_PREFIX + i, enabledSlots[i]);
      editor.putBoolean(PREF_VIBRATE_SLOT_PREFIX + i, vibrationEnabled[i]);
    }
    editor.apply();
  }

  public static boolean isGameController(InputDevice d) {
    return ExternalController.isGameController(d);
  }

  public static String getDeviceIdentifier(InputDevice device) {
    if (device == null) return null;
    return ExternalController.getPhysicalDeviceIdentifier(device);
  }

  public List<InputDevice> getDetectedDevices() {
    return detectedDevices;
  }

  public int getEnabledPlayerCount() {
    int count = 0;
    for (boolean enabled : enabledSlots) if (enabled) count++;
    return count;
  }

  public void assignDeviceToSlot(int slotIndex, InputDevice device) {
    if (slotIndex < 0 || slotIndex >= 4) return;
    String newDeviceIdentifier = getDeviceIdentifier(device);
    if (newDeviceIdentifier == null) return;

    for (int i = 0; i < 4; i++) {
      if (newDeviceIdentifier.equals(slotAssignments.get(i))) slotAssignments.remove(i);
    }
    slotAssignments.put(slotIndex, newDeviceIdentifier);
    saveAssignments();
  }

  public void unassignSlot(int slotIndex) {
    if (slotIndex < 0 || slotIndex >= 4) return;
    slotAssignments.remove(slotIndex);
    saveAssignments();
  }

  public int getSlotForDevice(int deviceId) {
    InputDevice device = inputManager.getInputDevice(deviceId);
    String deviceIdentifier = getDeviceIdentifier(device);
    if (deviceIdentifier == null) return -1;

    for (int i = 0; i < slotAssignments.size(); i++) {
      int key = slotAssignments.keyAt(i);
      if (deviceIdentifier.equals(slotAssignments.valueAt(i))) return key;
    }
    return -1;
  }

  public InputDevice getAssignedDeviceForSlot(int slotIndex) {
    String assignedIdentifier = slotAssignments.get(slotIndex);
    if (assignedIdentifier == null) return null;
    for (InputDevice device : detectedDevices) {
      if (assignedIdentifier.equals(getDeviceIdentifier(device))) return device;
    }
    return null;
  }

  public void setSlotEnabled(int slotIndex, boolean isEnabled) {
    if (slotIndex < 0 || slotIndex >= 4) return;
    enabledSlots[slotIndex] = isEnabled;
    saveAssignments();
  }

  public boolean isSlotEnabled(int slotIndex) {
    return slotIndex >= 0 && slotIndex < 4 && enabledSlots[slotIndex];
  }

  public boolean isVibrationEnabled(int slot) {
    return slot >= 0 && slot < 4 && vibrationEnabled[slot];
  }

  public void setVibrationEnabled(int slot, boolean enabled) {
    if (slot < 0 || slot >= 4) return;
    vibrationEnabled[slot] = enabled;
    saveAssignments();
  }
}
