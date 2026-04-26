package com.winlator.cmod.feature.shortcuts

import android.content.Context
import com.winlator.cmod.runtime.container.Shortcut
import java.io.File

object LibraryShortcutArtwork {
    private const val HOME_ICON_DIR = "library_home_icons"
    private const val VIEW_ARTWORK_DIR = "library_view_artwork"
    private const val CUSTOM_GAME_ARTWORK_DIR = "library_game_artwork"
    private const val LEGACY_CUSTOM_ICON_DIR = "custom_icons"

    enum class LibraryArtworkSlot(
        val extraKey: String,
        val fileSuffix: String,
    ) {
        GAME_CARD("customLibraryHeroArtPath", "hero"),
        GRID("customLibraryGridArtPath", "grid"),
        CAROUSEL("customLibraryCarouselArtPath", "carousel"),
        LIST("customLibraryListArtPath", "list"),
    }

    @JvmStatic
    fun ensureShortcutUuid(shortcut: Shortcut): String {
        if (shortcut.getExtra("uuid").isEmpty()) {
            shortcut.genUUID()
        }
        return shortcut.getExtra("uuid")
    }

    @JvmStatic
    fun buildManagedHomeIconFile(
        context: Context,
        shortcut: Shortcut,
    ): File {
        val shortcutUuid = ensureShortcutUuid(shortcut)
        val dir = File(context.filesDir, HOME_ICON_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "$shortcutUuid.png")
    }

    @JvmStatic
    fun buildManagedCustomGameArtworkFile(
        context: Context,
        shortcutUuid: String,
    ): File {
        val dir = File(context.filesDir, CUSTOM_GAME_ARTWORK_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "$shortcutUuid.png")
    }

    @JvmStatic
    fun buildManagedViewArtworkFile(
        context: Context,
        shortcut: Shortcut,
        slot: LibraryArtworkSlot,
    ): File {
        val shortcutUuid = ensureShortcutUuid(shortcut)
        val dir = File(context.filesDir, VIEW_ARTWORK_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "${shortcutUuid}_${slot.fileSuffix}.png")
    }

    @JvmStatic
    fun buildLegacyCustomIconFile(
        context: Context,
        shortcutName: String,
    ): File {
        val safeName = shortcutName.replace("/", "_").replace("\\", "_")
        return File(context.filesDir, "$LEGACY_CUSTOM_ICON_DIR/$safeName.png")
    }

    @JvmStatic
    fun findPreferredHomeIconFile(
        context: Context,
        shortcut: Shortcut,
    ): File? {
        val customLibraryIcon =
            shortcut
                .getExtra("customLibraryIconPath")
                .takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { it.isFile() }
        if (customLibraryIcon != null) {
            return customLibraryIcon
        }

        val customCoverArt =
            shortcut
                .getExtra("customCoverArtPath")
                .takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { it.isFile() }
        if (customCoverArt != null) {
            return customCoverArt
        }

        val shortcutDisplayName = shortcut.getExtra("custom_name", shortcut.name).ifBlank { shortcut.name }
        val legacyIcon = buildLegacyCustomIconFile(context, shortcutDisplayName)
        return legacyIcon.takeIf { it.isFile() }
    }

    @JvmStatic
    fun deleteManagedArtwork(
        context: Context,
        absolutePath: String?,
    ): Boolean {
        if (absolutePath.isNullOrBlank() || !isManagedArtworkPath(context, absolutePath)) {
            return false
        }

        val targetFile = File(absolutePath)
        return targetFile.exists() && targetFile.delete()
    }

    @JvmStatic
    fun deleteShortcutArtwork(
        context: Context,
        shortcut: Shortcut,
    ): Int {
        val managedPaths =
            buildSet {
                add(shortcut.getExtra("customLibraryIconPath"))
                add(shortcut.getExtra("customCoverArtPath"))
                LibraryArtworkSlot.values().forEach { slot ->
                    add(shortcut.getExtra(slot.extraKey))
                }
            }

        val shortcutDisplayName = shortcut.getExtra("custom_name", shortcut.name).ifBlank { shortcut.name }
        val legacyIcon = buildLegacyCustomIconFile(context, shortcutDisplayName)
        return managedPaths.count { deleteManagedArtwork(context, it) } +
            if (deleteManagedArtwork(context, legacyIcon.absolutePath)) 1 else 0
    }

    private fun isManagedArtworkPath(
        context: Context,
        absolutePath: String,
    ): Boolean {
        val target = try {
            File(absolutePath).canonicalFile
        } catch (_: Exception) {
            return false
        }

        val managedRoots =
            listOf(
                File(context.filesDir, HOME_ICON_DIR),
                File(context.filesDir, VIEW_ARTWORK_DIR),
                File(context.filesDir, CUSTOM_GAME_ARTWORK_DIR),
                File(context.filesDir, LEGACY_CUSTOM_ICON_DIR),
            )

        return managedRoots.any { root ->
            try {
                val canonicalRoot = root.canonicalFile
                target.path == canonicalRoot.path || target.path.startsWith(canonicalRoot.path + File.separator)
            } catch (_: Exception) {
                false
            }
        }
    }
}
