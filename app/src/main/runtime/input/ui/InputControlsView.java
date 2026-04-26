package com.winlator.cmod.runtime.input.ui;

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
import android.os.VibratorManager;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.util.HashMap;
import androidx.preference.PreferenceManager;
import com.winlator.cmod.R;
import com.winlator.cmod.runtime.display.winhandler.MouseEventFlags;
import com.winlator.cmod.runtime.display.winhandler.WinHandler;
import com.winlator.cmod.runtime.display.xserver.Pointer;
import com.winlator.cmod.runtime.display.xserver.XServer;
import com.winlator.cmod.runtime.input.controls.Binding;
import com.winlator.cmod.runtime.input.controls.ControlElement;
import com.winlator.cmod.runtime.input.controls.ControlsProfile;
import com.winlator.cmod.runtime.input.controls.ExternalController;
import com.winlator.cmod.runtime.input.controls.ExternalControllerBinding;
import com.winlator.cmod.runtime.input.controls.GamepadState;
import com.winlator.cmod.shared.math.Mathf;
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

public class InputControlsView extends View {
  public static final float DEFAULT_OVERLAY_OPACITY = 0.4f;
  private static final byte MOUSE_WHEEL_DELTA = 120;
  private boolean editMode = false;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path path = new Path();
  private final ColorFilter colorFilter =
      new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.SRC_IN);
  private final Point cursor = new Point();
  private boolean readyToDraw = false;
  private boolean moveCursor = false;
  private int snappingSize;
  private float offsetX;
  private float offsetY;
  private ControlElement selectedElement;
  private ControlsProfile profile;
  private float overlayOpacity = DEFAULT_OVERLAY_OPACITY;
  private TouchpadView touchpadView;
  private XServer xServer;
  private final Bitmap[] icons = new Bitmap[17];
  private Timer mouseMoveTimer;
  private volatile float mouseMoveOffsetX = 0f;
  private volatile float mouseMoveOffsetY = 0f;
  private boolean showTouchscreenControls = false;

  private Handler timeoutHandler; // Reference to the activity's timeout handler
  private Runnable hideControlsRunnable; // Runnable to hide the controls

  private SharedPreferences preferences;
  private final SparseArray<ControlElement> activeTouchElements = new SparseArray<>();

  private ControlElement stickElement;

  private boolean focusOnStick = false; // A flag to determine if we are focusing on the stick

  public boolean isFocusedOnStick() {
    return focusOnStick;
  }

  public void setFocusOnStick(boolean focus) {
    this.focusOnStick = focus;
    invalidate(); // Redraw the view with the new focus setting
  }

  @SuppressLint("ResourceType")
  public InputControlsView(Context context) {
    super(context);
    setClickable(true);
    setFocusable(true);
    setFocusableInTouchMode(true);
    requestFocus(); // Add this line to request focus
    setBackgroundColor(0x00000000);
    setPointerIcon(PointerIcon.load(getResources(), R.drawable.hidden_pointer_arrow));
    setLayoutParams(
        new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    preferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
  }

  @SuppressLint("ResourceType")
  public InputControlsView(Context context, Handler timeoutHandler, Runnable hideControlsRunnable) {
    super(context);
    this.timeoutHandler = timeoutHandler; // Store the reference to timeout handler
    this.hideControlsRunnable =
        hideControlsRunnable; // Store the reference to the hide controls runnable
    setClickable(true);
    setFocusable(true);
    setFocusableInTouchMode(true);
    requestFocus(); // Add this line to request focus
    setBackgroundColor(0x00000000);
    setPointerIcon(PointerIcon.load(getResources(), R.drawable.hidden_pointer_arrow));
    setLayoutParams(
        new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    preferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
  }

  public InputControlsView(Context context, boolean focusOnStick) {
    super(context);
    setClickable(true);
    setFocusable(true);
    setFocusableInTouchMode(true);
    requestFocus(); // Add this line to request focus
    setBackgroundColor(0x00000000);
    setPointerIcon(PointerIcon.load(getResources(), R.drawable.hidden_pointer_arrow));

    // If focusOnStick is true, adjust the layout params to match the stick element size
    if (focusOnStick) {
      setLayoutParams(
          new FrameLayout.LayoutParams(
              ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    } else {
      setLayoutParams(
          new FrameLayout.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    preferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
  }

  public void setEditMode(boolean editMode) {
    this.editMode = editMode;
  }

  public void setOverlayOpacity(float overlayOpacity) {
    this.overlayOpacity = overlayOpacity;
  }

  public int getSnappingSize() {
    return snappingSize;
  }

  @Override
  protected synchronized void onDraw(Canvas canvas) {
    int width, height;

    if (stickElement != null && isFocusedOnStick()) {
      // If focusing on the stick, set width and height to the stick's bounding box size
      Rect boundingBox = stickElement.getBoundingBox();
      width = boundingBox.width();
      height = boundingBox.height();
    } else {
      // Default behavior for full screen
      width = getWidth();
      height = getHeight();
    }

    if (width == 0 || height == 0) {
      readyToDraw = false;
      return;
    }

    snappingSize = width / 100;
    readyToDraw = true;

    if (editMode) {
      drawGrid(canvas);
      drawCursor(canvas);
    }

    if (stickElement != null) {
      // Draw only the stick element if focus mode is active
      stickElement.draw(canvas);
    }

    if (profile != null && (showTouchscreenControls || editMode) && !isFocusedOnStick()) {
      if (!profile.isElementsLoaded()) profile.loadElements(this);
      for (ControlElement element : profile.getElements()) {
        element.draw(canvas);
      }
    }

    super.onDraw(canvas);
  }

  public void resetStickPosition() {
    if (stickElement != null) {
      Rect boundingBox = stickElement.getBoundingBox();
      float centerX = boundingBox.centerX();
      float centerY = boundingBox.centerY();

      stickElement.setCurrentPosition(centerX, centerY); // Reset to the center of the bounding box
      invalidate(); // Redraw the stick in the centered position
    }
  }

  public void initializeStickElement(float x, float y, float scale) {
    stickElement = new ControlElement(this);
    stickElement.setType(ControlElement.Type.STICK); // Set type to STICK
    stickElement.setX((int) x);
    stickElement.setY((int) y);
    stickElement.setScale(scale);
    invalidate(); // Force the view to redraw with the stick
  }

  public void updateStickPosition(float x, float y) {
    if (stickElement != null) {
      stickElement.getCurrentPosition().x = x; // Update the thumbstick's position
      stickElement.getCurrentPosition().y = y; // Update the thumbstick's position
      invalidate(); // Redraw the view
    }
  }

  public ControlElement getStickElement() {
    return stickElement;
  }

  private void drawGrid(Canvas canvas) {
    paint.setStyle(Paint.Style.FILL);
    paint.setStrokeWidth(snappingSize * 0.0625f);

    // Background color from theme
    int bgColor = 0xFF060C23;
    canvas.drawColor(bgColor);

    paint.setAntiAlias(false);
    // Grid line color from theme (outline)
    int gridColor =
        androidx.core.content.ContextCompat.getColor(getContext(), R.color.settings_outline);
    paint.setColor(gridColor);

    int width = getMaxWidth();
    int height = getMaxHeight();

    for (int i = 0; i < width; i += snappingSize) {
      canvas.drawLine(i, 0, i, height, paint);
      canvas.drawLine(0, i, width, i, paint);
    }

    float cx = Mathf.roundTo(width * 0.5f, snappingSize);
    float cy = Mathf.roundTo(height * 0.5f, snappingSize);

    // Snapping center line color
    int snappingColor =
        androidx.core.content.ContextCompat.getColor(
            getContext(), R.color.settings_popup_surface_edge);
    paint.setColor(snappingColor);

    for (int i = 0; i < width; i += snappingSize * 2) {
      canvas.drawLine(cx, i, cx, i + snappingSize, paint);
      canvas.drawLine(i, cy, i + snappingSize, cy, paint);
    }

    paint.setAntiAlias(true);
  }

  private void drawCursor(Canvas canvas) {
    paint.setStyle(Paint.Style.FILL);
    paint.setStrokeWidth(snappingSize * 0.0625f);
    paint.setColor(0xffc62828);

    paint.setAntiAlias(false);
    canvas.drawLine(0, cursor.y, getMaxWidth(), cursor.y, paint);
    canvas.drawLine(cursor.x, 0, cursor.x, getMaxHeight(), paint);

    paint.setAntiAlias(true);
  }

  public synchronized boolean addElement() {
    if (editMode && profile != null) {
      ControlElement element = new ControlElement(this);
      element.setX(cursor.x);
      element.setY(cursor.y);
      profile.addElement(element);
      profile.save();
      selectElement(element);
      return true;
    } else return false;
  }

  public synchronized boolean removeElement() {
    if (editMode && selectedElement != null && profile != null) {
      profile.removeElement(selectedElement);
      selectedElement = null;
      profile.save();
      invalidate();
      return true;
    } else return false;
  }

  public ControlElement getSelectedElement() {
    return selectedElement;
  }

  private synchronized void deselectAllElements() {
    selectedElement = null;
    if (profile != null) {
      for (ControlElement element : profile.getElements()) element.setSelected(false);
    }
  }

  private void selectElement(ControlElement element) {
    deselectAllElements();
    if (element != null) {
      selectedElement = element;
      selectedElement.setSelected(true);
    }
    invalidate();
  }

  public synchronized ControlsProfile getProfile() {
    return profile;
  }

  public synchronized void setProfile(ControlsProfile profile) {
    releaseActiveTouchElements();
    if (profile != null) {
      this.profile = profile;
      deselectAllElements();
    } else this.profile = null;
    activeTouchElements.clear();
  }

  public boolean isShowTouchscreenControls() {
    return showTouchscreenControls;
  }

  public void setShowTouchscreenControls(boolean showTouchscreenControls) {
    if (this.showTouchscreenControls == showTouchscreenControls) {
      return;
    }
    if (!showTouchscreenControls) {
      releaseActiveTouchElements();
    }
    this.showTouchscreenControls = showTouchscreenControls;
    invalidate();
  }

  private void dispatchUnhandledTouch(MotionEvent event) {
    if (touchpadView != null) {
      touchpadView.onTouchEvent(event);
    }
  }

  public int getPrimaryColor() {
    return Color.argb((int) (overlayOpacity * 255), 255, 255, 255);
  }

  public int getSecondaryColor() {
    return Color.argb((int) (overlayOpacity * 255), 2, 119, 189);
  }

  private synchronized ControlElement intersectElement(float x, float y) {
    if (profile != null) {
      for (ControlElement element : profile.getElements()) {
        if (element.containsPoint(x, y)) return element;
      }
    }
    return null;
  }

  public Paint getPaint() {
    return paint;
  }

  public Path getPath() {
    return path;
  }

  public ColorFilter getColorFilter() {
    return colorFilter;
  }

  public TouchpadView getTouchpadView() {
    return touchpadView;
  }

  public void setTouchpadView(TouchpadView touchpadView) {
    this.touchpadView = touchpadView;
  }

  public XServer getXServer() {
    return xServer;
  }

  public void setXServer(XServer xServer) {
    this.xServer = xServer;
    createMouseMoveTimer();
  }

  public boolean hasMouseLeftButtonElement() {
    if (profile == null) return false;
    for (ControlElement element : profile.getElements()) {
      if (element.getBindingAt(0) == Binding.MOUSE_LEFT_BUTTON) return true;
    }
    return false;
  }

  private void releaseActiveTouchElements() {
    for (int i = 0; i < activeTouchElements.size(); i++) {
      int activePointerId = activeTouchElements.keyAt(i);
      ControlElement activeElement = activeTouchElements.valueAt(i);
      if (activeElement != null) {
        activeElement.handleTouchUp(activePointerId);
      }
    }
    activeTouchElements.clear();
  }

  public synchronized void cancelActiveTouches() {
    releaseActiveTouchElements();
  }

  public int getMaxWidth() {
    return (int) Mathf.roundTo(getWidth(), snappingSize);
  }

  @Override
  protected void onDetachedFromWindow() {
    if (mouseMoveTimer != null) mouseMoveTimer.cancel();
    super.onDetachedFromWindow();
  }

  public int getMaxHeight() {
    return (int) Mathf.roundTo(getHeight(), snappingSize);
  }

  private void createMouseMoveTimer() {
    WinHandler winHandler = xServer.getWinHandler();
    if (mouseMoveTimer == null && profile != null) {
      final float cursorSpeed = profile.getCursorSpeed();
      mouseMoveTimer = new Timer();
      mouseMoveTimer.schedule(
          new TimerTask() {
            @Override
            public void run() {
              if (mouseMoveOffsetX != 0 || mouseMoveOffsetY != 0) {
                if (xServer.isRelativeMouseMovement())
                  winHandler.mouseEvent(
                      MouseEventFlags.MOVE,
                      (int) (mouseMoveOffsetX * cursorSpeed * 20),
                      (int) (mouseMoveOffsetY * cursorSpeed * 20),
                      0);
                else
                  xServer.injectPointerMoveDelta(
                      (int) (mouseMoveOffsetX * cursorSpeed * 20),
                      (int) (mouseMoveOffsetY * cursorSpeed * 20));
              }
            }
          },
          0,
          1000 / 60); // 60 FPS
    }
  }

  //    private void processJoystickInput(ExternalController controller) {
  //        ExternalControllerBinding controllerBinding;
  //        final int[] axes = {MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z,
  // MotionEvent.AXIS_RZ, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y};
  //        final float[] values = {controller.state.thumbLX, controller.state.thumbLY,
  // controller.state.thumbRX, controller.state.thumbRY, controller.state.getDPadX(),
  // controller.state.getDPadY()};
  //
  //        for (byte i = 0; i < axes.length; i++) {
  //            if (Math.abs(values[i]) > ControlElement.STICK_DEAD_ZONE) {
  //                controllerBinding =
  // controller.getControllerBinding(ExternalControllerBinding.getKeyCodeForAxis(axes[i],
  // Mathf.sign(values[i])));
  //                if (controllerBinding != null) handleInputEvent(controllerBinding.getBinding(),
  // true, values[i]);
  //            }
  //            else {
  //                controllerBinding =
  // controller.getControllerBinding(ExternalControllerBinding.getKeyCodeForAxis(axes[i], (byte)
  // 1));
  //                if (controllerBinding != null) handleInputEvent(controllerBinding.getBinding(),
  // false, values[i]);
  //                controllerBinding =
  // controller.getControllerBinding(ExternalControllerBinding.getKeyCodeForAxis(axes[i],
  // (byte)-1));
  //                if (controllerBinding != null) handleInputEvent(controllerBinding.getBinding(),
  // false, values[i]);
  //            }
  //        }
  //    }

  private final HashMap<String, int[]> lastAxisSign = new HashMap<>();

  private void processJoystickInput(ExternalController controller) {
    final int[] axes = {
      MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
      MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
      MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y
    };
    final float[] values = {
      controller.state.thumbLX,
      controller.state.thumbLY,
      controller.state.thumbRX,
      controller.state.thumbRY,
      controller.state.getDPadX(),
      controller.state.getDPadY()
    };

    int[] lastSigns = lastAxisSign.get(controller.getId());
    if (lastSigns == null) {
      lastSigns = new int[axes.length];
      lastAxisSign.put(controller.getId(), lastSigns);
    }

    for (int i = 0; i < axes.length; i++) {
      float value = values[i];
      int currentSign = Math.abs(value) > ControlElement.STICK_DEAD_ZONE ? (int) Mathf.sign(value) : 0;
      int lastSign = lastSigns[i];

      if (currentSign != lastSign) {
        if (lastSign != 0) {
          int oldKeyCode = ExternalControllerBinding.getKeyCodeForAxis(axes[i], (byte) lastSign);
          ExternalControllerBinding oldBinding = controller.getControllerBinding(oldKeyCode);
          if (oldBinding != null) {
            handleInputEvent(controller, oldBinding.getBinding(), false, 0, false);
          }
        }

        if (currentSign != 0) {
          int newKeyCode = ExternalControllerBinding.getKeyCodeForAxis(axes[i], (byte) currentSign);
          ExternalControllerBinding newBinding = controller.getControllerBinding(newKeyCode);
          if (newBinding != null) {
            handleInputEvent(controller, newBinding.getBinding(), true, value, false);
          }
        }
        lastSigns[i] = currentSign;
      } else if (currentSign != 0) {
        // Sign hasn't changed, but deflection might have.
        // If it's a mouse move binding, we MUST update the offset for speed.
        int keyCode = ExternalControllerBinding.getKeyCodeForAxis(axes[i], (byte) currentSign);
        ExternalControllerBinding binding = controller.getControllerBinding(keyCode);
        if (binding != null && binding.getBinding().isMouseMove()) {
          handleInputEvent(controller, binding.getBinding(), true, value, false);
        }
      }
    }

    processTriggerInput(controller, controller.state.triggerL, KeyEvent.KEYCODE_BUTTON_L2, false);
    processTriggerInput(controller, controller.state.triggerR, KeyEvent.KEYCODE_BUTTON_R2, false);

    WinHandler winHandler = xServer != null ? xServer.getWinHandler() : null;
    if (winHandler != null) {
      winHandler.sendGamepadState(controller);
    }
  }

  private void processTriggerInput(
      ExternalController controller, float value, int keyCode, boolean sendUpdate) {
    ExternalControllerBinding binding = controller.getControllerBinding(keyCode);
    if (binding != null) {
      boolean isPressed = value > ControlElement.STICK_DEAD_ZONE;
      if (isPressed) {
        handleInputEvent(controller, binding.getBinding(), true, value, sendUpdate);
      } else {
        handleInputEvent(controller, binding.getBinding(), false, 0, sendUpdate);
      }
    }
  }

  //    @Override
  //    public boolean onGenericMotionEvent(MotionEvent event) {
  //        if (!editMode && profile != null) {
  //            ExternalController controller = profile.getController(event.getDeviceId());
  //            if (controller != null && controller.updateStateFromMotionEvent(event)) {
  //                ExternalControllerBinding controllerBinding;
  //                controllerBinding = controller.getControllerBinding(KeyEvent.KEYCODE_BUTTON_L2);
  //                if (controllerBinding != null) handleInputEvent(controllerBinding.getBinding(),
  // controller.state.isPressed(ExternalController.IDX_BUTTON_L2));
  //
  //                controllerBinding = controller.getControllerBinding(KeyEvent.KEYCODE_BUTTON_R2);
  //                if (controllerBinding != null) handleInputEvent(controllerBinding.getBinding(),
  // controller.state.isPressed(ExternalController.IDX_BUTTON_R2));
  //
  //                processJoystickInput(controller);
  //                return true;
  //            }
  //        }
  //        return super.onGenericMotionEvent(event);
  //    }

  @Override
  public boolean dispatchGenericMotionEvent(MotionEvent event) {
    return super.dispatchGenericMotionEvent(event);
  }

  @Override
  public boolean onGenericMotionEvent(MotionEvent event) {
    if (!editMode && profile != null) {
      ExternalController controller = profile.getController(event.getDeviceId());

      if (controller != null) {
        controller.updateStateFromMotionEvent(event);
        ExternalControllerBinding controllerBinding;

        controllerBinding = controller.getControllerBinding(KeyEvent.KEYCODE_BUTTON_L2);
        if (controllerBinding != null) {
          handleInputEvent(
              controller,
              controllerBinding.getBinding(),
              controller.state.isPressed(ExternalController.IDX_BUTTON_L2));
        }

        controllerBinding = controller.getControllerBinding(KeyEvent.KEYCODE_BUTTON_R2);
        if (controllerBinding != null) {
          handleInputEvent(
              controller,
              controllerBinding.getBinding(),
              controller.state.isPressed(ExternalController.IDX_BUTTON_R2));
        }

        processJoystickInput(controller);
        return true;
      }
    }
    return super.onGenericMotionEvent(event);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {

    boolean hapticsEnabled = preferences.getBoolean("touchscreen_haptics_enabled", true);

    if (editMode && readyToDraw) {
      switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
          {
            float x = event.getX();
            float y = event.getY();

            ControlElement element = intersectElement(x, y);
            moveCursor = true;
            if (element != null) {
              offsetX = x - element.getX();
              offsetY = y - element.getY();
              moveCursor = false;
            }

            selectElement(element);
            break;
          }
        case MotionEvent.ACTION_MOVE:
          {
            if (selectedElement != null) {
              selectedElement.setX((int) Mathf.roundTo(event.getX() - offsetX, snappingSize));
              selectedElement.setY((int) Mathf.roundTo(event.getY() - offsetY, snappingSize));
              invalidate();
            }
            break;
          }
        case MotionEvent.ACTION_UP:
          {
            if (selectedElement != null && profile != null) profile.save();
            if (moveCursor)
              cursor.set(
                  (int) Mathf.roundTo(event.getX(), snappingSize),
                  (int) Mathf.roundTo(event.getY(), snappingSize));
            invalidate();
            break;
          }
      }
    }

    if (!editMode && profile != null) {
      if (!showTouchscreenControls) {
        dispatchUnhandledTouch(event);
        return true;
      }

      int actionIndex = event.getActionIndex();
      int pointerId = event.getPointerId(actionIndex);
      int actionMasked = event.getActionMasked();
      boolean handled = false;

      switch (actionMasked) {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_POINTER_DOWN:
          {
            float x = event.getX(actionIndex);
            float y = event.getY(actionIndex);

            touchpadView.setPointerButtonLeftEnabled(!hasMouseLeftButtonElement());
            for (ControlElement element : profile.getElements()) {
              if (element.handleTouchDown(pointerId, x, y)) {
                handled = true;
                activeTouchElements.put(pointerId, element);

                // Trigger haptic feedback for input controls
                if (hapticsEnabled) {
                  Vibrator vibrator;
                  if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    VibratorManager vibratorManager =
                        getContext().getSystemService(VibratorManager.class);
                    vibrator =
                        vibratorManager != null ? vibratorManager.getDefaultVibrator() : null;
                  } else {
                    vibrator = getContext().getSystemService(Vibrator.class);
                  }
                  if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                  }
                }
                break;
              }
            }
            if (!handled) dispatchUnhandledTouch(event);
            break;
          }
        case MotionEvent.ACTION_MOVE:
          {
            for (byte i = 0, count = (byte) event.getPointerCount(); i < count; i++) {
              int movePointerId = event.getPointerId(i);
              float x = event.getX(i);
              float y = event.getY(i);

              ControlElement activeElement = activeTouchElements.get(movePointerId);
              handled = activeElement != null && activeElement.handleTouchMove(movePointerId, x, y);

              if (!handled && activeElement == null) {
                for (ControlElement element : profile.getElements()) {
                  if (element.handleTouchMove(movePointerId, x, y)) {
                    activeTouchElements.put(movePointerId, element);
                    handled = true;
                    break;
                  }
                }
              }
              if (!handled) dispatchUnhandledTouch(event);
            }
            break;
          }
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_POINTER_UP:
          {
            ControlElement activeElement = activeTouchElements.get(pointerId);
            if (activeElement != null) {
              handled = activeElement.handleTouchUp(pointerId);
              activeTouchElements.remove(pointerId);
            } else {
              for (ControlElement element : profile.getElements()) {
                if (element.handleTouchUp(pointerId)) {
                  handled = true;
                  break;
                }
              }
            }
            if (!handled) dispatchUnhandledTouch(event);
            break;
          }
        case MotionEvent.ACTION_CANCEL:
          {
            releaseActiveTouchElements();
            dispatchUnhandledTouch(event);
            break;
          }
      }
    }
    return true;
  }

  public void invalidateControlElement(ControlElement element) {
    if (element == null) return;

    Rect dirtyRect = element.getBoundingBox();
    int padding = Math.max(getSnappingSize() * 4, 32);
    postInvalidateOnAnimation(
        dirtyRect.left - padding,
        dirtyRect.top - padding,
        dirtyRect.right + padding,
        dirtyRect.bottom + padding);
  }

  public boolean onKeyEvent(KeyEvent event) {
    if (profile != null && event.getRepeatCount() == 0) {
      ExternalController controller = profile.getController(event.getDeviceId());
      if (controller != null) {
        ExternalControllerBinding controllerBinding =
            controller.getControllerBinding(event.getKeyCode());
        if (controllerBinding != null) {
          int action = event.getAction();

          if (action == KeyEvent.ACTION_DOWN) {
            handleInputEvent(controller, controllerBinding.getBinding(), true);
          } else if (action == KeyEvent.ACTION_UP) {
            handleInputEvent(controller, controllerBinding.getBinding(), false);
          }
          return true;
        }
      }
    }
    return false;
  }

  public void handleInputEvent(Binding binding, boolean isActionDown) {
    handleInputEvent(null, binding, isActionDown, 0);
  }

  public void handleInputEvent(
      ExternalController controller, Binding binding, boolean isActionDown) {
    handleInputEvent(controller, binding, isActionDown, 0);
  }

  /**
   * Updates both stick axes together so analog motion is dispatched as one coherent state update
   * instead of four competing per-direction writes.
   */
  public void handleStickInput(Binding firstBinding, float deltaX, float deltaY) {
    if (profile == null || !firstBinding.isGamepad()) return;

    GamepadState state = profile.getGamepadState();
    WinHandler winHandler = xServer != null ? xServer.getWinHandler() : null;

    boolean isLeftStick =
        firstBinding == Binding.GAMEPAD_LEFT_THUMB_UP
            || firstBinding == Binding.GAMEPAD_LEFT_THUMB_DOWN
            || firstBinding == Binding.GAMEPAD_LEFT_THUMB_LEFT
            || firstBinding == Binding.GAMEPAD_LEFT_THUMB_RIGHT;

    if (isLeftStick) {
      state.thumbLX = deltaX;
      state.thumbLY = deltaY;
    } else {
      state.thumbRX = deltaX;
      state.thumbRY = deltaY;
    }

    if (winHandler != null) {
      winHandler.sendGamepadState();
    }
  }

  public void handleInputEvent(Binding binding, boolean isActionDown, float offset) {
    handleInputEvent(null, binding, isActionDown, offset);
  }

  public void handleInputEvent(
      ExternalController controller, Binding binding, boolean isActionDown, float offset) {
    handleInputEvent(controller, binding, isActionDown, offset, true);
  }

  public void handleInputEvent(
      ExternalController controller,
      Binding binding,
      boolean isActionDown,
      float offset,
      boolean sendUpdate) {
    WinHandler winHandler = xServer != null ? xServer.getWinHandler() : null;
    if (binding.isGamepad()) {
      if (profile == null && controller == null) return;
      GamepadState state =
          (controller != null) ? controller.remappedState : profile.getGamepadState();
      boolean stateChanged = false;

      int buttonIdx = binding.ordinal() - Binding.GAMEPAD_BUTTON_A.ordinal();
      if (buttonIdx <= ExternalController.IDX_BUTTON_R2) {
        if (buttonIdx == ExternalController.IDX_BUTTON_L2) {
          float value = isActionDown ? (offset != 0 ? offset : 1.0f) : 0f;
          stateChanged = Float.compare(state.triggerL, value) != 0;
          state.triggerL = value;
        } else if (buttonIdx == ExternalController.IDX_BUTTON_R2) {
          float value = isActionDown ? (offset != 0 ? offset : 1.0f) : 0f;
          stateChanged = Float.compare(state.triggerR, value) != 0;
          state.triggerR = value;
        } else {
          stateChanged = state.isPressed(buttonIdx) != isActionDown;
          if (stateChanged) state.setPressed(buttonIdx, isActionDown);
        }
      } else if (binding == Binding.GAMEPAD_LEFT_THUMB_UP
          || binding == Binding.GAMEPAD_LEFT_THUMB_DOWN) {
        float val = (isActionDown && offset == 0) ? 1.0f : Math.abs(offset);
        float value = isActionDown ? (binding == Binding.GAMEPAD_LEFT_THUMB_UP ? -val : val) : 0;
        stateChanged = Float.compare(state.thumbLY, value) != 0;
        state.thumbLY = value;
      } else if (binding == Binding.GAMEPAD_LEFT_THUMB_LEFT
          || binding == Binding.GAMEPAD_LEFT_THUMB_RIGHT) {
        float val = (isActionDown && offset == 0) ? 1.0f : Math.abs(offset);
        float value = isActionDown ? (binding == Binding.GAMEPAD_LEFT_THUMB_LEFT ? -val : val) : 0;
        stateChanged = Float.compare(state.thumbLX, value) != 0;
        state.thumbLX = value;
      } else if (binding == Binding.GAMEPAD_RIGHT_THUMB_UP
          || binding == Binding.GAMEPAD_RIGHT_THUMB_DOWN) {
        float val = (isActionDown && offset == 0) ? 1.0f : Math.abs(offset);
        float value = isActionDown ? (binding == Binding.GAMEPAD_RIGHT_THUMB_UP ? -val : val) : 0;
        stateChanged = Float.compare(state.thumbRY, value) != 0;
        state.thumbRY = value;
      } else if (binding == Binding.GAMEPAD_RIGHT_THUMB_LEFT
          || binding == Binding.GAMEPAD_RIGHT_THUMB_RIGHT) {
        float val = (isActionDown && offset == 0) ? 1.0f : Math.abs(offset);
        float value =
            isActionDown ? (binding == Binding.GAMEPAD_RIGHT_THUMB_LEFT ? -val : val) : 0;
        stateChanged = Float.compare(state.thumbRX, value) != 0;
        state.thumbRX = value;
      } else if (binding == Binding.GAMEPAD_DPAD_UP
          || binding == Binding.GAMEPAD_DPAD_RIGHT
          || binding == Binding.GAMEPAD_DPAD_DOWN
          || binding == Binding.GAMEPAD_DPAD_LEFT) {
        int dpadIndex = binding.ordinal() - Binding.GAMEPAD_DPAD_UP.ordinal();
        stateChanged = state.dpad[dpadIndex] != isActionDown;
        state.dpad[dpadIndex] = isActionDown;
      }

      if (winHandler != null && sendUpdate && stateChanged) {
        if (controller != null) winHandler.sendGamepadState(controller);
        else winHandler.sendGamepadState();
      }
    } else {
      if (binding == Binding.MOUSE_MOVE_LEFT || binding == Binding.MOUSE_MOVE_RIGHT) {
        mouseMoveOffsetX =
            isActionDown
                ? (offset != 0 ? offset : (binding == Binding.MOUSE_MOVE_LEFT ? -1 : 1))
                : 0;
        if (isActionDown) createMouseMoveTimer();
      } else if (binding == Binding.MOUSE_MOVE_DOWN || binding == Binding.MOUSE_MOVE_UP) {
        mouseMoveOffsetY =
            isActionDown ? (offset != 0 ? offset : (binding == Binding.MOUSE_MOVE_UP ? -1 : 1)) : 0;
        if (isActionDown) createMouseMoveTimer();
      } else {
        Pointer.Button pointerButton = binding.getPointerButton();
        if (isActionDown) {
          if (pointerButton != null) {
            if (xServer.isRelativeMouseMovement()) {
              int wheelDelta =
                  pointerButton == Pointer.Button.BUTTON_SCROLL_UP
                      ? MOUSE_WHEEL_DELTA
                      : (pointerButton == Pointer.Button.BUTTON_SCROLL_DOWN
                          ? -MOUSE_WHEEL_DELTA
                          : 0);
              winHandler.mouseEvent(
                  MouseEventFlags.getFlagFor(pointerButton, true), 0, 0, wheelDelta);
            } else {
              xServer.injectPointerButtonPress(pointerButton);
            }
          } else xServer.injectKeyPress(binding.keycode);
        } else {
          if (pointerButton != null) {
            if (xServer.isRelativeMouseMovement()) {
              winHandler.mouseEvent(MouseEventFlags.getFlagFor(pointerButton, false), 0, 0, 0);
            } else {
              xServer.injectPointerButtonRelease(pointerButton);
            }
          } else xServer.injectKeyRelease(binding.keycode);
        }
      }
    }
  }

  public Bitmap getIcon(byte id) {
    if (icons[id] == null) {
      Context context = getContext();
      try (InputStream is = context.getAssets().open("inputcontrols/icons/" + id + ".png")) {
        icons[id] = BitmapFactory.decodeStream(is);
      } catch (IOException e) {
      }
    }
    return icons[id];
  }
}
