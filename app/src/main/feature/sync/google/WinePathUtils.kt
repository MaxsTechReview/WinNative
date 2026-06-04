package com.winlator.cmod.feature.sync.google

import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.display.environment.ImageFs
import java.io.File

/**
 * Bidirectional Wine-prefix path utilities used by the Custom Games save-folder picker
 * and Google save backup/restore. PathType.kt covers Steam/GOG/Epic forward mapping; this
 * helper covers the missing reverse direction (Android → Windows) plus a forward
 * resolver that goes through a specific Container.
 */
object WinePathUtils {

    /**
     * Container's drive_c root on the Android filesystem.
     * Containers built by WinNative store their wine prefix at `<container.rootDir>/.wine`.
     */
    fun driveCRoot(container: Container): File =
        File(container.rootDir, ".wine/drive_c")

    /**
     * Convert an absolute Android path that lives inside the container's drive_c
     * to its Windows equivalent (e.g. `C:\\users\\xuser\\AppData\\Roaming\\MyGame`).
     *
     * Returns null if the path is not inside the container's drive_c — callers should
     * reject the user's selection in that case rather than silently round-tripping.
     *
     * Case-insensitive prefix match on `drive_c` so a user picking through a path that
     * was case-changed at any point still resolves cleanly.
     */
    fun androidToWindowsPath(absolutePath: String, container: Container): String? {
        val driveC = driveCRoot(container)
        val canonicalDriveC = runCatching { driveC.canonicalPath }.getOrNull() ?: driveC.absolutePath
        val canonicalTarget = runCatching { File(absolutePath).canonicalPath }.getOrNull() ?: absolutePath

        val prefixWithSep = canonicalDriveC.trimEnd('/') + "/"
        val match =
            when {
                canonicalTarget.equals(canonicalDriveC, ignoreCase = true) -> ""
                canonicalTarget.startsWith(prefixWithSep, ignoreCase = true) ->
                    canonicalTarget.substring(prefixWithSep.length)
                else -> return null
            }

        val winSegments = match.trim('/').split('/').filter { it.isNotEmpty() }
        return if (winSegments.isEmpty()) {
            "C:\\"
        } else {
            "C:\\" + winSegments.joinToString("\\")
        }
    }

    /**
     * Convert a Windows path (e.g. `C:\\users\\xuser\\Documents\\MyGame`) to its
     * Android-side absolute path inside the given container's drive_c.
     *
     * Walks the actual on-disk tree case-insensitively so the resolved path matches
     * existing folder casing rather than producing a path with the user-entered casing
     * that would mkdir under a slightly-different name. If a path component doesn't yet
     * exist on disk the user-supplied casing is preserved for that component.
     */
    fun windowsToAndroidFile(windowsPath: String, container: Container): File {
        val normalized = windowsPath.trim().replace('/', '\\')
        val withoutDrive =
            when {
                normalized.length >= 2 && normalized[1] == ':' -> normalized.substring(2)
                else -> normalized
            }.trimStart('\\')

        val segments = withoutDrive.split('\\').filter { it.isNotEmpty() }
        var cursor: File = driveCRoot(container)
        for (segment in segments) {
            val match = cursor
                .takeIf { it.isDirectory }
                ?.listFiles()
                ?.firstOrNull { it.name.equals(segment, ignoreCase = true) }
            cursor = match ?: File(cursor, segment)
        }
        return cursor
    }

    /**
     * True if `candidate` is inside (or equal to) `container.rootDir/.wine/drive_c`.
     * Used by the folder picker to validate the user's pick.
     */
    fun isInsideDriveC(candidate: File, container: Container): Boolean {
        val driveC = driveCRoot(container)
        val canonicalDriveC = runCatching { driveC.canonicalPath }.getOrNull() ?: driveC.absolutePath
        val canonicalCandidate = runCatching { candidate.canonicalPath }.getOrNull() ?: candidate.absolutePath
        if (canonicalCandidate.equals(canonicalDriveC, ignoreCase = true)) return true
        val prefixWithSep = canonicalDriveC.trimEnd('/') + "/"
        return canonicalCandidate.startsWith(prefixWithSep, ignoreCase = true)
    }

    /** Convenience: ensure the wine `xuser` matches what we assume. */
    @Suppress("unused")
    val WINE_USER: String = ImageFs.USER
}
