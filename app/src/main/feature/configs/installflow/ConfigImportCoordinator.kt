package com.winlator.cmod.feature.configs.installflow

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager
import com.winlator.cmod.feature.configs.ConfigSerializer
import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.container.Shortcut
import com.winlator.cmod.runtime.content.ContentProfile
import com.winlator.cmod.runtime.content.ContentsManager
import com.winlator.cmod.runtime.content.Downloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import kotlin.coroutines.resume

/**
 * Drives the "Missing Components" import flow.
 *
 *  Idle
 *    → start(json, container, shortcut) →
 *  Analyzing
 *    → all requirements installed + no arch mismatch → Applying → Done
 *    → otherwise → ChoosingComponents
 *  ChoosingComponents
 *    → confirmDownload(): Downloading
 *    → applyAvailableOnly(): Applying (with missing-component keys stripped)
 *    → cancel(): Idle
 *  Downloading
 *    → every selected row → Done → Applying → Done | Failed
 *    → retry(rowId): Failed → Downloading
 *    → cancel(): scope cancelled, Idle
 *
 * Downloads run in parallel; install/extract is serialized via [installMutex]
 * because ContentsManager writes to a single shared tmp dir during extraction
 * and concurrent installs corrupt each other.
 *
 * Constructed with the application context. The Shortcut + Container references
 * are stored on the [Session] for the duration of the active import (Analyzing
 * → Downloading → Applying) so the apply step can reach them; they are cleared
 * on terminal completion (Done/Failed) and on [cancel]. Callers must invoke
 * [dispose] when the owning ViewModel/Activity is finished — otherwise an
 * in-flight install may continue running against a destroyed UI host.
 */
class ConfigImportCoordinator(private val appContext: Context) {
    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val installMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var session: Session? = null
    private var workJob: Job? = null

    /**
     * Begin the analysis + download + apply pipeline. No-op if not in [ImportState.Idle].
     * [onResult] is invoked exactly once when the flow reaches a terminal state (Done/Failed).
     */
    fun start(
        configJson: JSONObject,
        container: Container?,
        shortcut: Shortcut?,
        onResult: (String) -> Unit,
    ) {
        if (_state.value !is ImportState.Idle) {
            Timber.tag(TAG).w("start() ignored: state is ${_state.value::class.simpleName}")
            return
        }
        // Wrap the caller's onResult so internal invocations always marshal to
        // Main. This is the boundary between coordinator coroutines (Default/IO)
        // and the UI side — without it, a caller that touches Toast/View/etc. from
        // their lambda crashes with "Can't toast on a thread that has not called
        // Looper.prepare()".
        //
        // The runCatching is defense-in-depth: Handler.post propagates exceptions
        // to the Looper's uncaught-exception handler, which crashes the process on
        // Android. Without the catch, an OEM-quirk Toast crash inside the caller
        // lambda would upgrade from "swallowed by coordinator catch" (pre-fix) to
        // "hard process crash" (post-fix). Better to log and drop.
        val mainSafeOnResult: (String) -> Unit = { msg ->
            mainHandler.post {
                runCatching { onResult(msg) }
                    .onFailure { Timber.tag(TAG).w(it, "onResult callback threw") }
            }
        }
        if (container == null || shortcut == null) {
            terminalFailure("No shortcut or container to import into.", mainSafeOnResult)
            return
        }
        session = Session(configJson, container, shortcut, mainSafeOnResult)
        _state.value = ImportState.Analyzing
        workJob = scope.launch { runAnalysis() }
    }

    /** Toggle a row's checkbox while in ChoosingComponents. */
    fun toggleSelection(requirementId: String) {
        val current = _state.value as? ImportState.ChoosingComponents ?: return
        val entry = current.entries.firstOrNull { it.requirement.id == requirementId } ?: return
        if (entry.resolution !is RequirementResolution.Available) return // unavailable rows aren't selectable
        val nextSelected =
            if (requirementId in current.selectedIds) current.selectedIds - requirementId
            else current.selectedIds + requirementId
        _state.value = current.copy(selectedIds = nextSelected)
    }

