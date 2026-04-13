package com.winlator.cmod.runtime.display.renderer;

import com.winlator.cmod.runtime.display.xserver.Drawable;

class RenderableWindow {
  final Drawable content;
  short rootX;
  short rootY;

  public RenderableWindow(Drawable content, int rootX, int rootY) {
    this.content = content;
    this.rootX = (short) rootX;
    this.rootY = (short) rootY;
  }
}
