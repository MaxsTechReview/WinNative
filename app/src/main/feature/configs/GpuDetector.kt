package com.winlator.cmod.feature.configs

import android.os.Build
import timber.log.Timber
import java.io.File

/**
 * Detects the device's GPU model without needing a live OpenGL ES context.
 *
 * The previous implementation relied on `GLES20.glGetString(GL_RENDERER)`, which
 * returns null whenever the caller isn't inside a thread with a current GLES context
 * (e.g., the share-to-community path from the settings dialog). That left every
 * uploaded `config_json.gpu_renderer` empty, and the "My GPU" filter on the Best
 * Configs screen could never match anything. Sysfs + SoC mapping is context-free and
 * works on every modern Android device.
 *
 * Family fallback: when the exact model can't be resolved, callers get a family
 * label ("Adreno", "Mali", "Xclipse", ...) so filtering still groups roughly-correct
 * GPUs.
 */
object GpuDetector {
    private const val ADRENO_MODEL_PATH = "/sys/class/kgsl/kgsl-3d0/gpu_model"
    private const val ADRENO_LEGACY_PATH = "/sys/kernel/gpu/gpu_model"
    private const val MALI_SYSFS_ROOT = "/sys/class/misc/mali0"
    private const val POWERVR_DEBUGFS = "/sys/kernel/debug/pvr"
    private const val DEVFREQ_ROOT = "/sys/class/devfreq"

    /** Cached detection — sysfs reads are cheap but called from multiple call sites. */
    @Volatile private var cachedModel: String? = null

    fun detect(): String {
        cachedModel?.let { return it }
        val result = runCatching { detectInner() }.getOrElse {
            Timber.tag(TAG).w(it, "GpuDetector failed; defaulting to Unknown")
            "Unknown"
        }
        cachedModel = result
        return result
    }

    /**
     * Family-level token used as a coarse fallback filter on the Best Configs screen
     * when no row matches the exact model.
     */
    fun family(model: String): String = when {
        model.equals("Unknown", ignoreCase = true) -> ""
        model.contains("adreno", ignoreCase = true) -> "Adreno"
        model.contains("mali", ignoreCase = true) -> "Mali"
        model.contains("xclipse", ignoreCase = true) -> "Xclipse"
        model.contains("immortalis", ignoreCase = true) -> "Immortalis"
        model.contains("powervr", ignoreCase = true) -> "PowerVR"
        else -> ""
    }

    private fun detectInner(): String {
        // Adreno — Qualcomm KGSL driver exposes the model directly.
        readSysfsTrimmed(ADRENO_MODEL_PATH)?.let { raw ->
            return if (raw.contains("adreno", ignoreCase = true)) raw else "Adreno $raw"
        }
        readSysfsTrimmed(ADRENO_LEGACY_PATH)?.let { raw ->
            return if (raw.contains("adreno", ignoreCase = true)) raw else "Adreno $raw"
        }

        // Mali — sysfs node tells us it's Mali; the actual model comes from the SoC.
        if (File(MALI_SYSFS_ROOT).exists() || hasDevfreqMaliNode()) {
            return mapMaliBySoc()
        }

        // PowerVR — rare on modern Android phones.
        if (File(POWERVR_DEBUGFS).exists()) return "PowerVR"

        return "Unknown"
    }

    private fun hasDevfreqMaliNode(): Boolean = runCatching {
        File(DEVFREQ_ROOT).listFiles()?.any { it.name.contains("mali", ignoreCase = true) } == true
    }.getOrDefault(false)

    private fun readSysfsTrimmed(path: String): String? = runCatching {
        val f = File(path)
        if (!f.canRead()) return@runCatching null
        f.readText().trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

    /**
     * Maps SoC name to the GPU it ships with. Source: vendor specs as of 2025/2026.
     * Returns "Mali" (family-only) if the SoC isn't in the table — caller still gets
     * a useful family token.
     */
    private fun mapMaliBySoc(): String {
        val soc = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else "")
            .ifBlank { Build.HARDWARE.orEmpty() }
        val s = soc.lowercase()
        return when {
            // Google Tensor
            s.contains("tensor g4") || s.contains("gs401") -> "Mali-G715"
            s.contains("tensor g3") || s.contains("gs302") -> "Mali-G715"
            s.contains("tensor g2") || s.contains("gs201") -> "Mali-G710"
            s.contains("tensor") -> "Mali-G78"
            // Samsung Exynos
            s.contains("exynos 2400") -> "Xclipse 940"
            s.contains("exynos 2200") -> "Xclipse 920"
            s.contains("exynos 2100") -> "Mali-G78"
            s.contains("exynos 990") -> "Mali-G77"
            s.contains("exynos 9825") || s.contains("exynos 9820") -> "Mali-G76"
            // MediaTek Dimensity
            s.contains("dimensity 9400") -> "Immortalis-G925"
            s.contains("dimensity 9300") -> "Immortalis-G720"
            s.contains("dimensity 9200") -> "Immortalis-G715"
            s.contains("dimensity 9000") -> "Mali-G710"
            s.contains("dimensity 8300") -> "Mali-G615"
            s.contains("dimensity 8200") -> "Mali-G610"
            s.contains("dimensity 8100") -> "Mali-G610"
            s.contains("dimensity 1300") || s.contains("dimensity 1200") -> "Mali-G77"
            s.contains("dimensity 1100") || s.contains("dimensity 1000") -> "Mali-G77"
            s.contains("dimensity 920") || s.contains("dimensity 900") -> "Mali-G68"
            s.contains("dimensity 810") || s.contains("dimensity 800") -> "Mali-G57"
            else -> "Mali"
        }
    }

    private const val TAG = "GpuDetector"
}