    /** User tapped Download. Transition to Downloading and queue the selected rows. */
    fun confirmDownload() {
        val current = _state.value as? ImportState.ChoosingComponents ?: return
        val selectedEntries = current.entries.filter {
            it.requirement.id in current.selectedIds && it.resolution is RequirementResolution.Available
        }
        if (selectedEntries.isEmpty()) {
            // Nothing selected → apply available only
            applyAvailableOnly()
            return
        }
        val rowStates = selectedEntries.associate { it.requirement.id to (RowState.Queued as RowState) }
        _state.value = ImportState.Downloading(current.entries, rowStates, current.archMismatch)
        workJob = scope.launch { runDownloads(selectedEntries) }
    }

    /** User tapped "Apply available only" — strip keys for missing components and apply. */
    fun applyAvailableOnly() {
        val current = _state.value
        val entries = when (current) {
            is ImportState.ChoosingComponents -> current.entries
            is ImportState.Downloading -> current.entries
            else -> return
        }
        val keysToStrip = entries
            .filter { it.resolution !is RequirementResolution.Installed }
            .flatMap { it.requirement.keysGuarded }
            .toSet()
        _state.value = ImportState.Applying
        workJob = scope.launch { applyConfig(keysToStrip) }
    }

    /** Retry a failed download row. */
    fun retry(requirementId: String) {
        val current = _state.value as? ImportState.Downloading ?: return
        val entry = current.entries.firstOrNull { it.requirement.id == requirementId } ?: return
        if (current.rowStates[requirementId] !is RowState.Failed) return
        updateRow(requirementId, RowState.Queued)
        workJob = scope.launch { runDownloads(listOf(entry), reusePreviousStates = true) }
    }

    /**
     * Back-press / dismiss. Aborts in-flight work and resets to Idle. The
     * "Import cancelled." callback only fires when the flow was actually mid-run
     * — if we're already terminal (Done / Failed), onResult has already been
     * delivered and we don't double-toast.
     */
    fun cancel() {
        val terminalAlready = _state.value is ImportState.Done || _state.value is ImportState.Failed
        scope.coroutineContext.cancelChildren()
        if (!terminalAlready) session?.onResult?.invoke("Import cancelled.")
        session = null
        _state.value = ImportState.Idle
    }

    /** Caller must invoke when the parent UI is permanently gone (ViewModel.onCleared). */
    fun dispose() {
        scope.cancel()
        session = null
    }

    // -------------------------------------------------------------------------
    // Pipeline implementations
    // -------------------------------------------------------------------------

