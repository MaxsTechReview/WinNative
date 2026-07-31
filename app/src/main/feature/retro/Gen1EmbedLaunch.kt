package com.winlator.cmod.feature.retro

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.winlator.cmod.runtime.container.Shortcut
import java.io.File
import java.security.MessageDigest

/**
 * Launches a compatible Game Boy title into the 3D engine instead of the
 * libretro core, mirroring DolphinEmbedLaunch: WinNative resolves the settings
 * and hands them to the hosting activity, so the engine never shows a UI of its
 * own.
 *
 * Compatibility is decided by the ROM's SHA-1 rather than its filename, because
 * that is what the engine itself verifies on import -- a renamed or hacked dump
 * that would be rejected there must not be offered the toggle here.
 */
object Gen1EmbedLaunch {
    /** Per-game extra: "1" launches into the 3D engine. */
    const val KEY_ENGINE_3D = "retro_engine_3d"

    /** Mod id of the voxel renderer, as it appears in the engine's options. */
    const val VOXEL_MOD_ID = "DRAMATIC_SHAPE"

    private val COMPATIBLE = mapOf(
        "ea9bcae617fdf159b045185467ae58b2e4a48b9a" to "red",
        "d7037c83e1ae5b39bde3c30787637ba1d4c48ce2" to "blue",
        "cc7d03262ebfaf2f06772c1a480c7d9d5f4a38e1" to "yellow",
    )

    /**
     * The engine's version id for this ROM, or null when the ROM is not one of
     * the three the engine accepts. Hashing a 1 MiB file is cheap, but this is
     * still called off the UI thread by its callers.
     */
    fun versionForRom(rom: File): String? {
        if (!rom.isFile || rom.length() != 1024L * 1024L) return null
        val digest = MessageDigest.getInstance("SHA-1")
        rom.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        val sha1 = digest.digest().joinToString("") { "%02x".format(it) }
        return COMPATIBLE[sha1]
    }

    /** Whether this shortcut is a game the 3D engine can run at all. */
    fun isCompatible(context: Context, shortcut: Shortcut): Boolean =
        Gen1EngineActivity.isInstalled(context) &&
            versionForRom(File(RetroShortcuts.romPath(shortcut))) != null

    /** Whether the user has actually turned the 3D toggle on for this game. */
    fun isEnabled(shortcut: Shortcut): Boolean =
        shortcut.getExtra(KEY_ENGINE_3D) == "1"

    fun shouldLaunch(context: Context, shortcut: Shortcut): Boolean =
        isEnabled(shortcut) && isCompatible(context, shortcut)

    fun launch(context: Context, shortcut: Shortcut) {
        val intent = launchIntent(context, shortcut) ?: return
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * The Intent for this shortcut, or null if the 3D engine cannot run it.
     *
     * Deciding and building are one step on purpose. Both need the ROM's
     * version, and working that out means hashing the file -- so a caller that
     * asked [shouldLaunch] first and then built the Intent hashed the same
     * megabyte twice, and the second time was usually on the main thread. Call
     * this from a background thread and start the Intent it returns.
     */
    fun launchIntentIfSupported(context: Context, shortcut: Shortcut): Intent? {
        if (!Gen1EngineActivity.isInstalled(context)) return null
        val intent = launchIntent(context, shortcut) ?: return null
        // After the Intent, because building it is what resolves the version,
        // and before the Intent is started, because the engine reads its saves
        // as it boots.
        prepareLaunch(context, shortcut, intent.getStringExtra(Gen1EngineActivity.EXTRA_VERSION).orEmpty())
        return intent
    }

    /**
     * Brings this game's engine saves down from the cloud before it starts, if
     * the cloud has something newer.
     *
     * The same shape as the Dolphin path: a restore writes into the staging
     * directory, which is then copied into the engine's own save directory --
     * it has to happen before the engine boots, because the engine reads its
     * slot registry once at startup.
     *
     * Runs on the caller's thread and blocks; call it from the launch worker,
     * not the main thread.
     */
    private fun syncCloudSaves(context: Context, shortcut: Shortcut) {
        if (context !is android.app.Activity) return
        if (shortcut.getExtra("cloud_sync_enabled", "1") == "0") return
        val gameName = shortcut.getExtra("custom_name", shortcut.name)
        val cloudId = Gen1CloudSync.cloudId(shortcut)
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        runCatching {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeout(12_000L) {
                    val entries =
                        com.winlator.cmod.feature.sync.google.GameSaveBackupManager.listGoogleHistory(
                            context,
                            com.winlator.cmod.feature.sync.google.GameSaveBackupManager.GameSource.CUSTOM,
                            cloudId,
                            com.winlator.cmod.feature.sync.google.GoogleAuthMode.RESUME,
                        )
                    val latest = entries.maxByOrNull { it.timestampMs } ?: return@withTimeout
                    val localTs = Gen1CloudSync.localTimestamp(context, cloudId)
                    val mark = prefs.getLong("retro_cloud_mark_$cloudId", 0L)
                    val restore: suspend () -> Unit = {
                        val result =
                            com.winlator.cmod.feature.sync.google.GameSaveBackupManager.restoreFromGoogle(
                                context,
                                latest,
                                com.winlator.cmod.feature.sync.google.GameSaveBackupManager.GameSource.CUSTOM,
                                cloudId,
                                com.winlator.cmod.feature.sync.google.GoogleAuthMode.RESUME,
                                customSaveDir = Gen1CloudSync.stagingDir(context, cloudId),
                            )
                        if (result.success) {
                            Gen1CloudSync.applyStaged(context, cloudId)
                            prefs.edit().putLong("retro_cloud_mark_$cloudId", latest.timestampMs).apply()
                        }
                    }
                    if (localTs == 0L) {
                        // Nothing here yet: a new device, or this game's first
                        // run. Take the cloud copy without asking.
                        restore()
                    } else if (latest.timestampMs > localTs + 120_000L && latest.timestampMs > mark) {
                        if (askCloudConflict(context, gameName)) {
                            restore()
                        } else {
                            // Remembered so the same choice is not asked again
                            // for the same cloud save.
                            prefs.edit().putLong("retro_cloud_mark_$cloudId", latest.timestampMs).apply()
                        }
                    }
                }
            }
        }
    }

