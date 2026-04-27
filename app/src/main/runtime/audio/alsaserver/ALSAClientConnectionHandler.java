package com.winlator.cmod.runtime.audio.alsaserver;

import com.winlator.cmod.runtime.display.connector.Client;
import com.winlator.cmod.runtime.display.connector.ConnectionHandler;

public class ALSAClientConnectionHandler implements ConnectionHandler {
  private final ALSAClient.Options options;

  public ALSAClientConnectionHandler() {
    this(new ALSAClient.Options());
  }

  public ALSAClientConnectionHandler(ALSAClient.Options options) {
    this.options = options != null ? options : new ALSAClient.Options();
  }

  @Override
  public void handleNewConnection(Client client) {
    client.createIOStreams();
    client.setTag(new ALSAClient(options));
  }

  @Override
  public void handleConnectionShutdown(Client client) {
    ((ALSAClient) client.getTag()).release();
  }
}
