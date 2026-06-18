package com.winlator.cmod.feature.community

import android.os.Build

/**
 * Builds the hardware-identity block used by the community filters.
 *
 *  - The "Chipset" filter keys on the SoC model (e.g. "SM8750"), which is
 *    identical across every brand/region/model that ships that chip.
 *  - The "Device" filter keys (server-side) on a canonical device derived from
 *    brand + codename/model, unifying regional variants of the same phone.
 *
 * SOC_MODEL/SOC_MANUFACTURER are API 31+; below that we fall back to getprop.
 */
object DeviceIdentity {

    data class HardwareBlock(
        val socModel: String,
        val socManufacturer: String,
        val boardPlatform: String,
        val deviceCodename: String,
        val modelNumber: String,
        val modelRegion: String,
        val brand: String,
        val marketName: String,
    )

    fun current(): HardwareBlock {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Build.SOC_MODEL.orEmptyClean() else getprop("ro.soc.model")
        val socMfr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Build.SOC_MANUFACTURER.orEmptyClean() else getprop("ro.soc.manufacturer")
        val board = getprop("ro.board.platform")
        val market = listOf(
            getprop("ro.product.marketname"),
            getprop("ro.product.odm.marketname"),
            getprop("ro.config.marketing_name"),
            getprop("ro.product.vendor.marketname"),
        ).firstOrNull { it.isNotBlank() } ?: ""
        return HardwareBlock(
            socModel = soc.ifBlank { board }.take(48),
            socManufacturer = socMfr.take(48),
            boardPlatform = board.take(48),
            deviceCodename = (Build.DEVICE ?: getprop("ro.product.device")).clean().take(48),
            modelNumber = (Build.MODEL ?: getprop("ro.product.model")).clean().take(48),
            modelRegion = getprop("ro.product.name").take(48),
            brand = (Build.BRAND ?: getprop("ro.product.brand")).clean().take(48),
            marketName = market.take(64),
        )
    }

    /** Stable key sent for the default "Chipset" filter. */
    fun chipsetKey(): String {
        val hw = current()
        return hw.socModel.ifBlank { hw.boardPlatform }
    }

    private fun getprop(key: String): String = runCatching {
        val p = Runtime.getRuntime().exec(arrayOf("getprop", key))
        val out = p.inputStream.bufferedReader().use { it.readLine() } ?: ""
        p.waitFor()
        out.clean()
    }.getOrDefault("")

    private fun String?.clean(): String =
        (this ?: "").trim().filter { it.code in 32..126 }

    private fun String?.orEmptyClean(): String = this.clean()
}
