package com.winlator.cmod.runtime.display.winhandler;

interface UsbRumbleDevice {
  boolean start();

  void rumble(int strong, int weak);

  void stop();
}
