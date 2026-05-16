package com.winlator.cmod.feature.configs.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.winlator.cmod.feature.configs.ConfigRepository
import com.winlator.cmod.feature.configs.ConfigSerializer
import com.winlator.cmod.feature.configs.GpuDetector
import com.winlator.cmod.feature.configs.UploaderIdentity
import com.winlator.cmod.feature.configs.data.ConfigRow
import com.winlator.cmod.feature.configs.installflow.ConfigImportCoordinator
import com.winlator.cmod.feature.configs.installflow.ImportState
import com.winlator.cmod.runtime.container.ContainerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class BestConfigsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: ConfigRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    /**
     * Game-context fields. Defaulted from the SavedStateHandle for the
     * NavHost-route entry point; overridden via [bindGame] when the screen is
     * hosted inside the LibraryGameDetailDialog (where there are no nav args).
     */
    var gameSource: String = savedStateHandle.get<String>(NAV_ARG_GAME_SOURCE) ?: "CUSTOM_GAME"
        private set
    var gameId: String = savedStateHandle.get<String>(NAV_ARG_GAME_ID) ?: ""
        private set
    var gameName: String = savedStateHandle.get<String>(NAV_ARG_GAME_NAME) ?: ""
        private set

    private val deviceModel: String = Build.MODEL ?: ""
    private val deviceGpuModel: String = GpuDetector.detect()

    private val _state = MutableStateFlow(
        BestConfigsUiState(
            gameName = gameName,
            gameSource = gameSource,
            filter = ConfigFilter.MY_DEVICE,
        ),
    )
    val state: StateFlow<BestConfigsUiState> = _state.asStateFlow()

    /**
     * Re-target this ViewModel at a (possibly different) game and reload.
     *
     * Always triggers [refresh] so a fresh open of Community on the same game
     * picks up new server-side entries; only resets the visible row list when
     * the game actually changed (so we don't flash an empty list on a same-
     * game reopen). Safe to call from a LaunchedEffect keyed on the game tuple
     * — re-renders inside a single screen entry won't re-fire it.
     */
    fun bindGame(source: String, id: String, name: String) {
        val gameChanged = gameSource != source || gameId != id || gameName != name
        if (gameChanged) {
            gameSource = source
            gameId = id
            gameName = name
            _state.update {
                it.copy(
                    gameName = name,
                    gameSource = source,
                    phase = LoadPhase.Idle,
                    allRows = emptyList(),
                    errorMessage = null,
                )
            }
        }
        refresh()
    }

    /**
     * Owns the Missing-Components import flow. Survives config changes (ViewModel-
     * scoped) but is disposed when the ViewModel is destroyed. Re-used across
     * multiple import attempts — each call to [importWithComponentCheck] re-enters
     * the same coordinator from its Idle state.
     */
    private val importCoordinator: ConfigImportCoordinator = ConfigImportCoordinator(appContext)
    val importState: StateFlow<ImportState> = importCoordinator.state

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(phase = LoadPhase.Loading, errorMessage = null) }
            val res = repository.listForGame(gameSource = gameSource, gameId = gameId)
            res.fold(
                onSuccess = { rows ->
                    val myVotes = repository.myVotesIn(rows.map { it.id }).getOrNull() ?: emptyMap()
                    // Device id is cheap; the Play Games name lookup is best-effort here
                    // — refresh is called before the user has done anything, so we don't
                    // want it to block the list rendering. Fall back to anonymous if the
                    // ViewModel has no Activity (it doesn't).
                    val anonId = UploaderIdentity.resolveAnonymous(appContext)
                    _state.update {
                        it.copy(
                            phase = LoadPhase.Loaded,
                            allRows = rows,
                            myVotesByConfigId = myVotes,
                            myUserId = repository.currentUserId(),
                            myDeviceId = anonId.deviceId,
                            myUploaderName = it.myUploaderName, // preserve any name we already learned
                        )
                    }
                },
                onFailure = { ex ->
                    Timber.tag(TAG).w(ex, "listForGame failed")
                    _state.update {
                        it.copy(
                            phase = LoadPhase.Error,
                            errorMessage = ex.message ?: "Could not load configs.",
                        )
                    }
                },
            )
        }
    }

    /**
     * Refresh and additionally learn the signed-in Google Play Games display name
     * (if any) so rows uploaded by the same user from a different install can be
     * recognized as "mine" and offered a delete button. Call from an Activity-
     * scoped composition once the Best Configs screen is visible.
     */
    fun bindActivityIdentity(activity: Activity) {
        viewModelScope.launch {
            val identity = UploaderIdentity.resolve(activity)
            _state.update {
                it.copy(
                    myDeviceId = identity.deviceId,
                    myUploaderName = identity.name,
                )
            }
        }
    }

    fun setFilter(filter: ConfigFilter) {
        _state.update { it.copy(filter = filter) }
    }

    /**
     * Tap the upvote arrow. If the user already upvoted → undo (clear). If they had
     * downvoted → flip to upvote (delta +2). Otherwise → add upvote (delta +1).
     */
    fun upvote(row: ConfigRow) {
        val current = _state.value.myVotesByConfigId[row.id] ?: 0
        applyVote(row, target = if (current == 1) 0 else 1, previous = current)
    }

    /** Tap the downvote arrow. Mirror of [upvote]. */
    fun downvote(row: ConfigRow) {
        val current = _state.value.myVotesByConfigId[row.id] ?: 0
        applyVote(row, target = if (current == -1) 0 else -1, previous = current)
    }

    /**
     * Optimistic-UI core. Updates the state with the predicted vote count delta,
     * then writes through to the server. On failure, reverts the optimistic state.
     *
     * delta = target - previous (e.g., switching -1 → +1 is a +2 swing).
     */
    private fun applyVote(row: ConfigRow, target: Int, previous: Int) {
        if (target == previous) return
        val delta = target - previous
        _state.update {
            it.copy(
                myVotesByConfigId = it.myVotesByConfigId.toMutableMap().apply {
                    if (target == 0) remove(row.id) else put(row.id, target)
                },
                allRows = it.allRows.map { r ->
                    if (r.id == row.id) r.copy(voteCount = r.voteCount + delta) else r
                },
            )
        }
        viewModelScope.launch {
            val res = if (target == 0) repository.unvote(row.id) else repository.vote(row.id, target)
            res.onFailure { ex ->
                Timber.tag(TAG).w(ex, "applyVote failed (target=$target previous=$previous)")
                // Revert optimistic update.
                _state.update {
                    it.copy(
                        myVotesByConfigId = it.myVotesByConfigId.toMutableMap().apply {
                            if (previous == 0) remove(row.id) else put(row.id, previous)
                        },
                        allRows = it.allRows.map { r ->
                            if (r.id == row.id) r.copy(voteCount = r.voteCount - delta) else r
                        },
                        errorMessage = "Vote failed: ${ex.message ?: ""}",
                    )
                }
            }
        }
    }

    /**
     * Apply [row]'s config to the shortcut matching (gameSource, gameId). Routes
     * through [ConfigImportCoordinator] so missing components (Wine / DXVK /
     * VKD3D / Box64 / FEXCore) get detected and offered for download before the
     * config is actually applied. [onResult] is invoked exactly once when the
     * coordinator reaches a terminal state (Done or Failed).
     */
    fun import(row: ConfigRow, onResult: (String) -> Unit) {
        viewModelScope.launch {
            // ContainerManager + loadShortcuts touch the filesystem — keep off Main.
            val match = withContext(Dispatchers.IO) {
                runCatching {
                    val cm = ContainerManager(appContext)
                    cm.loadShortcuts().firstOrNull { sc ->
                        val src = sc.getExtra("game_source") ?: ""
                        if (src != gameSource) return@firstOrNull false
                        val id = ConfigSerializer.gameIdForShortcut(sc, gameSource)
                        id != null && id == gameId
                    }
                }.getOrNull()
            }
            if (match == null) {
                onResult("No shortcut found for ${gameName}. Install or create the shortcut first.")
                return@launch
            }
            val container = match.container
            if (container == null) {
                onResult("Shortcut has no container; cannot import settings.")
                return@launch
            }
            importCoordinator.start(row.configJson, container, match, onResult)
        }
    }

    /**
     * Apply an arbitrary config JSON to the matching shortcut. Used by the
     * Settings dialog's Preview→Import path: the user has just reviewed (and
     * possibly tweaked) a community config in the Settings UI; on tap of
     * "Import" the dialog serialises its current state to JSON and hands it
     * here, so missing components get downloaded through the same coordinator
     * the row-level Import button uses. Nothing is uploaded to Supabase.
     */
    fun importPreviewedConfig(json: org.json.JSONObject, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val match = withContext(Dispatchers.IO) {
                runCatching {
                    val cm = ContainerManager(appContext)
                    cm.loadShortcuts().firstOrNull { sc ->
                        val src = sc.getExtra("game_source") ?: ""
                        if (src != gameSource) return@firstOrNull false
                        val id = ConfigSerializer.gameIdForShortcut(sc, gameSource)
                        id != null && id == gameId
                    }
                }.getOrNull()
            }
            if (match == null) {
                onResult("No shortcut found for ${gameName}. Open Settings once to create one, then try again.")
                return@launch
            }
            val container = match.container
            if (container == null) {
                onResult("Shortcut has no container; cannot apply settings.")
                return@launch
            }
            importCoordinator.start(json, container, match, onResult)
        }
    }

    /**
     * Delete [row] from the community board. The client only invokes this when
     * [BestConfigsUiState.isOwnedByMe] returned true for the row; the server
     * still enforces ownership via RLS.
     */
    fun delete(row: ConfigRow, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val res = repository.deleteConfig(row.id)
            res.fold(
                onSuccess = {
                    _state.update { st ->
                        st.copy(allRows = st.allRows.filterNot { it.id == row.id })
                    }
                    onResult("Removed your community config.")
                },
                onFailure = { ex ->
                    Timber.tag(TAG).w(ex, "deleteConfig failed")
                    onResult("Delete failed: ${ex.message ?: "unknown error"}")
                },
            )
        }
    }

    /** Dialog actions delegate to the coordinator. */
    fun toggleImportSelection(id: String) = importCoordinator.toggleSelection(id)
    fun confirmImportDownload() = importCoordinator.confirmDownload()
    fun applyImportAvailableOnly() = importCoordinator.applyAvailableOnly()
    fun retryImportRow(id: String) = importCoordinator.retry(id)
    fun cancelImport() = importCoordinator.cancel()

    override fun onCleared() {
        importCoordinator.dispose()
        super.onCleared()
    }

    val currentDeviceModel: String get() = deviceModel
    val currentGpuModel: String get() = deviceGpuModel

    companion object {
        private const val TAG = "BestConfigsViewModel"
        const val NAV_ARG_GAME_SOURCE = "gameSource"
        const val NAV_ARG_GAME_ID = "gameId"
        const val NAV_ARG_GAME_NAME = "gameName"
    }
}

