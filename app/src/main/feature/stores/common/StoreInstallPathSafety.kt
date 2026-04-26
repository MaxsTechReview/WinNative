package com.winlator.cmod.feature.stores.common

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.winlator.cmod.feature.stores.steam.utils.PrefManager
import com.winlator.cmod.shared.io.FileUtils
import java.io.File

object StoreInstallPathSafety {
    data class DeleteCheck(
        val allowed: Boolean,
        val reason: String = "",
    )

    fun checkInstallDirDelete(
        context: Context?,
        targetPath: String,
        protectedRoots: Collection<String> = emptyList(),
    ): DeleteCheck {
        if (targetPath.isBlank()) {
            return DeleteCheck(false, "empty install path")
        }

        val target = normalize(File(targetPath))
        val protected = buildProtectedRoots(context, protectedRoots)
        val matchedRoot = protected.firstOrNull { samePath(target, it) }
        if (matchedRoot != null) {
            return DeleteCheck(false, "target is a protected root: ${matchedRoot.path}")
        }

        return DeleteCheck(true)
    }

    fun canDeleteInstallDir(
        context: Context?,
        targetPath: String,
        protectedRoots: Collection<String> = emptyList(),
    ): Boolean = checkInstallDirDelete(context, targetPath, protectedRoots).allowed

    private fun buildProtectedRoots(
        context: Context?,
        extraRoots: Collection<String>,
    ): List<File> {
        val roots = linkedMapOf<String, File>()

        fun add(file: File?) {
            val normalized = file?.let(::normalize) ?: return
            if (normalized.path.isBlank()) return
            roots[normalized.path] = normalized
        }

        add(File("/"))
        add(File("/storage"))
        add(File("/mnt/media_rw"))
        add(Environment.getExternalStorageDirectory())

        File("/storage").listFiles().orEmpty().forEach { child ->
            if (!child.isDirectory || child.name == "self") return@forEach
            if (child.name == "emulated") {
                add(File(child, "0"))
            } else {
                add(child)
            }
        }

        File("/mnt/media_rw").listFiles().orEmpty().forEach { child ->
            if (child.isDirectory) add(child)
        }

        context?.getExternalFilesDirs(null).orEmpty().forEach { externalFilesDir ->
            add(resolveStorageRootFromExternalFilesDir(externalFilesDir))
        }

        configuredDownloadRoots(context).forEach(::add)
        extraRoots.map(::File).forEach(::add)

        return roots.values.toList()
    }

    private fun configuredDownloadRoots(context: Context?): List<File> {
        val values =
            listOf(
                PrefManager.defaultDownloadFolder,
                PrefManager.steamDownloadFolder,
                PrefManager.epicDownloadFolder,
                PrefManager.gogDownloadFolder,
                PrefManager.externalStoragePath,
            )

        return values.mapNotNull { resolveConfiguredPath(context, it) }
    }

    private fun resolveConfiguredPath(
        context: Context?,
        value: String,
    ): File? {
        if (value.isBlank()) return null
        if (context != null) {
            val resolved =
                try {
                    FileUtils.getFilePathFromUri(context, Uri.parse(value))
                } catch (_: Exception) {
                    null
                }
            if (!resolved.isNullOrBlank()) return File(resolved)
        }

        val uriPath =
            try {
                Uri.parse(value).path
            } catch (_: Exception) {
                null
            }
        return File(uriPath ?: value)
    }

    private fun resolveStorageRootFromExternalFilesDir(dir: File): File? {
        val absolute = dir.absoluteFile
        val androidDir =
            generateSequence(absolute) { it.parentFile }
                .firstOrNull { it.name.equals("Android", ignoreCase = true) }
        return androidDir?.parentFile
    }

    private fun normalize(file: File): File =
        try {
            file.canonicalFile
        } catch (_: Exception) {
            file.absoluteFile
        }

    private fun samePath(
        a: File,
        b: File,
    ): Boolean = normalize(a).path.trimEnd('/') == normalize(b).path.trimEnd('/')
}
