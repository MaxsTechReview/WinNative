package com.winlator.cmod.runtime.input.controls;

import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.winlator.cmod.runtime.display.connector.XConnectorEpoll;
import com.winlator.cmod.sharedmemory.SysVSharedMemory;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FakeInputWriter {
  public static final short ABS_BRAKE = 10;
  public static final short ABS_GAS = 9;
  public static final short ABS_HAT0X = 16;
  public static final short ABS_HAT0Y = 17;
  public static final short ABS_RX = 3;
  public static final short ABS_RY = 4;
  public static final short ABS_X = 0;
  public static final short ABS_Y = 1;
  private static final int BUFFER_SIZE = 768;
  private static final int EVENT_SIZE = 24;
  private static final int MAX_FAKE_INPUT_SLOTS = 4;
  private static final int RING_CAPACITY_EVENTS = 512;
  private static final int RING_HEADER_SIZE = 64;
  private static final int RING_SIZE = RING_HEADER_SIZE + (RING_CAPACITY_EVENTS * EVENT_SIZE);
  private static final int RING_MAGIC = 0x46494252; // FIBR
  private static final int RING_VERSION = 1;
  private static final int RING_MAGIC_OFFSET = 0;
  private static final int RING_VERSION_OFFSET = 4;
  private static final int RING_EVENT_SIZE_OFFSET = 8;
  private static final int RING_CAPACITY_OFFSET = 12;
  private static final int RING_WRITE_SEQ_OFFSET = 16;
  private static final int RING_GENERATION_OFFSET = 24;
  public static final short EV_ABS = 3;
  public static final short EV_KEY = 1;
  public static final short EV_MSC = 4;
  public static final short EV_SYN = 0;
  private static final int MAX_EVENTS_PER_UPDATE = 32;
  public static final short MSC_SCAN = 4;
  public static final short SYN_REPORT = 0;
  private static final String TAG = "FakeInputWriter";
  private static final Object RING_LOCK = new Object();
  private static final RingSlot[] RING_SLOTS = new RingSlot[MAX_FAKE_INPUT_SLOTS];
  private final File eventFile;
  private final int slot;
  private int prevHatX;
  private int prevHatY;
  private int prevThumbLX;
  private int prevThumbLY;
  private int prevThumbRX;
  private int prevThumbRY;
  private int prevTriggerL;
  private int prevTriggerR;
  public static final short BTN_A = 304;
  public static final short BTN_B = 305;
  public static final short BTN_X = 307;
  public static final short BTN_Y = 308;
  public static final short BTN_TL = 310;
  public static final short BTN_TR = 311;
  public static final short BTN_SELECT = 314;
  public static final short BTN_START = 315;
  public static final short BTN_THUMBL = 317;
  public static final short BTN_THUMBR = 318;
  private static final short[] BUTTON_MAP = {
    BTN_A, BTN_B, BTN_X, BTN_Y, BTN_TL, BTN_TR, BTN_SELECT, BTN_START, BTN_THUMBL, BTN_THUMBR
  };
  private boolean isOpen = false;
  private volatile boolean destroyed = false;
  private final boolean[] prevButtonStates = new boolean[12];
  private boolean hasChanges = false;
  private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

  public FakeInputWriter(String fakeInputPath, int slot) {
    this.slot = slot;
    this.eventFile = new File(fakeInputPath, NotificationCompat.CATEGORY_EVENT + slot);
    this.buffer.order(ByteOrder.LITTLE_ENDIAN);
  }

  private static final class RingSlot {
    int fd = -1;
    ByteBuffer data;
    long generation;
    boolean active;
    boolean everActivated;
  }

  public static void prepareMemfdSlots(int slotCount) {
    int boundedSlotCount = Math.max(0, Math.min(slotCount, MAX_FAKE_INPUT_SLOTS));
    synchronized (RING_LOCK) {
      for (int slot = 0; slot < boundedSlotCount; slot++) {
        ensureRingSlotLocked(slot);
      }
    }
  }

  public static String getMemfdEnv() {
    synchronized (RING_LOCK) {
      prepareMemfdSlotsLocked(MAX_FAKE_INPUT_SLOTS);
      StringBuilder builder = new StringBuilder();
      int pid = android.os.Process.myPid();
      for (int slot = 0; slot < RING_SLOTS.length; slot++) {
        RingSlot ringSlot = RING_SLOTS[slot];
        if (ringSlot == null || ringSlot.fd < 0) {
          continue;
        }
        if (builder.length() > 0) {
          builder.append(';');
        }
        builder.append(slot).append("=/proc/").append(pid).append("/fd/").append(ringSlot.fd);
      }
      return builder.toString();
    }
  }

  public static void releaseAllMemfdSlots() {
    synchronized (RING_LOCK) {
      for (int slot = 0; slot < RING_SLOTS.length; slot++) {
        RingSlot ringSlot = RING_SLOTS[slot];
        if (ringSlot == null) {
          continue;
        }
        if (ringSlot.data != null) {
          SysVSharedMemory.unmapSHMSegment(ringSlot.data, RING_SIZE);
          ringSlot.data = null;
        }
        if (ringSlot.fd >= 0) {
          XConnectorEpoll.closeFd(ringSlot.fd);
          ringSlot.fd = -1;
        }
        RING_SLOTS[slot] = null;
      }
    }
  }

  private static void prepareMemfdSlotsLocked(int slotCount) {
    int boundedSlotCount = Math.max(0, Math.min(slotCount, MAX_FAKE_INPUT_SLOTS));
    for (int slot = 0; slot < boundedSlotCount; slot++) {
      ensureRingSlotLocked(slot);
    }
  }

  private static RingSlot ensureRingSlotLocked(int slot) {
    if (slot < 0 || slot >= MAX_FAKE_INPUT_SLOTS) {
      return null;
    }
    RingSlot existing = RING_SLOTS[slot];
    if (existing != null && existing.fd >= 0 && existing.data != null) {
      return existing;
    }

    int fd = SysVSharedMemory.createMemoryFd("fakeinput-" + slot, RING_SIZE);
    if (fd < 0) {
      Log.e(TAG, "Failed to create fake input memfd for slot " + slot);
      return null;
    }

    ByteBuffer data = SysVSharedMemory.mapSHMSegment(fd, RING_SIZE, 0, false);
    if (data == null) {
      XConnectorEpoll.closeFd(fd);
      Log.e(TAG, "Failed to map fake input memfd for slot " + slot);
      return null;
    }

    data.order(ByteOrder.LITTLE_ENDIAN);
    data.putInt(RING_MAGIC_OFFSET, RING_MAGIC);
    data.putInt(RING_VERSION_OFFSET, RING_VERSION);
    data.putInt(RING_EVENT_SIZE_OFFSET, EVENT_SIZE);
    data.putInt(RING_CAPACITY_OFFSET, RING_CAPACITY_EVENTS);
    data.putLong(RING_WRITE_SEQ_OFFSET, 0L);
    data.putLong(RING_GENERATION_OFFSET, 0L);

    RingSlot ringSlot = new RingSlot();
    ringSlot.fd = fd;
    ringSlot.data = data;
    RING_SLOTS[slot] = ringSlot;
    Log.i(TAG, "Created fake input memfd ring for slot " + slot + ": fd=" + fd);
    return ringSlot;
  }

  private RingSlot ensureRingSlot() {
    synchronized (RING_LOCK) {
      return ensureRingSlotLocked(this.slot);
    }
  }

  private boolean activateRingSlot() {
    RingSlot ringSlot = ensureRingSlot();
    if (ringSlot == null || ringSlot.data == null) {
      return false;
    }
    synchronized (ringSlot) {
      if (!ringSlot.active) {
        if (ringSlot.everActivated) {
          ringSlot.generation++;
        } else {
          ringSlot.everActivated = true;
        }
        ringSlot.data.putLong(RING_WRITE_SEQ_OFFSET, 0L);
        ringSlot.data.putLong(RING_GENERATION_OFFSET, ringSlot.generation);
        ringSlot.active = true;
      }
    }
    return true;
  }

  private void deactivateRingSlot() {
    RingSlot ringSlot;
    synchronized (RING_LOCK) {
      ringSlot =
          this.slot >= 0 && this.slot < RING_SLOTS.length ? RING_SLOTS[this.slot] : null;
    }
    if (ringSlot == null) {
      return;
    }
    synchronized (ringSlot) {
      ringSlot.active = false;
    }
  }

  private boolean flushBufferToRing() {
    RingSlot ringSlot = ensureRingSlot();
    if (ringSlot == null || ringSlot.data == null) {
      return false;
    }

    ByteBuffer source = this.buffer.duplicate();
    source.order(ByteOrder.LITTLE_ENDIAN);
    synchronized (ringSlot) {
      ByteBuffer ring = ringSlot.data;
      long writeSeq = ring.getLong(RING_WRITE_SEQ_OFFSET);
      while (source.remaining() >= EVENT_SIZE) {
        int eventIndex = (int) (writeSeq % RING_CAPACITY_EVENTS);
        int targetOffset = RING_HEADER_SIZE + (eventIndex * EVENT_SIZE);
        for (int i = 0; i < EVENT_SIZE; i++) {
          ring.put(targetOffset + i, source.get());
        }
        writeSeq++;
      }
      ring.putLong(RING_WRITE_SEQ_OFFSET, writeSeq);
    }
    return true;
  }

  public synchronized boolean open() {
    if (this.destroyed) {
      return false;
    }
    if (this.isOpen) {
      return true;
    }
    try {
      this.eventFile.getParentFile().mkdirs();
      if (!this.eventFile.exists()) {
        this.eventFile.createNewFile();
      }
      if (!activateRingSlot()) {
        return false;
      }
      this.isOpen = true;
      Log.i(TAG, "Opened fake input ring: " + this.eventFile.getAbsolutePath());
      return true;
    } catch (IOException e) {
      Log.e(TAG, "Failed to open: " + e.getMessage());
      return false;
    }
  }

  public synchronized void close() {
    this.isOpen = false;
  }

  public synchronized void reset() {
    if (this.isOpen || open()) {
      this.buffer.clear();
      this.hasChanges = false;
      for (int i = 0; i < BUTTON_MAP.length; i++) {
        if (this.prevButtonStates[i]) {
          this.prevButtonStates[i] = false;
          writeEvent((short) 4, (short) 4, BUTTON_MAP[i]);
          writeEvent((short) 1, BUTTON_MAP[i], 0);
        }
      }
      if (this.prevThumbLX != 0) {
        this.prevThumbLX = 0;
        writeEvent((short) 3, (short) 0, 0);
      }
      if (this.prevThumbLY != 0) {
        this.prevThumbLY = 0;
        writeEvent((short) 3, (short) 1, 0);
      }
      if (this.prevThumbRX != 0) {
        this.prevThumbRX = 0;
        writeEvent((short) 3, (short) 3, 0);
      }
      if (this.prevThumbRY != 0) {
        this.prevThumbRY = 0;
        writeEvent((short) 3, (short) 4, 0);
      }
      if (this.prevTriggerL != 0) {
        this.prevTriggerL = 0;
        writeEvent((short) 3, (short) 10, 0);
      }
      if (this.prevTriggerR != 0) {
        this.prevTriggerR = 0;
        writeEvent((short) 3, (short) 9, 0);
      }
      if (this.prevHatX != 0) {
        this.prevHatX = 0;
        writeEvent((short) 3, (short) 16, 0);
      }
      if (this.prevHatY != 0) {
        this.prevHatY = 0;
        writeEvent((short) 3, (short) 17, 0);
      }
      if (this.hasChanges) {
        writeEvent((short) 0, (short) 0, 0);
        this.buffer.flip();
        if (!flushBufferToRing()) Log.e(TAG, "Reset write error: memfd ring unavailable");
        Log.i(TAG, "Reset fake input to neutral state: " + this.eventFile.getAbsolutePath());
        return;
      }
      Log.i(TAG, "Reset fake input to neutral state: " + this.eventFile.getAbsolutePath());
    }
  }

  public synchronized void softRelease() {
    reset();
    close();
    Log.i(TAG, "Soft released fake input: " + this.eventFile.getAbsolutePath());
  }

  public synchronized void destroy() {
    this.destroyed = true;
    reset();
    close();
    deactivateRingSlot();
    if (this.eventFile != null && this.eventFile.exists()) {
      boolean deleted = this.eventFile.delete();
      Log.i(
          TAG,
          "Deleted fake input discovery node: "
              + this.eventFile.getAbsolutePath()
              + " ("
              + deleted
              + ")");
    }
  }

  private void writeEvent(short type, short code, int value) {
    long timeMs = System.currentTimeMillis();
    this.buffer.putLong(timeMs / 1000);
    this.buffer.putLong((timeMs % 1000) * 1000);
    this.buffer.putShort(type);
    this.buffer.putShort(code);
    this.buffer.putInt(value);
    this.hasChanges = true;
  }

  private void writeButton(int i, boolean z) {
    if (i < 0 || i >= BUTTON_MAP.length || this.prevButtonStates[i] == z) {
      return;
    }
    this.prevButtonStates[i] = z;
    writeEvent((short) 4, (short) 4, BUTTON_MAP[i]);
    writeEvent((short) 1, BUTTON_MAP[i], z ? 1 : 0);
  }

  private void writeAxis(short code, int value, int[] prevRef, int index) {
    if (prevRef[index] == value) {
      return;
    }
    prevRef[index] = value;
    writeEvent((short) 3, code, value);
  }

  public void writeGamepadState(GamepadState state) throws IOException {
    int hatX;
    if (!this.isOpen && !open()) {
      return;
    }
    this.buffer.clear();
    this.hasChanges = false;
    for (int i = 0; i < 10; i++) {
      writeButton(i, state.isPressed((byte) i));
    }
    int lx = (int) (state.thumbLX * 32767.0f);
    int ly = (int) (state.thumbLY * 32767.0f);
    int rx = (int) (state.thumbRX * 32767.0f);
    int ry = (int) (state.thumbRY * 32767.0f);
    int tl = (int) (state.triggerL * 255.0f);
    int tr = (int) (state.triggerR * 255.0f);

    // The fake evdev file is effectively a queue, so unchanged axes must stay silent.
    if (lx != this.prevThumbLX) {
      this.prevThumbLX = lx;
      writeEvent((short) 3, (short) 0, lx);
    }
    if (ly != this.prevThumbLY) {
      this.prevThumbLY = ly;
      writeEvent((short) 3, (short) 1, ly);
    }
    if (rx != this.prevThumbRX) {
      this.prevThumbRX = rx;
      writeEvent((short) 3, (short) 3, rx);
    }
    if (ry != this.prevThumbRY) {
      this.prevThumbRY = ry;
      writeEvent((short) 3, (short) 4, ry);
    }
    if (tl != this.prevTriggerL) {
      this.prevTriggerL = tl;
      writeEvent((short) 3, (short) 10, tl);
    }
    if (tr != this.prevTriggerR) {
      this.prevTriggerR = tr;
      writeEvent((short) 3, (short) 9, tr);
    }

    int hatY = 1;
    if (state.dpad[3]) {
      hatX = -1;
    } else {
      hatX = state.dpad[1] ? 1 : 0;
    }
    if (state.dpad[0]) {
      hatY = -1;
    } else if (!state.dpad[2]) {
      hatY = 0;
    }
    if (hatX != this.prevHatX) {
      this.prevHatX = hatX;
      writeEvent((short) 3, (short) 16, hatX);
    }
    if (hatY != this.prevHatY) {
      this.prevHatY = hatY;
      writeEvent((short) 3, (short) 17, hatY);
    }
    if (this.hasChanges) {
      writeEvent((short) 0, (short) 0, 0);
      this.buffer.flip();
      if (!flushBufferToRing()) Log.e(TAG, "Gamepad write error: memfd ring unavailable");
    }
  }
}
