package com.winlator.cmod.shared.util;

import java.io.File;

public interface OnExtractFileListener {
  File onExtractFile(File destination, long size);

  default void onExtractFileProgress(File destination, long size) {}

  default boolean mapsExtractedFiles() {
    return true;
  }

  default boolean reportsExtractedBytesOnly() {
    return false;
  }

  default void onExtractedBytes(long size) {}
}
