package com.winlator.cmod.shared.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

/**
 * Opens Android's system folder picker at normal device storage instead of
 * letting DocumentsUI restore an app-scoped provider as the initial root.
 */
class AndroidFileTreeContract : ActivityResultContract<Uri?, Uri?>() {
    override fun createIntent(
        context: Context,
        input: Uri?,
    ): Intent =
        createBaseIntent(context).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            )
            putExtra(EXTRA_SHOW_ADVANCED, true)
            putExtra(EXTRA_FANCY, true)
            putExtra(EXTRA_SHOW_FILESIZE, true)
            putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true)
            if (input != null) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, input)
            } else if (!hasExtra(DocumentsContract.EXTRA_INITIAL_URI)) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, PRIMARY_STORAGE_ROOT_URI)
            }
        }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Uri? =
        if (resultCode == Activity.RESULT_OK) {
            intent?.data
        } else {
            null
        }

    private fun createBaseIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val storageManager = context.getSystemService(StorageManager::class.java)
            val volumeIntent = storageManager?.primaryStorageVolume?.createOpenDocumentTreeIntent()
            if (volumeIntent != null) return volumeIntent
        }
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
    }

    private companion object {
        private const val EXTRA_SHOW_ADVANCED = "android.content.extra.SHOW_ADVANCED"
        private const val EXTRA_FANCY = "android.content.extra.FANCY"
        private const val EXTRA_SHOW_FILESIZE = "android.content.extra.SHOW_FILESIZE"
        private val PRIMARY_STORAGE_ROOT_URI: Uri =
            DocumentsContract.buildRootUri("com.android.externalstorage.documents", "primary")
    }
}

class AndroidFilePickerContract : ActivityResultContract<Array<String>, Uri?>() {
    override fun createIntent(
        context: Context,
        input: Array<String>,
    ): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input.singleOrNull() ?: "*/*"
            if (input.size > 1) {
                putExtra(Intent.EXTRA_MIME_TYPES, input)
            }
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
            putExtra(EXTRA_SHOW_ADVANCED, true)
            putExtra(EXTRA_FANCY, true)
            putExtra(EXTRA_SHOW_FILESIZE, true)
            putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true)
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, PRIMARY_STORAGE_ROOT_URI)
        }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Uri? =
        if (resultCode == Activity.RESULT_OK) {
            intent?.data
        } else {
            null
        }

    private companion object {
        private const val EXTRA_SHOW_ADVANCED = "android.content.extra.SHOW_ADVANCED"
        private const val EXTRA_FANCY = "android.content.extra.FANCY"
        private const val EXTRA_SHOW_FILESIZE = "android.content.extra.SHOW_FILESIZE"
        private val PRIMARY_STORAGE_ROOT_URI: Uri =
            DocumentsContract.buildRootUri("com.android.externalstorage.documents", "primary")
    }
}
