package com.winlator.cmod.runtime.input.controls;

import android.graphics.Path;
import android.graphics.RectF;
import java.util.EnumMap;

/**
 * Layout overlay applied when {@link VisualStyle#GAMEHUB} is active.
 *
 * <p>Each on-screen control whose primary binding matches a known gamepad role gets relocated and
 * reshaped at draw/hit-test time to mirror the GameHub launcher's reference layout (extracted from
 * the decompiled <code>OfficialLayout_gamepad.gtheme</code>). Underlying profile JSON is never
 * mutated — overrides are a runtime decoration only, so switching back to {@link
 * VisualStyle#ORIGINAL} immediately restores the user's saved positions.
 *
 * <p>Edit mode bypasses overrides so the user manipulates their real saved layout.
 */
public final class GameHubLayout {

  public enum Role {
    BTN_A,
    BTN_B,
    BTN_X,
    BTN_Y,
    LB,
    LT,
    RB,
    RT,
    L3,
    R3,
    LSTICK,
    RSTICK,
    DPAD,
    START,
    SELECT
  }

  /**
   * Shapes used by the GameHub renderer. Distinct from {@link ControlElement.Shape} so we don't
   * pollute the persisted enum with values that only make sense at draw time.
   */
  public enum RenderShape {
    CIRCLE,
    ROUND_RECT,
    TRIGGER_LT,
    TRIGGER_LB,
    TRIGGER_RT,
    TRIGGER_RB,
    DPAD_CROSS
  }

  public static final class Override {
    /** Anchor center in normalized view coordinates (0..1). */
    public final float normX;

    public final float normY;
    /** Half-extents expressed in snappingSize units (snappingSize = viewWidth/100). */
    public final float halfWidthSnap;

    public final float halfHeightSnap;
    public final RenderShape shape;
    public final float scale;

    Override(float nx, float ny, float hw, float hh, RenderShape shape, float scale) {
      this.normX = nx;
      this.normY = ny;
      this.halfWidthSnap = hw;
      this.halfHeightSnap = hh;
      this.shape = shape;
      this.scale = scale;
    }
  }

  private static final EnumMap<Role, Override> OVERRIDES = new EnumMap<>(Role.class);

  static {
    // Shoulders / triggers — anchored hard against the top corners. Vertical spacing matches the
    // GameHub asset (LT y=0.115, LB y=0.280) so the two pills sit clearly apart instead of
    // visually touching.
    OVERRIDES.put(Role.LT, new Override(0.087f, 0.115f, 5.5f, 2.3f, RenderShape.TRIGGER_LT, 1.0f));
    OVERRIDES.put(Role.LB, new Override(0.087f, 0.280f, 5.5f, 2.3f, RenderShape.TRIGGER_LB, 1.0f));
    OVERRIDES.put(Role.RT, new Override(0.913f, 0.115f, 5.5f, 2.3f, RenderShape.TRIGGER_RT, 1.0f));
    OVERRIDES.put(Role.RB, new Override(0.913f, 0.280f, 5.5f, 2.3f, RenderShape.TRIGGER_RB, 1.0f));

    // Stick-click buttons (L3/R3) — small circles tucked just under the shoulder column.
    OVERRIDES.put(Role.L3, new Override(0.055f, 0.42f, 2.1f, 2.1f, RenderShape.CIRCLE, 1.0f));
    OVERRIDES.put(Role.R3, new Override(0.945f, 0.42f, 2.1f, 2.1f, RenderShape.CIRCLE, 1.0f));

    // Joysticks — large translucent rings, bottom row. Ring diameter ≈ 15% of view width,
    // matching the GameHub reference measurement. Right stick pushed further right to clear the
    // d-pad cluster and match the GameHub asset's x=0.684 anchor.
    OVERRIDES.put(Role.LSTICK, new Override(0.16f, 0.70f, 7.0f, 7.0f, RenderShape.CIRCLE, 1.0f));
    OVERRIDES.put(Role.RSTICK, new Override(0.72f, 0.70f, 7.0f, 7.0f, RenderShape.CIRCLE, 1.0f));

    // D-pad in the gap between the left stick and the right-side face buttons. Bbox is large so
    // each detached arrow has room to render as a recognizable chevron.
    OVERRIDES.put(Role.DPAD, new Override(0.335f, 0.70f, 7.5f, 7.5f, RenderShape.DPAD_CROSS, 1.0f));

    // Face buttons — diamond cluster. X↔B center distance set to 0.11 of view WIDTH so it reads
    // visually equal to the Y↔A 0.24-of-view-HEIGHT span on a typical landscape device (since
    // view height in landscape ≈ view width / 2.2, 0.24*height ≈ 0.109*width). Cluster center at
    // 0.89 sits 25% closer to the right edge than the previous 0.85.
    OVERRIDES.put(Role.BTN_Y, new Override(0.890f, 0.60f, 3.0f, 3.0f, RenderShape.CIRCLE, 1.0f));
    OVERRIDES.put(Role.BTN_X, new Override(0.835f, 0.72f, 3.0f, 3.0f, RenderShape.CIRCLE, 1.0f));
    OVERRIDES.put(Role.BTN_B, new Override(0.945f, 0.72f, 3.0f, 3.0f, RenderShape.CIRCLE, 1.0f));
    OVERRIDES.put(Role.BTN_A, new Override(0.890f, 0.84f, 3.0f, 3.0f, RenderShape.CIRCLE, 1.0f));

    // Start / Select pills along the bottom center — leave at original-ish positions because the
    // GameHub reference is sparse here.
    OVERRIDES.put(Role.START, new Override(0.54f, 0.94f, 3f, 1.8f, RenderShape.ROUND_RECT, 1.0f));
    OVERRIDES.put(Role.SELECT, new Override(0.46f, 0.94f, 3f, 1.8f, RenderShape.ROUND_RECT, 1.0f));
  }

