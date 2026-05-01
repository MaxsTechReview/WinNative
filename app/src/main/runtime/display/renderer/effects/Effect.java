package com.winlator.cmod.runtime.display.renderer.effects;

public abstract class Effect {
    public static final int TYPE_CRT     = 0;
    public static final int TYPE_FSR     = 1;
    public static final int TYPE_HDR     = 2;
    public static final int TYPE_NATURAL = 3;

    public abstract int getNativeType();

    /**
     * Up to four floats forwarded to the native shader as push-constants:
     * [0] mode (interpreted by the shader),
     * [1] param0 (e.g. saturation, strength),
     * [2] param1 (e.g. contrast),
     * [3] param2 (e.g. sharpness).
     */
    public float[] getParams() {
        return new float[]{0f, 0f, 0f, 0f};
    }
}
