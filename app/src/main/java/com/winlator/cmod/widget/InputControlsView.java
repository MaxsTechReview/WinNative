package com.winlator.cmod.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.core.view.ViewCompat;
import androidx.preference.PreferenceManager;
import com.winlator.cmod.inputcontrols.Binding;
import com.winlator.cmod.inputcontrols.ControlElement;
import com.winlator.cmod.inputcontrols.ControlsProfile;
import com.winlator.cmod.inputcontrols.ExternalController;
import com.winlator.cmod.inputcontrols.ExternalControllerBinding;
import com.winlator.cmod.inputcontrols.GamepadState;
import com.winlator.cmod.math.Mathf;
import com.winlator.cmod.winhandler.MouseEventFlags;
import com.winlator.cmod.winhandler.WinHandler;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.XServer;
import com.winlator.cmod.R;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

public class InputControlsView extends View {
    public static final float DEFAULT_OVERLAY_OPACITY = 0.4f;
    private static final byte MOUSE_WHEEL_DELTA = 120;
    private final ColorFilter colorFilter;
    private final Point cursor = new Point();
    private boolean editMode = false;
    private boolean focusOnStick = false;
    private Runnable hideControlsRunnable;
    private final Bitmap[] icons = new Bitmap[Binding.values().length];
    private final PointF mouseMoveOffset = new PointF();
    private Timer mouseMoveTimer;
    private boolean moveCursor = false;
    private float offsetX;
    private float offsetY;
    private float overlayOpacity = 0.4f;
    private final Paint paint = new Paint(1);
    private final Path path = new Path();
    private SharedPreferences preferences;
    private ControlsProfile profile;
    private boolean readyToDraw = false;
    private ControlElement selectedElement;
    private boolean showTouchscreenControls = true;
    private int snappingSize;
    public ControlElement stickElement;
    private Handler timeoutHandler;

    public void updateStickPosition(float x, float y) {
        if (this.stickElement != null) {
            this.stickElement.getCurrentPosition().x = x;
            this.stickElement.getCurrentPosition().y = y;
            invalidate();
        }
    }
    private TouchpadView touchpadView;
    private XServer xServer;
    private final SparseArray<ControlElement> activeTouchElements = new SparseArray<>();

    public InputControlsView(Context context) {
        super(context);
        init();
    }

    public InputControlsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public InputControlsView(Context context, Handler timeoutHandler, Runnable hideControlsRunnable) {
        super(context);
        this.timeoutHandler = timeoutHandler;
        this.hideControlsRunnable = hideControlsRunnable;
        init();
    }

    public InputControlsView(Context context, boolean focusOnStick) {
        super(context);
        this.focusOnStick = focusOnStick;
        init();
        if (focusOnStick) {
            setLayoutParams(new FrameLayout.LayoutParams(-2, -2));
        } else {
            setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        }
    }

    private void init() {
        this.preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        this.snappingSize = getResources().getDimensionPixelSize(com.winlator.vanilla.R.dimen.input_controls_snapping_size);
        this.colorFilter = new PorterDuffColorFilter(-1, PorterDuff.Mode.SRC_IN);
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        setBackgroundColor(0);
        setPointerIcon(PointerIcon.load(getResources(), R.drawable.hidden_pointer_arrow));
    }

    public boolean isFocusedOnStick() { return this.focusOnStick; }
    public void setFocusOnStick(boolean focus) { this.focusOnStick = focus; invalidate(); }

    public int getPrimaryColor() {
        return Color.argb((int) (this.overlayOpacity * 255.0f), 255, 255, 255);
    }

    public int getSecondaryColor() {
        return Color.argb((int) (this.overlayOpacity * 255.0f), 2, 119, 189);
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        int width;
        int height;
        if (this.stickElement != null && isFocusedOnStick()) {
            Rect boundingBox = this.stickElement.getBoundingBox();
            width = boundingBox.width();
            height = boundingBox.height();
        } else {
            width = getWidth();
            height = getHeight();
        }
        if (width == 0 || height == 0) return;

        this.snappingSize = width / 100;
        this.readyToDraw = true;

        if (this.editMode) {
            drawGrid(canvas);
            drawCursor(canvas);
        }

        if (this.stickElement != null) {
            this.stickElement.draw(canvas);
        }

        if (this.profile != null && this.showTouchscreenControls && !isFocusedOnStick()) {
            if (!this.profile.isElementsLoaded()) this.profile.loadElements(this);
            for (ControlElement element : this.profile.getElements()) {
                element.draw(canvas);
            }
        }
        super.onDraw(canvas);
    }

