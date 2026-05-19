package com.winlator.cmod.feature.stores.steam.utils
import com.winlator.cmod.feature.stores.steam.enums.ELicenseFlags
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Serializes a Steam license to the JSON stored in the `cached_license`
 * Room table.
 *
 * Phase 9: the license now comes from the C++ WN-Steam-Client as plain field
 * values (see `processLicenseList`), so the only entry point is
 * [serializeLicenseFields]. The old JavaSteam `License`-object /
 * `DepotManifest` round-trips were dead code and were removed with the
 * JavaSteam dependency.
 */
object LicenseSerializer {
    /**
     * Serialize a license from plain field values.
     *
     * @param flags raw ELicenseFlags bitfield (decomposed to the per-flag
     *              code array the JSON format expects).
     */
    fun serializeLicenseFields(
        packageID: Int,
        lastChangeNumber: Int,
        timeCreatedMs: Long,
        timeNextProcessMs: Long,
        minuteLimit: Int,
        minutesUsed: Int,
        paymentMethod: Int,
        flags: Int,
        purchaseCode: String,
        licenseType: Int,
        territoryCode: Int,
        accessToken: Long,
        ownerAccountID: Int,
        masterPackageID: Int,
    ): String =
        try {
            JSONObject().apply {
                put("packageID", packageID)
                put("lastChangeNumber", lastChangeNumber)
                put("timeCreated", timeCreatedMs)
                put("timeNextProcess", timeNextProcessMs)
                put("minuteLimit", minuteLimit)
                put("minutesUsed", minutesUsed)
                put("paymentMethod", paymentMethod)
                put("licenseFlags", JSONArray(ELicenseFlags.from(flags).map { it.code() }))
                put("purchaseCode", purchaseCode)
                put("licenseType", licenseType)
                put("territoryCode", territoryCode)
                put("accessToken", accessToken)
                put("ownerAccountID", ownerAccountID)
                put("masterPackageID", masterPackageID)
            }.toString()
        } catch (e: Exception) {
            Timber.e(e, "Failed to serialize license fields: ${e.message}")
            ""
        }
}
