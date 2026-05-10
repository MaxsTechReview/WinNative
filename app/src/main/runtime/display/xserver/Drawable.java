package com.winlator.cmod.runtime.display.xserver;

import android.graphics.Bitmap;
import com.winlator.cmod.runtime.display.renderer.GPUImage;
import com.winlator.cmod.runtime.display.renderer.Texture;
import com.winlator.cmod.shared.math.Mathf;
import com.winlator.cmod.shared.util.Callback;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Drawable extends XResource {
  public final short width;
  public final short height;
  public final Visual visual;
  private Texture texture = new Texture();
  private ByteBuffer data;
  // volatile because the GLThread reads these in `isDirectScanoutContent`
  // (called outside renderLock for the candidate-search pass) while the
  // X-server worker thread mutates them via setDirectScanout/setScanoutSource
  // from PresentExtension and DRI3Extension. Without volatile, the GLThread
  // can observe stale values across frames. Long-form mutations of texture
  // / scanoutSource still serialize on `renderLock` for atomicity with reads
  // that take the lock (see `renderDrawable`, the Direct-Composition path).
  private volatile boolean directScanout = false;
  private volatile Drawable scanoutSource;
  /**
   * Producer-side acquire fence FD. -1 means "no fence; buffer is ready for
   * immediate read."
   *
   * <p>Today in this fork the value is always -1: the in-process Java X server
   * receives the {@code AHardwareBuffer} from the Wine client over the DRI3
   * {@code PIXMAP_FROM_BUFFERS} unix-socket path, which the X-server worker
   * thread processes only after the client has already submitted its Vulkan
   * command buffer. Empirically this CPU-side handoff latency exceeds the
   * GPU-write completion time, so we observe no tearing without an explicit
   * fence — but this is an observation about THIS pipeline, not a Vulkan or
   * Mesa contract. If a future codepath drives presents at a higher rate or
   * skips that handoff cost, an explicit acquire fence will become necessary.
   *
   * <p>Forward-compatible plumbing: when the X-server PRESENT/DRI3 parsing
   * gains real {@code wait_fence} support, that code calls
   * {@link #setAcquireFenceFd}. The Direct Composition push at
   * {@code GLRenderer.maybePushDirectComposition} consumes the value under
   * {@link #renderLock} via {@link #takeAcquireFenceFd} (atomic
   * read-and-clear), and forwards it to
   * {@code DirectCompositionLayer.pushBuffer} which transfers ownership to
   * the framework via {@code ASurfaceTransaction_setBuffer}.
   *
   * <p>Stored as {@link java.util.concurrent.atomic.AtomicInteger} so the
   * "consume" semantic is atomic with respect to concurrent
   * setAcquireFenceFd writes — without it, a producer could overwrite a
   * still-pending FD between the consumer's read and clear, leaking the
   * old FD.
   */
  private final java.util.concurrent.atomic.AtomicInteger acquireFenceFd =
      new java.util.concurrent.atomic.AtomicInteger(-1);
  private Runnable onDrawListener;
  private Callback<Drawable> onDestroyListener;
  public final Object renderLock = new Object();

  static {
    System.loadLibrary("winlator");
  }

  public Drawable(int id, int width, int height, Visual visual) {
    super(id);
    this.width = (short) width;
    this.height = (short) height;
    this.visual = visual;
    this.data = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
    if (this.data == null) {
      throw new IllegalStateException("Drawable.data initialized as null!");
    }
  }

  public static Drawable fromBitmap(Bitmap bitmap) {
    Drawable drawable = new Drawable(0, bitmap.getWidth(), bitmap.getHeight(), null);
    fromBitmap(bitmap, drawable.data);
    return drawable;
  }

  public Texture getTexture() {
    return texture;
  }

  public void setTexture(Texture texture) {
    if (texture instanceof GPUImage) {
      ByteBuffer virtualData = ((GPUImage) texture).getVirtualData();
      if (virtualData != null) data = virtualData;
    }
    this.texture = texture;
  }

  public Drawable getScanoutSource() {
    return scanoutSource;
  }

  public void setScanoutSource(Drawable scanoutSource) {
    this.scanoutSource = scanoutSource;
    if (texture != null) texture.setNeedsUpdate(true);
    if (onDrawListener != null) onDrawListener.run();
  }

  public void clearScanoutSource() {
    if (scanoutSource == null) return;
    scanoutSource = null;
    if (texture != null) texture.setNeedsUpdate(true);
  }

  /**
   * Atomic read-and-clear of the acquire fence FD: returns the current value
   * (or -1 if none) AND resets the field to -1 in a single CAS.
   *
   * <p>This is single-consumer "take" semantics: the caller now owns the FD
   * and is responsible for either closing it or transferring ownership
   * elsewhere (e.g. by passing it to {@code ASurfaceTransaction_setBuffer},
   * which closes it via the framework). A second concurrent caller of
   * {@code takeAcquireFenceFd} will get -1 — they did not own the original
   * fence.
   *
   * <p>Returns -1 today because no producer code currently calls
   * {@link #setAcquireFenceFd}.
   */
  public int takeAcquireFenceFd() {
    return acquireFenceFd.getAndSet(-1);
  }

  /**
   * Sets the producer acquire fence FD. Should be called by Present/DRI3
   * extension code immediately before the buffer is published to the GLRenderer
   * (i.e. before {@link #setScanoutSource}). Ownership transfers to the
   * Drawable; the next consumer of {@link #takeAcquireFenceFd} takes
   * ownership in turn. If the previous fence is still set (consumer hasn't
   * taken it yet), this method closes the previous fence to avoid a leak.
   * Pass {@code -1} to clear without setting a new fence.
   */
  public void setAcquireFenceFd(int fd) {
    int prior = acquireFenceFd.getAndSet(fd);
    if (prior >= 0 && prior != fd) {
      try {
        android.os.ParcelFileDescriptor.adoptFd(prior).close();
      } catch (java.io.IOException ignored) {
        // best-effort close; the FD is still leaked but we did our part
      }
    }
  }

  public ByteBuffer getData() {
    return data;
  }

  public void setData(ByteBuffer data) {
    if (data == null) {
      throw new IllegalArgumentException("Attempting to set Drawable.data to null!");
    }
    this.data = data;
  }

  public void setDirectScanout(boolean value) {
    this.directScanout = value;
  }

  public boolean isDirectScanout() {
    return directScanout;
  }

  private short getStride() {
    return texture instanceof GPUImage ? ((GPUImage) texture).getStride() : width;
  }

  public Runnable getOnDrawListener() {
    return onDrawListener;
  }

  public void setOnDrawListener(Runnable onDrawListener) {
    this.onDrawListener = onDrawListener;
  }

  public Callback<Drawable> getOnDestroyListener() {
    return onDestroyListener;
  }

  public void setOnDestroyListener(Callback<Drawable> onDestroyListener) {
    this.onDestroyListener = onDestroyListener;
  }

  public void drawImage(
      short srcX,
      short srcY,
      short dstX,
      short dstY,
      short width,
      short height,
      byte depth,
      ByteBuffer data,
      short totalWidth,
      short totalHeight) {
    clearScanoutSource();
    if (depth == 1) {
      drawBitmap(width, height, data, this.data);
    } else if (depth == 24 || depth == 32) {
      dstX = (short) Mathf.clamp(dstX, 0, this.width - 1);
      dstY = (short) Mathf.clamp(dstY, 0, this.height - 1);
      if ((dstX + width) > this.width) width = (short) ((this.width - dstX));
      if ((dstY + height) > this.height) height = (short) ((this.height - dstY));

      copyArea(
          srcX, srcY, dstX, dstY, width, height, totalWidth, this.getStride(), data, this.data);
    }

    this.data.rewind();
    data.rewind();

    texture.setNeedsUpdate(true);
    if (onDrawListener != null) onDrawListener.run();
  }

  public ByteBuffer getImage(short x, short y, short width, short height) {
    ByteBuffer dstData =
        ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);

    x = (short) Mathf.clamp(x, 0, this.width - 1);
    y = (short) Mathf.clamp(y, 0, this.height - 1);
    if ((x + width) > this.width) width = (short) (this.width - x);
    if ((y + height) > this.height) height = (short) (this.height - y);

    copyArea(
        x, y, (short) 0, (short) 0, width, height, this.getStride(), width, this.data, dstData);

    this.data.rewind();
    dstData.rewind();
    return dstData;
  }

  public void copyArea(
      short srcX,
      short srcY,
      short dstX,
      short dstY,
      short width,
      short height,
      Drawable drawable) {
    copyArea(srcX, srcY, dstX, dstY, width, height, drawable, GraphicsContext.Function.COPY);
  }

  public void copyArea(
      short srcX,
      short srcY,
      short dstX,
      short dstY,
      short width,
      short height,
      Drawable drawable,
      GraphicsContext.Function gcFunction) {
    clearScanoutSource();
    dstX = (short) Mathf.clamp(dstX, 0, this.width - 1);
    dstY = (short) Mathf.clamp(dstY, 0, this.height - 1);
    if ((dstX + width) > this.width) width = (short) (this.width - dstX);
    if ((dstY + height) > this.height) height = (short) (this.height - dstY);

    if (gcFunction == GraphicsContext.Function.COPY) {
      copyArea(
          srcX,
          srcY,
          dstX,
          dstY,
          width,
          height,
          drawable.getStride(),
          this.getStride(),
          drawable.data,
          this.data);
    } else
      copyAreaOp(
          srcX,
          srcY,
          dstX,
          dstY,
          width,
          height,
          drawable.getStride(),
          this.getStride(),
          drawable.data,
          this.data,
          gcFunction.ordinal());

    this.data.rewind();
    drawable.data.rewind();

    texture.setNeedsUpdate(true);
    if (onDrawListener != null) onDrawListener.run();
  }

  public void fillColor(int color) {
    fillRect(0, 0, width, height, color);
  }

  public void fillRect(int x, int y, int width, int height, int color) {
    clearScanoutSource();
    x = (short) Mathf.clamp(x, 0, this.width - 1);
    y = (short) Mathf.clamp(y, 0, this.height - 1);
    if ((x + width) > this.width) width = (short) ((this.width - x));
    if ((y + height) > this.height) height = (short) ((this.height - y));

    fillRect(
        (short) x, (short) y, (short) width, (short) height, color, this.getStride(), this.data);
    this.data.rewind();

    texture.setNeedsUpdate(true);
    if (onDrawListener != null) onDrawListener.run();
  }

  public void drawLines(int color, int lineWidth, short... points) {
    for (int i = 2; i < points.length; i += 2) {
      drawLine(
          points[i - 2], points[i - 1], points[i + 0], points[i + 1], color, (short) lineWidth);
    }
  }

  public void drawLine(int x0, int y0, int x1, int y1, int color, int lineWidth) {
    clearScanoutSource();
    x0 = Mathf.clamp(x0, 0, width - lineWidth);
    y0 = Mathf.clamp(y0, 0, height - lineWidth);
    x1 = Mathf.clamp(x1, 0, width - lineWidth);
    y1 = Mathf.clamp(y1, 0, height - lineWidth);

    drawLine(
        (short) x0,
        (short) y0,
        (short) x1,
        (short) y1,
        color,
        (short) lineWidth,
        this.getStride(),
        this.data);

    this.data.rewind();

    texture.setNeedsUpdate(true);
    if (onDrawListener != null) onDrawListener.run();
  }

  public void drawAlphaMaskedBitmap(
      byte foreRed,
      byte foreGreen,
      byte foreBlue,
      byte backRed,
      byte backGreen,
      byte backBlue,
      Drawable srcDrawable,
      Drawable maskDrawable) {
    clearScanoutSource();
    drawAlphaMaskedBitmap(
        foreRed,
        foreGreen,
        foreBlue,
        backRed,
        backGreen,
        backBlue,
        srcDrawable.data,
        maskDrawable.data,
        this.data);
    this.data.rewind();

    texture.setNeedsUpdate(true);
    if (onDrawListener != null) onDrawListener.run();
  }

  private static native void drawBitmap(
      short width, short height, ByteBuffer srcData, ByteBuffer dstData);

  private static native void drawAlphaMaskedBitmap(
      byte foreRed,
      byte foreGreen,
      byte foreBlue,
      byte backRed,
      byte backGreen,
      byte backBlue,
      ByteBuffer srcData,
      ByteBuffer maskData,
      ByteBuffer dstData);

  private static native void copyArea(
      short srcX,
      short srcY,
      short dstX,
      short dstY,
      short width,
      short height,
      short srcStride,
      short dstStride,
      ByteBuffer srcData,
      ByteBuffer dstData);

  private static native void copyAreaOp(
      short srcX,
      short srcY,
      short dstX,
      short dstY,
      short width,
      short height,
      short srcStride,
      short dstStride,
      ByteBuffer srcData,
      ByteBuffer dstData,
      int gcFunction);

  private static native void fillRect(
      short x, short y, short width, short height, int color, short stride, ByteBuffer data);

  private static native void drawLine(
      short x0,
      short y0,
      short x1,
      short y1,
      int color,
      short lineWidth,
      short stride,
      ByteBuffer data);

  private static native void fromBitmap(Bitmap bitmap, ByteBuffer data);
}

