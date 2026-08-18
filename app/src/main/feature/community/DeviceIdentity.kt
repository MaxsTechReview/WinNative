package com.winlator.cmod.feature.community

import android.os.Build

object DeviceIdentity {

    @Volatile
    private var cached: HardwareBlock? = null

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

    private val PLACEHOLDERS = setOf(
        "", "unknown", "null", "none", "n/a", "na", "0", "invalid", "undefined",
        "not available", "default", "generic",
    )

    private val SOC_MODEL_PROPS = listOf(
        "ro.soc.model",
        "ro.vendor.qti.soc_model",
        "ro.chipname",
        "ro.mediatek.platform",
        "ro.vendor.mediatek.platform",
        "ro.hardware.chipname",
    )

    private val SOC_MANUFACTURER_PROPS = listOf(
        "ro.soc.manufacturer",
        "ro.vendor.qti.soc_manufacturer",
        "ro.hardware.vendor",
    )

    private val BOARD_PROPS = listOf(
        "ro.board.platform",
        "ro.vendor.qti.soc_name",
        "ro.hardware",
    )

    fun current(): HardwareBlock {
        cached?.let { return it }
        return build().also { cached = it }
    }

    private fun build(): HardwareBlock {
        val socCandidates = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            socCandidates += Build.SOC_MODEL.clean()
        }
        SOC_MODEL_PROPS.forEach { socCandidates += getprop(it) }
        val soc = firstUsable(socCandidates)

        val mfrCandidates = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mfrCandidates += Build.SOC_MANUFACTURER.clean()
        }
        SOC_MANUFACTURER_PROPS.forEach { mfrCandidates += getprop(it) }
        val socMfr = firstUsable(mfrCandidates)

        val board = firstUsable(BOARD_PROPS.map { getprop(it) })

        val market = firstUsable(
            listOf(
                getprop("ro.product.marketname"),
                getprop("ro.product.odm.marketname"),
                getprop("ro.config.marketing_name"),
                getprop("ro.product.vendor.marketname"),
            )
        )

        return HardwareBlock(
            socModel = soc.ifBlank { board }.take(48),
            socManufacturer = socMfr.take(48),
            boardPlatform = board.take(48),
            deviceCodename = firstUsable(
                listOf(Build.DEVICE.clean(), getprop("ro.product.device"))
            ).take(48),
            modelNumber = firstUsable(
                listOf(Build.MODEL.clean(), getprop("ro.product.model"))
            ).take(48),
            modelRegion = getprop("ro.product.name").take(48),
            brand = firstUsable(
                listOf(Build.BRAND.clean(), getprop("ro.product.brand"))
            ).take(48),
            marketName = market.take(64),
        )
    }

    fun chipsetKey(): String {
        val hw = current()
        return hw.socModel.ifBlank { hw.boardPlatform }
    }

    private fun firstUsable(values: List<String>): String =
        values.firstOrNull { isUsable(it) } ?: ""

    private fun isUsable(value: String): Boolean {
        val v = value.trim()
        if (v.isEmpty() || v.lowercase() in PLACEHOLDERS) return false
        return v.any { it.isLetterOrDigit() }
    }

    private fun getprop(key: String): String = runCatching {
        val p = Runtime.getRuntime().exec(arrayOf("getprop", key))
        val out = p.inputStream.bufferedReader().use { it.readLine() } ?: ""
        p.waitFor()
        out.clean()
    }.getOrDefault("")

    private fun String?.clean(): String =
        (this ?: "").trim().filter { it.code in 32..126 }
}
