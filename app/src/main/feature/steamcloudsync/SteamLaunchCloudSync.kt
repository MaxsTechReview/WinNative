package com.winlator.cmod.feature.steamcloudsync

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

object SteamLaunchCloudSync {
    fun interface StatusSink {
        fun show(text: String)
    }

    /**
     * Steam provider cloud saves need launch-time reconciliation. This is Steam-only
     * and never starts Google Play Games or Drive consent.
     */
    @JvmStatic
    fun syncBeforeLaunch(
        activity: Activity,
        shortcut: Shortcut?,
        cloudSyncEnabled: Boolean,
        statusSink: StatusSink,
    ) {
        if (shortcut == null) return
        if (shortcut.getExtra("game_source") != "STEAM") return
        if (!cloudSyncEnabled || SteamCloudSyncHelper.isOfflineMode(shortcut)) return

        SteamCloudSyncHelper.forceDownloadOnContainerSwap(activity, shortcut)

        if (!SteamCloudSyncHelper.hasLocalCloudSaves(activity, shortcut)) {
            statusSink.show(activity.getString(R.string.preloader_downloading_cloud))
            SteamCloudSyncHelper.downloadCloudSaves(activity, shortcut)
            statusSink.show(activity.getString(R.string.preloader_initializing))
            return
        }

        val probe = SteamCloudSyncHelper.probeCloudConflict(activity, shortcut)
        if (!probe.differs) return

        val dialogLatch = CountDownLatch(1)
        var useCloud = false
        var keepBackup = false
        val timestamps = probe.timestamps

        // If the activity is destroyed (back-to-launcher, system kill, finish())
        // while the dialog is up, the latch must still count down or this thread
        // will block forever. Attach a lifecycle observer that releases the latch
        // on ON_DESTROY; the default of useCloud=false then falls back to "keep
        // local" — the safer choice when no user input is captured.
        val lifecycle = (activity as? LifecycleOwner)?.lifecycle
        val cancelObserver =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) {
                    Timber.tag("SteamLaunchCloudSync").w(
                        "Activity destroyed while cloud-conflict dialog was up; releasing latch",
                    )
                    dialogLatch.countDown()
                }
            }

        activity.runOnUiThread {
            lifecycle?.addObserver(cancelObserver)
            SteamCloudConflictDialog.show(
                activity,
                timestamps,
                onUseCloud = { keep ->
                    useCloud = true
                    keepBackup = keep
                    dialogLatch.countDown()
                },
                onUseLocal = { keep ->
                    useCloud = false
                    keepBackup = keep
                    dialogLatch.countDown()
                },
            )
        }

        try {
            // Belt-and-suspenders timeout in case both the dialog and the lifecycle
            // observer somehow fail to fire. 10 minutes is generous for a user
            // looking at the dialog, but bounds the worst case.
            if (!dialogLatch.await(10, TimeUnit.MINUTES)) {
                Timber.tag("SteamLaunchCloudSync").w(
                    "Cloud-conflict dialog timed out after 10 minutes; treating as 'keep local'",
                )
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            activity.runOnUiThread { lifecycle?.removeObserver(cancelObserver) }
            return
        }

        activity.runOnUiThread { lifecycle?.removeObserver(cancelObserver) }

        if (!useCloud) return
        if (keepBackup) {
            backupDiscardedSave(activity, shortcut, GameSaveBackupManager.BackupOrigin.LOCAL)
        }
        statusSink.show(activity.getString(R.string.preloader_syncing_cloud))
        SteamCloudSyncHelper.downloadCloudSaves(activity, shortcut)
        statusSink.show(activity.getString(R.string.preloader_initializing))
    }

    private fun backupDiscardedSave(
        activity: Activity,
        shortcut: Shortcut,
        origin: GameSaveBackupManager.BackupOrigin,
    ) {
        val gameId = shortcut.getExtra("app_id").takeIf { it.isNotEmpty() } ?: return
        val gameName = shortcut.name ?: "Unknown"
        try {
            val result =
                runBlocking(Dispatchers.IO) {
                    GameSaveBackupManager.backupDiscardedSave(
                        activity = activity,
                        gameSource = GameSaveBackupManager.GameSource.STEAM,
                        gameId = gameId,
                        gameName = gameName,
                        origin = origin,
                        authMode = GoogleAuthMode.SILENT,
                    )
                }
            Timber.tag("SteamLaunchCloudSync").i("Discarded Steam save backup: %s", result.message)
        } catch (e: Exception) {
            Timber.tag("SteamLaunchCloudSync").w(e, "Failed to back up discarded Steam save")
        }
    }
}
