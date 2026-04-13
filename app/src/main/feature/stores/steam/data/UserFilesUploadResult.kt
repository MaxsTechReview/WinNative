package com.winlator.cmod.feature.stores.steam.data

data class UserFilesUploadResult(
    val uploadBatchSuccess: Boolean,
    val appChangeNumber: Long,
    val filesUploaded: Int,
    val bytesUploaded: Long,
)
