package com.winlator.cmod.feature.lsfg

import android.content.Context
import android.util.Log
import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.display.environment.ImageFs
import com.winlator.cmod.runtime.wine.EnvVars
import com.winlator.cmod.shared.io.FileUtils
import java.io.File
import java.util.Locale

/**
 * Installs and configures the Android lsfg-vk implicit layer.
 *
 * This follows the working GameNative layout/protocol instead of upstream
 * lsfg-vk v2:
 * - layer .so: active-container/.local/lib/liblsfg-vk-layer.so
 * - manifest: active-container/.local/share/vulkan/implicit_layer.d/VkLayer_LS_frame_generation.json
 * - config: active-container/.config/lsfg-vk/conf.toml
 * - DLL: active-container/.local/share/lsfg-vk/Lossless.dll
 *
 * The Android fork consumes LSFG_CONFIG and LSFG_PROCESS, with a version 1
 * config containing a [[game]] entry. A disabled session writes multiplier=1
 * so the layer can remain loadable and hot-reload when the drawer toggle changes.
 */
object LsfgVkManager {
    private const val TAG = "LsfgVkManager"

    private const val ASSET_DIR = "lsfg_vk/android_arm64_v8a"
    private const val LAYER_SO = "liblsfg-vk-layer.so"
    private const val LAYER_MANIFEST = "VkLayer_LS_frame_generation.json"
    private const val VERSION_FILENAME = ".lsfg_vk_runtime_version"
    // Bump this whenever the bundled .so changes so existing containers re-copy.
    // Switched from prebuilt asset .so to gradle-built nativeLibraryDir source.
    private const val RUNTIME_VERSION = "v1.0.3-android-arm64-v8a-nativelib"
    private const val FALLBACK_PROCESS_EXE_IDENTIFIER = "winnative-lsfg"

    private const val CONFIG_RELATIVE_PATH = ".config/lsfg-vk/conf.toml"
    private const val LIB_RELATIVE_DIR = ".local/lib"
    private const val LAYER_RELATIVE_DIR = ".local/share/vulkan/implicit_layer.d"
    private const val DLL_RELATIVE_DIR = ".local/share/lsfg-vk"

    private const val LEGACY_LAYER_MANIFEST = "usr/share/vulkan/implicit_layer.d/VkLayer_LSFGVK_frame_generation.json"
    private const val LEGACY_LAYER_SO = "usr/lib/liblsfg-vk-layer.so"
    private const val ROOT_LOCAL_LAYER_MANIFEST = ".local/share/vulkan/implicit_layer.d/VkLayer_LS_frame_generation.json"
    private const val ROOT_LOCAL_LAYER_SO = ".local/lib/liblsfg-vk-layer.so"

    // Container extras
    const val EXTRA_ENABLED = "lsfgEnabled"
    const val EXTRA_MULTIPLIER = "lsfgMultiplier"
    const val EXTRA_FLOW_SCALE = "lsfgFlowScale"
    const val EXTRA_PERFORMANCE_MODE = "lsfgPerformanceMode"
    private const val EXTRA_PROCESS = "lsfgProcess"

    const val DEFAULT_MULTIPLIER = 2
    const val DEFAULT_FLOW_SCALE = 0.8f
    const val DEFAULT_PERFORMANCE_MODE = true

    fun getEnabled(container: Container): Boolean =
        parseBool(container.getExtra(EXTRA_ENABLED, "false"))

    fun getMultiplier(container: Container): Int {
        val raw = container.getExtra(EXTRA_MULTIPLIER, DEFAULT_MULTIPLIER.toString()).toIntOrNull()
            ?: DEFAULT_MULTIPLIER
        return if (raw == 1) 1 else raw.coerceIn(2, 4)
    }

    fun getFlowScale(container: Container): Float =
        container.getExtra(EXTRA_FLOW_SCALE, DEFAULT_FLOW_SCALE.toString())
            .toFloatOrNull()
            ?.coerceIn(0.25f, 1.0f)
            ?: DEFAULT_FLOW_SCALE

    fun getPerformanceMode(container: Container): Boolean =
        parseBool(container.getExtra(EXTRA_PERFORMANCE_MODE, DEFAULT_PERFORMANCE_MODE.toString()))

