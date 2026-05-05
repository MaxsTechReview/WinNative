package com.winlator.cmod.runtime.input;

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.os.SystemClock;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import com.winlator.cmod.R;
import com.winlator.cmod.runtime.input.controls.Binding;
import com.winlator.cmod.runtime.input.controls.ControlElement;
import com.winlator.cmod.runtime.input.controls.ControlsProfile;
import com.winlator.cmod.runtime.input.controls.InputControlsManager;
import com.winlator.cmod.runtime.input.ui.InputControlsView;
import com.winlator.cmod.shared.android.AppUtils;
import com.winlator.cmod.shared.android.FixedFontScaleAppCompatActivity;
import com.winlator.cmod.shared.io.FileUtils;
import com.winlator.cmod.shared.math.Mathf;
import com.winlator.cmod.shared.ui.widget.NumberPicker;
import com.winlator.cmod.shared.util.UnitUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class ControlsEditorActivity extends FixedFontScaleAppCompatActivity implements View.OnClickListener {
  private InputControlsView inputControlsView;
  private ControlsProfile profile;
  private boolean blockingUpdate = false;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    AppUtils.hideSystemUI(this);
    setContentView(R.layout.controls_editor_activity);

    inputControlsView = new InputControlsView(this);
    inputControlsView.setEditMode(true);
    inputControlsView.setOverlayOpacity(0.6f);

    profile =
        InputControlsManager.loadProfile(
            this, ControlsProfile.getProfileFile(this, getIntent().getIntExtra("profile_id", 0)));
    ((TextView) findViewById(R.id.TVProfileName)).setText(profile.getName());
    inputControlsView.setProfile(profile);

    FrameLayout container = findViewById(R.id.FLContainer);
    container.addView(inputControlsView, 0);

    container.findViewById(R.id.BTAddElement).setOnClickListener(this);
    container.findViewById(R.id.BTRemoveElement).setOnClickListener(this);
    container.findViewById(R.id.BTElementSettings).setOnClickListener(this);
    container.findViewById(R.id.BTColorPicker).setOnClickListener(this);

    setupToolbarDragging();

    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                finish();
                AppUtils.applyCloseActivityTransition(
                    ControlsEditorActivity.this, R.anim.slide_in_down, R.anim.slide_out_up);
              }
            });
  }

  @Override
  public void onClick(View v) {
    switch (v.getId()) {
      case R.id.BTAddElement:
        if (!inputControlsView.addElement()) {
          AppUtils.showToast(this, R.string.input_controls_editor_no_profile_selected);
        }
        break;
      case R.id.BTRemoveElement:
        if (!inputControlsView.removeElement()) {
          AppUtils.showToast(this, R.string.input_controls_editor_no_element_selected);
        }
        break;
      case R.id.BTElementSettings:
        ControlElement selectedElement = inputControlsView.getSelectedElement();
        if (selectedElement != null) {
          showControlElementSettings(v);
        } else AppUtils.showToast(this, R.string.input_controls_editor_no_element_selected);
        break;
      case R.id.BTColorPicker:
        ControlElement selectedElementForColor = inputControlsView.getSelectedElement();
        if (selectedElementForColor != null) {
          showColorPicker(v);
        } else AppUtils.showToast(this, R.string.input_controls_editor_no_element_selected);
        break;
    }
  }

  private void showColorPicker(View anchorView) {
    final ControlElement element = inputControlsView.getSelectedElement();
    View view = LayoutInflater.from(this).inflate(R.layout.color_picker_popup, null);
    GridView gvColors = view.findViewById(R.id.GVColors);
    final EditText etHexColor = view.findViewById(R.id.ETHexColor);

    final int[] colors = {
      0xFFFFFFFF, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00,
      0xFF00FFFF, 0xFFFF00FF, 0xFFFFA500, 0xFF800080, 0xFF008000,
      0xFF008080, 0xFF000080, 0xFF800000, 0xFF808000, 0xFF808080,
      0xFFC4460C, 0xFF2B2928, 0xFF3B0878, 0xFF000000, 0xFF964B00
    };

    gvColors.setAdapter(
        new BaseAdapter() {
          @Override
          public int getCount() {
            return colors.length;
          }

          @Override
          public Object getItem(int position) {
            return colors[position];
          }

          @Override
          public long getItemId(int position) {
            return position;
          }

          @Override
          public View getView(int position, View convertView, ViewGroup parent) {
            View colorView = new View(parent.getContext());
            int size = (int) UnitUtils.dpToPx(28);
            colorView.setLayoutParams(new GridView.LayoutParams(size, size));
            colorView.setBackgroundColor(colors[position]);
            colorView.setOnClickListener(
                v -> {
                  etHexColor.setText(String.format("#%06X", (0xFFFFFF & colors[position])));
                  element.setCustomColor(colors[position]);
                  inputControlsView.invalidate();
                  profile.save();
                });
            return colorView;
          }
        });

    if (element.getCustomColor() != -1) {
      etHexColor.setText(String.format("#%06X", (0xFFFFFF & element.getCustomColor())));
    }

    final PopupWindow popupWindow = AppUtils.showPopupWindow(anchorView, view, 300, 0);
    popupWindow.setFocusable(true);
    popupWindow.update();

    view.findViewById(R.id.BTReset)
        .setOnClickListener(
            v -> {
              element.setCustomColor(-1);
              inputControlsView.invalidate();
              profile.save();
              popupWindow.dismiss();
            });

    view.findViewById(R.id.BTConfirm)
        .setOnClickListener(
            v -> {
              String hex = etHexColor.getText().toString().trim();
              if (!hex.isEmpty()) {
                try {
                  int color = Color.parseColor(hex.startsWith("#") ? hex : "#" + hex);
                  element.setCustomColor(color);
                  inputControlsView.invalidate();
                  profile.save();
                } catch (Exception e) {
                  AppUtils.showToast(this, "Invalid Color");
                }
              }
              popupWindow.dismiss();
            });
  }
