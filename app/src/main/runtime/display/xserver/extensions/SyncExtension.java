package com.winlator.cmod.runtime.display.xserver.extensions;

import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import com.winlator.cmod.runtime.display.connector.SyncFenceFd;
import com.winlator.cmod.runtime.display.connector.XInputStream;
import com.winlator.cmod.runtime.display.connector.XOutputStream;
import com.winlator.cmod.runtime.display.xserver.XClient;
import com.winlator.cmod.runtime.display.xserver.errors.BadAlloc;
import com.winlator.cmod.runtime.display.xserver.errors.BadFence;
import com.winlator.cmod.runtime.display.xserver.errors.BadIdChoice;
import com.winlator.cmod.runtime.display.xserver.errors.BadImplementation;
import com.winlator.cmod.runtime.display.xserver.errors.BadMatch;
import com.winlator.cmod.runtime.display.xserver.errors.XRequestError;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SyncExtension implements Extension {
  public static final byte MAJOR_OPCODE = -104;
  private final SparseBooleanArray fences = new SparseBooleanArray();
  /** Fence ID -> imported sync_file FD watched by a background poller. */
  private final SparseIntArray waitFds = new SparseIntArray();
  /** Fence ID -> eventfds we own, signaled when the fence triggers. */
  private final SparseArray<List<Integer>> exportFds = new SparseArray<>();
  /** Fence ID -> background poller; exits when the fence triggers or is destroyed. */
  private final SparseArray<Thread> watchers = new SparseArray<>();
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
    List<Integer> toSignal = null;
    int waitFdToClose = -1;
    synchronized (fenceLock) {
      if (fences.indexOfKey(id) < 0) return;
      fences.put(id, true);
      toSignal = exportFds.get(id);
      if (toSignal != null) exportFds.remove(id);
      int waitIdx = waitFds.indexOfKey(id);
      if (waitIdx >= 0) {
        waitFdToClose = waitFds.valueAt(waitIdx);
        waitFds.removeAt(waitIdx);
      }
      watchers.remove(id);
      fenceLock.notifyAll();
    }
    if (toSignal != null) {
      for (Integer fd : toSignal) {
        SyncFenceFd.signalEventFd(fd);
        SyncFenceFd.closeFd(fd);
      }
    }
    if (waitFdToClose >= 0) SyncFenceFd.closeFd(waitFdToClose);
  }

  /** Blocks until at least one of {@code ids} triggers; raises BadFence for unknown IDs. */
  public void waitForFences(int[] ids) throws XRequestError {
    if (ids == null || ids.length == 0) return;
    boolean anyTriggered;
    do {
      anyTriggered = false;
      synchronized (fenceLock) {
        for (int id : ids) {
          if (fences.indexOfKey(id) < 0) throw new BadFence(id);
          if (fences.get(id)) {
            anyTriggered = true;
            break;
          }
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

  /**
   * Register a fence that triggers when {@code fd} (a kernel sync_file) signals. Takes
   * ownership of {@code fd}; closed when the fence is destroyed or the watcher sees the signal.
   */
  public void createFromFd(int id, boolean initiallyTriggered, int fd) throws XRequestError {
    if (fd < 0) throw new BadAlloc();
    Thread watcher = null;
    synchronized (fenceLock) {
      if (fences.indexOfKey(id) >= 0) {
        SyncFenceFd.closeFd(fd);
        throw new BadIdChoice(id);
      }
      fences.put(id, initiallyTriggered);
      waitFds.put(id, fd);
      if (initiallyTriggered) {
        fenceLock.notifyAll();
      } else {
        watcher = new Thread(() -> watchFenceFd(id, fd), "FenceFd-" + id);
        watcher.setDaemon(true);
        watchers.put(id, watcher);
      }
    }
    if (watcher != null) watcher.start();
    if (initiallyTriggered) setTriggered(id);
  }

  /**
   * Create an eventfd that becomes readable when the fence triggers, or -1 on failure. We
   * keep the original FD; the returned duplicate is for SCM_RIGHTS hand-off and the caller
   * must close it after sendmsg.
   */
  public int createExportFd(int id) {
    int serverFd = SyncFenceFd.createSignalEventFd();
    if (serverFd < 0) return -1;
    int replyFd = SyncFenceFd.dupFd(serverFd);
    if (replyFd < 0) {
      SyncFenceFd.closeFd(serverFd);
      return -1;
    }

    boolean signalImmediately;
    synchronized (fenceLock) {
      int idx = fences.indexOfKey(id);
      if (idx < 0) {
        SyncFenceFd.closeFd(replyFd);
        SyncFenceFd.closeFd(serverFd);
        return -1;
      }
      signalImmediately = fences.valueAt(idx);
      if (!signalImmediately) {
        List<Integer> list = exportFds.get(id);
        if (list == null) {
          list = new ArrayList<>(2);
          exportFds.put(id, list);
        }
        list.add(serverFd);
      }
    }
    if (signalImmediately) {
      SyncFenceFd.signalEventFd(serverFd);
      SyncFenceFd.closeFd(serverFd);
    }
    return replyFd;
  }

  private void watchFenceFd(int id, int fd) {
    while (!Thread.currentThread().isInterrupted()) {
      int rc = SyncFenceFd.pollFd(fd, 200);
      if (rc < 0) {
        // Treat poll errors as signaled so Present wait-fences cannot deadlock forever.
        setTriggered(id);
        return;
      }
      if (rc > 0) {
        setTriggered(id);
        return;
      }
      // rc == 0: exit if the fence was destroyed or triggered by another path.
      synchronized (fenceLock) {
        int idx = fences.indexOfKey(id);
        if (idx < 0 || fences.valueAt(idx)) {
          watchers.remove(id);
          return;
        }
      }
    }
  }

  private void drainAndSignalExports(int id) {
    List<Integer> toSignal;
    synchronized (fenceLock) {
      toSignal = exportFds.get(id);
      if (toSignal != null) exportFds.remove(id);
    }
    if (toSignal == null) return;
    for (Integer fd : toSignal) {
      SyncFenceFd.signalEventFd(fd);
      SyncFenceFd.closeFd(fd);
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
    if (initiallyTriggered) drainAndSignalExports(id);
  }

  private void triggerFence(XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    int id = inputStream.readInt();
    synchronized (fenceLock) {
      if (fences.indexOfKey(id) < 0) throw new BadFence(id);
    }
    setTriggered(id);
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
    List<Integer> exportsToClose = null;
    int waitFdToClose = -1;
    Thread watcherToInterrupt = null;
    synchronized (fenceLock) {
      if (fences.indexOfKey(id) < 0) throw new BadFence(id);
      fences.delete(id);

      int waitIdx = waitFds.indexOfKey(id);
      if (waitIdx >= 0) {
        waitFdToClose = waitFds.valueAt(waitIdx);
        waitFds.removeAt(waitIdx);
      }
      exportsToClose = exportFds.get(id);
      if (exportsToClose != null) exportFds.remove(id);
      watcherToInterrupt = watchers.get(id);
      if (watcherToInterrupt != null) watchers.remove(id);
    }
    if (watcherToInterrupt != null) watcherToInterrupt.interrupt();
    if (waitFdToClose >= 0) SyncFenceFd.closeFd(waitFdToClose);
    if (exportsToClose != null) {
      for (Integer fd : exportsToClose) SyncFenceFd.closeFd(fd);
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