  public static Override overrideFor(Role role) {
    return role != null ? OVERRIDES.get(role) : null;
  }

  /**
   * Maps a {@link ControlElement} to a logical role by inspecting its primary binding and type.
   * Returns {@code null} for elements that don't correspond to a known gamepad control (custom
   * keyboard buttons, trackpads, range buttons, radial menus, etc.) — those keep their saved
   * positions.
   */
  public static Role roleFor(ControlElement element) {
    if (element == null) return null;
    ControlElement.Type type = element.getType();

    if (type == ControlElement.Type.STICK) {
      for (int i = 0; i < element.getBindingCount(); i++) {
        Binding b = element.getBindingAt(i);
        if (b == Binding.GAMEPAD_LEFT_THUMB_UP
            || b == Binding.GAMEPAD_LEFT_THUMB_DOWN
            || b == Binding.GAMEPAD_LEFT_THUMB_LEFT
            || b == Binding.GAMEPAD_LEFT_THUMB_RIGHT) return Role.LSTICK;
        if (b == Binding.GAMEPAD_RIGHT_THUMB_UP
            || b == Binding.GAMEPAD_RIGHT_THUMB_DOWN
            || b == Binding.GAMEPAD_RIGHT_THUMB_LEFT
            || b == Binding.GAMEPAD_RIGHT_THUMB_RIGHT) return Role.RSTICK;
      }
      return null;
    }
    if (type == ControlElement.Type.D_PAD) return Role.DPAD;
    if (type != ControlElement.Type.BUTTON) return null;

    Binding b0 = element.getBindingAt(0);
    switch (b0) {
      case GAMEPAD_BUTTON_A:
        return Role.BTN_A;
      case GAMEPAD_BUTTON_B:
        return Role.BTN_B;
      case GAMEPAD_BUTTON_X:
        return Role.BTN_X;
      case GAMEPAD_BUTTON_Y:
        return Role.BTN_Y;
      case GAMEPAD_BUTTON_L1:
        return Role.LB;
      case GAMEPAD_BUTTON_R1:
        return Role.RB;
      case GAMEPAD_BUTTON_L2:
        return Role.LT;
      case GAMEPAD_BUTTON_R2:
        return Role.RT;
      case GAMEPAD_BUTTON_L3:
        return Role.L3;
      case GAMEPAD_BUTTON_R3:
        return Role.R3;
      case GAMEPAD_BUTTON_START:
        return Role.START;
      case GAMEPAD_BUTTON_SELECT:
        return Role.SELECT;
      default:
        return null;
    }
  }

