package com.winlator.cmod.runtime.wine;

import java.util.Locale;

/* Normalizes container LC_ALL into a value glibc inside the imagefs will
 * actually accept. Empty stored value -> derive from the app's current
 * Locale.getDefault() (which AppCompatDelegate.setApplicationLocales has
 * already updated to reflect the Settings > Other > Language picker).
 * Missing country -> best-effort fallback. Missing encoding -> append
 * .UTF-8. Already-encoded values pass through unchanged. */
public final class LocaleEnv {
    private LocaleEnv() {}

    public static String normalize(String stored) {
        if (stored != null && !stored.isEmpty()) {
            return ensureEncoding(stored);
        }
        return deriveFromDevice();
    }

    public static String deriveFromDevice() {
        Locale locale = Locale.getDefault();
        String lang = locale.getLanguage();
        String country = locale.getCountry();
        if (country == null || country.isEmpty()) {
            country = defaultCountryFor(lang);
        }
        if (lang == null || lang.isEmpty() || country == null || country.isEmpty()) {
            return "C.UTF-8";
        }
        return lang + "_" + country + ".UTF-8";
    }

    private static String ensureEncoding(String value) {
        int dot = value.indexOf('.');
        if (dot >= 0) return value;
        return value + ".UTF-8";
    }

    private static String defaultCountryFor(String lang) {
        if (lang == null) return null;
        switch (lang) {
            case "en": return "US";
            case "da": return "DK";
            case "de": return "DE";
            case "es": return "ES";
            case "fr": return "FR";
            case "it": return "IT";
            case "ko": return "KR";
            case "pl": return "PL";
            case "pt": return "BR";
            case "ro": return "RO";
            case "uk": return "UA";
            case "ja": return "JP";
            case "ru": return "RU";
            case "ar": return "EG";
            case "zh": return "CN";
            default: return null;
        }
    }
}
