package com.winlator.cmod.feature.leaderboard

import java.util.Locale

/**
 * Reverse lookup for the 4-byte SHA-256-prefix hashes stored inside a [PerfDigest].
 *
 * On submit, a device hashes its (normalized) GPU/SoC strings and embeds the prefix in
 * the scoreTag. On display, the receiving client looks the hash up here to render a
 * human-readable label ("Adreno 830", "Snapdragon 8 Gen 3") instead of raw hex.
 *
 * Devices whose hardware isn't in this dictionary still submit successfully — the row
 * just renders as a short hex label until the dictionary is extended. Extending is
 * additive: append a new entry, ship, no schema bump needed.
 */
object HardwareDictionary {
    private val gpuModels: List<String> = listOf(
        "Adreno 830",
        "Adreno 750",
        "Adreno 740",
        "Adreno 735",
        "Adreno 732",
        "Adreno 730",
        "Adreno 725",
        "Adreno 720",
        "Adreno 710",
        "Adreno 660",
        "Adreno 650",
        "Adreno 644",
        "Adreno 643",
        "Adreno 642L",
        "Adreno 642",
        "Adreno 640",
        "Adreno 630",
        "Adreno 619",
        "Adreno 618",
        "Adreno 616",
        "Adreno 615",
        "Adreno 610",
        "Mali-G925",
        "Mali-G720",
        "Mali-G715",
        "Mali-G710",
        "Mali-G78",
        "Mali-G77",
        "Mali-G76",
        "Mali-G68",
        "Mali-G57",
        "Mali-G52",
        "Mali-G51",
        "Xclipse 940",
        "Xclipse 920",
        "PowerVR GE8320",
        "PowerVR GE8300",
        "PowerVR Rogue GE8320",
        "Immortalis-G925",
        "Immortalis-G720",
        "Immortalis-G715",
    )

    private val socModels: List<String> = listOf(
        "Snapdragon 8 Elite",
        "Snapdragon 8 Gen 3",
        "Snapdragon 8 Gen 2",
        "Snapdragon 8 Gen 1",
        "Snapdragon 8+ Gen 1",
        "Snapdragon 888",
        "Snapdragon 870",
        "Snapdragon 865",
        "Snapdragon 855",
        "Snapdragon 845",
        "Snapdragon 7 Gen 3",
        "Snapdragon 7 Gen 1",
        "Snapdragon 778G",
        "Snapdragon 765G",
        "Snapdragon 750G",
        "Snapdragon 720G",
        "Snapdragon 695",
        "Snapdragon 680",
        "Snapdragon 6 Gen 1",
        "Tensor G4",
        "Tensor G3",
        "Tensor G2",
        "Tensor G1",
        "Exynos 2400",
        "Exynos 2200",
        "Exynos 2100",
        "Exynos 990",
        "Exynos 9825",
        "Exynos 9820",
        "Dimensity 9300",
        "Dimensity 9200",
        "Dimensity 9000",
        "Dimensity 8300",
        "Dimensity 8200",
        "Dimensity 8100",
        "Dimensity 1300",
        "Dimensity 1200",
        "Dimensity 1100",
        "Dimensity 1000",
        "Dimensity 920",
        "Dimensity 900",
        "Dimensity 810",
        "Dimensity 800",
    )

    private val gpuLookup: Map<HashKey, String> = gpuModels.associate {
        HashKey(PerfDigest.shortHash(canonicalize(it))) to it
    }

    private val socLookup: Map<HashKey, String> = socModels.associate {
        HashKey(PerfDigest.shortHash(canonicalize(it))) to it
    }

    /** Hash a raw GPU renderer string (e.g., `GLES20.glGetString(GL_RENDERER)`). */
    fun gpuHashOf(rendererString: String): ByteArray =
        PerfDigest.shortHash(canonicalize(rendererString))

    /** Hash a raw SoC model string (e.g., `Build.SOC_MODEL`). */
    fun socHashOf(socModelString: String): ByteArray =
        PerfDigest.shortHash(canonicalize(socModelString))

    /** Look up a friendly label for a stored GPU hash; null if the hash isn't known. */
    fun gpuLabelForHash(hash: ByteArray): String? = gpuLookup[HashKey(hash)]

    /** Look up a friendly label for a stored SoC hash; null if the hash isn't known. */
    fun socLabelForHash(hash: ByteArray): String? = socLookup[HashKey(hash)]

    /**
     * Renders a hash as a short hex label ("ab12cd34") when the dictionary has no match.
     * Used as the visible fallback in the leaderboard UI so unknown hardware still shows
     * something useful instead of a blank cell.
     */
    fun hexLabel(hash: ByteArray): String =
        hash.joinToString("") { "%02x".format(it) }

    /**
     * Canonicalize free-form vendor strings so visually-equivalent variants hash to the
     * same prefix. Drops "(TM)", "(R)", whitespace runs collapse to a single space,
     * lowercase, and trim. Conservative on purpose — collapsing too aggressively risks
     * collapsing distinct SKUs (e.g., "Adreno 642" vs "Adreno 642L").
     */
    private fun canonicalize(raw: String): String =
        raw
            .replace("(TM)", "", ignoreCase = true)
            .replace("(R)", "", ignoreCase = true)
            .replace("\u2122", "")
            .replace("\u00AE", "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase(Locale.ROOT)

    /** ByteArray wrapper that supports proper hashCode/equals for use as a Map key. */
    private data class HashKey(val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean =
            this === other || (other is HashKey && bytes.contentEquals(other.bytes))

        override fun hashCode(): Int = bytes.contentHashCode()
    }
}
