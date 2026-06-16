package com.winlator.cmod.runtime.display.renderer.effects;

public class ColorAdjustEffect extends Effect {
    private float brightness = 0.0f;
    private float contrast = 0.0f;
    private float gamma = 1.0f;

    public void set(float brightness, float contrast, float gamma) {
        this.brightness = brightness;
        this.contrast = contrast;
        this.gamma = gamma;
    }

    @Override
    public int getNativeType() {
        return TYPE_COLORADJ;
    }

    @Override
    public float[] getParams() {
        return new float[]{0f, brightness, contrast, gamma};
    }

    @Override
    public void writeParams(float[] out, int offset) {
        out[offset] = 0f;
        out[offset + 1] = brightness;
        out[offset + 2] = contrast;
        out[offset + 3] = gamma;
    }
}
