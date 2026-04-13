package com.winlator.cmod.runtime.audio.alsaserver;

import com.winlator.cmod.runtime.display.connector.Client;
import com.winlator.cmod.runtime.display.connector.ConnectionHandler;

public class ALSAClientConnectionHandler implements ConnectionHandler {
  @Override
  public void handleNewConnection(Client client) {
    client.createIOStreams();
    client.setTag(new ALSAClient());
  }

  @Override
  public void handleConnectionShutdown(Client client) {
    ((ALSAClient) client.getTag()).release();
  }
}
