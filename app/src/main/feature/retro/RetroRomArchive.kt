package com.winlator.cmod.feature.retro

import android.content.Context
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile

object RetroRomArchive {
    private val ARCHIVE_EXTS = setOf("zip", "7z")
    private const val MAX_INNER_BYTES = 96L * 1024 * 1024

    data class Inner(val name: String, val size: Long)

    fun isArchive(path: String?): Boolean =
        path != null && path.substringAfterLast('.', "").lowercase() in ARCHIVE_EXTS

    fun detect(path: String): RetroSystem? {
        val inner = innerRom(path) ?: return null
        return RetroSystems.fromExtension(inner.name.substringAfterLast('.', ""))
    }

    fun innerRom(path: String): Inner? {
        val f = File(path)
        if (!f.isFile) return null
        val entries =
            runCatching {
                when (path.substringAfterLast('.', "").lowercase()) {
                    "zip" -> zipNames(f)
                    "7z" -> sevenZNames(f)
                    else -> emptyList()
                }
            }.getOrDefault(emptyList())
        return entries.firstOrNull { playableRom(it.name, it.size) }
    }

    fun extractTo(context: Context, path: String): File? {
        val inner = innerRom(path) ?: return null
        val outDir = File(context.cacheDir, "retro_extracted").apply { mkdirs() }
        val target = File(outDir, File(inner.name).name)
        if (target.isFile && target.length() == inner.size) return target
        val ok =
            runCatching {
                when (path.substringAfterLast('.', "").lowercase()) {
                    "zip" -> extractZip(File(path), inner.name, target)
                    "7z" -> extractSevenZ(context, File(path), inner.name, target)
                    else -> false
                }
            }.getOrDefault(false)
        return if (ok && target.isFile) target else null
    }

    private fun playableRom(name: String, size: Long): Boolean {
        if (size <= 0L || size > MAX_INNER_BYTES) return false
        val sys = RetroSystems.fromExtension(name.substringAfterLast('.', "")) ?: return false
        return sys.id != RetroSystems.PS2.id && sys.id != RetroSystems.PSX.id
    }

    private fun zipNames(f: File): List<Inner> {
        val out = ArrayList<Inner>()
        ZipFile(f).use { zip ->
            val e = zip.entries()
            while (e.hasMoreElements()) {
                val entry = e.nextElement()
                if (!entry.isDirectory) out.add(Inner(entry.name, entry.size))
            }
        }
        return out
    }

    private fun sevenZNames(f: File): List<Inner> {
        val out = ArrayList<Inner>()
        SevenZFile(f).use { z ->
            var entry = z.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) out.add(Inner(entry.name, entry.size))
                entry = z.nextEntry
            }
        }
        return out
    }

    private fun extractZip(f: File, entryName: String, target: File): Boolean {
        ZipInputStream(f.inputStream().buffered()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                if (e.name == entryName) {
                    target.outputStream().use { zin.copyTo(it) }
                    return true
                }
                e = zin.nextEntry
            }
        }
        return false
    }

    private fun extractSevenZ(context: Context, f: File, entryName: String, target: File): Boolean {
        SevenZFile(f).use { z ->
            var entry = z.nextEntry
            while (entry != null) {
                if (entry.name == entryName) {
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = z.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                        }
                    }
                    return true
                }
                entry = z.nextEntry
            }
        }
        return false
    }
}
