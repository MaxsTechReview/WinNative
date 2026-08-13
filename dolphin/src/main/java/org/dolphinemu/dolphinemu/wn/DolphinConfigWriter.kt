package org.dolphinemu.dolphinemu.wn

import java.io.File

object DolphinConfigWriter {
    fun write(userDir: File, vars: Map<String, String>) {
        val core = linkedMapOf<String, String>()
        core["GFXBackend"] =
            if (vars["dolphin_renderer"] == "Software") "Software Renderer" else "Vulkan"
        core["CPUCore"] = vars["dolphin_cpu_core"]?.toIntOrNull()?.toString() ?: "4"
        core["CPUThread"] = ini(vars["dolphin_main_cpu_thread"] == "enabled")
        core["SkipIPL"] = ini(vars["dolphin_skip_gc_bios"] != "disabled")
        core["EnableCheats"] = ini(vars["dolphin_cheats_enabled"] == "enabled")
        core["DSPHLE"] = ini(vars["dolphin_dsp_hle"] != "disabled")
        core["FastDiscSpeed"] = ini(vars["dolphin_fast_disc_speed"] == "enabled")
        mergeIni(File(userDir, "Config/Dolphin.ini"), "Core", core)

        val dsp = linkedMapOf<String, String>()
        dsp["Muted"] = ini(vars["wn_audio"] == "0")
        mergeIni(File(userDir, "Config/Dolphin.ini"), "DSP", dsp)

        val gfxSettings = linkedMapOf<String, String>()
        gfxSettings["InternalResolution"] =
            vars["dolphin_efb_scale"]?.toIntOrNull()?.coerceIn(1, 6)?.toString() ?: "1"
        gfxSettings["AspectRatio"] =
            vars["dolphin_aspect_ratio"]?.toIntOrNull()?.coerceIn(0, 6)?.toString() ?: "0"
        gfxSettings["wideScreenHack"] = ini(vars["dolphin_widescreen_hack"] == "enabled")
        gfxSettings["DriverLibName"] = vars["dolphin_gpu_driver_lib"].orEmpty()
        gfxSettings["ShowFPS"] = "False"
        gfxSettings["ShowVPS"] = "False"
        gfxSettings["ShowSpeed"] = "False"
        mergeIni(File(userDir, "Config/GFX.ini"), "Settings", gfxSettings)

        val gfxHacks = linkedMapOf<String, String>()
        gfxHacks["VISkip"] = ini(vars["dolphin_vi_skip"] != "disabled")
        mergeIni(File(userDir, "Config/GFX.ini"), "Hacks", gfxHacks)

        vars["dolphin_log_verbosity"]?.toIntOrNull()?.let { level ->
            mergeIni(File(userDir, "Config/Logger.ini"), "Options", mapOf("Verbosity" to level.toString()))
        }
    }

    fun wiiWidescreen(vars: Map<String, String>): Boolean = vars["dolphin_widescreen"] != "disabled"

    fun sensorBarTop(vars: Map<String, String>): Boolean = vars["dolphin_sensor_bar_position"] == "1"

    private fun ini(value: Boolean) = if (value) "True" else "False"

    private fun mergeIni(file: File, section: String, keys: Map<String, String>) {
        val source = if (file.isFile) file.readText() else ""
        val lines = source.replace("\r\n", "\n").split('\n').toMutableList()
        val header = "[$section]"
        var start = lines.indexOfFirst { it.trim().equals(header, ignoreCase = true) }
        if (start < 0) {
            val builder = StringBuilder(source.trimEnd())
            if (builder.isNotEmpty()) builder.append("\n\n")
            builder.append(header).append('\n')
            for ((k, v) in keys) builder.append(k).append(" = ").append(v).append('\n')
            file.parentFile?.mkdirs()
            file.writeText(builder.toString())
            return
        }
        var end = lines.size
        for (i in (start + 1) until lines.size) {
            val t = lines[i].trim()
            if (t.startsWith("[") && t.endsWith("]")) {
                end = i
                break
            }
        }
        val remaining = keys.toMutableMap()
        for (i in (start + 1) until end) {
            val t = lines[i].trim()
            if (t.isEmpty() || t.startsWith("#") || t.startsWith(";")) continue
            val eq = t.indexOf('=')
            if (eq <= 0) continue
            val key = t.substring(0, eq).trim()
            val wanted = remaining.remove(key) ?: continue
            lines[i] = "$key = $wanted"
        }
        if (remaining.isNotEmpty()) {
            lines.addAll(end, remaining.map { (k, v) -> "$k = $v" })
        }
        file.parentFile?.mkdirs()
        file.writeText(lines.joinToString("\n").let { if (it.endsWith("\n")) it else "$it\n" })
    }
}
