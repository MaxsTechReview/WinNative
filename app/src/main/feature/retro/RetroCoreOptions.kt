package com.winlator.cmod.feature.retro

import android.content.Context
import com.winlator.cmod.R

enum class RetroOptionCategory {
    DISPLAY,
    SOUND,
    PERFORMANCE,
    CONTROLS,
    SYSTEM,
}

data class RetroCoreOption(
    val key: String,
    val label: String,
    val values: List<String>,
    val valueLabels: List<String>,
    val defaultValue: String,
    val category: RetroOptionCategory = RetroOptionCategory.SYSTEM,
    val advanced: Boolean = false,
    @androidx.annotation.StringRes val labelRes: Int? = null,
    val visibleWhen: Pair<String, String>? = null,
) {
    fun labelText(context: Context): String = labelRes?.let { context.getString(it) } ?: label

    fun isApplicable(current: (String) -> String?): Boolean {
        val (key, value) = visibleWhen ?: return true
        return (current(key) ?: return true) == value
    }
}

object RetroCoreOptions {
    private val DOLPHIN_COMMON_OPTIONS =
        listOf(
            RetroCoreOption(
                key = "dolphin_renderer",
                label = "Renderer",
                values = listOf("Hardware", "Software"),
                valueLabels = listOf("Hardware", "Software"),
                defaultValue = "Hardware",
                category = RetroOptionCategory.DISPLAY,
                labelRes = R.string.retro_co_renderer,
            ),
            RetroCoreOption(
                key = "dolphin_efb_scale",
                label = "Internal Resolution",
                values = listOf("1", "2", "3", "4", "5", "6"),
                valueLabels = listOf("1x", "2x", "3x", "4x", "5x", "6x"),
                defaultValue = "1",
                category = RetroOptionCategory.DISPLAY,
                labelRes = R.string.retro_co_internal_resolution,
            ),
        )

    private fun dolphinAspectOption(defaultValue: String) =
        RetroCoreOption(
            key = "dolphin_aspect_ratio",
            label = "Aspect Ratio",
            values = listOf("0", "1", "2", "3"),
            valueLabels = listOf("Auto", "16:9", "4:3", "Stretch"),
            defaultValue = defaultValue,
            category = RetroOptionCategory.DISPLAY,
            labelRes = R.string.retro_co_aspect_ratio,
        )

    private val DOLPHIN_CPU_OPTIONS =
        listOf(
            RetroCoreOption(
                key = "dolphin_cpu_core",
                label = "CPU Core",
                values = listOf("4", "5", "0"),
                valueLabels = listOf("JIT", "Cached Interpreter", "Interpreter"),
                defaultValue = "4",
                category = RetroOptionCategory.PERFORMANCE,
                labelRes = R.string.retro_co_cpu_core,
            ),
            RetroCoreOption(
                key = "dolphin_main_cpu_thread",
                label = "Dual Core",
                values = listOf("disabled", "enabled"),
                valueLabels = listOf("Off", "On"),
                defaultValue = "disabled",
                category = RetroOptionCategory.PERFORMANCE,
                labelRes = R.string.retro_co_dual_core,
            ),
        )

    private val DOLPHIN_CHEATS_OPTION =
        RetroCoreOption(
            key = "dolphin_cheats_enabled",
            label = "Internal Cheats",
            values = listOf("disabled", "enabled"),
            valueLabels = listOf("Off", "On"),
            defaultValue = "disabled",
            category = RetroOptionCategory.SYSTEM,
            labelRes = R.string.retro_co_internal_cheats,
        )

    private val DOLPHIN_VI_SKIP_OPTION =
        RetroCoreOption(
            key = "dolphin_vi_skip",
            label = "VBI Skip",
            values = listOf("enabled", "disabled"),
            valueLabels = listOf("On", "Off"),
            defaultValue = "enabled",
            category = RetroOptionCategory.PERFORMANCE,
            labelRes = R.string.retro_co_vbi_skip,
        )

    private val GAMECUBE_OPTIONS =
        DOLPHIN_COMMON_OPTIONS +
            dolphinAspectOption("2") +
            listOf(
                RetroCoreOption(
                    key = "dolphin_widescreen_hack",
                    label = "Widescreen Hack",
                    values = listOf("disabled", "enabled"),
                    valueLabels = listOf("Off", "On (16:9)"),
                    defaultValue = "disabled",
                    category = RetroOptionCategory.DISPLAY,
                    labelRes = R.string.retro_co_widescreen_hack,
                ),
            ) +
            DOLPHIN_CPU_OPTIONS +
            listOf(
                RetroCoreOption(
                    key = "dolphin_skip_gc_bios",
                    label = "Skip GameCube BIOS",
                    values = listOf("enabled", "disabled"),
                    valueLabels = listOf("On", "Off"),
                    defaultValue = "enabled",
                    category = RetroOptionCategory.SYSTEM,
                    labelRes = R.string.retro_co_skip_gc_bios,
                ),
                DOLPHIN_VI_SKIP_OPTION,
                DOLPHIN_CHEATS_OPTION,
            )

    private val WII_OPTIONS =
        DOLPHIN_COMMON_OPTIONS +
            dolphinAspectOption("1") +
            listOf(
                RetroCoreOption(
                    key = "dolphin_widescreen",
                    label = "Widescreen",
                    values = listOf("enabled", "disabled"),
                    valueLabels = listOf("On", "Off"),
                    defaultValue = "enabled",
                    category = RetroOptionCategory.DISPLAY,
                    labelRes = R.string.retro_co_widescreen,
                ),
            ) +
            DOLPHIN_CPU_OPTIONS +
            listOf(
                RetroCoreOption(
                    key = "dolphin_sensor_bar_position",
                    label = "Sensor Bar Position",
                    values = listOf("0", "1"),
                    valueLabels = listOf("Bottom", "Top"),
                    defaultValue = "0",
                    category = RetroOptionCategory.CONTROLS,
                    labelRes = R.string.retro_co_sensor_bar,
                ),
                DOLPHIN_VI_SKIP_OPTION,
                DOLPHIN_CHEATS_OPTION,
            )

