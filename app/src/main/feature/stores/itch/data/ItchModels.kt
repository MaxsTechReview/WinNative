package com.winlator.cmod.feature.stores.itch.data

enum class ItchPlatform {
    WINDOWS,
    LINUX,
    MACOS,
    ANDROID,
    WEB,
}

data class ItchGame(
    val id: Int,
    val title: String,
    val url: String,
    val coverUrl: String = "",
    val author: String = "",
    val shortText: String = "",
    val genre: String = "",
    val priceLabel: String = "",
    val onSale: Boolean = false,
    val platforms: Set<ItchPlatform> = emptySet(),
) {
    val isFree: Boolean get() = priceLabel.isBlank()

    val hasWindowsBuild: Boolean get() = ItchPlatform.WINDOWS in platforms

    val slug: String get() = url.trimEnd('/').substringAfterLast('/')

    val baseUrl: String get() = url.trimEnd('/').substringBeforeLast('/')
}

data class ItchUpload(
    val id: Long,
    val fileName: String,
    val sizeLabel: String,
    val sizeBytes: Long,
    val version: String,
    val platforms: Set<ItchPlatform>,
)

data class ItchGameDetails(
    val game: ItchGame,
    val heroImageUrl: String = "",
    val description: String = "",
    val screenshots: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val infoRows: List<Pair<String, String>> = emptyList(),
    val minPriceCents: Int? = null,
)

data class ItchFacet(
    val segment: String,
    val label: String,
    val kind: Kind = Kind.SORT,
) {
    enum class Kind { SORT, GENRE, TAG, OWNED }

    companion object {
        val POPULAR = ItchFacet("", "Popular")
        val OWNED = ItchFacet("owned", "Owned", Kind.OWNED)

        private val BROWSABLE =
            listOf(
                POPULAR,
                ItchFacet("new-and-popular", "New & Popular"),
                ItchFacet("newest", "Newest"),
                ItchFacet("top-rated", "Top Rated"),
                ItchFacet("top-sellers", "Top Sellers"),
                ItchFacet("genre-action", "Action", Kind.GENRE),
                ItchFacet("genre-adventure", "Adventure", Kind.GENRE),
                ItchFacet("genre-rpg", "RPG", Kind.GENRE),
                ItchFacet("genre-platformer", "Platformer", Kind.GENRE),
                ItchFacet("genre-shooter", "Shooter", Kind.GENRE),
                ItchFacet("genre-puzzle", "Puzzle", Kind.GENRE),
                ItchFacet("genre-simulation", "Simulation", Kind.GENRE),
                ItchFacet("genre-strategy", "Strategy", Kind.GENRE),
                ItchFacet("genre-sports", "Sports", Kind.GENRE),
                ItchFacet("genre-visual-novel", "Visual Novel", Kind.GENRE),
                ItchFacet("tag-horror", "Horror", Kind.TAG),
                ItchFacet("tag-pixel-art", "Pixel Art", Kind.TAG),
                ItchFacet("tag-2d", "2D", Kind.TAG),
                ItchFacet("tag-3d", "3D", Kind.TAG),
                ItchFacet("tag-roguelike", "Roguelike", Kind.TAG),
                ItchFacet("tag-multiplayer", "Multiplayer", Kind.TAG),
                ItchFacet("tag-anime", "Anime", Kind.TAG),
                ItchFacet("tag-retro", "Retro", Kind.TAG),
                ItchFacet("tag-story-rich", "Story Rich", Kind.TAG),
                ItchFacet("tag-sandbox", "Sandbox", Kind.TAG),
                ItchFacet("tag-fangame", "Fangame", Kind.TAG),
            )

        fun visible(signedIn: Boolean): List<ItchFacet> = if (signedIn) listOf(POPULAR, OWNED) + BROWSABLE.drop(1) else BROWSABLE
    }
}

data class ItchBrowseFilter(
    val facet: ItchFacet = ItchFacet.POPULAR,
    val windowsOnly: Boolean = true,
) {
    val isOwned: Boolean get() = facet.kind == ItchFacet.Kind.OWNED

    fun toPath(): String {
        val segments =
            when (facet.kind) {
                ItchFacet.Kind.OWNED -> listOf(FREE_SEGMENT)
                ItchFacet.Kind.SORT -> listOf(facet.segment, FREE_SEGMENT)
                else -> listOf(FREE_SEGMENT, facet.segment)
            }.filter { it.isNotEmpty() }
        return "games/" + segments.joinToString("/")
    }

    private companion object {
        const val FREE_SEGMENT = "free"
    }
}
