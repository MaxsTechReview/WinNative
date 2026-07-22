package com.winlator.cmod.runtime.display.renderer;

import android.util.Log;
import androidx.annotation.Keep;
import com.winlator.cmod.runtime.system.ApplicationLogGate;
import com.winlator.cmod.runtime.display.xserver.Drawable;
import java.nio.ByteBuffer;

/*
 * Vulkan-backed AHardwareBuffer texture.
 *
 * Local images create a CPU-mappable BGRA buffer for X server pixel writes,
 * then import it as a sampleable Vulkan image on demand.
 *
 * Imported images use an existing DRI3 buffer from a Unix socket without CPU mapping.
 */
public class GPUImage extends Texture {
    private static final String TAG = "GPUImage";

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
            if (ahbPtr == 0) {
                Log.w(TAG, "AHB allocation returned null for " + width + "x" + height);
                return;
            }
            virtualData = nativeAhbLock(ahbPtr);
            locked = virtualData != null && stride > 0;
            if (!locked) {
                Log.w(TAG, "AHB CPU mapping failed for " + width + "x" + height);
                nativeAhbDestroy(ahbPtr, false);
                ahbPtr = 0;
                virtualData = null;
            } else {
                if (ApplicationLogGate.isEnabled()) {
                    Log.i(TAG, "AHB allocated and CPU mapped: " + width + "x" + height
                            + " stride=" + Short.toUnsignedInt(stride)
                            + " ptr=0x" + Long.toHexString(ahbPtr));
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "Failed to create AHB-backed GPUImage", e);
            destroy();
        }
    }

    public GPUImage(int socketFd) {
        try {
            cpuAccessible = false;
            ahbPtr = nativeAhbImportFromSocket(socketFd);
            if (ahbPtr != 0) {
                if (ApplicationLogGate.isEnabled()) {
                    Log.i(TAG, "AHB loaded from DRI3 socket fd=" + socketFd
                            + " ptr=0x" + Long.toHexString(ahbPtr));
                }
            } else {
                Log.w(TAG, "AHB import from DRI3 socket failed for fd=" + socketFd);
            }
        } catch (Throwable e) {
            Log.e(TAG, "Failed to import AHB-backed GPUImage", e);
            destroy();
        }
    }

    @Override
    public void allocateTexture(short width, short height, ByteBuffer data) {
        if (isAllocated()) return;
        long renderer = getRendererHandle();
        if (renderer == 0 || ahbPtr == 0) {
            Log.w(TAG, "Skipping AHB Vulkan import: renderer=0x" + Long.toHexString(renderer)
                    + " ahb=0x" + Long.toHexString(ahbPtr));
            return;
        }
        nativeHandle = nativeImportAhbToVulkan(renderer, ahbPtr, true);
        if (nativeHandle == 0) {
            samplingFailed = true;
            Log.w(TAG, "AHB Vulkan import failed: " + width + "x" + height
                    + " ahb=0x" + Long.toHexString(ahbPtr));
        } else {
            handleGeneration = getRendererGeneration();
            if (ApplicationLogGate.isEnabled()) {
                Log.i(TAG, "AHB imported into Vulkan texture: " + width + "x" + height
                        + " tex=0x" + Long.toHexString(nativeHandle));
            }
        }
    }

    @Override
    public void updateFromDrawable(Drawable drawable) {
        if (!isAllocated()) allocateTexture(drawable.width, drawable.height, null);
        // The producer already shares this AHB with the GPU, so there is no CPU upload.
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

    /**
     * Raw {@code AHardwareBuffer*} pointer (a JNI-returned {@code long}). Used
     * by the Direct Composition path to hand this image directly to a child
     * {@code ASurfaceControl} via {@code ASurfaceTransaction_setBuffer},
     * bypassing the VulkanRenderer's GPU compositing blit for fullscreen game
     * frames — true zero-copy to the DPU overlay plane.
     *
     * <p>Returns {@code 0} if the buffer was never allocated / has been
     * destroyed — callers MUST treat 0 as "no buffer available, fall back to
     * VulkanRenderer composition".
     *
     * <p>The pointer remains valid only for the lifetime of this GPUImage.
     * SurfaceFlinger takes its own reference when the AHB is set on a layer,
     * so holding the pointer past {@link #destroy()} is illegal — release any
     * DirectCompositionLayer reference first.
     */
    public long getHardwareBufferPtr() {
        return ahbPtr;
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
            if (ApplicationLogGate.isEnabled()) Log.i(TAG, "Probing AHB Vulkan support");
            probe = new GPUImage(size, size);
            probe.allocateTexture(size, size, null);
            supported = probe.isValid() && probe.getNativeHandle() != 0;
            if (ApplicationLogGate.isEnabled()) {
                Log.i(TAG, "AHB Vulkan support probe result: supported=" + supported);
            }
        } catch (Throwable e) {
            supported = false;
            Log.e(TAG, "AHB Vulkan support probe failed", e);
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
