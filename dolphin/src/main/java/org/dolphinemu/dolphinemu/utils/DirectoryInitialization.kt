package org.dolphinemu.dolphinemu.utils

import android.content.Context
import java.io.File

object DirectoryInitialization {
    @JvmStatic
    external fun SetSysDirectory(path: String)

    @JvmStatic
    external fun SetGpuDriverDirectories(path: String, libPath: String)

    fun userDir(context: Context): File = File(context.filesDir, "dolphin-embed/User")

    @Volatile
    private var sysDirSet = false

    fun ensureDirectories(context: Context): File {
        val sysDir = File(context.filesDir, "dolphin-embed/Sys")
        val marker = File(sysDir, ".wn_sys_ready")
        if (!marker.isFile) {
            val bundled = File(context.filesDir, "retro/bundle/data/dolphin-emu/Sys")
            val copied = runCatching { bundled.copyRecursively(sysDir, overwrite = true) }.getOrDefault(false)
            if (copied) marker.writeText("1")
        }
        if (!sysDirSet) {
            sysDirSet = true
            SetSysDirectory(sysDir.path + File.separator)
            val driverRoot = File(context.filesDir, "dolphin-embed/GpuDrivers")
            File(driverRoot, "Extracted").mkdirs()
            File(driverRoot, "Tmp").mkdirs()
            File(driverRoot, "FileRedirect").mkdirs()
            SetGpuDriverDirectories(driverRoot.path, context.applicationInfo.nativeLibraryDir)
        }
        val user = userDir(context)
        File(user, "Config").mkdirs()
        return user
    }

    fun writeBaseConfig(userDir: File) {
        val ini = File(userDir, "Config/Dolphin.ini")
        if (!ini.isFile) {
            ini.writeText(
                """
                [Core]
                GFXBackend = Vulkan
                """.trimIndent() + "\n",
            )
        }
        File(userDir, "Config/Logger.ini").writeText(
            """
            [Options]
            Verbosity = 2
            WriteToConsole = True
            """.trimIndent() + "\n",
        )
    }
}
