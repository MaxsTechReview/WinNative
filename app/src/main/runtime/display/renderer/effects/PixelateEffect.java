package com.winlator.cmod.runtime.display.renderer.effects;

public class PixelateEffect extends Effect {
    private float blockSize = 6.0f;

    public void setBlockSize(float blockSize) {
        this.blockSize = Math.max(1.0f, blockSize);
    }

    @Override
    public int getNativeType() {
        return TYPE_PIXELATE;
    }

    @Override
    public float[] getParams() {
        return new float[]{0f, blockSize, 0f, 0f};
    }

    @Override
    public void writeParams(float[] out, int offset) {
        out[offset] = 0f;
        out[offset + 1] = blockSize;
        out[offset + 2] = 0f;
        out[offset + 3] = 0f;
    }
}
