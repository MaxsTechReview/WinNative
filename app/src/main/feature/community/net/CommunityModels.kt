package com.winlator.cmod.feature.community.net

import kotlinx.serialization.Serializable

@Serializable
data class ConfigSummary(
    val id: String,
    val resolution: String = "",
    val store: String = "",
    val uploaderHandle: String = "",
    val dxwrapper: String = "",
    val wineVersion: String = "",
    val deviceModel: String = "",
    val marketName: String = "",
    val up: Int = 0,
    val down: Int = 0,
    val myVote: Int = 0,
    val ownedByMe: Boolean = false,
    val schemaVersion: Int = 1,
    val createdAt: Long = 0,
)

@Serializable
data class ListResponse(
    val configs: List<ConfigSummary> = emptyList(),
    val filter: String = "chipset",
    val deviceDisplay: String = "",
)

@Serializable
data class UploadResult(
    val id: String = "",
    val exportName: String = "",
)

@Serializable
data class VoteResult(
    val up: Int = 0,
    val down: Int = 0,
    val myVote: Int = 0,
)

/** Filter modes for the download list. */
enum class CommunityFilter(val wire: String) {
    CHIPSET("chipset"), DEVICE("device"), ALL("all")
}
