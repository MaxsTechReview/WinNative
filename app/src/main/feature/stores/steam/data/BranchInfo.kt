package com.winlator.cmod.feature.stores.steam.data
import com.winlator.cmod.feature.stores.steam.db.serializers.DateSerializer
import kotlinx.serialization.Serializable
import java.util.Date

@Serializable
data class BranchInfo(
    val name: String,
    val buildId: Long,
    val pwdRequired: Boolean = false,
    @Serializable(with = DateSerializer::class)
    val timeUpdated: Date? = null,
)
