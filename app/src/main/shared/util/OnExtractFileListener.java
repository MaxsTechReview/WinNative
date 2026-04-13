package com.winlator.cmod.shared.util;

import java.io.File;

public interface OnExtractFileListener {
  File onExtractFile(File destination, long size);
}