    private suspend fun runAnalysis() {
        val s = session ?: return
        try {
            val manager = ContentsManager(appContext)
            val catalogJson = withContext(Dispatchers.IO) { fetchCatalogJson() }
            if (catalogJson == null) {
                // Couldn't reach the remote catalog. Don't silently mark everything
                // "Unavailable" — that misrepresents the failure as "nothing exists
                // in the catalog" rather than "we couldn't read the catalog".
                terminalFailure(
                    "Couldn't reach the components catalog. Check your connection and try again.",
                    s.onResult,
                )
                return
            }
            withContext(Dispatchers.IO) { manager.setRemoteProfiles(catalogJson) }
            withContext(Dispatchers.IO) { manager.syncContents() }

            val entries = ConfigImportDetector.detect(appContext, s.configJson, manager)
            val arch = computeArchMismatch(s.configJson, s.container)

            // Only Available rows are user-actionable — they have download buttons.
            // Unsupported rows (graphics drivers) are informational; Unavailable
            // rows are catalog misses the user should at least *see* before apply.
            val anyAvailable = entries.any { it.resolution is RequirementResolution.Available }
            val anyUnavailable = entries.any { it.resolution is RequirementResolution.Unavailable }
            val needsUserDecision = anyAvailable || anyUnavailable || arch != null

            // Collect any substitutions surfaced by the detector so applyConfig
            // can rewrite the JSON to point at the substitute that's actually
            // installed (or about to be). Without this, an old nightly token in
            // the config gets written verbatim to the shortcut and the launcher
            // can't resolve it.
            val substitutions = entries.mapNotNull { e ->
                val r = e.resolution
                if (r is RequirementResolution.Available && r.substituteFor != null) {
                    e.requirement.id to r.profile
                } else null
            }.toMap()
            session = s.copy(manager = manager, substitutionMap = substitutions)

            if (!needsUserDecision) {
                // Everything is either Installed or Unsupported (drivers). There's
                // nothing the user can do from a dialog, so just apply directly. The
                // result message will mention any Unsupported-driver notes via the
                // serializer's warning list.
                _state.value = ImportState.Applying
                applyConfig(emptySet())
                return
            }

            val defaultSelected = entries
                .filter { it.resolution is RequirementResolution.Available }
                .map { it.requirement.id }
                .toSet()
            _state.value = ImportState.ChoosingComponents(entries, defaultSelected, arch)
        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "runAnalysis failed")
            terminalFailure("Analysis failed: ${t.message ?: t::class.simpleName}", s.onResult)
        }
    }

    private suspend fun runDownloads(
        entries: List<RequirementEntry>,
        reusePreviousStates: Boolean = false,
    ) {
        val s = session ?: return
        val manager = s.manager ?: run {
            terminalFailure("Components manager not initialized.", s.onResult)
            return
        }

        try {
            coroutineScope {
                entries.map { entry ->
                    async(Dispatchers.IO) {
                        val req = entry.requirement
                        val profile = (entry.resolution as? RequirementResolution.Available)?.profile
                            ?: run {
                                updateRow(req.id, RowState.Failed("Not available"))
                                return@async
                            }
                        downloadAndInstall(manager, req, profile)
                    }
                }.awaitAll()
            }
        } catch (t: kotlinx.coroutines.CancellationException) {
            throw t
        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "runDownloads crashed")
        }

        // Decide whether to proceed to apply: only if every queued row across the
        // current state is Done. compareAndSet guards against the retry race —
        // two concurrent runDownloads tails can both observe allOk and try to
        // advance, but only one wins the CAS into Applying.
        val current = _state.value as? ImportState.Downloading ?: return
        val allOk = current.rowStates.values.all { it is RowState.Done }
        if (allOk && _state.compareAndSet(current, ImportState.Applying)) {
            applyConfig(emptySet())
        }
        // Else stay in Downloading; user can Retry failed rows or apply-available-only.
    }

    private suspend fun downloadAndInstall(
        manager: ContentsManager,
        req: ComponentRequirement,
        profile: ContentProfile,
    ) {
        val remoteUrl = profile.remoteUrl ?: run {
            updateRow(req.id, RowState.Failed("No download URL"))
            return
        }
        // --- Download phase (parallel-safe; writes to a unique tmp file) ----
        updateRow(req.id, RowState.Downloading(null))
        val tmp = File(appContext.cacheDir, "cfgimport_${System.currentTimeMillis()}_${req.id}")
        val downloadOk = runCatching {
            Downloader.downloadFileWinNativeFirst(remoteUrl, tmp) { downloaded, total ->
                val frac = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
                updateRow(req.id, RowState.Downloading(frac))
            }
        }.getOrDefault(false)

        if (!downloadOk) {
            runCatching { tmp.delete() }
            updateRow(req.id, RowState.Failed("Download failed"))
            return
        }

        // --- Install phase (must be serialized — shared tmp dir in ContentsManager) ----
        updateRow(req.id, RowState.Installing)
        installMutex.withLock {
            val installed = runCatching { runInstall(manager, tmp, profile) }
                .getOrElse {
                    Timber.tag(TAG).w(it, "Install failed for ${req.id}")
                    null
                }
            runCatching { tmp.delete() }
            if (installed != null) {
                withContext(Dispatchers.IO) { manager.syncContents() }
                updateRow(req.id, RowState.Done)
            } else {
                updateRow(req.id, RowState.Failed("Install failed"))
            }
        }
    }

    /**
     * Wraps the callback-based ContentsManager install pipeline in a suspend call.
     * The pipeline fires `onSucceed` twice — once after extraction, once after
     * `finishInstallContent`. We need to chain into `finishInstallContent` from the
     * first call, then resume on the second.
     */
    private suspend fun runInstall(
        manager: ContentsManager,
        archive: File,
        @Suppress("UNUSED_PARAMETER") expected: ContentProfile,
    ): ContentProfile? = suspendCancellableCoroutine { cont ->
        var extractedProfile: ContentProfile? = null
        val cb = object : ContentsManager.OnInstallFinishedCallback {
            override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
                Timber.tag(TAG).w(e, "install failed: $reason")
                if (cont.isActive) cont.resume(null)
            }

            override fun onSucceed(profile: ContentProfile) {
                if (extractedProfile == null) {
                    extractedProfile = profile
                    runCatching { manager.finishInstallContent(profile, this) }
                        .onFailure { if (cont.isActive) cont.resume(null) }
                } else {
                    if (cont.isActive) cont.resume(profile)
                }
            }
        }
        runCatching {
            manager.extraContentFile(Uri.parse(archive.absolutePath), cb, null)
        }.onFailure { ex ->
            Timber.tag(TAG).w(ex, "extraContentFile threw")
            if (cont.isActive) cont.resume(null)
        }
    }

    private suspend fun applyConfig(keysToStrip: Set<ComponentRequirement.KeyGuard>) {
        val s = session ?: return
        try {
            // Two transformations on the config JSON before writing it:
            //  1. Strip keys for components the user opted not to install.
            //  2. Rewrite the wineVersion / dxwrapperConfig.version /
            //     dxwrapperConfig.vkd3dVersion / box64Version / fexcoreVersion
            //     tokens to the substituted profile's identifier when the catalog
            //     fell back to a sibling (e.g. nightly → newer nightly). Without
            //     this rewrite the apply path writes the original token verbatim
            //     and the launcher fails at game start because the literal version
            //     it asks for isn't installed.
            val rewritten = if (s.substitutionMap.isEmpty()) s.configJson
            else applySubstitutions(s.configJson, s.substitutionMap)
            val filtered = if (keysToStrip.isEmpty()) rewritten
            else stripKeys(rewritten, keysToStrip)
            val applyResult = withContext(Dispatchers.IO) {
                val r = ConfigSerializer.applyToShortcut(filtered, s.container, s.shortcut)
                s.shortcut.saveData()
                r
            }
            val msgPrefix = if (keysToStrip.isEmpty()) "Config imported"
            else "Config imported with ${keysToStrip.size} key(s) skipped"
            val warningsTail = applyResult.warnings.take(3).joinToString("\n")
            val message = if (warningsTail.isBlank()) "$msgPrefix into ${s.shortcut.name}."
            else "$msgPrefix:\n$warningsTail"
            _state.value = ImportState.Done(message)
            s.onResult(message)
            session = null
        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "applyConfig failed")
            terminalFailure("Could not apply config: ${t.message ?: t::class.simpleName}", s.onResult)
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun computeArchMismatch(json: JSONObject, container: Container): ArchMismatch? {
        val ident = json.optJSONObject("container")?.optString("wineVersion").orEmpty()
        if (ident.isBlank()) return null
        val wantedArch = WINE_ARCH_REGEX.find(ident)?.groupValues?.get(1) ?: return null
        val containerArch = container.getExtra("wineprefixArch")?.takeIf { it.isNotEmpty() } ?: "x86_64"
        return if (wantedArch.equals(containerArch, ignoreCase = true)) null
        else ArchMismatch(expectedArch = wantedArch, containerArch = containerArch)
    }

    /**
     * Rewrite the version tokens in the config JSON to point at the substituted
     * profile that's actually installed. Operates on a deep copy of the input.
     *
     * - `wine` ⇒ `container.wineVersion`: full entry name via `getEntryName`
     *   (e.g. `Proton-9.0-arm64ec-2`) — the launcher's `WineInfo.fromIdentifier`
     *   parses both the canonical 4-part form and the regex-shaped legacy form.
     * - `dxvk` ⇒ inside `container.dxwrapperConfig`, replace `version=…` with
     *   `verName-verCode` of the substitute (the dropdown format).
     * - `vkd3d` ⇒ inside `container.dxwrapperConfig`, replace `vkd3dVersion=…`.
     * - `box64` / `fexcore` ⇒ `container.box64Version` / `fexcoreVersion`,
     *   preserving the original shape: if the source carried a verCode suffix,
     *   write `verName-verCode`; otherwise write just `verName`.
     */
    private fun applySubstitutions(
        src: JSONObject,
        substitutions: Map<String, com.winlator.cmod.runtime.content.ContentProfile>,
    ): JSONObject {
        val out = JSONObject(src.toString())
        val container = out.optJSONObject("container") ?: return out
        substitutions.forEach { (requirementId, profile) ->
            when (requirementId) {
                "wine" -> {
                    val newId = com.winlator.cmod.runtime.content.ContentsManager.getEntryName(profile)
                    container.put("wineVersion", newId)
                }
                "dxvk" -> {
                    val newToken = "${profile.verName}-${profile.verCode}"
                    val cfg = container.optString("dxwrapperConfig", "")
                    if (cfg.isNotEmpty()) container.put("dxwrapperConfig", rewriteCsv(cfg, "version", newToken))
                }
                "vkd3d" -> {
                    val newToken = "${profile.verName}-${profile.verCode}"
                    val cfg = container.optString("dxwrapperConfig", "")
                    if (cfg.isNotEmpty()) container.put("dxwrapperConfig", rewriteCsv(cfg, "vkd3dVersion", newToken))
                }
                "box64" -> {
                    val origin = container.optString("box64Version", "")
                    container.put("box64Version", preserveShape(origin, profile))
                }
                "fexcore" -> {
                    val origin = container.optString("fexcoreVersion", "")
                    container.put("fexcoreVersion", preserveShape(origin, profile))
                }
                else -> Unit // graphics-driver substitutions don't go through here
            }
        }
        return out
    }

    /** Returns `verName-verCode` if [origin] ends with `-<digits>`, else just `verName`. */
    private fun preserveShape(
        origin: String,
        profile: com.winlator.cmod.runtime.content.ContentProfile,
    ): String =
        if (Regex("-\\d+$").containsMatchIn(origin)) "${profile.verName}-${profile.verCode}"
        else profile.verName

    /** Replace `key=…` in a comma-delimited key=value string, leaving other pairs intact. */
    private fun rewriteCsv(csv: String, key: String, newValue: String): String {
        val parts = csv.split(",")
        var found = false
        val mutated = parts.map { part ->
            val eq = part.indexOf('=')
            if (eq > 0 && part.substring(0, eq).equals(key, ignoreCase = true)) {
                found = true
                "$key=$newValue"
            } else part
        }.toMutableList()
        if (!found) mutated += "$key=$newValue"
        return mutated.joinToString(",")
    }

    private fun stripKeys(src: JSONObject, guards: Set<ComponentRequirement.KeyGuard>): JSONObject {
        val out = JSONObject(src.toString()) // deep copy
        guards.forEach { g ->
            when (g.block) {
                ComponentRequirement.KeyGuard.Block.CONTAINER ->
                    out.optJSONObject("container")?.remove(g.key)
                ComponentRequirement.KeyGuard.Block.CONTAINER_EXTRAS ->
                    out.optJSONObject("containerExtras")?.remove(g.key)
                ComponentRequirement.KeyGuard.Block.SHORTCUT_EXTRAS ->
                    out.optJSONObject("shortcutExtras")?.remove(g.key)
                ComponentRequirement.KeyGuard.Block.DXWRAPPER_CONFIG_VKD3D -> {
                    // Strip just the vkd3dVersion key from the comma-delimited string.
                    val container = out.optJSONObject("container") ?: return@forEach
                    val cfg = container.optString("dxwrapperConfig", "")
                    if (cfg.isNotBlank()) {
                        val rebuilt = cfg.split(",")
                            .filterNot { it.startsWith("vkd3dVersion=", ignoreCase = true) }
                            .joinToString(",")
                        container.put("dxwrapperConfig", rebuilt)
                    }
                }
            }
        }
        return out
    }

    private fun updateRow(id: String, next: RowState) {
        _state.update { cur ->
            if (cur !is ImportState.Downloading) cur
            else cur.copy(rowStates = cur.rowStates + (id to next))
        }
    }

    private fun terminalFailure(reason: String, onResult: (String) -> Unit) {
        _state.value = ImportState.Failed(reason)
        onResult(reason)
        session = null
    }

    private fun fetchCatalogJson(): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        val url = prefs.getString("downloadable_contents_url", ContentsManager.REMOTE_PROFILES)
            ?: ContentsManager.REMOTE_PROFILES
        return runCatching { Downloader.downloadString(url) }.getOrNull()
    }

    private data class Session(
        val configJson: JSONObject,
        val container: Container,
        val shortcut: Shortcut,
        val onResult: (String) -> Unit,
        val manager: ContentsManager? = null,
        /**
         * Per-requirement substitution map: when the catalog had no exact match
         * and the resolver fell back to a sibling (e.g. nightly→newer nightly),
         * we record the substitute profile here so [applyConfig] can rewrite the
         * config JSON to point at the version that's actually installed on disk.
         */
        val substitutionMap: Map<String, com.winlator.cmod.runtime.content.ContentProfile> = emptyMap(),
    )

    companion object {
        private const val TAG = "ConfigImportCoordinator"
        private val WINE_ARCH_REGEX = Regex("(?i)-(x86_64|arm64ec|x86)\\s*$")
    }
}
