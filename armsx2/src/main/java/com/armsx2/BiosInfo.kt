package com.armsx2

class BiosInfo(
    @JvmField val version: Int,
    @JvmField val region: Int,
    @JvmField val description: String,
    @JvmField val zone: String,
) {
    val versionString: String get() = "v%d.%02d".format((version shr 8) and 0xFF, version and 0xFF)

    val regionFlag: String get() = when (region) {
        0 -> "🇯🇵"
        1 -> "🇺🇸"
        2 -> "🇪🇺"
        4 -> "🇭🇰"
        6 -> "🇨🇳"
        8 -> "🔧"
        9 -> "🧪"
        10 -> "🏳️"
        else -> "🌐"
    }
}
