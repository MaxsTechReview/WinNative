package com.winlator.cmod.runtime.display.renderer;

public class ViewTransformation {
  /**
   * Resize/fit: preserve the game's aspect ratio and scale it uniformly as large as possible
   * inside the physical surface, then center the result. Any leftover space (letterbox/pillarbox)
   * is split evenly on both sides rather than dumped entirely on the right/bottom edge.
   */
  public static final int FILL_MODE_FIT = 0;

  /** Stretch the game to fill the whole surface, ignoring aspect ratio. */
  public static final int FILL_MODE_STRETCH = 1;

  /** Zoom/crop: fill the surface preserving aspect, cropping the overflowing edges. */
  public static final int FILL_MODE_ZOOM = 2;

  public int mode = FILL_MODE_FIT;

  /**
   * When true, forces this update() to compute as FILL_MODE_STRETCH (ignore aspect ratio,
   * no letterbox, fill the whole outer rect) regardless of {@link #mode}. This is the
   * "Fullscreen Stretched" override: setting it here - instead of letting the renderer and
   * the touch input map each hardcode their own "fill the whole surface" formula - is what
   * keeps rendering and touch mapping using the exact same computation in that state, not
   * two independently-written ones that merely happen to agree.
   */
  public boolean forceStretch = false;

  /**
   * The mode actually used by the most recent update() - either {@link #mode}, or
   * FILL_MODE_STRETCH when {@link #forceStretch} was set. Callers must branch on this
   * field, not on {@link #mode}, so they stay consistent with what update() actually
   * computed for aspect/viewOffset/viewWidth/viewHeight.
   */
  public int effectiveMode = FILL_MODE_FIT;

  /** Pixel rectangle occupied by the rendered game inside the physical surface. */
  public int viewOffsetX;
  public int viewOffsetY;
  public int viewWidth;
  public int viewHeight;

  /** Uniform scale from guest pixels to physical pixels. */
  public float aspect;

  /** Inverse scales used by legacy callers for stretch mode. */
  public float sceneScaleX;
  public float sceneScaleY;

  /** Kept for API compatibility; resize/fit intentionally keeps these at zero. */
  public float sceneOffsetX;
  public float sceneOffsetY;

  public void update(int outerWidth, int outerHeight, int innerWidth, int innerHeight) {
    if (outerWidth <= 0 || outerHeight <= 0 || innerWidth <= 0 || innerHeight <= 0) return;

    effectiveMode = forceStretch ? FILL_MODE_STRETCH : mode;

    if (effectiveMode == FILL_MODE_STRETCH) {
      // Full-surface stretch. This is the only mode that intentionally distorts aspect ratio.
      aspect = (float) outerWidth / innerWidth;
      viewOffsetX = 0;
      viewOffsetY = 0;
      viewWidth = outerWidth;
      viewHeight = outerHeight;
      sceneScaleX = (float) innerWidth / outerWidth;
      sceneScaleY = (float) innerHeight / outerHeight;
      sceneOffsetX = 0f;
      sceneOffsetY = 0f;
      return;
    }

    /*
     * FIT and ZOOM both preserve aspect ratio and both center the result:
     *  - FIT uses the smaller ratio so the whole game frame stays visible, with any leftover
     *    surface area split evenly as letterbox/pillarbox bars.
     *  - ZOOM uses the larger ratio so the surface is fully covered, cropping the overflow
     *    evenly off both edges.
     * mapSurfaceToScene()/isInsideRenderedFrame() already account for a non-zero
     * viewOffsetX/Y, so centering here doesn't break touch/cursor mapping.
     */
    aspect = (effectiveMode == FILL_MODE_ZOOM)
        ? Math.max((float) outerWidth / innerWidth, (float) outerHeight / innerHeight)
        : Math.min((float) outerWidth / innerWidth, (float) outerHeight / innerHeight);

    viewWidth = Math.max(1, Math.round(innerWidth * aspect));
    viewHeight = Math.max(1, Math.round(innerHeight * aspect));

    viewOffsetX = Math.round((outerWidth - innerWidth * aspect) * 0.5f);
    viewOffsetY = Math.round((outerHeight - innerHeight * aspect) * 0.5f);

    // Legacy inverse-scale values. Rendering/input use 'aspect' for the uniform modes.
    sceneScaleX = (float) innerWidth / viewWidth;
    sceneScaleY = (float) innerHeight / viewHeight;
    sceneOffsetX = 0f;
    sceneOffsetY = 0f;
  }

  /**
   * Returns true when the physical point lies on the actual rendered game rectangle.
   * For FIT this keeps blank phone-screen area out of the guest coordinate space.
   */
  public boolean isInsideRenderedFrame(float surfaceX, float surfaceY) {
    if (viewWidth <= 0 || viewHeight <= 0) return false;
    return surfaceX >= viewOffsetX
        && surfaceY >= viewOffsetY
        && surfaceX < viewOffsetX + viewWidth
        && surfaceY < viewOffsetY + viewHeight;
  }

  /**
   * Convert a physical output-surface point into guest/X-server coordinates.
   * FIT/ZOOM use exactly the same uniform scale as rendering. FIT has no padding offset.
   */
  public float[] mapSurfaceToScene(float surfaceX, float surfaceY, float[] out) {
    if (out == null || out.length < 2) out = new float[2];

    if (aspect <= 0f) {
      out[0] = surfaceX;
      out[1] = surfaceY;
      return out;
    }

    if (effectiveMode == FILL_MODE_STRETCH) {
      out[0] = surfaceX * sceneScaleX;
      out[1] = surfaceY * sceneScaleY;
    } else {
      out[0] = (surfaceX - viewOffsetX) / aspect;
      out[1] = (surfaceY - viewOffsetY) / aspect;
    }

    // Clamp the inverse transform to the actual guest frame. This prevents taps outside
    // the resized frame from becoming out-of-range X-server coordinates.
    final float sceneWidth = viewWidth / aspect;
    final float sceneHeight = viewHeight / aspect;
    out[0] = Math.max(0f, Math.min(out[0], sceneWidth - 1f));
    out[1] = Math.max(0f, Math.min(out[1], sceneHeight - 1f));
    return out;
  }

  public float mapSurfaceXToScene(float x) {
    if (aspect <= 0f) return x;
    float result = effectiveMode == FILL_MODE_STRETCH ? x * sceneScaleX : (x - viewOffsetX) / aspect;
    return Math.max(0f, Math.min(result, viewWidth / aspect - 1f));
  }

  public float mapSurfaceYToScene(float y) {
    if (aspect <= 0f) return y;
    float result = effectiveMode == FILL_MODE_STRETCH ? y * sceneScaleY : (y - viewOffsetY) / aspect;
    return Math.max(0f, Math.min(result, viewHeight / aspect - 1f));
  }
}
