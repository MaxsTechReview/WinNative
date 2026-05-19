package com.winlator.cmod.feature.stores.steam.wnsteam

import android.content.Context
import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.display.environment.ImageFs
import com.winlator.cmod.shared.io.TarCompressorUtils
import timber.log.Timber
import java.io.File

/**
 * Stages the bundled Wine/Steam IPC binaries into the right places on disk
 * the first time we launch a Steam game (or whenever the Wine version
 * changes). Idempotent: each extract is gated on a sentinel file so we
 * don't blow CPU re-decompressing on every launch.
 *
 * Assets (shipped at app/src/main/assets/wnsteam/):
 *
 *   steam-androidarm64.tzst        Linux/Android side of the Steam Runtime.
 *                                  Extracts into <imageFs.rootDir>, dropping
 *                                  libsteamclient.so + libsteamnetworkingsockets.so
 *                                  + steamservice.so + libtier0_s.so +
 *                                  libvstdlib_s.so under usr/lib/. The JNI
 *                                  bootstrap (WnSteamBootstrap) dlopens
 *                                  libsteamclient.so from this path.
 *
 *   lsteamclient-arm64ec.tzst      Wine-side bridge for ARM64EC Proton.
 *                                  aarch64-windows/lsteamclient.dll  -> drive_c/windows/system32
 *                                  i386-windows/lsteamclient.dll     -> drive_c/windows/syswow64
 *                                  aarch64-unix/lsteamclient.so      -> wine lib (Wine loader picks it up automatically)
 *
 *   lsteamclient-x86_64.tzst       Same shape for x86_64 Proton:
 *                                  x86_64-windows/lsteamclient.dll   -> drive_c/windows/system32
 *                                  i386-windows/lsteamclient.dll     -> drive_c/windows/syswow64
 *                                  x86_64-unix/lsteamclient.so       -> wine lib
 *
 * Container.wineVersion is sniffed to pick the right lsteamclient archive
 * (matches the convention: substring "arm64ec" → ARM64EC build, otherwise
 * x86_64). Containers with a non-Proton Wine variant get neither (the
 * caller should check [isSupportedFor] before calling).
 */
object WnSteamAssetsInstaller {

    private const val TAG = "WnSteamAssets"

    private const val ASSET_DIR     = "wnsteam"
    private const val STEAM_TZST    = "steam-androidarm64.tzst"
    private const val LSC_ARM64EC   = "lsteamclient-arm64ec.tzst"
    private const val LSC_X86_64    = "lsteamclient-x86_64.tzst"

    /** True if we have any bundled IPC binaries that apply to this container. */
    fun isSupportedFor(container: Container): Boolean =
        lsteamclientArchive(container) != null

    /**
     * Run the install pass for [container]. Safe to call on every launch;
     * idempotent via the sentinel files written under [stamp].
     */
    fun install(context: Context, container: Container): Boolean {
        val imageFs = ImageFs.find(context)

        // 1) Linux/Android side — usr/lib/ libsteamclient.so + friends.
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

        // 2) Wine-side bridge (arm64ec or x86_64 lsteamclient.dll).
        val lscArchive = lsteamclientArchive(container)
        if (lscArchive == null) {
            Timber.tag(TAG).w(
                "No lsteamclient archive for wineVersion=%s; skipping Wine bridge install",
                container.wineVersion,
            )
            return true   // .so side is fine; just no Wine bridge
        }
        val wineStamp = File(imageFs.libDir, ".wnsteam-${lscArchive}.stamp")
        if (wineStamp.exists()) return true

        // Stage into a per-arch tmp dir, then copy the .dlls into the prefix
        // and (optionally) the .so onto the Wine lib path.
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

        // The .so side is dropped on the Wine lib dir so the loader picks
        // it up. Path resolution mirrors winlator's existing convention:
        // {imageFs.libDir}/wine/{arch}/lsteamclient.so.
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

    /**
     * Wipe stamps + installed files so the next [install] re-extracts.
     * Useful when the container's wine version changes (caller is expected
     * to detect this).
     */
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
