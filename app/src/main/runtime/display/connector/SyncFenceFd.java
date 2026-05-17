package com.winlator.cmod.runtime.display.connector;

/**
 * Native helpers for kernel sync_file FDs (dma_fence) and eventfd signaling.
 *
 * <p>Used by {@link com.winlator.cmod.runtime.display.xserver.extensions.SyncExtension SyncExtension}
 * to back X11 SYNC fences with kernel sync_files imported via DRI3 FenceFromFD, and to export
 * eventfds that signal when a SYNC fence triggers (DRI3 FDFromFence).
 */
public final class SyncFenceFd {
  static {
    System.loadLibrary("winlator");
  }

  private SyncFenceFd() {}

  /**
   * Batched poll(2) over POLLIN with a single timeout (-1 = infinite). Returns an int[] parallel
   * to {@code fds} containing each fd's revents (0 means no event); returns null on allocation
   * failure or if {@code fds} is null. EINTR is retried internally.
   */
  public static native int[] pollFds(int[] fds, int timeoutMs);

  /** Allocate a fresh non-blocking eventfd with initial count 0. */
  public static native int createSignalEventFd();

  /** Duplicate an FD with close-on-exec set. */
  public static native int dupFd(int fd);

  /** Write 1 to an eventfd so a peer waiting on it becomes ready. */
  public static native void signalEventFd(int fd);

  /** Read-and-discard the accumulated counter on a non-blocking eventfd. */
  public static native void drainEventFd(int fd);

  /** close(2) the FD. */
  public static native void closeFd(int fd);
}
