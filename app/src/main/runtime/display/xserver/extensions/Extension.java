package com.winlator.cmod.runtime.display.xserver.extensions;

import com.winlator.cmod.runtime.display.connector.XInputStream;
import com.winlator.cmod.runtime.display.connector.XOutputStream;
import com.winlator.cmod.runtime.display.xserver.XClient;
import com.winlator.cmod.runtime.display.xserver.errors.XRequestError;
import java.io.IOException;

public interface Extension {
  String getName();

  byte getMajorOpcode();

  byte getFirstErrorId();

  byte getFirstEventId();

  void handleRequest(XClient client, XInputStream inputStream, XOutputStream outputStream)
      throws IOException, XRequestError;
}
