package com.winlator.cmod.feature.shortcuts
import android.content.Context
import android.content.pm.ShortcutManager
import android.os.Build
import android.util.Log
import com.winlator.cmod.R
import com.winlator.cmod.feature.stores.steam.data.SteamApp
import com.winlator.cmod.runtime.container.ContainerManager
import com.winlator.cmod.runtime.container.Shortcut
import java.io.File
import java.util.Locale

object LibraryShortcutUtils {
    private const val TAG = "LibraryShortcutUtils"

    @JvmStatic
    fun inferGameSource(shortcut: Shortcut): String {
        val explicitSource = shortcut.getExtra("game_source").trim()
        if (explicitSource.isNotEmpty()) {
            return explicitSource.uppercase(Locale.US)
        }

        if (shortcut.getExtra("gog_id").isNotEmpty()) {
            return "GOG"
        }

        if (shortcut.getExtra("app_id").toIntOrNull() != null) {
            return "STEAM"
        }

        return "CUSTOM"
    }

    @JvmStatic
    fun isCustomLibraryShortcut(shortcut: Shortcut): Boolean = inferGameSource(shortcut) == "CUSTOM"

    @JvmStatic
    fun getCustomDisplayName(shortcut: Shortcut): String =
        shortcut
            .getExtra("custom_name", shortcut.name)
            .ifBlank { shortcut.name }

    @JvmStatic
    fun getCustomGameDirectory(shortcut: Shortcut): String =
        normalizeCustomPath(
            shortcut.getExtra(
                "game_install_path",
                shortcut.getExtra("custom_game_folder", ""),
            ),
        )

    @JvmStatic
    fun getCustomLaunchPath(shortcut: Shortcut): String =
        normalizeCustomPath(shortcut.getExtra("launch_exe_path", shortcut.getExtra("custom_exe", "")))

    @JvmStatic
    fun buildCustomIdentity(shortcut: Shortcut): String =
        buildCustomIdentity(
            gameDir = getCustomGameDirectory(shortcut),
            launchPath = getCustomLaunchPath(shortcut),
            displayName = getCustomDisplayName(shortcut),
        )

    @JvmStatic
    fun buildCustomIdentity(app: SteamApp): String =
        buildCustomIdentity(
            gameDir = normalizeCustomPath(app.gameDir),
            launchPath = normalizeCustomPath(app.installDir),
            displayName = app.name,
        )

    @JvmStatic
    fun buildCustomLibraryId(identity: String): Int = -(identity.hashCode().and(0x7FFFFFFF) + 1)

    @JvmStatic
    fun matchesCustomApp(
        shortcut: Shortcut,
        app: SteamApp,
    ): Boolean {
        if (!isCustomLibraryShortcut(shortcut)) return false
        val appIdentity = buildCustomIdentity(app)
        val shortcutIdentity = buildCustomIdentity(shortcut)
        if (appIdentity.isNotEmpty() && appIdentity == shortcutIdentity) {
            return true
        }

        val appDir = normalizeCustomPath(app.gameDir)
        val shortcutDir = getCustomGameDirectory(shortcut)
        if (appDir.isNotEmpty() && appDir == shortcutDir) {
            val appLaunch = normalizeCustomPath(app.installDir)
            val shortcutLaunch = getCustomLaunchPath(shortcut)
            return appLaunch.isEmpty() || shortcutLaunch.isEmpty() || appLaunch == shortcutLaunch
        }

        return getCustomDisplayName(shortcut).equals(app.name, ignoreCase = true)
    }

    @JvmStatic
    fun detectCustomGameFolder(exeFile: File): File {
        val executableRoot = detectExecutableRoot(exeFile)
        return detectPackageRoot(executableRoot)
    }

    @JvmStatic
    fun detectCustomGameFolder(exePath: String): String =
        detectCustomGameFolder(File(exePath)).absolutePath

    @JvmStatic
    fun buildPinnedHomeShortcutId(shortcut: Shortcut): String? {
        val shortcutId = shortcut.getExtra("uuid")
        if (shortcutId.isEmpty()) {
            return null
        }

        val canonicalShortcutPath = shortcut.file.absolutePath
        val shortcutPathHash = canonicalShortcutPath.hashCode()
        return "shortcut_${shortcut.container.id}_${shortcutId}_${shortcutPathHash.toUInt().toString(16)}"
    }

    @JvmStatic
    fun hasPinnedHomeShortcut(
        context: Context,
        shortcut: Shortcut,
    ): Boolean {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false
        val pinnedShortcutId = buildPinnedHomeShortcutId(shortcut) ?: return false

        return try {
            shortcutManager.pinnedShortcuts.any { it.id == pinnedShortcutId && it.isEnabled }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query pinned shortcuts for ${shortcut.file.absolutePath}", e)
            false
        }
    }

