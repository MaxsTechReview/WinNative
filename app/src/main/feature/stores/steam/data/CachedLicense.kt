package com.winlator.cmod.feature.stores.steam.data
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity to store a Steam license as serialized JSON.
 * Each license is stored in its own row.
 */
@Entity("cached_license")
data class CachedLicense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("license_json")
    val licenseJson: String, // Serialized License object as Base64
)
