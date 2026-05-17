package com.winlator.cmod.runtime.display.renderer.effects;

/*
 * Runtime wrapper for Snapdragon Game Super Resolution 1.
 * The SGSR1 shader implementation is adapted from Qualcomm's SGSR v1 reference:
 * Copyright (c) 2023, Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 */
public class SGSRUpscaler extends Effect {
    private float sharpness = 1.0f;

    public void setSharpness(float sharpness) {
        this.sharpness = Math.min(1.0f, Math.max(0.0f, sharpness));
    }

    public float getSharpness() {
        return sharpness;
    }

    @Override
    public int getNativeType() {
        return TYPE_SGSR1;
    }

    @Override
    public float[] getParams() {
        return new float[]{0f, 0f, 0f, sharpness};
    }

    @Override
    public void writeParams(float[] out, int offset) {
        out[offset] = 0f;
        out[offset + 1] = 0f;
        out[offset + 2] = 0f;
        out[offset + 3] = sharpness;
    }
}
