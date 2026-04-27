package com.winlator.cmod.shared.math;

public abstract class Mathf {
  public static float clamp(float x, float min, float max) {
    return (x < min) ? min : ((x > max) ? max : x);
  }

  public static int clamp(int x, int min, int max) {
    return (x < min) ? min : (x > max ? max : x);
  }

  public static float roundTo(float x, float step) {
    return (float) (Math.floor(x / step) * step);
  }

  public static int roundPoint(float x) {
    return (int) (x <= 0 ? Math.floor(x) : Math.ceil(x));
  }

  public static byte sign(float x) {
    return (byte) (x < 0 ? -1 : (x > 0 ? 1 : 0));
  }

  public static float lengthSq(float x, float y) {
    return x * x + y * y;
  }

  public static float distance(float x1, float y1, float x2, float y2) {
    float dx = x1 - x2;
    float dy = y1 - y2;
    return (float) Math.sqrt(dx * dx + dy * dy);
  }
}
