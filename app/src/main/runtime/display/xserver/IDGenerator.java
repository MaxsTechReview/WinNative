package com.winlator.cmod.runtime.display.xserver;

public abstract class IDGenerator {
  private static int id = 0;

  public static int generate() {
    return ++id;
  }
}
