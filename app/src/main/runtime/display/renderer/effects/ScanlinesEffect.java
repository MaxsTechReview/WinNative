package com.winlator.cmod.runtime.display.renderer.effects;

public class ScanlinesEffect extends Effect {
    private float intensity = 0.5f;

    public void setIntensity(float intensity) {
        this.intensity = Math.min(1.0f, Math.max(0.0f, intensity));
    }

    @Override
    public int getNativeType() {
        return TYPE_SCANLINES;
    }

    @Override
    public float[] getParams() {
        return new float[]{0f, intensity, 0f, 0f};
    }

    @Override
    public void writeParams(float[] out, int offset) {
        out[offset] = 0f;
        out[offset + 1] = intensity;
        out[offset + 2] = 0f;
        out[offset + 3] = 0f;
    }
}