    fun isActive(context: Context, container: Container): Boolean =
        LsfgVerification.isVerified(context) &&
            getEnabled(container) &&
            getMultiplier(container) >= 2

    private fun localLibDir(rootDir: File) = File(rootDir, LIB_RELATIVE_DIR)
    private fun layerDir(rootDir: File) = File(rootDir, LAYER_RELATIVE_DIR)
    private fun dllDir(rootDir: File) = File(rootDir, DLL_RELATIVE_DIR)
    private fun configFile(rootDir: File) = File(rootDir, CONFIG_RELATIVE_PATH)
    private fun layerSoFile(rootDir: File) = File(localLibDir(rootDir), LAYER_SO)
    private fun manifestFile(rootDir: File) = File(layerDir(rootDir), LAYER_MANIFEST)
    private fun versionFile(rootDir: File) = File(layerDir(rootDir), VERSION_FILENAME)
    private fun installedDllFile(rootDir: File) = File(dllDir(rootDir), "Lossless.dll")

    fun ensureRuntimeInstalled(
        context: Context,
        container: Container,
        imageFs: ImageFs,
    ): Boolean {
        if (!LsfgVerification.isVerified(context)) return false

        val rootDir = container.rootDir
        val soFile = layerSoFile(rootDir)
        val manifest = manifestFile(rootDir)
        val version = versionFile(rootDir)
        val dllDest = installedDllFile(rootDir)

        try {
            removeLegacyRuntime(rootDir)

            localLibDir(rootDir).mkdirs()
            layerDir(rootDir).mkdirs()
            dllDir(rootDir).mkdirs()
            configFile(rootDir).parentFile?.mkdirs()

            val installedVersion = version.takeIf { it.isFile }?.readText()?.trim().orEmpty()
            val needsRuntimeCopy = installedVersion != RUNTIME_VERSION ||
                !soFile.isFile ||
                !manifest.isFile

            if (needsRuntimeCopy) {
                if (!copyPackagedLayer(context, soFile)) {
                    Log.e(TAG, "Packaged liblsfg-vk-layer.so not found in nativeLibraryDir")
                    return false
                }
                extractAssetIfChanged(context, "$ASSET_DIR/$LAYER_MANIFEST", manifest)
                FileUtils.writeString(version, RUNTIME_VERSION)
            }

            FileUtils.chmod(soFile, 0b111101101) // 0755
            FileUtils.chmod(manifest, 0b110100100) // 0644
            FileUtils.chmod(version, 0b110100100) // 0644

            val dllSrc = LsfgVerification.dllFile(context)
            if (!dllSrc.isFile) {
                Log.w(TAG, "Verified flag set but DLL file missing at ${dllSrc.absolutePath}")
                return false
            }
            if (!dllDest.isFile || dllDest.length() != dllSrc.length()) {
                FileUtils.copy(dllSrc, dllDest)
            }
            FileUtils.chmod(dllDest, 0b110100100) // 0644

            return soFile.isFile && manifest.isFile && dllDest.isFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install LSFG runtime", e)
            return false
        }
    }

    fun writeConfig(context: Context, container: Container, imageFs: ImageFs) {
        writeConfig(context, container, imageFs, null)
    }

    fun writeConfig(context: Context, container: Container, imageFs: ImageFs, processIdentifier: String?) {
        if (!LsfgVerification.isVerified(context)) return

        val rootDir = container.rootDir
        val dllPath = installedDllFile(rootDir).absolutePath
        val enabled = getEnabled(container) && installedDllFile(rootDir).isFile
        val effectiveMultiplier = if (enabled) getMultiplier(container).coerceIn(2, 4) else 1
        val effectivePerfMode = enabled && getPerformanceMode(container)
        val effectiveProcess = processIdentifier(container, processIdentifier)
        rememberProcessIdentifier(container, effectiveProcess)

        val toml = buildConfigToml(
            dllPath = dllPath,
            processIdentifier = effectiveProcess,
            multiplier = effectiveMultiplier,
            flowScale = getFlowScale(container),
            performanceMode = effectivePerfMode,
        )

        val config = configFile(rootDir)
        config.parentFile?.mkdirs()
        if (FileUtils.writeString(config, toml)) {
            FileUtils.chmod(config, 0b110100100) // 0644
            config.setLastModified(System.currentTimeMillis())
        }
    }

