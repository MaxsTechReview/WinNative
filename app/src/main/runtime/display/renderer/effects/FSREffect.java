package com.winlator.cmod.runtime.display.renderer.effects;

public class FSREffect extends Effect {
    public static final int MODE_SUPER_RESOLUTION = 0;
    public static final int MODE_DLS = 1;

    private int mode = MODE_SUPER_RESOLUTION;
    private float strengthLevel = 1.0f;

    public void setMode(int mode) {
        this.mode = mode;
    }

    public int getMode() {
        return mode;
    }

    public void setLevel(float level) {
        this.strengthLevel = level;
    }

    public float getLevel() {
        return strengthLevel;
    }

    @Override
    public int getNativeType() {
        return TYPE_FSR;
    }

    @Override
    public float[] getParams() {
        // [mode, saturation, contrast, sharpness] — all forwarded as fragment push constants.
        if (mode == MODE_DLS) {
            float saturation = strengthLevel * 0.05f + 1.0f;
            float contrast   = strengthLevel * 0.02f + 1.0f;
            float sharpness  = strengthLevel * 0.2f;
            return new float[]{(float) mode, saturation, contrast, sharpness};
        }
        float sharpness = strengthLevel * 0.5f;
        return new float[]{(float) mode, 0f, 0f, sharpness};
    }

    @Override
    public void writeParams(float[] out, int offset) {
        out[offset] = (float) mode;
        if (mode == MODE_DLS) {
            out[offset + 1] = strengthLevel * 0.05f + 1.0f;
            out[offset + 2] = strengthLevel * 0.02f + 1.0f;
            out[offset + 3] = strengthLevel * 0.2f;
        } else {
            out[offset + 1] = 0f;
            out[offset + 2] = 0f;
            out[offset + 3] = strengthLevel * 0.5f;
        }
    }
}
