package com.winlator.cmod.feature.artwork

import java.io.File

abstract class ArtworkScraper() {
    abstract suspend fun getGameArtwork(gameName: String): MutableMap<String, File>
}
