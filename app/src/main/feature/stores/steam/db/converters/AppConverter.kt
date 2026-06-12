package com.winlator.cmod.feature.stores.steam.db.converters
import androidx.room.TypeConverter
import com.winlator.cmod.feature.stores.steam.data.BranchInfo
import com.winlator.cmod.feature.stores.steam.data.ConfigInfo
import com.winlator.cmod.feature.stores.steam.data.DepotInfo
import com.winlator.cmod.feature.stores.steam.data.LibraryAssetsInfo
import com.winlator.cmod.feature.stores.steam.data.UFS
import com.winlator.cmod.feature.stores.steam.enums.AppType
import com.winlator.cmod.feature.stores.steam.enums.ControllerSupport
import com.winlator.cmod.feature.stores.steam.enums.Language
import com.winlator.cmod.feature.stores.steam.enums.OS
import com.winlator.cmod.feature.stores.steam.enums.ReleaseState
import kotlinx.serialization.json.Json
import java.util.EnumSet

private val json = Json { ignoreUnknownKeys = true }

class AppConverter {
    @TypeConverter
    fun toAppType(appType: Int): AppType = AppType.fromCode(appType)

    @TypeConverter
    fun fromAppType(appType: AppType): Int = appType.code

    @TypeConverter
    fun toOS(os: Int): EnumSet<OS> = OS.from(os)

    @TypeConverter
    fun fromOS(os: EnumSet<OS>): Int = OS.code(os)

    @TypeConverter
    fun toReleaseState(releaseState: Int): ReleaseState = ReleaseState.from(releaseState)

    @TypeConverter
    fun fromReleaseState(releaseState: ReleaseState): Int = releaseState.code

    @TypeConverter
    fun toControllerSupport(controllerSupport: Int): ControllerSupport = ControllerSupport.from(controllerSupport)

    @TypeConverter
    fun fromControllerSupport(controllerSupport: ControllerSupport): Int = controllerSupport.code

    @TypeConverter
    fun toDepots(depots: String): Map<Int, DepotInfo> = json.decodeFromString<Map<Int, DepotInfo>>(depots)

    @TypeConverter
    fun fromDepots(depots: Map<Int, DepotInfo>): String = json.encodeToString(depots)

    @TypeConverter
    fun toBranches(branches: String): Map<String, BranchInfo> = json.decodeFromString<Map<String, BranchInfo>>(branches)

    @TypeConverter
    fun fromBranches(branches: Map<String, BranchInfo>): String = json.encodeToString(branches)

    @TypeConverter
    fun toLangMap(langMap: String): Map<Language, String> = json.decodeFromString<Map<Language, String>>(langMap)

    @TypeConverter
    fun fromLangMap(langMap: Map<Language, String>): String = json.encodeToString(langMap)

    @TypeConverter
    fun toLibraryAssetsInfo(langMap: String): LibraryAssetsInfo = json.decodeFromString<LibraryAssetsInfo>(langMap)

    @TypeConverter
    fun fromLibraryAssetsInfo(langMap: LibraryAssetsInfo): String = json.encodeToString(langMap)

    @TypeConverter
    fun toConfigInfo(configInfo: String): ConfigInfo = json.decodeFromString<ConfigInfo>(configInfo)

    @TypeConverter
    fun fromConfigInfo(configInfo: ConfigInfo): String = json.encodeToString(configInfo)

    @TypeConverter
    fun toUFS(ufs: String): UFS = json.decodeFromString<UFS>(ufs)

    @TypeConverter
    fun fromUFS(ufs: UFS): String = json.encodeToString(ufs)
}
