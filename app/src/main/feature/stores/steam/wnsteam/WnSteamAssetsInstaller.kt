package com.winlator.cmod.feature.stores.steam.wnsteam

import android.content.Context
import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.display.environment.ImageFs
import com.winlator.cmod.shared.io.TarCompressorUtils
import timber.log.Timber
import java.io.File

// Installs bundled Steam IPC assets into the imagefs and Wine prefix.
object WnSteamAssetsInstaller {

    private const val TAG = "WnSteamAssets"

    private const val ASSET_DIR     = "wnsteam"
    private const val STEAM_TZST    = "steam-androidarm64.tzst"
    private const val LSC_ARM64EC   = "lsteamclient-arm64ec.tzst"
    private const val LSC_X86_64    = "lsteamclient-x86_64.tzst"

    fun isSupportedFor(container: Container): Boolean =
        lsteamclientArchive(container) != null

    // Idempotent install pass for a Steam-supported container.
    fun install(context: Context, container: Container): Boolean {
        val imageFs = ImageFs.find(context)

        // Linux/Android side: usr/lib/libsteamclient.so and dependencies.
        val steamStamp = File(imageFs.libDir, ".wnsteam-androidarm64.stamp")
        if (!steamStamp.exists()) {
            Timber.tag(TAG).i("Installing $STEAM_TZST → ${imageFs.rootDir}")
            val ok = TarCompressorUtils.extract(
                TarCompressorUtils.Type.ZSTD,
                context,
                "$ASSET_DIR/$STEAM_TZST",
                imageFs.rootDir,
            )
            if (!ok) {
                Timber.tag(TAG).e("Failed to extract $STEAM_TZST")
                return false
            }
            steamStamp.writeText(STEAM_TZST)
        }

        // Wine-side bridge: arm64ec or x86_64 lsteamclient.dll.
        val lscArchive = lsteamclientArchive(container)
        if (lscArchive == null) {
            Timber.tag(TAG).w(
                "No lsteamclient archive for wineVersion=%s; skipping Wine bridge install",
                container.wineVersion,
            )
            return true
        }
        val wineStamp = File(imageFs.libDir, ".wnsteam-${lscArchive}.stamp")
        if (wineStamp.exists()) return true

        // Stage per-arch files, then copy DLLs into the prefix.
        val stagingRoot = File(imageFs.tmpDir, "wnsteam-stage").apply {
            deleteRecursively(); mkdirs()
        }
        Timber.tag(TAG).i("Installing $lscArchive → $stagingRoot")
        val staged = TarCompressorUtils.extract(
            TarCompressorUtils.Type.ZSTD,
            context,
            "$ASSET_DIR/$lscArchive",
            stagingRoot,
        )
        if (!staged) {
            Timber.tag(TAG).e("Failed to extract $lscArchive")
            return false
        }

        val isArm64ec = lscArchive == LSC_ARM64EC
        val winNative = if (isArm64ec) "aarch64-windows" else "x86_64-windows"
        val unixSide  = if (isArm64ec) "aarch64-unix"    else "x86_64-unix"

        val system32 = File(imageFs.wineprefix, "drive_c/windows/system32").apply { mkdirs() }
        val syswow64 = File(imageFs.wineprefix, "drive_c/windows/syswow64").apply { mkdirs() }

        val systemSrc = File(stagingRoot, "$winNative/lsteamclient.dll")
        val syswowSrc = File(stagingRoot, "i386-windows/lsteamclient.dll")
        if (!systemSrc.exists() || !syswowSrc.exists()) {
            Timber.tag(TAG).e("Staged lsteamclient.dlls missing in $stagingRoot")
            return false
        }
        systemSrc.copyTo(File(system32, "lsteamclient.dll"), overwrite = true)
        syswowSrc.copyTo(File(syswow64, "lsteamclient.dll"), overwrite = true)

        // Drop the Unix .so where Wine's loader already expects it.
        val unixSoSrc = File(stagingRoot, "$unixSide/lsteamclient.so")
        if (unixSoSrc.exists()) {
            val unixSoDest = File(imageFs.libDir, "wine/$unixSide/lsteamclient.so").apply {
                parentFile?.mkdirs()
            }
            unixSoSrc.copyTo(unixSoDest, overwrite = true)
        }

        stagingRoot.deleteRecursively()
        wineStamp.writeText(lscArchive)
        Timber.tag(TAG).i("Wine bridge installed (variant=$lscArchive)")
        return true
    }

    // Wipe stamps so the next [install] re-extracts assets.
    fun reset(context: Context) {
        val imageFs = ImageFs.find(context)
        listOf(
            File(imageFs.libDir, ".wnsteam-androidarm64.stamp"),
            File(imageFs.libDir, ".wnsteam-$LSC_ARM64EC.stamp"),
            File(imageFs.libDir, ".wnsteam-$LSC_X86_64.stamp"),
        ).forEach { if (it.exists()) it.delete() }
    }

    private fun lsteamclientArchive(container: Container): String? = when {
        container.wineVersion?.contains("arm64ec", ignoreCase = true) == true -> LSC_ARM64EC
        container.wineVersion?.contains("x86_64",  ignoreCase = true) == true -> LSC_X86_64
        else -> null
    }
}
