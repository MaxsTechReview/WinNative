package com.winlator.cmod.feature.stores.steam.enums
import timber.log.Timber

enum class Language(val displayName: String) {
    english("English"),
    german("German"),
    french("French"),
    italian("Italian"),
    koreana("Korean"),
    spanish("Spanish"),
    schinese("Simplified Chinese"),
    sc_schinese("Simplified Chinese (SC)"),
    tchinese("Traditional Chinese"),
    russian("Russian"),
    japanese("Japanese"),
    polish("Polish"),
    brazilian("Portuguese (Brazil)"),
    latam("Spanish (Latin America)"),
    vietnamese("Vietnamese"),
    portuguese("Portuguese"),
    danish("Danish"),
    dutch("Dutch"),
    swedish("Swedish"),
    norwegian("Norwegian"),
    finnish("Finnish"),
    turkish("Turkish"),
    thai("Thai"),
    czech("Czech"),
    unknown("Unknown"),
    ;

    companion object {
        /** All display names in enum order, excluding [unknown] and the non-standard [sc_schinese] duplicate. */
        fun displayLabels(): List<String> =
            entries.filter { it != unknown && it != sc_schinese }.map { it.displayName }

        /** Index of the entry whose [name] matches [containerLang], or the index of [english] as fallback. */
        fun indexForContainerLang(containerLang: String?): Int {
            val code = containerLang?.lowercase() ?: return 0
            val filtered = entries.filter { it != unknown && it != sc_schinese }
            val match = filtered.indexOfFirst { it.name == code }
            return if (match >= 0) match else 0
        }

        /** Container language enum name for a given display-name [index] (0-based into the filtered list). */
        fun containerLangForIndex(index: Int): String {
            val filtered = entries.filter { it != unknown && it != sc_schinese }
            return filtered.getOrNull(index)?.name ?: english.name
        }

        fun from(keyValue: String?): Language =
            when (keyValue?.lowercase()) {
                english.name -> {
                    english
                }

                german.name -> {
                    german
                }

                french.name -> {
                    french
                }

                italian.name -> {
                    italian
                }

                koreana.name -> {
                    koreana
                }

                spanish.name -> {
                    spanish
                }

                schinese.name -> {
                    schinese
                }

                sc_schinese.name -> {
                    sc_schinese
                }

                tchinese.name -> {
                    tchinese
                }

                russian.name -> {
                    russian
                }

                japanese.name -> {
                    japanese
                }

                polish.name -> {
                    polish
                }

                brazilian.name -> {
                    brazilian
                }

                latam.name -> {
                    latam
                }

                vietnamese.name -> {
                    vietnamese
                }

                portuguese.name -> {
                    portuguese
                }

                danish.name -> {
                    danish
                }

                dutch.name -> {
                    dutch
                }

                swedish.name -> {
                    swedish
                }

                norwegian.name -> {
                    norwegian
                }

                finnish.name -> {
                    finnish
                }

                turkish.name -> {
                    turkish
                }

                thai.name -> {
                    thai
                }

                czech.name -> {
                    czech
                }

                unknown.name -> {
                    unknown
                }

                else -> {
                    if (keyValue != null) {
                        Timber.e("Could not find proper Language from $keyValue")
                    }
                    unknown
                }
            }
    }
}
