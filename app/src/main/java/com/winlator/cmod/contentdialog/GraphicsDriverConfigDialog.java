package com.winlator.cmod.contentdialog;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.winlator.cmod.R;
import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.GPUInformation;
import com.winlator.cmod.core.StringUtils;
import com.winlator.cmod.widget.MultiSelectionComboBox;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class GraphicsDriverConfigDialog extends ContentDialog {

    private static final String TAG = "GraphicsDriverConfigDialog";
    private Spinner sVersion;
    private Spinner sVulkanVersion;
    private MultiSelectionComboBox mscAvailableExtensions;
    private Spinner sGPUName;
    private Spinner sMaxDeviceMemory;
    private Spinner sPresentMode;
    private Spinner sResourceType;
    private Spinner sBCnEmulation;
    private Spinner sBCnEmulationType;
    private Spinner sBCnEmulationCache;
    private CompoundButton cbSyncFrame;
    private CompoundButton cbDisablePresentWait;

    private static String selectedVulkanVersion = "1.3";
    private static String selectedVersion = "";
    private static String blacklistedExtensions = "";
    private static String selectedGPUName = "Device";
    private static String selectedDeviceMemory = "0";

    private static String isSyncFrame = "0";
    private static String isDisablePresentWait = "0";
    private static String selectedPresentMode = "mailbox";
    private static String selectedResourceType = "auto";
    private static String selectedBCnEmulation = "auto";
    private static String selectedBCnEmulationType = "compute";
    private static String isBCnCacheEnabled = "0";

    /**
     * SAFE factory method. Wraps the ENTIRE dialog construction (including super() and layout
     * inflation) in a try-catch(Throwable) so that native library errors, layout inflation
     * errors, and any other failures cannot crash the Activity.
     * 
     * @return true if dialog was shown, false if creation failed
     */
    public static boolean showSafe(View anchor, String graphicsDriver, TextView graphicsDriverVersionView) {
        try {
            GraphicsDriverConfigDialog dialog = new GraphicsDriverConfigDialog(anchor.getContext(), R.layout.graphics_driver_config_dialog);
            dialog.initializeDialog(anchor, graphicsDriver, graphicsDriverVersionView);
            dialog.show();
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "Failed to create/show GraphicsDriverConfigDialog", e);
            try {
                AppUtils.showToast(anchor.getContext(), "Error opening graphics driver settings: " + e.getMessage());
            } catch (Throwable ignored) {}
            return false;
        }
    }

    /**
     * Package-private constructor — use showSafe() instead of calling this directly.
     * This exists so that the super() call is part of the constructor chain but
     * showSafe() wraps the entire construction in catch(Throwable).
     */
    GraphicsDriverConfigDialog(Context context, int layoutResId) {
        super(context, layoutResId);
    }

    private void loadGPUNameSpinner(Context context, Spinner spinner) {
        ArrayList<String> entries = new ArrayList<>();
        entries.add("Device");

        try {
            String gpuNameList = FileUtils.readString(context, "gpu_cards.json");
            if (gpuNameList != null && !gpuNameList.isEmpty()) {
                JSONArray jarray = new JSONArray(gpuNameList);
                for (int i = 0; i < jarray.length(); i++) {
                    JSONObject jobj = jarray.getJSONObject(i);
                    String gpuName = jobj.getString("name");
                    entries.add(gpuName);
                }
            }
        } catch (Throwable e) {
            Log.w(TAG, "Error parsing gpu_cards.json", e);
        }
        AppUtils.setupThemedSpinner(spinner, context, entries);
    }

    public static HashMap<String, String> parseGraphicsDriverConfig(String graphicsDriverConfig) {
        HashMap<String, String> mappedConfig = new HashMap<>();
        if (graphicsDriverConfig == null || graphicsDriverConfig.isEmpty()) {
            return mappedConfig;
        }
        try {
            String[] configElements = graphicsDriverConfig.split(";");
            for (String element : configElements) {
                if (element == null || element.trim().isEmpty()) continue;
                String key;
                String value;
                String[] splittedElement = element.split("=", 2);
                key = splittedElement[0];
                if (splittedElement.length > 1)
                    value = splittedElement[1];
                else
                    value = "";
                if (!key.isEmpty()) {
                    mappedConfig.put(key, value);
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "Error parsing graphics driver config: " + graphicsDriverConfig, e);
        }
        return mappedConfig;
    }

    public static String toGraphicsDriverConfig(HashMap<String, String> config) {
        if (config == null || config.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            if (sb.length() > 0) sb.append(";");
            sb.append(entry.getKey()).append("=").append(entry.getValue() != null ? entry.getValue() : "");
        }
        return sb.toString();
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
        String graphicsDriverConfig = "vulkanVersion=" + (selectedVulkanVersion != null ? selectedVulkanVersion : "1.3") + ";" +
                "version=" + (selectedVersion != null ? selectedVersion : "") + ";" +
                "blacklistedExtensions=" + (blacklistedExtensions != null ? blacklistedExtensions : "") + ";" +
                "maxDeviceMemory=" + StringUtils.parseNumber(selectedDeviceMemory) + ";" +
                "presentMode=" + (selectedPresentMode != null ? selectedPresentMode : "mailbox") + ";" +
                "syncFrame=" + (isSyncFrame != null ? isSyncFrame : "0") + ";" +
                "disablePresentWait=" + (isDisablePresentWait != null ? isDisablePresentWait : "0") + ";" +
                "resourceType=" + (selectedResourceType != null ? selectedResourceType : "auto") + ";" +
                "bcnEmulation=" + (selectedBCnEmulation != null ? selectedBCnEmulation : "auto") + ";" +
                "bcnEmulationType=" + (selectedBCnEmulationType != null ? selectedBCnEmulationType : "compute") + ";" +
                "bcnEmulationCache=" + (isBCnCacheEnabled != null ? isBCnCacheEnabled : "0") + ";" +
                "gpuName=" + (selectedGPUName != null ? selectedGPUName : "Device");
        Log.i(TAG, "Written config " + graphicsDriverConfig);
        return graphicsDriverConfig;
    }

    private String[] queryAvailableExtensions(String driver, Context context) {
        return GPUInformation.enumerateExtensionsSafe(driver, context);
    }

    private void initializeDialog(View anchor, String graphicsDriver, TextView graphicsDriverVersionView) {
        setIcon(R.drawable.ic_drivers);
        setTitle(anchor.getContext().getString(R.string.graphics_driver_configuration));

        String graphicsDriverConfig = anchor.getTag() != null ? anchor.getTag().toString() : "";

        sVersion = findViewById(R.id.SGraphicsDriverVersion);
        sVulkanVersion = findViewById(R.id.SGraphicsDriverVulkanVersion);
        mscAvailableExtensions = findViewById(R.id.MSCAvailableExtensions);
        sPresentMode = findViewById(R.id.SGraphicsDriverPresentMode);
        sGPUName = findViewById(R.id.SGraphicsDriverGPUName);
        sMaxDeviceMemory = findViewById(R.id.SGraphicsDriverMaxDeviceMemory);
        sResourceType = findViewById(R.id.SGraphicsDriverResourceType);
        sBCnEmulation = findViewById(R.id.SGraphicsDriverBCnEmulation);
        sBCnEmulationType = findViewById(R.id.SGraphicsDriverBCnEmulationType);
        sBCnEmulationCache = findViewById(R.id.SGraphicsDriverBCnEmulationCache);
        cbSyncFrame = (CompoundButton) findViewById(R.id.CBSyncFrame);
        cbDisablePresentWait = (CompoundButton) findViewById(R.id.CBDisablePresentWait);

        // Verify all views found
        if (sVersion == null || sVulkanVersion == null || mscAvailableExtensions == null ||
            sPresentMode == null || sGPUName == null || sMaxDeviceMemory == null ||
            sResourceType == null || sBCnEmulation == null || sBCnEmulationType == null ||
            sBCnEmulationCache == null || cbSyncFrame == null || cbDisablePresentWait == null) {
            Log.e(TAG, "One or more views not found in layout!");
            return;
        }

        HashMap<String, String> config = parseGraphicsDriverConfig(graphicsDriverConfig);

        String vulkanVersion = config.get("vulkanVersion") != null ? config.get("vulkanVersion") : "1.3";
        String initialVersion = config.get("version") != null ? config.get("version") : "";
        String blExtensions = config.get("blacklistedExtensions") != null ? config.get("blacklistedExtensions") : "";
        String gpuName = config.get("gpuName") != null ? config.get("gpuName") : "Device";
        String maxDeviceMemory = config.get("maxDeviceMemory") != null ? config.get("maxDeviceMemory") : "0";
        String syncFrame = config.get("syncFrame") != null ? config.get("syncFrame") : "0";
        String disablePresentWait = config.get("disablePresentWait") != null ? config.get("disablePresentWait") : "0";
        String presentMode = config.get("presentMode") != null ? config.get("presentMode") : "mailbox";
        String resourceType = config.get("resourceType") != null ? config.get("resourceType") : "auto";
        String bcnEmulation = config.get("bcnEmulation") != null ? config.get("bcnEmulation") : "auto";
        String bcnEmulationType = config.get("bcnEmulationType") != null ? config.get("bcnEmulationType") : "compute";
        String bcnEmulationCache = config.get("bcnEmulationCache") != null ? config.get("bcnEmulationCache") : "0";

        // Initialize extensions with placeholder
        mscAvailableExtensions.setItems(new String[]{"Loading..."}, "Extensions");

        // Update the selectedVersion whenever the user selects a different version
        sVersion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                try {
                    selectedVersion = sVersion.getSelectedItem() != null ? sVersion.getSelectedItem().toString() : "";
                    String[] availableExtensions = queryAvailableExtensions(selectedVersion, anchor.getContext());
                    String currentBlacklisted = "";

                    if (availableExtensions.length > 0) {
                        mscAvailableExtensions.setItems(availableExtensions, "Extensions");
                        mscAvailableExtensions.setSelectedItems(availableExtensions);
                    } else {
                        mscAvailableExtensions.setItems(new String[]{"No extensions found"}, "Extensions");
                    }

                    if (selectedVersion != null && selectedVersion.equals(initialVersion))
                        currentBlacklisted = blExtensions != null ? blExtensions : "";

                    if (!currentBlacklisted.isEmpty()) {
                        String[] bl = currentBlacklisted.split(",");
                        for (String extension : bl) {
                            if (extension != null && !extension.trim().isEmpty()) {
                                mscAvailableExtensions.unsetSelectedItem(extension.trim());
                            }
                        }
                    }
                } catch (Throwable e) {
                    Log.e(TAG, "Error updating extensions list", e);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedVersion = sVersion.getSelectedItem() != null ? sVersion.getSelectedItem().toString() : "";
            }
        });

        sVulkanVersion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedVulkanVersion = sVulkanVersion.getSelectedItem() != null ? sVulkanVersion.getSelectedItem().toString() : "1.3";
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedVulkanVersion = sVulkanVersion.getSelectedItem() != null ? sVulkanVersion.getSelectedItem().toString() : "1.3";
            }
        });

        sGPUName.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedGPUName = sGPUName.getSelectedItem() != null ? sGPUName.getSelectedItem().toString() : "Device";
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedGPUName = sGPUName.getSelectedItem() != null ? sGPUName.getSelectedItem().toString() : "Device";
            }
        });

        sMaxDeviceMemory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedDeviceMemory = sMaxDeviceMemory.getSelectedItem() != null ? sMaxDeviceMemory.getSelectedItem().toString() : "0";
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedDeviceMemory = sMaxDeviceMemory.getSelectedItem() != null ? sMaxDeviceMemory.getSelectedItem().toString() : "0";
            }
        });

        sPresentMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedPresentMode = sPresentMode.getSelectedItem() != null ? sPresentMode.getSelectedItem().toString() : "mailbox";
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedPresentMode = sPresentMode.getSelectedItem() != null ? sPresentMode.getSelectedItem().toString() : "mailbox";
            }
        });

        sResourceType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedResourceType = sResourceType.getSelectedItem() != null ? sResourceType.getSelectedItem().toString() : "auto";
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                selectedResourceType = sResourceType.getSelectedItem() != null ? sResourceType.getSelectedItem().toString() : "auto";
            }
        });

        sBCnEmulation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedBCnEmulation = sBCnEmulation.getSelectedItem() != null ? sBCnEmulation.getSelectedItem().toString() : "auto";
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                selectedBCnEmulation = sBCnEmulation.getSelectedItem() != null ? sBCnEmulation.getSelectedItem().toString() : "auto";
            }
        });

        sBCnEmulationType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedBCnEmulationType = sBCnEmulationType.getSelectedItem() != null ? sBCnEmulationType.getSelectedItem().toString() : "compute";
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                selectedBCnEmulationType = sBCnEmulationType.getSelectedItem() != null ? sBCnEmulationType.getSelectedItem().toString() : "compute";
            }
        });

        sBCnEmulationCache.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                isBCnCacheEnabled = sBCnEmulationCache.getSelectedItem() != null ? sBCnEmulationCache.getSelectedItem().toString() : "0";
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                isBCnCacheEnabled = sBCnEmulationCache.getSelectedItem() != null ? sBCnEmulationCache.getSelectedItem().toString() : "0";
            }
        });

        isSyncFrame = syncFrame;
        cbSyncFrame.setChecked("1".equals(isSyncFrame));
        cbSyncFrame.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isSyncFrame = isChecked ? "1" : "0";
        });

        isDisablePresentWait = disablePresentWait;
        cbDisablePresentWait.setChecked("1".equals(isDisablePresentWait));
        cbDisablePresentWait.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isDisablePresentWait = isChecked ? "1" : "0";
        });

        // Ensure ContentsManager syncContents is called
        ContentsManager contentsManager = null;
        try {
            contentsManager = new ContentsManager(anchor.getContext());
            contentsManager.syncContents();
        } catch (Throwable e) {
            Log.e(TAG, "Error initializing ContentsManager", e);
        }

        // Populate the spinner with available versions
        populateGraphicsDriverVersions(anchor.getContext(), contentsManager, vulkanVersion, initialVersion, blExtensions, gpuName, maxDeviceMemory, presentMode, resourceType, bcnEmulation, bcnEmulationType, bcnEmulationCache, graphicsDriver);

        setOnConfirmCallback(() -> {
            try {
                blacklistedExtensions = mscAvailableExtensions.getUnSelectedItemsAsString();

                if (graphicsDriverVersionView != null)
                    graphicsDriverVersionView.setText(selectedVersion);

                anchor.setTag(writeGraphicsDriverConfig());
            } catch (Throwable e) {
                Log.e(TAG, "Error in confirm callback", e);
            }
        });
    }

    private void populateGraphicsDriverVersions(Context context, @Nullable ContentsManager contentsManager, String vulkanVersion, @Nullable String initialVersion, @Nullable String blExtensions, String gpuName, String maxDeviceMemory, String presentMode, String selectedResourceType, String bcnEmulation, String bcnEmulationType, String bcnEmulationCache, String graphicsDriver) {
        List<String> wrapperVersions = new ArrayList<>();
        try {
            String[] wrapperDefaultVersions = context.getResources().getStringArray(R.array.wrapper_graphics_driver_version_entries);

            for (String version : wrapperDefaultVersions) {
                try {
                    if (GPUInformation.isDriverSupported(version, context))
                        wrapperVersions.add(version);
                } catch (Throwable e) {
                    Log.w(TAG, "Error checking driver support for: " + version, e);
                    wrapperVersions.add(version);
                }
            }

            // Add installed versions from AdrenotoolsManager
            try {
                AdrenotoolsManager adrenotoolsManager = new AdrenotoolsManager(context);
                List<String> installed = adrenotoolsManager.enumarateInstalledDrivers();
                if (installed != null) {
                    wrapperVersions.addAll(installed);
                }
            } catch (Throwable e) {
                Log.w(TAG, "Error loading AdrenotoolsManager drivers", e);
            }
        } catch (Throwable e) {
            Log.e(TAG, "Error loading wrapper versions", e);
        }

        // Ensure we always have at least one entry
        if (wrapperVersions.isEmpty()) {
            wrapperVersions.add("System");
        }

        AppUtils.setupThemedSpinner(sVersion, context, wrapperVersions);

        // Apply themed adapters to all XML-entries spinners
        applyThemedAdapter(context, sVulkanVersion, R.array.vulkan_version_entries);
        applyThemedAdapter(context, sMaxDeviceMemory, R.array.device_memory_entries);
        applyThemedAdapter(context, sPresentMode, R.array.present_mode_entries);
        applyThemedAdapter(context, sResourceType, R.array.resource_type_entries);
        applyThemedAdapter(context, sBCnEmulation, R.array.bcn_emulation_entries);
        applyThemedAdapter(context, sBCnEmulationType, R.array.bcn_emulation_type_entries);
        applyThemedAdapter(context, sBCnEmulationCache, R.array.bcn_emulation_cache_entries);

        Log.d(TAG, "Graphics driver: " + graphicsDriver);
        Log.d(TAG, "Initial version: " + initialVersion);

        loadGPUNameSpinner(context, sGPUName);

        setSpinnerSelectionWithFallback(sVersion, initialVersion, graphicsDriver);
        AppUtils.setSpinnerSelectionFromValue(sVulkanVersion, vulkanVersion != null ? vulkanVersion : "1.3");
        AppUtils.setSpinnerSelectionFromValue(sGPUName, gpuName != null ? gpuName : "Device");
        AppUtils.setSpinnerSelectionFromNumber(sMaxDeviceMemory, maxDeviceMemory != null ? maxDeviceMemory : "0");
        AppUtils.setSpinnerSelectionFromValue(sPresentMode, presentMode != null ? presentMode : "mailbox");
        AppUtils.setSpinnerSelectionFromValue(sResourceType, selectedResourceType != null ? selectedResourceType : "auto");
        AppUtils.setSpinnerSelectionFromValue(sBCnEmulation, bcnEmulation != null ? bcnEmulation : "auto");
        AppUtils.setSpinnerSelectionFromValue(sBCnEmulationType, bcnEmulationType != null ? bcnEmulationType : "compute");
        AppUtils.setSpinnerSelectionFromValue(sBCnEmulationCache, bcnEmulationCache != null ? bcnEmulationCache : "0");

        Log.d(TAG, "Spinner selected position: " + sVersion.getSelectedItemPosition());
        Log.d(TAG, "Spinner selected value: " + sVersion.getSelectedItem());
    }

    private static void applyThemedAdapter(Context context, Spinner spinner, int arrayResId) {
        String[] items = context.getResources().getStringArray(arrayResId);
        AppUtils.setupThemedSpinner(spinner, context, Arrays.asList(items));
    }

    private void setSpinnerSelectionWithFallback(Spinner spinner, String version, String graphicsDriver) {
        if (spinner.getCount() == 0) return;

        if (version != null && !version.isEmpty()) {
            for (int i = 0; i < spinner.getCount(); i++) {
                Object item = spinner.getItemAtPosition(i);
                if (item != null && item.toString().equalsIgnoreCase(version)) {
                    spinner.setSelection(i);
                    return;
                }
            }
        }

        try {
            String fallback = GPUInformation.isDriverSupported(DefaultVersion.WRAPPER_ADRENO, getContext()) ? DefaultVersion.WRAPPER_ADRENO : DefaultVersion.WRAPPER;
            AppUtils.setSpinnerSelectionFromValue(spinner, fallback);
        } catch (Throwable e) {
            Log.w(TAG, "Error in setSpinnerSelectionWithFallback, using first item", e);
            spinner.setSelection(0);
        }
    }
}
