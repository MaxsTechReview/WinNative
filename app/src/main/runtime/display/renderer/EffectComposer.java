package com.winlator.cmod.runtime.display.renderer;

import android.opengl.GLES20;
import com.winlator.cmod.runtime.display.renderer.effects.Effect;
import com.winlator.cmod.runtime.display.renderer.material.ShaderMaterial;
import java.util.ArrayList;
import java.util.List;

public class EffectComposer {
    private static final String TAG = "EffectComposer";
    private boolean isRendering = false;
    private final List<Effect> effects = new ArrayList<>();
    private RenderTarget readBuffer;
    private RenderTarget writeBuffer;
    private final GLRenderer renderer;
    private int lastWidth = 0;
    private int lastHeight = 0;

    public EffectComposer(GLRenderer renderer) {
        this.renderer = renderer;
    }

    private void initBuffers(int width, int height) {
        if (readBuffer == null || width != lastWidth || height != lastHeight) {
            if (readBuffer != null) {
                GLES20.glDeleteFramebuffers(1, new int[]{readBuffer.getFramebuffer()}, 0);
            }
            if (writeBuffer != null) {
                GLES20.glDeleteFramebuffers(1, new int[]{writeBuffer.getFramebuffer()}, 0);
            }
            readBuffer = new RenderTarget();
            readBuffer.allocateFramebuffer(width, height);
            writeBuffer = new RenderTarget();
            writeBuffer.allocateFramebuffer(width, height);
            lastWidth = width;
            lastHeight = height;
        }
    }

    public synchronized void addEffect(Effect effect) {
        if (!effects.contains(effect)) {
            effects.add(effect);
        }
        renderer.xServerView.requestRender();
    }

    public synchronized <T extends Effect> T getEffect(Class<T> effectClass) {
        for (Effect effect : effects) {
            if (effect.getClass() == effectClass) {
                return effectClass.cast(effect);
            }
        }
        return null;
    }

    public synchronized boolean hasEffects() {
        return !effects.isEmpty();
    }

    public synchronized void removeEffect(Effect effect) {
        effects.remove(effect);
        renderer.xServerView.requestRender();
    }

    public synchronized void render() {
        if (isRendering) return;
        isRendering = true;

        try {
            int width = renderer.getSurfaceWidth();
            int height = renderer.getSurfaceHeight();
            initBuffers(width, height);

            if (hasEffects()) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, readBuffer.getFramebuffer());
            } else {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            }

            renderer.drawFrame();

            for (int i = 0; i < effects.size(); i++) {
                Effect effect = effects.get(i);
                boolean renderToScreen = (i == effects.size() - 1);
                int targetFramebuffer = renderToScreen ? 0 : writeBuffer.getFramebuffer();

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetFramebuffer);
                GLES20.glViewport(0, 0, width, height);
                renderer.setViewportNeedsUpdate(true);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

                renderEffect(effect);
                swapBuffers();
            }
        } finally {
            isRendering = false;
        }
    }

    private void renderEffect(Effect effect) {
        ShaderMaterial material = effect.getMaterial();
        if (material == null) return;

        material.use();
        renderer.getQuadVertices().bind(material.programId);

        material.setUniformVec2("resolution", renderer.surfaceWidth, renderer.surfaceHeight);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, readBuffer.getTextureId());
        material.setUniformInt("screenTexture", 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, renderer.quadVertices.count());
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    private void swapBuffers() {
        RenderTarget tmp = writeBuffer;
        writeBuffer = readBuffer;
        readBuffer = tmp;
    }
}
