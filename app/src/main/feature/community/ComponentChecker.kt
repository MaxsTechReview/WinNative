package com.winlator.cmod.feature.community

import android.content.Context
import com.winlator.cmod.R
import com.winlator.cmod.feature.settings.DXVKConfigUtils
import com.winlator.cmod.feature.settings.GraphicsDriverConfigUtils
import com.winlator.cmod.runtime.content.AdrenotoolsManager
import com.winlator.cmod.runtime.content.ContentProfile
import com.winlator.cmod.runtime.content.ContentsManager
import com.winlator.cmod.runtime.system.GPUInformation
import com.winlator.cmod.runtime.wine.WineInfo
import org.json.JSONObject

/**
 * Compares the components a downloaded config needs (Wine, DXVK, VKD3D, Box64,
 * FEXCore) against what is actually installed via [ContentsManager], so the UI
 * can show a "⚠ MISSING COMPONENT" warning before applying.
 *
 * Matching is deliberately lenient (treats anything it can't positively prove
 * missing as present) to avoid false positives that would block a valid apply.
 */
object ComponentChecker {

    data class Missing(val label: String)

    private val SKIP = setOf("", "none", "system", "builtin", "auto", "wined3d")

    fun findMissing(context: Context, contentsManager: ContentsManager, settings: JSONObject): List<Missing> {
        contentsManager.syncContents()
        val missing = mutableListOf<Missing>()

        // Wine / Proton
        val wineVer = settings.optString("wineVersion", "")
        if (wineVer.lowercase() !in SKIP) {
            val installed = installedNames(contentsManager, ContentProfile.ContentType.CONTENT_TYPE_WINE) +
                installedNames(contentsManager, ContentProfile.ContentType.CONTENT_TYPE_PROTON)
            // WineInfo resolves bundled/main versions; only flag if clearly a
            // managed profile name that isn't installed.
            val resolved = runCatching {
                WineInfo.fromIdentifier(context, contentsManager, wineVer)
            }.getOrNull()
            if (resolved == null && !matches(wineVer, installed) && installed.isNotEmpty()) {
                missing += Missing("Wine $wineVer")
            }
        }

        // DXVK / VKD3D from dxwrapperConfig
        val dxwrapper = settings.optString("dxwrapper", "")
        if (dxwrapper.lowercase() !in SKIP) {
            val cfg = DXVKConfigUtils.parseConfig(settings.optString("dxwrapperConfig", ""))
            checkVersion(cfg.get("version"), "DXVK",
                installedNames(contentsManager, ContentProfile.ContentType.CONTENT_TYPE_DXVK), missing)
            checkVersion(cfg.get("vkd3dVersion"), "VKD3D",
                installedNames(contentsManager, ContentProfile.ContentType.CONTENT_TYPE_VKD3D), missing)
        }

        // Box64 / WOWBox64
        checkVersion(settings.optString("box64Version", ""), "Box64",
            installedNames(contentsManager, ContentProfile.ContentType.CONTENT_TYPE_BOX64) +
                installedNames(contentsManager, ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64), missing)

        // FEXCore
        checkVersion(settings.optString("fexcoreVersion", ""), "FEXCore",
            installedNames(contentsManager, ContentProfile.ContentType.CONTENT_TYPE_FEXCORE), missing)

        // Graphics driver version (built-in wrapper version or installed custom
        // adrenotools driver). The `version=` token in graphicsDriverConfig is
        // the selected driver; if it's not available here, the config can't be
        // applied faithfully -> report it.
        val gdVersion = runCatching {
            (GraphicsDriverConfigUtils.parseGraphicsDriverConfig(
                settings.optString("graphicsDriverConfig", ""))["version"] ?: "").trim()
        }.getOrDefault("")
        if (gdVersion.isNotEmpty() && gdVersion.lowercase() !in SKIP &&
            !graphicsDriverAvailable(context, gdVersion)) {
            missing += Missing("Graphics driver \"$gdVersion\"")
        }

        return missing
    }

    private fun graphicsDriverAvailable(context: Context, version: String): Boolean {
        // Built-in / system wrapper versions (filtered by GPU support).
        runCatching {
            val sys = context.resources.getStringArray(R.array.wrapper_graphics_driver_version_entries)
            if (sys.any { it.equals(version, ignoreCase = true) }) {
                return GPUInformation.isDriverSupported(version, context)
            }
        }
        // Installed custom adrenotools drivers (the folder id == the version token).
        return runCatching {
            AdrenotoolsManager(context).enumarateInstalledDrivers()
                ?.any { it.equals(version, ignoreCase = true) } ?: false
        }.getOrDefault(false)
    }

    private fun checkVersion(version: String?, label: String, installed: List<String>, out: MutableList<Missing>) {
        val v = version?.trim() ?: ""
        if (v.lowercase() in SKIP) return
        if (installed.isEmpty()) {
            // Nothing of this type installed at all -> it's needed but absent.
            out += Missing("$label $v")
        } else if (!matches(v, installed)) {
            out += Missing("$label $v")
        }
    }

    private fun installedNames(cm: ContentsManager, type: ContentProfile.ContentType): List<String> {
        val list = cm.getProfiles(type) ?: return emptyList()
        return list.filter { it.isInstalled }.flatMap {
            listOf(it.verName ?: "", ContentsManager.getEntryName(it))
        }.filter { it.isNotBlank() }
    }

    private fun matches(version: String, installed: List<String>): Boolean {
        val v = version.lowercase()
        return installed.any { name ->
            val n = name.lowercase()
            n == v || n.endsWith(v) || n.contains(v) || v.contains(n)
        }
    }
}
