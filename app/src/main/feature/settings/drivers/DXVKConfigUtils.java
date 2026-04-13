package com.winlator.cmod.feature.settings;

import android.content.Context;

import com.winlator.cmod.runtime.container.Container;
import com.winlator.cmod.runtime.display.environment.ImageFs;
import com.winlator.cmod.runtime.wine.EnvVars;
import com.winlator.cmod.shared.util.KeyValueSet;

public final class DXVKConfigUtils {
    public static final String DEFAULT_CONFIG = Container.DEFAULT_DXWRAPPERCONFIG;
    public static final String[] VKD3D_FEATURE_LEVEL = {"12_0", "12_1", "12_2", "11_1", "11_0", "10_1", "10_0", "9_3", "9_2", "9_1"};

    private DXVKConfigUtils() {}

    public static KeyValueSet parseConfig(Object config) {
        String data = config != null && !config.toString().isEmpty() ? config.toString() : DEFAULT_CONFIG;
        return new KeyValueSet(data);
    }

    public static void setEnvVars(Context context, KeyValueSet config, EnvVars envVars) {
        setEnvVars(context, config, envVars, 0);
    }

    public static void setEnvVars(Context context, KeyValueSet config, EnvVars envVars, int refreshRateOverride) {
        String content = "";

        if (refreshRateOverride > 0) {
            String rateStr = String.valueOf(refreshRateOverride);
            content += "dxgi.syncInterval = 0; ";
            content += "dxgi.maxFrameRate = " + rateStr + "; ";
            content += "d3d9.maxFrameRate = " + rateStr;
            envVars.put("DXVK_FRAME_RATE", rateStr);
        } else {
            String framerate = config.get("framerate");

            if (!framerate.isEmpty() && !framerate.equals("0")) {
                content += "dxgi.maxFrameRate = " + framerate + "; ";
                content += "d3d9.maxFrameRate = " + framerate;
                envVars.put("DXVK_FRAME_RATE", framerate);
            }
        }

        String async = config.get("async");
        if (!async.isEmpty() && !async.equals("0")) {
            envVars.put("DXVK_ASYNC", "1");
        }

        String asyncCache = config.get("asyncCache");
        if (!asyncCache.isEmpty() && !asyncCache.equals("0")) {
            envVars.put("DXVK_GPLASYNCCACHE", "1");
        }

        if (!content.isEmpty()) {
            envVars.put("DXVK_CONFIG", content);
        }

        envVars.put("VKD3D_FEATURE_LEVEL", config.get("vkd3dLevel"));
        envVars.put("DXVK_STATE_CACHE_PATH", context.getFilesDir() + "/imagefs/" + ImageFs.CACHE_PATH);
    }
}
