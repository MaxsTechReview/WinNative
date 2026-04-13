package com.winlator.cmod.runtime.audio.alsaserver;

import com.winlator.cmod.runtime.display.connector.Client;
import com.winlator.cmod.runtime.display.connector.RequestHandler;
import com.winlator.cmod.runtime.display.connector.XConnectorEpoll;
import com.winlator.cmod.runtime.display.connector.XInputStream;
import com.winlator.cmod.runtime.display.connector.XOutputStream;
import com.winlator.cmod.runtime.display.connector.XStreamLock;
import com.winlator.cmod.sharedmemory.SysVSharedMemory;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ALSARequestHandler implements RequestHandler {
  private int maxSHMemoryId = 0;

  @Override
  public boolean handleRequest(Client client) throws IOException {
    ALSAClient alsaClient = (ALSAClient) client.getTag();
    XInputStream inputStream = client.getInputStream();
    XOutputStream outputStream = client.getOutputStream();

    if (inputStream.available() < 5) return false;
    byte requestCode = inputStream.readByte();
    int requestLength = inputStream.readInt();

    switch (requestCode) {
      case RequestCodes.CLOSE:
        alsaClient.release();
        break;
      case RequestCodes.START:
        alsaClient.start();
        break;
      case RequestCodes.STOP:
        alsaClient.stop();
        break;
      case RequestCodes.PAUSE:
        alsaClient.pause();
        break;
      case RequestCodes.PREPARE:
        if (inputStream.available() < requestLength) return false;

        alsaClient.setChannelCount(inputStream.readByte());
        alsaClient.setDataType(ALSAClient.DataType.values()[inputStream.readByte()]);
        alsaClient.setSampleRate(inputStream.readInt());
        alsaClient.setBufferSize(inputStream.readInt());
        alsaClient.prepare();

        createSharedMemory(alsaClient, outputStream);
        break;
      case RequestCodes.WRITE:
        ByteBuffer buffer = alsaClient.getSharedBuffer();
        if (buffer != null) {
          buffer.limit(requestLength);
          alsaClient.writeDataToStream(buffer);
        } else {
          if (inputStream.available() < requestLength) return false;
          alsaClient.writeDataToStream(inputStream.readByteBuffer(requestLength));
        }
        break;
      case RequestCodes.DRAIN:
        alsaClient.drain();
        break;
      case RequestCodes.POINTER:
        try (XStreamLock lock = outputStream.lock()) {
          outputStream.writeInt(alsaClient.pointer());
        }
        break;
    }
    return true;
  }

  private void createSharedMemory(ALSAClient alsaClient, XOutputStream outputStream)
      throws IOException {
    int size = alsaClient.getBufferSizeInBytes();
    int fd = SysVSharedMemory.createMemoryFd("alsa-shm" + (++maxSHMemoryId), size);

    if (fd >= 0) {
      ByteBuffer buffer = SysVSharedMemory.mapSHMSegment(fd, size, 0, true);
      if (buffer != null) alsaClient.setSharedBuffer(buffer);
    }

    try (XStreamLock lock = outputStream.lock()) {
      outputStream.writeByte((byte) 0);
      outputStream.setAncillaryFd(fd);
    } finally {
      if (fd >= 0) XConnectorEpoll.closeFd(fd);
    }
  }
}
