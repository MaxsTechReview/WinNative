package com.winlator.cmod.feature.settings;

import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.winlator.cmod.R;
import com.winlator.cmod.runtime.container.Container;
import com.winlator.cmod.shared.android.AppUtils;
import com.winlator.cmod.shared.io.AssetPaths;
import com.winlator.cmod.shared.ui.dialog.ContentDialog;
import com.winlator.cmod.runtime.wine.EnvVars;
import com.winlator.cmod.shared.io.FileUtils;
import com.winlator.cmod.shared.util.KeyValueSet;
import com.winlator.cmod.shared.util.StringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class WineD3DConfigDialog extends ContentDialog {
    public static String DEFAULT_CONFIG = Container.DEFAULT_DXWRAPPERCONFIG;
    public static String[] csmtValues = { "Enabled", "Disabled" };
    public static String[] strictShaderMathValues = { "Enabled", "Disabled" };
    public static String[] offscreenRenderingModeValues = { "fbo", "backbuffer" };
    public static String[] rendererValues = { "gl", "vulkan", "gdi" };
    private Context context;

    public WineD3DConfigDialog(View anchor) {
        super(anchor.getContext(), R.layout.wined3d_config_dialog);
        context = anchor.getContext();
        setIcon(R.drawable.icon_settings);
        setTitle("WineD3D " + context.getString(R.string.container_config_title));

        final Spinner sCSMT = findViewById(R.id.SCSMT);
        final Spinner sGPUName = findViewById(R.id.SGPUName);
        final Spinner sVideoMemorySize = findViewById(R.id.SVideoMemorySize);
        final Spinner sStrictShaderMath = findViewById(R.id.SStrictShaderMath);
        final Spinner sOffscreenRenderingMode = findViewById(R.id.SOffscreenRenderingMode);
        final Spinner sRenderer = findViewById(R.id.SRenderer);

        ArrayAdapter<String> csmtAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, csmtValues);
        sCSMT.setAdapter(csmtAdapter);

        ArrayAdapter<String> ssmAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, strictShaderMathValues);
        sStrictShaderMath.setAdapter(ssmAdapter);

        ArrayAdapter<String> ormAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, offscreenRenderingModeValues);
        sOffscreenRenderingMode.setAdapter(ormAdapter);

        ArrayAdapter<String> rendererAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, rendererValues);
        sRenderer.setAdapter(rendererAdapter);

        loadGPUNameSpinner(sGPUName);

        KeyValueSet config = parseConfig(anchor.getTag());

        sCSMT.setSelection(config.get("csmt").equals("3") ? 0 : 1);
        sStrictShaderMath.setSelection(config.get("strict_shader_math").equals("1") ? 0 : 1);
        AppUtils.setSpinnerSelectionFromValue(sOffscreenRenderingMode, config.get("OffscreenRenderingMode"));
        AppUtils.setSpinnerSelectionFromValue(sGPUName, config.get("gpuName"));
        AppUtils.setSpinnerSelectionFromValue(sRenderer, config.get("renderer"));
        AppUtils.setSpinnerSelectionFromNumber(sVideoMemorySize, config.get("videoMemorySize"));

        setOnConfirmCallback(() -> {
            config.put("csmt", sCSMT.getSelectedItem() != null && sCSMT.getSelectedItem().toString().equals("Enabled") ? "3": "0");
            config.put("strict_shader_math", sStrictShaderMath.getSelectedItem() != null && sStrictShaderMath.getSelectedItem().toString().equals("Enabled") ? "1" : "0");
            config.put("OffscreenRenderingMode", sOffscreenRenderingMode.getSelectedItem() != null ? sOffscreenRenderingMode.getSelectedItem().toString() : "fbo");
            config.put("gpuName", sGPUName.getSelectedItem() != null ? sGPUName.getSelectedItem().toString() : "");
            config.put("videoMemorySize", sVideoMemorySize.getSelectedItem() != null ? StringUtils.parseNumber(sVideoMemorySize.getSelectedItem().toString()) : "0");
            config.put("renderer", sRenderer.getSelectedItem() != null ? sRenderer.getSelectedItem().toString() : "gl");
            anchor.setTag(config.toString());
        });

    }

    public static KeyValueSet parseConfig(Object config) {
        String data = config != null && !config.toString().isEmpty() ? config.toString() :  DEFAULT_CONFIG;
        return new KeyValueSet(data);
    }

    private void loadGPUNameSpinner(Spinner spinner)  {
        String gpuNameList = FileUtils.readString(context, AssetPaths.GPU_CARDS);
        ArrayList<String> entries = new ArrayList<>();

        try {
            JSONArray jarray = new JSONArray(gpuNameList);
            for (int i = 0; i < jarray.length(); i++) {
                JSONObject jobj = jarray.getJSONObject(i);
                String gpuName = jobj.getString("name");
                entries.add(gpuName);
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, entries);
            spinner.setAdapter(adapter);
        }
        catch (JSONException e) {
        }
    }

    public static String getDeviceIdFromGPUName(Context context, String gpuName) {
        String gpuNameList = FileUtils.readString(context, AssetPaths.GPU_CARDS);
        String deviceId = "";
        try {
            JSONArray jsonArray = new JSONArray(gpuNameList);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jobj = jsonArray.getJSONObject(i);
                if (jobj.getString("name").contains(gpuName)) {
                    deviceId = jobj.getString("deviceID");
                }
            }
        }
        catch (JSONException e) {
        }

        return deviceId;
    }

    public static String getVendorIdFromGPUName(Context context, String gpuName) {
        String gpuNameList = FileUtils.readString(context, AssetPaths.GPU_CARDS);
        String vendorId = "";
        try {
            JSONArray jsonArray = new JSONArray(gpuNameList);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jobj = jsonArray.getJSONObject(i);
                if (jobj.getString("name").contains(gpuName)) {
                    vendorId = jobj.getString("vendorID");
                }
            }
        }
        catch (JSONException e) {
        }

        return vendorId;
    }

    public static void setEnvVars(Context context, KeyValueSet config, EnvVars vars) {
        String deviceID = getDeviceIdFromGPUName(context, config.get("gpuName"));
        String vendorID = getVendorIdFromGPUName(context, config.get("vendorID"));
        String wined3dConfig = "csmt=0x" + config.get("csmt") + ",strict_shader_math=0x" + config.get("strict_shader_math") + ",OffscreenRenderingMode=" + config.get("OffscreenRenderingMode") + ",VideoMemorySize=" + config.get("videoMemorySize") + ",VideoPciDeviceID=" + deviceID + ",VideoPciVendorID=" + vendorID + ",renderer=" + config.get("renderer");
        vars.put("WINE_D3D_CONFIG", wined3dConfig);
    }
}
