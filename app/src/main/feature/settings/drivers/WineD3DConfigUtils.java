package com.winlator.cmod.feature.settings;

import android.content.Context;

import com.winlator.cmod.runtime.container.Container;
import com.winlator.cmod.runtime.wine.EnvVars;
import com.winlator.cmod.shared.io.AssetPaths;
import com.winlator.cmod.shared.io.FileUtils;
import com.winlator.cmod.shared.util.KeyValueSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class WineD3DConfigUtils {
    public static final String DEFAULT_CONFIG = Container.DEFAULT_DXWRAPPERCONFIG;

    private WineD3DConfigUtils() {}

    public static KeyValueSet parseConfig(Object config) {
        String data = config != null && !config.toString().isEmpty() ? config.toString() : DEFAULT_CONFIG;
        return new KeyValueSet(data);
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
