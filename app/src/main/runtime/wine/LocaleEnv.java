package com.winlator.cmod.runtime.wine;

import java.util.Locale;

/* Guest locale environment normalization.
 *
 * The imagefs ships no glibc locale data, so requesting a real locale (e.g.
 * zh_CN.UTF-8) makes setlocale() fail and silently fall back to plain "C"
 * (ASCII-only charset), breaking every non-ASCII path/filename passed on
 * the wine command line (shortcut names, game directories, exe paths).
 *
 * LC_ALL is therefore pinned to glibc's built-in C.UTF-8 (needs no locale
 * data); the user's original locale (stored container/shortcut value, or
 * the device locale when empty) is exported via LANG instead. */
public final class LocaleEnv {
    private LocaleEnv() {}

    public static String normalize() {
        return "C.UTF-8";
    }

    public static String normalizeLang(String stored) {
        if (stored != null && !stored.isEmpty()) {
            return ensureEncoding(stored);
        }
        return deriveFromDevice();
    }

    /* Converts a stored LC_ALL value (e.g. "ja_JP", "zh_CN.UTF-8") into the
     * BCP-47 locale name ("ja-JP") accepted by Wine 10's SxS activeCodePage
     * application setting. bionic's setlocale() only recognizes C/POSIX/
     * C.UTF-8, so Wine can never perceive the real locale via LANG/LC_ALL
     * and the ANSI code page is fixed to 1252; however, Wine 10's ntdll
     * locale_init passes any locale name in activeCodePage to
     * find_lcname_entry for an NLS table lookup, adopting the locale's
     * default ANSI/OEM code page (e.g. ja-JP -> 932), equivalent to Locale
     * Emulator on Windows. Returns "" for empty values, pseudo-locales and
     * invalid inputs (meaning: no override). */
    public static String toBcp47(String stored) {
        if (stored == null) return "";
        String value = stored.trim();
        int dot = value.indexOf('.');
        if (dot >= 0) value = value.substring(0, dot);
        int at = value.indexOf('@');
        if (at >= 0) value = value.substring(0, at);
        value = value.trim().replace('_', '-');
        if (value.isEmpty() || value.equalsIgnoreCase("C")
                || value.equalsIgnoreCase("POSIX") || value.equalsIgnoreCase("C-UTF-8")) return "";
        if (!value.matches("^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$")) return "";
        return value;
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
