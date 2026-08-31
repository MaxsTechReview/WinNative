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
    val placement: Placement = Placement.LEADING,
) {
    enum class Placement { LEADING, TRAILING }

    companion object {
        val POPULAR = ItchFacet("", "Popular")

        val ALL =
            listOf(
                POPULAR,
                ItchFacet("new-and-popular", "New & Popular"),
                ItchFacet("newest", "Newest"),
                ItchFacet("top-rated", "Top Rated"),
                ItchFacet("top-sellers", "Top Sellers"),
                ItchFacet("free", "Free"),
                ItchFacet("on-sale", "On Sale"),
                ItchFacet("genre-action", "Action"),
                ItchFacet("genre-adventure", "Adventure"),
                ItchFacet("genre-rpg", "RPG"),
                ItchFacet("genre-platformer", "Platformer"),
                ItchFacet("genre-shooter", "Shooter"),
                ItchFacet("genre-puzzle", "Puzzle"),
                ItchFacet("genre-simulation", "Simulation"),
                ItchFacet("genre-strategy", "Strategy"),
                ItchFacet("genre-sports", "Sports"),
                ItchFacet("genre-visual-novel", "Visual Novel"),
                ItchFacet("tag-horror", "Horror", Placement.TRAILING),
                ItchFacet("tag-pixel-art", "Pixel Art", Placement.TRAILING),
                ItchFacet("tag-2d", "2D", Placement.TRAILING),
                ItchFacet("tag-3d", "3D", Placement.TRAILING),
                ItchFacet("tag-roguelike", "Roguelike", Placement.TRAILING),
                ItchFacet("tag-multiplayer", "Multiplayer", Placement.TRAILING),
                ItchFacet("tag-anime", "Anime", Placement.TRAILING),
                ItchFacet("tag-retro", "Retro", Placement.TRAILING),
                ItchFacet("tag-story-rich", "Story Rich", Placement.TRAILING),
                ItchFacet("tag-sandbox", "Sandbox", Placement.TRAILING),
                ItchFacet("tag-fangame", "Fangame", Placement.TRAILING),
            )
    }
}

data class ItchBrowseFilter(
    val facet: ItchFacet = ItchFacet.POPULAR,
    val windowsOnly: Boolean = true,
) {
    fun toPath(): String {
        val platform = if (windowsOnly) "platform-windows" else ""
        val segments =
            when {
                facet.segment.isEmpty() -> listOf(platform)
                facet.placement == ItchFacet.Placement.LEADING -> listOf(facet.segment, platform)
                else -> listOf(platform, facet.segment)
            }.filter { it.isNotEmpty() }
        return if (segments.isEmpty()) "games" else "games/" + segments.joinToString("/")
    }
}
