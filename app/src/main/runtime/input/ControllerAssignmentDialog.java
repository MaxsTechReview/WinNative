package com.winlator.cmod.runtime.input;

import android.app.Activity;
import android.content.Context;
import android.view.InputDevice;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import com.winlator.cmod.R;
import com.winlator.cmod.runtime.display.winhandler.WinHandler;
import com.winlator.cmod.runtime.input.controls.ControllerManager;
import com.winlator.cmod.shared.ui.dialog.ContentDialog;

public class ControllerAssignmentDialog {
  private final ContentDialog dialog;
  private final ControllerManager controllerManager;
  private final WinHandler winHandler; // may be null
  private final Activity hostActivity;

  private final CheckBox[] checkBoxes = new CheckBox[4];
  private final TextView[] deviceNameTextViews = new TextView[4];
  private final Button[] assignButtons = new Button[4];
  private final Button[] btnMacros = new Button[4]; // <-- Button array
  private final CheckBox[] vibrateBoxes = new CheckBox[4];
  private final Button[] resetButtons = new Button[4];

  private final TextView restartRequiredView;
  private final int initialPlayerCount;

  // ---------- Public entry points -----------------------------------------

  /**
   * Legacy call (e.g., from UnifiedActivity). Will try to grab WinHandler if context is
   * XServerDisplayActivity.
   */
  public static void show(Context context) {
    show(context, null);
  }

  /** Preferred call when you already have the handler. */
  public static void show(Context context, WinHandler winHandler) {
    int initialPlayerCount = ControllerManager.getInstance().getEnabledPlayerCount();
    Activity act = (Activity) context; // all current callers pass an Activity
    new ControllerAssignmentDialog(act, initialPlayerCount, winHandler).showContentDialog();
  }

  private static WinHandler extractWinHandler(Context ctx) {
    return null;
  }

  // ---------- Impl ---------------------------------------------------------

  private ControllerAssignmentDialog(
      Activity activity, int initialPlayerCount, WinHandler winHandler) {
    this.dialog = new ContentDialog(activity, R.layout.controller_assignment_dialog);
    this.dialog.setTitle(R.string.session_gamepad_controller_manager);

    this.controllerManager = ControllerManager.getInstance();
    this.initialPlayerCount = initialPlayerCount;
    this.winHandler = winHandler; // can be null
    this.hostActivity = activity;

    initializeViews();

    restartRequiredView = dialog.getContentView().findViewById(R.id.TVRestartRequired);

    // Apply white text to the whole content
    View root = dialog.getContentView();
    if (root != null && root instanceof ViewGroup)
      setTextColorForDialog((ViewGroup) root, 0xFFFFFFFF);

    populateView();
    setupListeners();
  }

  private static int dp(Context c, int v) {
    return Math.round(c.getResources().getDisplayMetrics().density * v);
  }

  public void showContentDialog() {
    dialog.show();
    Window w = dialog.getWindow();
    if (w == null) return;

    int widthPx;
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
      android.view.WindowMetrics metrics = w.getWindowManager().getCurrentWindowMetrics();
      android.graphics.Rect b = metrics.getBounds();
      widthPx = b.width();
    } else {
      widthPx = dialog.getContext().getResources().getDisplayMetrics().widthPixels;
    }

