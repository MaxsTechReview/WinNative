package com.winlator.cmod.feature.artwork

import android.net.Uri

data class GameArtworkInfo(
    val gameId: Int,
    val gameName: String,
    val gameStore: String,
    val gameCardImageUri: Uri,
    val gameGridImageUri: Uri,
    val gameCarouselImageUri: Uri,
    val gameListImageUri: Uri
)


abstract class ArtworkScraper() {
    abstract suspend fun getGameArtwork(gameName: String): GameArtworkInfo?
}