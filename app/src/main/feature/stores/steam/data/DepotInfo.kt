package com.winlator.cmod.feature.stores.steam.data
import com.winlator.cmod.feature.stores.steam.db.serializers.OsEnumSetSerializer
import com.winlator.cmod.feature.stores.steam.enums.OS
import com.winlator.cmod.feature.stores.steam.enums.OSArch
import com.winlator.cmod.feature.stores.steam.service.SteamService.Companion.INVALID_APP_ID
import kotlinx.serialization.Serializable
import java.util.EnumSet

@Serializable
data class DepotInfo(
    val depotId: Int,
    val dlcAppId: Int = INVALID_APP_ID,
    val depotFromApp: Int = INVALID_APP_ID,
    val sharedInstall: Boolean = false,
    @Serializable(with = OsEnumSetSerializer::class)
    val osList: java.util.EnumSet<OS> = java.util.EnumSet.of(OS.none),
    val osArch: OSArch = OSArch.Unknown,
    val manifests: Map<String, ManifestInfo> = emptyMap(),
    val encryptedManifests: Map<String, ManifestInfo> = emptyMap(),
    val language: String = "",
    val realm: String = "",
    val optionalDlcId: Int = INVALID_APP_ID,
)
