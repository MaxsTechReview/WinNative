package com.winlator.cmod

import android.content.Context
import com.winlator.cmod.contents.ContentProfile
import com.winlator.cmod.contents.ContentsManager
import com.winlator.cmod.core.WineInfo

fun buildRuntimeContainerName(
    context: Context,
    contentsManager: ContentsManager,
    profile: ContentProfile
): String {
    val wineVersion = ContentsManager.getEntryName(profile)
    val wineInfo = WineInfo.fromIdentifier(context, contentsManager, wineVersion)
    val arch = wineInfo.arch
    val archLabel = when {
        wineInfo.isArm64EC -> "ARM64EC"
        arch.equals("x86_64", ignoreCase = true) -> "x86-64"
        arch.equals("x86", ignoreCase = true) -> "x86"
        else -> arch.uppercase()
    }
    return "${runtimeDisplayLabel(profile)} $archLabel".trim()
}

fun profileMatchesVersionName(profile: ContentProfile, versionName: String): Boolean {
    return normalizeVersionName(profile.verName) == normalizeVersionName(versionName)
}

private fun normalizeVersionName(value: String?): String {
    return value
        ?.trim()
        ?.replace(Regex("[^A-Za-z0-9]+"), " ")
        ?.replace(Regex("\\s+"), " ")
        ?.lowercase()
        ?: ""
}
