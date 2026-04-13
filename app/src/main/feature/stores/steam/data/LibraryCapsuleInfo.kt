package com.winlator.cmod.feature.stores.steam.data
import com.winlator.cmod.feature.stores.steam.enums.Language
import kotlinx.serialization.Serializable

@Serializable
data class LibraryCapsuleInfo(
    val image: Map<Language, String> = emptyMap(),
    val image2x: Map<Language, String> = emptyMap(),
)