// package com.winlator.cmod.runtime.display.xserver;
//
// import android.graphics.Bitmap;
// import com.winlator.cmod.shared.util.Callback;
// import com.winlator.cmod.shared.math.Mathf;
// import com.winlator.cmod.runtime.display.renderer.GPUImage;
// import com.winlator.cmod.runtime.display.renderer.Texture;
//
// import java.nio.ByteBuffer;
// import java.nio.ByteOrder;
//
/// **
// * Merged Drawable class based on original + new Smali changes:
// * - Adds a 'blank' field (default true).
// * - Adds a 'useSharedData' field (default false).
// * - Adds a new method 'forceUpdate()' that sets the texture to need an update,
// *   sets blank = false, and triggers onDrawListener if present.
// * - Adds isBlank(), isUseSharedData(), setUseSharedData(...) from Smali.
// */
// public class Drawable extends XResource {
//    public final short width;
//    public final short height;
//    public final Visual visual;
//
//    // The texture bound to this Drawable.
//    private Texture texture = new Texture();
//
//    // Pixel data for this Drawable (e.g., RGBA).
//    private ByteBuffer data;
//
//    // Optional callback if something needs to be run after a draw.
//    private Runnable onDrawListener;
//
//    // Optional callback if something needs to be run on destroy.
//    private Callback<Drawable> onDestroyListener;
//
//    /**
//     * Locks concurrency for rendering. In many places, code calls synchronized(renderLock).
//     */
//    public final Object renderLock = new Object();
//
//    /**
//     * Whether this Drawable is "blank" (unused).
//     * Smali indicates it starts true, then set to false once we write or draw to it.
//     */
//    private boolean blank = true;
//
//    /**
//     * Whether this Drawable is using shared data externally, introduced in new Smali.
//     */
//    private boolean useSharedData = false;
//
//    static {
//        System.loadLibrary("winlator");
//    }
//
//    /**
//     * Main constructor. Allocates a ByteBuffer for the image data sized width*height*4.
//     */
//    public Drawable(int id, int width, int height, Visual visual) {
//        super(id);
//        this.width = (short) width;
//        this.height = (short) height;
//        this.visual = visual;
//
//        // Allocate local buffer (4 bytes per pixel)
//        this.data = ByteBuffer
//                .allocateDirect(width * height * 4)
//                .order(ByteOrder.LITTLE_ENDIAN);
//    }
//
//    /**
//     * Creates a Drawable from a Bitmap by copying its data into a newly allocated buffer.
//     * Also sets blank = false in the new Smali code.
//     */
//    public static Drawable fromBitmap(Bitmap bitmap) {
//        Drawable drawable = new Drawable(
//                0,
//                bitmap.getWidth(),
//                bitmap.getHeight(),
//                null
//        );
//        fromBitmap(bitmap, drawable.data);
//        drawable.blank = false; // Smali sets blank to false once we fill data
//        return drawable;
//    }
//
//    /**
//     * Force the Texture to be updated on the next render pass (based on new Smali).
//     * Also sets blank = false and calls onDrawListener if not null.
//     */
//    public void forceUpdate() {
//        texture.setNeedsUpdate(true);
//        blank = false;
//        if (onDrawListener != null) {
//            onDrawListener.run();
//        }
//    }
//
//    // -------------------------------------------------------------------------
//    // Getters & Setters
//    // -------------------------------------------------------------------------
//
//    public Texture getTexture() {
//        return texture;
//    }
//
//    /**
//     * If the Texture is a GPUImage, we also sync its ByteBuffer with this Drawable’s data.
//     */
//    public void setTexture(Texture texture) {
//        if (texture instanceof GPUImage) {
//            this.data = ((GPUImage) texture).getVirtualData();
//        }
//        this.texture = texture;
//    }
//
//    public ByteBuffer getData() {
//        return data;
//    }
//
//    public void setData(ByteBuffer data) {
//        this.data = data;
//        this.blank = false; // If we manually set data, it's no longer blank
//    }
//
//    /**
//     * New from Smali: isBlank() returns whether this Drawable has never been drawn to.
//     */
//    public boolean isBlank() {
//        return blank;
//    }
//
//    /**
//     * New from Smali: track whether we share data externally. Default is false.
//     */
//    public boolean isUseSharedData() {
//        return useSharedData;
//    }
//
//    public void setUseSharedData(boolean useSharedData) {
//        this.useSharedData = useSharedData;
//    }
//
//    public Runnable getOnDrawListener() {
//        return onDrawListener;
//    }
//
//    public void setOnDrawListener(Runnable onDrawListener) {
//        this.onDrawListener = onDrawListener;
//    }
//
//    public Callback<Drawable> getOnDestroyListener() {
//        return onDestroyListener;
//    }
//
//    public void setOnDestroyListener(Callback<Drawable> onDestroyListener) {
//        this.onDestroyListener = onDestroyListener;
//    }
//
//    // -------------------------------------------------------------------------
//    // Drawing & Copy Methods
//    // -------------------------------------------------------------------------
//
//    /**
//     * Draw an image into this Drawable.
//     * Depth 1 => drawBitmap(), Depth 24/32 => copy color data, etc.
//     */
//    public void drawImage(short srcX, short srcY, short dstX, short dstY,
//                          short width, short height, byte depth,
//                          ByteBuffer data, short totalWidth, short totalHeight) {
//        if (depth == 1) {
//            drawBitmap(width, height, data, this.data);
//        } else if (depth == 24 || depth == 32) {
//            dstX = (short) Mathf.clamp(dstX, 0, this.width - 1);
//            dstY = (short) Mathf.clamp(dstY, 0, this.height - 1);
//            if ((dstX + width) > this.width) width = (short) (this.width - dstX);
//            if ((dstY + height) > this.height) height = (short) (this.height - dstY);
//
//            copyArea(srcX, srcY, dstX, dstY, width, height,
//                    totalWidth, getStride(), data, this.data);
//        }
//
//        this.data.rewind();
//        data.rewind();
//
//        forceUpdate(); // Replaces older setNeedsUpdate + onDrawListener run
//    }
//
//    /**
//     * Extract an image from this Drawable.
//     */
//    public ByteBuffer getImage(short x, short y, short width, short height) {
//        ByteBuffer dstData = ByteBuffer
//                .allocateDirect(width * height * 4)
//                .order(ByteOrder.LITTLE_ENDIAN);
//
//        x = (short) Mathf.clamp(x, 0, this.width - 1);
//        y = (short) Mathf.clamp(y, 0, this.height - 1);
//        if ((x + width) > this.width) width = (short) (this.width - x);
//        if ((y + height) > this.height) height = (short) (this.height - y);
//
//        copyArea(x, y, (short) 0, (short) 0, width, height,
//                getStride(), width, this.data, dstData);
//
//        this.data.rewind();
//        dstData.rewind();
//        return dstData;
//    }
//
//    /**
//     * Copy from another Drawable into this Drawable, possibly applying a GC function.
//     */
//    public void copyArea(short srcX, short srcY, short dstX, short dstY,
//                         short width, short height, Drawable drawable) {
//        copyArea(srcX, srcY, dstX, dstY, width, height, drawable, GraphicsContext.Function.COPY);
//    }
//
//    public void copyArea(short srcX, short srcY, short dstX, short dstY,
//                         short width, short height, Drawable drawable,
//                         GraphicsContext.Function gcFunction) {
//        dstX = (short) Mathf.clamp(dstX, 0, this.width - 1);
//        dstY = (short) Mathf.clamp(dstY, 0, this.height - 1);
//        if ((dstX + width) > this.width) width = (short) (this.width - dstX);
//        if ((dstY + height) > this.height) height = (short) (this.height - dstY);
//
//        if (gcFunction == GraphicsContext.Function.COPY) {
//            copyArea(srcX, srcY, dstX, dstY, width, height,
//                    drawable.getStride(), getStride(),
//                    drawable.data, this.data);
//        } else {
//            copyAreaOp(srcX, srcY, dstX, dstY, width, height,
//                    drawable.getStride(), getStride(),
//                    drawable.data, this.data,
//                    gcFunction.ordinal());
//        }
//
//        this.data.rewind();
//        drawable.data.rewind();
//
//        forceUpdate();
//    }
//
//    public void fillColor(int color) {
//        fillRect(0, 0, width, height, color);
//    }
//
//    public void fillRect(int x, int y, int width, int height, int color) {
//        x = (short) Mathf.clamp(x, 0, this.width - 1);
//        y = (short) Mathf.clamp(y, 0, this.height - 1);
//        if ((x + width) > this.width) width = (short) (this.width - x);
//        if ((y + height) > this.height) height = (short) (this.height - y);
//
//        fillRect((short) x, (short) y, (short) width, (short) height,
//                color, getStride(), this.data);
//        this.data.rewind();
//
//        forceUpdate();
//    }
//
//    public void drawLines(int color, int lineWidth, short... points) {
//        for (int i = 2; i < points.length; i += 2) {
//            drawLine(points[i - 2], points[i - 1], points[i], points[i + 1], color, lineWidth);
//        }
//    }
//
//    public void drawLine(int x0, int y0, int x1, int y1, int color, int lineWidth) {
//        x0 = Mathf.clamp(x0, 0, width - lineWidth);
//        y0 = Mathf.clamp(y0, 0, height - lineWidth);
//        x1 = Mathf.clamp(x1, 0, width - lineWidth);
//        y1 = Mathf.clamp(y1, 0, height - lineWidth);
//
//        drawLine((short) x0, (short) y0, (short) x1, (short) y1,
//                color, (short) lineWidth, getStride(), this.data);
//
//        this.data.rewind();
//
//        forceUpdate();
//    }
//
//    public void drawAlphaMaskedBitmap(byte foreRed, byte foreGreen, byte foreBlue,
//                                      byte backRed, byte backGreen, byte backBlue,
//                                      Drawable srcDrawable, Drawable maskDrawable) {
//        drawAlphaMaskedBitmap(foreRed, foreGreen, foreBlue,
//                backRed, backGreen, backBlue,
//                srcDrawable.data, maskDrawable.data, this.data);
//        this.data.rewind();
//
//        forceUpdate();
//    }
//
//    // -------------------------------------------------------------------------
//    // Private / Native Helpers
//    // -------------------------------------------------------------------------
//
//    private short getStride() {
//        return (texture instanceof GPUImage)
//                ? ((GPUImage) texture).getStride()
//                : width;
//    }
//
//    private static native void drawBitmap(short width, short height,
//                                          ByteBuffer srcData, ByteBuffer dstData);
//
//    private static native void drawAlphaMaskedBitmap(byte foreRed, byte foreGreen, byte foreBlue,
//                                                     byte backRed, byte backGreen, byte backBlue,
//                                                     ByteBuffer srcData, ByteBuffer maskData,
//                                                     ByteBuffer dstData);
//
//    private static native void copyArea(short srcX, short srcY, short dstX, short dstY,
//                                        short width, short height,
//                                        short srcStride, short dstStride,
//                                        ByteBuffer srcData, ByteBuffer dstData);
//
//    private static native void copyAreaOp(short srcX, short srcY, short dstX, short dstY,
//                                          short width, short height,
//                                          short srcStride, short dstStride,
//                                          ByteBuffer srcData, ByteBuffer dstData,
//                                          int gcFunction);
//
//    private static native void fillRect(short x, short y, short width, short height,
//                                        int color, short stride, ByteBuffer data);
//
//    private static native void drawLine(short x0, short y0, short x1, short y1,
//                                        int color, short lineWidth,
//                                        short stride, ByteBuffer data);
//
//    private static native void fromBitmap(Bitmap bitmap, ByteBuffer data);
// }
