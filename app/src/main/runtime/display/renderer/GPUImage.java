package com.winlator.cmod.runtime.display.renderer;

import androidx.annotation.Keep;
import com.winlator.cmod.runtime.display.xserver.Drawable;
import java.nio.ByteBuffer;

/**
 * Vulkan-backed AHardwareBuffer texture.
 *
 * <p>Two creation paths:
 * <ul>
 *   <li><b>Local</b>: {@link #GPUImage(short, short)} allocates a CPU-mappable BGRA AHB,
 *       locks it for CPU read+write (so the X server can push pixels), and lazily imports it
 *       as a sampleable VkImage on first {@link #allocateTexture}.</li>
 *   <li><b>Imported</b>: {@link #GPUImage(int)} reads an existing AHB handle from a Unix
 *       socket (DRI3 zero-copy path); no CPU mapping.</li>
 * </ul>
 */
public class GPUImage extends Texture {
    private long ahbPtr = 0;
    private ByteBuffer virtualData;
    private short stride;
    private boolean locked;
    private boolean cpuAccessible;
    private boolean samplingFailed;
    private static boolean supported = false;

    public GPUImage(short width, short height) {
        try {
            cpuAccessible = true;
            ahbPtr = nativeAhbCreate(width, height);
            if (ahbPtr == 0) return;
            virtualData = nativeAhbLock(ahbPtr);
            locked = virtualData != null && stride > 0;
            if (!locked) {
                nativeAhbDestroy(ahbPtr, false);
                ahbPtr = 0;
                virtualData = null;
            }
        } catch (Throwable e) {
            System.err.println("Error: Failed to create GPUImage: " + e.getMessage());
            destroy();
        }
    }

    public GPUImage(int socketFd) {
        try {
            cpuAccessible = false;
            ahbPtr = nativeAhbImportFromSocket(socketFd);
        } catch (Throwable e) {
            System.err.println("Error: Failed to import GPUImage: " + e.getMessage());
            destroy();
        }
    }

    @Override
    public void allocateTexture(short width, short height, ByteBuffer data) {
        if (isAllocated()) return;
        long renderer = getRendererHandle();
        if (renderer == 0 || ahbPtr == 0) return;
        nativeHandle = nativeImportAhbToVulkan(renderer, ahbPtr, true);
        if (nativeHandle == 0) {
            samplingFailed = true;
        } else {
            handleGeneration = getRendererGeneration();
        }
    }

    @Override
    public void updateFromDrawable(Drawable drawable) {
        if (!isAllocated()) allocateTexture(drawable.width, drawable.height, null);
        // AHB-backed image is GPU-shared with the producer; no upload needed.
        needsUpdate = false;
    }

    @Override
    boolean appendUploadFromDrawable(Drawable drawable, UploadBatch batch) {
        updateFromDrawable(drawable);
        return false;
    }

    public short getStride() {
        return stride;
    }

    @Keep
    private void setStride(short stride) {
        this.stride = stride;
    }

    public ByteBuffer getVirtualData() {
        return virtualData;
    }

    public boolean isValid() {
        return ahbPtr != 0 && (!cpuAccessible || (virtualData != null && stride > 0));
    }

    public boolean hasSamplingFailed() {
        return samplingFailed;
    }

    @Override
    public void destroy() {
        super.destroy();
        if (ahbPtr != 0) {
            nativeAhbDestroy(ahbPtr, locked);
            ahbPtr = 0;
        }
        locked = false;
        virtualData = null;
        samplingFailed = false;
    }

    public static boolean isSupported() {
        return supported;
    }

    public static void checkIsSupported() {
        final short size = 8;
        GPUImage probe = null;
        try {
            probe = new GPUImage(size, size);
            probe.allocateTexture(size, size, null);
            supported = probe.isValid() && probe.getNativeHandle() != 0;
        } catch (Throwable e) {
            supported = false;
            System.err.println("Error: GPUImage support probe failed: " + e.getMessage());
        } finally {
            if (probe != null) probe.destroy();
        }
    }

    private native long nativeAhbCreate(short width, short height);
    private native long nativeAhbImportFromSocket(int socketFd);
    private native ByteBuffer nativeAhbLock(long ahbPtr);
    private native void nativeAhbDestroy(long ahbPtr, boolean locked);

    private static native long nativeImportAhbToVulkan(long rendererHandle, long ahbPtr, boolean transferOwnership);
}
