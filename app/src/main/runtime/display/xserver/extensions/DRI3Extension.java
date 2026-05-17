package com.winlator.cmod.runtime.display.xserver.extensions;

import static com.winlator.cmod.runtime.display.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import android.util.Log;
import com.winlator.cmod.runtime.display.connector.SyncFenceFd;
import com.winlator.cmod.runtime.display.connector.XConnectorEpoll;
import com.winlator.cmod.runtime.display.connector.XInputStream;
import com.winlator.cmod.runtime.display.connector.XOutputStream;
import com.winlator.cmod.runtime.display.connector.XStreamLock;
import com.winlator.cmod.runtime.display.renderer.GPUImage;
import com.winlator.cmod.runtime.display.xserver.Drawable;
import com.winlator.cmod.runtime.display.xserver.Pixmap;
import com.winlator.cmod.runtime.display.xserver.Window;
import com.winlator.cmod.runtime.display.xserver.WindowManager;
import com.winlator.cmod.runtime.display.xserver.XClient;
import com.winlator.cmod.runtime.display.xserver.XLock;
import com.winlator.cmod.runtime.display.xserver.XServer;
import com.winlator.cmod.runtime.display.xserver.errors.BadAlloc;
import com.winlator.cmod.runtime.display.xserver.errors.BadDrawable;
import com.winlator.cmod.runtime.display.xserver.errors.BadIdChoice;
import com.winlator.cmod.runtime.display.xserver.errors.BadImplementation;
import com.winlator.cmod.runtime.display.xserver.errors.BadWindow;
import com.winlator.cmod.runtime.display.xserver.errors.XRequestError;
import com.winlator.cmod.shared.util.Callback;
import com.winlator.cmod.sharedmemory.SysVSharedMemory;
import java.io.IOException;
import java.nio.ByteBuffer;

public class DRI3Extension implements Extension {
  private static final String TAG = "DRI3Extension";
  public static final byte MAJOR_OPCODE = -102;
  private static final int MAX_BUFFERS = 4;
  // Mesa's Android WSI path uses this private modifier to pass an AHardwareBuffer socket.
  private static final long ANDROID_NATIVE_BUFFER_MODIFIER = 1255L;
  // Standard DRM modifier for plain linear buffers (CPU-shm fallback path).
  private static final long DRM_FORMAT_MOD_LINEAR = 0L;
  private final Callback<Drawable> onDestroyDrawableListener =
      (drawable) -> {
        ByteBuffer data = drawable.getData();
        if (data != null) SysVSharedMemory.unmapSHMSegment(data, data.capacity());
      };
  private SyncExtension syncExtension;
  private boolean loggedAhbAdvertised;
  private boolean loggedAhbUnavailable;

  private abstract static class ClientOpcodes {
    private static final byte QUERY_VERSION = 0;
    private static final byte OPEN = 1;
    private static final byte PIXMAP_FROM_BUFFER = 2;
    private static final byte FENCE_FROM_FD = 4;
    private static final byte FD_FROM_FENCE = 5;
    private static final byte GET_SUPPORTED_MODIFIERS = 6;
    private static final byte PIXMAP_FROM_BUFFERS = 7;
    // BuffersFromPixmap (8) intentionally not implemented; AHB re-export over X is rare and
    // requires a worker pool to drive AHardwareBuffer_sendHandleToUnixSocket asynchronously.
    private static final byte SET_DRM_DEVICE_IN_USE = 9;
  }

  @Override
  public String getName() {
    return "DRI3";
  }

  @Override
  public byte getMajorOpcode() {
    return MAJOR_OPCODE;
  }

  @Override
  public byte getFirstErrorId() {
    return 0;
  }

  @Override
  public byte getFirstEventId() {
    return 0;
  }

  private SyncExtension getSyncExtension(XClient client) {
    if (syncExtension == null) syncExtension = client.xServer.getExtension(SyncExtension.MAJOR_OPCODE);
    return syncExtension;
  }

  private void queryVersion(XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    int clientMajor = inputStream.readInt();
    int clientMinor = inputStream.readInt();
    int major = 1;
    int minor = 3;
    if (clientMajor < major || (clientMajor == major && clientMinor < minor)) {
      major = clientMajor;
      minor = clientMinor;
    }

    try (XStreamLock lock = outputStream.lock()) {
      outputStream.writeByte(RESPONSE_CODE_SUCCESS);
      outputStream.writeByte((byte) 0);
      outputStream.writeShort(client.getSequenceNumber());
      outputStream.writeInt(0);
      outputStream.writeInt(major);
      outputStream.writeInt(minor);
      outputStream.writePad(16);
    }
  }

