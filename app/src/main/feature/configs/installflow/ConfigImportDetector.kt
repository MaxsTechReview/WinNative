package com.winlator.cmod.feature.configs.installflow

import android.content.Context
import com.winlator.cmod.feature.configs.installflow.ComponentRequirement.KeyGuard
import com.winlator.cmod.feature.settings.DXVKConfigUtils
import com.winlator.cmod.feature.settings.GraphicsDriverConfigUtils
import com.winlator.cmod.runtime.content.AdrenotoolsManager
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
        driverCandidates: List<DriverAssetCandidate> = emptyList(),
    ): List<RequirementEntry> {
        // Log the effective values the detector is about to act on. Visible in
        // adb logcat regardless of Timber being planted, so a missing-dialog
        // bug report is debuggable from the user's log alone.
        android.util.Log.d(
            TAG,
            "detect: containerKeys=${configJson.optJSONObject("container")?.keys()?.asSequence()?.toList()?.sorted()} " +
                "shortcutExtraKeys=${configJson.optJSONObject("shortcutExtras")?.keys()?.asSequence()?.toList()?.sorted()} " +
                "driverCandidates=${driverCandidates.size}",
        )
        // Look up the effective value of a config key across the two blocks the
        // serializer writes: per-shortcut override (`shortcutExtras`) wins over
        // container default (`container`). Without this merge, a community
        // config whose uploader had `dxwrapper="wined3d"` at the container level
        // and a per-shortcut override to `"dxvk"` would silently skip DXVK
        // detection — the detector would only see "wined3d" from `container`
        // and never notice that the shortcut actually needs DXVK installed.
        val containerBlock = configJson.optJSONObject("container") ?: JSONObject()
        val shortcutExtrasBlock = configJson.optJSONObject("shortcutExtras") ?: JSONObject()
        fun effective(key: String): String {
            val override = shortcutExtrasBlock.optString(key, "")
            if (override.isNotEmpty()) return override
            return containerBlock.optString(key, "")
        }
        val results = mutableListOf<RequirementEntry>()

        // --- Wine / Proton -----------------------------------------------------
        effective("wineVersion")
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
        val dxwrapper = effective("dxwrapper").lowercase()
        val dxwrapperConfig = effective("dxwrapperConfig")
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
        effective("box64Version").takeIf { it.isNotBlank() }?.let { ver ->
            val emu = effective("emulator").lowercase()
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

        effective("fexcoreVersion").takeIf { it.isNotBlank() }?.let { ver ->
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

        // --- Graphics driver (adrenotools, via GitHub release repos) ----------
        //
        // The wrapper type (`graphicsDriver`: turnip / adreno / system / virgl /
        // llvmpipe) is always present — those are built into the app. The
        // *specific* adrenotools driver build the shortcut wants is the
        // `version=` value inside `graphicsDriverConfig` (set when the user
        // installs a custom Mesa Turnip / Adreno-Tools driver from the Drivers
        // screen).
        val gfxConfig = effective("graphicsDriverConfig")
        val gfxVersion = if (gfxConfig.isNotBlank()) {
            runCatching {
                GraphicsDriverConfigUtils.parseGraphicsDriverConfig(gfxConfig)["version"]
            }.getOrNull()
        } else null
        if (!gfxVersion.isNullOrBlank() && !gfxVersion.equals("System", ignoreCase = true)) {
            val resolved = resolveGraphicsDriver(context, gfxVersion, driverCandidates)
            results += RequirementEntry(
                ComponentRequirement(
                    id = "graphics-driver",
                    type = ContentProfile.ContentType.CONTENT_TYPE_WINE, // sentinel; install path branches on id
                    identifier = gfxVersion,
                    displayLabel = "Graphics driver · $gfxVersion",
                    keysGuarded = listOf(KeyGuard(KeyGuard.Block.CONTAINER, "graphicsDriverConfig")),
                ),
                resolved,
            )
        }

        // Per-requirement summary — what each component resolved to. This is
        // the line to grep for in logcat to confirm whether the dialog SHOULD
        // surface a download row (Available / AvailableDriver / Unavailable
        // count as user-actionable).
        results.forEach { e ->
            val resName = e.resolution::class.java.simpleName
            android.util.Log.d(
                TAG,
                "detect: ${e.requirement.id}='${e.requirement.identifier}' → $resName",
            )
        }
        return results
    }

    /**
     * Resolve a graphics-driver identifier against the adrenotools install dir
     * and (if the local check misses) every asset published in the user's
     * configured driver-repo GitHub releases.
     *
     * Match strategy:
     *   1. Built-in token ("System") → [RequirementResolution.Installed].
     *   2. Identifier matches a directory under the adrenotools content dir →
     *      [RequirementResolution.Installed].
     *   3. Identifier (or a normalized variant) appears as a substring of any
     *      candidate asset name → [RequirementResolution.AvailableDriver].
     *      When multiple assets match, pick the newest by `publishedAt` so
     *      nightly identifiers fall forward to the latest build automatically.
     *   4. Otherwise → [RequirementResolution.Unavailable].
     */
    private fun resolveGraphicsDriver(
        context: Context,
        identifier: String,
        candidates: List<DriverAssetCandidate>,
    ): RequirementResolution {
        if (identifier.equals("System", ignoreCase = true)) return RequirementResolution.Installed
        runCatching {
            val adrenotools = AdrenotoolsManager(context)
            val installed = adrenotools.enumarateInstalledDrivers()
            if (installed.any { it.equals(identifier, ignoreCase = true) }) {
                return RequirementResolution.Installed
            }
            // Some configs store the *source asset filename* (e.g.
            // "Turnip_25.0.0_v1.zip") rather than the installed driver
            // directory name. Cross-check via getSourceAsset() so we don't
            // re-download something we already have.
            val asAsset = installed.firstOrNull { driverId ->
                runCatching { adrenotools.getSourceAsset(driverId) }
                    .getOrDefault("")
                    .equals(identifier, ignoreCase = true)
            }
            if (asAsset != null) return RequirementResolution.Installed
        }.onFailure { Timber.tag(TAG).w(it, "adrenotools enumerate failed") }

        if (candidates.isEmpty()) {
            return RequirementResolution.Unavailable(
                "No graphics-driver repositories configured. Add one in Settings → Drivers, then re-import.",
            )
        }

        // 1. Exact-ish: asset name contains the full requested identifier.
        val exact = candidates
            .filter { it.assetName.contains(identifier, ignoreCase = true) }
            .maxByOrNull { it.publishedAt }
        if (exact != null) {
            return RequirementResolution.AvailableDriver(
                downloadUrl = exact.downloadUrl,
                assetName = exact.assetName,
                repoName = exact.repoName,
            )
        }

        // 2. Family fallback: strip the build-tag suffix and try the bare
        //    family prefix (e.g. "turnip-r6.0.0_nightly_abc" → "turnip-r6.0.0"
        //    → "turnip"). Surfaces an "→ latest" hint in the UI via substituteFor.
        var stem = identifier
        while (stem.isNotBlank()) {
            val lastDash = stem.lastIndexOfAny(charArrayOf('-', '_', '.'))
            if (lastDash <= 0) break
            stem = stem.substring(0, lastDash)
            if (stem.length < 3) break // refuse a degenerate match like "t"
            val sub = candidates
                .filter { it.assetName.contains(stem, ignoreCase = true) }
                .maxByOrNull { it.publishedAt }
            if (sub != null) {
                return RequirementResolution.AvailableDriver(
                    downloadUrl = sub.downloadUrl,
                    assetName = sub.assetName,
                    repoName = sub.repoName,
                    substituteFor = identifier,
                )
            }
        }

        return RequirementResolution.Unavailable(
            "No matching driver asset in any configured repo.",
        )
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
     *
     * Resolution attempts (first hit wins):
     *  1. [findProfileWithFallback] on the raw token.
     *  2. Same, but with the lowercased ContentType prefix stripped — handles
     *     tokens like `vkd3d-3.0.1-nightly-abc` matching a catalog entry whose
     *     verName is just `3.0.1-nightly-abc` (or the reverse).
     *  3. Numeric-version peel: extract the leading "X.Y(.Z…)" version from
     *     the token and look for any catalog entry whose verName starts with
     *     the SAME version followed by a `-` build-tag. This is what catches
     *     the user-reported case — `3.0.1-aaffggs-1` finding `3.0.1-jjkskls-2`
     *     even when the dash-peel doesn't reach because the catalog's verName
     *     never matched the original tag.
     */
    private fun resolveByVerNameAndCode(
        context: Context,
        manager: ContentsManager,
        type: ContentProfile.ContentType,
        token: String,
    ): RequirementResolution {
        val candidates = manager.getProfiles(type).orEmpty()
        val (match, substituted) = findProfileWithFallback(candidates, token)
            ?: run {
                // Try again with a stripped type prefix (defensive — some
                // catalogs include the prefix in verName, some don't).
                val typePrefix = type.toString().lowercase()
                val stripped = if (token.startsWith("$typePrefix-", ignoreCase = true)) {
                    token.substring(typePrefix.length + 1)
                } else null
                stripped?.let { findProfileWithFallback(candidates, it) }
            }
            ?: matchByNumericVersion(candidates, token)
            ?: return RequirementResolution.Unavailable("Not in the components catalog")
        return classifyProfile(context, match, substituteFor = if (substituted) token else null)
    }

    /**
     * Last-resort matcher: extract a `X.Y(.Z…)` numeric-version stem from
     * [token] AND from each candidate's verName, then match on equal version
     * stems. Picks newest verCode. This is what catches the user-reported
     * case — `3.0.1-aaffggs-1` finding `3.0.1-jjkskls-2` (or any other
     * `3.0.1-<random-tag>`) even when the catalog has zero entries whose
     * verName begins literally with the original request's build-tag.
     *
     * Extracting on BOTH sides means we naturally tolerate type-prefix noise
     * — `vkd3d-3.0.1-tag` and `3.0.1-tag` both extract to `3.0.1`.
     *
     * Returns `(match, substituted=true)` when the picked verName differs
     * from the request stem, so the UI surfaces a "→ latest" hint and
     * [applyConfig] rewrites the persisted shortcut value.
     */
    private fun matchByNumericVersion(
        candidates: List<ContentProfile>,
        token: String,
    ): Pair<ContentProfile, Boolean>? {
        val tokenVersion = NUMERIC_VERSION_ANYWHERE.find(token)?.value ?: return null
        if (tokenVersion.isBlank()) return null
        val reqSignature = extractSignature(token)
        val match = candidates
            .filter { p ->
                val cv = NUMERIC_VERSION_ANYWHERE.find(p.verName)?.value
                if (cv == null || !cv.equals(tokenVersion, ignoreCase = true)) return@filter false
                signatureMatches(reqSignature, extractSignature(p.verName))
            }
            .maxByOrNull { it.verCode }
            ?: run {
                android.util.Log.d(TAG, "matchByNumericVersion: no signature-compatible match for '$token' (sig=$reqSignature) — ${candidates.size} candidates")
                return null
            }
        val tokenBare = TRAILING_DIGITS_REGEX
            .find(token)
            ?.let { token.removeRange(it.range) }
            ?: token
        val substituted = !match.verName.equals(tokenBare, ignoreCase = true)
        android.util.Log.d(TAG, "matchByNumericVersion: '$token' → ${match.verName}-${match.verCode} (substituted=$substituted)")
        return match to substituted
    }

    /**
     * Progressive matcher used by every component resolver.
     *
     * Strategy, in order:
     *  1. Exact (verName + "-" + verCode) — when the token carries a trailing
     *     `-<digits>` and matches a stored profile perfectly.
     *  2. Exact verName, any verCode — picks newest verCode among matches.
     *  3. Prefix peel with **signature lock**: drop the last hyphen-separated
     *     segment of the token, search for any profile whose verName equals
     *     the new stem OR begins with `<stem>-`, but ALSO has a matching
     *     `(arch, variants)` signature — same arch tag (or both none) and the
     *     same set of recognised variant tokens (gplasync / async / pre / reg
     *     / nightly / etc.). This stops the peel from silently substituting,
     *     say, the request `2.4.1-gplasync-pre-reg` with a locally-installed
     *     `2.4.1-1-gplasync-arm64ec-pre-reg` (different arch) or a catalog
     *     `Dxvk-2.4.1-pre-reg` (missing the gplasync variant).
     *
     * Returns (profile, substituted) — `substituted = true` for anything found
     * via the peel-back path, so the UI can flag the substitution. Nightly
     * builds with random alphanumeric build hashes (`2.4.1-nightly-abc123`)
     * still match the latest sibling because hash tokens aren't part of the
     * variant whitelist.
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
        }?.let {
            android.util.Log.d(TAG, "findProfileWithFallback: exact verName-verCode match for '$token' → ${it.verName}-${it.verCode}")
            return it to false
        }

        // Strip any trailing "-<digits>" to get the bare verName.
        val trailingDigits = TRAILING_DIGITS_REGEX.find(token)
        val bareVerName = if (trailingDigits != null) {
            token.removeRange(trailingDigits.range)
        } else token

        // 2. exact verName, any verCode (pick newest).
        filtered.filter { it.verName.equals(bareVerName, ignoreCase = true) }
            .maxByOrNull { it.verCode }
            ?.let {
                android.util.Log.d(TAG, "findProfileWithFallback: exact verName match for '$bareVerName' → ${it.verName}-${it.verCode}")
                return it to false
            }

        // 3. Progressive prefix peel — but only accept candidates whose
        // (arch, variants) signature matches the request. See extractSignature
        // for the rules. Without this filter the peel walks down to a bare
        // numeric version like `2.4.1` and grabs ANY sibling at that level,
        // including ones with a different arch or missing/extra variant
        // tokens — which the original test scenario blew up on.
        val reqSignature = extractSignature(token)
        var stem = bareVerName
        while (true) {
            val lastDash = stem.lastIndexOf('-')
            if (lastDash <= 0) break
            stem = stem.substring(0, lastDash)
            val nextStem = "$stem-"
            val match = filtered
                .filter { p ->
                    val nameMatches = p.verName.equals(stem, ignoreCase = true) ||
                        p.verName.startsWith(nextStem, ignoreCase = true)
                    nameMatches && signatureMatches(reqSignature, extractSignature(p.verName))
                }
                .maxByOrNull { it.verCode }
            if (match != null) {
                android.util.Log.d(TAG, "findProfileWithFallback: peel match for '$token' (stem '$stem') → ${match.verName}-${match.verCode}")
                return match to true
            }
        }
        android.util.Log.d(TAG, "findProfileWithFallback: no match for '$token' (sig=${reqSignature}); ${filtered.size} candidates")
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

    /**
     * Known architecture markers in component identifiers. Treated as a hard
     * constraint when matching: if the request specifies an arch, the
     * candidate must specify the same one (and vice versa — a request
     * without an arch tag will NOT match a candidate that has one, because
     * arch-tagged builds are not arch-agnostic).
     */
    private val ARCH_TOKENS = setOf(
        "arm", "arm64", "arm64ec", "x86", "x86_64", "i686",
    )

    /**
     * Known variant tokens. Used to distinguish meaningful behavioural
     * differences (gplasync vs async, pre-reg, nightly, fsync, etc.) from
     * random build hashes (e.g. `60978eb9`, `aaffggs`). A candidate matches
     * a request iff their variant *sets* (intersection of identifier tokens
     * with this whitelist) are equal. Anything not on the list is treated
     * as a build hash and ignored — so an old nightly hash naturally falls
     * forward to the newest sibling with the same variant signature.
     */
    private val VARIANT_TOKENS = setOf(
        "gplasync", "async",
        "pre", "reg", "nightly",
        "fsync", "ntsync",
        "sarek", "stripped",
        "ge", "be",
        "denuvo", "fix", "tfix",
        "special", "tilting", "coffincolors",
        "ref4ik", "bionic", "wow",
        "g", "d8",
    )

    /**
     * (arch?, variantSet) signature for a component identifier. Used by
     * [signatureMatches] to gate peel-back / numeric-version substitutions
     * so they don't silently swap variants or archs.
     */
    private fun extractSignature(identifier: String): Pair<String?, Set<String>> {
        var arch: String? = null
        val variants = mutableSetOf<String>()
        for (raw in identifier.split('-')) {
            val token = raw.lowercase()
            when {
                token.isEmpty() -> Unit
                token in ARCH_TOKENS -> arch = token
                token in VARIANT_TOKENS -> variants += token
                else -> Unit // build hash / version / verCode / type prefix
            }
        }
        return arch to variants
    }

    private fun signatureMatches(
        req: Pair<String?, Set<String>>,
        cand: Pair<String?, Set<String>>,
    ): Boolean = req.first == cand.first && req.second == cand.second

    /**
     * Captures the "extended version" of an identifier — the leading numeric
     * `X.Y(.Z…)` run plus any **directly attached** letters (no separator).
     *
     * Examples:
     *  - `vkd3d-3.0.1-aaffggs-1`   → `3.0.1`
     *  - `3.0.1-jjkskls`           → `3.0.1`
     *  - `3.0b`                    → `3.0b`   (attached letter = version letter)
     *  - `3.0-b`                   → `3.0`    (separator → `b` is a tag)
     *  - `proton-9.0-arm64ec-2`    → `9.0`
     *
     * This is what lets the user-reported rule work: short attached suffixes
     * like the `b` in `3.0b` stay part of the version and demand an exact
     * match, while long alphanumeric build tags after a `-` / `_` / `.`
     * separator are treated as substitutable so a new nightly hash can
     * replace an older one as long as the version itself is unchanged.
     */
    private val NUMERIC_VERSION_ANYWHERE = Regex("\\d+(?:\\.\\d+)+[a-zA-Z]*")
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
