package com.winlator.cmod.runtime.input.controls;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
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
  public static final float STICK_DEAD_ZONE = 0.01f;
  public static final float DPAD_DEAD_ZONE = 0.3f;
  public static final float STICK_SENSITIVITY = 2.0f;
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
      bindings[0] = Binding.KEY_W;
      bindings[1] = Binding.KEY_D;
      bindings[2] = Binding.KEY_S;
      bindings[3] = Binding.KEY_A;
    } else if (type == Type.D_PAD) {
      bindings[0] = Binding.GAMEPAD_DPAD_UP;
      bindings[1] = Binding.GAMEPAD_DPAD_RIGHT;
      bindings[2] = Binding.GAMEPAD_DPAD_DOWN;
      bindings[3] = Binding.GAMEPAD_DPAD_LEFT;
    } else if (type == Type.TRACKPAD) {
      bindings[0] = Binding.GAMEPAD_RIGHT_THUMB_UP;
      bindings[1] = Binding.GAMEPAD_RIGHT_THUMB_RIGHT;
      bindings[2] = Binding.GAMEPAD_RIGHT_THUMB_DOWN;
      bindings[3] = Binding.GAMEPAD_RIGHT_THUMB_LEFT;
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
    return boundingBox;
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
    if (text != null && !text.isEmpty()) {
      return text;
    } else {
      Binding binding = getBindingAt(0);
      String text = binding.toString().replace("NUMPAD ", "NP").replace("BUTTON ", "");
      if (text.length() > 7) {
        String[] parts = text.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) sb.append(part.charAt(0));
        return (binding.isMouse() ? "M" : "") + sb;
      } else return text;
    }
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
    int snappingSize = inputControlsView.getSnappingSize();
    Paint paint = inputControlsView.getPaint();
    float effectiveOpacity = inputControlsView.isEditMode() ? Math.max(0.15f, opacity) : opacity;
    int primaryColor = customColor != -1
        ? ColorUtils.setAlphaComponent(customColor, (int) (Math.min(1.0f,
            inputControlsView.getOverlayOpacity() * 2.0f) * 255))
        : inputControlsView.getPrimaryColor();
    int alpha = (int) (Color.alpha(primaryColor) * effectiveOpacity);
    primaryColor = ColorUtils.setAlphaComponent(primaryColor, alpha);
    int fillColor = ColorUtils.setAlphaComponent(primaryColor, (int) (70 * effectiveOpacity));

    int highlightAlpha = (int) (255 * inputControlsView.getOverlayOpacity());
    int secondaryColor = ColorUtils.setAlphaComponent(inputControlsView.getSecondaryColor(), highlightAlpha);

    paint.setColor(
        (selected && customColor == -1) ? secondaryColor : primaryColor);
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
              (selected && customColor == -1)
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
              (selected && customColor == -1)
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
          final boolean[] states = {
            deltaY <= -STICK_DEAD_ZONE,
            deltaX >= STICK_DEAD_ZONE,
            deltaY >= STICK_DEAD_ZONE,
            deltaX <= -STICK_DEAD_ZONE
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
            if (xServer.isRelativeMouseMovement())
              xServer.getWinHandler().mouseEvent(MouseEventFlags.MOVE, cursorDx, cursorDy, 0);
            else inputControlsView.getXServer().injectPointerMoveDelta(cursorDx, cursorDy);
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
