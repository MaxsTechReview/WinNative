package com.winlator.cmod.runtime.display.connector;

import java.io.IOException;

public interface XStreamLock extends AutoCloseable {
  void close() throws IOException;
}
