package com.winlator.cmod.runtime.display.renderer;

import com.winlator.cmod.runtime.display.xserver.Drawable;
import java.nio.ByteBuffer;

/**
 * Vulkan-backed texture. The underlying VkImage / VkImageView / VkSampler / descriptor set
 * are owned by native code; Java holds a pointer plus the renderer pointer needed to free it.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>{@link #allocateTexture(short, short, ByteBuffer)} synchronously creates and uploads
 *       to a VkImage. Blocks the calling thread for a one-shot transfer submit.</li>
 *   <li>{@link #updateFromDrawable(Drawable)} re-uploads the drawable's data when {@code needsUpdate}.</li>
 *   <li>{@link #destroy()} schedules deferred destruction via the renderer's graveyard.</li>
 * </ul>
 */
public class Texture {
    static {
        System.loadLibrary("winlator");
    }

    protected long nativeHandle = 0;
    protected boolean needsUpdate = true;
    protected long handleGeneration = 0;
    private int dirtyX = 0;
    private int dirtyY = 0;
    private int dirtyWidth = -1;
    private int dirtyHeight = -1;

    private static long sRendererHandle = 0;
    private static long sRendererGeneration = 0;

    /** Called by the renderer at startup so static texture create/destroy methods know which device to use. */
    public static void setRendererHandle(long handle) {
        if (handle != sRendererHandle) {
            sRendererGeneration++;
            sRendererHandle = handle;
        }
    }

    public static long getRendererHandle() {
        return sRendererHandle;
    }

    public static long getRendererGeneration() {
        return sRendererGeneration;
    }

    public Texture() {}

    // A renderer teardown frees every VkTexture it owns. Java handles from that generation
    // are dangling pointers; drop them so the next access reallocates against the live renderer.
    protected void invalidateIfStale() {
        if (nativeHandle != 0 && handleGeneration != sRendererGeneration) {
            nativeHandle = 0;
            needsUpdate = true;
        }
    }

    public void allocateTexture(short width, short height, ByteBuffer data) {
        invalidateIfStale();
        if (nativeHandle != 0 || sRendererHandle == 0) return;
        int strideBytes = data != null ? data.capacity() / Math.max(1, height) : width * 4;
        int stridePixels = Math.max(1, strideBytes / 4);
        nativeHandle = nativeAllocate(sRendererHandle, width, height, data, stridePixels);
        if (nativeHandle != 0) {
            handleGeneration = sRendererGeneration;
            needsUpdate = false;
            clearDirtyRect();
        }
    }

    public void updateFromDrawable(Drawable drawable) {
        if (sRendererHandle == 0) return;
        invalidateIfStale();
        ByteBuffer data = drawable.getData();
        if (data == null) return;

        if (nativeHandle == 0) {
            allocateTexture(drawable.width, drawable.height, data);
            return;
        }
        if (needsUpdate) {
            int strideBytes = data.capacity() / Math.max(1, drawable.height);
            int stridePixels = Math.max(1, strideBytes / 4);
            int x = dirtyWidth > 0 ? dirtyX : 0;
            int y = dirtyHeight > 0 ? dirtyY : 0;
            int w = dirtyWidth > 0 ? dirtyWidth : drawable.width;
            int h = dirtyHeight > 0 ? dirtyHeight : drawable.height;
            if (nativeUpdate(sRendererHandle, nativeHandle, drawable.width, drawable.height,
                             data, stridePixels, x, y, w, h)) {
                needsUpdate = false;
                clearDirtyRect();
            }
        }
    }

    public boolean isAllocated() {
        invalidateIfStale();
        return nativeHandle != 0;
    }

    public boolean isNeedsUpdate() {
        return needsUpdate;
    }

    public void setNeedsUpdate(boolean needsUpdate) {
        this.needsUpdate = needsUpdate;
        if (needsUpdate) {
            dirtyX = 0;
            dirtyY = 0;
            dirtyWidth = -1;
            dirtyHeight = -1;
        } else {
            clearDirtyRect();
        }
    }

    public void markDirty(int x, int y, int width, int height, int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0) return;
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        int x1 = Math.min(textureWidth, x + width);
        int y1 = Math.min(textureHeight, y + height);
        if (x1 <= x0 || y1 <= y0) return;

        needsUpdate = true;
        if (dirtyWidth <= 0 || dirtyHeight <= 0) {
            dirtyX = x0;
            dirtyY = y0;
            dirtyWidth = x1 - x0;
            dirtyHeight = y1 - y0;
            return;
        }

        int oldX1 = dirtyX + dirtyWidth;
        int oldY1 = dirtyY + dirtyHeight;
        dirtyX = Math.min(dirtyX, x0);
        dirtyY = Math.min(dirtyY, y0);
        dirtyWidth = Math.max(oldX1, x1) - dirtyX;
        dirtyHeight = Math.max(oldY1, y1) - dirtyY;
    }

    private void clearDirtyRect() {
        dirtyX = 0;
        dirtyY = 0;
        dirtyWidth = 0;
        dirtyHeight = 0;
    }

    public void destroy() {
        invalidateIfStale();
        if (nativeHandle != 0 && sRendererHandle != 0) {
            nativeDestroy(sRendererHandle, nativeHandle);
            nativeHandle = 0;
        }
    }

    public long getNativeHandle() {
        invalidateIfStale();
        return nativeHandle;
    }

    private static native long nativeAllocate(long rendererHandle, int width, int height,
                                              ByteBuffer data, int stridePixels);
    private static native boolean nativeUpdate(long rendererHandle, long texHandle, int width,
                                               int height, ByteBuffer data, int stridePixels,
                                               int dirtyX, int dirtyY, int dirtyWidth,
                                               int dirtyHeight);
    private static native void nativeDestroy(long rendererHandle, long texHandle);
}
