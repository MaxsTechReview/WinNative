package com.winlator.cmod.feature.stores.steam.db.converters
import androidx.room.TypeConverter
import com.winlator.cmod.feature.stores.steam.data.UserFileInfo
import kotlinx.serialization.json.Json

// Tolerant of rows written by a build with a different schema; an unreadable row decodes to
// null rather than throwing and breaking the owning app lookup. See AppConverter for details.
private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

class UserFileInfoListConverter {
    @TypeConverter
    fun fromUserFileInfoList(userFileInfoList: List<UserFileInfo>?): String? = userFileInfoList?.let { json.encodeToString(it) }

    @TypeConverter
    fun toUserFileInfoList(value: String?): List<UserFileInfo>? =
        value?.let { runCatching { json.decodeFromString<List<UserFileInfo>>(it) }.getOrNull() }
}
