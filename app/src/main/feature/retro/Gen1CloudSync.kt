package com.winlator.cmod.feature.retro

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.winlator.cmod.runtime.container.Shortcut
import java.io.File

/**
 * Puts the 3D engine's saves into WinNative's cloud sync.
 *
 * The engine is not a libretro core, so none of its progress lives where the
 * cloud backup looks. A libretro Game Boy game keeps an .srm and its save
 * states in one directory per game, and that directory IS what gets uploaded.
 * The engine instead writes Lua save files into its own LOVE save directory,
 * shared by every Gen 1 game, and spread across two places:
 *
 *   saves/<version>/slot*.lua   the playthroughs themselves
 *   options.lua                 which slots exist, which is active, their names
 *
 * Both are needed. The slot registry is ONLY in options.lua -- the engine does
 * not rediscover slot files by listing the directory (see ensureVersionSlots in
 * src/core/SaveData.lua), so restoring the slot files alone would put the saves
 * back on disk and leave the game unable to see them.
 *
 * Since the backup wants one directory per game and the engine's layout is
 * neither one directory nor per-game, this stages a copy, exactly as the
 * Dolphin path does for the same reason. The staging directory is what the
 * cloud uploads and what a restore writes into; [stage] fills it before an
 * upload and [applyStaged] copies it back afterwards.
 */
object Gen1CloudSync {
    private const val TAG = "WnGen1Cloud"

    /** Records the fingerprint of a staging copy this class made itself. */
    private const val STAGE_MARKER = ".winnative-staged"

    /**
     * The engine's LOVE save directory. Fixed by LOVE from the game's identity
     * rather than chosen here; [Gen1EngineBridge] resolves the same path.
     */
    private fun saveRoot(context: Context): File =
        File(context.getExternalFilesDir(null), "save/pokemon-love2d")

    fun stagingDir(context: Context, cloudId: String): File =
        File(context.filesDir, "gen1-engine/cloud/$cloudId")

    /**
     * Which game version this shortcut is, remembered at launch.
     *
     * Working it out means hashing the ROM, and a backup runs with no ROM in
     * hand and no reason to pay for that. Recorded once when the game starts;
     * until then staging falls back to taking every version, which is
     * wasteful but never wrong.
     */
    private fun versionKey(cloudId: String) = "gen1_cloud_version_$cloudId"

    fun rememberVersion(context: Context, cloudId: String, version: String) {
        if (version.isBlank()) return
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putString(versionKey(cloudId), version).apply()
    }

    private fun rememberedVersion(context: Context, cloudId: String): String? =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString(versionKey(cloudId), null)
            ?.takeIf { it.isNotBlank() }

    /** The cloud id a retro shortcut backs up under. */
    fun cloudId(shortcut: Shortcut): String =
        com.winlator.cmod.feature.sync.google.GameSaveBackupManager.customGameId(
            shortcut.container.id,
            shortcut.file.name,
        )

    /**
     * Whether this shortcut's saves belong to the engine rather than to a
     * libretro core.
     *
     * The toggle decides it, not the system: a Game Boy shortcut with 3D off
     * is an ordinary libretro game whose saves are an .srm, and the same
     * shortcut with 3D on has its progress inside the engine instead. They are
     * genuinely different saves, so they sync from different places.
     */
    fun isEngineShortcut(shortcut: Shortcut?): Boolean =
        shortcut != null && Gen1EmbedLaunch.isEnabled(shortcut)

    /**
     * Copies the engine's saves for this game into its staging directory.
     * Returns true when there was anything to copy.
     *
     * Paths are kept relative to the LOVE save root, so [applyStaged] can put
     * them back without knowing what they are.
     */
    fun stage(context: Context, cloudId: String): Boolean =
        runCatching {
            val root = saveRoot(context)
            if (!root.isDirectory) return false
            val staging = stagingDir(context, cloudId)
            staging.deleteRecursively()

            var any = false
            fun copy(file: File) {
                if (!file.isFile) return
                val target = File(staging, file.relativeTo(root).path)
                target.parentFile?.mkdirs()
                file.copyTo(target, overwrite = true)
                any = true
            }

            // The registry and every engine setting. Global to the engine
            // rather than per game -- see the note on restore below.
            copy(File(root, "options.lua"))

            val version = rememberedVersion(context, cloudId)
            val slotRoots =
                if (version != null) {
                    listOf(File(root, "saves/$version"))
                } else {
                    // Version not yet known: take them all rather than guess.
                    File(root, "saves").listFiles()?.filter { it.isDirectory }.orEmpty()
                }
            slotRoots.forEach { dir ->
                dir.listFiles()?.forEach(::copy)
            }

            // Pre-slots saves, which the engine still reads when a version has
            // no slot registered.
            copy(File(root, if (version == null) "save.lua" else legacyName(version)))

            if (any) markStaged(context, cloudId)
            any
        }.onFailure { Log.w(TAG, "stage failed: ${it.message}") }.getOrDefault(false)

