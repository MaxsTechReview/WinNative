package com.winlator.cmod.runtime.display.renderer.effects;

public class SharpenEffect extends Effect {
    private float strength = 0.5f;

    public void setStrength(float strength) {
        this.strength = Math.min(1.0f, Math.max(0.0f, strength));
    }

    @Override
    public int getNativeType() {
        return TYPE_SHARPEN;
    }

    @Override
    public float[] getParams() {
        return new float[]{0f, strength, 0f, 0f};
    }

    @Override
    public void writeParams(float[] out, int offset) {
        out[offset] = 0f;
        out[offset + 1] = strength;
        out[offset + 2] = 0f;
        out[offset + 3] = 0f;
    }
}
