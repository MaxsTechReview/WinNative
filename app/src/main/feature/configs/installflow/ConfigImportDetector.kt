package com.winlator.cmod.feature.configs.installflow

import android.content.Context
import com.winlator.cmod.feature.configs.installflow.ComponentRequirement.KeyGuard
import com.winlator.cmod.feature.settings.DXVKConfigUtils
import com.winlator.cmod.runtime.content.ContentProfile
import com.winlator.cmod.runtime.content.ContentsManager
import org.json.JSONObject
import timber.log.Timber

/**
 * Stateless analyzer. Reads an imported config JSON and a freshly-synced
 * [ContentsManager] and returns one [RequirementEntry] per declared component.
 *
 * The matching strategy mirrors how the in-app settings dialog builds its dropdown
 * lists — Wine/Proton/DXVK/VKD3D all carry their full *entry name* in the config
 * payload, including arch and variant tokens. So the lookup is an exact (case-
 * insensitive) match against the catalog rather than version-only fuzzy matching.
 * This matters for variants like "dxvk-2.7.1-gplasync" vs "dxvk-2.4.1-arm64ec-
 * gplasync" — version-only matching would conflate the two and pick the wrong file.
 */
object ConfigImportDetector {
    private const val TAG = "ConfigImportDetector"

    fun detect(
        context: Context,
        configJson: JSONObject,
        contentsManager: ContentsManager,
    ): List<RequirementEntry> {
        val containerBlock = configJson.optJSONObject("container") ?: JSONObject()
        val results = mutableListOf<RequirementEntry>()

        // --- Wine / Proton -----------------------------------------------------
        containerBlock.optString("wineVersion")
            .takeIf { it.isNotBlank() }
            ?.let { wineIdent ->
                val req = ComponentRequirement(
                    id = "wine",
                    type = wineTypeFor(wineIdent),
                    identifier = wineIdent,
                    displayLabel = prettyWine(wineIdent),
                    keysGuarded = listOf(KeyGuard(KeyGuard.Block.CONTAINER, "wineVersion")),
                )
                results += RequirementEntry(req, resolveWine(context, contentsManager, wineIdent))
            }

        // --- DXVK -------------------------------------------------------------
        // dxwrapper has values like "dxvk", "wined3d", "vkd3d", etc. Only the
        // "dxvk" wrapper carries a DXVK version in dxwrapperConfig.
        val dxwrapper = containerBlock.optString("dxwrapper", "").lowercase()
        val dxwrapperConfig = containerBlock.optString("dxwrapperConfig", "")
        if (dxwrapper.contains("dxvk")) {
            val token = readKey(dxwrapperConfig, "version")
            if (!token.isNullOrBlank() && !token.equals("None", ignoreCase = true)) {
                // The dropdown stores `verName + "-" + verCode` (see
                // ShortcutSettingsComposeDialog.kt:2202-2207). Split into the two
                // parts; if no trailing verCode, treat as verName-only.
                val resolved = resolveByVerNameAndCode(context, contentsManager, ContentProfile.ContentType.CONTENT_TYPE_DXVK, token)
                results += RequirementEntry(
                    ComponentRequirement(
                        id = "dxvk",
                        type = ContentProfile.ContentType.CONTENT_TYPE_DXVK,
                        identifier = token,
                        displayLabel = "DXVK · $token",
                        keysGuarded = listOf(
                            KeyGuard(KeyGuard.Block.CONTAINER, "dxwrapper"),
                            KeyGuard(KeyGuard.Block.CONTAINER, "dxwrapperConfig"),
                        ),
                    ),
                    resolved,
                )
            }
        }

        // --- VKD3D ------------------------------------------------------------
        // vkd3dVersion can appear in dxwrapperConfig regardless of the dxwrapper
        // kind (the wrapper "dxvk" enables both DXVK + VKD3D when the user opts
        // into DX12).
        val vk = readKey(dxwrapperConfig, "vkd3dVersion")
        if (!vk.isNullOrBlank() && !vk.equals("None", ignoreCase = true)) {
            val resolved = resolveByVerNameAndCode(context, contentsManager, ContentProfile.ContentType.CONTENT_TYPE_VKD3D, vk)
            results += RequirementEntry(
                ComponentRequirement(
                    id = "vkd3d",
                    type = ContentProfile.ContentType.CONTENT_TYPE_VKD3D,
                    identifier = vk,
                    displayLabel = "VKD3D · $vk",
                    keysGuarded = listOf(KeyGuard(KeyGuard.Block.DXWRAPPER_CONFIG_VKD3D, "vkd3dVersion")),
                ),
                resolved,
            )
        }

        // --- Box64 / WowBox64 / FEXCore ---------------------------------------
        // These store verName + verCode separately on Container, but the upload
        // serializer flattens them to a single string. We match by verName only,
        // accepting any verCode (newest-installed-wins).
        containerBlock.optString("box64Version").takeIf { it.isNotBlank() }?.let { ver ->
            val emu = containerBlock.optString("emulator", "").lowercase()
            val type = if (emu == "wowbox64") ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
            else ContentProfile.ContentType.CONTENT_TYPE_BOX64
            val resolved = resolveByVerNameAndCode(context, contentsManager, type, ver)
            results += RequirementEntry(
                ComponentRequirement(
                    id = "box64",
                    type = type,
                    identifier = ver,
                    displayLabel = "${type.toString().removePrefix("Box64").ifBlank { "Box64" }} · $ver",
                    keysGuarded = listOf(KeyGuard(KeyGuard.Block.CONTAINER, "box64Version")),
                ),
                resolved,
            )
        }

        containerBlock.optString("fexcoreVersion").takeIf { it.isNotBlank() }?.let { ver ->
            val resolved = resolveByVerNameAndCode(context, contentsManager, ContentProfile.ContentType.CONTENT_TYPE_FEXCORE, ver)
            results += RequirementEntry(
                ComponentRequirement(
                    id = "fexcore",
                    type = ContentProfile.ContentType.CONTENT_TYPE_FEXCORE,
                    identifier = ver,
                    displayLabel = "FEXCore · $ver",
                    keysGuarded = listOf(KeyGuard(KeyGuard.Block.CONTAINER, "fexcoreVersion")),
                ),
                resolved,
            )
        }

        // --- Graphics driver (not in ContentsManager) -------------------------
        containerBlock.optString("graphicsDriver").takeIf { it.isNotBlank() }?.let { gfx ->
            // Only flag if the driver value is something other than the built-in
            // "turnip"/"adreno"/"system" tokens — exact matches are always present.
            val builtIn = gfx.equals("turnip", ignoreCase = true) ||
                gfx.equals("adreno", ignoreCase = true) ||
                gfx.equals("system", ignoreCase = true) ||
                gfx.equals("virgl", ignoreCase = true) ||
                gfx.equals("llvmpipe", ignoreCase = true)
            if (!builtIn) {
                results += RequirementEntry(
                    ComponentRequirement(
                        id = "graphics-driver",
                        type = ContentProfile.ContentType.CONTENT_TYPE_WINE, // sentinel; not used for download
                        identifier = gfx,
                        displayLabel = "Graphics driver · $gfx",
                        keysGuarded = emptyList(),
                    ),
                    RequirementResolution.Unsupported(
                        "Custom graphics drivers can't be auto-installed yet. " +
                            "Install it from Settings → Drivers, then re-import.",
                    ),
                )
            }
        }

        return results
    }

