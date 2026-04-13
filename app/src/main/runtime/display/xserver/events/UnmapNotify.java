package com.winlator.cmod.runtime.display.xserver.events;

import com.winlator.cmod.runtime.display.connector.XOutputStream;
import com.winlator.cmod.runtime.display.connector.XStreamLock;
import com.winlator.cmod.runtime.display.xserver.Window;
import java.io.IOException;

public class UnmapNotify extends Event {
  private final Window event;
  private final Window window;

  public UnmapNotify(Window event, Window window) {
    super(18);
    this.event = event;
    this.window = window;
  }

  @Override
  public void send(short sequenceNumber, XOutputStream outputStream) throws IOException {
    try (XStreamLock lock = outputStream.lock()) {
      outputStream.writeByte(code);
      outputStream.writeByte((byte) 0);
      outputStream.writeShort(sequenceNumber);
      outputStream.writeInt(event.id);
      outputStream.writeInt(window.id);
      outputStream.writeByte((byte) 0);
      outputStream.writePad(19);
    }
  }
}
