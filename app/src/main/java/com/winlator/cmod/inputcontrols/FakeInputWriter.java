package com.winlator.cmod.inputcontrols;

import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

public class FakeInputWriter {
    public static final short ABS_BRAKE = 10;
    public static final short ABS_GAS = 9;
    public static final short ABS_HAT0X = 16;
    public static final short ABS_HAT0Y = 17;
    public static final short ABS_RX = 3;
    public static final short ABS_RY = 4;
    public static final short ABS_X = 0;
    public static final short ABS_Y = 1;
    public static final short BTN_A = 304;
    public static final short BTN_B = 305;
    public static final short BTN_SELECT = 314;
    public static final short BTN_START = 315;
    public static final short BTN_THUMBL = 317;
    public static final short BTN_THUMBR = 318;
    public static final short BTN_TL = 310;
    public static final short BTN_TL2 = 312;
    public static final short BTN_TR = 311;
    public static final short BTN_TR2 = 313;
    public static final short BTN_X = 307;
    public static final short BTN_Y = 308;
    private static final short[] BUTTON_MAP = {304, 305, 307, 308, 310, 311, 314, 315, 317, 318, 312, 313};
    public static final short EV_ABS = 3;
    public static final short EV_KEY = 1;
    public static final short EV_MSC = 4;
    public static final short EV_SYN = 0;
    public static final short MSC_SCAN = 4;
    public static final short SYN_REPORT = 0;
    private static final String TAG = "FakeInputWriter";
    private final ByteBuffer buffer;
    private FileChannel channel;
    private final File eventFile;
    private boolean hasChanges;
    private boolean isOpen = false;
    private int prevHatX;
    private int prevHatY;
    private int prevThumbLX;
    private int prevThumbLY;
    private int prevThumbRX;
    private int prevThumbRY;
    private int prevTriggerL;
    private int prevTriggerR;
    private RandomAccessFile raf;
    private final boolean[] prevButtonStates = new boolean[12];

    public FakeInputWriter(String fakeInputPath, int slot) {
        this.eventFile = new File(fakeInputPath, NotificationCompat.CATEGORY_EVENT + slot);
        ByteBuffer allocateDirect = ByteBuffer.allocateDirect(480);
        this.buffer = allocateDirect;
        allocateDirect.order(ByteOrder.LITTLE_ENDIAN);
    }

    public boolean open() {
        if (this.isOpen) {
            return true;
        }
        try {
            this.eventFile.getParentFile().mkdirs();
            if (!this.eventFile.exists()) {
                this.eventFile.createNewFile();
            }
            RandomAccessFile randomAccessFile = new RandomAccessFile(this.eventFile, "rw");
            this.raf = randomAccessFile;
            randomAccessFile.seek(randomAccessFile.length());
            this.channel = this.raf.getChannel();
            this.isOpen = true;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void close() {
        FileChannel fileChannel = this.channel;
        if (fileChannel != null) {
            try {
                fileChannel.close();
            } catch (IOException e) {
            }
            this.channel = null;
        }
        RandomAccessFile randomAccessFile = this.raf;
        if (randomAccessFile != null) {
            try {
                randomAccessFile.close();
            } catch (IOException e2) {
            }
            this.raf = null;
        }
        this.isOpen = false;
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

    private void writeButton(int idx, boolean pressed) {
        if (idx < 0) {
            return;
        }
        short[] sArr = BUTTON_MAP;
        if (idx >= sArr.length || this.prevButtonStates[idx] == pressed) {
            return;
        }
        this.prevButtonStates[idx] = pressed;
        writeEvent((short) 4, (short) 4, sArr[idx]);
        writeEvent((short) 1, sArr[idx], pressed ? 1 : 0);
    }

    public void writeGamepadState(GamepadState state) {
        if (!this.isOpen && !open()) {
            return;
        }
        this.buffer.clear();
        this.hasChanges = false;
        for (int i = 0; i < 12; i++) {
            writeButton(i, state.isPressed((byte) i));
        }
        int lx = (int) (state.thumbLX * 32767.0f);
        int ly = (int) (state.thumbLY * 32767.0f);
        int rx = (int) (state.thumbRX * 32767.0f);
        int ry = (int) (state.thumbRY * 32767.0f);
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
        int tl = (int) (state.triggerL * 255.0f);
        int tr = (int) (state.triggerR * 255.0f);
        if (tl != this.prevTriggerL) {
            this.prevTriggerL = tl;
            writeEvent((short) 3, (short) 10, tl);
        }
        if (tr != this.prevTriggerR) {
            this.prevTriggerR = tr;
            writeEvent((short) 3, (short) 9, tr);
        }
        int hatX = state.dpad[3] ? -1 : state.dpad[1] ? 1 : 0;
        int hatY = state.dpad[0] ? -1 : state.dpad[2] ? 1 : 0;
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
            try {
                this.channel.write(this.buffer);
            } catch (IOException e) {
                Log.e(TAG, "Write error: " + e.getMessage());
            }
        }
    }

    public boolean isOpen() {
        return this.isOpen;
    }
}
