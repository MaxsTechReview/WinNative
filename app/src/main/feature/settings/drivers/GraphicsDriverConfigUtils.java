package com.winlator.cmod.feature.settings;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public final class GraphicsDriverConfigUtils {
    private static final String TAG = "GraphicsDriverConfigUtil";

    private GraphicsDriverConfigUtils() {}

    public static HashMap<String, String> parseGraphicsDriverConfig(String graphicsDriverConfig) {
        HashMap<String, String> mappedConfig = new HashMap<>();
        if (graphicsDriverConfig == null || graphicsDriverConfig.isEmpty()) {
            return mappedConfig;
        }
        try {
            String[] configElements = graphicsDriverConfig.split(";");
            for (String element : configElements) {
                if (element == null || element.trim().isEmpty()) continue;
                String[] splittedElement = element.split("=", 2);
                String key = splittedElement[0];
                String value = splittedElement.length > 1 ? splittedElement[1] : "";
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
}
