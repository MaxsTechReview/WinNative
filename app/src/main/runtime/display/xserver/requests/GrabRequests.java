package com.winlator.cmod.runtime.display.xserver.requests;

import static com.winlator.cmod.runtime.display.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import com.winlator.cmod.runtime.display.connector.XInputStream;
import com.winlator.cmod.runtime.display.connector.XOutputStream;
import com.winlator.cmod.runtime.display.connector.XStreamLock;
import com.winlator.cmod.runtime.display.xserver.Bitmask;
import com.winlator.cmod.runtime.display.xserver.Window;
import com.winlator.cmod.runtime.display.xserver.XClient;
import com.winlator.cmod.runtime.display.xserver.errors.BadWindow;
import com.winlator.cmod.runtime.display.xserver.errors.XRequestError;
import java.io.IOException;

public abstract class GrabRequests {
  private enum Status {
    SUCCESS,
    ALREADY_GRABBED,
    INVALID_TIME,
    NOT_VIEWABLE,
    FROZEN
  }

  public static void grabPointer(
      XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    if (client.xServer.isRelativeMouseMovement()) {
      client.skipRequest();
      try (XStreamLock lock = outputStream.lock()) {
        outputStream.writeByte(RESPONSE_CODE_SUCCESS);
        outputStream.writeByte((byte) Status.ALREADY_GRABBED.ordinal());
        outputStream.writeShort(client.getSequenceNumber());
        outputStream.writeInt(0);
        outputStream.writePad(24);
      }
      return;
    }

    boolean ownerEvents = client.getRequestData() == 1;
    int windowId = inputStream.readInt();
    Window window = client.xServer.windowManager.getWindow(windowId);
    if (window == null) throw new BadWindow(windowId);

    Bitmask eventMask = new Bitmask(inputStream.readShort());
    inputStream.skip(14);

    Status status;
    if (client.xServer.grabManager.getWindow() != null
        && client.xServer.grabManager.getClient() != client) {
      status = Status.ALREADY_GRABBED;
    } else if (window.getMapState() != Window.MapState.VIEWABLE) {
      status = Status.NOT_VIEWABLE;
    } else {
      status = Status.SUCCESS;
      client.xServer.grabManager.activatePointerGrab(window, ownerEvents, eventMask, client);
    }

    try (XStreamLock lock = outputStream.lock()) {
      outputStream.writeByte(RESPONSE_CODE_SUCCESS);
      outputStream.writeByte((byte) status.ordinal());
      outputStream.writeShort(client.getSequenceNumber());
      outputStream.writeInt(0);
      outputStream.writePad(24);
    }
  }

  public static void ungrabPointer(
      XClient client, XInputStream inputStream, XOutputStream outputStream) {
    inputStream.skip(4);
    client.xServer.grabManager.deactivatePointerGrab();
  }
}
