package com.winlator.cmod.runtime.display.renderer.effects;

public class ColorGradeEffect extends Effect {
    private float saturation = 1.0f;
    private float temperature = 0.0f;
    private float tint = 0.0f;

    public void set(float saturation, float temperature, float tint) {
        this.saturation = saturation;
        this.temperature = temperature;
        this.tint = tint;
    }

    @Override
    public int getNativeType() {
        return TYPE_COLORGRADE;
    }

    @Override
    public float[] getParams() {
        return new float[]{0f, saturation, temperature, tint};
    }

    @Override
    public void writeParams(float[] out, int offset) {
        out[offset] = 0f;
        out[offset + 1] = saturation;
        out[offset + 2] = temperature;
        out[offset + 3] = tint;
    }
}
