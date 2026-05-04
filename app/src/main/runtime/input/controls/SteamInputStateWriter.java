package com.winlator.cmod.runtime.input.controls;

import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public final class SteamInputStateWriter {
  public static final int MAX_STEAM_INPUT_SLOTS = 4;
  public static final String STATE_ENV_PREFIX = "WINNATIVE_STEAMINPUT_STATE";
  public static final String ACTIONS_ENV = "WINNATIVE_STEAMINPUT_ACTIONS";
  public static final String ENABLED_ENV = "WINNATIVE_STEAMINPUT_ENABLED";
  public static final String WINNATIVE_ACTION_MAP_FILE_NAME = "winnative_steaminput_actions.txt";
  public static final String DEFAULT_ACTION_MAP_FILE_NAME = "actions.txt";

  private static final String TAG = "SteamInputStateWriter";
  private static final String STATE_DIR_NAME = "winnative-steaminput";
  private static final int STATE_MAGIC = 0x50494E57; // WNIP, little endian
  private static final int STATE_VERSION = 1;
  private static final int STATE_SIZE = 56;
  private static final Object LOCK = new Object();
  private static final StateSlot[] SLOTS = new StateSlot[MAX_STEAM_INPUT_SLOTS];
  private static File configuredStateDir;

  private SteamInputStateWriter() {}

  private static final class StateSlot {
    File stateFile;
    RandomAccessFile raf;
    FileChannel channel;
    MappedByteBuffer data;
    long seq;
  }

  public static File getStateDir(File imageRootDir) {
    return new File(new File(imageRootDir, "tmp"), STATE_DIR_NAME);
  }

  public static File getStateFile(File stateDir, int slot) {
    return new File(stateDir, "pad" + slot + ".state");
  }

  public static File getDefaultActionMapFile(File stateDir) {
    return new File(stateDir, DEFAULT_ACTION_MAP_FILE_NAME);
  }

  public static void prepareStateSlots(File stateDir, int slotCount) {
    if (stateDir == null) {
      return;
    }

    int boundedSlotCount = Math.max(0, Math.min(slotCount, MAX_STEAM_INPUT_SLOTS));
    synchronized (LOCK) {
      File desiredDir = stateDir.getAbsoluteFile();
      if (configuredStateDir != null && !configuredStateDir.equals(desiredDir)) {
        releaseAllSlotsLocked();
      }
      configuredStateDir = desiredDir;

      if (!configuredStateDir.exists() && !configuredStateDir.mkdirs()) {
        Log.e(TAG, "Failed to create Steam Input state directory: " + configuredStateDir);
        return;
      }

      ensureDefaultActionMapLocked(configuredStateDir);
      for (int slot = 0; slot < boundedSlotCount; slot++) {
        ensureSlotLocked(slot);
      }
    }
  }

  public static void writeConnectedState(int slot, GamepadState state) {
    writeState(slot, state, true);
  }

  public static void writeDisconnectedState(int slot) {
    writeState(slot, null, false);
  }

  public static void releaseAllSlots() {
    synchronized (LOCK) {
      releaseAllSlotsLocked();
      configuredStateDir = null;
    }
  }

  private static void writeState(int slot, GamepadState state, boolean connected) {
    StateSlot stateSlot;
    synchronized (LOCK) {
      stateSlot = ensureSlotLocked(slot);
    }
    if (stateSlot == null) {
      return;
    }

    synchronized (stateSlot) {
      writeStateLocked(stateSlot, state, connected, true);
    }
  }

  private static StateSlot ensureSlotLocked(int slot) {
    if (slot < 0 || slot >= MAX_STEAM_INPUT_SLOTS || configuredStateDir == null) {
      return null;
    }

    File stateFile = getStateFile(configuredStateDir, slot).getAbsoluteFile();
    StateSlot existing = SLOTS[slot];
    if (existing != null && existing.data != null && stateFile.equals(existing.stateFile)) {
      return existing;
    }

    releaseSlotLocked(slot);

    RandomAccessFile raf = null;
    FileChannel channel = null;
    try {
      File parent = stateFile.getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
        Log.e(TAG, "Failed to create Steam Input state parent: " + parent);
        return null;
      }

      raf = new RandomAccessFile(stateFile, "rw");
      raf.setLength(STATE_SIZE);
      channel = raf.getChannel();
      MappedByteBuffer data = channel.map(FileChannel.MapMode.READ_WRITE, 0, STATE_SIZE);
      data.order(ByteOrder.LITTLE_ENDIAN);

      StateSlot stateSlot = new StateSlot();
      stateSlot.stateFile = stateFile;
      stateSlot.raf = raf;
      stateSlot.channel = channel;
      stateSlot.data = data;
      stateSlot.seq = 0L;
      writeStateLocked(stateSlot, null, false, false);
      SLOTS[slot] = stateSlot;
      return stateSlot;
    } catch (IOException e) {
      Log.e(TAG, "Failed to map Steam Input state slot " + slot + ": " + e.getMessage());
      try {
        if (channel != null) channel.close();
      } catch (IOException ignored) {
      }
      try {
        if (raf != null) raf.close();
      } catch (IOException ignored) {
      }
      return null;
    }
  }

  private static void ensureDefaultActionMapLocked(File stateDir) {
    File actionMap = getDefaultActionMapFile(stateDir);
    if (actionMap.exists()) {
      return;
    }
    try {
      actionMap.createNewFile();
    } catch (IOException e) {
      Log.w(TAG, "Failed to create default Steam Input action map: " + e.getMessage());
    }
  }

  private static void releaseAllSlotsLocked() {
    for (int slot = 0; slot < SLOTS.length; slot++) {
      releaseSlotLocked(slot);
    }
  }

  private static void releaseSlotLocked(int slot) {
    StateSlot stateSlot = slot >= 0 && slot < SLOTS.length ? SLOTS[slot] : null;
    if (stateSlot == null) {
      return;
    }

    synchronized (stateSlot) {
      if (stateSlot.data != null) {
        writeStateLocked(stateSlot, null, false, true);
        stateSlot.data.force();
      }
      closeSlotFile(stateSlot);
    }
    SLOTS[slot] = null;
  }

  private static void closeSlotFile(StateSlot stateSlot) {
    try {
      if (stateSlot.channel != null) stateSlot.channel.close();
    } catch (IOException ignored) {
    }
    try {
      if (stateSlot.raf != null) stateSlot.raf.close();
    } catch (IOException ignored) {
    }
    stateSlot.channel = null;
    stateSlot.raf = null;
    stateSlot.data = null;
  }

  private static void writeStateLocked(
      StateSlot stateSlot, GamepadState state, boolean connected, boolean incrementSeq) {
    if (stateSlot.data == null) {
      return;
    }

    if (incrementSeq) {
      stateSlot.seq++;
    }

    stateSlot.data.putInt(0, STATE_MAGIC);
    stateSlot.data.putInt(4, STATE_VERSION);
    stateSlot.data.putLong(8, stateSlot.seq);
    stateSlot.data.putFloat(16, connected && state != null ? clampStick(state.thumbLX) : 0.0f);
    stateSlot.data.putFloat(20, connected && state != null ? clampStick(state.thumbLY) : 0.0f);
    stateSlot.data.putFloat(24, connected && state != null ? clampStick(state.thumbRX) : 0.0f);
    stateSlot.data.putFloat(28, connected && state != null ? clampStick(state.thumbRY) : 0.0f);
    stateSlot.data.putFloat(32, connected && state != null ? clampTrigger(state.triggerL) : 0.0f);
    stateSlot.data.putFloat(36, connected && state != null ? clampTrigger(state.triggerR) : 0.0f);
    stateSlot.data.putInt(40, connected && state != null ? buildButtonMask(state) : 0);
    stateSlot.data.put(44, connected && state != null ? state.getDPadX() : (byte) 0);
    stateSlot.data.put(45, connected && state != null ? state.getDPadY() : (byte) 0);
    stateSlot.data.put(46, (byte) (connected ? 1 : 0));
    for (int i = 47; i < STATE_SIZE; i++) {
      stateSlot.data.put(i, (byte) 0);
    }
  }

  private static int buildButtonMask(GamepadState state) {
    int mask = 0;
    if (state.isPressed(GamepadState.BUTTON_A)) mask |= 1;
    if (state.isPressed(GamepadState.BUTTON_B)) mask |= 1 << 1;
    if (state.isPressed(GamepadState.BUTTON_X)) mask |= 1 << 2;
    if (state.isPressed(GamepadState.BUTTON_Y)) mask |= 1 << 3;
    if (state.isPressed(GamepadState.BUTTON_L1)) mask |= 1 << 4;
    if (state.isPressed(GamepadState.BUTTON_R1)) mask |= 1 << 5;
    if (state.isPressed(GamepadState.BUTTON_SELECT)) mask |= 1 << 6;
    if (state.isPressed(GamepadState.BUTTON_START)) mask |= 1 << 7;
    if (state.isPressed(GamepadState.BUTTON_L3)) mask |= 1 << 8;
    if (state.isPressed(GamepadState.BUTTON_R3)) mask |= 1 << 9;
    if (state.isPressed(GamepadState.BUTTON_GUIDE)) mask |= 1 << 10;
    return mask;
  }

  private static float clampStick(float value) {
    if (value < -1.0f) return -1.0f;
    if (value > 1.0f) return 1.0f;
    return value;
  }

  private static float clampTrigger(float value) {
    if (value < 0.0f) return 0.0f;
    if (value > 1.0f) return 1.0f;
    return value;
  }
}
