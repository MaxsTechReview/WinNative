package com.winlator.cmod.runtime.display.renderer.effects;

public class HDREffect extends Effect {
    private boolean enabled = true;

    public void setStrength(float s) {
        this.enabled = s > 0.5f;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public int getNativeType() {
        return TYPE_HDR;
    }
}
