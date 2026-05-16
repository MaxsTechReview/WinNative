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
   * Poll the given FD for POLLIN with the specified timeout in milliseconds (-1 = infinite).
   * Returns 1 if the FD is signaled, 0 on timeout, or -1 on error (including the FD being
   * closed while we were polling).
   */
  public static native int pollFd(int fd, int timeoutMs);

  /** Allocate a fresh non-blocking eventfd with initial count 0. */
  public static native int createSignalEventFd();

  /** Duplicate an FD with close-on-exec set. */
  public static native int dupFd(int fd);

  /** Write 1 to an eventfd so a peer waiting on it becomes ready. */
  public static native void signalEventFd(int fd);

  /** close(2) the FD. */
  public static native void closeFd(int fd);

  /** Open /dev/null in O_RDONLY and return the FD. Returns -1 on failure. */
  public static native int openDevNull();
}