    public void resetStickPosition() {
        if (this.stickElement != null) {
            Rect boundingBox = this.stickElement.getBoundingBox();
            this.stickElement.setCurrentPosition(boundingBox.centerX(), boundingBox.centerY());
            invalidate();
        }
    }

    public void initializeStickElement(float x, float y, float scale) {
        this.stickElement = new ControlElement(this);
        this.stickElement.setType(ControlElement.Type.STICK);
        this.stickElement.setX((int) x);
        this.stickElement.setY((int) y);
        this.stickElement.setScale(scale);
        invalidate();
    }

    private void drawGrid(Canvas canvas) {
        this.paint.setStyle(Paint.Style.FILL);
        this.paint.setStrokeWidth(this.snappingSize * 0.0625f);
        this.paint.setColor(ViewCompat.MEASURED_STATE_MASK);
        canvas.drawColor(ViewCompat.MEASURED_STATE_MASK);
        this.paint.setAntiAlias(false);
        this.paint.setColor(-13619152);
        int width = getMaxWidth();
        int height = getMaxHeight();
        for (int i = 0; i < width; i += this.snappingSize) {
            canvas.drawLine(i, 0.0f, i, height, this.paint);
            canvas.drawLine(0.0f, i, width, i, this.paint);
        }
        float cx = Mathf.roundTo(width * 0.5f, this.snappingSize);
        float cy = Mathf.roundTo(height * 0.5f, this.snappingSize);
        this.paint.setColor(-12434878);
        for (int i2 = 0; i2 < width; i2 += this.snappingSize * 2) {
            canvas.drawLine(cx, i2, cx, this.snappingSize + i2, this.paint);
            canvas.drawLine(i2, cy, this.snappingSize + i2, cy, this.paint);
        }
        this.paint.setAntiAlias(true);
    }

    private void drawCursor(Canvas canvas) {
        this.paint.setStyle(Paint.Style.FILL);
        this.paint.setStrokeWidth(this.snappingSize * 0.0625f);
        this.paint.setColor(-3790808);
        this.paint.setAntiAlias(false);
        canvas.drawLine(0.0f, this.cursor.y, getMaxWidth(), this.cursor.y, this.paint);
        canvas.drawLine(this.cursor.x, 0.0f, this.cursor.x, getMaxHeight(), this.paint);
        this.paint.setAntiAlias(true);
    }

