package com.winlator.cmod.feature.stores.steam.db.converters
import androidx.room.TypeConverter
import com.winlator.cmod.feature.stores.steam.data.UserFileInfo
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

class UserFileInfoListConverter {
    @TypeConverter
    fun fromUserFileInfoList(userFileInfoList: List<UserFileInfo>?): String? = userFileInfoList?.let { json.encodeToString(it) }

    @TypeConverter
    fun toUserFileInfoList(value: String?): List<UserFileInfo>? = value?.let { json.decodeFromString<List<UserFileInfo>>(it) }
}