    fun applyLaunchEnv(
        context: Context,
        container: Container,
        imageFs: ImageFs,
        envVars: EnvVars,
    ) {
        applyLaunchEnv(context, container, imageFs, envVars, null)
    }

    fun applyLaunchEnv(
        context: Context,
        container: Container,
        imageFs: ImageFs,
        envVars: EnvVars,
        processIdentifier: String?,
    ) {
        clearLsfgEnv(envVars)

        if (!LsfgVerification.isVerified(context) || !isRuntimeInstalled(container) || !getEnabled(container)) {
            disableLayerInContainer(container.rootDir, envVars)
            return
        }

        val rootDir = container.rootDir
        val effectiveProcess = processIdentifier(container, processIdentifier)
        val lsfgTmpDir = dllDir(rootDir)

        envVars.put("LSFG_CONFIG", configFile(rootDir).absolutePath)
        envVars.put("LSFG_PROCESS", effectiveProcess)
        envVars.put("LSFG_PROCESS_EXE", effectiveProcess)
        envVars.put("LSFG_LAST_PATH", File(lsfgTmpDir, "lsfg-vk_last").absolutePath)
        envVars.put("LSFG_TMP_DIR", lsfgTmpDir.absolutePath)
        envVars.put("LSFG_MULTIPLIER", getMultiplier(container).coerceIn(2, 4).toString())
        envVars.put("LSFG_FLOW_SCALE", String.format(Locale.US, "%.2f", getFlowScale(container)))
        envVars.put("LSFG_PERFORMANCE_MODE", if (getPerformanceMode(container)) "1" else "0")
        envVars.put("LSFG_HDR_MODE", "0")
        envVars.put("LSFG_EXPERIMENTAL_PRESENT_MODE", "fifo")
        envVars.put("LSFG_DLL_PATH", installedDllFile(rootDir).absolutePath)
        envVars.put("LSFG_DLL_PATH_UNIX", installedDllFile(rootDir).absolutePath)
        prependPathEnv(envVars, "VK_LAYER_PATH", layerDir(rootDir).absolutePath)
        prependPathEnv(envVars, "VK_IMPLICIT_LAYER_PATH", layerDir(rootDir).absolutePath)
        rememberProcessIdentifier(container, effectiveProcess)
    }

    private fun buildConfigToml(
        dllPath: String,
        processIdentifier: String,
        multiplier: Int,
        flowScale: Float,
        performanceMode: Boolean,
    ): String = buildString {
        append("version = 1\n\n")
        append("[global]\n")
        append("dll = ").append(tomlString(dllPath)).append("\n")
        append("no_fp16 = false\n\n")
        append("[[game]]\n")
        append("exe = ").append(tomlString(processIdentifier)).append("\n")
        append("multiplier = ").append(multiplier).append("\n")
        append("flow_scale = ").append(String.format(Locale.US, "%.2f", flowScale.coerceIn(0.25f, 1.0f))).append("\n")
        append("performance_mode = ").append(performanceMode).append("\n")
        append("hdr_mode = false\n")
        append("experimental_present_mode = ").append(tomlString("fifo")).append("\n")
    }

