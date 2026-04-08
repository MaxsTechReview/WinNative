package com.winlator.cmod.inputcontrols;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import androidx.core.graphics.ColorUtils;
import com.winlator.cmod.math.Mathf;
import com.winlator.cmod.widget.InputControlsView;
import com.winlator.cmod.widget.TouchpadView;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ControlElement {
    public static final float STICK_DEAD_ZONE = 0.15f;
    private PointF currentPosition;
    private byte iconId;
    private final InputControlsView inputControlsView;
    private short x;
    private short y;
    private Type type = Type.BUTTON;
    private Shape shape = Shape.CIRCLE;
    private Binding[] bindings = {Binding.NONE, Binding.NONE, Binding.NONE, Binding.NONE};
    private float scale = 1.0f;
    private boolean selected = false;
    private boolean toggleSwitch = false;
    private int currentPointerId = -1;
    private final Rect boundingBox = new Rect();
    private boolean[] states = new boolean[4];
    private boolean boundingBoxNeedsUpdate = true;
    private String text = "";
    private int customColor = -1;

    public enum Type {
        BUTTON, D_PAD, RANGE_BUTTON, STICK, TRACKPAD;
        public static String[] names() {
            Type[] types = values();
            String[] names = new String[types.length];
            for (int i = 0; i < types.length; i++) names[i] = types[i].name().replace("_", "-");
            return names;
        }
    }

    public enum Shape {
        CIRCLE, RECT, ROUND_RECT, SQUARE;
        public static String[] names() {
            Shape[] shapes = values();
            String[] names = new String[shapes.length];
            for (int i = 0; i < shapes.length; i++) names[i] = shapes[i].name().replace("_", " ");
            return names;
        }
    }

    public ControlElement(InputControlsView inputControlsView) {
        this.inputControlsView = inputControlsView;
    }

    private void reset() {
        setBinding(Binding.NONE);
        if (this.type == Type.STICK) {
            this.bindings[0] = Binding.KEY_W;
            this.bindings[1] = Binding.KEY_D;
            this.bindings[2] = Binding.KEY_S;
            this.bindings[3] = Binding.KEY_A;
        } else if (this.type == Type.D_PAD) {
            this.bindings[0] = Binding.GAMEPAD_DPAD_UP;
            this.bindings[1] = Binding.GAMEPAD_DPAD_RIGHT;
            this.bindings[2] = Binding.GAMEPAD_DPAD_DOWN;
            this.bindings[3] = Binding.GAMEPAD_DPAD_LEFT;
        }
        this.text = "";
        this.iconId = (byte) 0;
        this.boundingBoxNeedsUpdate = true;
    }

    public Type getType() { return this.type; }
    public void setType(Type type) { this.type = type; reset(); }

    public int getBindingCount() { return this.bindings.length; }
    public void setBindingCount(int bindingCount) {
        this.bindings = new Binding[bindingCount];
        setBinding(Binding.NONE);
        this.states = new boolean[bindingCount];
        this.boundingBoxNeedsUpdate = true;
    }

    public Shape getShape() { return this.shape; }
    public void setShape(Shape shape) { this.shape = shape; this.boundingBoxNeedsUpdate = true; }

    public boolean isToggleSwitch() { return this.toggleSwitch; }
    public void setToggleSwitch(boolean toggleSwitch) { this.toggleSwitch = toggleSwitch; }

    public int getCustomColor() { return this.customColor; }
    public void setCustomColor(int customColor) { this.customColor = customColor; }

    public Binding getBindingAt(int index) { return index < this.bindings.length ? this.bindings[index] : Binding.NONE; }
    public void setBindingAt(int index, Binding binding) {
        if (index >= this.bindings.length) {
            int oldLength = this.bindings.length;
            this.bindings = Arrays.copyOf(this.bindings, index + 1);
            Arrays.fill(this.bindings, oldLength, this.bindings.length, Binding.NONE);
            this.states = new boolean[this.bindings.length];
            this.boundingBoxNeedsUpdate = true;
        }
        this.bindings[index] = binding;
    }

    public void setBinding(Binding binding) { Arrays.fill(this.bindings, binding); }

    public float getScale() { return this.scale; }
    public void setScale(float scale) { this.scale = scale; this.boundingBoxNeedsUpdate = true; }

    public short getX() { return this.x; }
    public void setX(int x) { this.x = (short) x; this.boundingBoxNeedsUpdate = true; }

    public short getY() { return this.y; }
    public void setY(int y) { this.y = (short) y; this.boundingBoxNeedsUpdate = true; }

    public boolean isSelected() { return this.selected; }
    public void setSelected(boolean selected) { this.selected = selected; }

    public String getText() { return this.text; }
    public void setText(String text) { this.text = text != null ? text : ""; }

    public byte getIconId() { return this.iconId; }
    public void setIconId(int iconId) { this.iconId = (byte) iconId; }

    public Rect getBoundingBox() {
        if (this.boundingBoxNeedsUpdate) computeBoundingBox();
        return this.boundingBox;
    }

    private Rect computeBoundingBox() {
        int snappingSize = this.inputControlsView.getSnappingSize();
        int halfWidth = 0;
        int halfHeight = 0;
        switch (this.type) {
            case BUTTON:
                switch (this.shape) {
                    case RECT:
                    case ROUND_RECT: halfWidth = snappingSize * 4; halfHeight = snappingSize * 2; break;
                    case SQUARE: halfWidth = (int) (snappingSize * 2.5f); halfHeight = (int) (snappingSize * 2.5f); break;
                    case CIRCLE: halfWidth = snappingSize * 3; halfHeight = snappingSize * 3; break;
                }
                break;
            case D_PAD: halfWidth = snappingSize * 7; halfHeight = snappingSize * 7; break;
            case TRACKPAD:
            case STICK: halfWidth = snappingSize * 6; halfHeight = snappingSize * 6; break;
        }
        int hw = (int) (halfWidth * this.scale);
        int hh = (int) (halfHeight * this.scale);
        this.boundingBox.set(this.x - hw, this.y - hh, this.x + hw, this.y + hh);
        this.boundingBoxNeedsUpdate = false;
        return this.boundingBox;
    }

    private String getDisplayText() {
        if (!this.text.isEmpty()) return this.text;
        Binding binding = getBindingAt(0);
        String text = binding.toString().replace("NUMPAD ", "NP").replace("BUTTON ", "");
        if (text.length() > 7) {
            String[] parts = text.split(" ");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) sb.append(part.charAt(0));
            return (binding.isMouse() ? "M" : "") + sb.toString();
        }
        return text;
    }

    private static float getTextSizeForWidth(Paint paint, String text, float desiredWidth) {
        paint.setTextSize(48.0f);
        return (48.0f * desiredWidth) / paint.measureText(text);
    }

    private boolean isEngaged() {
        return this.currentPointerId != -1 || (this.toggleSwitch && this.selected);
    }

    public void draw(Canvas canvas) {
        int snappingSize = this.inputControlsView.getSnappingSize();
        Paint paint = this.inputControlsView.getPaint();
        
        // Use customColor from ICP if available, else fallback to standard
        int primaryColor = this.customColor != -1 ? this.customColor : 0xFFFFFFFF;
        int fillColor = ColorUtils.setAlphaComponent(primaryColor, 70);
        
        Rect bbox = getBoundingBox();
        float strokeWidth = 0.25f * snappingSize;
        paint.setStrokeWidth(strokeWidth);

        switch (this.type) {
            case BUTTON:
                float cx = bbox.centerX();
                float cy = bbox.centerY();
                if (isEngaged()) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(fillColor);
                    switch (this.shape) {
                        case RECT: canvas.drawRect(bbox, paint); break;
                        case ROUND_RECT: float r = bbox.height() * 0.5f; canvas.drawRoundRect(bbox.left, bbox.top, bbox.right, bbox.bottom, r, r, paint); break;
                        case SQUARE: float r2 = snappingSize * 0.75f * this.scale; canvas.drawRoundRect(bbox.left, bbox.top, bbox.right, bbox.bottom, r2, r2, paint); break;
                        case CIRCLE: canvas.drawCircle(cx, cy, bbox.width() * 0.5f, paint); break;
                    }
                }
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(this.selected ? 0xFFFFFF00 : primaryColor);
                switch (this.shape) {
                    case RECT: canvas.drawRect(bbox, paint); break;
                    case ROUND_RECT: float r = bbox.height() * 0.5f; canvas.drawRoundRect(bbox.left, bbox.top, bbox.right, bbox.bottom, r, r, paint); break;
                    case SQUARE: float r2 = snappingSize * 0.75f * this.scale; canvas.drawRoundRect(bbox.left, bbox.top, bbox.right, bbox.bottom, r2, r2, paint); break;
                    case CIRCLE: canvas.drawCircle(cx, cy, bbox.width() * 0.5f, paint); break;
                }
                if (this.iconId > 0) {
                    // Logic for icons if needed
                } else {
                    String text = getDisplayText();
                    paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, bbox.width() - (strokeWidth * 2.0f)), snappingSize * 2 * this.scale));
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(primaryColor);
                    canvas.drawText(text, this.x, this.y - ((paint.descent() + paint.ascent()) * 0.5f), paint);
                }
                break;
            case D_PAD:
                float dcx = bbox.centerX();
                float dcy = bbox.centerY();
                float doffsetX = snappingSize * 2 * this.scale;
                float doffsetY = snappingSize * 3 * this.scale;
                float dstart = snappingSize * this.scale;
                Path path = this.inputControlsView.getPath();
                path.reset();
                path.moveTo(dcx, dcy - dstart);
                path.lineTo(dcx - doffsetX, dcy - doffsetY);
                path.lineTo(dcx - doffsetX, bbox.top);
                path.lineTo(dcx + doffsetX, bbox.top);
                path.lineTo(dcx + doffsetX, dcy - doffsetY);
                path.close();
                path.moveTo(dcx - dstart, dcy);
                path.lineTo(dcx - doffsetY, dcy - doffsetX);
                path.lineTo(bbox.left, dcy - doffsetX);
                path.lineTo(bbox.left, dcy + doffsetX);
                path.lineTo(dcx - doffsetY, dcy + doffsetX);
                path.close();
                path.moveTo(dcx, dcy + dstart);
                path.lineTo(dcx - doffsetX, dcy + doffsetY);
                path.lineTo(dcx - doffsetX, bbox.bottom);
                path.lineTo(dcx + doffsetX, bbox.bottom);
                path.lineTo(dcx + doffsetX, dcy + doffsetY);
                path.close();
                path.moveTo(dcx + dstart, dcy);
                path.lineTo(dcx + doffsetY, dcy - doffsetX);
                path.lineTo(bbox.right, dcy - doffsetX);
                path.lineTo(bbox.right, dcy + doffsetX);
                path.lineTo(dcx + doffsetY, dcy + doffsetX);
                path.close();
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(this.selected ? 0xFFFFFF00 : primaryColor);
                canvas.drawPath(path, paint);
                break;
            case STICK:
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(this.selected ? 0xFFFFFF00 : primaryColor);
                canvas.drawCircle(bbox.centerX(), bbox.centerY(), bbox.height() * 0.5f, paint);
                float tx = getCurrentPosition().x;
                float ty = getCurrentPosition().y;
                float tr = snappingSize * 3.5f * this.scale;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ColorUtils.setAlphaComponent(primaryColor, isEngaged() ? 120 : 50));
                canvas.drawCircle(tx, ty, tr, paint);
                break;
        }
    }

    public boolean containsPoint(float x, float y) {
        return getBoundingBox().contains((int) (x + 0.5f), (int) (y + 0.5f));
    }

    public boolean handleTouchDown(int pointerId, float x, float y) {
        if (this.currentPointerId != -1 || !containsPoint(x, y)) return false;
        this.currentPointerId = pointerId;
        if (this.type == Type.BUTTON) {
            if (!this.toggleSwitch || !this.selected) this.inputControlsView.handleInputEvent(getBindingAt(0), true);
            this.inputControlsView.invalidate();
            return true;
        }
        return handleTouchMove(pointerId, x, y);
    }

    public boolean handleTouchMove(int pointerId, float x, float y) {
        if (pointerId != this.currentPointerId) return false;
        if (this.type == Type.BUTTON) {
            if (!containsPoint(x, y)) handleTouchUp(pointerId);
            return true;
        }
        if (this.type == Type.D_PAD || this.type == Type.STICK) {
            Rect bbox = getBoundingBox();
            float radius = bbox.width() * 0.5f;
            float dx = Mathf.clamp((x - bbox.centerX()) / radius, -1.0f, 1.0f);
            float dy = Mathf.clamp((y - bbox.centerY()) / radius, -1.0f, 1.0f);

            if (this.type == Type.STICK) {
                if (this.currentPosition == null) this.currentPosition = new PointF();
                this.currentPosition.set(bbox.centerX() + (dx * radius), bbox.centerY() + (dy * radius));
                Binding binding = getBindingAt(0);
                if (binding.isGamepad()) {
                    this.inputControlsView.handleStickInput(binding, dx, dy);
                } else {
                    boolean[] states = {dy <= -STICK_DEAD_ZONE, dx >= STICK_DEAD_ZONE, dy >= STICK_DEAD_ZONE, dx <= -STICK_DEAD_ZONE};
                    for (int i = 0; i < 4; i++) {
                        this.inputControlsView.handleInputEvent(getBindingAt(i), states[i], (i == 1 || i == 3) ? dx : dy);
                        this.states[i] = states[i];
                    }
                }
            } else if (this.type == Type.D_PAD) {
                boolean[] states = {dy <= -0.3f, dx >= 0.3f, dy >= 0.3f, dx <= -0.3f};
                for (int i = 0; i < 4; i++) {
                    this.inputControlsView.handleInputEvent(getBindingAt(i), states[i], (i == 1 || i == 3) ? dx : dy);
                    this.states[i] = states[i];
                }
            }
            this.inputControlsView.invalidate();
            return true;
        }
        return false;
    }

    public boolean handleTouchUp(int pointerId) {
        if (pointerId != this.currentPointerId) return false;
        if (this.type == Type.BUTTON) {
            Binding binding = getBindingAt(0);
            if (!this.toggleSwitch || this.selected) this.inputControlsView.handleInputEvent(binding, false);
            if (this.toggleSwitch) this.selected = !this.selected;
            this.inputControlsView.invalidate();
        } else {
            for (int i = 0; i < this.states.length; i++) {
                if (this.states[i]) this.inputControlsView.handleInputEvent(getBindingAt(i), false);
                this.states[i] = false;
            }
            if (this.type == Type.STICK) {
                this.inputControlsView.handleStickInput(getBindingAt(0), 0, 0);
                this.currentPosition = null;
            }
            this.inputControlsView.invalidate();
        }
        this.currentPointerId = -1;
        return true;
    }

    public PointF getCurrentPosition() {
        if (this.currentPosition == null) this.currentPosition = new PointF(this.x, this.y);
        return this.currentPosition;
    }

    public void setX(int x) { this.x = (short) x; this.boundingBoxNeedsUpdate = true; }
    public void setY(int y) { this.y = (short) y; this.boundingBoxNeedsUpdate = true; }

    public JSONObject toJSONObject() {
        try {
            JSONObject data = new JSONObject();
            data.put("type", this.type.name());
            data.put("shape", this.shape.name());
            data.put("x", ((double) this.x) / ((double) this.inputControlsView.getWidth()));
            data.put("y", ((double) this.y) / ((double) this.inputControlsView.getHeight()));
            data.put("scale", Float.valueOf(this.scale));
            data.put("toggleSwitch", this.toggleSwitch);
            data.put("text", this.text);
            data.put("iconId", (int) this.iconId);
            data.put("customColor", this.customColor);
            JSONArray bindingsJSONArray = new JSONArray();
            for (Binding binding : this.bindings) bindingsJSONArray.put(binding.name());
            data.put("bindings", bindingsJSONArray);
            return data;
        } catch (JSONException e) { return null; }
    }
}
