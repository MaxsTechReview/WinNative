package com.winlator.cmod.runtime.display.xserver.extensions;

import static com.winlator.cmod.runtime.display.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import com.winlator.cmod.runtime.display.connector.XConnectorEpoll;
import com.winlator.cmod.runtime.display.connector.XInputStream;
import com.winlator.cmod.runtime.display.connector.XOutputStream;
import com.winlator.cmod.runtime.display.connector.XStreamLock;
import com.winlator.cmod.runtime.display.renderer.GPUImage;
import com.winlator.cmod.runtime.display.xserver.Drawable;
import com.winlator.cmod.runtime.display.xserver.Pixmap;
import com.winlator.cmod.runtime.display.xserver.Window;
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
  public static final byte MAJOR_OPCODE = -102;
  private static final int MAX_BUFFERS = 4;
  // Mesa's Android WSI path uses this private modifier to pass an AHardwareBuffer socket.
  private static final long ANDROID_NATIVE_BUFFER_MODIFIER = 1255L;
  private final Callback<Drawable> onDestroyDrawableListener =
      (drawable) -> {
        ByteBuffer data = drawable.getData();
        if (data != null) SysVSharedMemory.unmapSHMSegment(data, data.capacity());
      };

  private abstract static class ClientOpcodes {
    private static final byte QUERY_VERSION = 0;
    private static final byte OPEN = 1;
    private static final byte PIXMAP_FROM_BUFFER = 2;
    private static final byte PIXMAP_FROM_BUFFERS = 7;
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

  private void queryVersion(XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    inputStream.skip(8);

    try (XStreamLock lock = outputStream.lock()) {
      outputStream.writeByte(RESPONSE_CODE_SUCCESS);
      outputStream.writeByte((byte) 0);
      outputStream.writeShort(client.getSequenceNumber());
      outputStream.writeInt(0);
      outputStream.writeInt(1);
      outputStream.writeInt(0);
      outputStream.writePad(16);
    }
  }

  private void open(XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    int drawableId = inputStream.readInt();
    inputStream.skip(4);

    Drawable drawable = client.xServer.drawableManager.getDrawable(drawableId);
    if (drawable == null) throw new BadDrawable(drawableId);

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
        return;
      }

      if (numBuffers != 1 || stride <= 0 || size <= 0) throw new BadImplementation();
      int fd = fds[0];
      fds[0] = -1;
      pixmapFromFd(client, pixmapId, width, height, stride, offset, depth, bpp, fd, size);
    } finally {
      closeFds(fds);
    }
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
      case ClientOpcodes.PIXMAP_FROM_BUFFERS:
        try (XLock lock =
            client.xServer.lock(
                XServer.Lockable.WINDOW_MANAGER,
                XServer.Lockable.PIXMAP_MANAGER,
                XServer.Lockable.DRAWABLE_MANAGER)) {
          pixmapFromBuffers(client, inputStream, outputStream);
        }
        break;
      default:
        throw new BadImplementation();
    }
  }
}
