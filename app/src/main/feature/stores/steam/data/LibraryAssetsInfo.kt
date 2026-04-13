package com.winlator.cmod.feature.stores.steam.data
import kotlinx.serialization.Serializable

@Serializable
data class LibraryAssetsInfo(
    val libraryCapsule: LibraryCapsuleInfo = LibraryCapsuleInfo(),
    val libraryHero: LibraryHeroInfo = LibraryHeroInfo(),
    val libraryLogo: LibraryLogoInfo = LibraryLogoInfo(),
)
