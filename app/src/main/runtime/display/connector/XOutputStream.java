package com.winlator.cmod.runtime.display.connector;

import com.winlator.cmod.runtime.display.xserver.XServer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.locks.ReentrantLock;

public class XOutputStream {
  private static final byte[] ZERO = new byte[64];
  private static final double FP3232_SCALE = 4.294967296E9d;
  public ByteBuffer buffer;
  public final ClientSocket clientSocket;
  private final ReentrantLock lock = new ReentrantLock();
  private int ancillaryFd = -1;

  public XOutputStream(int initialCapacity) {
    this(null, initialCapacity);
  }

  public XOutputStream(ClientSocket clientSocket, int initialCapacity) {
    this.clientSocket = clientSocket;
    buffer = ByteBuffer.allocateDirect(initialCapacity);
  }

  public void setByteOrder(ByteOrder byteOrder) {
    buffer.order(byteOrder);
  }

  public void setAncillaryFd(int ancillaryFd) {
    this.ancillaryFd = ancillaryFd;
  }

  public void writeByte(byte value) {
    if (buffer == null) return;
    ensureSpaceIsAvailable(1);
    buffer.put(value);
  }

  public void writeShort(short value) {
    if (buffer == null) return;
    ensureSpaceIsAvailable(2);
    buffer.putShort(value);
  }

  public void writeInt(int value) {
    if (buffer == null) return;
    ensureSpaceIsAvailable(4);
    buffer.putInt(value);
  }

  public void writeLong(long value) {
    if (buffer == null) return;
    ensureSpaceIsAvailable(8);
    buffer.putLong(value);
  }

  public void writeFP3232(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) throw new IllegalArgumentException("FP3232 value must be finite");
    writeLong(Math.round(value * FP3232_SCALE));
  }

  public void writeString8(String str) {
    if (buffer == null) return;
    byte[] bytes = str.getBytes(XServer.LATIN1_CHARSET);
    int length = -bytes.length & 3;
    ensureSpaceIsAvailable(bytes.length + length);
    buffer.put(bytes);
    if (length > 0) writePad(length);
  }

  public void write(byte[] data) {
    write(data, 0, data.length);
  }

  public void write(byte[] data, int offset, int length) {
    if (buffer == null) return;
    ensureSpaceIsAvailable(length);
    buffer.put(data, offset, length);
  }

  public void write(ByteBuffer data) {
    if (buffer == null) return;
    ensureSpaceIsAvailable(data.remaining());
    buffer.put(data);
  }

  public void writePad(int length) {
    write(ZERO, 0, length);
  }

  private void flush() throws IOException {
    if (buffer == null) return;
    if (buffer.position() != 0) {
      buffer.flip();

      if (ancillaryFd != -1) {
        clientSocket.sendAncillaryMsg(buffer, ancillaryFd);
        ancillaryFd = -1;
      } else clientSocket.write(buffer);

      buffer.clear();
    }
  }

  public XStreamLock lock() {
    return new OutputStreamLock();
  }

  private void ensureSpaceIsAvailable(int length) {
    int position = buffer.position();
    if ((buffer.capacity() - position) >= length) return;
    ByteBuffer newBuffer =
        ByteBuffer.allocateDirect(buffer.capacity() + length).order(buffer.order());
    buffer.rewind();
    newBuffer.put(buffer).position(position);
    buffer = newBuffer;
  }

  private class OutputStreamLock implements XStreamLock {
    public OutputStreamLock() {
      lock.lock();
    }

    @Override
    public void close() throws IOException {
      try {
        flush();
      } finally {
        lock.unlock();
      }
    }
  }

  // Writers hold `lock` for the whole event write; releasing under the same lock
  // means the buffer can never be freed mid-write, and later writes see null and
  // discard (dead connection).
  public void release() {
    lock.lock();
    try {
      if (buffer != null) {
        XInputStream.freeDirectBuffer(buffer);
        buffer = null;
      }
    } finally {
      lock.unlock();
    }
  }

  public void writeSuccessReply(int sequenceNumber, int replyLength) throws IOException {
    try (XStreamLock lock = lock()) {
      writeByte((byte) 1); // Response Code for Success
      writeByte((byte) 0); // Unused
      writeShort((short) sequenceNumber); // Sequence number
      writeInt(replyLength); // Reply length in 4-byte units
      writePad(24); // Unused padding
    }
  }
}
