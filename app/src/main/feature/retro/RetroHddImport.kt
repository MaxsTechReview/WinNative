package com.winlator.cmod.feature.retro

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream

object RetroHddImport {
    const val BLANK_IMAGE = "DEV9hdd.raw"

    fun hddDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "hdd").apply { mkdirs() }

    fun installed(context: Context): List<File> =
        hddDir(context).listFiles().orEmpty()
            .filter { it.isFile && it.extension.equals("raw", true) && it.name != BLANK_IMAGE }
            .sortedBy { it.name.lowercase() }

    fun imageFile(context: Context, name: String?): File? {
        if (name.isNullOrBlank()) return null
        val f = File(hddDir(context), name)
        return f.takeIf { it.isFile }
    }

    fun delete(context: Context, name: String): Boolean =
        File(hddDir(context), name).let { it.isFile && it.delete() }

    private fun displayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()

    fun importFromUri(context: Context, uri: Uri): Result<String> =
        runCatching {
            val name = displayName(context, uri) ?: "hdd.raw"
            val dir = hddDir(context)
            if (name.endsWith(".zip", ignoreCase = true)) {
                var extracted: String? = null
                context.contentResolver.openInputStream(uri)?.use { input ->
                    ZipInputStream(input.buffered()).use { zin ->
                        var entry = zin.nextEntry
                        while (entry != null && extracted == null) {
                            val entryName = entry.name.substringAfterLast('/')
                            if (!entry.isDirectory && entryName.endsWith(".raw", ignoreCase = true)) {
                                File(dir, entryName).outputStream().buffered().use { out -> zin.copyTo(out, 1 shl 20) }
                                extracted = entryName
                            }
                            zin.closeEntry()
                            entry = zin.nextEntry
                        }
                    }
                } ?: throw IllegalStateException("Could not read file")
                extracted ?: throw IllegalArgumentException("No .raw HDD image found inside the ZIP")
            } else {
                val outName = if (name.endsWith(".raw", ignoreCase = true)) name else "$name.raw"
                context.contentResolver.openInputStream(uri)?.use { input ->
                    File(dir, outName).outputStream().buffered().use { out -> input.copyTo(out, 1 shl 20) }
                } ?: throw IllegalStateException("Could not read file")
                outName
            }
        }
}
