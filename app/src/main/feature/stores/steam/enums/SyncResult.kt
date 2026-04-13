package com.winlator.cmod.feature.stores.steam.enums

enum class SyncResult {
    Success,
    UpToDate,
    InProgress,
    PendingOperations,
    Conflict,
    UpdateFail,
    DownloadFail,
    UnknownFail,
}
