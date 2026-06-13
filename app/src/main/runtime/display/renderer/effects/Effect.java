package com.winlator.cmod.runtime.display.renderer.effects;

public abstract class Effect {
    public static final int TYPE_CRT      = 0;
    public static final int TYPE_VIVID    = 1;
    public static final int TYPE_HDR      = 2;
    public static final int TYPE_NATURAL  = 3;
    public static final int TYPE_SGSR1    = 4;
    public static final int TYPE_TOON      = 5;
    public static final int TYPE_NTSC      = 6;
    public static final int TYPE_COLORADJ  = 7;
    public static final int TYPE_COLORGRADE = 8;
    public static final int TYPE_SHARPEN   = 9;
    public static final int TYPE_SCANLINES = 10;
    public static final int TYPE_NTSC2     = 11;
    public static final int TYPE_COLORBLIND = 12;
    public static final int TYPE_PIXELATE  = 13;

    public abstract int getNativeType();

    /**
     * Up to four floats forwarded to the native effect path:
     * [0] mode/reserved,
     * [1] param0 (e.g. SGSR upscale factor, saturation, strength),
     * [2] param1 (e.g. contrast),
     * [3] param2 (e.g. sharpness).
     */
    public float[] getParams() {
        return new float[]{0f, 0f, 0f, 0f};
    }

    public void writeParams(float[] out, int offset) {
        out[offset] = 0f;
        out[offset + 1] = 0f;
        out[offset + 2] = 0f;
        out[offset + 3] = 0f;
    }
}