private void showControlElementSettings(View anchorView) {
  final ControlElement element = inputControlsView.getSelectedElement();
  View view = LayoutInflater.from(this).inflate(R.layout.control_element_settings, null);

    final Runnable updateLayout =
        () -> {
          if (blockingUpdate) return;
          blockingUpdate = true;
          ControlElement.Type type = element.getType();
          view.findViewById(R.id.LLShape).setVisibility(View.GONE);
          view.findViewById(R.id.CBToggleSwitch).setVisibility(View.GONE);
          view.findViewById(R.id.LLCustomTextIcon).setVisibility(View.GONE);
          view.findViewById(R.id.LLRangeOptions).setVisibility(View.GONE);
          view.findViewById(R.id.LLRadialMenuOptions).setVisibility(View.GONE);

          if (type == ControlElement.Type.BUTTON || type == ControlElement.Type.RADIAL_MENU) {
            view.findViewById(R.id.LLCustomTextIcon).setVisibility(View.VISIBLE);
            if (type == ControlElement.Type.BUTTON) {
              view.findViewById(R.id.LLShape).setVisibility(View.VISIBLE);
              view.findViewById(R.id.CBToggleSwitch).setVisibility(View.VISIBLE);
            } else {
              view.findViewById(R.id.LLRadialMenuOptions).setVisibility(View.VISIBLE);
            }
          } else if (type == ControlElement.Type.RANGE_BUTTON) {
            view.findViewById(R.id.LLRangeOptions).setVisibility(View.VISIBLE);
          }

          loadBindingSpinners(element, view);
          blockingUpdate = false;
        };

    loadTypeSpinner(element, view.findViewById(R.id.SType), updateLayout);
    loadShapeSpinner(element, view.findViewById(R.id.SShape));
    loadRangeSpinner(element, view.findViewById(R.id.SRange));

    RadioGroup rgOrientation = view.findViewById(R.id.RGOrientation);
    rgOrientation.check(element.getOrientation() == 1 ? R.id.RBVertical : R.id.RBHorizontal);
    rgOrientation.setOnCheckedChangeListener(
        (group, checkedId) -> {
          element.setOrientation((byte) (checkedId == R.id.RBVertical ? 1 : 0));
          profile.save();
          inputControlsView.invalidate();
        });

    loadNumberSpinner(view.findViewById(R.id.SColumns), element.getBindingCount(), 3, 8, (value) -> {
      if (element.getType() == ControlElement.Type.RANGE_BUTTON && element.getBindingCount() != value) {
        element.setBindingCount(value);
        profile.save();
        inputControlsView.invalidate();
      }
    });

    loadNumberSpinner(view.findViewById(R.id.SBindingsCount), element.getBindingCount(), 3, 12, (value) -> {
      if (element.getType() == ControlElement.Type.RADIAL_MENU && element.getBindingCount() != value) {
        element.setBindingCount(value);
        profile.save();
        updateLayout.run();
        inputControlsView.invalidate();
      }
    });

    final TextView tvScale = view.findViewById(R.id.TVScale);
    SeekBar sbScale = view.findViewById(R.id.SBScale);
    sbScale.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            tvScale.setText(progress + "%");
            if (fromUser) {
              progress = (int) Mathf.roundTo(progress, 5);
              seekBar.setProgress(progress);
              element.setScale(progress / 100.0f);
              profile.save();
              inputControlsView.invalidate();
            }
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    sbScale.setProgress((int) (element.getScale() * 100));

    final TextView tvOpacity = view.findViewById(R.id.TVOpacity);
    SeekBar sbOpacity = view.findViewById(R.id.SBOpacity);
    sbOpacity.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            tvOpacity.setText(progress + "%");
            if (fromUser) {
              element.setOpacity(progress / 100.0f);
              profile.save();
              inputControlsView.invalidate();
            }
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    sbOpacity.setProgress((int) (element.getOpacity() * 100));
    tvOpacity.setText(sbOpacity.getProgress() + "%");

    CheckBox cbToggleSwitch = view.findViewById(R.id.CBToggleSwitch);
    cbToggleSwitch.setChecked(element.isToggleSwitch());
    cbToggleSwitch.setOnCheckedChangeListener(
        (buttonView, isChecked) -> {
          element.setToggleSwitch(isChecked);
          profile.save();
        });

    final EditText etCustomText = view.findViewById(R.id.ETCustomText);
    etCustomText.setText(element.getText());
    final LinearLayout llIconList = view.findViewById(R.id.LLIconList);
    loadIcons(llIconList, element.getIconId());

    updateLayout.run();
    blockingUpdate = false;

    PopupWindow popupWindow = AppUtils.showPopupWindow(anchorView, view, 340, 0);
    popupWindow.setOnDismissListener(
        () -> {
          String text = etCustomText.getText().toString().trim();
          byte iconId = 0;
          for (int i = 0; i < llIconList.getChildCount(); i++) {
            View child = llIconList.getChildAt(i);
            if (child.isSelected()) {
              iconId = (byte) child.getTag();
              break;
            }
          }

          element.setText(text);
          element.setIconId(iconId);
          profile.save();
          inputControlsView.invalidate();
        });
  }

  private void loadNumberSpinner(Spinner spinner, int currentValue, int min, int max, com.winlator.cmod.shared.util.Callback<Integer> callback) {
    java.util.ArrayList<String> items = new java.util.ArrayList<>();
    for (int i = min; i <= max; i++) items.add(String.valueOf(i));
    AppUtils.setupThemedSpinner(spinner, this, items);
    spinner.setSelection(Mathf.clamp(currentValue - min, 0, max - min), false);
    spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (!blockingUpdate) callback.call(position + min);
      }
      @Override
      public void onNothingSelected(AdapterView<?> parent) {}
    });
  }

  private void loadTypeSpinner(final ControlElement element, Spinner spinner, Runnable callback) {
    AppUtils.setupThemedSpinner(spinner, this, Arrays.asList(ControlElement.Type.names()));
    spinner.setSelection(element.getType().ordinal(), false);
    spinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (!blockingUpdate) {
              ControlElement.Type newType = ControlElement.Type.values()[position];
              if (newType == element.getType()) return;
              element.setType(newType);
              profile.save();
              callback.run();
              inputControlsView.invalidate();
            }
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {}
        });
  }

  private void loadShapeSpinner(final ControlElement element, Spinner spinner) {
    AppUtils.setupThemedSpinner(spinner, this, Arrays.asList(ControlElement.Shape.names()));
    spinner.setSelection(element.getShape().ordinal(), false);
    spinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (!blockingUpdate) {
              element.setShape(ControlElement.Shape.values()[position]);
              profile.save();
              inputControlsView.invalidate();
            }
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {}
        });
  }

  private void loadBindingSpinners(ControlElement element, View view) {
    LinearLayout container = view.findViewById(R.id.LLBindings);
    container.removeAllViews();

    ControlElement.Type type = element.getType();
    if (type == ControlElement.Type.BUTTON || type == ControlElement.Type.RADIAL_MENU) {
      int count = element.getBindingCount();
      for (int i = 0; i < count; i++) {
        String title = (type == ControlElement.Type.RADIAL_MENU) ? "Binding " + (i + 1) : (i == 0 ? getString(R.string.input_controls_editor_binding) : getString(R.string.binding_secondary));
        loadBindingSpinner(element, container, i, title);
      }
    } else if (type == ControlElement.Type.D_PAD
        || type == ControlElement.Type.STICK
        || type == ControlElement.Type.TRACKPAD) {
      loadBindingSpinner(element, container, 0, getString(R.string.input_controls_editor_binding_up));
      loadBindingSpinner(element, container, 1, getString(R.string.input_controls_editor_binding_right));
      loadBindingSpinner(element, container, 2, getString(R.string.input_controls_editor_binding_down));
      loadBindingSpinner(element, container, 3, getString(R.string.input_controls_editor_binding_left));
    }
  }

  private void loadBindingSpinner(
      final ControlElement element, LinearLayout container, final int index, String title) {
    View view = LayoutInflater.from(this).inflate(R.layout.binding_field, container, false);
    ((TextView) view.findViewById(R.id.TVTitle)).setText(title);
    final Spinner sBindingType = view.findViewById(R.id.SBindingType);
    final Spinner sBinding = view.findViewById(R.id.SBinding);

    AppUtils.setupThemedSpinner(
        sBindingType,
        this,
        Arrays.asList(getResources().getStringArray(R.array.binding_type_entries)));

    Runnable update =
        () -> {
          String[] bindingEntries = null;
          switch (sBindingType.getSelectedItemPosition()) {
            case 0:
              bindingEntries = Binding.keyboardBindingLabels();
              break;
            case 1:
              bindingEntries = Binding.mouseBindingLabels();
              break;
            case 2:
              bindingEntries = Binding.gamepadBindingLabels();
              break;
          }

          AppUtils.setupThemedSpinner(sBinding, this, Arrays.asList(bindingEntries));
          AppUtils.setSpinnerSelectionFromValue(sBinding, element.getBindingAt(index).toString());
        };

    sBindingType.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            update.run();
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {}
        });

    Binding selectedBinding = element.getBindingAt(index);
    int typeIndex = 0;
    if (selectedBinding.isMouse()) {
      typeIndex = 1;
    } else if (selectedBinding.isGamepad() || (selectedBinding == Binding.NONE && (element.getType() == ControlElement.Type.STICK || element.getType() == ControlElement.Type.D_PAD))) {
      typeIndex = 2;
    }
    sBindingType.setSelection(typeIndex);

    sBinding.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (blockingUpdate) return;
            Binding binding = Binding.NONE;
            switch (sBindingType.getSelectedItemPosition()) {
              case 0:
                binding = Binding.keyboardBindingValues()[position];
                break;
              case 1:
                binding = Binding.mouseBindingValues()[position];
                break;
              case 2:
                binding = Binding.gamepadBindingValues()[position];
                break;
            }

            if (binding != element.getBindingAt(index)) {
              element.setBindingAt(index, binding);
              profile.save();
              inputControlsView.invalidate();
            }
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {}
        });

    update.run();
    container.addView(view);
  }

  private void loadRangeSpinner(final ControlElement element, Spinner spinner) {
    AppUtils.setupThemedSpinner(spinner, this, Arrays.asList(ControlElement.Range.names()));
    spinner.setSelection(element.getRange().ordinal(), false);
    spinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            element.setRange(ControlElement.Range.values()[position]);
            profile.save();
            inputControlsView.invalidate();
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {}
        });
  }

  private void loadIcons(final LinearLayout parent, byte selectedId) {
    byte[] iconIds = new byte[0];
    try {
      String[] filenames = getAssets().list("inputcontrols/icons/");
      iconIds = new byte[filenames.length];
      for (int i = 0; i < filenames.length; i++) {
        iconIds[i] = Byte.parseByte(FileUtils.getBasename(filenames[i]));
      }
    } catch (IOException e) {
    }

    Arrays.sort(iconIds);

    int size = (int) UnitUtils.dpToPx(40);
    int margin = (int) UnitUtils.dpToPx(2);
    int padding = (int) UnitUtils.dpToPx(4);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
    params.setMargins(margin, 0, margin, 0);

    for (final byte id : iconIds) {
      ImageView imageView = new ImageView(this);
      imageView.setLayoutParams(params);
      imageView.setPadding(padding, padding, padding, padding);
      imageView.setBackgroundResource(R.drawable.icon_background);
      imageView.setTag(id);
      imageView.setSelected(id == selectedId);
      imageView.setOnClickListener(
          (v) -> {
            for (int i = 0; i < parent.getChildCount(); i++)
              parent.getChildAt(i).setSelected(false);
            imageView.setSelected(true);
          });

      try (InputStream is = getAssets().open("inputcontrols/icons/" + id + ".png")) {
        imageView.setImageBitmap(BitmapFactory.decodeStream(is));
      } catch (IOException e) {
      }

      parent.addView(imageView);
    }
  }

  private void setupToolbarDragging() {
    View toolbar = findViewById(R.id.LLToolbar);
    if (toolbar != null) {
      toolbar.setOnTouchListener(new View.OnTouchListener() {
        private int activePointerId = -1;
        private float dX, dY;
        private float downRawX, downRawY;
        private boolean isDragging = false;
        private static final float TAP_SLOP = 20f;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
          if (event.getPointerCount() > 1) {
            this.activePointerId = -1;
            return false;
          }
          switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
              this.activePointerId = event.getPointerId(0);
              this.dX = view.getX() - event.getRawX();
              this.dY = view.getY() - event.getRawY();
              this.downRawX = event.getRawX();
              this.downRawY = event.getRawY();
              this.isDragging = false;
              view.bringToFront();
              return true;
            case MotionEvent.ACTION_MOVE:
              if (this.activePointerId != -1) {
                float dx = Math.abs(event.getRawX() - this.downRawX);
                float dy = Math.abs(event.getRawY() - this.downRawY);
                if (dx > TAP_SLOP || dy > TAP_SLOP) {
                  this.isDragging = true;
                }
                if (this.isDragging) {
                  view.setX(event.getRawX() + this.dX);
                  view.setY(event.getRawY() + this.dY);
                  clampToParentBounds(view);
                }
                return true;
              }
              break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
              if (this.activePointerId != -1) {
                if (this.isDragging) {
                  clampToParentBounds(view);
                }
                this.activePointerId = -1;
                return this.isDragging;
              }
              return false;
          }
          return false;
        }
      });
    }
  }

  private void clampToParentBounds(View view) {
    View parentView = (View) view.getParent();
    if (parentView == null) return;
    if (parentView.getWidth() <= 0 || parentView.getHeight() <= 0 || view.getWidth() <= 0 || view.getHeight() <= 0) return;
    float maxX = Math.max(0f, parentView.getWidth() - view.getWidth());
    float maxY = Math.max(0f, parentView.getHeight() - view.getHeight());
    view.setX(Math.max(0f, Math.min(view.getX(), maxX)));
    view.setY(Math.max(0f, Math.min(view.getY(), maxY)));
  }
}
