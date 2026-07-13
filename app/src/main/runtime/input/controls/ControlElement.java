package com.winlator.cmod.runtime.input.controls;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
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
  private final Path path = new Path();
  private Path[] paths;
  private boolean[] states = new boolean[4];
  private boolean boundingBoxNeedsUpdate = true;
  private String text = "";
  private byte iconId;
  private Range range;
  private byte orientation;
  private PointF currentPosition;
  private PointF trackpadOrigin;
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

  public Binding[] getBindings() {
    return bindings;
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
    // Position and size always come from the ICP profile. VisualStyle (ORIGINAL vs GAMEHUB) only
    // changes how an element is drawn, never where it sits — GameHub layouts ship as their own ICP.
    return boundingBox;
  }

  /** Trigger/bumper silhouette for this element when drawn in the GameHub style, or null. */
  private GameHubLayout.RenderShape gameHubTriggerShape() {
    return GameHubLayout.triggerShapeFor(GameHubLayout.roleFor(this));
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
    String bumper = bumperTriggerLabel(binding);
    if (bumper != null) return bumper;
    String text = binding.toString().replace("NUMPAD ", "NP").replace("BUTTON ", "");
    if (text.length() > 7) {
      String[] parts = text.split(" ");
      StringBuilder sb = new StringBuilder();
      for (String part : parts) sb.append(part.charAt(0));
      return (binding.isMouse() ? "M" : "") + sb;
    } else return text;
  }

  /**
   * Returns the per-element customColor (user explicit override), or {@code -1} if unset (caller
   * should fall back to the theme accent or the overlay primary color).
   */
  private int resolveAccentColor() {
    return customColor;
  }

  /** Bumper/trigger labels follow the standard gamepad naming; null for every other binding. */
  private static String bumperTriggerLabel(Binding binding) {
    if (binding == null) return null;
    switch (binding) {
      case GAMEPAD_BUTTON_L1:
        return "LB";
      case GAMEPAD_BUTTON_R1:
        return "RB";
      case GAMEPAD_BUTTON_L2:
        return "LT";
      case GAMEPAD_BUTTON_R2:
        return "RT";
      default:
        return null;
    }
  }

  private String getBindingShortText(int index) {
    Binding binding = getBindingAt(index);
    String bumper = bumperTriggerLabel(binding);
    if (bumper != null) return bumper;
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

  // Shared draw caches. Drawing happens only on the UI thread, so static temps are safe.
  private static Shader bloomShader;
  private static Shader edgeShadeShader;
  private static final Matrix shaderMatrix = new Matrix();
  private static final RectF tempRect = new RectF();
  private PorterDuffColorFilter cachedAccentFilter;
  private int cachedAccentFilterColor = 1;
  private CornerPathEffect cachedCornerEffect;
  private float cachedCornerRadius = -1f;

  private static Shader getBloomShader() {
    if (bloomShader == null) {
      bloomShader =
          new RadialGradient(
              0f, 0f, 1f,
              new int[] {0x8CFFFFFF, 0x3EFFFFFF, 0x00FFFFFF},
              new float[] {0f, 0.6f, 1f},
              Shader.TileMode.CLAMP);
    }
    return bloomShader;
  }

  private static Shader getEdgeShadeShader() {
    if (edgeShadeShader == null) {
      edgeShadeShader =
          new RadialGradient(0f, 0f, 1f, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP);
    }
    return edgeShadeShader;
  }

  private static void placeShader(Shader shader, float cx, float cy, float r) {
    shaderMatrix.reset();
    shaderMatrix.postScale(r, r);
    shaderMatrix.postTranslate(cx, cy);
    shader.setLocalMatrix(shaderMatrix);
  }

  private PorterDuffColorFilter accentFilter(int color) {
    if (cachedAccentFilterColor != color) {
      cachedAccentFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
      cachedAccentFilterColor = color;
    }
    return cachedAccentFilter;
  }

  private CornerPathEffect cornerEffect(float radius) {
    if (cachedCornerEffect == null || cachedCornerRadius != radius) {
      cachedCornerEffect = new CornerPathEffect(radius);
      cachedCornerRadius = radius;
    }
    return cachedCornerEffect;
  }

  private int resolveThemedAccent() {
    int c = resolveAccentColor();
    return c != -1 ? c : inputControlsView.getAccentTheme().accent;
  }

  private void beginBloom(Paint paint, float cx, float cy, float r, int accent, float alpha) {
    Shader bloom = getBloomShader();
    placeShader(bloom, cx, cy, r);
    paint.setStyle(Paint.Style.FILL);
    paint.setShader(bloom);
    paint.setColorFilter(accentFilter(accent));
    paint.setAlpha((int) (255 * alpha));
  }

  private static void endBloom(Paint paint) {
    paint.setShader(null);
    paint.setColorFilter(null);
  }

  public void draw(Canvas canvas) {
    VisualStyle style = inputControlsView.getVisualStyle();
    if (style == VisualStyle.GAMEHUB) {
      drawGameHub(canvas);
      return;
    }
    if (style != VisualStyle.ORIGINAL) {
      drawClean(canvas, style);
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

  private float cleanCornerRadius(Rect bb) {
    switch (shape) {
      case ROUND_RECT:
        return bb.height() * 0.5f;
      case SQUARE:
        return Math.min(bb.width(), bb.height()) * 0.28f;
      case RECT:
        return bb.height() * 0.22f;
      default:
        return 0f;
    }
  }

  private void drawCleanBody(Canvas canvas, Paint paint, Rect bb, float inset) {
    if (shape == Shape.CIRCLE) {
      canvas.drawCircle(bb.exactCenterX(), bb.exactCenterY(), bb.width() * 0.5f - inset, paint);
    } else {
      float r = Math.max(2f, cleanCornerRadius(bb) - inset);
      canvas.drawRoundRect(
          bb.left + inset, bb.top + inset, bb.right - inset, bb.bottom - inset, r, r, paint);
    }
  }

  /**
   * Themed visual styles (SLATE, HALO, GLINT) — flat graphite body with the accent color carried
   * by the outline, indicators and labels. Geometry (positions, bounding boxes, stick thumb,
   * radial menu paths) matches the original code; only the look differs.
   */
  private void drawClean(Canvas canvas, VisualStyle style) {
    int snappingSize = inputControlsView.getSnappingSize();
    Paint paint = inputControlsView.getPaint();
    float effectiveOpacity = inputControlsView.isEditMode() ? Math.max(0.15f, opacity) : opacity;
    float overlayOpacity = inputControlsView.getOverlayOpacity();
    float dim = overlayOpacity <= 0.4f
        ? 0.28f + (overlayOpacity - 0.1f) * (0.5f / 0.3f)
        : 0.78f + (overlayOpacity - 0.4f) * (0.22f / 0.6f);
    float a = Mathf.clamp(dim, 0f, 1f) * effectiveOpacity;
    boolean engaged = isEngaged();
    Rect boundingBox = getBoundingBox();
    int accent = resolveThemedAccent();
    int bodyColor = Color.argb((int) (150 * a), 0x14, 0x18, 0x1F);
    float hairline = Math.max(1.5f, snappingSize * 0.09f * scale);
    boolean halo = style == VisualStyle.HALO;
    boolean glint = style == VisualStyle.GLINT;

    paint.setStrokeJoin(Paint.Join.ROUND);
    paint.setStrokeCap(Paint.Cap.BUTT);

    int frameColor;
    float frameWidth;
    if (halo) {
      frameColor = ColorUtils.setAlphaComponent(accent, (int) ((engaged ? 250 : 215) * a));
      frameWidth = hairline * (engaged ? 2.8f : 1.9f);
    } else if (glint) {
      frameColor = engaged
          ? ColorUtils.setAlphaComponent(accent, (int) (245 * a))
          : Color.argb((int) (55 * a), 255, 255, 255);
      frameWidth = hairline * (engaged ? 1.8f : 1f);
    } else {
      frameColor = ColorUtils.setAlphaComponent(accent, (int) ((engaged ? 240 : 150) * a));
      frameWidth = hairline * (engaged ? 1.6f : 1f);
    }
    if (selected && resolveAccentColor() == -1) {
      frameColor =
          ColorUtils.setAlphaComponent(inputControlsView.getSecondaryColor(), (int) (235 * a));
    }
    float frameInset = halo ? hairline * 2.5f : 0f;
    int labelColor = (halo || engaged)
        ? Color.argb((int) (240 * a), 255, 255, 255)
        : ColorUtils.setAlphaComponent(accent, (int) (235 * a));
    ColorFilter iconTint =
        (halo || engaged) ? inputControlsView.getColorFilter() : accentFilter(accent);

    switch (type) {
      case BUTTON: {
        float cx = boundingBox.centerX();
        float cy = boundingBox.centerY();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bodyColor);
        drawCleanBody(canvas, paint, boundingBox, 0f);
        if (engaged) {
          float bloomRadius = Math.min(boundingBox.width(), boundingBox.height()) * 0.52f;
          beginBloom(paint, cx, cy, bloomRadius, accent, a * 0.9f);
          drawCleanBody(canvas, paint, boundingBox, 0f);
          endBloom(paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(frameWidth);
        paint.setColor(frameColor);
        drawCleanBody(canvas, paint, boundingBox, frameInset);

        if (glint && !engaged) {
          paint.setStrokeCap(Paint.Cap.ROUND);
          paint.setStrokeWidth(hairline * 1.9f);
          paint.setColor(ColorUtils.setAlphaComponent(accent, (int) (220 * a)));
          if (shape == Shape.CIRCLE) {
            tempRect.set(
                boundingBox.left + hairline,
                boundingBox.top + hairline,
                boundingBox.right - hairline,
                boundingBox.bottom - hairline);
            canvas.drawArc(tempRect, 62, 56, false, paint);
          } else {
            float lineY = boundingBox.bottom - hairline * 2.2f;
            float halfLength = boundingBox.width() * 0.21f;
            canvas.drawLine(cx - halfLength, lineY, cx + halfLength, lineY, paint);
          }
          paint.setStrokeCap(Paint.Cap.BUTT);
        }

        if (iconId > 0) {
          paint.setColor(labelColor);
          drawIcon(canvas, cx, cy, boundingBox.width(), boundingBox.height(), iconId, true, iconTint);
        } else {
          String label = getDisplayText();
          paint.setStyle(Paint.Style.FILL);
          paint.setColor(labelColor);
          paint.setTextSize(
              Math.min(
                  getTextSizeForWidth(paint, label, boundingBox.width() - hairline * 4),
                  snappingSize * 2 * scale));
          paint.setTextAlign(Paint.Align.CENTER);
          canvas.drawText(label, x, (y - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
        }
        break;
      }
      case D_PAD: {
        float cx = boundingBox.exactCenterX();
        float cy = boundingBox.exactCenterY();
        float base = Math.min(boundingBox.width(), boundingBox.height());
        float half = base * 0.5f;
        float arm = base * 0.17f;
        path.reset();
        path.moveTo(cx - arm, cy - half);
        path.lineTo(cx + arm, cy - half);
        path.lineTo(cx + arm, cy - arm);
        path.lineTo(cx + half, cy - arm);
        path.lineTo(cx + half, cy + arm);
        path.lineTo(cx + arm, cy + arm);
        path.lineTo(cx + arm, cy + half);
        path.lineTo(cx - arm, cy + half);
        path.lineTo(cx - arm, cy + arm);
        path.lineTo(cx - half, cy + arm);
        path.lineTo(cx - half, cy - arm);
        path.lineTo(cx - arm, cy - arm);
        path.close();

        paint.setPathEffect(cornerEffect(base * 0.09f));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bodyColor);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(frameWidth);
        paint.setColor(frameColor);
        canvas.drawPath(path, paint);
        paint.setPathEffect(null);

        boolean hasStates = engaged && states.length >= 4;
        for (int i = 0; i < 4; i++) {
          int dx = i == 1 ? 1 : i == 3 ? -1 : 0;
          int dy = i == 2 ? 1 : i == 0 ? -1 : 0;
          boolean hot = hasStates && states[i];
          if (hot) {
            float bx = cx + dx * half * 0.55f;
            float by = cy + dy * half * 0.55f;
            beginBloom(paint, bx, by, base * 0.24f, accent, a);
            canvas.drawCircle(bx, by, base * 0.24f, paint);
            endBloom(paint);
          }
          float ax = cx + dx * half * 0.62f;
          float ay = cy + dy * half * 0.62f;
          float t = base * 0.055f;
          path.reset();
          if (i == 0) {
            path.moveTo(ax, ay - t);
            path.lineTo(ax + 0.85f * t, ay + 0.65f * t);
            path.lineTo(ax - 0.85f * t, ay + 0.65f * t);
          } else if (i == 1) {
            path.moveTo(ax + t, ay);
            path.lineTo(ax - 0.65f * t, ay + 0.85f * t);
            path.lineTo(ax - 0.65f * t, ay - 0.85f * t);
          } else if (i == 2) {
            path.moveTo(ax, ay + t);
            path.lineTo(ax - 0.85f * t, ay - 0.65f * t);
            path.lineTo(ax + 0.85f * t, ay - 0.65f * t);
          } else {
            path.moveTo(ax - t, ay);
            path.lineTo(ax + 0.65f * t, ay - 0.85f * t);
            path.lineTo(ax + 0.65f * t, ay + 0.85f * t);
          }
          path.close();
          paint.setStyle(Paint.Style.FILL);
          paint.setColor(hot
              ? ColorUtils.setAlphaComponent(accent, (int) (240 * a))
              : Color.argb((int) (190 * a), 255, 255, 255));
          canvas.drawPath(path, paint);
        }

        if (glint) {
          paint.setStyle(Paint.Style.STROKE);
          paint.setStrokeWidth(hairline * 1.9f);
          paint.setStrokeCap(Paint.Cap.ROUND);
          float tickInset = hairline * 1.6f;
          float span = arm * 0.45f;
          for (int i = 0; i < 4; i++) {
            boolean hot = hasStates && states[i];
            paint.setColor(ColorUtils.setAlphaComponent(accent, (int) ((hot ? 245 : 170) * a)));
            if (i == 0) {
              canvas.drawLine(cx - span, cy - half + tickInset, cx + span, cy - half + tickInset, paint);
            } else if (i == 1) {
              canvas.drawLine(cx + half - tickInset, cy - span, cx + half - tickInset, cy + span, paint);
            } else if (i == 2) {
              canvas.drawLine(cx - span, cy + half - tickInset, cx + span, cy + half - tickInset, paint);
            } else {
              canvas.drawLine(cx - half + tickInset, cy - span, cx - half + tickInset, cy + span, paint);
            }
          }
          paint.setStrokeCap(Paint.Cap.BUTT);
        }
        break;
      }
      case STICK: {
        float cx = boundingBox.exactCenterX();
        float cy = boundingBox.exactCenterY();
        float r = boundingBox.height() * 0.5f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb((int) (120 * a), 0x14, 0x18, 0x1F));
        canvas.drawCircle(cx, cy, r, paint);

        float thumbX = getCurrentPosition().x;
        float thumbY = getCurrentPosition().y;
        float thumbRadius = snappingSize * 3.5f * scale;

        if (engaged) {
          beginBloom(paint, thumbX, thumbY, thumbRadius * 1.6f, accent, a * 0.8f);
          canvas.drawCircle(thumbX, thumbY, thumbRadius * 1.6f, paint);
          endBloom(paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(frameWidth);
        paint.setColor(frameColor);
        canvas.drawCircle(cx, cy, r - frameInset, paint);

        float guideWidth = Math.max(2f, snappingSize * 0.14f * scale);
        paint.setStrokeWidth(guideWidth);
        if (glint && !engaged) {
          paint.setStrokeCap(Paint.Cap.ROUND);
          paint.setColor(ColorUtils.setAlphaComponent(accent, (int) (110 * a)));
          float arcRadius = r - hairline;
          tempRect.set(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius);
          canvas.drawArc(tempRect, 62, 56, false, paint);
          paint.setStrokeCap(Paint.Cap.BUTT);
        } else {
          paint.setColor(ColorUtils.setAlphaComponent(accent, (int) ((engaged ? 220 : 110) * a)));
          canvas.drawCircle(cx, cy, r * 0.58f, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb((int) (200 * a), 0x1C, 0x22, 0x2B));
        canvas.drawCircle(thumbX, thumbY, thumbRadius, paint);
        if (glint) {
          paint.setStyle(Paint.Style.STROKE);
          paint.setStrokeWidth(hairline);
          paint.setColor(Color.argb((int) (55 * a), 255, 255, 255));
          canvas.drawCircle(thumbX, thumbY, thumbRadius, paint);
          paint.setStyle(Paint.Style.FILL);
          paint.setColor(ColorUtils.setAlphaComponent(accent, (int) (240 * a)));
          canvas.drawCircle(thumbX, thumbY, Math.max(2.5f, snappingSize * 0.3f), paint);
        } else {
          paint.setStyle(Paint.Style.STROKE);
          paint.setStrokeWidth(hairline * (halo ? 2.2f : 1.4f));
          paint.setColor(ColorUtils.setAlphaComponent(accent, (int) ((engaged ? 245 : 150) * a)));
          canvas.drawCircle(thumbX, thumbY, thumbRadius, paint);
        }
        break;
      }
      case TRACKPAD: {
        float cx = boundingBox.exactCenterX();
        float cy = boundingBox.exactCenterY();
        float radius = boundingBox.height() * 0.18f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bodyColor);
        canvas.drawRoundRect(
            boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom,
            radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(frameWidth);
        paint.setColor(frameColor);
        if (halo) {
          float in = frameInset;
          float fr = Math.max(2f, radius - in);
          canvas.drawRoundRect(
              boundingBox.left + in, boundingBox.top + in,
              boundingBox.right - in, boundingBox.bottom - in,
              fr, fr, paint);
        } else {
          canvas.drawRoundRect(
              boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom,
              radius, radius, paint);
        }
        if (glint && !engaged) {
          paint.setStrokeCap(Paint.Cap.ROUND);
          paint.setStrokeWidth(hairline * 1.9f);
          paint.setColor(ColorUtils.setAlphaComponent(accent, (int) (220 * a)));
          float lineY = boundingBox.bottom - hairline * 2.2f;
          float halfLength = boundingBox.width() * 0.175f;
          canvas.drawLine(cx - halfLength, lineY, cx + halfLength, lineY, paint);
          paint.setStrokeCap(Paint.Cap.BUTT);
        }

        float s = Math.min(boundingBox.width(), boundingBox.height()) * 0.45f;
        float glyphW = 0.42f * s;
        float glyphH = 0.62f * s;
        float glyphRadius = glyphW * 0.5f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, s * 0.035f));
        paint.setColor(ColorUtils.setAlphaComponent(accent, (int) ((engaged ? 220 : 110) * a)));
        canvas.drawRoundRect(
            cx - glyphW * 0.5f, cy - glyphH * 0.5f, cx + glyphW * 0.5f, cy + glyphH * 0.5f,
            glyphRadius, glyphRadius, paint);
        canvas.drawLine(cx, cy - 0.30f * glyphH, cx, cy - 0.02f * glyphH, paint);
        break;
      }
      case RANGE_BUTTON: {
        Range range = getRange();
        float rr = (orientation == 0 ? boundingBox.height() : boundingBox.width()) * 0.45f;
        float elementSize = scroller.getElementSize();
        float minTextSize = snappingSize * 2 * scale;
        float scrollOffset = scroller.getScrollOffset();
        byte[] rangeIndex = scroller.getRangeIndex();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bodyColor);
        canvas.drawRoundRect(
            boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom,
            rr, rr, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(frameWidth);
        paint.setColor(frameColor);
        canvas.drawRoundRect(
            boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom,
            rr, rr, paint);

        canvas.save();
        path.reset();
        path.addRoundRect(
            boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom,
            rr, rr, Path.Direction.CW);
        canvas.clipPath(path);

        int pressedIndex =
            currentPointerId != -1 && !scroller.isScrolling() ? scroller.getBindingIndex() : -1;
        int dividerColor = Color.argb((int) (45 * a), 255, 255, 255);
        float dividerWidth = Math.max(1f, snappingSize * 0.06f);
        int hotTextColor = Color.argb((int) (240 * a), 255, 255, 255);
        int cellTextColor = ColorUtils.setAlphaComponent(accent, (int) (225 * a));

        if (orientation == 0) {
          float lineTop = boundingBox.top + boundingBox.height() * 0.25f;
          float lineBottom = boundingBox.bottom - boundingBox.height() * 0.25f;
          float startX = boundingBox.left - (scrollOffset % elementSize);

          for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
            int index = i % range.max;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dividerWidth);
            paint.setColor(dividerColor);
            if (startX > boundingBox.left && startX < boundingBox.right)
              canvas.drawLine(startX, lineTop, startX, lineBottom, paint);

            if (startX < boundingBox.right && startX + elementSize > boundingBox.left) {
              boolean hot = index == pressedIndex;
              if (hot) {
                drawCleanHotCell(
                    canvas, paint, style, accent, a, hairline,
                    startX, boundingBox.top, startX + elementSize, boundingBox.bottom);
              }
              String cellText = getRangeTextForIndex(range, index);
              paint.setStyle(Paint.Style.FILL);
              paint.setColor(hot ? hotTextColor : cellTextColor);
              paint.setTextSize(
                  Math.min(
                      getTextSizeForWidth(paint, cellText, elementSize - hairline * 4),
                      minTextSize));
              paint.setTextAlign(Paint.Align.CENTER);
              canvas.drawText(
                  cellText,
                  startX + elementSize * 0.5f,
                  (y - ((paint.descent() + paint.ascent()) * 0.5f)),
                  paint);
            }
            startX += elementSize;
          }
        } else {
          float lineLeft = boundingBox.left + boundingBox.width() * 0.25f;
          float lineRight = boundingBox.right - boundingBox.width() * 0.25f;
          float startY = boundingBox.top - (scrollOffset % elementSize);

          for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dividerWidth);
            paint.setColor(dividerColor);
            if (startY > boundingBox.top && startY < boundingBox.bottom)
              canvas.drawLine(lineLeft, startY, lineRight, startY, paint);

            if (startY < boundingBox.bottom && startY + elementSize > boundingBox.top) {
              boolean hot = i % range.max == pressedIndex;
              if (hot) {
                drawCleanHotCell(
                    canvas, paint, style, accent, a, hairline,
                    boundingBox.left, startY, boundingBox.right, startY + elementSize);
              }
              String cellText = getRangeTextForIndex(range, i % range.max);
              paint.setStyle(Paint.Style.FILL);
              paint.setColor(hot ? hotTextColor : cellTextColor);
              paint.setTextSize(
                  Math.min(
                      getTextSizeForWidth(paint, cellText, boundingBox.width() - hairline * 4),
                      minTextSize));
              paint.setTextAlign(Paint.Align.CENTER);
              canvas.drawText(
                  cellText,
                  x,
                  startY + elementSize * 0.5f - ((paint.descent() + paint.ascent()) * 0.5f),
                  paint);
            }
            startY += elementSize;
          }
        }
        canvas.restore();
        break;
      }
      case RADIAL_MENU: {
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
              boolean active = i == activeRadialBindingIndex;
              paint.setStyle(Paint.Style.FILL);
              paint.setColor(
                  active ? ColorUtils.setAlphaComponent(accent, (int) (80 * a)) : bodyColor);
              canvas.drawPath(paths[i], paint);

              paint.setStyle(Paint.Style.STROKE);
              paint.setStrokeWidth(hairline);
              paint.setColor(Color.argb((int) (50 * a), 255, 255, 255));
              canvas.drawPath(paths[i], paint);

              float middleAngle = (float) Math.toRadians(-90.0f + i * angleStep + angleStep * 0.5f);
              float labelRadius = (innerRadius + outerRadius) * 0.5f;
              float labelX = (float) (cx + Math.cos(middleAngle) * labelRadius);
              float labelY = (float) (cy + Math.sin(middleAngle) * labelRadius);

              String label = getBindingShortText(i);
              paint.setStyle(Paint.Style.FILL);
              paint.setColor(active
                  ? Color.argb((int) (240 * a), 255, 255, 255)
                  : ColorUtils.setAlphaComponent(accent, (int) (215 * a)));
              paint.setTextSize(snappingSize * 1.2f * scale);
              paint.setTextAlign(Paint.Align.CENTER);
              canvas.drawText(label, labelX, labelY - ((paint.descent() + paint.ascent()) * 0.5f), paint);
            }
          }
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bodyColor);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(frameWidth);
        paint.setColor(frameColor);
        canvas.drawCircle(cx, cy, radius - frameInset, paint);

        paint.setColor(labelColor);
        drawIcon(
            canvas, cx, cy, boundingBox.width(), boundingBox.height(),
            iconId > 0 ? iconId : 34, true, iconTint);
        break;
      }
    }
    paint.setStrokeJoin(Paint.Join.MITER);
    paint.setStrokeCap(Paint.Cap.BUTT);
  }

  private void drawCleanHotCell(
      Canvas canvas, Paint paint, VisualStyle style, int accent, float a, float hairline,
      float left, float top, float right, float bottom) {
    if (style == VisualStyle.GLINT) {
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(hairline * 2.5f);
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setColor(ColorUtils.setAlphaComponent(accent, (int) (240 * a)));
      float cx = (left + right) * 0.5f;
      float halfBar = (right - left) * 0.275f;
      float lineY = bottom - (bottom - top) * 0.12f;
      canvas.drawLine(cx - halfBar, lineY, cx + halfBar, lineY, paint);
      paint.setStrokeCap(Paint.Cap.BUTT);
      return;
    }
    tempRect.set(left + 4, top + 5, right - 4, bottom - 5);
    float r = tempRect.height() * 0.5f;
    if (style == VisualStyle.HALO) {
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(hairline * 1.6f);
      paint.setColor(ColorUtils.setAlphaComponent(accent, (int) (235 * a)));
    } else {
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(ColorUtils.setAlphaComponent(accent, (int) (70 * a)));
    }
    canvas.drawRoundRect(tempRect, r, r, paint);
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

    int custom = resolveAccentColor();
    AccentTheme theme = inputControlsView.getAccentTheme();
    int accent = custom != -1 ? custom : (theme != AccentTheme.MONO ? theme.accent : -1);
    boolean hasAccent = accent != -1;

    // Anchored at 40% default; steeper below, gentle to full above.
    float gameHubDim = overlayOpacity <= 0.4f
        ? 0.28f + (overlayOpacity - 0.1f) * (0.5f / 0.3f)
        : 0.78f + (overlayOpacity - 0.4f) * (0.22f / 0.6f);
    int fillAlpha = (int) (90 * gameHubDim * effectiveOpacity);
    int strokeAlpha = (int) (150 * gameHubDim * effectiveOpacity);
    int pressedFillAlpha = (int) (60 * gameHubDim * effectiveOpacity);
    int pressedStrokeAlpha = (int) (220 * gameHubDim * effectiveOpacity);
    int textAlpha = (int) (255 * gameHubDim * effectiveOpacity);
    int glassEdgeAlpha = (int) (75 * gameHubDim * effectiveOpacity);
    // The edge shade is modulated by the body fill's alpha so the vignette keeps its subtle weight.
    int glassShadeAlpha = glassEdgeAlpha * fillAlpha / 255;
    int pressedGlassShadeAlpha = glassEdgeAlpha * pressedFillAlpha / 255;

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
        GameHubLayout.RenderShape triggerShape = gameHubTriggerShape();
        boolean isTrigger = triggerShape != null;

        if (isTrigger) {
          GameHubLayout.buildTriggerPath(
              path, triggerShape,
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
              Math.max(boundingBox.width(), boundingBox.height()) * 0.5f,
              engaged ? pressedGlassShadeAlpha : glassShadeAlpha);
          paint.setStyle(Paint.Style.STROKE);
          paint.setColor(engaged ? pressedStrokeColor : strokeColor);
          canvas.drawPath(path, paint);
        } else {
          drawGameHubShape(canvas, paint, boundingBox, fillColor, true);
          if (engaged) drawGameHubShape(canvas, paint, boundingBox, pressedFillColor, true);
          drawGameHubGlassShape(
              canvas, paint, boundingBox, engaged ? pressedGlassShadeAlpha : glassShadeAlpha);
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

        if (glassShadeAlpha > 0) {
          placeShader(getEdgeShadeShader(), cx, cy, ringRadius);
          paint.setShader(getEdgeShadeShader());
          paint.setStyle(Paint.Style.FILL);
          paint.setAlpha(glassShadeAlpha);
          canvas.drawCircle(cx, cy, ringRadius, paint);
          paint.setShader(null);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(engaged ? pressedStrokeColor : strokeColor);
        canvas.drawCircle(cx, cy, ringRadius - strokeWidth * 0.5f, paint);

        float thumbX = engaged ? getCurrentPosition().x : cx;
        float thumbY = engaged ? getCurrentPosition().y : cy;
        float thumbRadius = ringRadius * 0.48f;
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

        float radius = Math.min(boundingBox.width(), boundingBox.height()) * 0.5f;
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
                canvas, paint, path, arrowCenter[0], arrowCenter[1], arrowGradR,
                engaged ? pressedGlassShadeAlpha : glassShadeAlpha);
          }
        }
        GameHubLayout.buildDpadArrows(path, cx, cy, radius);
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
      case RADIAL_MENU: {
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
              boolean isSegmentEngaged = i == activeRadialBindingIndex;
              paint.setStyle(Paint.Style.FILL);
              paint.setColor(isSegmentEngaged ? pressedFillColor : fillColor);
              canvas.drawPath(paths[i], paint);

              drawGameHubGlassOnPath(
                  canvas, paint, paths[i], cx, cy, outerRadius,
                  isSegmentEngaged ? pressedGlassShadeAlpha : glassShadeAlpha);

              paint.setStyle(Paint.Style.STROKE);
              paint.setColor(isSegmentEngaged ? pressedStrokeColor : strokeColor);
              canvas.drawPath(paths[i], paint);

              float middleAngle = (float) Math.toRadians(-90.0f + i * angleStep + angleStep * 0.5f);
              float labelRadius = (innerRadius + outerRadius) * 0.5f;
              float labelX = (float) (cx + Math.cos(middleAngle) * labelRadius);
              float labelY = (float) (cy + Math.sin(middleAngle) * labelRadius);

              String label = getBindingShortText(i);
              paint.setStyle(Paint.Style.FILL);
              paint.setColor(textColor);
              paint.setTextSize(snappingSize * 1.2f * scale);
              paint.setTextAlign(Paint.Align.CENTER);
              canvas.drawText(label, labelX, labelY - ((paint.descent() + paint.ascent()) * 0.5f), paint);
            }
          }
        }

        drawGameHubShape(canvas, paint, boundingBox, fillColor, true);
        if (engaged) drawGameHubShape(canvas, paint, boundingBox, pressedFillColor, true);
        drawGameHubGlassShape(
            canvas, paint, boundingBox, engaged ? pressedGlassShadeAlpha : glassShadeAlpha);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(engaged ? pressedStrokeColor : strokeColor);
        drawGameHubShape(canvas, paint, boundingBox, 0, false);

        if (iconId > 0) {
          drawIcon(canvas, cx, cy, boundingBox.width(), boundingBox.height(), iconId);
        } else {
          drawIcon(canvas, cx, cy, boundingBox.width(), boundingBox.height(), 34);
        }
        break;
      }
      case RANGE_BUTTON: {
        Range range = getRange();
        float radius = snappingSize * 0.75f * scale;
        float elementSize = scroller.getElementSize();
        float minTextSize = snappingSize * 2 * scale;
        float scrollOffset = scroller.getScrollOffset();
        byte[] rangeIndex = scroller.getRangeIndex();
        path.reset();

        drawGameHubShape(canvas, paint, boundingBox, fillColor, true, Shape.ROUND_RECT);
        if (engaged) drawGameHubShape(canvas, paint, boundingBox, pressedFillColor, true, Shape.ROUND_RECT);
        drawGameHubGlassShape(
            canvas, paint, boundingBox,
            engaged ? pressedGlassShadeAlpha : glassShadeAlpha, Shape.ROUND_RECT);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(engaged ? pressedStrokeColor : strokeColor);
        drawGameHubShape(canvas, paint, boundingBox, 0, false, Shape.ROUND_RECT);

        canvas.save();
        path.addRoundRect(
            boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom,
            radius, radius, Path.Direction.CW);
        canvas.clipPath(path);

        if (orientation == 0) {
          float lineTop = boundingBox.top + strokeWidth * 0.5f;
          float lineBottom = boundingBox.bottom - strokeWidth * 0.5f;
          float startX = boundingBox.left - (scrollOffset % elementSize);

          for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
            int index = i % range.max;
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(strokeColor);
            if (startX > boundingBox.left && startX < boundingBox.right)
              canvas.drawLine(startX, lineTop, startX, lineBottom, paint);
            String text = getRangeTextForIndex(range, index);
            if (startX < boundingBox.right && startX + elementSize > boundingBox.left) {
              paint.setStyle(Paint.Style.FILL);
              paint.setColor(textColor);
              paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, elementSize - strokeWidth * 2), minTextSize));
              paint.setTextAlign(Paint.Align.CENTER);
              canvas.drawText(text, startX + elementSize * 0.5f, (boundingBox.centerY() - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
            }
            startX += elementSize;
          }
        } else {
          float lineLeft = boundingBox.left + strokeWidth * 0.5f;
          float lineRight = boundingBox.right - strokeWidth * 0.5f;
          float startY = boundingBox.top - (scrollOffset % elementSize);

          for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(strokeColor);
            if (startY > boundingBox.top && startY < boundingBox.bottom)
              canvas.drawLine(lineLeft, startY, lineRight, startY, paint);
            String text = getRangeTextForIndex(range, i);
            if (startY < boundingBox.bottom && startY + elementSize > boundingBox.top) {
              paint.setStyle(Paint.Style.FILL);
              paint.setColor(textColor);
              paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, boundingBox.width() - strokeWidth * 2), minTextSize));
              paint.setTextAlign(Paint.Align.CENTER);
              canvas.drawText(text, boundingBox.centerX(), startY + elementSize * 0.5f - ((paint.descent() + paint.ascent()) * 0.5f), paint);
            }
            startY += elementSize;
          }
        }
        canvas.restore();
        break;
      }
      default:
        drawOriginalLegacy(canvas);
        break;
    }
    paint.setStrokeJoin(Paint.Join.MITER);
    paint.setStrokeCap(Paint.Cap.BUTT);
  }

  private void drawGameHubShape(Canvas canvas, Paint paint, Rect bb, int color, boolean fill) {
    drawGameHubShape(canvas, paint, bb, color, fill, shape);
  }

  private void drawGameHubShape(Canvas canvas, Paint paint, Rect bb, int color, boolean fill, Shape overrideShape) {
    if (fill) {
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(color);
    }
    int snappingSize = inputControlsView.getSnappingSize();
    switch (overrideShape) {
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

  private void drawGameHubGlassShape(Canvas canvas, Paint paint, Rect bb, int edgeAlpha) {
    drawGameHubGlassShape(canvas, paint, bb, edgeAlpha, shape);
  }

  private void drawGameHubGlassShape(Canvas canvas, Paint paint, Rect bb, int edgeAlpha, Shape overrideShape) {
    if (edgeAlpha <= 0) return;
    float cx = bb.exactCenterX();
    float cy = bb.exactCenterY();
    float gradR = Math.max(bb.width(), bb.height()) * 0.5f;
    placeShader(getEdgeShadeShader(), cx, cy, gradR);
    paint.setShader(getEdgeShadeShader());
    paint.setStyle(Paint.Style.FILL);
    paint.setAlpha(edgeAlpha);
    int snappingSize = inputControlsView.getSnappingSize();
    switch (overrideShape) {
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

  private void drawGameHubGlassOnPath(
      Canvas canvas, Paint paint, Path path, float cx, float cy, float gradR, int edgeAlpha) {
    if (edgeAlpha <= 0 || gradR <= 0) return;
    placeShader(getEdgeShadeShader(), cx, cy, gradR);
    paint.setShader(getEdgeShadeShader());
    paint.setStyle(Paint.Style.FILL);
    paint.setAlpha(edgeAlpha);
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

  private static final Rect iconSrcRect = new Rect();
  private static final Rect iconDstRect = new Rect();

  private void drawIcon(Canvas canvas, float cx, float cy, float width, float height, int iconId) {
    drawIcon(canvas, cx, cy, width, height, iconId, true);
  }

  private void drawIcon(Canvas canvas, float cx, float cy, float width, float height, int iconId, boolean automargin) {
    drawIcon(canvas, cx, cy, width, height, iconId, automargin, inputControlsView.getColorFilter());
  }

  private void drawIcon(Canvas canvas, float cx, float cy, float width, float height, int iconId, boolean automargin, ColorFilter tint) {
    Bitmap icon = inputControlsView.getIcon((byte) iconId);
    if (icon == null) return;
    Paint paint = inputControlsView.getPaint();
    paint.setColorFilter(tint);
    int margin = automargin ? (int) (inputControlsSize() * (shape == Shape.CIRCLE || shape == Shape.SQUARE ? 2.0f : 1.0f) * scale) : 0;
    int halfSize = (int) ((Math.min(width, height) - margin) * 0.5f);

    iconSrcRect.set(0, 0, icon.getWidth(), icon.getHeight());
    iconDstRect.set(
        (int) (cx - halfSize),
        (int) (cy - halfSize),
        (int) (cx + halfSize),
        (int) (cy + halfSize));
    canvas.drawBitmap(icon, iconSrcRect, iconDstRect, paint);
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
          if (trackpadOrigin == null) trackpadOrigin = new PointF();
          trackpadOrigin.set(x, y);
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
          if (trackpadOrigin == null) trackpadOrigin = new PointF(x, y);
          float offsetX = x - trackpadOrigin.x;
          float offsetY = y - trackpadOrigin.y;
          float distance = (float) Math.sqrt(offsetX * offsetX + offsetY * offsetY);
          float finalX = 0;
          float finalY = 0;
          if (distance > 0) {
            float magnitude = Math.min(distance / radius, 1.0f);
            if (magnitude > STICK_DEAD_ZONE) {
              float scaled = (magnitude - STICK_DEAD_ZONE) / (1.0f - STICK_DEAD_ZONE);
              finalX = (offsetX / distance) * scaled;
              finalY = (offsetY / distance) * scaled;
            }
          }
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
        trackpadOrigin = null;
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
