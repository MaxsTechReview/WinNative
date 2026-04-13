package com.winlator.cmod.runtime.display.xserver.events;

import com.winlator.cmod.runtime.display.connector.XOutputStream;
import com.winlator.cmod.runtime.display.connector.XStreamLock;
import java.io.IOException;

public class RawEvent extends Event {
  private final byte[] data;

  public RawEvent(byte[] data) {
    super(data[0]);
    this.data = data;
  }

  @Override
  public void send(short sequenceNumber, XOutputStream outputStream) throws IOException {
    try (XStreamLock lock = outputStream.lock()) {
      outputStream.write(data);
    }
  }
}
