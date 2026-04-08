package com.winlator.cmod.inputcontrols;

import java.nio.ByteBuffer;

public class GamepadState {
    public float thumbLX = 0;
    public float thumbLY = 0;
    public float thumbRX = 0;
    public float thumbRY = 0;
    public float triggerL = 0;
    public float triggerR = 0;
    public final boolean[] dpad = new boolean[4];
    public short buttons = 0;

    public static final int BUTTON_A = 0;
    public static final int BUTTON_B = 1;
    public static final int BUTTON_X = 2;
    public static final int BUTTON_Y = 3;
    public static final int BUTTON_L1 = 4;
    public static final int BUTTON_R1 = 5;
    public static final int BUTTON_SELECT = 6;
    public static final int BUTTON_START = 7;
    public static final int BUTTON_L3 = 8;
    public static final int BUTTON_R3 = 9;
    public static final int BUTTON_L2 = 10;
    public static final int BUTTON_R2 = 11;
    public static final int BUTTON_GUIDE = 12;

    public static final int BUTTON_DPAD_UP = 13;
    public static final int BUTTON_DPAD_DOWN = 14;
    public static final int BUTTON_DPAD_LEFT = 15;
    public static final int BUTTON_DPAD_RIGHT = 16;

    public byte getPovHat() {
        if (this.dpad[0] && this.dpad[1]) return (byte) 1;
        if (this.dpad[1] && this.dpad[2]) return (byte) 3;
        if (this.dpad[2] && this.dpad[3]) return (byte) 5;
        if (this.dpad[3] && this.dpad[0]) return (byte) 7;
        if (this.dpad[0]) return (byte) 0;
        if (this.dpad[1]) return (byte) 2;
        if (this.dpad[2]) return (byte) 4;
        return this.dpad[3] ? (byte) 6 : (byte) -1;
    }

    public void writeTo(ByteBuffer buffer) {
        // Must match evshim.c: struct gamepad_io
        // Axes: lx, ly, rx, ry, lt, rt (int16_t * 6 = 12 bytes)
        buffer.putShort((short) (this.thumbLX * 32767.0f));
        buffer.putShort((short) (this.thumbLY * 32767.0f));
        buffer.putShort((short) (this.thumbRX * 32767.0f));
        buffer.putShort((short) (this.thumbRY * 32767.0f));
        buffer.putShort((short) (this.triggerL * 32767.0f));
        buffer.putShort((short) (this.triggerR * 32767.0f));

        // Buttons: uint8_t[15] (15 bytes)
        for (int i = 0; i < 15; i++) {
            buffer.put((byte) (isPressed(i) ? 1 : 0));
        }

        // Hat/DPad: uint8_t (1 byte)
        buffer.put(getPovHat());

        // Padding: uint8_t[4] (4 bytes)
        buffer.putInt(0); 

        // Rumble: uint16_t * 2 (4 bytes) - Read back from C++ if needed
        // (Position should now be 12 + 15 + 1 + 4 = 32)
    }

    public void setPressed(int buttonIdx, boolean pressed) {
        if (buttonIdx >= BUTTON_DPAD_UP && buttonIdx <= BUTTON_DPAD_RIGHT) {
            this.dpad[buttonIdx - BUTTON_DPAD_UP] = pressed;
        } else {
            int flag = 1 << buttonIdx;
            if (pressed) {
                this.buttons = (short) (this.buttons | flag);
            } else {
                this.buttons = (short) (this.buttons & (~flag));
            }
        }
    }

    public boolean isPressed(int buttonIdx) {
        return (this.buttons & (1 << buttonIdx)) != 0;
    }

    public boolean isButtonPressed(int buttonCode) {
        if (buttonCode == BUTTON_DPAD_UP) return dpad[0];
        if (buttonCode == BUTTON_DPAD_RIGHT) return dpad[1];
        if (buttonCode == BUTTON_DPAD_DOWN) return dpad[2];
        if (buttonCode == BUTTON_DPAD_LEFT) return dpad[3];
        if (buttonCode == BUTTON_GUIDE) return false;
        return isPressed(buttonCode);
    }

    public byte getDPadX() {
        return (byte) (this.dpad[1] ? 1 : this.dpad[3] ? -1 : 0);
    }

    public byte getDPadY() {
        return (byte) (this.dpad[0] ? -1 : this.dpad[2] ? 1 : 0);
    }

    public void copy(GamepadState other) {
        this.thumbLX = other.thumbLX;
        this.thumbLY = other.thumbLY;
        this.thumbRX = other.thumbRX;
        this.thumbRY = other.thumbRY;
        this.triggerL = other.triggerL;
        this.triggerR = other.triggerR;
        this.buttons = other.buttons;
        System.arraycopy(other.dpad, 0, this.dpad, 0, 4);
    }
}
