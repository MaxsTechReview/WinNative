package com.winlator.cmod.runtime.display.renderer;

/** Generic surface-driven render callback. The Vulkan renderer implements this. */
public interface RenderCallback {
    void onSurfaceCreated();
    void onSurfaceChanged(int width, int height);
    void onSurfaceDestroyed();
    /** Called on the render thread for each frame to build and submit. */
    void onDrawFrame();
}
