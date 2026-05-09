package com.winlator.cmod.feature.stores.epic.service

import android.content.Context
import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.wine.WineRegistryEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages EOS (Epic Online Services) Overlay installation and configuration inside Wine
 * containers.
 *
 * The overlay provides in-game Epic notifications, friend activity, and purchasing UI.
 * Implementation follows Legendary's overlay flow (legendary/lfs/eos.py, legendary/core.py).
 *
 * Install flow:
 *  1. Fetch the latest overlay manifest from Epic's CDN.
 *  2. Download and install overlay files into the Wine prefix (as-is, no DLL modification).
 *  3. Write the overlay install path to the Wine registry
 *     (HKCU\SOFTWARE\Epic Games\EOS\OverlayPath).
 *
 * Failure to install the overlay is non-fatal — the EOS SDK degrades gracefully when the
 * overlay is missing (no HUD/notifications) but auth/online features keep working.
 */
@Singleton
class EpicOverlayManager
    @Inject
    constructor(
        private val epicManager: EpicManager,
        private val epicDownloadManager: EpicDownloadManager,
    ) {
        companion object {
            // EOS Overlay Epic app identifiers — source: legendary/lfs/eos.py EOSOverlayApp.
            const val OVERLAY_APP_NAME = "98bc04bc842e4906993fd6d6644ffb8d"
            const val OVERLAY_NAMESPACE = "302e5ede476149b1bc3e4fe6ae45e50e"
            const val OVERLAY_CATALOG_ITEM_ID = "cc15684f44d849e89e9bf4cec0508b68"

            // Wine prefix path mirrors the standard Epic launcher install location. Legendary
            // searches for the overlay at:
            //   {prefix}/drive_c/Program Files (x86)/Epic Games/Launcher/Portal/Extras/Overlay
            const val OVERLAY_WINE_RELATIVE_PATH =
                "drive_c/Program Files (x86)/Epic Games/Launcher/Portal/Extras/Overlay"

            // Windows-style path used in the registry value (HKCU\SOFTWARE\Epic Games\EOS).
            const val OVERLAY_WIN_PATH =
                "C:\\Program Files (x86)\\Epic Games\\Launcher\\Portal\\Extras\\Overlay"

            // Registry keys — source: legendary/lfs/eos.py EOS_OVERLAY_KEY / EOS_OVERLAY_VALUE.
            const val EOS_OVERLAY_REG_KEY = "SOFTWARE\\Epic Games\\EOS"
            const val EOS_OVERLAY_REG_VALUE = "OverlayPath"

            // Presence of this file signals that the overlay is installed.
            // Mirrors legendary/core.py Core.is_overlay_install().
            const val OVERLAY_MARKER_FILE = "EOSOVH-Win64-Shipping.dll"

            private const val TAG = "EOSOverlay"
        }

        /**
         * Install the EOS overlay into [container]'s Wine prefix.
         *
         * Idempotent: if the overlay marker file already exists, the function returns success
         * without re-downloading unless [forceReinstall] is true.
         */
        suspend fun installOverlay(
            context: Context,
            container: Container,
            forceReinstall: Boolean = false,
            onProgress: ((Int, Int) -> Unit)? = null,
        ): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    val overlayDir = overlayDir(container)

                    if (!forceReinstall && isOverlayInstalled(container)) {
                        Timber.tag(TAG).i("Overlay already installed at ${overlayDir.absolutePath}, skipping")
                        // Defensive: ensure the registry pointer matches the on-disk overlay.
                        // The overlay can be physically present from a previous install while
                        // user.reg has been wiped (e.g. by a container reset). EOS SDK uses the
                        // registry pointer to load the overlay. Skip the write if it's already
                        // current to avoid touching user.reg on every launch.
                        ensureRegistryPath(container, OVERLAY_WIN_PATH)
                        return@withContext Result.success(Unit)
                    }

                    Timber.tag(TAG).i("Starting EOS overlay install into container ${container.id}")

                    val manifestResult =
                        epicManager.fetchManifestFromEpic(
                            context = context,
                            namespace = OVERLAY_NAMESPACE,
                            catalogItemId = OVERLAY_CATALOG_ITEM_ID,
                            appName = OVERLAY_APP_NAME,
                        )
                    if (manifestResult.isFailure) {
                        return@withContext Result.failure(
                            manifestResult.exceptionOrNull()
                                ?: Exception("Failed to fetch EOS overlay manifest"),
                        )
                    }

                    val manifest = manifestResult.getOrNull()!!
                    overlayDir.mkdirs()

                    Timber.tag(TAG).i("Downloading overlay files to ${overlayDir.absolutePath}")
                    val downloadResult =
                        epicDownloadManager.downloadOverlay(
                            manifestResult = manifest,
                            installPath = overlayDir.absolutePath,
                            onProgress = onProgress,
                        )
                    if (downloadResult.isFailure) {
                        return@withContext Result.failure(
                            downloadResult.exceptionOrNull()
                                ?: Exception("Failed to download EOS overlay files"),
                        )
                    }

                    // Update the registry to point to the overlay path. The EOS SDK in games
                    // reads this to locate the overlay; if the overlay DLLs fail to load under
                    // Wine the SDK degrades gracefully (no HUD) while keeping auth/online
                    // features working.
                    ensureRegistryPath(container, OVERLAY_WIN_PATH)

                    Timber.tag(TAG).i("EOS overlay installation complete")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "EOS overlay installation failed")
                    Result.failure(e)
                }
            }

        /**
         * Returns true if the overlay marker file exists in [container]'s Wine prefix.
         */
        fun isOverlayInstalled(container: Container): Boolean =
            File(overlayDir(container), OVERLAY_MARKER_FILE).exists()

        /**
         * Remove all overlay files from [container] and clear the registry path.
         */
        suspend fun removeOverlay(container: Container): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    val dir = overlayDir(container)
                    if (dir.exists()) {
                        dir.deleteRecursively()
                        Timber.tag(TAG).i("Removed overlay directory: ${dir.absolutePath}")
                    }
                    removeRegistryPath(container)
                    Result.success(Unit)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to remove EOS overlay")
                    Result.failure(e)
                }
            }

        // ─────────────────────────────────────────────────────────────────────────────
        // Internal helpers
        // ─────────────────────────────────────────────────────────────────────────────

        /**
         * Returns the overlay install directory inside [container]'s Wine prefix.
         */
        private fun overlayDir(container: Container): File =
            File(container.rootDir, ".wine/$OVERLAY_WINE_RELATIVE_PATH")

        /**
         * Ensure HKCU\SOFTWARE\Epic Games\EOS\OverlayPath in [container]'s Wine user.reg points
         * to [winPath]. No-op if the value is already correct.
         *
         * Mirrors `add_registry_entries` in legendary/lfs/eos.py for the Wine/prefix code path
         * (HKCU only; Vulkan implicit layers are not set because they do not work in Wine).
         *
         * Skips when `user.reg` does not yet exist: Wine generates it on first prefix boot, and
         * writing to a non-existent file from this editor produces a header-less registry file
         * which Wine treats as malformed and discards. The next launch (post-wineboot) will
         * re-trigger this method via the already-installed branch and write the value safely.
         */
        private fun ensureRegistryPath(container: Container, winPath: String) {
            val userRegFile = File(container.rootDir, ".wine/user.reg")
            if (!userRegFile.exists()) {
                Timber.tag(TAG).w(
                    "user.reg missing at ${userRegFile.absolutePath}; deferring overlay registry write " +
                        "until Wine bootstraps the prefix",
                )
                return
            }
            WineRegistryEditor(userRegFile).use { editor ->
                val current = editor.getStringValue(EOS_OVERLAY_REG_KEY, EOS_OVERLAY_REG_VALUE, null)
                if (current == winPath) {
                    return
                }
                editor.setCreateKeyIfNotExist(true)
                editor.setStringValue(EOS_OVERLAY_REG_KEY, EOS_OVERLAY_REG_VALUE, winPath)
            }
            Timber.tag(TAG).d(
                "Registry updated: HKCU\\$EOS_OVERLAY_REG_KEY\\$EOS_OVERLAY_REG_VALUE = $winPath",
            )
        }

        /**
         * Clear the EOS overlay path from the Wine user.reg by removing the registry value (not
         * just blanking it — some EOS SDK builds treat empty differently from missing).
         *
         * Mirrors `remove_registry_entries` in legendary/lfs/eos.py.
         */
        private fun removeRegistryPath(container: Container) {
            val userRegFile = File(container.rootDir, ".wine/user.reg")
            if (!userRegFile.exists()) return
            WineRegistryEditor(userRegFile).use { editor ->
                editor.removeValue(EOS_OVERLAY_REG_KEY, EOS_OVERLAY_REG_VALUE)
            }
            Timber.tag(TAG).d("Removed HKCU\\$EOS_OVERLAY_REG_KEY\\$EOS_OVERLAY_REG_VALUE")
        }
    }