    private fun askCloudConflict(activity: android.app.Activity, gameName: String): Boolean {
        val latch = java.util.concurrent.CountDownLatch(1)
        val useCloud = java.util.concurrent.atomic.AtomicBoolean(false)
        activity.runOnUiThread {
            androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle(activity.getString(com.winlator.cmod.R.string.retro_lr_cloud_save))
                .setMessage(
                    activity.getString(com.winlator.cmod.R.string.retro_lr_cloud_conflict_message, gameName),
                )
                .setCancelable(false)
                .setPositiveButton(activity.getString(com.winlator.cmod.R.string.retro_lr_use_cloud_save)) { _, _ ->
                    useCloud.set(true)
                    latch.countDown()
                }
                .setNegativeButton(activity.getString(com.winlator.cmod.R.string.retro_scr_keep_local_save)) { _, _ ->
                    latch.countDown()
                }
                .show()
        }
        latch.await()
        return useCloud.get()
    }

    /**
     * Everything that must happen before the engine starts: pull a newer cloud
     * save down, and record which game version this shortcut is so a later
     * backup does not have to hash the ROM again to find out.
     */
    fun prepareLaunch(context: Context, shortcut: Shortcut, version: String) {
        val cloudId = Gen1CloudSync.cloudId(shortcut)
        Gen1CloudSync.rememberVersion(context, cloudId, version)
        // A restore made from the Cloud Saves screen landed in the staging
        // directory and has been waiting for a launch to be copied in.
        Gen1CloudSync.applyRestoreIfPending(context, cloudId)
        syncCloudSaves(context, shortcut)
    }

    fun launchIntent(context: Context, shortcut: Shortcut): Intent? {
        val rom = File(RetroShortcuts.romPath(shortcut))
        val version = versionForRom(rom) ?: return null

        return Intent(context, Gen1EngineActivity::class.java).apply {
            // GameActivity takes its game path from the Intent data when the
            // embed resource is false, which is how the engine archive can live
            // in the retro bundle and still be found.
            data = Uri.fromFile(Gen1EngineActivity.gameArchive(context))
            putExtra(Gen1EngineActivity.EXTRA_ROM_PATH, rom.absolutePath)
            putExtra(Gen1EngineActivity.EXTRA_VERSION, version)
            putExtra(
                Gen1EngineActivity.EXTRA_GAME_NAME,
                shortcut.getExtra("custom_name", shortcut.name),
            )
            putExtra(Gen1EngineActivity.EXTRA_SHORTCUT_PATH, shortcut.file.absolutePath)
            // The loading screen shown during a first-boot ROM import uses the
            // game's own artwork, so the player sees the game they picked
            // rather than the engine's splash.
            putExtra(
                Gen1EngineActivity.EXTRA_ARTWORK_PATH,
                shortcut.getExtra("customCoverArtPath"),
            )
        }
    }
}
