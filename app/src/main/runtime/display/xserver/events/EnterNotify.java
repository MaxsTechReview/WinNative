package com.winlator.cmod.runtime.display.xserver.events;

import com.winlator.cmod.runtime.display.xserver.Bitmask;
import com.winlator.cmod.runtime.display.xserver.Window;

public class EnterNotify extends PointerWindowEvent {
  public EnterNotify(
      Detail detail,
      Window root,
      Window event,
      Window child,
      short rootX,
      short rootY,
      short eventX,
      short eventY,
      Bitmask state,
      Mode mode,
      boolean sameScreenAndFocus) {
    super(
        7,
        detail,
        root,
        event,
        child,
        rootX,
        rootY,
        eventX,
        eventY,
        state,
        mode,
        sameScreenAndFocus);
  }
}
