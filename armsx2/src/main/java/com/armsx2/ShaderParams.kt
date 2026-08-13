package com.armsx2

import android.content.Context
import android.util.Log
import kr.co.iefriends.pcsx2.NativeApp
import org.json.JSONArray
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class ShaderParam(
    val name: String,
    val description: String,
    val initial: Float,
    val minimum: Float,
    val maximum: Float,
    val step: Float,
) {
    val isAdjustable: Boolean get() = maximum > minimum

    val stepCount: Int
        get() {
            if (!isAdjustable) return 0
            val span = maximum - minimum
            val increment = if (step > 0f && step <= span) step else span / 100f
            return (span / increment).roundToInt().coerceIn(1, 10_000)
        }

    fun indexOf(value: Float): Int {
        if (!isAdjustable) return 0
        val frac = (value - minimum) / (maximum - minimum)
        return (frac * stepCount).roundToInt().coerceIn(0, stepCount)
    }

    fun valueAt(index: Int): Float {
        if (!isAdjustable) return minimum
        if (index >= stepCount) return maximum
        return minimum + (maximum - minimum) * (index.toFloat() / stepCount.toFloat())
    }

    fun format(value: Float): String {
        val span = maximum - minimum
        val increment = if (step > 0f && step <= span) step else span / 100f
        val decimals = when {
            increment >= 1f -> 0
            increment >= 0.1f -> 1
            increment >= 0.01f -> 2
            else -> 3
        }
        return String.format("%.${decimals}f", value)
    }

    fun isInitial(value: Float): Boolean {
        val span = maximum - minimum
        val increment = if (step > 0f && step <= span) step else span / 100f
        return abs(value - initial) < (increment / 2f).coerceAtLeast(1e-6f)
    }
}

object ShaderParams {

    private const val TAG = "ShaderParams"

    fun read(presetPath: String): List<ShaderParam> {
        if (presetPath.isBlank()) return emptyList()

        val json = try {
            NativeApp.shaderPresetParams(presetPath)
        } catch (t: Throwable) {
            Log.w(TAG, "shaderPresetParams threw for $presetPath", t)
            null
        } ?: return emptyList()

        return try {
            val array = JSONArray(json)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val name = o.optString("name")
                    if (name.isEmpty()) continue
                    add(
                        ShaderParam(
                            name = name,
                            description = o.optString("description").ifBlank { name },
                            initial = o.optDouble("initial", 0.0).toFloat(),
                            minimum = o.optDouble("minimum", 0.0).toFloat(),
                            maximum = o.optDouble("maximum", 0.0).toFloat(),
                            step = o.optDouble("step", 0.0).toFloat(),
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "malformed parameter JSON for $presetPath", t)
            emptyList()
        }
    }

    fun push(presetPath: String, values: Map<String, Float>) {
        if (presetPath.isBlank()) return
        val names = values.keys.toTypedArray()
        val floats = FloatArray(names.size) { values.getValue(names[it]) }
        try {
            NativeApp.setShaderChainParams(presetPath, names, floats)
        } catch (t: Throwable) {
            Log.w(TAG, "setShaderChainParams failed for $presetPath", t)
        }
    }

    fun pushEffective(presetPath: String, params: List<ShaderParam>, overrides: Map<String, Float>) {
        if (presetPath.isBlank()) return
        push(presetPath, params.associate { it.name to (overrides[it.name] ?: it.initial) })
    }

    private val UNSAFE_NAME = Regex("[^A-Za-z0-9 _.-]")

    fun savePreset(
        context: Context,
        name: String,
        basePreset: String,
        overrides: Map<String, Float>,
    ): String? {
        val safe = name.trim().replace(UNSAFE_NAME, "_")
        if (safe.isEmpty() || basePreset.isBlank()) return null

        return try {
            val dir = ShaderRepo.userPresetDir(context)
            val out = File(dir, "$safe.slangp")
            val base = File(basePreset)

            val reference = runCatching { base.relativeTo(dir).invariantSeparatorsPath }
                .getOrDefault(base.absolutePath)

            val text = buildString {
                append("#reference \"").append(reference).append("\"\n\n")
                overrides.forEach { (param, value) ->
                    append(param).append(" = \"")
                        .append(String.format(Locale.US, "%.6f", value)).append("\"\n")
                }
            }
            out.writeText(text)
            out.absolutePath
        } catch (t: Throwable) {
            Log.w(TAG, "could not save preset '$name'", t)
            null
        }
    }

    fun listSavedPresets(context: Context): List<File> =
        runCatching {
            ShaderRepo.userPresetDir(context)
                .listFiles { f -> f.isFile && f.extension.equals("slangp", ignoreCase = true) }
                ?.sortedBy { it.name.lowercase() }
                .orEmpty()
        }.getOrDefault(emptyList())

    fun isSavedPreset(context: Context, presetPath: String): Boolean =
        presetPath.isNotBlank() &&
            runCatching {
                File(presetPath).parentFile?.canonicalFile == ShaderRepo.userPresetDir(context).canonicalFile
            }.getOrDefault(false)

    fun deleteSavedPreset(context: Context, presetPath: String): Boolean =
        isSavedPreset(context, presetPath) && runCatching { File(presetPath).delete() }.getOrDefault(false)
}
