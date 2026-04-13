package com.winlator.cmod.sharedmemory;

import com.winlator.cmod.runtime.display.connector.Client;
import com.winlator.cmod.runtime.display.connector.ConnectionHandler;

public class SysVSHMConnectionHandler implements ConnectionHandler {
  private final SysVSharedMemory sysVSharedMemory;

  public SysVSHMConnectionHandler(SysVSharedMemory sysVSharedMemory) {
    this.sysVSharedMemory = sysVSharedMemory;
  }

  @Override
  public void handleNewConnection(Client client) {
    client.createIOStreams();
    client.setTag(sysVSharedMemory);
  }

  @Override
  public void handleConnectionShutdown(Client client) {}
}