enum class ConfigFilter { MY_DEVICE, MY_GPU, ALL }

enum class LoadPhase { Idle, Loading, Loaded, Error }

data class BestConfigsUiState(
    val gameName: String,
    val gameSource: String,
    val filter: ConfigFilter,
    val phase: LoadPhase = LoadPhase.Idle,
    val allRows: List<ConfigRow> = emptyList(),
    /** Per-config vote direction the current user has cast (+1 / -1). Absence = no vote. */
    val myVotesByConfigId: Map<String, Int> = emptyMap(),
    val myUserId: String? = null,
    /** Stable per-install ANDROID_ID — second ownership signal alongside GPG name. */
    val myDeviceId: String? = null,
    /** Signed-in Google Play Games display name, if any. */
    val myUploaderName: String? = null,
    val errorMessage: String? = null,
) {
    /**
     * A row is "owned by me" — and therefore eligible for the delete button —
     * when any of these match the current install:
     *   - the Supabase anon user id used to create the row
     *   - the stable device id captured at upload time
     *   - the GPG-signed-in display name captured at upload time
     */
    fun isOwnedByMe(row: ConfigRow): Boolean {
        if (myUserId != null && row.userId == myUserId) return true
        if (!myDeviceId.isNullOrBlank() && row.deviceId == myDeviceId) return true
        if (!myUploaderName.isNullOrBlank() &&
            myUploaderName != UploaderIdentity.ANONYMOUS_NAME &&
            row.uploaderName == myUploaderName
        ) {
            return true
        }
        return false
    }

    fun visibleRows(deviceModel: String, gpuModel: String): List<ConfigRow> {
        if (filter == ConfigFilter.ALL || allRows.isEmpty()) return allRows
        return when (filter) {
            ConfigFilter.MY_DEVICE -> allRows.filter { it.deviceModel.equals(deviceModel, ignoreCase = true) }
            ConfigFilter.MY_GPU -> filterByGpu(gpuModel)
            ConfigFilter.ALL -> allRows
        }
    }

    /**
     * Match strategy: exact model substring first ("Adreno 840" matches stored
     * "Adreno (TM) 840" or vice-versa). If nothing matches that, fall back to the
     * family token ("Adreno" / "Mali" / "Xclipse" / "Immortalis" / "PowerVR") so the
     * user still sees configs from broadly-similar GPUs instead of an empty list.
     */
    private fun filterByGpu(myGpu: String): List<ConfigRow> {
        if (myGpu.isBlank() || myGpu.equals("Unknown", ignoreCase = true)) return allRows
        val exact = allRows.filter { row ->
            val rg = row.gpuRenderer?.takeIf { it.isNotBlank() } ?: return@filter false
            rg.contains(myGpu, ignoreCase = true) || myGpu.contains(rg, ignoreCase = true)
        }
        if (exact.isNotEmpty()) return exact
        val family = GpuDetector.family(myGpu)
        if (family.isBlank()) return emptyList()
        return allRows.filter { it.gpuRenderer?.contains(family, ignoreCase = true) == true }
    }
}
