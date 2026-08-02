package com.winlator.cmod.feature.retro

import android.content.Context
import java.io.File

object RetroCoreManager {
    private fun resolveCore(
        context: Context,
        fileName: String,
    ): File = File(RetroBundle.coresDir(context), fileName)

    fun coreFile(
        context: Context,
        system: RetroSystem,
    ): File = resolveCore(context, system.coreFileName)

    fun multiplayerCoreFile(
        context: Context,
        system: RetroSystem,
    ): File =
        if (system.id == RetroSystems.GBA.id) {
            resolveCore(context, RetroSystems.GBA_MULTIPLAYER_CORE)
        } else {
            coreFile(context, system)
        }

    fun isCoreAvailable(
        context: Context,
        system: RetroSystem,
    ): Boolean = coreFile(context, system).isFile

    fun isMultiplayerCoreAvailable(
        context: Context,
        system: RetroSystem,
    ): Boolean = multiplayerCoreFile(context, system).isFile

    fun systemDir(context: Context): File = File(context.filesDir, "retro/system").also { it.mkdirs() }

    fun savesDir(context: Context): File = File(context.filesDir, "retro/saves").also { it.mkdirs() }

    fun statesDir(context: Context): File = File(context.filesDir, "retro/states").also { it.mkdirs() }

    fun stateFile(
        context: Context,
        gameName: String,
        slot: Int,
    ): File {
        val safe = gameName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val suffix = if (slot <= 0) "state" else "state$slot"
        return File(statesDir(context), "$safe.$suffix")
    }

    fun ensureGlideN64Ini(context: Context) {
        val target = File(File(systemDir(context), "Mupen64plus").also { it.mkdirs() }, "GLideN64.custom.ini")
        runCatching {
            context.assets.open("retro/GLideN64.custom.ini").use { input ->
                val bytes = input.readBytes()
                if (!target.isFile || target.length() != bytes.size.toLong()) {
                    target.writeBytes(bytes)
                }
            }
        }
    }

    fun ensureDolphinSys(context: Context) {
        val marker = File(systemDir(context), "dolphin-emu/.wn_sys_ready")
        if (marker.isFile) return
        val destRoot = File(systemDir(context), "dolphin-emu").also { it.mkdirs() }
        runCatching {
            val fromBundle = File(RetroBundle.dataDir(context), "dolphin-emu")
            if (!fromBundle.isDirectory) return
            fromBundle.copyRecursively(destRoot, overwrite = true)
            marker.parentFile?.mkdirs()
            marker.writeText("1")
        }
    }

    fun ensureDolphinUser(context: Context) {
        val userRoot = File(savesDir(context), "User")
        val gcRoot = File(userRoot, "GC").also { it.mkdirs() }
        for (region in listOf("USA", "EUR", "JAP")) {
            File(gcRoot, "$region/Card A").mkdirs()
            File(gcRoot, "$region/Card B").mkdirs()
        }
        File(userRoot, "Config").mkdirs()
        File(userRoot, "Wii").mkdirs()
        File(userRoot, "GBA/Saves").mkdirs()
        ensureDolphinIni(File(userRoot, "Config/Dolphin.ini"))
    }

    private fun ensureDolphinIni(ini: File) {
        runCatching {
            val existing = if (ini.isFile) ini.readText() else ""
            val desired =
                mapOf(
                    "SlotA" to "8",
                    "SlotB" to "255",
                    "MemcardAPath" to "",
                    "MemcardBPath" to "",
                    "GCIFolderAPath" to "",
                    "GCIFolderBPath" to "",
                    "MemoryCardSize" to "-1",
                    "CPUThread" to "False",
                    "Fastmem" to "False",
                    "FastmemArena" to "False",
                )
            val merged = mergeIniSection(existing, "Core", desired)
            if (merged != existing) {
                ini.parentFile?.mkdirs()
                ini.writeText(merged)
            }
        }
    }

    private fun mergeIniSection(
        source: String,
        section: String,
        keys: Map<String, String>,
    ): String {
        if (keys.isEmpty()) return source
        val lines = source.replace("\r\n", "\n").split('\n').toMutableList()
        val header = "[$section]"
        var sectionStart = lines.indexOfFirst { it.trim().equals(header, ignoreCase = true) }
        if (sectionStart < 0) {
            val builder = StringBuilder(source.trimEnd())
            if (builder.isNotEmpty()) builder.append("\n\n")
            builder.append(header).append('\n')
            for ((k, v) in keys) builder.append(k).append(" = ").append(v).append('\n')
            return builder.toString()
        }
        var sectionEnd = lines.size
        for (i in (sectionStart + 1) until lines.size) {
            val t = lines[i].trim()
            if (t.startsWith("[") && t.endsWith("]")) {
                sectionEnd = i
                break
            }
        }
        val remaining = keys.toMutableMap()
        for (i in (sectionStart + 1) until sectionEnd) {
            val raw = lines[i]
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) continue
            val eq = trimmed.indexOf('=')
            if (eq <= 0) continue
            val key = trimmed.substring(0, eq).trim()
            val wanted = remaining.remove(key) ?: continue
            lines[i] = "$key = $wanted"
        }
        if (remaining.isNotEmpty()) {
            val insertAt = sectionEnd
            val block = remaining.map { (k, v) -> "$k = $v" }
            lines.addAll(insertAt, block)
        }
        return lines.joinToString("\n").let { if (it.endsWith("\n")) it else "$it\n" }
    }

    fun usesDolphinCore(system: RetroSystem?): Boolean =
        system?.id == RetroSystems.GAMECUBE.id || system?.id == RetroSystems.WII.id

    fun missingBios(
        context: Context,
        system: RetroSystem,
    ): Boolean {
        if (!system.needsBios) return false
        val dir = systemDir(context)
        return system.biosFiles.none { File(dir, it).isFile }
    }
}