    int capPx = dp(dialog.getContext(), 540);
    int target = Math.min((int) (widthPx * 0.90f), capPx);
    w.setLayout(target, WindowManager.LayoutParams.WRAP_CONTENT);
  }

  private void initializeViews() {
    View view = dialog.getContentView();

    // Player 1
    checkBoxes[0] = view.findViewById(R.id.CBPlayer1);
    deviceNameTextViews[0] = view.findViewById(R.id.TVPlayer1DeviceName);
    assignButtons[0] = view.findViewById(R.id.BTNAssignP1);
    vibrateBoxes[0] = view.findViewById(R.id.CBVibrateP1);
    resetButtons[0] = view.findViewById(R.id.BTNResetP1);
    btnMacros[0] = view.findViewById(R.id.BTNMacrosP1);
    btnMacros[0].setOnClickListener(v -> {});

    // Player 2
    checkBoxes[1] = view.findViewById(R.id.CBPlayer2);
    deviceNameTextViews[1] = view.findViewById(R.id.TVPlayer2DeviceName);
    assignButtons[1] = view.findViewById(R.id.BTNAssignP2);
    vibrateBoxes[1] = view.findViewById(R.id.CBVibrateP2);
    resetButtons[1] = view.findViewById(R.id.BTNResetP2);
    btnMacros[1] = view.findViewById(R.id.BTNMacrosP2);
    btnMacros[1].setOnClickListener(v -> {});

    // Player 3
    checkBoxes[2] = view.findViewById(R.id.CBPlayer3);
    deviceNameTextViews[2] = view.findViewById(R.id.TVPlayer3DeviceName);
    assignButtons[2] = view.findViewById(R.id.BTNAssignP3);
    vibrateBoxes[2] = view.findViewById(R.id.CBVibrateP3);
    resetButtons[2] = view.findViewById(R.id.BTNResetP3);
    btnMacros[2] = view.findViewById(R.id.BTNMacrosP3);
    btnMacros[2].setOnClickListener(v -> {});

    // Player 4
    checkBoxes[3] = view.findViewById(R.id.CBPlayer4);
    deviceNameTextViews[3] = view.findViewById(R.id.TVPlayer4DeviceName);
    assignButtons[3] = view.findViewById(R.id.BTNAssignP4);
    vibrateBoxes[3] = view.findViewById(R.id.CBVibrateP4);
    resetButtons[3] = view.findViewById(R.id.BTNResetP4);
    btnMacros[3] = view.findViewById(R.id.BTNMacrosP4);
    btnMacros[3].setOnClickListener(v -> {});
  }

  private void populateView() {
    controllerManager.scanForDevices();

    for (int i = 0; i < 4; i++) {
      checkBoxes[i].setChecked(controllerManager.isSlotEnabled(i));
      if (vibrateBoxes[i] != null) {
        vibrateBoxes[i].setChecked(controllerManager.isVibrationEnabled(i));
      }
      InputDevice device = controllerManager.getAssignedDeviceForSlot(i);
      deviceNameTextViews[i].setText(
          device != null
              ? device.getName()
              : dialog.getContext().getString(R.string.session_gamepad_not_assigned));
      deviceNameTextViews[i].setSelected(true);
    }
  }

  private void setupListeners() {
    for (int i = 0; i < 4; i++) {
      final int slotIndex = i;

      checkBoxes[i].setOnCheckedChangeListener(
          (buttonView, isChecked) -> {
            controllerManager.setSlotEnabled(slotIndex, isChecked);
            if (!isChecked) {
              for (int j = slotIndex + 1; j < 4; j++) {
                if (controllerManager.isSlotEnabled(j)) controllerManager.setSlotEnabled(j, false);
              }
            } else {
              for (int j = 0; j < slotIndex; j++) {
                if (!controllerManager.isSlotEnabled(j)) controllerManager.setSlotEnabled(j, true);
              }
            }
            populateView();

            if (controllerManager.getEnabledPlayerCount() != initialPlayerCount) {
              restartRequiredView.setVisibility(View.VISIBLE);
            } else {
              restartRequiredView.setVisibility(View.GONE);
            }
          });

      vibrateBoxes[i].setOnCheckedChangeListener(
          (b, checked) -> controllerManager.setVibrationEnabled(slotIndex, checked));

      resetButtons[i].setOnClickListener(
          v -> {
            controllerManager.unassignSlot(slotIndex);
            populateView();
          });

      assignButtons[i].setOnClickListener(
          v -> {
            String message =
                dialog.getContext().getString(R.string.session_gamepad_press_any_button_for_player)
                    + " "
                    + (slotIndex + 1);
            dialog.setMessage(message);

            dialog.setOnControllerInputListener(
                device -> {
                  if (!ControllerManager.isGameController(device)) return;
                  controllerManager.assignDeviceToSlot(slotIndex, device);
                  dialog.setMessage(null);
                  dialog.setOnControllerInputListener(null);
                  populateView();
                });
          });
    }

    dialog.setOnConfirmCallback(() -> controllerManager.saveAssignments());
  }

  private void setTextColorForDialog(ViewGroup viewGroup, int color) {
    for (int i = 0; i < viewGroup.getChildCount(); i++) {
      View child = viewGroup.getChildAt(i);
      if (child instanceof ViewGroup) {
        setTextColorForDialog((ViewGroup) child, color);
      } else if (child instanceof TextView || child instanceof Button) {
        ((TextView) child).setTextColor(color);
      }
    }
  }
}