  /**
   * Builds the exact GameHub shoulder/trigger silhouette in {@code out}. Geometry reverse-engineered
   * from the decompiled GameHub asset (sources/defpackage/yhm.java) — see References/GameHub.
   *
   * <p>For each variant the slanted corner is on the OUTER (screen-edge) side, and is formed by a
   * straight diagonal between two quadratic-curved "elbow" points. The opposite three corners are
   * standard 90° arcs of radius {@code r = min(w,h) * 0.1875}.
   */
  public static void buildTriggerPath(
      Path out, RenderShape shape, float left, float top, float right, float bottom) {
    out.reset();
    float w = right - left;
    float h = bottom - top;
    float r = Math.min(w, h) * 0.1875f;
    float f = 0.2f * w;
    float diag = (float) Math.sqrt(h * h + f * f);
    float sx = (f / diag) * r; // horizontal inset of the slant endpoint near the slanted corner
    float sy = (h / diag) * r; // vertical inset of the slant endpoint at the elbow

    RectF tmp = new RectF();
    switch (shape) {
      case TRIGGER_LT: {
        // Slant on TOP-LEFT (outer corner of the top-left trigger).
        out.moveTo(left + sx, bottom - sy);
        out.lineTo(left + f - sx, top + sy);
        out.quadTo(left + f, top, left + f + r, top);
        out.lineTo(right - r, top);
        tmp.set(right - 2 * r, top, right, top + 2 * r);
        out.arcTo(tmp, -90, 90, false);
        out.lineTo(right, bottom - r);
        tmp.set(right - 2 * r, bottom - 2 * r, right, bottom);
        out.arcTo(tmp, 0, 90, false);
        out.lineTo(left + r, bottom);
        out.quadTo(left, bottom, left + sx, bottom - sy);
        out.close();
        break;
      }
      case TRIGGER_LB: {
        // Slant on BOTTOM-LEFT (outer corner of the left bumper).
        out.moveTo(left + r, top);
        out.lineTo(right - r, top);
        tmp.set(right - 2 * r, top, right, top + 2 * r);
        out.arcTo(tmp, -90, 90, false);
        out.lineTo(right, bottom - r);
        tmp.set(right - 2 * r, bottom - 2 * r, right, bottom);
        out.arcTo(tmp, 0, 90, false);
        out.lineTo(left + f + r, bottom);
        out.quadTo(left + f, bottom, left + f - sx, bottom - sy);
        out.lineTo(left + sx, top + sy);
        out.quadTo(left, top, left + r, top);
        out.close();
        break;
      }
      case TRIGGER_RT: {
        // Slant on TOP-RIGHT (mirror of TRIGGER_LT) — symmetric with the left trigger so both
        // upper-row triggers point their slanted "tabs" toward the outer screen corners.
        out.moveTo(right - sx, bottom - sy);
        out.lineTo(right - f + sx, top + sy);
        out.quadTo(right - f, top, right - f - r, top);
        out.lineTo(left + r, top);
        tmp.set(left, top, left + 2 * r, top + 2 * r);
        out.arcTo(tmp, 270, -90, false);
        out.lineTo(left, bottom - r);
        tmp.set(left, bottom - 2 * r, left + 2 * r, bottom);
        out.arcTo(tmp, 180, -90, false);
        out.lineTo(right - r, bottom);
        out.quadTo(right, bottom, right - sx, bottom - sy);
        out.close();
        break;
      }
      case TRIGGER_RB: {
        // Slant on BOTTOM-RIGHT (mirror of TRIGGER_LB) — bumper's outer slant.
        out.moveTo(right - r, top);
        out.lineTo(left + r, top);
        tmp.set(left, top, left + 2 * r, top + 2 * r);
        out.arcTo(tmp, 270, -90, false);
        out.lineTo(left, bottom - r);
        tmp.set(left, bottom - 2 * r, left + 2 * r, bottom);
        out.arcTo(tmp, 180, -90, false);
        out.lineTo(right - f - r, bottom);
        out.quadTo(right - f, bottom, right - f + sx, bottom - sy);
        out.lineTo(right - sx, top + sy);
        out.quadTo(right, top, right - r, top);
        out.close();
        break;
      }
      default:
        out.addRoundRect(left, top, right, bottom, r, r, Path.Direction.CW);
        break;
    }
  }

  /**
   * Builds the four separate GameHub d-pad arrows into {@code out} as a single path with four
   * sub-shapes. Each arrow is a small pentagonal "wedge" with the tip on the outer edge of a
   * circle of radius {@code radius} centered at {@code (cx, cy)} — matching the proportions in
   * {@code features_gamepad_config_dpad_center.xml} (128x128 viewport, tip ~1 unit from the edge
   * and base ~9 units in).
   */
  public static void buildDpadArrows(Path out, float cx, float cy, float radius) {
    out.reset();
    for (int side = 0; side < 4; side++) buildDpadArrow(out, side, cx, cy, radius);
  }

  /** D-pad arrow sides used by {@link #buildDpadArrow} and {@link #dpadArrowCenter}. */
  public static final int DPAD_UP = 0;
  public static final int DPAD_DOWN = 1;
  public static final int DPAD_LEFT = 2;
  public static final int DPAD_RIGHT = 3;

