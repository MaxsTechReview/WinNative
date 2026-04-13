package com.winlator.cmod.feature.stores.steam.utils

/**
 * Extension functions relating to [String] as the receiver type.
 */

private const val AVATAR_BASE_URL = "https://steamcdn-a.akamaihd.net/steamcommunity/public/images/avatars/"
private const val MISSING_AVATAR_URL = "${AVATAR_BASE_URL}fe/fef49e7fa7e1997310d705b2a6158ff8dc1cdfeb_full.jpg"

fun String.getAvatarURL(): String =
    this
        .ifEmpty { null }
        ?.takeIf { str -> str.isNotEmpty() && !str.all { it == '0' } }
        ?.let { "${AVATAR_BASE_URL}${it.substring(0, 2)}/${it}_full.jpg" }
        ?: MISSING_AVATAR_URL
