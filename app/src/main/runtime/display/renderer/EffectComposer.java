package com.winlator.cmod.runtime.display.renderer;

import com.winlator.cmod.runtime.display.renderer.effects.Effect;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds the active list of post-process effects in the order they should be applied.
 * The Vulkan renderer reads this list each frame and runs the corresponding shader pipelines.
 *
 * <p>Public API is unchanged from the previous OpenGL implementation so callers
 * (e.g. {@code XServerDisplayActivity}) continue to work without modification.
 */
public class EffectComposer {
    private final List<Effect> effects = new ArrayList<>();
    private final VulkanRenderer renderer;
    private Effect[] cachedSnapshot = new Effect[0];

    public EffectComposer(VulkanRenderer renderer) {
        this.renderer = renderer;
    }

    public synchronized void addEffect(Effect effect) {
        if (effect == null) return;
        if (!effects.contains(effect)) {
            effects.add(effect);
            cachedSnapshot = effects.toArray(new Effect[0]);
        }
        if (renderer != null && renderer.xServerView != null) {
            renderer.xServerView.requestRender();
        }
    }

    public synchronized void removeEffect(Effect effect) {
        if (effects.remove(effect)) {
            cachedSnapshot = effects.toArray(new Effect[0]);
        }
        if (renderer != null && renderer.xServerView != null) {
            renderer.xServerView.requestRender();
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized <T extends Effect> T getEffect(Class<T> effectClass) {
        for (Effect e : effects) {
            if (e.getClass() == effectClass) return (T) e;
        }
        return null;
    }

    public synchronized boolean hasEffects() {
        return !effects.isEmpty();
    }

    /** Snapshot the active effects for the renderer's per-frame consumption. */
    public synchronized Effect[] snapshot() {
        return cachedSnapshot;
    }
}
