package com.winlator.cmod.runtime.container

import android.content.Context
import com.winlator.cmod.runtime.compat.box64.Box64Preset
import com.winlator.cmod.runtime.compat.fexcore.FEXCorePreset
import com.winlator.cmod.runtime.content.ContentProfile
import com.winlator.cmod.runtime.content.ContentsManager
import com.winlator.cmod.runtime.display.winhandler.WinHandler
import com.winlator.cmod.runtime.wine.WineInfo
import com.winlator.cmod.runtime.wine.WineThemeManager
import com.winlator.cmod.runtime.wine.WineUtils
import com.winlator.cmod.shared.util.Callback
import org.json.JSONObject

object ContainerCreation {
    @JvmStatic
    fun displayNameForProfile(profile: ContentProfile): String {
        val prefix =
            when (profile.type) {
                ContentProfile.ContentType.CONTENT_TYPE_WINE -> "Wine"
                ContentProfile.ContentType.CONTENT_TYPE_PROTON -> "Proton"
                else -> profile.type.toString()
            }
        val withoutPrefix =
            removeLeadingRuntimePrefix(profile.verName)
                .trim()
        return "$prefix $withoutPrefix"
            .replace(Regex("[^a-zA-Z0-9._\\- ]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
            .ifBlank { prefix }
    }

    @JvmStatic
    fun displayNameForWineVersion(
        context: Context,
        contentsManager: ContentsManager,
        wineVersion: String,
    ): String {
        findRuntimeProfile(contentsManager, wineVersion)?.let {
            return displayNameForProfile(it)
        }

        val wineInfo = WineInfo.fromIdentifier(context, contentsManager, wineVersion)
        return wineInfo.toString()
            .replace(Regex("[^a-zA-Z0-9._\\- ]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
            .ifBlank { wineVersion }
    }

    private fun findRuntimeProfile(
        contentsManager: ContentsManager,
        entryName: String,
    ): ContentProfile? =
        (
            contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WINE).orEmpty() +
                contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_PROTON).orEmpty()
            ).firstOrNull { ContentsManager.getEntryName(it) == entryName }

    private fun removeLeadingRuntimePrefix(versionName: String): String {
        val trimmed = versionName.trim()
        val prefixEnd =
            when {
                trimmed.regionMatches(0, "wine", 0, "wine".length, ignoreCase = true) -> "wine".length
                trimmed.regionMatches(0, "proton", 0, "proton".length, ignoreCase = true) -> "proton".length
                else -> return trimmed
            }
        val next = trimmed.getOrNull(prefixEnd)
        return if (next == null || next == '-' || next == '_' || next.isWhitespace()) {
            trimmed.drop(prefixEnd).dropWhile { it == '-' || it == '_' || it.isWhitespace() }
        } else {
            trimmed
        }
    }

    @JvmStatic
    fun uniqueName(
        containerManager: ContainerManager,
        desiredName: String,
    ): String {
        val baseName = desiredName.trim().ifBlank { "Container" }
        var candidate = baseName
        var counter = 2
        while (containerManager.containers.any { it.name.equals(candidate, ignoreCase = true) }) {
            candidate = "$baseName $counter"
            counter++
        }
        return candidate
    }

    @JvmStatic
    fun buildLaunchReadyData(
        context: Context,
        contentsManager: ContentsManager,
        name: String,
        wineVersion: String,
    ): JSONObject {
        contentsManager.syncContents()
        val wineInfo = WineInfo.fromIdentifier(context, contentsManager, wineVersion)
        val isArm64Ec = wineInfo.isArm64EC

        return JSONObject().apply {
            put("name", name)
            put("wineVersion", wineVersion)
            put("screenSize", Container.DEFAULT_SCREEN_SIZE)
            put("envVars", Container.DEFAULT_ENV_VARS)
            put("cpuList", Container.getFallbackCPUList())
            put("cpuListWoW64", Container.getFallbackCPUListWoW64())
            put("graphicsDriver", Container.DEFAULT_GRAPHICS_DRIVER)
            put("graphicsDriverConfig", replaceDelimitedConfigValue(
                Container.DEFAULT_GRAPHICSDRIVERCONFIG,
                ';',
                "version",
                "System",
            ))
            put("dxwrapper", Container.DEFAULT_DXWRAPPER)
            put("dxwrapperConfig", buildDefaultDxWrapperConfig(contentsManager, isArm64Ec))
            put("audioDriver", Container.DEFAULT_AUDIO_DRIVER)
            put("emulator", if (isArm64Ec) "fexcore" else "box64")
            put("emulator64", if (isArm64Ec) "fexcore" else "box64")
            put("wincomponents", Container.DEFAULT_WINCOMPONENTS)
            put("drives", WineUtils.normalizePersistentDrives(context, Container.DEFAULT_DRIVES))
            put("fullscreenStretched", false)
            put("inputType", WinHandler.DEFAULT_INPUT_TYPE.toInt())
            put("startupSelection", Container.STARTUP_SELECTION_ESSENTIAL.toInt())
            put("box64Version", resolvePreferredContentVersion(
                contentsManager,
                if (isArm64Ec) {
                    ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
                } else {
                    ContentProfile.ContentType.CONTENT_TYPE_BOX64
                },
                "",
            ))
            put("box64Preset", Box64Preset.PERFORMANCE)
            put("fexcoreVersion", resolvePreferredContentVersion(
                contentsManager,
                ContentProfile.ContentType.CONTENT_TYPE_FEXCORE,
                "",
            ))
            put("fexcorePreset", FEXCorePreset.PERFORMANCE)
            put("desktopTheme", WineThemeManager.DEFAULT_DESKTOP_THEME)
            put("midiSoundFont", "")
            put("lc_all", "")
            put("execArgs", "")
        }
    }

    @JvmStatic
    fun createContainer(
        context: Context,
        containerManager: ContainerManager,
        contentsManager: ContentsManager,
        name: String,
        wineVersion: String,
    ): Container? {
        val data = buildLaunchReadyData(context, contentsManager, name, wineVersion)
        return containerManager.createContainer(data, contentsManager)?.also {
            applyLaunchReadyDefaults(context, contentsManager, it)
        }
    }

    @JvmStatic
    fun createContainerForProfile(
        context: Context,
        containerManager: ContainerManager,
        contentsManager: ContentsManager,
        profile: ContentProfile,
        desiredName: String = displayNameForProfile(profile),
    ): Container? =
        createContainer(
            context,
            containerManager,
            contentsManager,
            uniqueName(containerManager, desiredName),
            ContentsManager.getEntryName(profile),
        )

    @JvmStatic
    fun createContainerAsync(
        context: Context,
        containerManager: ContainerManager,
        contentsManager: ContentsManager,
        name: String,
        wineVersion: String,
        callback: Callback<Container?>,
    ) {
        val data = buildLaunchReadyData(context, contentsManager, name, wineVersion)
        containerManager.createContainerAsync(data, contentsManager) { container ->
            if (container != null) {
                applyLaunchReadyDefaults(context, contentsManager, container)
            }
            callback.call(container)
        }
    }

    @JvmStatic
    fun createContainerAsync(
        containerManager: ContainerManager,
        contentsManager: ContentsManager,
        data: JSONObject,
        callback: Callback<Container?>,
    ) {
        containerManager.createContainerAsync(data, contentsManager) { container ->
            callback.call(container)
        }
    }

    @JvmStatic
    fun createContainerForProfileAsync(
        context: Context,
        containerManager: ContainerManager,
        contentsManager: ContentsManager,
        profile: ContentProfile,
        callback: Callback<Container?>,
    ) {
        val uniqueName = uniqueName(containerManager, displayNameForProfile(profile))
        createContainerAsync(
            context,
            containerManager,
            contentsManager,
            uniqueName,
            ContentsManager.getEntryName(profile),
            callback,
        )
    }

    @JvmStatic
    fun getOrCreateContainerForProfile(
        context: Context,
        containerManager: ContainerManager,
        contentsManager: ContentsManager,
        profile: ContentProfile,
        desiredName: String = displayNameForProfile(profile),
    ): Container? {
        val wineVersion = ContentsManager.getEntryName(profile)
        containerManager.containers.firstOrNull { it.name == desiredName }?.let {
            if (it.wineVersion != wineVersion) {
                it.setWineVersion(wineVersion)
                it.putExtra("wineprefixNeedsUpdate", "t")
            }
            applyLaunchReadyDefaults(context, contentsManager, it)
            return it
        }
        return createContainerForProfile(context, containerManager, contentsManager, profile, desiredName)
    }

    @JvmStatic
    fun applyLaunchReadyDefaults(
        context: Context,
        contentsManager: ContentsManager,
        container: Container,
    ) {
        contentsManager.syncContents()
        val wineInfo = WineInfo.fromIdentifier(context, contentsManager, container.wineVersion)
        val isArm64Ec = wineInfo.isArm64EC

        container.setGraphicsDriver(Container.DEFAULT_GRAPHICS_DRIVER)
        container.setCPUList(Container.getFallbackCPUList())
        container.setCPUListWoW64(Container.getFallbackCPUListWoW64())
        container.setDrives(WineUtils.normalizePersistentDrives(
            context,
            container.drives ?: Container.DEFAULT_DRIVES,
        ))
        container.setGraphicsDriverConfig(replaceDelimitedConfigValue(
            Container.DEFAULT_GRAPHICSDRIVERCONFIG,
            ';',
            "version",
            "System",
        ))
        container.setDXWrapper(Container.DEFAULT_DXWRAPPER)
        container.setDXWrapperConfig(buildDefaultDxWrapperConfig(contentsManager, isArm64Ec))
        container.setEmulator(if (isArm64Ec) "fexcore" else "box64")
        container.setEmulator64(if (isArm64Ec) "fexcore" else "box64")
        container.setBox64Version(resolvePreferredContentVersion(
            contentsManager,
            if (isArm64Ec) {
                ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
            } else {
                ContentProfile.ContentType.CONTENT_TYPE_BOX64
            },
            "",
        ))
        container.setFEXCoreVersion(resolvePreferredContentVersion(
            contentsManager,
            ContentProfile.ContentType.CONTENT_TYPE_FEXCORE,
            "",
        ))
        container.setBox64Preset(Box64Preset.PERFORMANCE)
        container.setFEXCorePreset(FEXCorePreset.PERFORMANCE)
        container.saveData()
    }

    private fun buildDefaultDxWrapperConfig(
        contentsManager: ContentsManager,
        isArm64Ec: Boolean,
    ): String {
        val dxvkVersion =
            resolvePreferredContentVersion(
                contentsManager,
                ContentProfile.ContentType.CONTENT_TYPE_DXVK,
                "",
                includePattern = if (isArm64Ec) Regex("arm64ec", RegexOption.IGNORE_CASE) else null,
                excludePattern = if (isArm64Ec) null else Regex("arm64ec", RegexOption.IGNORE_CASE),
            )
        val vkd3dVersion =
            resolvePreferredContentVersion(
                contentsManager,
                ContentProfile.ContentType.CONTENT_TYPE_VKD3D,
                "None",
                includePattern = if (isArm64Ec) Regex("arm64ec", RegexOption.IGNORE_CASE) else null,
                excludePattern = if (isArm64Ec) null else Regex("arm64ec", RegexOption.IGNORE_CASE),
            )

        return replaceDelimitedConfigValue(
            replaceDelimitedConfigValue(
                Container.DEFAULT_DXWRAPPERCONFIG,
                ',',
                "version",
                dxvkVersion,
            ),
            ',',
            "vkd3dVersion",
            vkd3dVersion,
        )
    }

    private fun resolvePreferredContentVersion(
        manager: ContentsManager,
        type: ContentProfile.ContentType,
        fallback: String,
        includePattern: Regex? = null,
        excludePattern: Regex? = null,
    ): String {
        val installedProfiles =
            manager.getProfiles(type)
                .orEmpty()
                .filter { it.isInstalled }
        val matchingProfiles =
            installedProfiles
                .filter { profile ->
                    val versionName = profile.verName
                    (includePattern == null || includePattern.containsMatchIn(versionName)) &&
                        (excludePattern == null || !excludePattern.containsMatchIn(versionName))
                }.ifEmpty { installedProfiles }

        val newestInstalled =
            matchingProfiles.maxWithOrNull(
                compareBy<ContentProfile> { it.verCode }.thenBy { it.verName.lowercase() },
            )
        return newestInstalled?.let(::contentVersionIdentifier) ?: fallback
    }

    private fun contentVersionIdentifier(profile: ContentProfile): String {
        val entryName = ContentsManager.getEntryName(profile)
        val firstDash = entryName.indexOf('-')
        return if (firstDash >= 0) entryName.substring(firstDash + 1) else entryName
    }

    private fun replaceDelimitedConfigValue(
        config: String,
        delimiter: Char,
        key: String,
        value: String,
    ): String {
        val parts = config.split(delimiter).toMutableList()
        var replaced = false
        for (index in parts.indices) {
            if (parts[index].startsWith("$key=")) {
                parts[index] = "$key=$value"
                replaced = true
            }
        }
        if (!replaced) {
            parts += "$key=$value"
        }
        return parts.joinToString(delimiter.toString())
    }
}
