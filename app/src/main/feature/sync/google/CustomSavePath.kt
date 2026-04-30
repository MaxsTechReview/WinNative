package com.winlator.cmod.feature.sync.google

import com.winlator.cmod.runtime.container.Container
import java.io.File

/**
 * Validation + normalization for the user-picked custom-game save folder.
 *
 * The folder always lives under `<container.rootDir>/.wine/drive_c/`. We store
 * it as a path **relative to** that drive_c root (e.g. `users/xuser/Documents/MyGame/Saves`)
 * so the same shortcut record is portable across devices: each device resolves
 * the relative path under its own container layout.
 *
 * Untrusted-input policy: any read of `shortcut.extraData["custom_save_path"]`
 * **or** a backup-manifest's `customRelPath` MUST go through [normalizeAndValidate]
 * before being used to read or write files. The stored value could have been
 * tampered with, restored from another device, or imported from a stale build.
 */
internal object CustomSavePath {

    /**
     * Normalize and validate a path supplied by the user picker (absolute) OR a
     * stored value (typically relative to drive_c).
     *
     * Returns the path *relative to drive_c*, with forward slashes and no leading slash,
     * or `null` if the path cannot be resolved inside drive_c (escape attempt, symlink
     * traversal, parent-of-drive_c, file instead of dir, etc.).
     *
     * The check uses canonical-path containment, which collapses `..` and resolves
     * symlinks before comparison.
     */
    fun normalizeAndValidate(input: String, container: Container): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val driveC =
            runCatching { File(container.rootDir, ".wine/drive_c").canonicalFile }
                .getOrNull() ?: return null

        // If the input looks absolute (starts with / on Unix), use it; otherwise resolve under drive_c.
        val candidateFile =
            if (File(trimmed).isAbsolute) File(trimmed)
            else File(driveC, trimmed)

        val canonical = runCatching { candidateFile.canonicalFile }.getOrNull() ?: return null

        val driveCPath = driveC.path
        val candPath = canonical.path

        // Containment: must equal driveC OR start with "<driveC>/" (separator-aware).
        if (candPath != driveCPath && !candPath.startsWith(driveCPath + File.separator)) {
            return null
        }

        // Reject the bare drive_c root — backing up the entire C: drive is almost
        // never the user's intent and the result would be huge.
        if (candPath == driveCPath) return null

        // Path relative to drive_c, normalized to forward slashes.
        val rel = canonical.relativeTo(driveC).path.replace(File.separatorChar, '/').trimStart('/')
        if (rel.isEmpty()) return null
        return rel
    }

    /** Resolve a previously-validated relative path back to an absolute File under drive_c. */
    fun resolveAbsolute(relPath: String, container: Container): File =
        File(File(container.rootDir, ".wine/drive_c"), relPath)
}
