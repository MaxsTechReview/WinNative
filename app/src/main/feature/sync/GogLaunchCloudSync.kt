package com.winlator.cmod.feature.sync

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.winlator.cmod.R
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

        CloudSyncHelper.forceDownloadOnContainerSwap(activity, shortcut)

        if (!CloudSyncHelper.hasLocalCloudSaves(activity, shortcut)) {
            statusSink.show(activity.getString(R.string.preloader_downloading_cloud))
            CloudSyncHelper.downloadCloudSaves(activity, shortcut)
            statusSink.show(activity.getString(R.string.preloader_initializing))
            return
        }

        if (!CloudSyncHelper.cloudSavesDiffer(activity, shortcut)) return

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
                onUseLocal = {
                    useCloud = false
                    useLocal = true
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
                    backupDiscardedSave(activity, shortcut, GameSaveBackupManager.BackupOrigin.LOCAL)
                }
                statusSink.show(activity.getString(R.string.preloader_syncing_cloud))
                CloudSyncHelper.downloadCloudSaves(activity, shortcut)
                statusSink.show(activity.getString(R.string.preloader_initializing))
            }
            useLocal -> {
                statusSink.show(activity.getString(R.string.preloader_syncing_cloud))
                CloudSyncHelper.uploadCloudSaves(activity, shortcut)
                statusSink.show(activity.getString(R.string.preloader_initializing))
            }
        }
    }

    private fun backupDiscardedSave(
        activity: Activity,
        shortcut: Shortcut,
        origin: GameSaveBackupManager.BackupOrigin,
    ) {
        val gameId = shortcut.getExtra("gog_id").ifEmpty { shortcut.getExtra("app_id") }.takeIf { it.isNotEmpty() } ?: return
        val gameName = shortcut.name ?: "Unknown"
        try {
            val result =
                runBlocking(Dispatchers.IO) {
                    GameSaveBackupManager.backupDiscardedSave(
                        activity = activity,
                        gameSource = GameSaveBackupManager.GameSource.GOG,
                        gameId = gameId,
                        gameName = gameName,
                        origin = origin,
                        authMode = GoogleAuthMode.RESUME,
                    )
                }
            Timber.tag("GogLaunchCloudSync").i("Discarded GOG save backup: %s", result.message)
        } catch (e: Exception) {
            Timber.tag("GogLaunchCloudSync").w(e, "Failed to back up discarded GOG save")
        }
    }
}