    // -------------------------------------------------------------------------
    // Resolution helpers
    // -------------------------------------------------------------------------

    /**
     * Resolve a Wine/Proton identifier. See [findProfileWithFallback] for the
     * matching strategy — exact, then progressive prefix peel to find the latest
     * sibling in the same family (e.g. an old nightly maps to the newest one).
     */
    private fun resolveWine(
        context: Context,
        manager: ContentsManager,
        identifier: String,
    ): RequirementResolution {
        val type = wineTypeFor(identifier)
        // Built-in "main" wine ships with the app — always Installed.
        if (identifier.equals(MAIN_PROTON_IDENT, ignoreCase = true) ||
            identifier.equals(MAIN_WINE_IDENT, ignoreCase = true)
        ) {
            return RequirementResolution.Installed
        }
        // Canonical "Type-verName-verCode" — works when the user picked a custom
        // profile from the dropdown. This carries a real verCode so we route it
        // through the same find-with-fallback machinery as DXVK et al.
        manager.getProfileByEntryName(identifier)?.let { return classifyProfile(context, it) }

        // Strip the "type-" prefix so the rest of the matching logic operates on
        // verName-shaped tokens (e.g. "9.0-arm64ec" rather than "proton-9.0-arm64ec").
        val typePrefix = type.toString().lowercase()
        val token = if (identifier.startsWith("$typePrefix-", ignoreCase = true))
            identifier.substring(typePrefix.length + 1)
        else identifier

        // Lock to the requested architecture — wine/proton binaries are PE-format
        // and arm64ec vs x86_64 are not interchangeable. Without this guard the
        // peel fallback would happily downgrade an arm64ec request to x86_64.
        val archConstraint = WINE_ARCH_TAIL.find(identifier)?.groupValues?.get(1)

        val candidates = manager.getProfiles(type).orEmpty()
        val (match, substituted) = findProfileWithFallback(
            candidates,
            token,
            archConstraint = archConstraint,
        ) ?: return RequirementResolution.Unavailable("Not in the components catalog")
        return classifyProfile(context, match, substituteFor = if (substituted) identifier else null)
    }

    /**
     * Resolve a DXVK / VKD3D / Box64 / FEXCore token. The settings dialog stores
     * the dropdown value as `verName-verCode` (see ShortcutSettingsComposeDialog
     * line ~2207). We try the exact pair first, then peel back to verName-only,
     * then walk a prefix-fallback chain so an old nightly that's been pruned from
     * the catalog still finds the latest sibling in the same family.
     */
    private fun resolveByVerNameAndCode(
        context: Context,
        manager: ContentsManager,
        type: ContentProfile.ContentType,
        token: String,
    ): RequirementResolution {
        val candidates = manager.getProfiles(type).orEmpty()
        val (match, substituted) = findProfileWithFallback(candidates, token)
            ?: return RequirementResolution.Unavailable("Not in the components catalog")
        return classifyProfile(context, match, substituteFor = if (substituted) token else null)
    }