    /**
     * Records what this staging directory held when WE filled it.
     *
     * A restore writes into the same directory -- that is how the cloud hands
     * a save back -- and nothing else tells us it happened. So the fingerprint
     * of our own copy is written alongside it: if what is there later does not
     * match, the contents came from the cloud rather than from here, and they
     * still have to be copied into the engine's save directory to mean
     * anything. Without this, restoring from the Cloud Saves screen would
     * report success and change nothing the player can see.
     */
    private fun markerFile(context: Context, cloudId: String) =
        File(stagingDir(context, cloudId), STAGE_MARKER)

    private fun markStaged(context: Context, cloudId: String) {
        runCatching { markerFile(context, cloudId).writeText(stagedFingerprint(context, cloudId)) }
    }

    /** True when the staging directory holds something we did not put there. */
    fun hasRestoredContent(context: Context, cloudId: String): Boolean {
        val staging = stagingDir(context, cloudId)
        if (!staging.isDirectory) return false
        if (staging.walkTopDown().none { it.isFile && it.name != STAGE_MARKER }) return false
        val marker = markerFile(context, cloudId)
        val recorded = runCatching { marker.takeIf { it.isFile }?.readText() }.getOrNull()
        // No marker at all means the directory was created by a restore.
        return recorded != stagedFingerprint(context, cloudId)
    }

    /**
     * Copies a restored staging directory into the engine, but only when the
     * staging directory actually holds a restore. Called at launch so a
     * restore made from the Cloud Saves screen takes effect on the next start.
     */
    fun applyRestoreIfPending(context: Context, cloudId: String) {
        if (!hasRestoredContent(context, cloudId)) return
        applyStaged(context, cloudId)
        markStaged(context, cloudId)
    }

    /** The flat save name the engine uses for a version before slots exist. */
    private fun legacyName(version: String): String =
        if (version == "red") "save.lua" else "save_$version.lua"

    /**
     * Copies a restored staging directory back into the engine's save
     * directory.
     *
     * Note this includes options.lua, which the engine shares across every Gen
     * 1 game: restoring one game's cloud save also restores the engine
     * settings and the slot registries as they were when that backup was made.
     * That is the right answer for a single Gen 1 game, and it is what makes a
     * restore visible at all -- without the registry the engine cannot see the
     * slot files sitting next to it.
     */
    fun applyStaged(context: Context, cloudId: String) {
        runCatching {
            val staging = stagingDir(context, cloudId)
            if (!staging.isDirectory) return
            val root = saveRoot(context)
            root.mkdirs()
            staging.walkTopDown().filter { it.isFile }.forEach { file ->
                val target = File(root, file.relativeTo(staging).path)
                target.parentFile?.mkdirs()
                file.copyTo(target, overwrite = true)
            }
            Log.i(TAG, "restored engine saves for $cloudId")
        }.onFailure { Log.w(TAG, "applyStaged failed: ${it.message}") }
    }

    /**
     * Identifies the staged content, so an unchanged save is not uploaded
     * again on every exit.
     */
    fun stagedFingerprint(context: Context, cloudId: String): String {
        val staging = stagingDir(context, cloudId)
        if (!staging.isDirectory) return ""
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        // The marker is excluded, or recording the fingerprint inside the
        // directory would change the fingerprint it records.
        staging.walkTopDown()
            .filter { it.isFile && it.name != STAGE_MARKER }
            .sortedBy { it.path }
            .forEach { f -> digest.update("${f.relativeTo(staging).path}:${f.length()}".toByteArray()) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** How recent the local saves are, for the launch-time conflict check. */
    fun localTimestamp(context: Context, cloudId: String): Long {
        val root = saveRoot(context)
        if (!root.isDirectory) return 0L
        val version = rememberedVersion(context, cloudId)
        val dirs =
            if (version != null) {
                listOf(File(root, "saves/$version"))
            } else {
                File(root, "saves").listFiles()?.filter { it.isDirectory }.orEmpty()
            }
        return dirs.flatMap { it.listFiles()?.toList().orEmpty() }
            .filter { it.isFile }
            .maxOfOrNull { it.lastModified() } ?: 0L
    }

    /** Refreshes the staging copy just before a backup reads it. */
    fun refreshForBackup(context: Context, shortcut: Shortcut) {
        if (!isEngineShortcut(shortcut)) return
        stage(context, cloudId(shortcut))
    }

    /**
     * Queues an upload of this game's engine saves.
     *
     * Uses the same two preferences the libretro path uses, so the existing
     * uploader in UnifiedActivity picks this up with no change: it already
     * runs whatever is pending the next time the app is in the foreground and
     * Drive is connected.
     */
    fun queueBackup(context: Context, cloudId: String, gameName: String) {
        if (!stage(context, cloudId)) {
            Log.i(TAG, "nothing to back up for $cloudId")
            return
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val fingerprint = stagedFingerprint(context, cloudId)
        if (fingerprint.isNotEmpty() && fingerprint == prefs.getString("retro_cloud_fp_$cloudId", null)) {
            Log.i(TAG, "engine saves unchanged, skipping upload for $cloudId")
            return
        }
        prefs.edit()
            .putString("retro_pending_backup_id", cloudId)
            .putString("retro_pending_backup_name", gameName)
            .putString("retro_cloud_fp_$cloudId", fingerprint)
            .apply()
        Log.i(TAG, "queued engine save backup for $cloudId")
    }
}
