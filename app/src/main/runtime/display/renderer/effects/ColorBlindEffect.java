package com.winlator.cmod.runtime.display.renderer.effects;

public class ColorBlindEffect extends Effect {
    private float mode = 1.0f;

    public void setMode(int mode) {
        this.mode = mode;
    }

    @Override
    public int getNativeType() {
        return TYPE_COLORBLIND;
    }

    @Override
    public float[] getParams() {
        return new float[]{0f, mode, 0f, 0f};
    }

    @Override
    public void writeParams(float[] out, int offset) {
        out[offset] = 0f;
        out[offset + 1] = mode;
        out[offset + 2] = 0f;
        out[offset + 3] = 0f;
    }
}
