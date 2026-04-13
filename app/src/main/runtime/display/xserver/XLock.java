package com.winlator.cmod.runtime.display.xserver;

public interface XLock extends AutoCloseable {
  @Override
  void close();
}
