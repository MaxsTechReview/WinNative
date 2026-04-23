package com.winlator.cmod.shared.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.preference.PreferenceManager
import com.winlator.cmod.feature.stores.steam.utils.PrefManager

object StoragePermissionCleanup {
    private const val TAG = "StoragePermissionCleanup"

    @JvmStatic
    fun cleanupUnusedTreeUriPermissions(context: Context) {
        val appContext = context.applicationContext
        val activeUris = collectActiveTreeUris(appContext) ?: return
        val resolver = appContext.contentResolver

        resolver.persistedUriPermissions.forEach { permission ->
            val uri = permission.uri
            if (!DocumentsContract.isTreeUri(uri) || uri.toString() in activeUris) {
                return@forEach
            }

            var flags = 0
            if (permission.isReadPermission) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (permission.isWritePermission) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            if (flags == 0) return@forEach

            runCatching {
                resolver.releasePersistableUriPermission(uri, flags)
            }.onFailure {
                Log.w(TAG, "Failed to release stale storage permission for $uri", it)
            }
        }
    }

    private fun collectActiveTreeUris(context: Context): Set<String>? {
        val active = linkedSetOf<String>()
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)

        listOf(
            defaultPrefs.getString("winlator_path_uri", null),
            defaultPrefs.getString("shortcuts_export_path_uri", null),
        ).forEach { uri -> addTreeUri(active, uri) }

        runCatching {
            PrefManager.init(context)
            listOf(
                PrefManager.defaultDownloadFolder,
                PrefManager.steamDownloadFolder,
                PrefManager.epicDownloadFolder,
                PrefManager.gogDownloadFolder,
            ).forEach { uri -> addTreeUri(active, uri) }
        }.onFailure {
            Log.w(TAG, "Unable to inspect store folder preferences", it)
            return null
        }

        return active
    }

    private fun addTreeUri(
        active: MutableSet<String>,
        uriString: String?,
    ) {
        if (uriString.isNullOrBlank()) return
        runCatching {
            val uri = Uri.parse(uriString)
            if (DocumentsContract.isTreeUri(uri)) active.add(uri.toString())
        }
    }
}
