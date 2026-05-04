package com.winlator.cmod.runtime.display.environment.components;

import com.winlator.cmod.runtime.display.connector.UnixSocketConfig;
import com.winlator.cmod.runtime.display.connector.XConnectorEpoll;
import com.winlator.cmod.runtime.display.environment.EnvironmentComponent;
import com.winlator.cmod.runtime.display.xserver.XClientConnectionHandler;
import com.winlator.cmod.runtime.display.xserver.XClientRequestHandler;
import com.winlator.cmod.runtime.display.xserver.XServer;

public class XServerComponent extends EnvironmentComponent {
  private XConnectorEpoll connector;
  private final XServer xServer;
  private final UnixSocketConfig socketConfig;

  public XServerComponent(XServer xServer, UnixSocketConfig socketConfig) {
    this.xServer = xServer;
    this.socketConfig = socketConfig;
  }

  @Override
  public void start() {
    if (connector != null) return;
    connector =
        new XConnectorEpoll(
            socketConfig, new XClientConnectionHandler(xServer), new XClientRequestHandler());
    connector.setMultithreadedClients(true);
    connector.setInitialInputBufferCapacity(262144);
    connector.setCanReceiveAncillaryMessages(true);
    connector.start();
  }

  @Override
  public void stop() {
    if (connector != null) {
      connector.stop();
      connector = null;
    }
  }

  public XServer getXServer() {
    return xServer;
  }
}
