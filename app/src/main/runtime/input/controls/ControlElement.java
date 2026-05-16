package com.winlator.cmod.runtime.input.controls;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import androidx.core.graphics.ColorUtils;
import com.winlator.cmod.runtime.display.winhandler.MouseEventFlags;
import com.winlator.cmod.runtime.display.xserver.XServer;
import com.winlator.cmod.runtime.input.ui.InputControlsView;
import com.winlator.cmod.runtime.input.ui.TouchpadView;
import com.winlator.cmod.shared.math.Mathf;
import com.winlator.cmod.shared.ui.CubicBezierInterpolator;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ControlElement {
  public static final float STICK_DEAD_ZONE = 0.15f;
  public static final float DPAD_DEAD_ZONE = 0.3f;
  public static final float STICK_SENSITIVITY = 3.0f;
  public static final float STICK_CROSS_ZONE = 0.3f;
  public static final float TRACKPAD_MIN_SPEED = 0.8f;
  public static final float TRACKPAD_MAX_SPEED = 20.0f;
  public static final byte TRACKPAD_ACCELERATION_THRESHOLD = 4;
  public static final short BUTTON_MIN_TIME_TO_KEEP_PRESSED = 300;

  public enum Type {
    BUTTON,
    D_PAD,
    RANGE_BUTTON,
    STICK,
    TRACKPAD,
    RADIAL_MENU;

    public static String[] names() {
      Type[] types = values();
      String[] names = new String[types.length];
      for (int i = 0; i < types.length; i++) names[i] = types[i].name().replace("_", "-");
      return names;
    }
  }

  public enum Shape {
    CIRCLE,
    RECT,
    ROUND_RECT,
    SQUARE;

    public static String[] names() {
      Shape[] shapes = values();
      String[] names = new String[shapes.length];
      for (int i = 0; i < shapes.length; i++) names[i] = shapes[i].name().replace("_", " ");
      return names;
    }
  }

  public enum Range {
    FROM_A_TO_Z(26),
    FROM_0_TO_9(10),
    FROM_F1_TO_F12(12),
    FROM_NP0_TO_NP9(10);
    public final byte max;

    Range(int max) {
      this.max = (byte) max;
    }

    public static String[] names() {
      Range[] ranges = values();
      String[] names = new String[ranges.length];
      for (int i = 0; i < ranges.length; i++) names[i] = ranges[i].name().replace("_", " ");
      return names;
    }
  }

  private final InputControlsView inputControlsView;
  private Type type = Type.BUTTON;
  private Shape shape = Shape.CIRCLE;
  private Binding[] bindings = {Binding.NONE, Binding.NONE, Binding.NONE, Binding.NONE};
  private float scale = 1.0f;
  private float opacity = 1.0f;
  private short x;
  private short y;
  private boolean selected = false;
  private boolean toggleSwitch = false;
  private boolean radialMenuExpanded = false;
  private int activeRadialBindingIndex = -1;
  private boolean isRadialBindingCurrentlyHeld = false;
  private boolean wasExpandedOnDown = false;
  private int currentPointerId = -1;
  private final Rect boundingBox = new Rect();
  /** Scratch rect for the GameHub layout override — kept separate so the cached saved-layout
   * {@link #boundingBox} stays valid when the user toggles styles back and forth. */
  private final Rect overrideBoundingBox = new Rect();
  private final Path path = new Path();
  private Path[] paths;
  private boolean[] states = new boolean[4];
  private boolean boundingBoxNeedsUpdate = true;
  private String text = "";
  private byte iconId;
  private Range range;
  private byte orientation;
  private PointF currentPosition;
  private int customColor = -1;
  private RangeScroller scroller;
  private CubicBezierInterpolator interpolator;
  private Object touchTime;

  public ControlElement(InputControlsView inputControlsView) {
    this.inputControlsView = inputControlsView;
  }

  private void reset() {
    scroller = null;

    if (type == Type.STICK) {
      bindings[0] = Binding.NONE;
      bindings[1] = Binding.NONE;
      bindings[2] = Binding.NONE;
      bindings[3] = Binding.NONE;
    } else if (type == Type.D_PAD) {
      bindings[0] = Binding.NONE;
      bindings[1] = Binding.NONE;
      bindings[2] = Binding.NONE;
      bindings[3] = Binding.NONE;
    } else if (type == Type.TRACKPAD) {
      bindings[0] = Binding.NONE;
      bindings[1] = Binding.NONE;
      bindings[2] = Binding.NONE;
      bindings[3] = Binding.NONE;
    } else if (type == Type.RANGE_BUTTON) {
      scroller = new RangeScroller(inputControlsView, this);
    } else if (type == Type.RADIAL_MENU) {
      if (bindings.length < 3) setBindingCount(3);
    }

    text = "";
    iconId = 0;
    range = null;
    boundingBoxNeedsUpdate = true;
    radialMenuExpanded = false;
    paths = null;
  }

  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type;
    reset();
  }

  public int getBindingCount() {
    return bindings.length;
  }

  public void setBindingCount(int bindingCount) {
    int oldLength = bindings.length;
    bindings = Arrays.copyOf(bindings, bindingCount);
    if (bindingCount > oldLength) {
      Arrays.fill(bindings, oldLength, bindingCount, Binding.NONE);
    }
    states = new boolean[bindingCount];
    boundingBoxNeedsUpdate = true;
    paths = null;
  }

  public Shape getShape() {
    return shape;
  }

  public void setShape(Shape shape) {
    this.shape = shape;
    boundingBoxNeedsUpdate = true;
  }

  public Range getRange() {
    return range != null ? range : Range.FROM_A_TO_Z;
  }

  public void setRange(Range range) {
    this.range = range;
  }

  public byte getOrientation() {
    return orientation;
  }

  public void setOrientation(byte orientation) {
    this.orientation = orientation;
    boundingBoxNeedsUpdate = true;
  }

  public boolean isToggleSwitch() {
    return toggleSwitch;
  }

  public void setToggleSwitch(boolean toggleSwitch) {
    this.toggleSwitch = toggleSwitch;
  }

  public float getOpacity() {
    return opacity;
  }

  public void setOpacity(float opacity) {
    this.opacity = opacity;
  }

  public boolean isRadialMenuExpanded() {
    return radialMenuExpanded;
  }

  public void setRadialMenuExpanded(boolean radialMenuExpanded) {
    this.radialMenuExpanded = radialMenuExpanded;
    paths = null;
  }

  public int getCustomColor() {
    return customColor;
  }

  public void setCustomColor(int customColor) {
    this.customColor = customColor;
    this.boundingBoxNeedsUpdate = true;
  }

  public Binding getBindingAt(int index) {
    return index < bindings.length ? bindings[index] : Binding.NONE;
  }

  public void setBindingAt(int index, Binding binding) {
    if (index >= bindings.length) {
      int oldLength = bindings.length;
      bindings = Arrays.copyOf(bindings, index + 1);
      Arrays.fill(bindings, oldLength, bindings.length, Binding.NONE);
      states = new boolean[bindings.length];
      boundingBoxNeedsUpdate = true;
    }
    bindings[index] = binding;
    paths = null;
  }

  public void setBinding(Binding binding) {
    Arrays.fill(bindings, binding);
    paths = null;
  }

  public float getScale() {
    return scale;
  }

  public void setScale(float scale) {
    this.scale = scale;
    boundingBoxNeedsUpdate = true;
    paths = null;
  }

  public short getX() {
    return x;
  }

  public void setX(int x) {
    this.x = (short) x;
    boundingBoxNeedsUpdate = true;
    paths = null;
  }

  public short getY() {
    return y;
  }

  public void setY(int y) {
    this.y = (short) y;
    boundingBoxNeedsUpdate = true;
    paths = null;
  }

  public boolean isSelected() {
    return selected;
  }

  public void setSelected(boolean selected) {
    this.selected = selected;
    if (type == Type.RADIAL_MENU) {
      this.radialMenuExpanded = selected;
      this.paths = null;
    }
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text != null ? text : "";
  }

  public byte getIconId() {
    return iconId;
  }

  public void setIconId(int iconId) {
    this.iconId = (byte) iconId;
  }

  public Rect getBoundingBox() {
    if (boundingBoxNeedsUpdate) computeBoundingBox();
    // When the GameHub visual style is active (and we're not editing), known gamepad roles get
    // relocated and resized to the GameHub reference layout. Override is written into a scratch
    // rect so the cached saved-layout box stays valid for ORIGINAL renderings.
    if (inputControlsView.getVisualStyle() == VisualStyle.GAMEHUB && !inputControlsView.isEditMode()) {
      GameHubLayout.Override ov = currentGameHubOverride();
      if (ov != null) {
        int snappingSize = inputControlsView.getSnappingSize();
        if (snappingSize > 0) {
          int cx = (int) (ov.normX * inputControlsView.getMaxWidth());
          int cy = (int) (ov.normY * inputControlsView.getMaxHeight());
          int hw = (int) (ov.halfWidthSnap * snappingSize * ov.scale);
          int hh = (int) (ov.halfHeightSnap * snappingSize * ov.scale);
          overrideBoundingBox.set(cx - hw, cy - hh, cx + hw, cy + hh);
          return overrideBoundingBox;
        }
      }
    }
    return boundingBox;
  }

  /** Returns the current GameHub override for this element (cached lookup), or null. */
  private GameHubLayout.Override currentGameHubOverride() {
    GameHubLayout.Role role = GameHubLayout.roleFor(this);
    return role != null ? GameHubLayout.overrideFor(role) : null;
  }

  private Rect computeBoundingBox() {
    int snappingSize = inputControlsView.getSnappingSize();
    int halfWidth = 0;
    int halfHeight = 0;

    switch (type) {
      case BUTTON:
        switch (shape) {
          case RECT:
          case ROUND_RECT:
            halfWidth = snappingSize * 4;
            halfHeight = snappingSize * 2;
            break;
          case SQUARE:
            halfWidth = (int) (snappingSize * 2.5f);
            halfHeight = (int) (snappingSize * 2.5f);
            break;
          case CIRCLE:
            halfWidth = snappingSize * 3;
            halfHeight = snappingSize * 3;
            break;
        }
        break;
      case D_PAD:
        {
          halfWidth = snappingSize * 7;
          halfHeight = snappingSize * 7;
          break;
        }
      case TRACKPAD:
      case STICK:
        {
          halfWidth = snappingSize * 6;
          halfHeight = snappingSize * 6;
          break;
        }
      case RANGE_BUTTON:
        {
          halfWidth = snappingSize * ((bindings.length * 4) / 2);
          halfHeight = snappingSize * 2;

          if (orientation == 1) {
            int tmp = halfWidth;
            halfWidth = halfHeight;
            halfHeight = tmp;
          }
          break;
        }
      case RADIAL_MENU:
        {
          halfWidth = snappingSize * 3;
          halfHeight = snappingSize * 3;
          break;
        }
    }
halfWidth *= scale;
halfHeight *= scale;
boundingBox.set(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight);
boundingBoxNeedsUpdate = false;
return boundingBox;
}

  private String getDisplayText() {
    // Per-element text always wins (user explicit override).
    if (text != null && !text.isEmpty()) {
      return text;
    }
    Binding binding = getBindingAt(0);
    // LabelTheme override (Xbox/PS glyphs and shoulder labels) is applied only when the user
    // hasn't typed a custom label themselves.
    LabelTheme theme = inputControlsView.getLabelTheme();
    String themed = theme != null ? theme.labelFor(binding) : null;
    if (themed != null) return themed;
    String text = binding.toString().replace("NUMPAD ", "NP").replace("BUTTON ", "");
    if (text.length() > 7) {
      String[] parts = text.split(" ");
      StringBuilder sb = new StringBuilder();
      for (String part : parts) sb.append(part.charAt(0));
      return (binding.isMouse() ? "M" : "") + sb;
    } else return text;
  }

  /**
   * Returns the effective accent color for this element after applying (in order): per-element
   * customColor (highest priority — user explicit), LabelTheme color for the primary binding, or
   * {@code -1} if no override applies (caller should fall back to the overlay primary color).
   */
  private int resolveAccentColor() {
    if (customColor != -1) return customColor;
    LabelTheme theme = inputControlsView.getLabelTheme();
    if (theme != null) {
      int c = theme.colorFor(getBindingAt(0));
      if (c != 0) return c;
    }
    return -1;
  }

  private String getBindingShortText(int index) {
    Binding binding = getBindingAt(index);
    String text = binding.toString().replace("NUMPAD ", "NP").replace("BUTTON ", "").replace("KEY_", "").replace("GAMEPAD_", "");
    if (text.length() > 6) {
      String[] parts = text.split("_");
      StringBuilder sb = new StringBuilder();
      for (String part : parts) if (!part.isEmpty()) sb.append(part.charAt(0));
      return (binding.isMouse() ? "M" : "") + sb.toString();
    }
    return text.replace("_", " ");
  }

  private static float getTextSizeForWidth(Paint paint, String text, float desiredWidth) {
    final byte testTextSize = 48;
    paint.setTextSize(testTextSize);
    return testTextSize * desiredWidth / paint.measureText(text);
  }

  private static String getRangeTextForIndex(Range range, int index) {
    String text = "";
    switch (range) {
      case FROM_A_TO_Z:
        text = String.valueOf((char) (65 + index));
        break;
      case FROM_0_TO_9:
        text = String.valueOf((index + 1) % 10);
        break;
      case FROM_F1_TO_F12:
        text = "F" + (index + 1);
        break;
      case FROM_NP0_TO_NP9:
        text = "NP" + ((index + 1) % 10);
        break;
    }
    return text;
  }

  private boolean isEngaged() {
    return currentPointerId != -1 || (toggleSwitch && selected);
  }

  public void draw(Canvas canvas) {
    if (inputControlsView.getVisualStyle() == VisualStyle.GAMEHUB) {
      drawGameHub(canvas);
      return;
    }
    int snappingSize = inputControlsView.getSnappingSize();
    Paint paint = inputControlsView.getPaint();
    float effectiveOpacity = inputControlsView.isEditMode() ? Math.max(0.15f, opacity) : opacity;
    int accent = resolveAccentColor();
    int primaryColor = accent != -1
        ? ColorUtils.setAlphaComponent(accent, (int) (Math.min(1.0f,
            inputControlsView.getOverlayOpacity() * 2.0f) * 255))
        : inputControlsView.getPrimaryColor();
    int alpha = (int) (Color.alpha(primaryColor) * effectiveOpacity);
    primaryColor = ColorUtils.setAlphaComponent(primaryColor, alpha);
    int fillColor = ColorUtils.setAlphaComponent(primaryColor, (int) (70 * effectiveOpacity));

    int highlightAlpha = (int) (255 * inputControlsView.getOverlayOpacity());
    int secondaryColor = ColorUtils.setAlphaComponent(inputControlsView.getSecondaryColor(), highlightAlpha);

    paint.setColor(
        (selected && accent == -1) ? secondaryColor : primaryColor);
    paint.setStyle(Paint.Style.STROKE);
    float strokeWidth = snappingSize * 0.25f;
    paint.setStrokeWidth(strokeWidth);
    Rect boundingBox = getBoundingBox();

    switch (type) {
      case BUTTON:
        {
          float cx = boundingBox.centerX();
          float cy = boundingBox.centerY();

          if (isEngaged()) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fillColor);
            switch (shape) {
              case CIRCLE:
                canvas.drawCircle(cx, cy, boundingBox.width() * 0.5f, paint);
                break;
              case RECT:
                canvas.drawRect(boundingBox, paint);
                break;
              case ROUND_RECT:
                {
                  float r = boundingBox.height() * 0.5f;
                  canvas.drawRoundRect(
                      boundingBox.left,
                      boundingBox.top,
                      boundingBox.right,
                      boundingBox.bottom,
                      r,
                      r,
                      paint);
                  break;
                }
              case SQUARE:
                {
                  float r = snappingSize * 0.75f * scale;
                  canvas.drawRoundRect(
                      boundingBox.left,
                      boundingBox.top,
                      boundingBox.right,
                      boundingBox.bottom,
                      r,
                      r,
                      paint);
                  break;
                }
            }
          }

          paint.setStyle(Paint.Style.STROKE);
          paint.setColor(
              (selected && accent == -1)
                  ? secondaryColor
                  : primaryColor);
          paint.setStrokeWidth(strokeWidth);

          switch (shape) {
            case CIRCLE:
              canvas.drawCircle(cx, cy, boundingBox.width() * 0.5f, paint);
              break;
            case RECT:
              canvas.drawRect(boundingBox, paint);
              break;
            case ROUND_RECT:
              {
                float radius = boundingBox.height() * 0.5f;
                canvas.drawRoundRect(
                    boundingBox.left,
                    boundingBox.top,
                    boundingBox.right,
                    boundingBox.bottom,
                    radius,
                    radius,
                    paint);
                break;
              }
            case SQUARE:
              {
                float radius = snappingSize * 0.75f * scale;
                canvas.drawRoundRect(
                    boundingBox.left,
                    boundingBox.top,
                    boundingBox.right,
                    boundingBox.bottom,
                    radius,
                    radius,
                    paint);
                break;
              }
          }

          if (iconId > 0) {
            drawIcon(canvas, cx, cy, boundingBox.width(), boundingBox.height(), iconId);
          } else {
            String text = getDisplayText();
            paint.setTextSize(
                Math.min(
                    getTextSizeForWidth(paint, text, boundingBox.width() - strokeWidth * 2),
                    snappingSize * 2 * scale));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(primaryColor);
            canvas.drawText(text, x, (y - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
          }
          break;
        }
      case RADIAL_MENU:
        {
          float cx = boundingBox.centerX();
          float cy = boundingBox.centerY();
          float radius = boundingBox.width() * 0.5f;

          if (radialMenuExpanded && bindings.length > 0 && radius > 0) {
            float innerRadius = radius + snappingSize * 0.5f;
            float outerRadius = boundingBox.width() + (snappingSize * scale);
            float angleStep = 360.0f / bindings.length;

            if (paths == null || paths.length != bindings.length) {
              paths = new Path[bindings.length];
              RectF outerRect = new RectF(cx - outerRadius, cy - outerRadius, cx + outerRadius, cy + outerRadius);
              RectF innerRect = new RectF(cx - innerRadius, cy - innerRadius, cx + innerRadius, cy + innerRadius);

              for (int i = 0; i < bindings.length; i++) {
                float startAngle = -90.0f + i * angleStep;
                paths[i] = new Path();
                paths[i].arcTo(outerRect, startAngle, angleStep, true);
                paths[i].arcTo(innerRect, startAngle + angleStep, -angleStep, false);
                paths[i].close();
              }
            }

            if (paths != null && paths.length == bindings.length) {
              for (int i = 0; i < bindings.length; i++) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(i == activeRadialBindingIndex ? secondaryColor : fillColor);
                canvas.drawPath(paths[i], paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(primaryColor);
                canvas.drawPath(paths[i], paint);

                float middleAngle = (float) Math.toRadians(-90.0f + i * angleStep + angleStep * 0.5f);
                float labelRadius = (innerRadius + outerRadius) * 0.5f;
                float labelX = (float) (cx + Math.cos(middleAngle) * labelRadius);
                float labelY = (float) (cy + Math.sin(middleAngle) * labelRadius);

                String label = getBindingShortText(i);
                paint.setStyle(Paint.Style.FILL);
                paint.setTextSize(snappingSize * 1.2f * scale);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(label, labelX, labelY - ((paint.descent() + paint.ascent()) * 0.5f), paint);
              }
            }
          }

          paint.setStyle(Paint.Style.STROKE);
          paint.setColor(
              (selected && accent == -1)
                  ? secondaryColor
                  : primaryColor);
          canvas.drawCircle(cx, cy, radius, paint);

          if (iconId > 0) {
            drawIcon(canvas, cx, cy, boundingBox.width(), boundingBox.height(), iconId);
          } else {
            drawIcon(canvas, cx, cy, boundingBox.width(), boundingBox.height(), 34);
          }
          break;
        }
      case D_PAD:
        {
          float cx = boundingBox.centerX();
          float cy = boundingBox.centerY();
          float offsetX = snappingSize * 2 * scale;
          float offsetY = snappingSize * 3 * scale;
          float start = snappingSize * scale;
          path.reset();

          path.moveTo(cx, cy - start);
          path.lineTo(cx - offsetX, cy - offsetY);
          path.lineTo(cx - offsetX, boundingBox.top);
          path.lineTo(cx + offsetX, boundingBox.top);
          path.lineTo(cx + offsetX, cy - offsetY);
          path.close();

          path.moveTo(cx - start, cy);
          path.lineTo(cx - offsetY, cy - offsetX);
          path.lineTo(boundingBox.left, cy - offsetX);
          path.lineTo(boundingBox.left, cy + offsetX);
          path.lineTo(cx - offsetY, cy + offsetX);
          path.close();

          path.moveTo(cx, cy + start);
          path.lineTo(cx - offsetX, cy + offsetY);
          path.lineTo(cx - offsetX, boundingBox.bottom);
          path.lineTo(cx + offsetX, boundingBox.bottom);
          path.lineTo(cx + offsetX, cy + offsetY);
          path.close();

          path.moveTo(cx + start, cy);
          path.lineTo(cx + offsetY, cy - offsetX);
          path.lineTo(boundingBox.right, cy - offsetX);
          path.lineTo(boundingBox.right, cy + offsetX);
          path.lineTo(cx + offsetY, cy + offsetX);
          path.close();

          canvas.drawPath(path, paint);
          break;
        }
      case RANGE_BUTTON:
        {
          Range range = getRange();
          int oldColor = paint.getColor();
          float radius = snappingSize * 0.75f * scale;
          float elementSize = scroller.getElementSize();
          float minTextSize = snappingSize * 2 * scale;
          float scrollOffset = scroller.getScrollOffset();
          byte[] rangeIndex = scroller.getRangeIndex();
          path.reset();

          if (orientation == 0) {
            float lineTop = boundingBox.top + strokeWidth * 0.5f;
            float lineBottom = boundingBox.bottom - strokeWidth * 0.5f;
            float startX = boundingBox.left;
            canvas.drawRoundRect(
                startX,
                boundingBox.top,
                boundingBox.right,
                boundingBox.bottom,
                radius,
                radius,
                paint);

            canvas.save();
            path.addRoundRect(
                startX,
                boundingBox.top,
                boundingBox.right,
                boundingBox.bottom,
                radius,
                radius,
                Path.Direction.CW);
            canvas.clipPath(path);
            startX -= scrollOffset % elementSize;

            for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
              int index = i % range.max;
              paint.setStyle(Paint.Style.STROKE);
              paint.setColor(oldColor);

              if (startX > boundingBox.left && startX < boundingBox.right)
                canvas.drawLine(startX, lineTop, startX, lineBottom, paint);
              String text = getRangeTextForIndex(range, index);

              if (startX < boundingBox.right && startX + elementSize > boundingBox.left) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(primaryColor);
                paint.setTextSize(
                    Math.min(
                        getTextSizeForWidth(paint, text, elementSize - strokeWidth * 2),
                        minTextSize));
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(
                    text,
                    startX + elementSize * 0.5f,
                    (y - ((paint.descent() + paint.ascent()) * 0.5f)),
                    paint);
              }
              startX += elementSize;
            }

            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(oldColor);
            canvas.restore();
          } else {
            float lineLeft = boundingBox.left + strokeWidth * 0.5f;
            float lineRight = boundingBox.right - strokeWidth * 0.5f;
            float startY = boundingBox.top;
            canvas.drawRoundRect(
                boundingBox.left,
                startY,
                boundingBox.right,
                boundingBox.bottom,
                radius,
                radius,
                paint);

            canvas.save();
            path.addRoundRect(
                boundingBox.left,
                startY,
                boundingBox.right,
                boundingBox.bottom,
                radius,
                radius,
                Path.Direction.CW);
            canvas.clipPath(path);
            startY -= scrollOffset % elementSize;

            for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
              paint.setStyle(Paint.Style.STROKE);
              paint.setColor(oldColor);

              if (startY > boundingBox.top && startY < boundingBox.bottom)
                canvas.drawLine(lineLeft, startY, lineRight, startY, paint);
              String text = getRangeTextForIndex(range, i);

              if (startY < boundingBox.bottom && startY + elementSize > boundingBox.top) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(primaryColor);
                paint.setTextSize(
                    Math.min(
                        getTextSizeForWidth(paint, text, boundingBox.width() - strokeWidth * 2),
                        minTextSize));
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(
                    text,
                    x,
                    startY + elementSize * 0.5f - ((paint.descent() + paint.ascent()) * 0.5f),
                    paint);
              }
              startY += elementSize;
            }

            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(oldColor);
            canvas.restore();
          }
          break;
        }
      case STICK:
        {
          int cx = boundingBox.centerX(); // Fixed outer circle center
          int cy = boundingBox.centerY(); // Fixed outer circle center
          int oldColor = paint.getColor();

          // Draw the outer circle (base of the stick)
          canvas.drawCircle(cx, cy, boundingBox.height() * 0.5f, paint);

          // Draw the inner thumbstick (current position based on gyroscope movement)
          float thumbstickX = getCurrentPosition().x;
          float thumbstickY = getCurrentPosition().y;

          short thumbRadius = (short) (snappingSize * 3.5f * scale);
          int engagedAlpha = isEngaged() ? 120 : 50;
          paint.setStyle(Paint.Style.FILL);
          paint.setColor(ColorUtils.setAlphaComponent(primaryColor, engagedAlpha));
          canvas.drawCircle(thumbstickX, thumbstickY, thumbRadius, paint); // Draw thumbstick

          // Draw the thumbstick border
          paint.setStyle(Paint.Style.STROKE);
          paint.setColor(oldColor);
          canvas.drawCircle(thumbstickX, thumbstickY, thumbRadius + strokeWidth * 0.5f, paint);
          break;
        }

      case TRACKPAD:
        {
          float radius = boundingBox.height() * 0.15f;
          canvas.drawRoundRect(
              boundingBox.left,
              boundingBox.top,
              boundingBox.right,
              boundingBox.bottom,
              radius,
              radius,
              paint);
          float offset = strokeWidth * 2.5f;
          float innerStrokeWidth = strokeWidth * 2;
          float innerHeight = boundingBox.height() - offset * 2;
          radius =
              (innerHeight / boundingBox.height()) * radius
                  - (innerStrokeWidth * 0.5f + strokeWidth * 0.5f);
          paint.setStrokeWidth(innerStrokeWidth);
          canvas.drawRoundRect(
              boundingBox.left + offset,
              boundingBox.top + offset,
              boundingBox.right - offset,
              boundingBox.bottom - offset,
              radius,
              radius,
              paint);
          break;
        }
    }
  }

  /**
   * GameHub visual style — dark translucent glass body, light white rim, brighter rim & inner glow
   * when pressed, soft outer shadow. Used when the user picks the "GameHub" style.
   *
   * <p>Geometry (positions, bounding boxes, sticks, dpad arms, radial menu paths) is reused from
   * the original code; only the paint properties differ.
   */
  private void drawGameHub(Canvas canvas) {
    int snappingSize = inputControlsView.getSnappingSize();
    Paint paint = inputControlsView.getPaint();
    float effectiveOpacity = inputControlsView.isEditMode() ? Math.max(0.15f, opacity) : opacity;
    float overlayOpacity = inputControlsView.getOverlayOpacity();
    boolean engaged = isEngaged();
    Rect boundingBox = getBoundingBox();
    GameHubLayout.Override layoutOverride = inputControlsView.isEditMode() ? null : currentGameHubOverride();

    // Resolve accent (user customColor → theme color → none).
    int accent = resolveAccentColor();
    boolean hasAccent = accent != -1;

    // GameHub palette — uses the source asset's alpha values directly so the dark-glass identity
    // doesn't disappear at the default overlay opacity. Per-element opacity still applies; the
    // overlayOpacity slider modulates with a gentle floor so the controls never look fully washed.
    float gameHubDim = Math.min(1.0f, 0.5f + overlayOpacity * 0.7f);
    int fillAlpha = (int) (90 * gameHubDim * effectiveOpacity); // dark glass body
    int strokeAlpha = (int) (150 * gameHubDim * effectiveOpacity); // light rim
    int pressedFillAlpha = (int) (60 * gameHubDim * effectiveOpacity); // brighter inner glow when pressed
    int pressedStrokeAlpha = (int) (220 * gameHubDim * effectiveOpacity); // brighter rim when pressed
    int textAlpha = (int) (255 * gameHubDim * effectiveOpacity);
    // Glass vignette — translucent black at the inner edge of each shape that fades to fully
    // transparent at the center, producing a soft inset-shadow / glass look on top of the base fill.
    int glassEdgeAlpha = (int) (75 * gameHubDim * effectiveOpacity);

    int fillColor = Color.argb(fillAlpha, 0, 0, 0);
    int strokeColor = hasAccent
        ? ColorUtils.setAlphaComponent(accent, Math.max(strokeAlpha, 110))
        : Color.argb(strokeAlpha, 255, 255, 255);
    int pressedFillBase = hasAccent ? accent : Color.WHITE;
    int pressedFillColor = ColorUtils.setAlphaComponent(pressedFillBase, pressedFillAlpha);
    int pressedStrokeColor = hasAccent
        ? ColorUtils.setAlphaComponent(accent, Math.max(pressedStrokeAlpha, 160))
        : Color.argb(pressedStrokeAlpha, 255, 255, 255);
    int textColor = hasAccent
        ? ColorUtils.setAlphaComponent(accent, textAlpha)
        : Color.argb(textAlpha, 255, 255, 255);

    // Edit-mode selection highlight reuses the existing secondary color so editing UX stays the
    // same regardless of style.
    if (selected && !hasAccent) {
      int highlightAlpha = (int) (255 * overlayOpacity);
      strokeColor = ColorUtils.setAlphaComponent(inputControlsView.getSecondaryColor(), highlightAlpha);
    }

    float strokeWidth = Math.max(2f, snappingSize * 0.18f);
    paint.setStrokeWidth(strokeWidth);
    paint.setStrokeJoin(Paint.Join.ROUND);
    paint.setStrokeCap(Paint.Cap.ROUND);

    switch (type) {
      case BUTTON: {
        float cx = boundingBox.centerX();
        float cy = boundingBox.centerY();
        boolean isTrigger = layoutOverride != null
            && (layoutOverride.shape == GameHubLayout.RenderShape.TRIGGER_LT
                || layoutOverride.shape == GameHubLayout.RenderShape.TRIGGER_LB
                || layoutOverride.shape == GameHubLayout.RenderShape.TRIGGER_RT
                || layoutOverride.shape == GameHubLayout.RenderShape.TRIGGER_RB);

        if (isTrigger) {
          GameHubLayout.buildTriggerPath(
              path, layoutOverride.shape,
              boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom);
          paint.setStyle(Paint.Style.FILL);
          paint.setColor(fillColor);
          canvas.drawPath(path, paint);
          if (engaged) {
            paint.setColor(pressedFillColor);
            canvas.drawPath(path, paint);
          }
          drawGameHubGlassOnPath(
              canvas, paint, path, cx, cy,
              Math.max(boundingBox.width(), boundingBox.height()) * 0.5f, glassEdgeAlpha);
          paint.setStyle(Paint.Style.STROKE);
          paint.setColor(engaged ? pressedStrokeColor : strokeColor);
          canvas.drawPath(path, paint);
        } else {
          drawGameHubShape(canvas, paint, boundingBox, fillColor, true);
          if (engaged) drawGameHubShape(canvas, paint, boundingBox, pressedFillColor, true);
          drawGameHubGlassShape(canvas, paint, boundingBox, glassEdgeAlpha);
          paint.setStyle(Paint.Style.STROKE);
          paint.setColor(engaged ? pressedStrokeColor : strokeColor);
          drawGameHubShape(canvas, paint, boundingBox, 0, false);
        }

        if (iconId > 0) {
          drawIcon(canvas, cx, cy, boundingBox.width(), boundingBox.height(), iconId);
        } else {
          String label = getDisplayText();
          paint.setStyle(Paint.Style.FILL);
          paint.setColor(textColor);
          paint.setTextSize(
              Math.min(
                  getTextSizeForWidth(paint, label, boundingBox.width() - strokeWidth * 2),
                  snappingSize * 2 * scale));
          paint.setTextAlign(Paint.Align.CENTER);
          paint.setFakeBoldText(true);
          canvas.drawText(label, cx, (cy - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
          paint.setFakeBoldText(false);
        }
        break;
      }
      case STICK: {
        int cx = boundingBox.centerX();
        int cy = boundingBox.centerY();
        float ringRadius = boundingBox.height() * 0.5f;

        // Outer ring — solid translucent dark fill matching the button fill alpha so the
        // joystick shadowing reads with the same weight as the rest of the controls.
        int ringFillAlpha = fillAlpha;
        int ringFill = Color.argb(ringFillAlpha, 0, 0, 0);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ringFill);
        canvas.drawCircle(cx, cy, ringRadius, paint);

        // Glass vignette across the outer ring — the inner thumb will cover the brightest center
        // anyway, so this reads as a darker shadow just inside the ring's rim.
        if (glassEdgeAlpha > 0) {
          paint.setShader(new RadialGradient(
              cx, cy, ringRadius,
              Color.argb(0, 0, 0, 0), Color.argb(glassEdgeAlpha, 0, 0, 0),
              Shader.TileMode.CLAMP));
          paint.setStyle(Paint.Style.FILL);
          canvas.drawCircle(cx, cy, ringRadius, paint);
          paint.setShader(null);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(engaged ? pressedStrokeColor : strokeColor);
        canvas.drawCircle(cx, cy, ringRadius - strokeWidth * 0.5f, paint);

        // Inner thumb — substantial translucent white disc with a thin rim so it visually pops
        // against the dark ring fill. When a GameHub layout override is active and the stick
        // isn't being touched, snap the thumb to the overridden center; getCurrentPosition() is
        // otherwise seeded from the saved profile coordinates and would draw off-center.
        float thumbX;
        float thumbY;
        if (engaged) {
          thumbX = getCurrentPosition().x;
          thumbY = getCurrentPosition().y;
        } else if (layoutOverride != null) {
          thumbX = cx;
          thumbY = cy;
        } else {
          thumbX = getCurrentPosition().x;
          thumbY = getCurrentPosition().y;
        }
        // Thumb radius at ~48% of outer ring — the user reported the previous 45% looked too
        // small. Matches GameHub's reference of roughly 0.43-0.50.
        float thumbRadius =
            layoutOverride != null ? ringRadius * 0.48f : snappingSize * 3.5f * scale;
        int thumbFillAlpha = (int) ((engaged ? 100 : 77) * gameHubDim * effectiveOpacity);
        int thumbFill = hasAccent
            ? ColorUtils.setAlphaComponent(accent, thumbFillAlpha)
            : Color.argb(thumbFillAlpha, 255, 255, 255);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(thumbFill);
        canvas.drawCircle(thumbX, thumbY, thumbRadius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(engaged ? pressedStrokeColor : strokeColor);
        canvas.drawCircle(thumbX, thumbY, thumbRadius - strokeWidth * 0.5f, paint);
        break;
      }
      case D_PAD: {
        float cx = boundingBox.centerX();
        float cy = boundingBox.centerY();

        // GameHub override: draw 4 detached pentagonal arrows at the cardinal edges of the bbox.
        if (layoutOverride != null && layoutOverride.shape == GameHubLayout.RenderShape.DPAD_CROSS) {
          float radius = Math.min(boundingBox.width(), boundingBox.height()) * 0.5f;
          // Per-arrow fill + glass vignette so each arrow has its own inner-shadow centroid;
          // a single radial gradient across the whole cluster would put the bright spot in the
          // gap between arrows rather than inside each one.
          float[] arrowCenter = new float[2];
          float arrowGradR = radius * 0.5f;
          for (int side = 0; side < 4; side++) {
            path.reset();
            GameHubLayout.buildDpadArrow(path, side, cx, cy, radius);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fillColor);
            canvas.drawPath(path, paint);
            if (engaged) {
              paint.setColor(pressedFillColor);
              canvas.drawPath(path, paint);
            }
            if (glassEdgeAlpha > 0) {
              GameHubLayout.dpadArrowCenter(side, cx, cy, radius, arrowCenter);
              drawGameHubGlassOnPath(
                  canvas, paint, path, arrowCenter[0], arrowCenter[1], arrowGradR, glassEdgeAlpha);
            }
          }
          // Combined stroke pass for all four arrows.
          GameHubLayout.buildDpadArrows(path, cx, cy, radius);
          paint.setStyle(Paint.Style.STROKE);
          paint.setColor(engaged ? pressedStrokeColor : strokeColor);
          canvas.drawPath(path, paint);
          break;
        } else {
          // Same dpad geometry as ORIGINAL (used when no override applies — e.g. mixed profiles).
          float offsetX = snappingSize * 2 * scale;
          float offsetY = snappingSize * 3 * scale;
          float start = snappingSize * scale;
          path.reset();
          path.moveTo(cx, cy - start);
          path.lineTo(cx - offsetX, cy - offsetY);
          path.lineTo(cx - offsetX, boundingBox.top);
          path.lineTo(cx + offsetX, boundingBox.top);
          path.lineTo(cx + offsetX, cy - offsetY);
          path.close();
          path.moveTo(cx - start, cy);
          path.lineTo(cx - offsetY, cy - offsetX);
          path.lineTo(boundingBox.left, cy - offsetX);
          path.lineTo(boundingBox.left, cy + offsetX);
          path.lineTo(cx - offsetY, cy + offsetX);
          path.close();
          path.moveTo(cx, cy + start);
          path.lineTo(cx - offsetX, cy + offsetY);
          path.lineTo(cx - offsetX, boundingBox.bottom);
          path.lineTo(cx + offsetX, boundingBox.bottom);
          path.lineTo(cx + offsetX, cy + offsetY);
          path.close();
          path.moveTo(cx + start, cy);
          path.lineTo(cx + offsetY, cy - offsetX);
          path.lineTo(boundingBox.right, cy - offsetX);
          path.lineTo(boundingBox.right, cy + offsetX);
          path.lineTo(cx + offsetY, cy + offsetX);
          path.close();
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fillColor);
        canvas.drawPath(path, paint);
        if (engaged) {
          paint.setColor(pressedFillColor);
          canvas.drawPath(path, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(engaged ? pressedStrokeColor : strokeColor);
        canvas.drawPath(path, paint);
        break;
      }
      case TRACKPAD: {
        float radius = boundingBox.height() * 0.18f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fillColor);
        canvas.drawRoundRect(
            boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom,
            radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(engaged ? pressedStrokeColor : strokeColor);
        canvas.drawRoundRect(
            boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom,
            radius, radius, paint);
        break;
      }
      case RADIAL_MENU:
      case RANGE_BUTTON:
      default:
        // Less common element types — defer to the original renderer to avoid duplicating their
        // bespoke geometry; the surrounding color resolution already covers theme colors.
        drawOriginalLegacy(canvas);
        break;
    }
  }

  private void drawGameHubShape(Canvas canvas, Paint paint, Rect bb, int color, boolean fill) {
    if (fill) {
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(color);
    }
    int snappingSize = inputControlsView.getSnappingSize();
    switch (shape) {
      case CIRCLE:
        canvas.drawCircle(bb.centerX(), bb.centerY(), bb.width() * 0.5f, paint);
        break;
      case RECT:
        canvas.drawRect(bb, paint);
        break;
      case ROUND_RECT: {
        float r = bb.height() * 0.5f;
        canvas.drawRoundRect(bb.left, bb.top, bb.right, bb.bottom, r, r, paint);
        break;
      }
      case SQUARE: {
        float r = snappingSize * 0.85f * scale;
        canvas.drawRoundRect(bb.left, bb.top, bb.right, bb.bottom, r, r, paint);
        break;
      }
    }
  }

  /**
   * Draws a soft radial vignette (transparent center → translucent black at the rim) clipped to
   * the element's GameHub shape. Layered on top of the base fill so the inner edge of the border
   * darkens and gradually fades toward the center, producing a glass / inset-shadow look.
   */
  private void drawGameHubGlassShape(Canvas canvas, Paint paint, Rect bb, int edgeAlpha) {
    if (edgeAlpha <= 0) return;
    float cx = bb.exactCenterX();
    float cy = bb.exactCenterY();
    float gradR = Math.max(bb.width(), bb.height()) * 0.5f;
    paint.setShader(new RadialGradient(
        cx, cy, gradR,
        Color.argb(0, 0, 0, 0), Color.argb(edgeAlpha, 0, 0, 0),
        Shader.TileMode.CLAMP));
    paint.setStyle(Paint.Style.FILL);
    int snappingSize = inputControlsView.getSnappingSize();
    switch (shape) {
      case CIRCLE:
        canvas.drawCircle(cx, cy, bb.width() * 0.5f, paint);
        break;
      case RECT:
        canvas.drawRect(bb, paint);
        break;
      case ROUND_RECT: {
        float r = bb.height() * 0.5f;
        canvas.drawRoundRect(bb.left, bb.top, bb.right, bb.bottom, r, r, paint);
        break;
      }
      case SQUARE: {
        float r = snappingSize * 0.85f * scale;
        canvas.drawRoundRect(bb.left, bb.top, bb.right, bb.bottom, r, r, paint);
        break;
      }
    }
    paint.setShader(null);
  }

  /**
   * Same vignette as {@link #drawGameHubGlassShape} but clipped to an arbitrary path (used for
   * trigger silhouettes and per-arrow d-pad shapes). The gradient is anchored at {@code (cx, cy)}
   * with radius {@code gradR}, which the caller picks to match the path's centroid and extent.
   */
  private void drawGameHubGlassOnPath(
      Canvas canvas, Paint paint, Path path, float cx, float cy, float gradR, int edgeAlpha) {
    if (edgeAlpha <= 0 || gradR <= 0) return;
    paint.setShader(new RadialGradient(
        cx, cy, gradR,
        Color.argb(0, 0, 0, 0), Color.argb(edgeAlpha, 0, 0, 0),
        Shader.TileMode.CLAMP));
    paint.setStyle(Paint.Style.FILL);
    canvas.drawPath(path, paint);
    paint.setShader(null);
  }

  /**
   * Fallback to the original draw routine for element types we don't customize in the GameHub
   * style (RADIAL_MENU, RANGE_BUTTON). This temporarily switches the view's style back to ORIGINAL
   * just for this draw call.
   */
  private void drawOriginalLegacy(Canvas canvas) {
    VisualStyle saved = inputControlsView.getVisualStyle();
    try {
      inputControlsView.setVisualStyleSilent(VisualStyle.ORIGINAL);
      draw(canvas);
    } finally {
      inputControlsView.setVisualStyleSilent(saved);
    }
  }

  private void drawIcon(Canvas canvas, float cx, float cy, float width, float height, int iconId) {
    drawIcon(canvas, cx, cy, width, height, iconId, true);
  }

  private void drawIcon(Canvas canvas, float cx, float cy, float width, float height, int iconId, boolean automargin) {
    Bitmap icon = inputControlsView.getIcon((byte) iconId);
    if (icon == null) return;
    Paint paint = inputControlsView.getPaint();
    paint.setColorFilter(inputControlsView.getColorFilter());
    int margin = automargin ? (int) (inputControlsSize() * (shape == Shape.CIRCLE || shape == Shape.SQUARE ? 2.0f : 1.0f) * scale) : 0;
    int halfSize = (int) ((Math.min(width, height) - margin) * 0.5f);

    Rect srcRect = new Rect(0, 0, icon.getWidth(), icon.getHeight());
    Rect dstRect =
        new Rect(
            (int) (cx - halfSize),
            (int) (cy - halfSize),
            (int) (cx + halfSize),
            (int) (cy + halfSize));
    canvas.drawBitmap(icon, srcRect, dstRect, paint);
    paint.setColorFilter(null);
  }

  private int inputControlsSize() {
    return inputControlsView.getSnappingSize();
  }

  public JSONObject toJSONObject() {
    try {
      JSONObject elementJSONObject = new JSONObject();
      elementJSONObject.put("type", type.name());
      elementJSONObject.put("shape", shape.name());
      elementJSONObject.put("customColor", customColor);

      JSONArray bindingsJSONArray = new JSONArray();
      for (Binding binding : bindings) bindingsJSONArray.put(binding.name());

      elementJSONObject.put("bindings", bindingsJSONArray);
      elementJSONObject.put("scale", Float.valueOf(scale));
      if (opacity < 1.0f) elementJSONObject.put("opacity", Float.valueOf(opacity));
      elementJSONObject.put("x", (float) x / inputControlsView.getMaxWidth());
      elementJSONObject.put("y", (float) y / inputControlsView.getMaxHeight());
      elementJSONObject.put("toggleSwitch", toggleSwitch);
      elementJSONObject.put("text", text);
      elementJSONObject.put("iconId", iconId);

      if (type == Type.RANGE_BUTTON && range != null) {
        elementJSONObject.put("range", range.name());
        if (orientation != 0) elementJSONObject.put("orientation", orientation);
      }
      return elementJSONObject;
    } catch (JSONException e) {
      return null;
    }
  }

  public boolean containsPoint(float x, float y) {
    if (type == Type.RADIAL_MENU && radialMenuExpanded) {
      float outerRadius = boundingBox.width() + (inputControlsView.getSnappingSize() * scale);
      return Mathf.distance((float) boundingBox.centerX(), (float) boundingBox.centerY(), x, y) < outerRadius;
    }
    return getBoundingBox().contains((int) (x + 0.5f), (int) (y + 0.5f));
  }

  private boolean isKeepButtonPressedAfterMinTime() {
    Binding binding = getBindingAt(0);
    return !toggleSwitch
        && (binding == Binding.GAMEPAD_BUTTON_L3 || binding == Binding.GAMEPAD_BUTTON_R3);
  }

  private void dispatchButtonBinding(Binding primary, Binding secondary, boolean pressed) {
    inputControlsView.handleInputEvent(primary, pressed);
    if (secondary != Binding.NONE && secondary != primary) {
      inputControlsView.handleInputEvent(secondary, pressed);
    }
  }

  public boolean handleTouchDown(int pointerId, float x, float y) {
    if (currentPointerId == -1 && containsPoint(x, y)) {
      if (type != Type.RANGE_BUTTON && type != Type.RADIAL_MENU) {
        boolean hasBinding = false;
        for (Binding binding : bindings) {
          if (binding != Binding.NONE) {
            hasBinding = true;
            break;
          }
        }
        if (!hasBinding) return false;
      }

      currentPointerId = pointerId;
      if (type == Type.BUTTON) {
        if (isKeepButtonPressedAfterMinTime()) touchTime = System.currentTimeMillis();
        if (!toggleSwitch || !selected) {
          dispatchButtonBinding(getBindingAt(0), getBindingAt(1), true);
        }
        inputControlsView.invalidate();
        return true;
      } else if (type == Type.RADIAL_MENU) {
        wasExpandedOnDown = radialMenuExpanded;
        if (!radialMenuExpanded) {
          radialMenuExpanded = true;
          paths = null;
          isRadialBindingCurrentlyHeld = false;
        } else {
          activeRadialBindingIndex = getRadialBindingIndexAt(x, y);
          boolean isInsideRadius = isPointerInsideRadialMenuRadius(x, y);
          
          if (activeRadialBindingIndex != -1) {
            Binding binding = getBindingAt(activeRadialBindingIndex);
            if (isInsideRadius) {
              inputControlsView.handleInputEvent(binding, true);
              isRadialBindingCurrentlyHeld = true;
            } else if (binding != Binding.NONE) {
              inputControlsView.handleInputEvent(binding, true);
              inputControlsView.postDelayed(() -> inputControlsView.handleInputEvent(binding, false), 30);
            }
          } else if (Mathf.distance((float) boundingBox.centerX(), (float) boundingBox.centerY(), x, y) < boundingBox.width() * 0.5f) {
            radialMenuExpanded = false;
            paths = null;
            isRadialBindingCurrentlyHeld = false;
          }
        }
        inputControlsView.invalidate();
        return true;
      } else if (type == Type.RANGE_BUTTON) {
        scroller.handleTouchDown(x, y);
        inputControlsView.invalidate();
        return true;
      } else {
        if (type == Type.TRACKPAD) {
          if (currentPosition == null) currentPosition = new PointF();
          currentPosition.set(x, y);
        }
        return handleTouchMove(pointerId, x, y);
      }
    } else return false;
  }

  public boolean handleTouchMove(int pointerId, float x, float y) {
    if (pointerId == currentPointerId && type == Type.BUTTON) {
      if (!containsPoint(x, y)) {
        handleTouchUp(pointerId, x, y);
      }
      return true;
    }

    if (pointerId == currentPointerId && type == Type.RADIAL_MENU && radialMenuExpanded) {
      int index = getRadialBindingIndexAt(x, y);
      boolean isInsideRadius = isPointerInsideRadialMenuRadius(x, y);

      if (index != activeRadialBindingIndex) {
        if (activeRadialBindingIndex != -1 && isRadialBindingCurrentlyHeld) {
          inputControlsView.handleInputEvent(getBindingAt(activeRadialBindingIndex), false);
          isRadialBindingCurrentlyHeld = false;
        }

        activeRadialBindingIndex = index;

        if (activeRadialBindingIndex != -1) {
          Binding binding = getBindingAt(activeRadialBindingIndex);
          if (isInsideRadius) {
            inputControlsView.handleInputEvent(binding, true);
            isRadialBindingCurrentlyHeld = true;
          } else if (binding != Binding.NONE) {
            inputControlsView.handleInputEvent(binding, true);
            inputControlsView.postDelayed(() -> inputControlsView.handleInputEvent(binding, false), 30);
          }
        }
      } else if (isInsideRadius != isRadialBindingCurrentlyHeld) {
        if (activeRadialBindingIndex != -1) {
          Binding binding = getBindingAt(activeRadialBindingIndex);
          if (isInsideRadius) {
            inputControlsView.handleInputEvent(binding, true);
            isRadialBindingCurrentlyHeld = true;
          } else {
            inputControlsView.handleInputEvent(binding, false);
            isRadialBindingCurrentlyHeld = false;
          }
        }
      }

      inputControlsView.invalidate();
      return true;
    }

    if (pointerId == currentPointerId
        && (type == Type.D_PAD || type == Type.STICK || type == Type.TRACKPAD)) {
      float deltaX, deltaY;
      Rect boundingBox = getBoundingBox();
      float radius = boundingBox.width() * 0.5f;
      TouchpadView touchpadView = inputControlsView.getTouchpadView();

      if (type == Type.TRACKPAD) {
        if (currentPosition == null) currentPosition = new PointF();
        float[] deltaPoint =
            touchpadView.computeDeltaPoint(currentPosition.x, currentPosition.y, x, y);
        deltaX = deltaPoint[0];
        deltaY = deltaPoint[1];
        currentPosition.set(x, y);
      } else {
        float localX = x - boundingBox.left;
        float localY = y - boundingBox.top;
        float offsetX = localX - radius;
        float offsetY = localY - radius;

        float distance = Mathf.lengthSq(radius - localX, radius - localY);
        if (distance > radius * radius) {
          float angle = (float) Math.atan2(offsetY, offsetX);
          offsetX = (float) (Math.cos(angle) * radius);
          offsetY = (float) (Math.sin(angle) * radius);
        }

        deltaX = Mathf.clamp(offsetX / radius, -1, 1);
        deltaY = Mathf.clamp(offsetY / radius, -1, 1);
      }

      if (type == Type.STICK) {
        if (currentPosition == null) currentPosition = new PointF();
        currentPosition.x = boundingBox.left + deltaX * radius + radius;
        currentPosition.y = boundingBox.top + deltaY * radius + radius;
        Binding firstBinding = getBindingAt(0);
        if (firstBinding.isGamepad()) {
          float magnitude = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
          float finalX = 0;
          float finalY = 0;

          if (magnitude > STICK_DEAD_ZONE) {
            float normalizedX = deltaX / magnitude;
            float normalizedY = deltaY / magnitude;
            float scaledMagnitude = Math.max(0, magnitude - 0.01f) * STICK_SENSITIVITY;
            scaledMagnitude = Math.min(scaledMagnitude, 1.0f);
            finalX = normalizedX * scaledMagnitude;
            finalY = normalizedY * scaledMagnitude;
          }

          inputControlsView.handleStickInput(firstBinding, finalX, finalY);
          for (byte i = 0; i < 4; i++) {
            this.states[i] = true;
          }
        } else {
          float adjDeltaX = (Math.abs(deltaX) < Math.abs(deltaY) * STICK_CROSS_ZONE) ? 0 : deltaX;
          float adjDeltaY = (Math.abs(deltaY) < Math.abs(deltaX) * STICK_CROSS_ZONE) ? 0 : deltaY;
          final boolean[] states = {
            adjDeltaY <= -STICK_DEAD_ZONE,
            adjDeltaX >= STICK_DEAD_ZONE,
            adjDeltaY >= STICK_DEAD_ZONE,
            adjDeltaX <= -STICK_DEAD_ZONE
          };

          for (byte i = 0; i < 4; i++) {
            float value = i == 1 || i == 3 ? deltaX : deltaY;
            Binding binding = getBindingAt(i);
            boolean state = binding.isMouseMove() ? (states[i] || states[(i + 2) % 4]) : states[i];
            inputControlsView.handleInputEvent(binding, state, value);
            this.states[i] = state;
          }
        }

        inputControlsView.invalidate();
      } else if (type == Type.TRACKPAD) {
        Binding firstBinding = getBindingAt(0);
        if (firstBinding.isGamepad()) {
          if (interpolator == null) interpolator = new CubicBezierInterpolator();
          interpolator.set(0.075f, 0.95f, 0.45f, 0.95f);
          float valueX = deltaX;
          float valueY = deltaY;
          if (Math.abs(valueX) > TRACKPAD_ACCELERATION_THRESHOLD) valueX *= STICK_SENSITIVITY;
          if (Math.abs(valueY) > TRACKPAD_ACCELERATION_THRESHOLD) valueY *= STICK_SENSITIVITY;
          float interpX =
              interpolator.getInterpolation(Math.min(1.0f, Math.abs(valueX / TRACKPAD_MAX_SPEED)));
          float interpY =
              interpolator.getInterpolation(Math.min(1.0f, Math.abs(valueY / TRACKPAD_MAX_SPEED)));
          float finalX = Mathf.clamp(Mathf.sign(valueX) * interpX, -1, 1);
          float finalY = Mathf.clamp(Mathf.sign(valueY) * interpY, -1, 1);
          inputControlsView.handleStickInput(firstBinding, finalX, finalY);
          for (byte i = 0; i < 4; i++) {
            this.states[i] = true;
          }
        } else {
          final boolean[] states = {
            deltaY <= -TRACKPAD_MIN_SPEED,
            deltaX >= TRACKPAD_MIN_SPEED,
            deltaY >= TRACKPAD_MIN_SPEED,
            deltaX <= -TRACKPAD_MIN_SPEED
          };
          int cursorDx = 0;
          int cursorDy = 0;

          for (byte i = 0; i < 4; i++) {
            float value = (i == 1 || i == 3 ? deltaX : deltaY);
            Binding binding = getBindingAt(i);
            if (Math.abs(value) > TouchpadView.CURSOR_ACCELERATION_THRESHOLD)
              value *= TouchpadView.CURSOR_ACCELERATION;
            if (binding == Binding.MOUSE_MOVE_LEFT || binding == Binding.MOUSE_MOVE_RIGHT) {
              cursorDx = Mathf.roundPoint(value);
            } else if (binding == Binding.MOUSE_MOVE_UP || binding == Binding.MOUSE_MOVE_DOWN) {
              cursorDy = Mathf.roundPoint(value);
            } else {
              inputControlsView.handleInputEvent(binding, states[i], value);
              this.states[i] = states[i];
            }
          }

          if (cursorDx != 0 || cursorDy != 0) {
            XServer xServer = inputControlsView.getXServer();
            if (xServer.isRelativeMouseMovement()) {
              xServer.updatePointerForDisplayDelta(cursorDx, cursorDy);
              xServer.getWinHandler().mouseMoveDelta(cursorDx, cursorDy);
            } else inputControlsView.getXServer().injectPointerMoveDelta(cursorDx, cursorDy);
          }
        }
      } else {
        final boolean[] states = {
          deltaY <= -DPAD_DEAD_ZONE,
          deltaX >= DPAD_DEAD_ZONE,
          deltaY >= DPAD_DEAD_ZONE,
          deltaX <= -DPAD_DEAD_ZONE
        };

        for (byte i = 0; i < 4; i++) {
          float value = i == 1 || i == 3 ? deltaX : deltaY;
          Binding binding = getBindingAt(i);
          boolean state = binding.isMouseMove() ? (states[i] || states[(i + 2) % 4]) : states[i];
          inputControlsView.handleInputEvent(binding, state, value);
          this.states[i] = state;
        }
      }

      return true;
    } else if (pointerId == currentPointerId && type == Type.RANGE_BUTTON) {
      scroller.handleTouchMove(x, y);
      return true;
    } else return false;
  }

  public boolean handleTouchUp(int pointerId, float x, float y) {
    if (pointerId != currentPointerId) return false;

    if (type == Type.BUTTON) {
      final Binding binding = getBindingAt(0);
      final Binding bindingSecondary = getBindingAt(1);
      if (isKeepButtonPressedAfterMinTime() && touchTime != null) {
        long held = System.currentTimeMillis() - (long) touchTime;
        long delay = Math.max(0L, BUTTON_MIN_TIME_TO_KEEP_PRESSED - held);
        inputControlsView.postDelayed(
            () -> {
              dispatchButtonBinding(binding, bindingSecondary, false);
              inputControlsView.invalidate();
            },
            delay);
        touchTime = null;
      } else {
        if (!toggleSwitch || selected) {
          dispatchButtonBinding(binding, bindingSecondary, false);
        }
        if (toggleSwitch) selected = !selected;
      }
      inputControlsView.invalidate();
    } else if (type == Type.RADIAL_MENU) {
      if (activeRadialBindingIndex != -1) {
        if (isRadialBindingCurrentlyHeld) {
           inputControlsView.handleInputEvent(getBindingAt(activeRadialBindingIndex), false);
        }
        
        activeRadialBindingIndex = -1;
        isRadialBindingCurrentlyHeld = false;
        radialMenuExpanded = false;
        paths = null;
      } else {
        if (wasExpandedOnDown) {
          radialMenuExpanded = false;
          paths = null;
        }
      }
      inputControlsView.invalidate();
    } else if (type == Type.RANGE_BUTTON
        || type == Type.D_PAD
        || type == Type.STICK
        || type == Type.TRACKPAD) {
      for (byte i = 0; i < states.length; i++) {
        if (states[i]) inputControlsView.handleInputEvent(getBindingAt(i), false);
        states[i] = false;
      }

      if (type == Type.RANGE_BUTTON) {
        scroller.handleTouchUp();
      }
      if (type == Type.STICK) {
        Binding firstBinding = getBindingAt(0);
        if (firstBinding.isGamepad()) {
          inputControlsView.handleStickInput(firstBinding, 0.0f, 0.0f);
        }
        currentPosition = null;
      }
      if (type == Type.TRACKPAD) {
        Binding firstBinding = getBindingAt(0);
        if (firstBinding.isGamepad()) {
          inputControlsView.handleStickInput(firstBinding, 0.0f, 0.0f);
        }
        currentPosition = null;
      }

      inputControlsView.invalidate();
    }

    currentPointerId = -1;
    return true;
  }

  private int getRadialBindingIndexAt(float x, float y) {
    if (bindings.length == 0) return -1;
    int snappingSize = inputControlsView.getSnappingSize();
    float cx = boundingBox.centerX();
    float cy = boundingBox.centerY();
    float radius = boundingBox.width() * 0.5f;
    float innerRadius = radius + snappingSize * 0.5f;

    float distance = Mathf.distance((float) cx, (float) cy, x, y);
    if (distance >= innerRadius) {
      float angle = (float) Math.toDegrees(Math.atan2(y - cy, x - cx));
      if (angle < 0) angle += 360;
      angle = (angle + 90) % 360;

      int index = (int) (angle / (360.0f / bindings.length));
      return (index >= 0 && index < bindings.length) ? index : -1;
    }
    return -1;
  }

  private boolean isPointerInsideRadialMenuRadius(float x, float y) {
    int snappingSize = inputControlsView.getSnappingSize();
    float cx = boundingBox.centerX();
    float cy = boundingBox.centerY();
    float outerRadius = boundingBox.width() + (snappingSize * scale);
    float distance = Mathf.distance((float) cx, (float) cy, x, y);
    return distance <= outerRadius;
  }

  private void handleRadialMenuClick(float x, float y) {
    int index = getRadialBindingIndexAt(x, y);
    if (index != -1) {
      Binding binding = getBindingAt(index);
      if (binding != Binding.NONE) {
        radialMenuExpanded = false;
        paths = null;
        inputControlsView.handleInputEvent(binding, true);
        inputControlsView.postDelayed(() -> inputControlsView.handleInputEvent(binding, false), 30);
      }
    }
  }

  public boolean handleTouchUp(int pointerId) {
    return handleTouchUp(pointerId, 0, 0);
  }

  public PointF getCurrentPosition() {
    if (currentPosition == null) {
      currentPosition = new PointF(x, y); // Initialize to the center (same as outer circle)
    }
    return currentPosition;
  }

  // New setter for current position to allow resetting
  public void setCurrentPosition(float x, float y) {
    if (currentPosition == null) {
      currentPosition = new PointF();
    }
    currentPosition.set(x, y);
    inputControlsView.invalidate();
  }
}
