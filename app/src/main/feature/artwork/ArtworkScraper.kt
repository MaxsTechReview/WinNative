package com.winlator.cmod.feature.artwork

import java.io.File

data class GameArtworkInfo(
    val gameId: Int,
    val gameName: String,
    val gameStore: String,
    val gameCardImageFile: File,
    val gameGridImageFile: File,
    val gameCarouselImageFile: File,
    val gameListImageFile: File
)


abstract class ArtworkScraper() {
    abstract suspend fun getGameArtwork(gameName: String): GameArtworkInfo?
}