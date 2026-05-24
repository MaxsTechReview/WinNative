package com.winlator.cmod.feature.sync

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.winlator.cmod.R
import com.winlator.cmod.feature.stores.gog.service.GOGCloudSavesManager
import com.winlator.cmod.runtime.container.Shortcut
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
                onUseCloud = {
                    useCloud = true
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
}
