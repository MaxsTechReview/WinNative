package com.winlator.cmod.feature.sync.local

import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager
import com.winlator.cmod.app.config.SettingsConfig
import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.container.Shortcut
import com.winlator.cmod.shared.io.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object LocalGameSaveSyncManager {
    private const val TAG = "LocalGameSaveSync"
    private const val KEY_ENABLED = "local_save_sync_enabled"
    private const val KEY_PATH_URI = "local_save_sync_path_uri"
    private const val HISTORY_LIMIT = 5
    private const val DEFAULT_RELATIVE_DIR = "SaveSync"

    data class SyncResult(
        val success: Boolean,
        val message: String,
    )

    fun isEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_ENABLED, false)

    fun getConfiguredPath(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val fallback = "${SettingsConfig.DEFAULT_WINLATOR_PATH}/$DEFAULT_RELATIVE_DIR"
        val uriStr = prefs.getString(KEY_PATH_URI, null)
        if (uriStr.isNullOrEmpty()) return fallback
        return try {
            FileUtils.getFilePathFromUri(context, Uri.parse(uriStr)) ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }

    suspend fun restoreLatestCustomContainerBackup(
        context: Context,
        container: Container,
        shortcut: Shortcut,
    ): SyncResult =
        withContext(Dispatchers.IO) {
            try {
                val backupDir = getGameBackupDir(context, shortcut, container)
                val latestZip =
                    backupDir
                        .listFiles { file -> file.isFile && file.extension.equals("zip", ignoreCase = true) }
                        ?.sortedByDescending { it.name }
                        ?.firstOrNull()
                        ?: return@withContext SyncResult(false, "No local container backup found.")

                restoreContainerSnapshot(container, latestZip.readBytes())
                SyncResult(true, "Restored ${latestZip.name}.")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed restoring custom container backup for %s", container.getName())
                SyncResult(false, "Local restore failed: ${e.message}")
            }
        }

    suspend fun backupCustomContainer(
        context: Context,
        container: Container,
        shortcut: Shortcut,
    ): SyncResult =
        withContext(Dispatchers.IO) {
            try {
                val zipBytes = createContainerSnapshot(container)
                if (zipBytes.isEmpty()) {
                    return@withContext SyncResult(false, "Container snapshot is empty.")
                }

                val backupDir = getGameBackupDir(context, shortcut, container)
                if (!backupDir.exists() && !backupDir.mkdirs()) {
                    return@withContext SyncResult(false, "Failed to create local backup directory.")
                }

                val zipFile = File(backupDir, buildSnapshotFileName())
                FileOutputStream(zipFile).use { fos -> fos.write(zipBytes) }
                pruneHistory(backupDir)
                SyncResult(true, "Saved ${zipFile.name}.")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed backing up custom container %s", container.getName())
                SyncResult(false, "Local backup failed: ${e.message}")
            }
        }

    private fun getGameBackupDir(
        context: Context,
        shortcut: Shortcut,
        container: Container,
    ): File {
        val rootDir = File(getConfiguredPath(context))
        return File(rootDir, "custom/${buildCustomGameKey(shortcut, container)}")
    }

    private fun buildCustomGameKey(
        shortcut: Shortcut,
        container: Container,
    ): String {
        val identity = resolveCustomGameIdentity(shortcut)
        val hash = sha1(identity.lowercase(Locale.US)).take(12)
        return hash
    }

    private fun resolveCustomGameIdentity(shortcut: Shortcut): String {
        val candidates =
            listOf(
                shortcut.getExtra("custom_game_folder"),
                shortcut.getExtra("game_install_path"),
                shortcut.getExtra("launch_exe_path"),
                shortcut.getExtra("custom_exe"),
            )

        for (candidate in candidates) {
            if (candidate.isBlank()) continue
            val file = File(candidate)
            val base =
                when {
                    file.isDirectory -> file
                    file.isFile -> file.parentFile
                    else -> if (candidate.contains("\\") || candidate.contains("/")) file.parentFile else null
                }
            if (base != null) {
                return runCatching { base.canonicalPath }.getOrElse { base.absolutePath }
            }
        }

        val shortcutPath = shortcut.path ?: ""
        if (shortcutPath.isNotBlank()) {
            return shortcutPath
        }
        return shortcut.name ?: "custom-game"
    }

    private fun sha1(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun buildSnapshotFileName(): String {
        val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return "${fmt.format(Date())}_auto.zip"
    }

    private fun pruneHistory(backupDir: File) {
        val snapshots =
            backupDir
                .listFiles { file -> file.isFile && file.extension.equals("zip", ignoreCase = true) }
                ?.sortedByDescending { it.name }
                .orEmpty()

        snapshots.drop(HISTORY_LIMIT).forEach {
            if (!it.delete()) {
                Timber.tag(TAG).w("Failed to delete old snapshot %s", it.absolutePath)
            }
        }
    }

    private fun createContainerSnapshot(container: Container): ByteArray {
        val containerRoot = container.rootDir ?: return ByteArray(0)
        val targets =
            listOf(
                File(containerRoot, ".wine/drive_c/users") to "drive_c/users",
                File(containerRoot, ".wine/drive_c/ProgramData") to "drive_c/ProgramData",
                File(containerRoot, ".wine/user.reg") to "registry/user.reg",
                File(containerRoot, ".wine/system.reg") to "registry/system.reg",
            )

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((file, zipRoot) in targets) {
                if (!file.exists()) continue
                if (file.isDirectory) {
                    zos.putNextEntry(ZipEntry("$zipRoot/"))
                    zos.closeEntry()
                    zipDirRecursive(zos, file, zipRoot)
                } else {
                    addFileToZip(zos, file, zipRoot)
                }
            }
        }
        return baos.toByteArray()
    }

    private fun restoreContainerSnapshot(
        container: Container,
        zipBytes: ByteArray,
    ) {
        val containerRoot = container.rootDir ?: return
        val usersDir = File(containerRoot, ".wine/drive_c/users")
        val programDataDir = File(containerRoot, ".wine/drive_c/ProgramData")
        val userReg = File(containerRoot, ".wine/user.reg")
        val systemReg = File(containerRoot, ".wine/system.reg")

        deleteRecursively(usersDir)
        deleteRecursively(programDataDir)
        if (userReg.exists()) userReg.delete()
        if (systemReg.exists()) systemReg.delete()

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry?
            while (zis.nextEntry.also { entry = it } != null) {
                val entryName = entry!!.name
                val outFile =
                    when {
                        entryName.startsWith("drive_c/users/") ->
                            File(containerRoot, ".wine/drive_c/users/" + entryName.removePrefix("drive_c/users/"))
                        entryName == "drive_c/users/" -> File(containerRoot, ".wine/drive_c/users")
                        entryName.startsWith("drive_c/ProgramData/") ->
                            File(containerRoot, ".wine/drive_c/ProgramData/" + entryName.removePrefix("drive_c/ProgramData/"))
                        entryName == "drive_c/ProgramData/" -> File(containerRoot, ".wine/drive_c/ProgramData")
                        entryName == "registry/user.reg" -> userReg
                        entryName == "registry/system.reg" -> systemReg
                        else -> null
                    }

                if (outFile == null) {
                    zis.closeEntry()
                    continue
                }

                val canonicalTarget = outFile.canonicalFile
                val canonicalRoot = containerRoot.canonicalFile
                if (!canonicalTarget.path.startsWith(canonicalRoot.path)) {
                    throw SecurityException("Zip entry escapes container root")
                }

                if (entry!!.isDirectory) {
                    canonicalTarget.mkdirs()
                } else {
                    canonicalTarget.parentFile?.mkdirs()
                    FileOutputStream(canonicalTarget).use { fos ->
                        val buffer = ByteArray(8192)
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                }
                zis.closeEntry()
            }
        }
    }

    private fun addFileToZip(
        zos: ZipOutputStream,
        file: File,
        entryName: String,
    ) {
        zos.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var len: Int
            while (fis.read(buffer).also { len = it } > 0) {
                zos.write(buffer, 0, len)
            }
        }
        zos.closeEntry()
    }

    private fun zipDirRecursive(
        zos: ZipOutputStream,
        dir: File,
        baseName: String,
    ) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            val entryName = "$baseName/${child.name}"
            if (child.isDirectory) {
                zos.putNextEntry(ZipEntry("$entryName/"))
                zos.closeEntry()
                zipDirRecursive(zos, child, entryName)
            } else {
                addFileToZip(zos, child, entryName)
            }
        }
    }

    private fun deleteRecursively(file: File) {
        if (!file.exists()) return
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        if (!file.delete()) {
            Timber.tag(TAG).w("Failed deleting %s", file.absolutePath)
        }
    }
}
