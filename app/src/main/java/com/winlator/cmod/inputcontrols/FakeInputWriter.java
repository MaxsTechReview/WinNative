package com.winlator.cmod.inputcontrols;

import android.util.Log;

import java.io.File;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

public class FakeInputWriter {
    private static final String TAG = "FakeInputWriter";
    private static final int EVENT_SIZE = 24;
    private static final int MAX_EVENTS_PER_UPDATE = 20; // Buttons + axes + sync
    private static final int BUFFER_SIZE = EVENT_SIZE * MAX_EVENTS_PER_UPDATE;

    // Event types
    public static final short EV_SYN = 0x00;
    public static final short EV_KEY = 0x01;
    public static final short EV_ABS = 0x03;
    public static final short EV_MSC = 0x04;

    // Event codes
    public static final short MSC_SCAN = 0x04;
    public static final short SYN_REPORT = 0x00;

    // Xbox 360 controller button codes
    public static final short BTN_A = 0x130;
    public static final short BTN_B = 0x131;
    public static final short BTN_X = 0x133;
    public static final short BTN_Y = 0x134;
    public static final short BTN_TL = 0x136;
    public static final short BTN_TR = 0x137;
    public static final short BTN_SELECT = 0x13A;
    public static final short BTN_START = 0x13B;
    public static final short BTN_THUMBL = 0x13D;
    public static final short BTN_THUMBR = 0x13E;

    // Absolute axis codes
    public static final short ABS_X = 0x00;
    public static final short ABS_Y = 0x01;
    public static final short ABS_RX = 0x03;
    public static final short ABS_RY = 0x04;
    public static final short ABS_HAT0X = 0x10;
    public static final short ABS_HAT0Y = 0x11;
    public static final short ABS_GAS = 0x09;
    public static final short ABS_BRAKE = 0x0A;

    // Button mapping
    private static final short[] BUTTON_MAP = {
            BTN_A, BTN_B, BTN_X, BTN_Y, BTN_TL, BTN_TR,
            BTN_SELECT, BTN_START, BTN_THUMBL, BTN_THUMBR
    };

    private final File eventFile;
    private RandomAccessFile raf;
    private FileChannel channel;
    private final ByteBuffer buffer;
    private boolean isOpen = false;
    private volatile boolean destroyed = false;

    private final boolean[] prevButtonStates = new boolean[12];
    private int prevThumbLX, prevThumbLY, prevThumbRX, prevThumbRY;
    private int prevTriggerL, prevTriggerR;
    private int prevHatX, prevHatY;
    private boolean hasChanges = false;

    public FakeInputWriter(String fakeInputPath, int slot) {
        this.eventFile = new File(fakeInputPath, "event" + slot);
        this.buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        this.buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    public synchronized boolean open() {
        if (destroyed)
            return false;
        if (isOpen)
            return true;

        try {
            eventFile.getParentFile().mkdirs();
            if (!eventFile.exists()) {
                eventFile.createNewFile();
            }

            raf = new RandomAccessFile(eventFile, "rw");
            raf.seek(raf.length());
            channel = raf.getChannel();
            isOpen = true;
            Log.i(TAG, "Opened fake input: " + eventFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to open: " + e.getMessage());
            return false;
        }
    }

    public synchronized void close() {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
            }
            channel = null;
        }
        if (raf != null) {
            try {
                raf.close();
            } catch (IOException e) {
            }
            raf = null;
        }
        isOpen = false;
    }

    public synchronized void reset() {
        if (!isOpen && !open())
            return;

        buffer.clear();
        hasChanges = false;

        for (int i = 0; i < BUTTON_MAP.length; i++) {
            if (prevButtonStates[i]) {
                prevButtonStates[i] = false;
                writeEvent(EV_MSC, MSC_SCAN, BUTTON_MAP[i]);
                writeEvent(EV_KEY, BUTTON_MAP[i], 0);
            }
        }

        if (prevThumbLX != 0) {
            prevThumbLX = 0;
            writeEvent(EV_ABS, ABS_X, 0);
        }
        if (prevThumbLY != 0) {
            prevThumbLY = 0;
            writeEvent(EV_ABS, ABS_Y, 0);
        }
        if (prevThumbRX != 0) {
            prevThumbRX = 0;
            writeEvent(EV_ABS, ABS_RX, 0);
        }
        if (prevThumbRY != 0) {
            prevThumbRY = 0;
            writeEvent(EV_ABS, ABS_RY, 0);
        }
        if (prevTriggerL != 0) {
            prevTriggerL = 0;
            writeEvent(EV_ABS, ABS_BRAKE, 0);
        }
        if (prevTriggerR != 0) {
            prevTriggerR = 0;
            writeEvent(EV_ABS, ABS_GAS, 0);
        }
        if (prevHatX != 0) {
            prevHatX = 0;
            writeEvent(EV_ABS, ABS_HAT0X, 0);
        }
        if (prevHatY != 0) {
            prevHatY = 0;
            writeEvent(EV_ABS, ABS_HAT0Y, 0);
        }

        if (hasChanges) {
            writeEvent(EV_SYN, SYN_REPORT, 0);
            buffer.flip();
            try {
                channel.write(buffer);
            } catch (IOException e) {
                Log.e(TAG, "Reset write error: " + e.getMessage());
            }
        }
        Log.i(TAG, "Reset fake input to neutral state: " + eventFile.getAbsolutePath());
    }

