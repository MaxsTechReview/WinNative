package com.winlator.cmod.contentdialog;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.GPUInformation;
import com.winlator.cmod.core.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class GraphicsDriverConfigDialog extends ContentDialog {

    private static final String TAG = "GraphicsDriverConfigDialog"; // Tag for logging
    HashMap<String, Boolean> extensionsState = new HashMap<>();
    private Spinner sVersion;
    private Spinner sAvailableExtensions;
    private Spinner sMaxDeviceMemory;
    private Spinner sFrameSynchronization;
    private CheckBox cbAdrenotoolsTurnip;
    private static String selectedVersion;
    private static String blacklistedExtensions = "";
    private static String selectedDeviceMemory;
    private static String isAdrenotoolsTurnip = "1";
    private static String frameSynchronization;

    protected class ExtensionAdapter extends ArrayAdapter<String> {
        ArrayList<String> extensions;

        public ExtensionAdapter(Context context, List<String> list) {
            super(context, 0, list);
            this.extensions = new ArrayList<>(list);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return initSpinnerElement(position, convertView, parent);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return initDropDownView(position, convertView, parent);
        }

        private View initSpinnerElement(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = (View)new TextView(getContext());
            }
            ((TextView)convertView).setText(extensions.size() + " System Extensions");
            return convertView;
        }

        private View initDropDownView(int position, View convertView, ViewGroup parent) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
            boolean isDarkMode = sp.getBoolean("dark_mode", false);
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.checkbox_spinner, parent, false);
            }
            CheckBox cb = convertView.findViewById(R.id.checkbox);
            cb.setTextAppearance(isDarkMode ? R.style.CheckBox_Dark : R.style.CheckBox);
            cb.setText(extensions.get(position));
            cb.setOnCheckedChangeListener(null);
            cb.setChecked(extensionsState.getOrDefault(extensions.get(position), true));
            cb.setOnCheckedChangeListener((buttonView, isChecked) ->  {
                extensionsState.put(extensions.get(position), isChecked);
            });
            return convertView;
        }
    }

    public static HashMap<String, String> parseGraphicsDriverConfig(String graphicsDriverConfig) {
        HashMap<String, String> mappedConfig = new HashMap<>();
        String[] configElements = graphicsDriverConfig.split(";");
        for (String element : configElements) {
            String key;
            String value;
            String[] splittedElement = element.split("=");
            key = splittedElement[0];
            if (splittedElement.length > 1)
                value = element.split("=")[1];
            else
                value = "";
            mappedConfig.put(key, value);
        }
        return mappedConfig;
    }

    public static String toGraphicsDriverConfig(HashMap<String, String> config) {
        String graphicsDriverConfig = "";
        for (Map.Entry<String, String> entry : config.entrySet()) {
            graphicsDriverConfig += entry.getKey() + "=" + entry.getValue() + ";";
        }
        return graphicsDriverConfig.substring(0, graphicsDriverConfig.length() - 1);
    }

    public static String getVersion(String graphicsDriverConfig) {
        HashMap<String, String> config = parseGraphicsDriverConfig(graphicsDriverConfig);
        return config.get("version");
    }

    public static String getExtensionsBlacklist(String graphicsDriverConfig) {
        HashMap<String, String> config = parseGraphicsDriverConfig(graphicsDriverConfig);
        return config.get("blacklistedExtensions");
    }

    public static String writeGraphicsDriverConfig() {
        String graphicsDriverConfig = "version=" + selectedVersion + ";" + "blacklistedExtensions=" + blacklistedExtensions + ";" + "maxDeviceMemory=" + StringUtils.parseNumber(selectedDeviceMemory) + ";" + "adrenotoolsTurnip=" + isAdrenotoolsTurnip + ";" + "frameSync=" + frameSynchronization;
        Log.i(TAG, "Written config " + graphicsDriverConfig);
        return graphicsDriverConfig;
    }
  
    public GraphicsDriverConfigDialog(View anchor, String graphicsDriver, TextView graphicsDriverVersionView) {
        super(anchor.getContext(), R.layout.graphics_driver_config_dialog);
        initializeDialog(anchor, graphicsDriver, graphicsDriverVersionView);
    }

    private void initializeDialog(View anchor, String graphicsDriver, TextView graphicsDriverVersionView) {
        setIcon(R.drawable.icon_settings);
        setTitle(anchor.getContext().getString(R.string.graphics_driver_configuration));

        String graphicsDriverConfig = anchor.getTag().toString();

        sVersion = findViewById(R.id.SGraphicsDriverVersion);
        sAvailableExtensions = findViewById(R.id.SGraphicsDriverAvailableExtensions);
        sFrameSynchronization = findViewById(R.id.SGraphicsDriverFrameSync);
        sMaxDeviceMemory = findViewById(R.id.SGraphicsDriverMaxDeviceMemory);
        cbAdrenotoolsTurnip = findViewById(R.id.CBAdrenotoolsTurnip);

        HashMap<String, String> config = parseGraphicsDriverConfig(graphicsDriverConfig);

        String initialVersion = config.get("version");
        String blExtensions = config.get("blacklistedExtensions");
        String maxDeviceMemory = config.get("maxDeviceMemory");
        String adrenotoolsTurnip = config.get("adrenotoolsTurnip");
        String frameSync = config.get("frameSync");

        // Update the selectedVersion whenever the user selects a different version
        sVersion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedVersion = sVersion.getSelectedItem().toString();
                Log.d(TAG, "User selected version: " + selectedVersion);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedVersion = sVersion.getSelectedItem().toString();
                Log.d(TAG, "User selected version: " + selectedVersion);
            }
        });

        sMaxDeviceMemory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedDeviceMemory = sMaxDeviceMemory.getSelectedItem().toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        sFrameSynchronization.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                frameSynchronization = sFrameSynchronization.getSelectedItem().toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        cbAdrenotoolsTurnip.setOnCheckedChangeListener(null);
        cbAdrenotoolsTurnip.setChecked(adrenotoolsTurnip.equals("1") ? true : false);
        cbAdrenotoolsTurnip.setOnCheckedChangeListener((buttonView, isChecked) ->  {
            isAdrenotoolsTurnip = isChecked ? "1" : "0";
        });

        // Ensure ContentsManager syncContents is called
        ContentsManager contentsManager = new ContentsManager(anchor.getContext());
        contentsManager.syncContents();
        
        // Populate the spinner with available versions from ContentsManager and pre-select the initial version
        populateGraphicsDriverVersions(anchor.getContext(), contentsManager, initialVersion, blExtensions, maxDeviceMemory, frameSync, graphicsDriver);

        setOnConfirmCallback(() -> {
            for (HashMap.Entry<String, Boolean> entry : extensionsState.entrySet()) {
                if(!entry.getKey().isEmpty() && !entry.getValue()) {
                    blacklistedExtensions += entry.getKey() + ",";
                }
            }

            if (!blacklistedExtensions.isEmpty())
                blacklistedExtensions = blacklistedExtensions.substring(0, blacklistedExtensions.length() - 1);

            if (graphicsDriverVersionView != null)
                graphicsDriverVersionView.setText(selectedVersion);

            anchor.setTag(writeGraphicsDriverConfig());
        });
    }

    private void populateGraphicsDriverVersions(Context context, ContentsManager contentsManager, @Nullable String initialVersion, @Nullable String blExtensions, String maxDeviceMemory, String frameSync, String graphicsDriver) {
        List<String> wrapperVersions = new ArrayList<>();
        ArrayList<String> availableExtensions;

        String[] wrapperDefaultVersions = context.getResources().getStringArray(R.array.wrapper_graphics_driver_version_entries);

        wrapperVersions.addAll(Arrays.asList(wrapperDefaultVersions));
        
        // Add installed versions from AdrenotoolsManager
        AdrenotoolsManager adrenotoolsManager = new AdrenotoolsManager(context);
        wrapperVersions.addAll(adrenotoolsManager.enumarateInstalledDrivers());


        availableExtensions = new ArrayList<>(Arrays.asList(GPUInformation.enumerateExtensions()));

        // Remove essential and wrapper disabled extensions
        String[] essentialExtensions = {"VK_EXT_hdr_metadata", "VK_GOOGLE_display_timing", "VK_KHR_shader_float_controls", "VK_KHR_shader_presentable_image", "VK_EXT_image_compression_control_swapchain"};
        for (String extension : essentialExtensions) {
            availableExtensions.remove(extension);
        }

        // Set the adapter and select the initial version
        ArrayAdapter<String> wrapperAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, wrapperVersions);
        ExtensionAdapter extensionsAdapter = new ExtensionAdapter(context, availableExtensions);

        String[] bl = blExtensions.split("\\,");

        for (String extension : bl) {
            if (!extension.isEmpty()) {
                Log.d("GraphicsDriverConfigDialog", "Getting initial blacklisted extension: " + extension);
                extensionsState.put(extension, false);
            }
        }
        
        sVersion.setAdapter(wrapperAdapter);
        sAvailableExtensions.setAdapter(extensionsAdapter);
        
        // We can start logging selected graphics driver and initial version
        Log.d(TAG, "Graphics driver: " + graphicsDriver);
        Log.d(TAG, "Initial version: " + initialVersion);

        // Use the custom selection logic
        setSpinnerSelectionWithFallback(sVersion, initialVersion, graphicsDriver);
        AppUtils.setSpinnerSelectionFromNumber(sMaxDeviceMemory, maxDeviceMemory);
        AppUtils.setSpinnerSelectionFromValue(sFrameSynchronization, frameSync);

        // We can log the spinner values now
        Log.d(TAG, "Spinner selected position: " + sVersion.getSelectedItemPosition());
        Log.d(TAG, "Spinner selected value: " + sVersion.getSelectedItem());
    }

    private void setSpinnerSelectionWithFallback(Spinner spinner, String version, String graphicsDriver) {
        // First, attempt to find an exact match (case-insensitive)
        for (int i = 0; i < spinner.getCount(); i++) {
            String item = spinner.getItemAtPosition(i).toString();
            if (item.equalsIgnoreCase(version)) {
                spinner.setSelection(i);
                return;
            }
        }

        AppUtils.setSpinnerSelectionFromValue(spinner, DefaultVersion.WRAPPER);
    }

}