    fun sanitizeDolphinVariables(vars: MutableMap<String, String>) {
        when (vars["dolphin_cpu_core"]?.trim()) {
            "JITARM64", "JIT", "JIT64" -> vars["dolphin_cpu_core"] = "4"
            "Cached Interpreter", "CachedInterpreter" -> vars["dolphin_cpu_core"] = "5"
            "Interpreter" -> vars["dolphin_cpu_core"] = "0"
        }
        vars["dolphin_efb_scale"]?.let { raw ->
            val t = raw.trim()
            if (t.length == 1 && t[0].isDigit()) return@let
            val digit =
                Regex("""^x?(\d+)""", RegexOption.IGNORE_CASE).find(t)?.groupValues?.getOrNull(1)
            if (digit != null) vars["dolphin_efb_scale"] = digit
        }
        DOLPHIN_PERF_DEFAULTS.forEach { (k, v) ->
            if (vars[k].isNullOrBlank()) vars[k] = v
        }
        if (vars["dolphin_skip_dupe_frames"] == "enabled") {
            vars["dolphin_skip_dupe_frames"] = "disabled"
        }
    }

    private val DOLPHIN_PERF_DEFAULTS: Map<String, String> =
        mapOf(
            "dolphin_renderer" to "Hardware",
            "dolphin_efb_scale" to "1",
            "dolphin_cpu_core" to "4",
            "dolphin_main_cpu_thread" to "disabled",
            "dolphin_dsp_hle" to "enabled",
            "dolphin_fast_disc_speed" to "enabled",
            "dolphin_vi_skip" to "disabled",
            "dolphin_skip_dupe_frames" to "disabled",
            "dolphin_cheats_enabled" to "disabled",
        )

    fun defaultVariables(system: RetroSystem?): Map<String, String> =
        when (system?.id) {
            RetroSystems.N64.id ->
                mapOf(
                    "mupen64plus-43screensize" to "640x480",
                    "mupen64plus-EnableFBEmulation" to "True",
                    "mupen64plus-aspect" to "4:3",
                )
            RetroSystems.PSX.id ->
                mapOf(
                    "beetle_psx_skip_bios" to "enabled",
                )
            RetroSystems.GAMECUBE.id ->
                DOLPHIN_PERF_DEFAULTS +
                    mapOf(
                        "dolphin_widescreen" to "disabled",
                        "dolphin_widescreen_hack" to "disabled",
                        "dolphin_aspect_ratio" to "2",
                        "dolphin_skip_gc_bios" to "enabled",
                    )
            RetroSystems.WII.id ->
                DOLPHIN_PERF_DEFAULTS +
                    mapOf(
                        "dolphin_widescreen" to "enabled",
                        "dolphin_aspect_ratio" to "1",
                        "dolphin_sensor_bar_position" to "0",
                    )
            else -> emptyMap()
        }

    fun forSystem(system: RetroSystem?): List<RetroCoreOption> =
        when (system?.id) {
            RetroSystems.NES.id -> RetroCoreCatalog.FCEUMM
            RetroSystems.SNES.id -> RetroCoreCatalog.SNES9X
            RetroSystems.GAMEBOY.id, RetroSystems.GAMEBOY_COLOR.id -> RetroCoreCatalog.GAMBATTE
            RetroSystems.GBA.id -> RetroCoreCatalog.MGBA
            RetroSystems.GENESIS.id -> forGenesisFamily(RetroSystems.GENESIS.id)
            RetroSystems.MASTER_SYSTEM.id -> forGenesisFamily(RetroSystems.MASTER_SYSTEM.id)
            RetroSystems.GAME_GEAR.id -> forGenesisFamily(RetroSystems.GAME_GEAR.id)
            RetroSystems.N64.id -> RetroCoreCatalog.MUPEN64PLUS_NEXT
            RetroSystems.PSX.id -> RetroCoreCatalog.BEETLE_PSX
            RetroSystems.GAMECUBE.id -> GAMECUBE_OPTIONS
            RetroSystems.WII.id -> WII_OPTIONS
            else -> emptyList()
        }

    private fun forGenesisFamily(systemId: String): List<RetroCoreOption> {
        val promote =
            when (systemId) {
                RetroSystems.MASTER_SYSTEM.id ->
                    setOf(
                        "genesis_plus_gx_ym2413",
                        "genesis_plus_gx_ym2413_core",
                        "genesis_plus_gx_left_border",
                    )
                RetroSystems.GAME_GEAR.id ->
                    setOf(
                        "genesis_plus_gx_gg_extra",
                        "genesis_plus_gx_lcd_filter",
                    )
                else -> emptySet()
            }
        val demote =
            when (systemId) {
                RetroSystems.GENESIS.id ->
                    setOf(
                        "genesis_plus_gx_ym2413",
                        "genesis_plus_gx_ym2413_core",
                        "genesis_plus_gx_left_border",
                        "genesis_plus_gx_gg_extra",
                    )
                else -> emptySet()
            }
        return RetroCoreCatalog.GENESIS_PLUS_GX.map { option ->
            when (option.key) {
                in promote -> option.copy(advanced = false)
                in demote -> option.copy(advanced = true)
                else -> option
            }
        }
    }
}
