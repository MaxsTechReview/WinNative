package com.winlator.cmod.feature.stores.ubisoft

import android.content.Context
import com.winlator.cmod.runtime.container.ContainerManager
import com.winlator.cmod.runtime.display.environment.ImageFs
import com.winlator.cmod.shared.io.FileUtils
import java.io.File

/**
 * Ubisoft Connect pre-setup helpers.
 *
 * Ubisoft games launched from Steam (Assassin's Creed, etc.) require the Ubisoft Connect client
 * (upc.exe / UbisoftConnect.exe). This object downloads and installs that client, and lets the user
 * sign in once. The install and login always land in the single shared store at
 * `imagefs/home/.ubisoft-store/` because every container symlinks its Ubisoft directories there
 * (see XServerDisplayActivity.shareUbisoftConnectLogin), so one install + one sign-in covers every
 * container.
 */
object UbisoftConnect {
    const val INSTALLER_URL =
        "https://github.com/maxjivi05/proton-wine/releases/download/Ubisoft/UbisoftConnectInstaller.exe"
    const val INSTALLER_FILE = "UbisoftConnectInstaller.exe"

    // The Windows install location Ubisoft Connect uses; this path is symlinked to the shared store.
    private const val LAUNCHER_WINDOWS_DIR = "C:\\Program Files (x86)\\Ubisoft\\Ubisoft Game Launcher"

    // Keep in sync with XServerDisplayActivity.UBISOFT_STORE_RELATIVE_PATH (+ launcher subdir).
    private const val STORE_REL = "home/.ubisoft-store"
    private const val STORE_LAUNCHER_REL = "$STORE_REL/launcher"

    // In-prefix Ubisoft directories (symlinked to the shared store) and the installer's shortcuts.
    private const val LAUNCHER_PREFIX_REL = ".wine/drive_c/Program Files (x86)/Ubisoft/Ubisoft Game Launcher"
    private val LOCALAPPDATA_PREFIX_REL = ".wine/drive_c/users/${ImageFs.USER}/AppData/Local/Ubisoft Game Launcher"
    private val DESKTOP_PREFIX_REL = ".wine/drive_c/users/${ImageFs.USER}/Desktop"

    /** The single shared directory that every container's launcher dir is symlinked to. */
    fun sharedLauncherDir(context: Context): File =
        File(ImageFs.find(context).rootDir, STORE_LAUNCHER_REL)

    /** True once the Ubisoft Connect client binaries are present in the shared store. */
    fun isInstalled(context: Context): Boolean {
        val dir = sharedLauncherDir(context)
        return File(dir, "UbisoftConnect.exe").isFile || File(dir, "upc.exe").isFile
    }

    /** Windows path of the executable to run for interactive sign-in. */
    fun signInWinPath(context: Context): String {
        val dir = sharedLauncherDir(context)
        val exe = if (File(dir, "UbisoftConnect.exe").isFile) "UbisoftConnect.exe" else "upc.exe"
        return "$LAUNCHER_WINDOWS_DIR\\$exe"
    }

    /**
     * One-step ComponentInstaller manifest: download the installer, then run it silently (`/S`, the
     * same switch Bottles uses). It installs into the symlinked launcher dir, i.e. the shared store.
     */
    fun installManifest(): String =
        """
        Steps:
          - action: install_exe
            url: "$INSTALLER_URL"
            file_name: "$INSTALLER_FILE"
            arguments: "/S"
        """.trimIndent()

    /**
     * Fully uninstalls Ubisoft Connect: deletes the shared store (client binaries + login token),
     * then clears each container's symlinks to it and the installer's leftover desktop shortcuts.
     * After this, isInstalled() is false and the store re-creates itself empty on the next launch.
     * Run off the main thread — deleting the store touches hundreds of MB.
     */
    fun uninstall(context: Context) {
        val rootDir = ImageFs.find(context).rootDir
        // Remove per-container symlinks + leftover launcher shortcuts.
        runCatching {
            for (c in ContainerManager(context).containers) {
                FileUtils.delete(File(c.rootDir, LAUNCHER_PREFIX_REL))
                FileUtils.delete(File(c.rootDir, LOCALAPPDATA_PREFIX_REL))
                val desktop = File(c.rootDir, DESKTOP_PREFIX_REL)
                FileUtils.delete(File(desktop, "Ubisoft Connect.lnk"))
                FileUtils.delete(File(desktop, "Ubisoft Connect.desktop"))
            }
        }
        // Remove the shared store itself — this is the actual uninstall.
        runCatching { FileUtils.delete(File(rootDir, STORE_REL)) }
    }
}
