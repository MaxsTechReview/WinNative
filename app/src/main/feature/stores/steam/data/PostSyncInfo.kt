package com.winlator.cmod.feature.stores.steam.data
import com.winlator.cmod.feature.stores.steam.enums.SyncResult

data class PostSyncInfo(
    val syncResult: SyncResult,
    val remoteTimestamp: Long = 0,
    val localTimestamp: Long = 0,
    val uploadsRequired: Boolean = false,
    val uploadsCompleted: Boolean = true,
    val filesUploaded: Int = 0,
    val filesDownloaded: Int = 0,
    val filesDeleted: Int = 0,
    val filesManaged: Int = 0,
    val bytesUploaded: Long = 0L,
    val bytesDownloaded: Long = 0L,
    val microsecTotal: Long = 0L,
    val microsecInitCaches: Long = 0L,
    val microsecValidateState: Long = 0L,
    val microsecAcLaunch: Long = 0L,
    val microsecAcPrepUserFiles: Long = 0L,
    val microsecAcExit: Long = 0L,
    val microsecBuildSyncList: Long = 0L,
    val microsecDeleteFiles: Long = 0L,
    val microsecDownloadFiles: Long = 0L,
    val microsecUploadFiles: Long = 0L,
    // ECloudPendingRemoteOperation codes (1 AppSessionActive, 2 UploadInProgress,
    // 3 UploadPending, 4 AppSessionSuspended); empty = clear to launch.
    val pendingRemoteOperations: List<Int> = emptyList(),
)
