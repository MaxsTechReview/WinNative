package com.winlator.cmod.runtime.display.connector;

public interface ConnectionHandler {
  void handleConnectionShutdown(Client client);

  void handleNewConnection(Client client);
}
