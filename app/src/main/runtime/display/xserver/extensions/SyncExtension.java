package com.winlator.cmod.runtime.display.xserver.extensions;

import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import com.winlator.cmod.runtime.display.connector.SyncFenceFd;
import com.winlator.cmod.runtime.display.connector.XInputStream;
import com.winlator.cmod.runtime.display.connector.XOutputStream;
import com.winlator.cmod.runtime.display.xserver.Pixmap;
import com.winlator.cmod.runtime.display.xserver.Window;
import com.winlator.cmod.runtime.display.xserver.XClient;
import com.winlator.cmod.runtime.display.xserver.XResource;
import com.winlator.cmod.runtime.display.xserver.XResourceManager;
import com.winlator.cmod.runtime.display.xserver.XServer;
import com.winlator.cmod.runtime.display.xserver.errors.BadAlloc;
import com.winlator.cmod.runtime.display.xserver.errors.BadFence;
import com.winlator.cmod.runtime.display.xserver.errors.BadIdChoice;
import com.winlator.cmod.runtime.display.xserver.errors.BadImplementation;
import com.winlator.cmod.runtime.display.xserver.errors.BadMatch;
import com.winlator.cmod.runtime.display.xserver.errors.XRequestError;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SyncExtension
    implements Extension, XResourceManager.OnResourceLifecycleListener {
  public static final byte MAJOR_OPCODE = -104;
  private byte firstEventId;
  private byte firstErrorId;
  private final SparseBooleanArray fences = new SparseBooleanArray();
  /** Fence ID -> drawable id from CreateFence. */
  private final SparseIntArray fenceDrawables = new SparseIntArray();
  /** Fence ID -> imported sync_file FD currently watched by the dispatcher. */
  private final SparseIntArray waitFds = new SparseIntArray();
  /** Fence ID -> eventfds we own, signaled when the fence triggers. */
  private final SparseArray<List<Integer>> exportFds = new SparseArray<>();
  /**
   * FDs that were removed from {@link #waitFds} but cannot be closed yet because the dispatcher
   * may still be polling them. Closed by the dispatcher after each wake, before reissuing poll.
   */
  private final List<Integer> pendingCloseFds = new ArrayList<>();
  private final Object fenceLock = new Object();

  /** eventfd used to wake the dispatcher when its watch set changes; -1 until lazy start. */
  private volatile int dispatcherControlFd = -1;
  private Thread dispatcherThread = null;
  private boolean lifecycleListenersRegistered = false;

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
    return firstErrorId;
  }

  @Override
  public byte getFirstEventId() {
    return firstEventId;
  }

  @Override
  public int getNumEvents() {
    return 2;
  }

  @Override
  public int getNumErrors() {
    return 2;
  }

  @Override
  public void setFirstEventId(byte id) {
    this.firstEventId = id;
  }

  @Override
  public void setFirstErrorId(byte id) {
    this.firstErrorId = id;
  }

  /**
   * Mark fence {@code id} triggered. Signals all export eventfds and queues the imported
   * sync_file FD for close by the dispatcher (so we don't close an FD the dispatcher may
   * currently be polling). No-op if the fence is unknown.
   */
  public void setTriggered(int id) {
    List<Integer> toSignal;
    boolean wake = false;
    synchronized (fenceLock) {
      if (fences.indexOfKey(id) < 0) return;
      fences.put(id, true);
      toSignal = exportFds.get(id);
      if (toSignal != null) exportFds.remove(id);
      int waitIdx = waitFds.indexOfKey(id);
      if (waitIdx >= 0) {
        pendingCloseFds.add(waitFds.valueAt(waitIdx));
        waitFds.removeAt(waitIdx);
        wake = true;
      }
      fenceLock.notifyAll();
    }
    if (toSignal != null) {
      for (Integer fd : toSignal) {
        SyncFenceFd.signalEventFd(fd);
        SyncFenceFd.closeFd(fd);
      }
    }
    if (wake) wakeDispatcher();
  }

  /** Blocks until at least one of {@code ids} triggers; raises BadFence for unknown IDs. */
  public void waitForFences(int[] ids) throws XRequestError {
    if (ids == null || ids.length == 0) return;
    synchronized (fenceLock) {
      while (true) {
        for (int id : ids) {
          if (fences.indexOfKey(id) < 0) throw new BadFence(id);
          if (fences.get(id)) return;
        }
        try {
          fenceLock.wait();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }
  }

  /**
   * Register a fence that triggers when {@code fd} (a kernel sync_file) signals. Takes
   * ownership of {@code fd}; closed when the fence is destroyed or the dispatcher sees the
   * signal.
   */
  public void createFromFd(int id, boolean initiallyTriggered, int fd) throws XRequestError {
    if (fd < 0) throw new BadAlloc();
    synchronized (fenceLock) {
      if (fences.indexOfKey(id) >= 0) {
        SyncFenceFd.closeFd(fd);
        throw new BadIdChoice(id);
      }
      // Make sure the dispatcher exists BEFORE recording the fence, otherwise a failure
      // here would leave a fence that can never be polled or triggered.
      if (!initiallyTriggered && !ensureDispatcherStarted()) {
        SyncFenceFd.closeFd(fd);
        throw new BadAlloc();
      }
      fences.put(id, initiallyTriggered);
      if (initiallyTriggered) {
        // No polling needed; release the FD immediately.
        SyncFenceFd.closeFd(fd);
        fenceLock.notifyAll();
      } else {
        waitFds.put(id, fd);
      }
    }
    if (initiallyTriggered) drainAndSignalExports(id);
    else wakeDispatcher();
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

  // --- Dispatcher ----------------------------------------------------------

  /** Must be called under {@link #fenceLock}. Returns false on eventfd allocation failure. */
  private boolean ensureDispatcherStarted() {
    if (dispatcherThread != null) return true;
    int fd = SyncFenceFd.createSignalEventFd();
    if (fd < 0) return false;
    dispatcherControlFd = fd;
    dispatcherThread = new Thread(this::dispatcherLoop, "SyncFenceDispatcher");
    dispatcherThread.setDaemon(true);
    dispatcherThread.start();
    return true;
  }

  private void wakeDispatcher() {
    int fd = dispatcherControlFd;
    if (fd >= 0) SyncFenceFd.signalEventFd(fd);
  }

  private void dispatcherLoop() {
    int[] watchIds = new int[0];
    // First slot is the control fd; remaining slots mirror watchIds.
    int[] pollFdArr = new int[] { dispatcherControlFd };

    while (!Thread.currentThread().isInterrupted()) {
      int[] revents = SyncFenceFd.pollFds(pollFdArr, -1);
      if (revents == null || revents.length != pollFdArr.length) {
        // Native failure; back off briefly so we don't spin if it persists.
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
        continue;
      }

      if (revents[0] != 0) SyncFenceFd.drainEventFd(dispatcherControlFd);
      for (int i = 0; i < watchIds.length; i++) {
        // Any event (POLLIN/POLLERR/POLLHUP/POLLNVAL) means the fence is done; setTriggered
        // is a no-op if the fence was already destroyed.
        if (revents[i + 1] != 0) setTriggered(watchIds[i]);
      }

      // Drain pending closes and rebuild the watch snapshot under lock. setTriggered above
      // added to pendingCloseFds for each fd that fired, so this is also where freshly-fired
      // FDs get closed.
      List<Integer> toClose;
      synchronized (fenceLock) {
        toClose = pendingCloseFds.isEmpty() ? null : new ArrayList<>(pendingCloseFds);
        pendingCloseFds.clear();
        int n = waitFds.size();
        if (watchIds.length != n) watchIds = new int[n];
        pollFdArr = new int[n + 1];
        pollFdArr[0] = dispatcherControlFd;
        for (int i = 0; i < n; i++) {
          watchIds[i] = waitFds.keyAt(i);
          pollFdArr[i + 1] = waitFds.valueAt(i);
        }
      }
      if (toClose != null) {
        for (int fd : toClose) SyncFenceFd.closeFd(fd);
      }
    }
  }

  // --- X SYNC opcodes ------------------------------------------------------

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
    int drawableId = inputStream.readInt();
    int id = inputStream.readInt();

    boolean initiallyTriggered = inputStream.readByte() == 1;
    inputStream.skip(3);

    synchronized (fenceLock) {
      if (fences.indexOfKey(id) >= 0) throw new BadIdChoice(id);

      fences.put(id, initiallyTriggered);
      fenceDrawables.put(id, drawableId);
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
    boolean wake = false;
    synchronized (fenceLock) {
      if (fences.indexOfKey(id) < 0) throw new BadFence(id);
      fences.delete(id);
      fenceDrawables.delete(id);

      int waitIdx = waitFds.indexOfKey(id);
      if (waitIdx >= 0) {
        pendingCloseFds.add(waitFds.valueAt(waitIdx));
        waitFds.removeAt(waitIdx);
        wake = true;
      }
      exportsToClose = exportFds.get(id);
      if (exportsToClose != null) exportFds.remove(id);
      // Wake any waitForFences blocked on this id so they observe the deletion and raise
      // BadFence rather than blocking forever.
      fenceLock.notifyAll();
    }
    if (wake) wakeDispatcher();
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

    waitForFences(ids);
  }

  private void registerLifecycleListeners(XServer xServer) {
    if (lifecycleListenersRegistered) return;
    synchronized (this) {
      if (lifecycleListenersRegistered) return;
      xServer.pixmapManager.addOnResourceLifecycleListener(this);
      xServer.windowManager.addOnResourceLifecycleListener(this);
      lifecycleListenersRegistered = true;
    }
  }

  /** Drop fences bound to a freed drawable the client never destroyed; mirrors destroyFence. */
  @Override
  public void onFreeResource(XResource resource) {
    if (!(resource instanceof Pixmap) && !(resource instanceof Window)) return;
    int drawableId = resource.id;
    List<Integer> exportsToClose = null;
    boolean wake = false;
    synchronized (fenceLock) {
      for (int i = fenceDrawables.size() - 1; i >= 0; i--) {
        if (fenceDrawables.valueAt(i) != drawableId) continue;
        int id = fenceDrawables.keyAt(i);
        fenceDrawables.removeAt(i);
        fences.delete(id);

        int waitIdx = waitFds.indexOfKey(id);
        if (waitIdx >= 0) {
          pendingCloseFds.add(waitFds.valueAt(waitIdx));
          waitFds.removeAt(waitIdx);
          wake = true;
        }
        List<Integer> exports = exportFds.get(id);
        if (exports != null) {
          if (exportsToClose == null) exportsToClose = new ArrayList<>();
          exportsToClose.addAll(exports);
          exportFds.remove(id);
        }
      }
      fenceLock.notifyAll();
    }
    if (wake) wakeDispatcher();
    if (exportsToClose != null) {
      for (Integer fd : exportsToClose) SyncFenceFd.closeFd(fd);
    }
  }

  @Override
  public void handleRequest(XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError {
    registerLifecycleListeners(client.xServer);
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
