package com.winlator.cmod.feature.configs.installflow

/**
 * The state machine the [ConfigImportCoordinator] drives. Sealed so the UI can
 * pattern-match exhaustively. See the file-level KDoc on the coordinator for the
 * full transition table.
 */
sealed class ImportState {
    object Idle : ImportState()

    /** Catalog refresh + missing-component analysis in flight. */
    object Analyzing : ImportState()

    /**
     * Dialog is showing. [entries] lists every required component plus its
     * resolution. [selectedIds] tracks the checkbox state. [archMismatch] is
     * non-null if the imported Wine identifier targets a different arch from
     * the current shortcut's container.
     */
    data class ChoosingComponents(
        val entries: List<RequirementEntry>,
        val selectedIds: Set<String>,
        val archMismatch: ArchMismatch?,
    ) : ImportState()

    /**
     * Downloads in flight. [rowStates] is one entry per requirement (a row that
     * isn't in this map is not being downloaded — e.g., already installed or
     * not selected).
     */
    data class Downloading(
        val entries: List<RequirementEntry>,
        val rowStates: Map<String, RowState>,
        val archMismatch: ArchMismatch?,
    ) : ImportState()

    /** Final apply step. Briefly visible while writing the shortcut. */
    object Applying : ImportState()

    /** Terminal success. */
    data class Done(val message: String) : ImportState()

    /** Terminal failure that the user must dismiss. */
    data class Failed(val reason: String) : ImportState()
}

/**
 * Per-row UI state during the [ImportState.Downloading] phase. The transition
 * Queued → Downloading(...) → Installing → Done is monotonic; only Retry can
 * move a Failed row back to Downloading.
 */
sealed class RowState {
    object Queued : RowState()
    data class Downloading(val fraction: Float?) : RowState()
    object Installing : RowState()
    object Done : RowState()
    data class Failed(val reason: String) : RowState()
}

data class ArchMismatch(
    val expectedArch: String,
    val containerArch: String,
)
