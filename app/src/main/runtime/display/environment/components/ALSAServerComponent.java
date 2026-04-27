package com.winlator.cmod.runtime.display.environment.components;

import com.winlator.cmod.runtime.audio.alsaserver.ALSAClientConnectionHandler;
import com.winlator.cmod.runtime.audio.alsaserver.ALSARequestHandler;
import com.winlator.cmod.runtime.audio.alsaserver.ALSAClient;
import com.winlator.cmod.runtime.display.connector.UnixSocketConfig;
import com.winlator.cmod.runtime.display.connector.XConnectorEpoll;
import com.winlator.cmod.runtime.display.environment.EnvironmentComponent;

public class ALSAServerComponent extends EnvironmentComponent {
  private XConnectorEpoll connector;
  private final UnixSocketConfig socketConfig;
  private final ALSAClient.Options options;

  public ALSAServerComponent(UnixSocketConfig socketConfig) {
    this(socketConfig, new ALSAClient.Options());
  }

  public ALSAServerComponent(UnixSocketConfig socketConfig, ALSAClient.Options options) {
    this.socketConfig = socketConfig;
    this.options = options != null ? options : new ALSAClient.Options();
  }

  @Override
  public void start() {
    if (connector != null) return;
    ALSAClient.assignFramesPerBuffer(environment.getContext());
    connector =
        new XConnectorEpoll(
            socketConfig, new ALSAClientConnectionHandler(options), new ALSARequestHandler());
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
