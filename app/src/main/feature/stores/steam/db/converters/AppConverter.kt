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

// A cached row may have been written by a build whose schema differs (e.g. a field present
// only in another branch). The strict default Json throws on such rows, which previously broke
// app lookup and any download depending on it. ignoreUnknownKeys + coerceInputValues decode
// drifted rows without data loss; the per-converter fallback below keeps a truly unreadable row
// from aborting the read (the row is re-fetched from PICS instead). Encoding is left at the
// kotlinx default (defaults omitted) so rows stay readable by other builds.
private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

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
    fun toDepots(depots: String): Map<Int, DepotInfo> =
        runCatching { json.decodeFromString<Map<Int, DepotInfo>>(depots) }.getOrDefault(emptyMap())

    @TypeConverter
    fun fromDepots(depots: Map<Int, DepotInfo>): String = json.encodeToString(depots)

    @TypeConverter
    fun toBranches(branches: String): Map<String, BranchInfo> =
        runCatching { json.decodeFromString<Map<String, BranchInfo>>(branches) }.getOrDefault(emptyMap())

    @TypeConverter
    fun fromBranches(branches: Map<String, BranchInfo>): String = json.encodeToString(branches)

    @TypeConverter
    fun toLangMap(langMap: String): Map<Language, String> =
        runCatching { json.decodeFromString<Map<Language, String>>(langMap) }.getOrDefault(emptyMap())

    @TypeConverter
    fun fromLangMap(langMap: Map<Language, String>): String = json.encodeToString(langMap)

    @TypeConverter
    fun toLibraryAssetsInfo(langMap: String): LibraryAssetsInfo =
        runCatching { json.decodeFromString<LibraryAssetsInfo>(langMap) }.getOrDefault(LibraryAssetsInfo())

    @TypeConverter
    fun fromLibraryAssetsInfo(langMap: LibraryAssetsInfo): String = json.encodeToString(langMap)

    @TypeConverter
    fun toConfigInfo(configInfo: String): ConfigInfo =
        runCatching { json.decodeFromString<ConfigInfo>(configInfo) }.getOrDefault(ConfigInfo())

    @TypeConverter
    fun fromConfigInfo(configInfo: ConfigInfo): String = json.encodeToString(configInfo)

    @TypeConverter
    fun toUFS(ufs: String): UFS =
        runCatching { json.decodeFromString<UFS>(ufs) }.getOrDefault(UFS())

    @TypeConverter
    fun fromUFS(ufs: UFS): String = json.encodeToString(ufs)
}