  /**
   * Appends a single GameHub d-pad arrow ({@code side} in [0..3]) to {@code out} without resetting
   * it. Geometry matches {@link #buildDpadArrows}; broken out so callers can render per-arrow
   * effects (e.g. a radial glass vignette anchored at each arrow's centroid).
   */
  public static void buildDpadArrow(Path out, int side, float cx, float cy, float radius) {
    // GameHub d-pad: each arrow is a "house" / "square with a pointer" — a rectangle on the
    // outer side, then two short angled walls converging to a single point that aims AT the
    // center of the d-pad. The two outer corners get a small radius; the inner shoulder corners
    // and the tip stay sharp so the pointer reads clearly.
    float outer = radius * 0.95f;
    float shoulder = radius * 0.45f;
    float inner = radius * 0.18f;
    float halfOuter = radius * 0.275f;
    float cornerR = radius * 0.05f;

    switch (side) {
      case DPAD_UP:
        out.moveTo(cx - halfOuter + cornerR, cy - outer);
        out.lineTo(cx + halfOuter - cornerR, cy - outer);
        out.quadTo(cx + halfOuter, cy - outer, cx + halfOuter, cy - outer + cornerR);
        out.lineTo(cx + halfOuter, cy - shoulder);
        out.lineTo(cx, cy - inner);
        out.lineTo(cx - halfOuter, cy - shoulder);
        out.lineTo(cx - halfOuter, cy - outer + cornerR);
        out.quadTo(cx - halfOuter, cy - outer, cx - halfOuter + cornerR, cy - outer);
        out.close();
        break;
      case DPAD_DOWN:
        out.moveTo(cx + halfOuter - cornerR, cy + outer);
        out.lineTo(cx - halfOuter + cornerR, cy + outer);
        out.quadTo(cx - halfOuter, cy + outer, cx - halfOuter, cy + outer - cornerR);
        out.lineTo(cx - halfOuter, cy + shoulder);
        out.lineTo(cx, cy + inner);
        out.lineTo(cx + halfOuter, cy + shoulder);
        out.lineTo(cx + halfOuter, cy + outer - cornerR);
        out.quadTo(cx + halfOuter, cy + outer, cx + halfOuter - cornerR, cy + outer);
        out.close();
        break;
      case DPAD_LEFT:
        out.moveTo(cx - outer, cy + halfOuter - cornerR);
        out.lineTo(cx - outer, cy - halfOuter + cornerR);
        out.quadTo(cx - outer, cy - halfOuter, cx - outer + cornerR, cy - halfOuter);
        out.lineTo(cx - shoulder, cy - halfOuter);
        out.lineTo(cx - inner, cy);
        out.lineTo(cx - shoulder, cy + halfOuter);
        out.lineTo(cx - outer + cornerR, cy + halfOuter);
        out.quadTo(cx - outer, cy + halfOuter, cx - outer, cy + halfOuter - cornerR);
        out.close();
        break;
      case DPAD_RIGHT:
        out.moveTo(cx + outer, cy - halfOuter + cornerR);
        out.lineTo(cx + outer, cy + halfOuter - cornerR);
        out.quadTo(cx + outer, cy + halfOuter, cx + outer - cornerR, cy + halfOuter);
        out.lineTo(cx + shoulder, cy + halfOuter);
        out.lineTo(cx + inner, cy);
        out.lineTo(cx + shoulder, cy - halfOuter);
        out.lineTo(cx + outer - cornerR, cy - halfOuter);
        out.quadTo(cx + outer, cy - halfOuter, cx + outer, cy - halfOuter + cornerR);
        out.close();
        break;
    }
  }

  /**
   * Writes the approximate centroid of a single d-pad arrow into {@code outXY} (length ≥ 2).
   * Used as the anchor point for the per-arrow radial glass vignette so each arrow lights up
   * from its own middle outward rather than from the d-pad cluster center.
   */
  public static void dpadArrowCenter(int side, float cx, float cy, float radius, float[] outXY) {
    // Midpoint between the outer flat edge (0.95r) and the pointer tip (0.18r) along the
    // arrow's axis — close enough to the visual center for a smooth vignette.
    float along = radius * 0.565f;
    switch (side) {
      case DPAD_UP:    outXY[0] = cx;          outXY[1] = cy - along; break;
      case DPAD_DOWN:  outXY[0] = cx;          outXY[1] = cy + along; break;
      case DPAD_LEFT:  outXY[0] = cx - along;  outXY[1] = cy;         break;
      case DPAD_RIGHT: outXY[0] = cx + along;  outXY[1] = cy;         break;
      default:         outXY[0] = cx;          outXY[1] = cy;         break;
    }
  }

  private GameHubLayout() {}
}
