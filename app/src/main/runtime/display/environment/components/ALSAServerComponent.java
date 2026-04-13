package com.winlator.cmod.runtime.display.environment.components;

import com.winlator.cmod.runtime.audio.alsaserver.ALSAClientConnectionHandler;
import com.winlator.cmod.runtime.audio.alsaserver.ALSARequestHandler;
import com.winlator.cmod.runtime.display.connector.UnixSocketConfig;
import com.winlator.cmod.runtime.display.connector.XConnectorEpoll;
import com.winlator.cmod.runtime.display.environment.EnvironmentComponent;

public class ALSAServerComponent extends EnvironmentComponent {
  private XConnectorEpoll connector;
  private final UnixSocketConfig socketConfig;

  public ALSAServerComponent(UnixSocketConfig socketConfig) {
    this.socketConfig = socketConfig;
  }

  @Override
  public void start() {
    if (connector != null) return;
    connector =
        new XConnectorEpoll(
            socketConfig, new ALSAClientConnectionHandler(), new ALSARequestHandler());
    connector.setMultithreadedClients(true);
    connector.start();
  }

  @Override
  public void stop() {
    if (connector != null) {
      connector.stop();
      connector = null;
    }
  }
}
