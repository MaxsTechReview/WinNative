package com.winlator.cmod.runtime.display.xserver;

public class ScreenInfo {
  public volatile short width;
  public volatile short height;

  public ScreenInfo(String value) {
    String[] parts = value.split("x");
    setSize(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
  }

  public ScreenInfo(int width, int height) {
    setSize(width, height);
  }

  public void setSize(ScreenInfo other) {
    if (other == null) return;
    setSize(other.width, other.height);
  }

  public void setSize(int width, int height) {
    if (width <= 0 || height <= 0 || width > Short.MAX_VALUE || height > Short.MAX_VALUE) {
      throw new IllegalArgumentException("Invalid screen size " + width + "x" + height);
    }
    this.width = (short) width;
    this.height = (short) height;
  }

  public short getWidthInMillimeters() {
    return (short) (width / 10);
  }

  public short getHeightInMillimeters() {
    return (short) (height / 10);
  }

  @Override
  public String toString() {
    return width + "x" + height;
  }
}