    /**
     * Progressive matcher used by every component resolver.
     *
     * Strategy, in order:
     *  1. Exact (verName + "-" + verCode) — when the token carries a trailing
     *     `-<digits>` and matches a stored profile perfectly.
     *  2. Exact verName, any verCode — picks newest verCode among matches.
     *  3. Prefix peel: drop the last hyphen-separated segment, search for any
     *     profile whose verName equals the new stem OR begins with `<stem>-`,
     *     pick newest verCode. Repeat until a match is found or no dashes left.
     *
     * Returns (profile, substituted) — `substituted = true` for anything found
     * via the peel-back path, so the UI can flag the substitution. Nightly builds
     * like `2.4.1-nightly-abc123` end up matching the latest `2.4.1-nightly-*`
     * even when the literal hash isn't in the catalog anymore.
     */
    private fun findProfileWithFallback(
        candidates: List<ContentProfile>,
        token: String,
        archConstraint: String? = null,
    ): Pair<ContentProfile, Boolean>? {
        // Family-tag stickiness: when the original asks for a nightly, restrict
        // peel candidates to other nightlies. Without this, `2.4.1-nightly-abc-1`
        // could collapse to `2.4.1-gplasync` once peel reaches `2.4.1` — silently
        // substituting a stable variant for what the user marked as nightly.
        val mustBeNightly = token.contains("nightly", ignoreCase = true)
        val filtered = candidates.filter { p ->
            (archConstraint == null || p.verName.endsWith("-$archConstraint", ignoreCase = true)) &&
                (!mustBeNightly || p.verName.contains("nightly", ignoreCase = true))
        }

        // 1. exact verName + verCode pair.
        filtered.firstOrNull { p ->
            "${p.verName}-${p.verCode}".equals(token, ignoreCase = true)
        }?.let { return it to false }

        // Strip any trailing "-<digits>" to get the bare verName.
        val trailingDigits = TRAILING_DIGITS_REGEX.find(token)
        val bareVerName = if (trailingDigits != null) {
            token.removeRange(trailingDigits.range)
        } else token

        // 2. exact verName, any verCode (pick newest).
        filtered.filter { it.verName.equals(bareVerName, ignoreCase = true) }
            .maxByOrNull { it.verCode }
            ?.let { return it to false }

        // 3. Progressive prefix peel.
        var stem = bareVerName
        while (true) {
            val lastDash = stem.lastIndexOf('-')
            if (lastDash <= 0) break
            stem = stem.substring(0, lastDash)
            val nextStem = "$stem-"
            val match = filtered
                .filter { p ->
                    p.verName.equals(stem, ignoreCase = true) ||
                        p.verName.startsWith(nextStem, ignoreCase = true)
                }
                .maxByOrNull { it.verCode }
            if (match != null) return match to true
        }
        return null
    }

    private fun classifyProfile(
        context: Context,
        profile: ContentProfile,
        substituteFor: String? = null,
    ): RequirementResolution {
        if (ContentsManager.isInstalled(context, profile)) return RequirementResolution.Installed
        val url = profile.remoteUrl
        return if (url.isNullOrEmpty()) RequirementResolution.Unavailable("Component found locally but no download URL")
        else RequirementResolution.Available(profile, substituteFor = substituteFor)
    }

    private fun readKey(dxwrapperConfig: String, key: String): String? = runCatching {
        DXVKConfigUtils.parseConfig(dxwrapperConfig).get(key)?.takeIf { it.isNotEmpty() }
    }.onFailure { Timber.tag(TAG).w(it, "Could not parse dxwrapperConfig key=$key") }.getOrNull()

    private val TRAILING_DIGITS_REGEX = Regex("-\\d+$")
    private val WINE_ARCH_TAIL = Regex("(?i)-(x86_64|arm64ec|x86)$")
    private const val MAIN_PROTON_IDENT = "proton-9.0-x86_64"
    private const val MAIN_WINE_IDENT = "wine-9.0-x86_64"

    /** Wine identifiers start with "wine-" or "proton-"; pick the matching type. */
    private fun wineTypeFor(identifier: String): ContentProfile.ContentType =
        if (identifier.startsWith("proton", ignoreCase = true))
            ContentProfile.ContentType.CONTENT_TYPE_PROTON
        else ContentProfile.ContentType.CONTENT_TYPE_WINE

    private fun prettyWine(identifier: String): String {
        // "proton-9.0-arm64ec" → "Proton 9.0 (arm64ec)"
        val parts = identifier.split('-')
        if (parts.size < 3) return identifier
        val type = parts.first().replaceFirstChar { it.uppercase() }
        val arch = parts.last()
        val version = parts.drop(1).dropLast(1).joinToString(".").ifBlank { parts[1] }
        return "$type $version ($arch)"
    }
}
