package com.winlator.cmod.feature.configs.installflow

import com.winlator.cmod.runtime.content.ContentProfile

/**
 * One required component identified from an imported config.
 *
 * `identifier` is whatever token the config carries (full entry name for Wine /
 * Proton / DXVK / VKD3D, plain `verName` for Box64/FEXCore). `displayLabel` is the
 * pretty version we show in the dialog. `keysGuarded` lists the JSON keys whose
 * presence in the config depends on this component being installed — used by the
 * "Apply available only" fallback to strip keys whose component is missing.
 */
data class ComponentRequirement(
    val id: String,
    val type: ContentProfile.ContentType,
    val identifier: String,
    val displayLabel: String,
    val keysGuarded: List<KeyGuard>,
) {
    /** Where in the config-JSON tree a key lives — used when we want to drop it. */
    data class KeyGuard(
        val block: Block,
        val key: String,
    ) {
        enum class Block { CONTAINER, CONTAINER_EXTRAS, SHORTCUT_EXTRAS, DXWRAPPER_CONFIG_VKD3D }
    }
}

/**
 * Result of matching a [ComponentRequirement] against the local + remote profile
 * inventory. Sealed so the UI can pattern-match (and so the coordinator can pick
 * which rows are queued for download).
 */
sealed class RequirementResolution {
    /** Already on disk — nothing to do. */
    object Installed : RequirementResolution()

    /**
     * In the remote catalog and downloadable. [profile] has a non-null `remoteUrl`.
     *
     * [substituteFor] is non-null when the catalog had no exact match for the
     * requested version and we fell back to the latest sibling in the same nightly
     * family. The UI surfaces this as a "→ latest" hint so the user knows they're
     * getting a substitute rather than the literal version the config asked for.
     */
    data class Available(
        val profile: ContentProfile,
        val substituteFor: String? = null,
    ) : RequirementResolution()

    /** No matching entry in the catalog (or matched but no `remoteUrl`). [reason] is shown inline. */
    data class Unavailable(val reason: String) : RequirementResolution()

    /**
     * The component lives outside the ContentsManager system (graphics drivers via
     * AdrenotoolsManager, anything we don't yet support). Shown disabled with the
     * given guidance message.
     */
    data class Unsupported(val reason: String) : RequirementResolution()
}

/** Decorated requirement + resolution + (later) per-row UI state in the dialog. */
data class RequirementEntry(
    val requirement: ComponentRequirement,
    val resolution: RequirementResolution,
)
