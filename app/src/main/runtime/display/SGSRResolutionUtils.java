package com.winlator.cmod.runtime.display;

public final class SGSRResolutionUtils {
    private SGSRResolutionUtils() {}

    public static float getUpscaleFactor(int mode) {
        switch (mode) {
            case 1: return 1.0f;
            case 2: return 1.25f;
            case 3: return 1.3333334f;
            case 4: return 1.5f;
            case 5: return 1.6666667f;
            case 6: return 2.0f;
            default: return 1.0f;
        }
    }

    public static int clampUpscaleMode(int mode) {
        return Math.max(1, Math.min(6, mode));
    }

    public static int normalizeShortcutUpscaleMode(int mode) {
        return clampUpscaleMode(mode);
    }

    public static boolean modeAdjustsScreenSize(int mode) {
        return mode >= 2 && mode <= 6;
    }

    public static int[] parseScreenSize(String value) {
        if (value == null || value.isEmpty()) return null;
        String[] parts = value.split("x");
        if (parts.length != 2) return null;
        try {
            int width = Integer.parseInt(parts[0].trim());
            int height = Integer.parseInt(parts[1].trim());
            if (width <= 0 || height <= 0) return null;
            return new int[]{width, height};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String applyRenderScale(String baseScreenSize, boolean sgsrEnabled, int mode) {
        if (baseScreenSize == null || baseScreenSize.isEmpty()) return baseScreenSize;
        if (!sgsrEnabled) return baseScreenSize;
        if (!modeAdjustsScreenSize(mode)) return baseScreenSize;

        float factor = getUpscaleFactor(mode);
        if (factor <= 1.0f) return baseScreenSize;

        int[] base = parseScreenSize(baseScreenSize);
        if (base == null) return baseScreenSize;

        int scaledW = Math.max(320, Math.round((float) base[0] / factor));
        int scaledH = Math.max(180, Math.round((float) base[1] / factor));
        return evenDimension(scaledW) + "x" + evenDimension(scaledH);
    }

    private static int evenDimension(int value) {
        return (value & 1) == 0 ? value : value + 1;
    }
}