    public synchronized void softRelease() {
        reset();
        close();
        Log.i(TAG, "Soft released fake input: " + eventFile.getAbsolutePath());
    }

    public synchronized void destroy() {
        destroyed = true;
        reset();
        close();
        if (eventFile != null && eventFile.exists()) {
            boolean deleted = eventFile.delete();
            Log.i(TAG, "Deleted fake input: " + eventFile.getAbsolutePath() + " (" + deleted + ")");
        }
    }

    private void writeEvent(short type, short code, int value) {
        long timeMs = System.currentTimeMillis();
        buffer.putLong(timeMs / 1000);
        buffer.putLong((timeMs % 1000) * 1000);
        buffer.putShort(type);
        buffer.putShort(code);
        buffer.putInt(value);
        hasChanges = true;
    }

    private void writeButton(int idx, boolean pressed) {
        if (idx < 0 || idx >= BUTTON_MAP.length)
            return;
        if (prevButtonStates[idx] == pressed)
            return;
        prevButtonStates[idx] = pressed;
        writeEvent(EV_MSC, MSC_SCAN, BUTTON_MAP[idx]);
        writeEvent(EV_KEY, BUTTON_MAP[idx], pressed ? 1 : 0);
    }

    public void writeGamepadState(GamepadState state) {
        if (!isOpen && !open())
            return;

        buffer.clear();
        hasChanges = false;

        for (int i = 0; i < 10; i++) {
            writeButton(i, state.isPressed(i));
        }

        int lx = (int) (state.thumbLX * 32767);
        int ly = (int) (state.thumbLY * 32767);
        int rx = (int) (state.thumbRX * 32767);
        int ry = (int) (state.thumbRY * 32767);

        if (lx != prevThumbLX) {
            prevThumbLX = lx;
            writeEvent(EV_ABS, ABS_X, lx);
        }
        if (ly != prevThumbLY) {
            prevThumbLY = ly;
            writeEvent(EV_ABS, ABS_Y, ly);
        }
        if (rx != prevThumbRX) {
            prevThumbRX = rx;
            writeEvent(EV_ABS, ABS_RX, rx);
        }
        if (ry != prevThumbRY) {
            prevThumbRY = ry;
            writeEvent(EV_ABS, ABS_RY, ry);
        }

        int tl = (int) (state.triggerL * 255);
        int tr = (int) (state.triggerR * 255);
        if (tl != prevTriggerL) {
            prevTriggerL = tl;
            writeEvent(EV_ABS, ABS_BRAKE, tl);
        }
        if (tr != prevTriggerR) {
            prevTriggerR = tr;
            writeEvent(EV_ABS, ABS_GAS, tr);
        }

        int hatX = state.dpad[3] ? -1 : (state.dpad[1] ? 1 : 0);
        int hatY = state.dpad[0] ? -1 : (state.dpad[2] ? 1 : 0);
        if (hatX != prevHatX) {
            prevHatX = hatX;
            writeEvent(EV_ABS, ABS_HAT0X, hatX);
        }
        if (hatY != prevHatY) {
            prevHatY = hatY;
            writeEvent(EV_ABS, ABS_HAT0Y, hatY);
        }

        if (hasChanges) {
            writeEvent(EV_SYN, SYN_REPORT, 0);
            buffer.flip();
            try {
                channel.write(buffer);
            } catch (IOException e) {
                Log.e(TAG, "Write error: " + e.getMessage());
            }
        }
    }

    public boolean isOpen() {
        return isOpen;
    }
}
