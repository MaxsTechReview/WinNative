package com.winlator.cmod.feature.retro

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.winlator.cmod.runtime.container.Shortcut
import java.io.File

object Gen1CloudSync {
    private const val TAG = "WnGen1Cloud"

    private const val STAGE_MARKER = ".winnative-staged"

    private fun saveRoot(context: Context): File =
        File(context.getExternalFilesDir(null), "save/pokemon-love2d")

    fun stagingDir(context: Context, cloudId: String): File =
        File(context.filesDir, "gen1-engine/cloud/$cloudId")

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

    fun cloudId(shortcut: Shortcut): String =
        com.winlator.cmod.feature.sync.google.GameSaveBackupManager.engineGameId(
            com.winlator.cmod.feature.sync.google.GameSaveBackupManager.customGameId(
                shortcut.container.id,
                shortcut.file.name,
            ),
        )

    fun isEngineShortcut(shortcut: Shortcut?): Boolean =
        shortcut != null && Gen1EmbedLaunch.isEnabled(shortcut)

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

            copy(File(root, "options.lua"))

            val version = rememberedVersion(context, cloudId)
            val slotRoots =
                if (version != null) {
                    listOf(File(root, "saves/$version"))
                } else {
                    File(root, "saves").listFiles()?.filter { it.isDirectory }.orEmpty()
                }
            slotRoots.forEach { dir ->
                dir.listFiles()?.forEach(::copy)
            }

            copy(File(root, if (version == null) "save.lua" else legacyName(version)))

            if (any) markStaged(context, cloudId)
            any
        }.onFailure { Log.w(TAG, "stage failed: ${it.message}") }.getOrDefault(false)

    private fun markerFile(context: Context, cloudId: String) =
        File(stagingDir(context, cloudId), STAGE_MARKER)

    private fun markStaged(context: Context, cloudId: String) {
        runCatching { markerFile(context, cloudId).writeText(stagedFingerprint(context, cloudId)) }
    }

    fun hasRestoredContent(context: Context, cloudId: String): Boolean {
        val staging = stagingDir(context, cloudId)
        if (!staging.isDirectory) return false
        if (staging.walkTopDown().none { it.isFile && it.name != STAGE_MARKER }) return false
        val marker = markerFile(context, cloudId)
        val recorded = runCatching { marker.takeIf { it.isFile }?.readText() }.getOrNull()
        return recorded != stagedFingerprint(context, cloudId)
    }

    fun applyRestoreIfPending(context: Context, cloudId: String) {
        if (!hasRestoredContent(context, cloudId)) return
        applyStaged(context, cloudId)
        markStaged(context, cloudId)
    }

    private fun legacyName(version: String): String =
        if (version == "red") "save.lua" else "save_$version.lua"

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

    fun stagedFingerprint(context: Context, cloudId: String): String {
        val staging = stagingDir(context, cloudId)
        if (!staging.isDirectory) return ""
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        staging.walkTopDown()
            .filter { it.isFile && it.name != STAGE_MARKER }
            .sortedBy { it.path }
            .forEach { f -> digest.update("${f.relativeTo(staging).path}:${f.length()}".toByteArray()) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

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

    fun refreshForBackup(context: Context, shortcut: Shortcut) {
        if (!isEngineShortcut(shortcut)) return
        stage(context, cloudId(shortcut))
    }

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
