package com.winlator.cmod.runtime.display.xserver.errors;

public class BadPixmap extends XRequestError {
  public BadPixmap(int id) {
    super(4, id);
  }
}
