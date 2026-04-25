package com.winlator.cmod.runtime.display.renderer;

import androidx.annotation.Keep;
import com.winlator.cmod.runtime.display.xserver.Drawable;
import java.nio.ByteBuffer;

public class GPUImage extends Texture {
  private long hardwareBufferPtr;
  private long imageKHRPtr;
  private ByteBuffer virtualData;
  private short stride;
  private boolean locked;
  private boolean cpuAccessible;
  private boolean samplingFailed;
  private static boolean supported = false;

  static {
    System.loadLibrary("winlator");
  }

  public GPUImage(short width, short height) {
    try {
      cpuAccessible = true;
      hardwareBufferPtr = createHardwareBuffer(width, height);
      initializeCpuMapping();
    } catch (Throwable e) {
      System.err.println("Error: Failed to create GPUImage: " + e.getMessage());
      destroy();
    }
  }

  public GPUImage(int socketFd) {
    try {
      cpuAccessible = false;
      hardwareBufferPtr = hardwareBufferFromSocket(socketFd);
    } catch (Throwable e) {
      System.err.println("Error: Failed to import GPUImage: " + e.getMessage());
      destroy();
    }
  }

  private void initializeCpuMapping() {
    if (hardwareBufferPtr == 0) {
      System.err.println("Error: Failed to create hardware buffer");
      return;
    }

    virtualData = lockHardwareBuffer(hardwareBufferPtr);
    if (virtualData == null) {
      System.err.println("Error: Failed to lock hardware buffer");
      destroyHardwareBuffer(hardwareBufferPtr, false);
      hardwareBufferPtr = 0;
    } else {
      locked = true;
    }
  }

  @Override
  public void allocateTexture(short width, short height, ByteBuffer data) {
    if (isAllocated()) return;
    super.allocateTexture(width, height, null);
    if (hardwareBufferPtr != 0) {
      imageKHRPtr = createImageKHR(hardwareBufferPtr, textureId);
      if (imageKHRPtr == 0) {
        System.err.println("Error: Failed to create EGL image");
        samplingFailed = true;
        destroyHardwareBuffer(hardwareBufferPtr, locked);
        hardwareBufferPtr = 0;
        locked = false;
        virtualData = null;
        super.destroy();
      }
    }
  }

  @Override
  public void updateFromDrawable(Drawable drawable) {
    if (!isAllocated()) allocateTexture(drawable.width, drawable.height, null);
    if (!isAllocated()) return;
    needsUpdate = false;
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
    return hardwareBufferPtr != 0 && (!cpuAccessible || (virtualData != null && stride > 0));
  }

  public boolean hasSamplingFailed() {
    return samplingFailed;
  }

  @Override
  public void destroy() {
    if (imageKHRPtr != 0) {
      destroyImageKHR(imageKHRPtr);
      imageKHRPtr = 0;
    }
    if (hardwareBufferPtr != 0) {
      destroyHardwareBuffer(hardwareBufferPtr, locked);
      hardwareBufferPtr = 0;
    }
    locked = false;
    virtualData = null;
    samplingFailed = false;
    super.destroy();
  }

  public static boolean isSupported() {
    return supported;
  }

  public static void checkIsSupported() {
    final short size = 8;
    GPUImage gpuImage = null;
    try {
      gpuImage = new GPUImage(size, size);
      gpuImage.allocateTexture(size, size, null);
      supported = gpuImage.isValid() && gpuImage.imageKHRPtr != 0;
    } catch (Throwable e) {
      supported = false;
      System.err.println("Error: GPUImage support probe failed: " + e.getMessage());
    } finally {
      if (gpuImage != null) gpuImage.destroy();
    }
  }

  private native long hardwareBufferFromSocket(int fd);

  private native long createHardwareBuffer(short width, short height);

  private native void destroyHardwareBuffer(long hardwareBufferPtr, boolean locked);

  private native ByteBuffer lockHardwareBuffer(long hardwareBufferPtr);

  private native long createImageKHR(long hardwareBufferPtr, int textureId);

  private native void destroyImageKHR(long imageKHRPtr);
}
