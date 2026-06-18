package com.winlator.cmod.feature.sync

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.winlator.cmod.R
import com.winlator.cmod.feature.stores.gog.service.GOGCloudSavesManager
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager
import com.winlator.cmod.feature.sync.google.GoogleAuthMode
import com.winlator.cmod.runtime.container.Shortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object GogLaunchCloudSync {
    fun interface StatusSink {
        fun show(text: String)
    }

    @JvmStatic
    fun syncBeforeLaunch(
        activity: Activity,
        shortcut: Shortcut?,
        cloudSyncEnabled: Boolean,
        statusSink: StatusSink,
    ) {
        if (shortcut == null) return
        if (shortcut.getExtra("game_source") != "GOG") return
        if (!cloudSyncEnabled || CloudSyncHelper.isOfflineMode(shortcut)) return

        Timber.tag("GogLaunchCloudSync").i("Checking GOG cloud saves before launch for ${shortcut.name}")
        CloudSyncHelper.forceDownloadOnContainerSwap(activity, shortcut)

        if (!CloudSyncHelper.hasLocalCloudSaves(activity, shortcut)) {
            Timber.tag("GogLaunchCloudSync").i("No local GOG cloud-save files found; downloading before launch")
            statusSink.show(activity.getString(R.string.preloader_downloading_cloud))
            CloudSyncHelper.downloadCloudSaves(activity, shortcut)
            statusSink.show(activity.getString(R.string.preloader_initializing))
            return
        }

        val pendingAction = CloudSyncHelper.getGogPendingSyncAction(activity, shortcut)
        Timber.tag("GogLaunchCloudSync").i("Pending GOG cloud action before launch: $pendingAction")
        when (pendingAction) {
            GOGCloudSavesManager.SyncAction.NONE -> return
            GOGCloudSavesManager.SyncAction.DOWNLOAD -> {
                statusSink.show(activity.getString(R.string.preloader_downloading_cloud))
                CloudSyncHelper.downloadCloudSaves(activity, shortcut)
                statusSink.show(activity.getString(R.string.preloader_initializing))
                return
            }
            GOGCloudSavesManager.SyncAction.UPLOAD -> {
                Timber.tag("GogLaunchCloudSync").i(
                    "Local GOG cloud saves are newer before launch; deferring upload until exit",
                )
                return
            }
            GOGCloudSavesManager.SyncAction.CONFLICT -> {
                // Fall through to the conflict dialog below.
            }
        }

        val dialogLatch = CountDownLatch(1)
        var useCloud = false
        var useLocal = false
        var keepBackup = false
        val timestamps = CloudSyncHelper.getGogConflictTimestamps(activity, shortcut)

        val lifecycle = (activity as? LifecycleOwner)?.lifecycle
        val cancelObserver =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) {
                    Timber.tag("GogLaunchCloudSync").w(
                        "Activity destroyed while GOG cloud-conflict dialog was up; releasing latch",
                    )
                    dialogLatch.countDown()
                }
            }

        activity.runOnUiThread {
            lifecycle?.addObserver(cancelObserver)
            GogCloudConflictDialog.show(
                activity = activity,
                timestamps = timestamps,
                onUseCloud = { keep ->
                    useCloud = true
                    keepBackup = keep
                    dialogLatch.countDown()
                },
                onUseLocal = { keep ->
                    useCloud = false
                    useLocal = true
                    keepBackup = keep
                    dialogLatch.countDown()
                },
            )
        }

        try {
            if (!dialogLatch.await(10, TimeUnit.MINUTES)) {
                Timber.tag("GogLaunchCloudSync").w(
                    "GOG cloud-conflict dialog timed out after 10 minutes; treating as 'keep local'",
                )
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            activity.runOnUiThread { lifecycle?.removeObserver(cancelObserver) }
            return
        }

        activity.runOnUiThread { lifecycle?.removeObserver(cancelObserver) }

        when {
            useCloud -> {
                if (keepBackup) {
                    // "Use Cloud" overwrites the local save — back it up first (M-2).
                    backupLocalSaveToGoogle(activity, shortcut)
                }
                statusSink.show(activity.getString(R.string.preloader_syncing_cloud))
                CloudSyncHelper.downloadCloudSaves(activity, shortcut)
                statusSink.show(activity.getString(R.string.preloader_initializing))
            }
            useLocal -> {
                if (keepBackup) {
                    // "Use Local" pushes the local save over the GOG cloud. GOG has no
                    // non-destructive way to capture the cloud copy (only Steam does), so honor the
                    // checkbox by backing up the local save to Google as a recovery point at this
                    // conflict — rather than silently doing nothing.
                    backupLocalSaveToGoogle(activity, shortcut)
                }
                statusSink.show(activity.getString(R.string.preloader_syncing_cloud))
                CloudSyncHelper.uploadCloudSaves(activity, shortcut)
                statusSink.show(activity.getString(R.string.preloader_initializing))
            }
        }
    }

    /**
     * Back up the current local GOG save before it is overwritten by a "Use Cloud" download.
     * GOG has no local snapshot backend, so this mirrors the save to Google Play Games (silent;
     * a no-op when not signed in). Best-effort — never blocks or throws into the launch path.
     */
    private fun backupLocalSaveToGoogle(
        activity: Activity,
        shortcut: Shortcut,
    ) {
        val gameId =
            shortcut.getExtra("gog_id").ifEmpty { shortcut.getExtra("app_id") }.takeIf { it.isNotEmpty() } ?: return
        val gameName = shortcut.name ?: "Unknown"
        try {
            val result =
                runBlocking(Dispatchers.IO) {
                    GameSaveBackupManager.backupSaveToGoogle(
                        activity = activity,
                        gameSource = GameSaveBackupManager.GameSource.GOG,
                        gameId = gameId,
                        gameName = gameName,
                        origin = GameSaveBackupManager.BackupOrigin.LOCAL,
                        authMode = GoogleAuthMode.RESUME,
                    )
                }
            Timber.tag("GogLaunchCloudSync").i("Pre-overwrite GOG local backup: %s", result.message)
        } catch (e: Exception) {
            Timber.tag("GogLaunchCloudSync").w(e, "Failed to back up GOG local save before Use-Cloud")
        }
    }
}
