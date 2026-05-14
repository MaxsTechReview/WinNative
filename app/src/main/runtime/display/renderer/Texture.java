package com.winlator.cmod.runtime.display.renderer;

import com.winlator.cmod.runtime.display.xserver.Drawable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
    public static final int MAX_UPLOAD_RECTS = 8;
    private static final int BATCH_ENTRY_SIZE = 48;
    private static final long MAX_BATCH_BYTES = 16L * 1024L * 1024L;

    static {
        System.loadLibrary("winlator");
    }

    protected long nativeHandle = 0;
    protected boolean needsUpdate = true;
    protected long handleGeneration = 0;
    private final int[] dirtyRects = new int[MAX_UPLOAD_RECTS * 4];
    private int dirtyRectCount = 0;
    private boolean dirtyFull = true;
    private long dirtySerial = 1;

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
            dirtyFull = true;
            dirtyRectCount = 0;
            dirtySerial++;
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
            int stridePixels = stridePixels(data, drawable.height);
            int[] bounds = dirtyBounds(drawable.width, drawable.height);
            int x = bounds[0];
            int y = bounds[1];
            int w = bounds[2];
            int h = bounds[3];
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
        dirtySerial++;
        if (needsUpdate) {
            dirtyFull = true;
            dirtyRectCount = 0;
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
        dirtySerial++;
        if (dirtyFull) return;

        if (dirtyRectCount < MAX_UPLOAD_RECTS) {
            int off = dirtyRectCount * 4;
            dirtyRects[off] = x0;
            dirtyRects[off + 1] = y0;
            dirtyRects[off + 2] = x1 - x0;
            dirtyRects[off + 3] = y1 - y0;
            dirtyRectCount++;
            return;
        }

        int minX = x0;
        int minY = y0;
        int maxX = x1;
        int maxY = y1;
        for (int i = 0; i < dirtyRectCount; i++) {
            int off = i * 4;
            minX = Math.min(minX, dirtyRects[off]);
            minY = Math.min(minY, dirtyRects[off + 1]);
            maxX = Math.max(maxX, dirtyRects[off] + dirtyRects[off + 2]);
            maxY = Math.max(maxY, dirtyRects[off + 1] + dirtyRects[off + 3]);
        }
        dirtyRects[0] = minX;
        dirtyRects[1] = minY;
        dirtyRects[2] = maxX - minX;
        dirtyRects[3] = maxY - minY;
        dirtyRectCount = 1;
    }

    boolean appendUploadFromDrawable(Drawable drawable, UploadBatch batch) {
        if (sRendererHandle == 0) return false;
        invalidateIfStale();
        ByteBuffer data = drawable.getData();
        if (data == null) return false;

        if (nativeHandle == 0) {
            allocateTexture(drawable.width, drawable.height, data);
            return false;
        }
        if (!needsUpdate) return false;

        int stridePixels = stridePixels(data, drawable.height);
        long serial = dirtySerial;
        if (dirtyFull || dirtyRectCount == 0) {
            if (!batch.add(this, drawable, serial, data, drawable.width, drawable.height,
                    stridePixels, 0, 0, drawable.width, drawable.height)) {
                updateFromDrawable(drawable);
                return false;
            }
            return true;
        }

        for (int i = 0; i < dirtyRectCount; i++) {
            int off = i * 4;
            if (!batch.add(this, drawable, serial, data, drawable.width, drawable.height,
                    stridePixels, dirtyRects[off], dirtyRects[off + 1],
                    dirtyRects[off + 2], dirtyRects[off + 3])) {
                updateFromDrawable(drawable);
                return false;
            }
        }
        return true;
    }

    private void markUploaded(long serial) {
        if (dirtySerial != serial) return;
        needsUpdate = false;
        clearDirtyRect();
    }

    private int[] dirtyBounds(int textureWidth, int textureHeight) {
        if (dirtyFull || dirtyRectCount == 0) {
            return new int[]{0, 0, textureWidth, textureHeight};
        }

        int minX = textureWidth;
        int minY = textureHeight;
        int maxX = 0;
        int maxY = 0;
        for (int i = 0; i < dirtyRectCount; i++) {
            int off = i * 4;
            minX = Math.min(minX, dirtyRects[off]);
            minY = Math.min(minY, dirtyRects[off + 1]);
            maxX = Math.max(maxX, dirtyRects[off] + dirtyRects[off + 2]);
            maxY = Math.max(maxY, dirtyRects[off + 1] + dirtyRects[off + 3]);
        }
        return new int[]{minX, minY, Math.max(0, maxX - minX), Math.max(0, maxY - minY)};
    }

    private static int stridePixels(ByteBuffer data, int height) {
        int strideBytes = data.capacity() / Math.max(1, height);
        return Math.max(1, strideBytes / 4);
    }

    private void clearDirtyRect() {
        dirtyFull = false;
        dirtyRectCount = 0;
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
    private static native boolean nativeBatchUpdate(long rendererHandle, ByteBuffer entries,
                                                    Object[] buffers, int count);
    private static native void nativeDestroy(long rendererHandle, long texHandle);

    public static final class UploadBatch {
        private final ByteBuffer entries;
        private final Object[] buffers;
        private final Texture[] textures;
        private final Drawable[] drawables;
        private final long[] serials;
        private int count;
        private long totalBytes;

        public UploadBatch(int capacity) {
            entries = ByteBuffer.allocateDirect(capacity * BATCH_ENTRY_SIZE)
                    .order(ByteOrder.nativeOrder());
            buffers = new Object[capacity];
            textures = new Texture[capacity];
            drawables = new Drawable[capacity];
            serials = new long[capacity];
        }

        public void reset() {
            for (int i = 0; i < count; i++) {
                buffers[i] = null;
                textures[i] = null;
                drawables[i] = null;
            }
            count = 0;
            totalBytes = 0;
        }

        private boolean add(Texture texture, Drawable drawable, long serial, ByteBuffer data,
                            int width, int height, int stridePixels,
                            int dirtyX, int dirtyY, int dirtyWidth, int dirtyHeight) {
            if (count >= buffers.length) return false;
            int off = count * BATCH_ENTRY_SIZE;
            entries.putLong(off, texture.nativeHandle);
            entries.putInt(off + 8, width);
            entries.putInt(off + 12, height);
            entries.putInt(off + 16, stridePixels);
            entries.putInt(off + 20, dirtyX);
            entries.putInt(off + 24, dirtyY);
            entries.putInt(off + 28, dirtyWidth);
            entries.putInt(off + 32, dirtyHeight);
            entries.putInt(off + 36, count);
            buffers[count] = data;
            textures[count] = texture;
            drawables[count] = drawable;
            serials[count] = serial;
            count++;
            totalBytes += (long) dirtyWidth * Math.max(0, dirtyHeight) * 4L;
            return true;
        }

        public void flush(long rendererHandle) {
            if (count == 0 || rendererHandle == 0) return;
            if (count == 1 || totalBytes > MAX_BATCH_BYTES) {
                for (int i = 0; i < count; i++) {
                    synchronized (drawables[i].renderLock) {
                        textures[i].updateFromDrawable(drawables[i]);
                    }
                }
                return;
            }
            boolean ok = nativeBatchUpdate(rendererHandle, entries, buffers, count);
            if (ok) {
                for (int i = 0; i < count; i++) {
                    synchronized (drawables[i].renderLock) {
                        textures[i].markUploaded(serials[i]);
                    }
                }
            } else {
                for (int i = 0; i < count; i++) {
                    synchronized (drawables[i].renderLock) {
                        textures[i].updateFromDrawable(drawables[i]);
                    }
                }
            }
        }
    }
}
