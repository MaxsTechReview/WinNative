/* Language selection helper for the in-app language picker.
 *
 * Uses AppCompatDelegate.setApplicationLocales() which:
 *   - On Android 13+ (API 33+): delegates to the platform LocaleManager, so
 *     Android stores the preference and re-applies it on every launch.
 *   - On Android 12 and below: the AppCompat backport handles persistence
 *     via AppLocalesMetadataHolderService (declared in AndroidManifest.xml)
 *     with autoStoreLocales=true, and the saved locale is automatically
 *     applied during AppCompatActivity startup.
 *
 * Supported language tags must match res/xml/locales_config.xml. */
package com.winlator.cmod.shared.android
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleHelper {
    /** BCP-47 language tags supported by the app. Keep in sync with
     *  res/xml/locales_config.xml and the res/values-* folders. */
    val SUPPORTED_LANGUAGE_TAGS: List<String> =
        listOf(
            "en",
            "da",
            "de",
            "es",
            "fr",
            "it",
            "ko",
            "pl",
            "pt-BR",
            "ro",
            "uk",
            "zh-CN",
            "zh-TW",
        )

    /** Native-language display names, indexed parallel to SUPPORTED_LANGUAGE_TAGS.
     *  Shown as-is in the dropdown so users can always recognize their own language. */
    val NATIVE_LANGUAGE_NAMES: List<String> =
        listOf(
            "English",
            "Dansk",
            "Deutsch",
            "Español",
            "Français",
            "Italiano",
            "한국어",
            "Polski",
            "Português (Brasil)",
            "Română",
            "Українська",
            "简体中文",
            "繁體中文",
        )

    /** The language tag currently applied via AppCompatDelegate, or null if
     *  the app is following the system default. */
    fun getAppliedLanguageTag(): String? {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return null
        return locales.toLanguageTags()
    }

    /** Apply the given language tag app-wide. Pass null to follow the system. */
    fun applyLanguageTag(tag: String?) {
        val list =
            if (tag.isNullOrEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(tag)
            }
        AppCompatDelegate.setApplicationLocales(list)
    }

    /** 0 = System default; 1..N map to SUPPORTED_LANGUAGE_TAGS. */
    fun indexForTag(tag: String?): Int {
        if (tag.isNullOrEmpty()) return 0
        // Normalize region separator — getApplicationLocales() returns tags like "pt-BR",
        // which should match our list; fall back to primary subtag if not found.
        val exact = SUPPORTED_LANGUAGE_TAGS.indexOf(tag)
        if (exact >= 0) return exact + 1
        val primary = tag.substringBefore('-')
        val byPrimary = SUPPORTED_LANGUAGE_TAGS.indexOfFirst { it == primary }
        return if (byPrimary >= 0) byPrimary + 1 else 0
    }

    /** Inverse of indexForTag: 0 returns null (system); 1..N returns the tag. */
    fun tagForIndex(index: Int): String? {
        if (index <= 0) return null
        val adjusted = index - 1
        return SUPPORTED_LANGUAGE_TAGS.getOrNull(adjusted)
    }
}
