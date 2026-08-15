package com.winlator.cmod.feature.retro

import android.content.Context
import org.dolphinemu.dolphinemu.wn.DolphinEmulationActivity
import java.io.File

object DolphinCloudSync {
    private fun userDir(context: Context) = File(context.filesDir, "dolphin-embed/User")

    fun stagingDir(context: Context, cloudId: String): File =
        File(context.filesDir, "dolphin-embed/cloud/$cloudId")

    private fun pendingFile(context: Context) = File(context.filesDir, "dolphin-embed/.pending_cloud")

    fun stage(context: Context, stageKey: String, gameId: String?): Boolean =
        runCatching {
            val user = userDir(context)
            val staging = stagingDir(context, stageKey)
            staging.deleteRecursively()
            var any = false
            fun copy(f: File) {
                f.copyTo(File(staging, f.relativeTo(user).path).apply { parentFile?.mkdirs() }, overwrite = true)
                any = true
            }
            fun copyTree(root: File, accept: (File) -> Boolean) {
                if (!root.isDirectory) return
                root.walkTopDown().filter { it.isFile && accept(it) }.forEach(::copy)
            }
            copyTree(File(user, "GC")) { it.extension.equals("gci", true) || it.name.equals("SRAM.raw", true) }
            copyTree(File(user, "Wii/title")) { true }
            if (!gameId.isNullOrBlank()) {
                File(user, "StateSaves").listFiles()
                    ?.filter { it.isFile && it.name.startsWith(gameId) }
                    ?.forEach(::copy)
            }
            any
        }.getOrDefault(false)

    fun stagedFingerprint(context: Context, stageKey: String): String {
        val staging = stagingDir(context, stageKey)
        if (!staging.isDirectory) return ""
        val md = java.security.MessageDigest.getInstance("SHA-256")
        staging.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach { f ->
            md.update("${f.relativeTo(staging).path}:${f.length()}:${f.lastModified()}".toByteArray())
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun refreshForBackup(context: Context, systemId: String, gameName: String) {
        val system = RetroSystems.fromId(systemId) ?: return
        if (!RetroCoreManager.usesDolphinCore(system)) return
        val stageKey = RetroSaveStates.cloudGameId(system.id, gameName)
        val gameId =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .getString("retro_gc_gameid_$stageKey", "")
        stage(context, stageKey, gameId)
    }

    fun applyStaged(context: Context, cloudId: String) {
        runCatching {
            val user = userDir(context)
            val staging = stagingDir(context, cloudId)
            if (!staging.isDirectory) return
            staging.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(staging).path
                f.copyTo(File(user, rel).apply { parentFile?.mkdirs() }, overwrite = true)
            }
        }
    }

    fun localTimestamp(context: Context, cloudId: String): Long {
        val staging = stagingDir(context, cloudId)
        if (!staging.isDirectory) return 0L
        return staging.walkTopDown().filter { it.isFile }.maxOfOrNull { it.lastModified() } ?: 0L
    }

    fun writePending(context: Context, cloudId: String, gameName: String, fingerprint: String) {
        runCatching { pendingFile(context).writeText("$cloudId\n$gameName\n$fingerprint") }
    }

    fun stageAndQueue(activity: android.app.Activity) {
        val intent = activity.intent
        val systemId = intent.getStringExtra(DolphinEmulationActivity.EXTRA_SYSTEM_ID)
        val system = RetroSystems.fromId(systemId) ?: return
        val gameName = intent.getStringExtra(DolphinEmulationActivity.EXTRA_GAME_NAME) ?: return
        val shortcutPath = intent.getStringExtra(DolphinEmulationActivity.EXTRA_SHORTCUT_PATH) ?: return
        val enabled =
            runCatching {
                val cm = com.winlator.cmod.runtime.container.ContainerManager(activity)
                val cid = intent.getIntExtra(DolphinEmulationActivity.EXTRA_CONTAINER_ID, 0)
                val container =
                    if (cid == com.winlator.cmod.runtime.container.ContainerManager.RETRO_CONTAINER_ID) {
                        cm.retroContainer
                    } else {
                        cm.getContainerById(cid)
                    }
                container?.let { com.winlator.cmod.runtime.container.Shortcut(it, java.io.File(shortcutPath)) }
                    ?.getExtra("cloud_sync_enabled", "1") != "0"
            }.getOrDefault(true)
        if (!enabled) {
            android.util.Log.i("WnDolphin", "cloud stage skipped (sync disabled)")
            return
        }
        val stageKey = RetroSaveStates.cloudGameId(system.id, gameName)
        val cloudId =
            com.winlator.cmod.feature.sync.google.GameSaveBackupManager.customGameId(
                intent.getIntExtra(DolphinEmulationActivity.EXTRA_CONTAINER_ID, 0),
                java.io.File(shortcutPath).name,
            )
        val gameId = DolphinEmulationActivity.currentGameId
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
            .edit().putString("retro_gc_gameid_$stageKey", gameId).apply()
        if (!stage(activity, stageKey, gameId)) {
            android.util.Log.i("WnDolphin", "cloud stage found no save files (gameId=$gameId)")
            return
        }
        val fingerprint = stagedFingerprint(activity, stageKey)
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
        if (fingerprint == prefs.getString("retro_cloud_fp_$cloudId", null)) {
            android.util.Log.i("WnDolphin", "cloud stage unchanged, skip upload id=$cloudId")
            return
        }
        writePending(activity, cloudId, gameName, fingerprint)
        android.util.Log.i("WnDolphin", "cloud staged id=$cloudId gameId=$gameId")
    }

    data class Pending(val cloudId: String, val gameName: String, val fingerprint: String)

    fun peekPending(context: Context): Pending? =
        runCatching {
            val f = pendingFile(context)
            if (!f.isFile) return null
            val lines = f.readText().split('\n')
            if (lines.size >= 2 && lines[0].isNotBlank()) {
                Pending(lines[0], lines[1], lines.getOrElse(2) { "" })
            } else {
                null
            }
        }.getOrNull()

    fun clearPending(context: Context) {
        runCatching { pendingFile(context).delete() }
    }
}