  private void open(XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    int drawableId = inputStream.readInt();
    inputStream.skip(4); // provider

    Drawable drawable = client.xServer.drawableManager.getDrawable(drawableId);
    if (drawable == null) throw new BadDrawable(drawableId);

    // We deliberately reply with nfd=0. On Android, render-node access (/dev/dri/render*)
    // is not available to unprivileged apps, and substituting /dev/null risks tripping Mesa
    // into trying DRM ioctls on a non-DRM FD. Mesa's Android WSI path does not need this FD
    // anyway - it goes directly to PixmapFromBuffers with the AHB-socket modifier.
    try (XStreamLock lock = outputStream.lock()) {
      outputStream.writeByte(RESPONSE_CODE_SUCCESS);
      outputStream.writeByte((byte) 0);
      outputStream.writeShort(client.getSequenceNumber());
      outputStream.writeInt(0);
      outputStream.writePad(24);
    }
  }

  private void pixmapFromBuffer(
      XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    int pixmapId = inputStream.readInt();
    int windowId = inputStream.readInt();
    int size = inputStream.readInt();
    short width = inputStream.readShort();
    short height = inputStream.readShort();
    short stride = inputStream.readShort();
    byte depth = inputStream.readByte();
    byte bpp = inputStream.readByte();

    Window window = client.xServer.windowManager.getWindow(windowId);
    if (window == null) throw new BadWindow(windowId);

    Pixmap pixmap = client.xServer.pixmapManager.getPixmap(pixmapId);
    if (pixmap != null) throw new BadIdChoice(pixmapId);

    int fd = inputStream.getAncillaryFd();
    if (fd < 0) throw new BadAlloc();
    pixmapFromFd(client, pixmapId, width, height, stride, 0, depth, bpp, fd, size);
    client.xServer.windowManager.triggerOnFramePresented(
        window, WindowManager.FrameSource.DRI3_BUFFER, 0);
  }

  private void pixmapFromBuffers(
      XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    int pixmapId = inputStream.readInt();
    int windowId = inputStream.readInt();
    int numBuffers = inputStream.readUnsignedByte();
    inputStream.skip(3);
    short width = inputStream.readShort();
    short height = inputStream.readShort();
    int[] strides = new int[MAX_BUFFERS];
    int[] offsets = new int[MAX_BUFFERS];
    for (int i = 0; i < MAX_BUFFERS; i++) {
      strides[i] = inputStream.readInt();
      offsets[i] = inputStream.readInt();
    }
    byte depth = inputStream.readByte();
    byte bpp = inputStream.readByte();
    inputStream.skip(2);
    long modifier = inputStream.readLong();

    Window window = client.xServer.windowManager.getWindow(windowId);
    if (window == null) throw new BadWindow(windowId);
    Pixmap pixmap = client.xServer.pixmapManager.getPixmap(pixmapId);
    if (pixmap != null) throw new BadIdChoice(pixmapId);

    int[] fds = readAncillaryFds(inputStream, numBuffers);
    int stride = strides[0];
    int offset = offsets[0];
    long size = (long) stride * height;

    try {
      if (modifier == ANDROID_NATIVE_BUFFER_MODIFIER
          && numBuffers == 1
          && tryPixmapFromHardwareBuffer(client, pixmapId, width, height, depth, fds[0])) {
        // fds[0] stays non-negative so the finally block closes the AHB socket.
        client.xServer.windowManager.triggerOnFramePresented(
            window, WindowManager.FrameSource.DRI3_BUFFER, 0);
        return;
      }

      if (modifier == ANDROID_NATIVE_BUFFER_MODIFIER) {
        Log.w(
            TAG,
            "AHB pixmap import failed; falling back to linear SHM path: pixmap="
                + pixmapId
                + " numBuffers="
                + numBuffers
                + " size="
                + Short.toUnsignedInt(width)
                + "x"
                + Short.toUnsignedInt(height));
      }

      if (numBuffers != 1 || stride <= 0 || size <= 0) throw new BadImplementation();
      int fd = fds[0];
      fds[0] = -1;
      pixmapFromFd(client, pixmapId, width, height, stride, offset, depth, bpp, fd, size);
      client.xServer.windowManager.triggerOnFramePresented(
          window, WindowManager.FrameSource.DRI3_BUFFER, 0);
    } finally {
      closeFds(fds);
    }
  }

