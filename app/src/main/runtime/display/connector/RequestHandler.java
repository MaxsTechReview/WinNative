package com.winlator.cmod.runtime.display.connector;

import java.io.IOException;

public interface RequestHandler {
  boolean handleRequest(Client client) throws IOException;
}
