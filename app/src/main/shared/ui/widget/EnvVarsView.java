package com.winlator.cmod.shared.ui.widget;

import android.content.Context;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.winlator.cmod.R;
import com.winlator.cmod.runtime.wine.EnvVars;
import com.winlator.cmod.shared.android.AppUtils;
import com.winlator.cmod.shared.util.UnitUtils;
import java.util.Arrays;
import java.util.List;

public class EnvVarsView extends FrameLayout {
  public static final String[][] knownEnvVars = {
    {"ZINK_DESCRIPTORS", "SELECT", "auto", "lazy", "cached", "notemplates"},
    {
      "ZINK_DEBUG",
      "SELECT_MULTIPLE",
      "nir",
      "spirv",
      "tgsi",
      "validation",
      "sync",
      "compact",
      "noreorder"
    },
    {"MESA_SHADER_CACHE_DISABLE", "CHECKBOX", "false", "true"},
    {"mesa_glthread", "CHECKBOX", "false", "true"},
    {"WINEESYNC", "CHECKBOX", "0", "1"},
    {"FD_DEV_FEATURES", "SELECT_MULTIPLE", "enable_tp_ubwc_flag_hint=1", "storage_8bit=1"},
    {
      "TU_DEBUG",
      "SELECT_MULTIPLE",
      "forcecb",
      "nocb",
      "startup",
      "deck_emu",
      "nir",
      "nobin",
      "sysmem",
      "gmem",
      "forcebin",
      "layout",
      "noubwc",
      "nomultipos",
      "nolrz",
      "nolrzfc",
      "perf",
      "perfc",
      "flushall",
      "syncdraw",
      "push_consts_per_stage",
      "rast_order",
      "unaligned_store",
      "log_skip_gmem_ops",
      "dynamic",
      "bos",
      "3d_load",
      "fdm",
      "noconform",
      "rd"
    },
    {"IR3_SHADER_DEBUG", "SELECT_MULTIPLE", "nouboopt", "nopreamble", "noearlypreamble"},
    {
      "DXVK_HUD",
      "SELECT_MULTIPLE",
      "scale=0.5",
      "scale=0.7",
      "opacity=0.5",
      "opacity=0.7",
      "devinfo",
      "fps",
      "frametimes",
      "submissions",
      "drawcalls",
      "pipelines",
      "descriptors",
      "memory",
      "gpuload",
      "version",
      "api",
      "cs",
      "compiler",
      "samplers"
    },
    {"MESA_EXTENSION_MAX_YEAR", "TEXT"},
    {"WRAPPER_MAX_IMAGE_COUNT", "TEXT"},
    {"MESA_GL_VERSION_OVERRIDE", "TEXT"},
    {"PULSE_LATENCY_MSEC", "NUMBER"},
    {"WINE_DO_NOT_CREATE_DXGI_DEVICE_MANAGER", "CHECKBOX", "0", "1"},
    {"WINE_NEW_MEDIASOURCE", "CHECKBOX", "0", "1"},
    {"WINE_LARGE_ADDRESS_AWARE", "CHECKBOX", "0", "1"},
    {"WINEDLLOVERRIDES", "TEXT"},
    {"GALLIUM_HUD", "SELECT_MULTIPLE", "simple", "fps", "frametime"}
  };
  private final LinearLayout container;
  private final TextView emptyTextView;
  private final LayoutInflater inflater;

  private interface GetValueCallback {
    String call();
  }

  public EnvVarsView(Context context) {
    this(context, null);
  }

  public EnvVarsView(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public EnvVarsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    this(context, attrs, defStyleAttr, 0);
  }

  public EnvVarsView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    inflater = LayoutInflater.from(context);
    container = new LinearLayout(context);
    container.setOrientation(LinearLayout.VERTICAL);
    container.setLayoutParams(
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    addView(container);

    emptyTextView = new TextView(context);
    emptyTextView.setText(R.string.common_ui_no_items_to_display);
    emptyTextView.setTextColor(ContextCompat.getColor(context, R.color.settings_text_secondary));
    emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
    emptyTextView.setGravity(Gravity.CENTER);
    int padding = (int) UnitUtils.dpToPx(16);
    emptyTextView.setPadding(padding, padding, padding, padding);
    addView(emptyTextView);
  }

