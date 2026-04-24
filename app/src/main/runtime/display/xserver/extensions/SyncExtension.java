package com.winlator.cmod.runtime.display.xserver.extensions;

import android.util.SparseBooleanArray;
import com.winlator.cmod.runtime.display.connector.XInputStream;
import com.winlator.cmod.runtime.display.connector.XOutputStream;
import com.winlator.cmod.runtime.display.xserver.XClient;
import com.winlator.cmod.runtime.display.xserver.errors.BadFence;
import com.winlator.cmod.runtime.display.xserver.errors.BadIdChoice;
import com.winlator.cmod.runtime.display.xserver.errors.BadImplementation;
import com.winlator.cmod.runtime.display.xserver.errors.BadMatch;
import com.winlator.cmod.runtime.display.xserver.errors.XRequestError;
import java.io.IOException;

public class SyncExtension implements Extension {
  public static final byte MAJOR_OPCODE = -104;
  private final SparseBooleanArray fences = new SparseBooleanArray();
  private final Object fenceLock = new Object();

  private abstract static class ClientOpcodes {
    private static final byte CREATE_FENCE = 14;
    private static final byte TRIGGER_FENCE = 15;
    private static final byte RESET_FENCE = 16;
    private static final byte DESTROY_FENCE = 17;
    private static final byte AWAIT_FENCE = 19;
  }

  @Override
  public String getName() {
    return "SYNC";
  }

  @Override
  public byte getMajorOpcode() {
    return MAJOR_OPCODE;
  }

  @Override
  public byte getFirstErrorId() {
    return Byte.MIN_VALUE;
  }

  @Override
  public byte getFirstEventId() {
    return 0;
  }

  public void setTriggered(int id) {
    synchronized (fenceLock) {
      if (fences.indexOfKey(id) >= 0) {
        fences.put(id, true);
        fenceLock.notifyAll();
      }
    }
  }

  private void createFence(XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    inputStream.skip(4);
    int id = inputStream.readInt();

    boolean initiallyTriggered = inputStream.readByte() == 1;
    inputStream.skip(3);

    synchronized (fenceLock) {
      if (fences.indexOfKey(id) >= 0) throw new BadIdChoice(id);

      fences.put(id, initiallyTriggered);
      if (initiallyTriggered) fenceLock.notifyAll();
    }
  }

  private void triggerFence(XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    int id = inputStream.readInt();
    synchronized (fenceLock) {
      if (fences.indexOfKey(id) < 0) throw new BadFence(id);
      fences.put(id, true);
      fenceLock.notifyAll();
    }
  }

  private void resetFence(XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    int id = inputStream.readInt();
    synchronized (fenceLock) {
      if (fences.indexOfKey(id) < 0) throw new BadFence(id);

      boolean triggered = fences.get(id);
      if (!triggered) throw new BadMatch();

      fences.put(id, false);
    }
  }

  private void destroyFence(XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    int id = inputStream.readInt();
    synchronized (fenceLock) {
      if (fences.indexOfKey(id) < 0) throw new BadFence(id);
      fences.delete(id);
    }
  }

  private void awaitFence(XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    int length = client.getRemainingRequestLength();
    if (length < 0) length = 0;

    int idCount = length / 4;
    int[] ids = new int[idCount];
    for (int i = 0; i < idCount; i++) ids[i] = inputStream.readInt();

    int remaining = length - idCount * 4;
    if (remaining > 0) inputStream.skip(remaining);
    if (ids.length == 0) return;

    boolean anyTriggered;
    do {
      anyTriggered = false;
      synchronized (fenceLock) {
        for (int id : ids) {
          if (fences.indexOfKey(id) < 0) throw new BadFence(id);
          anyTriggered = fences.get(id);
          if (anyTriggered) break;
        }
        if (!anyTriggered) {
          try {
            fenceLock.wait(2L);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
          }
        }
      }
    } while (!anyTriggered);
  }

  @Override
  public void handleRequest(XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    int opcode = client.getRequestData();
    switch (opcode) {
      case ClientOpcodes.CREATE_FENCE:
        createFence(client, inputStream, outputStream);
        break;
      case ClientOpcodes.TRIGGER_FENCE:
        triggerFence(client, inputStream, outputStream);
        break;
      case ClientOpcodes.RESET_FENCE:
        resetFence(client, inputStream, outputStream);
        break;
      case ClientOpcodes.DESTROY_FENCE:
        destroyFence(client, inputStream, outputStream);
        break;
      case ClientOpcodes.AWAIT_FENCE:
        awaitFence(client, inputStream, outputStream);
        break;
      default:
        throw new BadImplementation();
    }
  }
}
