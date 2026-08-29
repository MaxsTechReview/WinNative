package com.winlator.cmod.feature.stores.steam.service

import com.winlator.cmod.feature.stores.steam.data.BranchInfo
import com.winlator.cmod.feature.stores.steam.data.ManifestInfo

/**
 * Pure branch/manifest selection rules, kept free of Android and Steam-session dependencies so the
 * behaviour that decides *which build gets downloaded* is unit testable on the JVM.
 */
object SteamBranchSelection {
    const val DEFAULT_BRANCH: String = "public"

    /**
     * Branches a user may pick. Password-protected branches are excluded because the depot pipeline
     * sends an empty branch password hash, so Steam would refuse their manifest request codes.
     */
    fun selectableBranches(branches: Map<String, BranchInfo>): List<BranchInfo> {
        if (branches.isEmpty()) return emptyList()
        return branches
            .map { (name, info) -> if (info.name.isBlank()) info.copy(name = name) else info }
            .filterNot { it.pwdRequired }
            .filterNot { it.name.isBlank() }
            .distinctBy { it.name.lowercase() }
            .sortedWith(
                compareByDescending<BranchInfo> { it.name.equals(DEFAULT_BRANCH, ignoreCase = true) }
                    .thenBy { it.name.lowercase() },
            )
    }

    /**
     * Resolves the branch a download should use. An unknown or password-protected stored value falls
     * back to public rather than silently downloading branch-less content under the wrong name.
     */
    fun resolveBranch(
        stored: String?,
        branches: Map<String, BranchInfo>,
    ): String {
        val trimmed = stored.orEmpty().trim()
        if (trimmed.isEmpty() || trimmed.equals(DEFAULT_BRANCH, ignoreCase = true)) return DEFAULT_BRANCH
        if (branches.isEmpty()) return trimmed
        val match = branches.entries.firstOrNull { it.key.equals(trimmed, ignoreCase = true) }
        if (match == null || match.value.pwdRequired) return DEFAULT_BRANCH
        return match.key
    }

    /** Build id published for [branch], falling back to public so callers never report a bare 0. */
    fun buildIdForBranch(
        branches: Map<String, BranchInfo>,
        branch: String,
    ): Long =
        branches[branch]?.buildId
            ?: branches.entries.firstOrNull { it.key.equals(branch, ignoreCase = true) }?.value?.buildId
            ?: branches[DEFAULT_BRANCH]?.buildId
            ?: 0L

    /**
     * Build id to report as installed. A recorded value always wins; the branch's current build id is
     * only a fallback for installs made before the installed build id was tracked.
     */
    fun installedBuildId(
        recorded: Long,
        branches: Map<String, BranchInfo>,
        branch: String,
    ): Long = if (recorded > 0L) recorded else buildIdForBranch(branches, branch)

    /**
     * Manifest to report for an installed depot. The id recorded on disk wins over the branch's
     * current manifest, so an out-of-date install is never described as if it were the newest build.
     */
    fun installedManifest(
        resolved: ManifestInfo?,
        onDiskManifestId: Long?,
        branch: String = DEFAULT_BRANCH,
    ): ManifestInfo? {
        val onDisk = onDiskManifestId?.takeIf { it > 0L && it != Long.MAX_VALUE }
        val manifest =
            when {
                onDisk == null -> resolved
                resolved == null -> ManifestInfo(name = branch, gid = onDisk, size = 0L, download = 0L)
                resolved.gid == onDisk -> resolved
                else -> resolved.copy(gid = onDisk)
            }
        return manifest?.takeIf { it.gid != 0L }
    }

    /** True when the content on disk is behind the branch's published build. */
    fun isInstallStale(
        installedBuildId: Long,
        branches: Map<String, BranchInfo>,
        branch: String,
    ): Boolean {
        if (installedBuildId <= 0L) return false
        val latest = buildIdForBranch(branches, branch)
        return latest > 0L && latest != installedBuildId
    }
}
