package com.winlator.cmod.epic.db.converters

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

class EpicConverter {
    @TypeConverter
    fun toList(list: String): List<String> = Json.decodeFromString<List<String>>(list)

    @TypeConverter
    fun fromList(list: List<String>): String = Json.encodeToString(list)
}