    private fun tomlString(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(ch)
            }
        }
        append('"')
    }

    private fun isRuntimeInstalled(container: Container): Boolean {
        val rootDir = container.rootDir
        return layerSoFile(rootDir).isFile &&
            manifestFile(rootDir).isFile &&
            installedDllFile(rootDir).isFile &&
            configFile(rootDir).isFile
    }

    private fun clearLsfgEnv(envVars: EnvVars) {
        envVars.remove("LSFG_CONFIG")
        envVars.remove("LSFG_PROCESS")
        envVars.remove("LSFG_PROCESS_EXE")
        envVars.remove("LSFG_LAST_PATH")
        envVars.remove("LSFG_TMP_DIR")
        envVars.remove("LSFG_MULTIPLIER")
        envVars.remove("LSFG_FLOW_SCALE")
        envVars.remove("LSFG_PERFORMANCE_MODE")
        envVars.remove("LSFG_HDR_MODE")
        envVars.remove("LSFG_EXPERIMENTAL_PRESENT_MODE")
        envVars.remove("LSFG_DLL_PATH")
        envVars.remove("LSFG_DLL_PATH_UNIX")
        envVars.remove("LSFGVK_CONFIG")
        envVars.remove("LSFGVK_PROFILE")
        envVars.remove("DISABLE_LSFG")
        envVars.remove("DISABLE_LSFGVK")
    }

    fun resolveProcessIdentifier(guestExecutable: String?): String {
        if (guestExecutable.isNullOrBlank()) return FALLBACK_PROCESS_EXE_IDENTIFIER

        val candidates = mutableListOf<String>()
        val quotedExe = Regex("\"([^\"]+?\\.exe)\"", RegexOption.IGNORE_CASE)
        quotedExe.findAll(guestExecutable).forEach { match ->
            candidates += match.groupValues[1].toExeName()
        }

        val bareExe = Regex("([^\\s\"]+\\.exe)", RegexOption.IGNORE_CASE)
        bareExe.findAll(guestExecutable).forEach { match ->
            candidates += match.groupValues[1].toExeName()
        }

        return candidates
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .lastOrNull { !it.isLauncherProcessName() }
            ?: candidates.lastOrNull { it.isNotEmpty() }
            ?: FALLBACK_PROCESS_EXE_IDENTIFIER
    }

    private fun processIdentifier(container: Container, processIdentifier: String?): String {
        val resolved = resolveProcessIdentifier(processIdentifier)
        if (resolved != FALLBACK_PROCESS_EXE_IDENTIFIER) return resolved
        return container.getExtra(EXTRA_PROCESS, FALLBACK_PROCESS_EXE_IDENTIFIER)
            .takeIf { it.isNotBlank() }
            ?: FALLBACK_PROCESS_EXE_IDENTIFIER
    }

    private fun rememberProcessIdentifier(container: Container, processIdentifier: String) {
        if (processIdentifier.isBlank()) return
        if (container.getExtra(EXTRA_PROCESS, "") == processIdentifier) return
        container.putExtra(EXTRA_PROCESS, processIdentifier)
        container.saveData()
    }

    private fun String.toExeName(): String =
        substringAfterLast('\\').substringAfterLast('/').trim()

    private fun String.isLauncherProcessName(): Boolean {
        return when (lowercase(Locale.US)) {
            "wine.exe",
            "wine64.exe",
            "explorer.exe",
            "winhandler.exe",
            "wineboot.exe",
            "wineserver.exe" -> true
            else -> false
        }
    }

    private fun disableLayerInContainer(rootDir: File, envVars: EnvVars) {
        manifestFile(rootDir).delete()
        removeLegacyRuntime(rootDir)
        envVars.put("DISABLE_LSFG", "1")
        envVars.put("DISABLE_LSFGVK", "1")
    }

    private fun removeLegacyRuntime(rootDir: File) {
        File(rootDir, LEGACY_LAYER_MANIFEST).delete()
        File(rootDir, LEGACY_LAYER_SO).delete()
        File(rootDir, ROOT_LOCAL_LAYER_MANIFEST).delete()
        File(rootDir, ROOT_LOCAL_LAYER_SO).delete()
    }

    private fun prependPathEnv(envVars: EnvVars, key: String, path: String) {
        val current = envVars.get(key)
        if (current.isNullOrBlank()) {
            envVars.put(key, path)
            return
        }
        if (current.split(':').contains(path)) return
        envVars.put(key, "$path:$current")
    }

    private fun extractAssetIfChanged(context: Context, assetPath: String, dst: File) {
        val tmp = File.createTempFile(dst.name, ".tmp", dst.parentFile)
        try {
            context.assets.open(assetPath).use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            if (dst.isFile && dst.length() == tmp.length() && dst.readBytes().contentEquals(tmp.readBytes())) {
                return
            }
            tmp.copyTo(dst, overwrite = true)
        } finally {
            tmp.delete()
        }
    }

    private fun copyPackagedLayer(context: Context, dst: File): Boolean {
        val src = File(context.applicationInfo.nativeLibraryDir, LAYER_SO)
        if (!src.isFile) return false
        if (dst.isFile && dst.length() == src.length() && dst.readBytes().contentEquals(src.readBytes())) {
            return true
        }
        FileUtils.copy(src, dst)
        return dst.isFile
    }

    private fun parseBool(value: String): Boolean =
        value.equals("true", ignoreCase = true) || value == "1"
}