    @JvmStatic
    fun disablePinnedHomeShortcut(
        context: Context,
        shortcut: Shortcut,
    ): Boolean {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false
        val pinnedShortcutId = buildPinnedHomeShortcutId(shortcut) ?: return false

        return try {
            val isPinned = shortcutManager.pinnedShortcuts.any { it.id == pinnedShortcutId }
            if (isPinned) {
                shortcutManager.disableShortcuts(
                    listOf(pinnedShortcutId),
                    context.getString(R.string.shortcuts_list_not_available),
                )
                shortcutManager.removeDynamicShortcuts(listOf(pinnedShortcutId))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    shortcutManager.removeLongLivedShortcuts(listOf(pinnedShortcutId))
                }
            }
            isPinned
        } catch (e: Exception) {
            Log.w(TAG, "Failed to disable pinned home shortcut for ${shortcut.file.absolutePath}", e)
            false
        }
    }

    @JvmStatic
    fun deleteShortcutArtifacts(
        context: Context,
        shortcut: Shortcut,
    ): Boolean {
        var deletedAny = false

        disablePinnedHomeShortcut(context, shortcut)
        LibraryShortcutArtwork.deleteShortcutArtwork(context, shortcut)

        try {
            ShortcutsFragment.disableShortcutOnScreen(context, shortcut)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to disable pinned shortcut for ${shortcut.file.absolutePath}", e)
        }

        val desktopFile = shortcut.file
        if (desktopFile.exists()) {
            deletedAny = desktopFile.delete() || deletedAny
        }

        val lnkFile = File(desktopFile.path.substringBeforeLast(".") + ".lnk")
        if (lnkFile.exists()) {
            deletedAny = lnkFile.delete() || deletedAny
        }

        return deletedAny
    }

    @JvmStatic
    fun deleteSteamShortcuts(
        context: Context,
        appId: Int,
    ): Int =
        deleteMatchingShortcuts(context) { shortcut ->
            inferGameSource(shortcut) == "STEAM" && shortcut.getExtra("app_id") == appId.toString()
        }

    @JvmStatic
    fun deleteEpicShortcuts(
        context: Context,
        appId: Int,
    ): Int =
        deleteMatchingShortcuts(context) { shortcut ->
            inferGameSource(shortcut) == "EPIC" && shortcut.getExtra("app_id") == appId.toString()
        }

    @JvmStatic
    fun deleteGogShortcuts(
        context: Context,
        gogId: String,
        appId: String = "",
    ): Int =
        deleteMatchingShortcuts(context) { shortcut ->
            inferGameSource(shortcut) == "GOG" &&
                (
                    shortcut.getExtra("gog_id") == gogId ||
                        (appId.isNotEmpty() && shortcut.getExtra("app_id") == appId)
                )
        }

    private inline fun deleteMatchingShortcuts(
        context: Context,
        predicate: (Shortcut) -> Boolean,
    ): Int {
        var deletedCount = 0

        ContainerManager(context)
            .loadShortcuts()
            .filter(predicate)
            .forEach { shortcut ->
                if (deleteShortcutArtifacts(context, shortcut)) {
                    deletedCount++
                }
            }

        return deletedCount
    }

    private fun detectExecutableRoot(exeFile: File): File {
        val subDirNames =
            setOf(
                "bin",
                "binaries",
                "x64",
                "x86",
                "win64",
                "win32",
                "bin64",
                "bin32",
                "game",
                "build",
                "release",
                "shipping",
                "debug",
                "retail",
                "dist",
            )
        var dir = exeFile.parentFile ?: return exeFile
        while (dir.parentFile != null) {
            if (dir.name.lowercase(Locale.getDefault()) in subDirNames) {
                dir = dir.parentFile!!
            } else {
                break
            }
        }
        return dir
    }

    private fun detectPackageRoot(executableRoot: File): File {
        var root = executableRoot
        repeat(3) {
            val parent = root.parentFile ?: return root
            if (shouldPromoteToParentPackageRoot(root, parent)) {
                root = parent
            } else {
                return root
            }
        }
        return root
    }

    private fun buildCustomIdentity(
        gameDir: String,
        launchPath: String,
        displayName: String,
    ): String {
        val normalizedDir = normalizeCustomPath(gameDir)
        val normalizedLaunch = normalizeCustomPath(launchPath)
        val normalizedName = displayName.trim().lowercase(Locale.US)

        return when {
            normalizedDir.isNotEmpty() && normalizedLaunch.isNotEmpty() -> "$normalizedDir|$normalizedLaunch"
            normalizedDir.isNotEmpty() -> "dir:$normalizedDir"
            normalizedLaunch.isNotEmpty() -> "exe:$normalizedLaunch"
            normalizedName.isNotEmpty() -> "name:$normalizedName"
            else -> ""
        }
    }

    private fun normalizeCustomPath(path: String?): String =
        path
            .orEmpty()
            .trim()
            .replace('\\', '/')
            .removeSuffix("/")
            .lowercase(Locale.US)

    private fun shouldPromoteToParentPackageRoot(
        currentRoot: File,
        parentRoot: File,
    ): Boolean {
        val projectMarkers =
            listOf("Binaries", "Content", "Plugins", "Resources", "Data", "Managed")
        val sharedRuntimeMarkers =
            listOf("Engine", "_CommonRedist", "Redist", "Redistributables", "Support")
        val currentLooksLikeProjectDir = hasChildDirectoryNamed(currentRoot, projectMarkers)
        val parentHasSharedRuntime = hasChildDirectoryNamed(parentRoot, sharedRuntimeMarkers)
        return currentLooksLikeProjectDir && parentHasSharedRuntime
    }

    private fun hasChildDirectoryNamed(
        dir: File,
        names: List<String>,
    ): Boolean {
        val normalizedNames = names.map { it.lowercase(Locale.getDefault()) }.toSet()
        return dir
            .listFiles()
            .orEmpty()
            .any { it.isDirectory && it.name.lowercase(Locale.getDefault()) in normalizedNames }
    }
}
