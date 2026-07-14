package com.winlator.cmod.feature.stores.ubisoft

import android.content.Context
import com.winlator.cmod.runtime.display.environment.ImageFs
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

    // Keep in sync with XServerDisplayActivity.UBISOFT_STORE_RELATIVE_PATH + UBISOFT_LAUNCHER_STORE_NAME.
    private const val STORE_LAUNCHER_REL = "home/.ubisoft-store/launcher"

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
}
