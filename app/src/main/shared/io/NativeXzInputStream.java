package com.winlator.cmod.shared.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public final class NativeXzInputStream extends InputStream {
  private final byte[] singleByteBuffer = new byte[1];
  private long nativeHandle;
  private boolean closed = false;

  static {
    System.loadLibrary("winlator");
  }

  public NativeXzInputStream(File source) throws IOException {
    if (source == null) {
      throw new NullPointerException("source");
    }
    if (!source.isFile()) {
      throw new IOException("Not a file: " + source);
    }

    nativeHandle = nativeOpen(source.getAbsolutePath());
    if (nativeHandle == 0) {
      throw new IOException("Failed to open native XZ decoder for " + source);
    }
  }

  @Override
  public int read() throws IOException {
    int amountRead = read(singleByteBuffer, 0, 1);
    if (amountRead <= 0) {
      return -1;
    }
    return singleByteBuffer[0] & 0xff;
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    if (buffer == null) {
      throw new NullPointerException("buffer");
    }
    if (offset < 0 || length < 0 || length > buffer.length - offset) {
      throw new IndexOutOfBoundsException();
    }
    if (length == 0) {
      return 0;
    }
    ensureOpen();
    return nativeRead(nativeHandle, buffer, offset, length);
  }

  @Override
  public void close() throws IOException {
    if (closed) return;
    closed = true;
    if (nativeHandle != 0) {
      nativeClose(nativeHandle);
      nativeHandle = 0;
    }
  }

  private void ensureOpen() throws IOException {
    if (closed || nativeHandle == 0) {
      throw new IOException("Stream closed");
    }
  }

  private static native long nativeOpen(String path) throws IOException;

  private static native int nativeRead(long handle, byte[] buffer, int offset, int length)
      throws IOException;

  private static native void nativeClose(long handle);
}
