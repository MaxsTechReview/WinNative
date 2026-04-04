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
        wineInfo.isArm64EC -> "arm64ec"
        arch.equals("x86_64", ignoreCase = true) -> "x86-64"
        arch.equals("x86", ignoreCase = true) -> "x86"
        else -> arch.lowercase()
    }
    return "${runtimeVersionLabel(profile)}_${archLabel}".trim()
}

fun profileMatchesVersionName(profile: ContentProfile, versionName: String): Boolean {
    return normalizeVersionName(profile.verName) == normalizeVersionName(versionName)
}

fun runtimeVersionLabel(profile: ContentProfile): String {
    val versionName = profile.verName.trim()
    val normalized = versionName
        .replace('_', '-')
        .replace(Regex("(?i)\\b(?:wine|proton|sm\\.proton|wine-be|proton-be)\\b[\\.-_]*"), "")
        .replace(Regex("(?i)[\\.-_]?(x86[-_]?64|arm64ec|x86|wow64)\\b"), "")
        .replace(Regex("(?i)[\\.-_]?(coffincolors|gplasync|async|stable|nightly)\\b"), "")
        .replace(Regex("[^A-Za-z0-9.+-]+"), "-")
        .replace(Regex("-+"), "-")
        .trim('-')

    val fallback = Regex("([0-9]+(?:[.][0-9]+)*(?:-[A-Za-z0-9]+)*)")
        .find(versionName.replace('_', '-'))
        ?.groupValues
        ?.getOrNull(1)
        ?.trim('-')

    return (normalized.ifBlank { fallback.orEmpty() }).ifBlank { versionName }
}

private fun normalizeVersionName(value: String?): String {
    return value
        ?.trim()
        ?.replace(Regex("[^A-Za-z0-9]+"), " ")
        ?.replace(Regex("\\s+"), " ")
        ?.lowercase()
        ?: ""
}