    public boolean isEditMode() { return this.editMode; }
    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        if (editMode) this.cursor.set(getWidth() / 2, getHeight() / 2);
        else selectElement(null);
        invalidate();
    }

    public ControlsProfile getProfile() { return this.profile; }
    public void setProfile(ControlsProfile profile) {
        this.profile = profile;
        if (profile != null) {
            this.profile.loadElements(this);
            deselectAllElements();
        }
        invalidate();
    }

    public XServer getXServer() { return this.xServer; }
    public void setXServer(XServer xServer) { this.xServer = xServer; createMouseMoveTimer(); }

    public TouchpadView getTouchpadView() { return this.touchpadView; }
    public void setTouchpadView(TouchpadView touchpadView) { this.touchpadView = touchpadView; }

    public boolean isShowTouchscreenControls() { return this.showTouchscreenControls; }
    public void setShowTouchscreenControls(boolean showTouchscreenControls) {
        this.showTouchscreenControls = showTouchscreenControls;
        invalidate();
    }

    public int getSnappingSize() { return this.snappingSize; }
    public Path getPath() { return this.path; }
    public Paint getPaint() { return this.paint; }
    public ColorFilter getColorFilter() { return this.colorFilter; }

    public ControlElement getSelectedElement() { return this.selectedElement; }
    public void selectElement(ControlElement element) {
        deselectAllElements();
        if (element != null) {
            this.selectedElement = element;
            this.selectedElement.setSelected(true);
        }
        invalidate();
    }

    private synchronized void deselectAllElements() {
        this.selectedElement = null;
        if (this.profile != null) {
            for (ControlElement element : this.profile.getElements()) element.setSelected(false);
        }
    }

    public int getMaxWidth() { return (int) Mathf.roundTo(getWidth(), this.snappingSize); }
    public int getMaxHeight() { return (int) Mathf.roundTo(getHeight(), this.snappingSize); }

    @Override
    protected void onDetachedFromWindow() {
        if (this.mouseMoveTimer != null) this.mouseMoveTimer.cancel();
        super.onDetachedFromWindow();
    }

    private void createMouseMoveTimer() {
        final WinHandler winHandler = this.xServer.getWinHandler();
        if (this.mouseMoveTimer == null && this.profile != null) {
            final float cursorSpeed = this.profile.getCursorSpeed();
            this.mouseMoveTimer = new Timer();
            this.mouseMoveTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (mouseMoveOffset.x != 0 || mouseMoveOffset.y != 0) {
                        if (xServer.isRelativeMouseMovement()) {
                            winHandler.mouseEvent(1, (int)(mouseMoveOffset.x * cursorSpeed * 10.0f), (int)(mouseMoveOffset.y * cursorSpeed * 10.0f), 0);
                        } else {
                            xServer.injectPointerMoveDelta((int)(mouseMoveOffset.x * cursorSpeed * 10.0f), (int)(mouseMoveOffset.y * cursorSpeed * 10.0f));
                        }
                    }
                }
            }, 0, 16);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus && showTouchscreenControls) {
            setVisibility(View.VISIBLE);
            resetTouchscreenTimeout();
        }
    }

    private void resetTouchscreenTimeout() {
        if (this.timeoutHandler != null && this.hideControlsRunnable != null) {
            this.timeoutHandler.removeCallbacks(this.hideControlsRunnable);
            this.timeoutHandler.postDelayed(this.hideControlsRunnable, 5000L);
        }
    }

    public void handleInputEvent(Binding binding, boolean isActionDown) {
        handleInputEvent(null, binding, isActionDown, 0.0f);
    }

    public void handleInputEvent(ExternalController controller, Binding binding, boolean isActionDown) {
        handleInputEvent(controller, binding, isActionDown, 0.0f);
    }

    public void handleStickInput(Binding firstBinding, float deltaX, float deltaY) {
        if (firstBinding.isGamepad()) {
            GamepadState state = this.profile.getGamepadState();
            WinHandler winHandler = this.xServer != null ? this.xServer.getWinHandler() : null;
            boolean isLeftStick = firstBinding == Binding.GAMEPAD_LEFT_THUMB_UP || firstBinding == Binding.GAMEPAD_LEFT_THUMB_DOWN || firstBinding == Binding.GAMEPAD_LEFT_THUMB_LEFT || firstBinding == Binding.GAMEPAD_LEFT_THUMB_RIGHT;
            if (isLeftStick) {
                state.thumbLX = deltaX;
                state.thumbLY = deltaY;
            } else {
                state.thumbRX = deltaX;
                state.thumbRY = deltaY;
            }
            if (winHandler != null) winHandler.sendGamepadState();
        }
    }

    public void handleInputEvent(Binding binding, boolean isActionDown, float offset) {
        handleInputEvent(null, binding, isActionDown, offset);
    }

    public void handleInputEvent(ExternalController controller, Binding binding, boolean isActionDown, float offset) {
        handleInputEvent(controller, binding, isActionDown, offset, true);
    }

    public void handleInputEvent(ExternalController controller, Binding binding, boolean isActionDown, float offset, boolean sendUpdate) {
        WinHandler winHandler = this.xServer != null ? this.xServer.getWinHandler() : null;
        if (binding.isGamepad()) {
            GamepadState state = controller != null ? controller.remappedState : this.profile.getGamepadState();
            int buttonIdx = binding.ordinal() - Binding.GAMEPAD_BUTTON_A.ordinal();
            if (buttonIdx <= 11) {
                if (buttonIdx == 10)
                    state.triggerL = isActionDown ? offset != 0.0f ? offset : 1.0f : 0.0f;
                else if (buttonIdx == 11)
                    state.triggerR = isActionDown ? offset != 0.0f ? offset : 1.0f : 0.0f;
                else
                    state.setPressed(buttonIdx, isActionDown);
            } else if (binding == Binding.GAMEPAD_LEFT_THUMB_UP || binding == Binding.GAMEPAD_LEFT_THUMB_DOWN) {
                float val = (isActionDown && offset == 0.0f) ? 1.0f : Math.abs(offset);
                state.thumbLY = isActionDown ? (binding == Binding.GAMEPAD_LEFT_THUMB_UP ? -val : val) : 0.0f;
            } else if (binding == Binding.GAMEPAD_LEFT_THUMB_LEFT || binding == Binding.GAMEPAD_LEFT_THUMB_RIGHT) {
                float val2 = (isActionDown && offset == 0.0f) ? 1.0f : Math.abs(offset);
                state.thumbLX = isActionDown ? (binding == Binding.GAMEPAD_LEFT_THUMB_LEFT ? -val2 : val2) : 0.0f;
            } else if (binding == Binding.GAMEPAD_RIGHT_THUMB_UP || binding == Binding.GAMEPAD_RIGHT_THUMB_DOWN) {
                float val3 = (isActionDown && offset == 0.0f) ? 1.0f : Math.abs(offset);
                state.thumbRY = isActionDown ? (binding == Binding.GAMEPAD_RIGHT_THUMB_UP ? -val3 : val3) : 0.0f;
            } else if (binding == Binding.GAMEPAD_RIGHT_THUMB_LEFT || binding == Binding.GAMEPAD_RIGHT_THUMB_RIGHT) {
                float val4 = (isActionDown && offset == 0.0f) ? 1.0f : Math.abs(offset);
                state.thumbRX = isActionDown ? (binding == Binding.GAMEPAD_RIGHT_THUMB_LEFT ? -val4 : val4) : 0.0f;
            } else if (binding == Binding.GAMEPAD_DPAD_UP || binding == Binding.GAMEPAD_DPAD_RIGHT || binding == Binding.GAMEPAD_DPAD_DOWN || binding == Binding.GAMEPAD_DPAD_LEFT) {
                state.dpad[binding.ordinal() - Binding.GAMEPAD_DPAD_UP.ordinal()] = isActionDown;
            }
            if (winHandler != null && sendUpdate) {
                if (controller != null) winHandler.sendGamepadState(controller);
                else winHandler.sendGamepadState();
            }
            return;
        }
        if (binding == Binding.MOUSE_MOVE_LEFT || binding == Binding.MOUSE_MOVE_RIGHT) {
            if (isActionDown) mouseMoveOffset.x = offset != 0.0f ? offset : (binding == Binding.MOUSE_MOVE_LEFT ? -1 : 1);
            else mouseMoveOffset.x = 0;
            if (isActionDown) createMouseMoveTimer();
            return;
        }
        if (binding == Binding.MOUSE_MOVE_DOWN || binding == Binding.MOUSE_MOVE_UP) {
            if (isActionDown) mouseMoveOffset.y = offset != 0.0f ? offset : (binding == Binding.MOUSE_MOVE_UP ? -1 : 1);
            else mouseMoveOffset.y = 0;
            if (isActionDown) createMouseMoveTimer();
            return;
        }
        Pointer.Button pointerButton = binding.getPointerButton();
        if (isActionDown) {
            if (pointerButton != null) {
                if (this.xServer.isRelativeMouseMovement()) {
                    int wheelDelta = pointerButton == Pointer.Button.BUTTON_SCROLL_UP ? 120 : (pointerButton == Pointer.Button.BUTTON_SCROLL_DOWN ? -120 : 0);
                    winHandler.mouseEvent(MouseEventFlags.getFlagFor(pointerButton, true), 0, 0, wheelDelta);
                } else this.xServer.injectPointerButtonPress(pointerButton);
            } else this.xServer.injectKeyPress(binding.keycode);
        } else {
            if (pointerButton != null) {
                if (this.xServer.isRelativeMouseMovement()) winHandler.mouseEvent(MouseEventFlags.getFlagFor(pointerButton, false), 0, 0, 0);
                else this.xServer.injectPointerButtonRelease(pointerButton);
            } else this.xServer.injectKeyRelease(binding.keycode);
        }
    }

    public Bitmap getIcon(byte id) {
        if (this.icons[id] == null) {
            Context context = getContext();
            try (InputStream is = context.getAssets().open("inputcontrols/icons/" + ((int) id) + ".png")) {
                this.icons[id] = BitmapFactory.decodeStream(is);
            } catch (IOException e) {}
        }
        return this.icons[id];
    }
}
