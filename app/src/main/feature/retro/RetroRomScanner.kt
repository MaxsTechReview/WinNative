package com.winlator.cmod.feature.retro

import android.content.Context
import com.winlator.cmod.feature.shortcuts.LibraryShortcutUtils
import com.winlator.cmod.runtime.container.ContainerManager
import java.io.File

object RetroRomScanner {
    data class Result(val added: Int, val removed: Int)

    fun scanConfiguredFolder(context: Context): Result {
        val dir = RetroDefaults.romsDir(context)?.let(::File) ?: return Result(0, 0)
        if (!dir.isDirectory) return Result(0, 0)
        return scan(context, dir)
    }

    fun scan(context: Context, dir: File): Result {
        val dirPath = dir.absolutePath
        val retro =
            runCatching {
                ContainerManager(context).loadShortcuts().filter { RetroShortcuts.isRetroShortcut(it) }
            }.getOrDefault(emptyList())

        var removed = 0
        retro.forEach { shortcut ->
            val rom = RetroShortcuts.romPath(shortcut)
            if (rom.isNotBlank() && isUnder(rom, dirPath) && !File(rom).isFile) {
                if (LibraryShortcutUtils.deleteShortcutArtifacts(context, shortcut)) removed++
            }
        }

        val existing =
            retro.mapNotNull { RetroShortcuts.romPath(it).takeIf { p -> p.isNotBlank() && File(p).isFile } }.toHashSet()
        val roms =
            dir.walkTopDown()
                .maxDepth(4)
                .filter { it.isFile && RetroSystems.isRetroRom(it.absolutePath) }
                .filter { it.absolutePath !in existing }
                .toList()
        var added = 0
        for (rom in roms) {
            val system = RetroSystems.detectForFile(rom.absolutePath) ?: continue
            val name = rom.nameWithoutExtension.trim().ifBlank { rom.name }
            if (RetroShortcuts.create(context, name, rom.absolutePath, system)) added++
        }

        RetroBoxart.ensureArtworkAsync(context)
        return Result(added, removed)
    }

    private fun isUnder(path: String, dir: String): Boolean {
        val d = if (dir.endsWith("/")) dir else "$dir/"
        return path == dir || path.startsWith(d)
    }
}
