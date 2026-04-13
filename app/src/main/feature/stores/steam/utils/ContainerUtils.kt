package com.winlator.cmod.feature.stores.steam.utils
import android.content.Context
import com.winlator.cmod.feature.setup.SetupWizardActivity
import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.container.ContainerManager
import timber.log.Timber
import java.io.File

object ContainerUtils {
    fun getContainerId(appId: String): String = appId

    /**
     * Extracts the game ID from a container ID string
     */
    fun extractGameIdFromContainerId(containerId: String): Int {
        // Remove duplicate suffix like (1), (2) if present
        val idWithoutSuffix =
            if (containerId.contains("(")) {
                containerId.substringBefore("(")
            } else {
                containerId
            }

        // Split by underscores and find the last numeric part
        val parts = idWithoutSuffix.split("_")
        // The last part should be the numeric ID
        val lastPart = parts.lastOrNull() ?: throw IllegalArgumentException("Invalid container ID format: $containerId")

        return try {
            lastPart.toInt()
        } catch (e: NumberFormatException) {
            Timber.d("extractGameIdFromContainerId: Non-numeric ID '$lastPart' -> hashCode=${lastPart.hashCode()}")
            lastPart.hashCode()
        }
    }

    fun hasContainer(
        context: Context,
        containerId: String,
    ): Boolean = getContainer(context, containerId) != null

    fun getContainer(
        context: Context,
        containerId: String,
    ): Container? {
        val containerManager = ContainerManager(context)
        return containerManager
            .getContainerById(extractGameIdFromContainerId(containerId))
            ?.takeIf { SetupWizardActivity.isContainerUsable(context, it) }
    }

    fun getUsableContainerOrNull(
        context: Context,
        appId: String,
    ): Container? {
        val containerManager = ContainerManager(context)
        SetupWizardActivity.getPreferredGameContainer(context, containerManager)?.let { return it }

        val containerName = getContainerId(appId)
        return containerManager.containers.firstOrNull {
            it.name == containerName && SetupWizardActivity.isContainerUsable(context, it)
        }
    }

    fun getOrCreateContainer(
        context: Context,
        appId: String,
    ): Container =
        getUsableContainerOrNull(context, appId)
            ?: throw IllegalStateException("No installed Wine/Proton container available")

    fun getGameDrivePath(drives: String): String? {
        for (drive in Container.drivesIterator(drives)) {
            if (drive[0] != "C" && drive[0] != "D" && drive[0] != "E" && drive[0] != "Z") {
                return drive[1]
            }
        }
        return null
    }

    fun deleteContainer(
        context: Context,
        containerId: String,
    ) {
        val containerManager = ContainerManager(context)
        val container = containerManager.getContainerById(extractGameIdFromContainerId(containerId))
        if (container != null) {
            containerManager.removeContainerAsync(container) {}
        }
    }

    /**
     * Recursively scans the mapped game drive for .exe files, returning relative paths.
     * Custom download paths are preserved because we respect the drives string.
     */
    @JvmStatic
    fun scanExecutablesInGameDrive(drives: String): List<String> {
        val executables = mutableListOf<String>()

        try {
            val gameDrivePath = getGameDrivePath(drives)
            if (gameDrivePath == null) {
                Timber.w("No mapped game drive found in container drives")
                return emptyList()
            }

            val aDir = File(gameDrivePath)
            if (!aDir.exists() || !aDir.isDirectory) {
                Timber.w("Mapped game drive path does not exist or is not a directory: $gameDrivePath")
                return emptyList()
            }

            fun scanRecursive(
                dir: File,
                baseDir: File,
                depth: Int = 0,
                maxDepth: Int = 10,
            ) {
                if (depth > maxDepth) return
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        scanRecursive(file, baseDir, depth + 1, maxDepth)
                    } else if (file.isFile && file.name.lowercase().endsWith(".exe")) {
                        val relativePath = baseDir.toURI().relativize(file.toURI()).path
                        executables.add(relativePath)
                    }
                }
            }

            scanRecursive(aDir, aDir)

            executables.sortWith { a, b ->
                val aScore = getExecutablePriority(a)
                val bScore = getExecutablePriority(b)
                if (aScore != bScore) {
                    bScore.compareTo(aScore)
                } else {
                    a.compareTo(b, ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scanning mapped game drive for executables")
        }

        return executables
    }

    /**
     * Filters executables to avoid obvious installers/utilities when unpacking DRM.
     */
    @JvmStatic
    fun filterExesForUnpacking(exePaths: List<String>): List<String> =
        exePaths.filter { path ->
            val fileName = path.substringAfterLast('/').substringAfterLast('\\').lowercase()
            !isSystemExecutable(fileName)
        }

    private fun getExecutablePriority(exePath: String): Int {
        val fileName = exePath.substringAfterLast('\\').lowercase()
        val baseName = fileName.substringBeforeLast('.')

        return when {
            fileName.contains("game") -> 100
            fileName.contains("start") -> 85
            fileName.contains("main") -> 80
            fileName.contains("launcher") && !fileName.contains("unins") -> 75
            baseName.length >= 4 && !isSystemExecutable(fileName) -> 70
            !isSystemExecutable(fileName) -> 50
            else -> 10
        }
    }

    private fun isSystemExecutable(fileName: String): Boolean {
        val systemKeywords =
            listOf(
                "unins",
                "setup",
                "install",
                "config",
                "crash",
                "handler",
                "viewer",
                "compiler",
                "tool",
                "redist",
                "vcredist",
                "directx",
                "steam",
                "origin",
                "uplay",
                "epic",
                "battlenet",
            )
        return systemKeywords.any { fileName.contains(it) }
    }
}