  private String[] findKnownEnvVar(String name) {
    for (String[] values : knownEnvVars) {
      if (values[0].equals(name)) return values;
    }
    return null;
  }

  public String getEnvVars() {
    EnvVars envVars = new EnvVars();
    for (int i = 0; i < container.getChildCount(); i++) {
      View child = container.getChildAt(i);
      GetValueCallback getValueCallback = (GetValueCallback) child.getTag();
      String name = ((TextView) child.findViewById(R.id.TextView)).getText().toString();
      String value = getValueCallback.call().trim().replace(" ", "");
      if (!value.isEmpty()) envVars.put(name, value);
    }
    return envVars.toString();
  }

  public boolean containsName(String name) {
    for (int i = 0; i < container.getChildCount(); i++) {
      View child = container.getChildAt(i);
      String text = ((TextView) child.findViewById(R.id.TextView)).getText().toString();
      if (name.equals(text)) return true;
    }
    return false;
  }

  public void add(String name, String value) {
    final Context context = getContext();
    final View itemView = inflater.inflate(R.layout.env_vars_list_item, container, false);
    ((TextView) itemView.findViewById(R.id.TextView)).setText(name);

    String[] knownEnvVar = findKnownEnvVar(name);
    GetValueCallback getValueCallback;

    String type = knownEnvVar != null ? knownEnvVar[1] : "TEXT";

    switch (type) {
      case "CHECKBOX":
        final CompoundButton toggleButton = itemView.findViewById(R.id.ToggleButton);
        toggleButton.setVisibility(VISIBLE);
        toggleButton.setChecked(value.equals("1") || value.equals("true"));
        getValueCallback = () -> toggleButton.isChecked() ? knownEnvVar[3] : knownEnvVar[2];
        break;
      case "SELECT":
        List<String> items = Arrays.asList(Arrays.copyOfRange(knownEnvVar, 2, knownEnvVar.length));
        final Spinner spinner = itemView.findViewById(R.id.Spinner);
        AppUtils.setupThemedSpinner(spinner, context, items);
        AppUtils.setSpinnerSelectionFromValue(spinner, value);
        spinner.setVisibility(VISIBLE);
        getValueCallback = () -> spinner.getSelectedItem().toString();
        break;
      case "SELECT_MULTIPLE":
        final MultiSelectionComboBox comboBox = itemView.findViewById(R.id.MultiSelectionComboBox);
        comboBox.setItems(Arrays.copyOfRange(knownEnvVar, 2, knownEnvVar.length));
        comboBox.setSelectedItems(value.split(","));
        comboBox.setVisibility(VISIBLE);
        getValueCallback = comboBox::getSelectedItemsAsString;
        break;
      case "NUMBER":
        EditText editTextNumber = itemView.findViewById(R.id.EditText);
        editTextNumber.setVisibility(VISIBLE);
        editTextNumber.setText(value);
        editTextNumber.setInputType(InputType.TYPE_CLASS_NUMBER);
        getValueCallback = () -> editTextNumber.getText().toString();
        break;
      case "TEXT":
      default:
        EditText editText = itemView.findViewById(R.id.EditText);
        editText.setVisibility(VISIBLE);
        editText.setText(value);
        getValueCallback = () -> editText.getText().toString();
        break;
    }

    itemView.setTag(getValueCallback);
    itemView
        .findViewById(R.id.BTRemove)
        .setOnClickListener(
            (v) -> {
              container.removeView(itemView);
              if (container.getChildCount() == 0) emptyTextView.setVisibility(View.VISIBLE);
            });
    container.addView(itemView);
    emptyTextView.setVisibility(View.GONE);
  }

  public void setEnvVars(EnvVars envVars) {
    container.removeAllViews();
    for (String name : envVars) add(name, envVars.get(name));
  }

  /** No-op kept for call-site compatibility. Theming is now handled by XML styles. */
  public void setDarkMode(boolean isDarkMode) {}
}
