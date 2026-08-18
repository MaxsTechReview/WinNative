package com.winlator.cmod.feature.community

import android.content.Context
import com.winlator.cmod.R
import com.winlator.cmod.feature.settings.DXVKConfigUtils
import com.winlator.cmod.feature.settings.GraphicsDriverConfigUtils
import com.winlator.cmod.runtime.content.AdrenotoolsManager
import com.winlator.cmod.runtime.content.ContentProfile
import com.winlator.cmod.runtime.content.ContentsManager
import com.winlator.cmod.runtime.reshade.ReshadeConfigWriter
import com.winlator.cmod.runtime.reshade.ReshadeLoadout
import com.winlator.cmod.runtime.reshade.ReshadeManager
import com.winlator.cmod.runtime.system.GPUInformation
import com.winlator.cmod.runtime.wine.WineInfo
import org.json.JSONObject

object ComponentChecker {

    data class Missing(val label: String)

    private val SKIP = setOf("", "none", "system", "builtin", "auto", "wined3d")

    fun findMissing(
        context: Context,
        contentsManager: ContentsManager,
        settings: JSONObject,
    ): List<Missing> {
        contentsManager.syncContents()
        val missing = mutableListOf<Missing>()

        val wineVer = settings.optString("wineVersion", "")
        if (wineVer.lowercase() !in SKIP) {
            val installed = installedNames(contentsManager, ContentProfile.ContentType.CONTENT_TYPE_WINE) +
                installedNames(contentsManager, ContentProfile.ContentType.CONTENT_TYPE_PROTON)
            val resolved = runCatching {
                WineInfo.fromIdentifier(context, contentsManager, wineVer)
            }.getOrNull()
            if (resolved == null && !matches(wineVer, installed) && installed.isNotEmpty()) {
                missing += Missing("Wine $wineVer")
            }
        }

        val dxwrapper = settings.optString("dxwrapper", "")
        if (dxwrapper.lowercase() !in SKIP) {
            val cfg = DXVKConfigUtils.parseConfig(settings.optString("dxwrapperConfig", ""))
            checkVersion(
                cfg.get("version"), "DXVK",
                installedNames(contentsManager, ContentProfile.ContentType.CONTENT_TYPE_DXVK), missing,
            )
            checkVersion(
                cfg.get("vkd3dVersion"), "VKD3D",
                installedNames(contentsManager, ContentProfile.ContentType.CONTENT_TYPE_VKD3D), missing,
            )
        }

        checkVersion(
            settings.optString("box64Version", ""), "Box64",
            installedNames(contentsManager, ContentProfile.ContentType.CONTENT_TYPE_BOX64) +
                installedNames(contentsManager, ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64),
            missing,
        )

        checkVersion(
            settings.optString("fexcoreVersion", ""), "FEXCore",
            installedNames(contentsManager, ContentProfile.ContentType.CONTENT_TYPE_FEXCORE), missing,
        )

        val gdVersion = runCatching {
            (GraphicsDriverConfigUtils.parseGraphicsDriverConfig(
                settings.optString("graphicsDriverConfig", ""),
            )["version"] ?: "").trim()
        }.getOrDefault("")
        if (gdVersion.isNotEmpty() && gdVersion.lowercase() !in SKIP &&
            !graphicsDriverAvailable(context, gdVersion)
        ) {
            missing += Missing("Graphics driver \"$gdVersion\"")
        }

        missing += missingReshadeEffects(context, settings)

        return missing
    }

    private fun missingReshadeEffects(context: Context, settings: JSONObject): List<Missing> {
        val loadout = settings.optString(ReshadeConfigWriter.EXTRA_LOADOUT, "")
        val legacy = settings.optString(ReshadeConfigWriter.EXTRA_EFFECT, "")
        val wanted = runCatching { ReshadeLoadout.parse(loadout, legacy) }.getOrNull().orEmpty()
        if (wanted.isEmpty()) return emptyList()
        val installed = runCatching { ReshadeManager.scanEffectNames(context) }.getOrNull().orEmpty()
        return wanted
            .filter { entry -> installed.none { it.equals(entry.name, ignoreCase = true) } }
            .map { Missing("ReShade effect \"${it.name}\"") }
    }

    private fun graphicsDriverAvailable(context: Context, version: String): Boolean {
        runCatching {
            val sys = context.resources.getStringArray(R.array.wrapper_graphics_driver_version_entries)
            if (sys.any { it.equals(version, ignoreCase = true) }) {
                return GPUInformation.isDriverSupported(version, context)
            }
        }
        return runCatching {
            AdrenotoolsManager(context).enumarateInstalledDrivers()
                ?.any { it.equals(version, ignoreCase = true) } ?: false
        }.getOrDefault(false)
    }

    private fun checkVersion(
        version: String?,
        label: String,
        installed: List<String>,
        out: MutableList<Missing>,
    ) {
        val v = version?.trim() ?: ""
        if (v.lowercase() in SKIP) return
        if (!matches(v, installed)) out += Missing("$label $v")
    }

    private fun installedNames(cm: ContentsManager, type: ContentProfile.ContentType): List<String> {
        val list = cm.getProfiles(type) ?: return emptyList()
        return list.filter { it.isInstalled }.flatMap {
            listOf(it.verName ?: "", ContentsManager.getEntryName(it))
        }.filter { it.isNotBlank() }
    }

    private fun matches(version: String, installed: List<String>): Boolean {
        val v = version.lowercase()
        if (v.isEmpty()) return true
        return installed.any { name ->
            val n = name.lowercase()
            n == v || n.endsWith(v) || n.contains(v) || (n.length >= 4 && v.contains(n))
        }
    }
}
