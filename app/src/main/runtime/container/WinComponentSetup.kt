package com.winlator.cmod.runtime.container

import android.content.Context
import android.util.Log
import com.winlator.cmod.runtime.display.environment.ImageFs
import com.winlator.cmod.runtime.wine.WineInfo
import com.winlator.cmod.runtime.wine.WineUtils
import com.winlator.cmod.shared.io.FileUtils
import com.winlator.cmod.shared.io.TarCompressorUtils
import com.winlator.cmod.shared.util.KeyValueSet
import com.winlator.cmod.shared.util.OnExtractFileListener
import org.json.JSONException
import org.json.JSONObject
import java.io.File

object WinComponentSetup {
    private const val TAG = "WinComponentSetup"
    private const val DIRECT3D_COMPONENT = "direct3d"

    /*
     * Applies optional Windows component archives to the Wine prefix. Keep the
     * Wine and ARM64EC paths explicit here because mixing their DLL layouts can
     * leave system32 with the wrong architecture.
     *
     * Wine/x86_64 path:
     *   Native WinComponents are extracted directly into drive_c/windows.
     *
     * ARM64EC path:
     *   system32 is ARM64/ARM64EC, so most native WinComponent archives are
     *   skipped and Wine's own arch-correct DLLs are restored. Direct3D helper
     *   DLLs are the explicit exception because x64 D3D apps load them.
     */
    @JvmStatic
    fun applyWinComponents(
        context: Context,
        imageFs: ImageFs,
        wineInfo: WineInfo,
        container: Container,
        wincomponents: String,
        previousWincomponents: String,
        firstTimeBoot: Boolean,
        onExtractFileListener: OnExtractFileListener?,
    ) {
        Log.d(TAG, "Applying WinComponents")

        val rootDir = imageFs.rootDir
        val windowsDir = File(rootDir, ImageFs.WINEPREFIX + "/drive_c/windows")
        val systemRegFile = File(rootDir, ImageFs.WINEPREFIX + "/system.reg")

        try {
            val wincomponentsStr = FileUtils.readString(context, "wincomponents/wincomponents.json")
            val wincomponentsJson = JSONObject(wincomponentsStr ?: "{}")
            val dlls = ArrayList<String>()
            val oldWinComponentsIter =
                KeyValueSet(previousWincomponents.ifEmpty { Container.FALLBACK_WINCOMPONENTS }).iterator()

            val isArm64EC = wineInfo.isArm64EC

            for (wincomponent in KeyValueSet(wincomponents)) {
                val identifier = wincomponent[0]
                val useNative = wincomponent[1] == "1"
                val oldValue = oldWinComponentsIter.next()[1]
                if (wincomponent[1] == oldValue && !firstTimeBoot) continue

                val shouldExtractArchive =
                    shouldExtractNativeWinComponentArchive(identifier, useNative, isArm64EC)

                if (shouldExtractArchive) {
                    // Wine/x86_64 extracts all selected archives. ARM64EC only extracts
                    // archives that are known to be safe for x64 app-side loading.
                    extractNativeWinComponent(context, identifier, windowsDir, onExtractFileListener)
                } else {
                    // ARM64EC lands here for skipped native archives, and disabled
                    // components land here on both Wine paths. Restore Wine's own DLLs.
                    dlls.addAll(wineDllsForComponentRestore(wincomponentsJson, identifier))
                }

                Log.d(
                    TAG,
                    "Setting wincomponent $identifier to $useNative" +
                        if (useNative && isArm64EC && !shouldExtractArchive) {
                            " (arm64ec: tzst skipped, restoring arch-correct DLLs)"
                        } else {
                            ""
                        },
                )
                WineUtils.overrideWinComponentDlls(context, container, identifier, useNative)
                WineUtils.setWinComponentRegistryKeys(systemRegFile, identifier, useNative, context)
            }

            if (dlls.isNotEmpty()) restoreWineBuiltinDllFiles(imageFs, wineInfo, *dlls.toTypedArray())
        } catch (e: JSONException) {
            Log.w(TAG, "Failed to parse WinComponents metadata", e)
        }
    }

    @JvmStatic
    fun restoreWineBuiltinDllFiles(
        imageFs: ImageFs,
        wineInfo: WineInfo,
        vararg dlls: String,
    ) {
        val windowsDir = File(imageFs.rootDir, ImageFs.WINEPREFIX + "/drive_c/windows")

        // Pick the Wine DLL directory that matches system32 for this prefix:
        // x86_64 Wine uses x86_64-windows, ARM64EC Wine uses aarch64-windows.
        val wineSystem32Dlls = wineSystem32DllDir(imageFs, wineInfo)
        val wineSyswow64Dlls = File(imageFs.winePath + "/lib/wine/i386-windows")

        for (dll in dlls) {
            restoreOneWineDll(File(wineSystem32Dlls, dll), File(windowsDir, "system32/$dll"))
            restoreOneWineDll(File(wineSyswow64Dlls, dll), File(windowsDir, "syswow64/$dll"))
        }
    }

    private fun shouldExtractNativeWinComponentArchive(
        identifier: String,
        useNative: Boolean,
        isArm64EC: Boolean,
    ): Boolean = useNative && (!isArm64EC || identifier == DIRECT3D_COMPONENT)

    private fun extractNativeWinComponent(
        context: Context,
        identifier: String,
        windowsDir: File,
        onExtractFileListener: OnExtractFileListener?,
    ) {
        TarCompressorUtils.extract(
            TarCompressorUtils.Type.ZSTD,
            context,
            "wincomponents/$identifier.tzst",
            windowsDir,
            onExtractFileListener,
        )
    }

    private fun wineDllsForComponentRestore(
        wincomponentsJson: JSONObject,
        identifier: String,
    ): List<String> {
        val dlnames = wincomponentsJson.getJSONArray(identifier)
        val dlls = ArrayList<String>(dlnames.length())
        for (i in 0 until dlnames.length()) {
            val dlname = dlnames.getString(i)
            dlls.add(if (dlname.endsWith(".exe")) dlname else "$dlname.dll")
        }
        return dlls
    }

    private fun wineSystem32DllDir(
        imageFs: ImageFs,
        wineInfo: WineInfo,
    ): File =
        // ARM64EC Wine keeps ARM64/ARM64EC DLLs in aarch64-windows; regular
        // x86_64 Wine keeps x64 DLLs in x86_64-windows.
        if (wineInfo.isArm64EC) {
            File(imageFs.winePath + "/lib/wine/aarch64-windows")
        } else {
            File(imageFs.winePath + "/lib/wine/x86_64-windows")
        }

    private fun restoreOneWineDll(
        srcFile: File,
        dstFile: File,
    ) {
        if (srcFile.exists()) {
            if (!FileUtils.copy(srcFile, dstFile)) {
                Log.w(TAG, "restoreWineBuiltinDllFiles: copy failed $srcFile -> $dstFile")
            }
            return
        }
        if (dstFile.exists()) {
            if (dstFile.delete()) {
                Log.w(TAG, "restoreWineBuiltinDllFiles: no source for $srcFile, deleted stale $dstFile")
            } else {
                Log.e(TAG, "restoreWineBuiltinDllFiles: no source for $srcFile and failed to delete stale $dstFile")
            }
        }
    }
}
