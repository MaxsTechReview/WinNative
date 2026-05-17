package com.winlator.cmod.runtime.display.renderer.effects;

public class VividEffect extends Effect {
    private float strengthLevel = 1.0f;

    public void setLevel(float level) {
        this.strengthLevel = level;
    }

    public float getLevel() {
        return strengthLevel;
    }

    @Override
    public int getNativeType() {
        return TYPE_VIVID;
    }

    @Override
    public float[] getParams() {
        float saturation = strengthLevel * 0.05f + 1.0f;
        float contrast = strengthLevel * 0.02f + 1.0f;
        float sharpness = strengthLevel * 0.2f;
        return new float[]{0f, saturation, contrast, sharpness};
    }

    @Override
    public void writeParams(float[] out, int offset) {
        out[offset] = 0f;
        out[offset + 1] = strengthLevel * 0.05f + 1.0f;
        out[offset + 2] = strengthLevel * 0.02f + 1.0f;
        out[offset + 3] = strengthLevel * 0.2f;
    }
}
