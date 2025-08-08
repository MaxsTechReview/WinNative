package com.winlator.cmod.core;

import java.util.Locale;

public abstract class GPUInformation {

    public static boolean isAdreno6xx() {
        return getRenderer().toLowerCase(Locale.ENGLISH).matches(".*adreno[^6]+6[0-9]{2}.*");
    }

    public static boolean isAdreno7xx() {
        return getRenderer().toLowerCase(Locale.ENGLISH).matches(".*adreno[^7]+7[0-9]{2}.*");
    }

    public static boolean isAdreno8xx() {
        return getRenderer().toLowerCase(Locale.ENGLISH).matches(".*adreno[^8]+8[0-9]{2}.*");
    }

    public native static String getVersion();
    public native static String getRenderer();
    public native static long getMemorySize();
    public native static String[] enumerateExtensions();

    static {
        System.loadLibrary("winlator");
    }
}
