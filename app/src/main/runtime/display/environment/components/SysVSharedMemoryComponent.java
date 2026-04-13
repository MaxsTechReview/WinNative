package com.winlator.cmod.runtime.display.environment.components;

import com.winlator.cmod.runtime.display.connector.UnixSocketConfig;
import com.winlator.cmod.runtime.display.connector.XConnectorEpoll;
import com.winlator.cmod.runtime.display.environment.EnvironmentComponent;
import com.winlator.cmod.runtime.display.xserver.SHMSegmentManager;
import com.winlator.cmod.runtime.display.xserver.XServer;
import com.winlator.cmod.sharedmemory.SysVSHMConnectionHandler;
import com.winlator.cmod.sharedmemory.SysVSHMRequestHandler;
import com.winlator.cmod.sharedmemory.SysVSharedMemory;

public class SysVSharedMemoryComponent extends EnvironmentComponent {
  private XConnectorEpoll connector;
  public final UnixSocketConfig socketConfig;
  private SysVSharedMemory sysVSharedMemory;
  private final XServer xServer;

  public SysVSharedMemoryComponent(XServer xServer, UnixSocketConfig socketConfig) {
    this.xServer = xServer;
    this.socketConfig = socketConfig;
  }

  @Override
  public void start() {
    if (connector != null) return;
    sysVSharedMemory = new SysVSharedMemory();
    connector =
        new XConnectorEpoll(
            socketConfig,
            new SysVSHMConnectionHandler(sysVSharedMemory),
            new SysVSHMRequestHandler());
    connector.start();

    xServer.setSHMSegmentManager(new SHMSegmentManager(sysVSharedMemory));
  }

  @Override
  public void stop() {
    if (connector != null) {
      connector.stop();
      connector = null;
    }

    sysVSharedMemory.deleteAll();
  }
}
