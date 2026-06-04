package com.winlator.cmod.feature.stores.steam.db.converters
import androidx.room.TypeConverter
import com.winlator.cmod.feature.stores.steam.enums.ELicenseFlags
import com.winlator.cmod.feature.stores.steam.enums.ELicenseType
import com.winlator.cmod.feature.stores.steam.enums.EPaymentMethod
import kotlinx.serialization.json.Json
import java.util.EnumSet

class LicenseConverter {
    @TypeConverter
    fun toLicenseFlags(licenseFlags: Int): EnumSet<ELicenseFlags> = ELicenseFlags.from(licenseFlags)

    @TypeConverter
    fun fromLicenseFlags(licenseFlags: EnumSet<ELicenseFlags>): Int = ELicenseFlags.code(licenseFlags)

    @TypeConverter
    fun toLicenseType(licenseType: Int): ELicenseType =
        ELicenseType.from(licenseType) ?: ELicenseType.NoLicense

    @TypeConverter
    fun fromLicenseType(licenseType: ELicenseType): Int = licenseType.code()

    @TypeConverter
    fun toPaymentMethod(paymentMethod: Int): EPaymentMethod =
        EPaymentMethod.from(paymentMethod) ?: EPaymentMethod.None

    @TypeConverter
    fun fromPaymentMethod(paymentMethod: EPaymentMethod): Int = paymentMethod.code()

    @TypeConverter
    fun toIntList(appIds: String): List<Int> = Json.decodeFromString<List<Int>>(appIds)

    @TypeConverter
    fun fromIntList(appIds: List<Int>): String = Json.encodeToString(appIds)
}