  /**
   * DRI3 1.2 GetSupportedModifiers (opcode 6). Advertises ANDROID_NATIVE_BUFFER_MODIFIER for
   * window pixmaps (zero-copy AHB) and DRM_FORMAT_MOD_LINEAR for screen pixmaps (CPU/SHM).
   */
  private void getSupportedModifiers(
      XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException {
    inputStream.readInt(); // window (we do not vary modifiers per window)
    int depth = inputStream.readUnsignedByte();
    int bpp = inputStream.readUnsignedByte();
    inputStream.skip(2);

    boolean ahbSupported = GPUImage.isSupported() && bpp == 32 && (depth == 24 || depth == 32);
    int numWindow = ahbSupported ? 1 : 0;
    int numScreen = 1; // always advertise LINEAR for the CPU/SHM path
    if (ahbSupported && !loggedAhbAdvertised) {
      Log.i(
          TAG,
          "Advertising DRI3 AHB modifier for window pixmaps: depth=" + depth + " bpp=" + bpp);
      loggedAhbAdvertised = true;
    } else if (!ahbSupported && !loggedAhbUnavailable) {
      Log.i(
          TAG,
          "DRI3 AHB modifier unavailable: gpuImageSupported="
              + GPUImage.isSupported()
              + " depth="
              + depth
              + " bpp="
              + bpp);
      loggedAhbUnavailable = true;
    }

    int extraBytes = (numWindow + numScreen) * 8;
    try (XStreamLock lock = outputStream.lock()) {
      outputStream.writeByte(RESPONSE_CODE_SUCCESS);
      outputStream.writeByte((byte) 0);
      outputStream.writeShort(client.getSequenceNumber());
      outputStream.writeInt(extraBytes / 4);
      outputStream.writeInt(numWindow);
      outputStream.writeInt(numScreen);
      outputStream.writePad(16);
      if (ahbSupported) outputStream.writeLong(ANDROID_NATIVE_BUFFER_MODIFIER);
      outputStream.writeLong(DRM_FORMAT_MOD_LINEAR);
    }
  }

  /** DRI3 1.0 FenceFromFD (opcode 4). Imports a sync_file FD as an X SYNC fence. */
  private void fenceFromFd(XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    int drawableId = inputStream.readInt();
    int fenceId = inputStream.readInt();
    boolean initiallyTriggered = inputStream.readByte() != 0;
    inputStream.skip(3);

    Drawable drawable = client.xServer.drawableManager.getDrawable(drawableId);
    if (drawable == null) throw new BadDrawable(drawableId);

    int fd = inputStream.getAncillaryFd();
    if (fd < 0) throw new BadAlloc();

    SyncExtension sync = getSyncExtension(client);
    if (sync == null) {
      SyncFenceFd.closeFd(fd);
      throw new BadImplementation();
    }
    sync.createFromFd(fenceId, initiallyTriggered, fd);
  }

  /**
   * DRI3 1.0 FDFromFence (opcode 5). Hands the client an eventfd that becomes readable when
   * the SYNC fence triggers, so it can poll instead of round-tripping AwaitFence.
   */
  private void fdFromFence(XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    int drawableId = inputStream.readInt();
    int fenceId = inputStream.readInt();

    Drawable drawable = client.xServer.drawableManager.getDrawable(drawableId);
    if (drawable == null) throw new BadDrawable(drawableId);

    SyncExtension sync = getSyncExtension(client);
    if (sync == null) throw new BadImplementation();

    int exportFd = sync.createExportFd(fenceId);
    if (exportFd < 0) throw new BadAlloc();

    // Reply FD is for SCM_RIGHTS only; close our copy once sendmsg has duplicated it.
    try {
      try (XStreamLock lock = outputStream.lock()) {
        outputStream.setAncillaryFd(exportFd);
        outputStream.writeByte(RESPONSE_CODE_SUCCESS);
        outputStream.writeByte((byte) 1);
        outputStream.writeShort(client.getSequenceNumber());
        outputStream.writeInt(0);
        outputStream.writePad(24);
      }
    } finally {
      SyncFenceFd.closeFd(exportFd);
    }
  }

  /** DRI3 1.3 SetDRMDeviceInUse (opcode 9). Single-GPU path, accepted as a no-op. */
  private void setDrmDeviceInUse(
      XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    int windowId = inputStream.readInt();
    inputStream.skip(8); // drm_major + drm_minor

    Window window = client.xServer.windowManager.getWindow(windowId);
    if (window == null) throw new BadWindow(windowId);
    // intentionally a no-op
  }

  private int[] readAncillaryFds(XInputStream inputStream, int numBuffers) throws XRequestError {
    if (numBuffers < 1 || numBuffers > MAX_BUFFERS) throw new BadAlloc();

    int[] fds = new int[numBuffers];
    for (int i = 0; i < numBuffers; i++) fds[i] = -1;
    for (int i = 0; i < numBuffers; i++) {
      fds[i] = inputStream.getAncillaryFd();
      if (fds[i] < 0) {
        closeFds(fds);
        throw new BadAlloc();
      }
    }
    return fds;
  }

  private void closeFds(int[] fds) {
    if (fds == null) return;
    for (int i = 0; i < fds.length; i++) {
      if (fds[i] >= 0) {
        XConnectorEpoll.closeFd(fds[i]);
        fds[i] = -1;
      }
    }
  }

  private boolean tryPixmapFromHardwareBuffer(
      XClient client, int pixmapId, short width, short height, byte depth, int fd)
      throws IOException, XRequestError {
    GPUImage gpuImage = new GPUImage(fd);
    if (!gpuImage.isValid()) {
      Log.w(
          TAG,
          "Rejected DRI3 AHB pixmap: pixmap="
              + pixmapId
              + " size="
              + Short.toUnsignedInt(width)
              + "x"
              + Short.toUnsignedInt(height)
              + " depth="
              + Byte.toUnsignedInt(depth));
      gpuImage.destroy();
      return false;
    }

    Drawable drawable =
        client.xServer.drawableManager.createDrawable(pixmapId, width, height, depth);
    if (drawable == null) {
      gpuImage.destroy();
      throw new BadIdChoice(pixmapId);
    }
    drawable.setTexture(gpuImage);
    drawable.setDirectScanout(true);
    client.xServer.pixmapManager.createPixmap(drawable);
    Log.i(
        TAG,
        "Loaded DRI3 AHB pixmap for direct scanout: pixmap="
            + pixmapId
            + " size="
            + Short.toUnsignedInt(width)
            + "x"
            + Short.toUnsignedInt(height)
            + " depth="
            + Byte.toUnsignedInt(depth));
    return true;
  }

  private void pixmapFromFd(
      XClient client,
      int pixmapId,
      short width,
      short height,
      int stride,
      int offset,
      byte depth,
      byte bpp,
      int fd,
      long size)
      throws IOException, XRequestError {
    try {
      if (Byte.toUnsignedInt(bpp) != 32) throw new BadImplementation();
      ByteBuffer buffer = SysVSharedMemory.mapSHMSegment(fd, size, offset, true);
      if (buffer == null) throw new BadAlloc();

      short totalWidth = (short) (stride / 4);
      Drawable drawable =
          client.xServer.drawableManager.createDrawable(pixmapId, totalWidth, height, depth);
      drawable.setData(buffer);
      drawable.setTexture(null);
      drawable.setOnDestroyListener(onDestroyDrawableListener);
      client.xServer.pixmapManager.createPixmap(drawable);
    } finally {
      XConnectorEpoll.closeFd(fd);
    }
  }

  @Override
  public void handleRequest(XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    int opcode = client.getRequestData();
    switch (opcode) {
      case ClientOpcodes.QUERY_VERSION:
        queryVersion(client, inputStream, outputStream);
        break;
      case ClientOpcodes.OPEN:
        try (XLock lock = client.xServer.lock(XServer.Lockable.DRAWABLE_MANAGER)) {
          open(client, inputStream, outputStream);
        }
        break;
      case ClientOpcodes.PIXMAP_FROM_BUFFER:
        try (XLock lock =
            client.xServer.lock(
                XServer.Lockable.WINDOW_MANAGER,
                XServer.Lockable.PIXMAP_MANAGER,
                XServer.Lockable.DRAWABLE_MANAGER)) {
          pixmapFromBuffer(client, inputStream, outputStream);
        }
        break;
      case ClientOpcodes.FENCE_FROM_FD:
        try (XLock lock = client.xServer.lock(XServer.Lockable.DRAWABLE_MANAGER)) {
          fenceFromFd(client, inputStream, outputStream);
        }
        break;
      case ClientOpcodes.FD_FROM_FENCE:
        try (XLock lock = client.xServer.lock(XServer.Lockable.DRAWABLE_MANAGER)) {
          fdFromFence(client, inputStream, outputStream);
        }
        break;
      case ClientOpcodes.GET_SUPPORTED_MODIFIERS:
        getSupportedModifiers(client, inputStream, outputStream);
        break;
      case ClientOpcodes.PIXMAP_FROM_BUFFERS:
        try (XLock lock =
            client.xServer.lock(
                XServer.Lockable.WINDOW_MANAGER,
                XServer.Lockable.PIXMAP_MANAGER,
                XServer.Lockable.DRAWABLE_MANAGER)) {
          pixmapFromBuffers(client, inputStream, outputStream);
        }
        break;
      case ClientOpcodes.SET_DRM_DEVICE_IN_USE:
        try (XLock lock = client.xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
          setDrmDeviceInUse(client, inputStream, outputStream);
        }
        break;
      default:
        throw new BadImplementation();
    }
  }
}
