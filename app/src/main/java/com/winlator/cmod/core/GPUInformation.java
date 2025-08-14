package com.winlator.cmod.core;

import android.content.Context;

import java.util.Locale;

public abstract class GPUInformation {

    public static boolean isAdreno6xx() {
        return getRenderer(null, null).toLowerCase(Locale.ENGLISH).matches(".*adreno[^6]+6[0-9]{2}.*");
    }

    public static boolean isAdreno7xx() {
        return getRenderer(null, null).toLowerCase(Locale.ENGLISH).matches(".*adreno[^7]+7[0-9]{2}.*");
    }

    public static boolean isAdreno8xx() {
        return getRenderer(null, null).toLowerCase(Locale.ENGLISH).matches(".*adreno[^8]+8[0-9]{2}.*");
    }

    public native static String getVersion(String driverName, Context context);
    public native static String getVulkanVersion(String driverName, Context context);
    public native static String getRenderer(String driverName, Context context);
    public native static long getMemorySize(String driverName, Context context);
    public native static String[] enumerateExtensions(String driverName, Context context);

    static {
        System.loadLibrary("winlator");
    }
}
