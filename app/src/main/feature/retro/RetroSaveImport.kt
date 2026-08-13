package com.winlator.cmod.feature.retro

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile

object RetroSaveImport {
    private val SAVE_EXTS = setOf("srm", "sav", "eep", "sra", "fla", "mpk", "bkr", "dsv", "mcr", "sms", "state", "st0", "ss0")
    private const val MAX_SAVE_BYTES = 16 * 1024 * 1024

    sealed class Result {
        data class Success(val name: String, val bytes: Int) : Result()
        data class Invalid(val reason: String) : Result()
    }

    private data class Entry(val name: String, val bytes: ByteArray)

    fun import(context: Context, gameName: String, sourceName: String, raw: ByteArray): Result {
        if (raw.isEmpty()) return Result.Invalid("The file is empty.")

        val entries =
            when (archiveKind(raw)) {
                "zip" -> runCatching { unzip(raw) }.getOrNull()
                    ?: return Result.Invalid("This archive could not be read.")
                "7z" -> runCatching { un7z(context, raw) }.getOrNull()
                    ?: return Result.Invalid("This 7z archive could not be read.")
                "rar" -> return Result.Invalid("RAR archives aren't supported — extract the save first, then import it.")
                else -> listOf(Entry(sourceName, raw))
            }

        val candidates = entries.filter { it.bytes.isNotEmpty() && !isRom(it.name) && it.bytes.size <= MAX_SAVE_BYTES }
        if (candidates.isEmpty()) {
            return if (entries.any { isRom(it.name) }) {
                Result.Invalid("That looks like a ROM, not a save file.")
            } else {
                Result.Invalid("No valid save file was found inside.")
            }
        }

        val chosen =
            candidates.firstOrNull { ext(it.name) in SAVE_EXTS }
                ?: candidates.maxByOrNull { it.bytes.size }!!

        return runCatching {
            RetroSaveStates.sramFile(context, gameName).writeBytes(chosen.bytes)
            Result.Success(chosen.name, chosen.bytes.size)
        }.getOrElse { Result.Invalid("Could not write the save to storage.") }
    }

    private fun archiveKind(b: ByteArray): String? {
        if (b.size >= 4 && b[0] == 0x50.toByte() && b[1] == 0x4B.toByte() &&
            (b[2] == 0x03.toByte() || b[2] == 0x05.toByte() || b[2] == 0x07.toByte())
        ) {
            return "zip"
        }
        if (b.size >= 6 && b[0] == 0x37.toByte() && b[1] == 0x7A.toByte() &&
            b[2] == 0xBC.toByte() && b[3] == 0xAF.toByte() && b[4] == 0x27.toByte() && b[5] == 0x1C.toByte()
        ) {
            return "7z"
        }
        if (b.size >= 4 && b[0] == 0x52.toByte() && b[1] == 0x61.toByte() &&
            b[2] == 0x72.toByte() && b[3] == 0x21.toByte()
        ) {
            return "rar"
        }
        return null
    }

    private fun unzip(raw: ByteArray): List<Entry> {
        val out = ArrayList<Entry>()
        ZipInputStream(ByteArrayInputStream(raw)).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                if (!e.isDirectory) out.add(Entry(File(e.name).name, zin.readBytes()))
                e = zin.nextEntry
            }
        }
        return out
    }

    private fun un7z(context: Context, raw: ByteArray): List<Entry> {
        val tmp = File.createTempFile("import", ".7z", context.cacheDir)
        return try {
            tmp.writeBytes(raw)
            val out = ArrayList<Entry>()
            SevenZFile(tmp).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        val buf = ByteArray(e.size.toInt().coerceAtLeast(0))
                        z.read(buf)
                        out.add(Entry(File(e.name).name, buf))
                    }
                    e = z.nextEntry
                }
            }
            out
        } finally {
            tmp.delete()
        }
    }

    private fun ext(name: String): String = name.substringAfterLast('.', "").lowercase()

    private fun isRom(name: String): Boolean = ext(name) in RetroSystems.allExtensions
}
