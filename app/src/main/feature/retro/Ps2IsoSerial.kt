package com.winlator.cmod.feature.retro

import java.io.File
import java.io.RandomAccessFile

object Ps2IsoSerial {
    private const val LOGICAL = 2048

    private data class Layout(val sectorSize: Int, val dataOffset: Int)

    fun serialOf(file: File): String? {
        if (!file.isFile || file.length() < 17L * LOGICAL) return null
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val layout = detectLayout(raf) ?: return null
                val pvd = readLogical(raf, layout, 16)
                val rootLba = le32(pvd, 156 + 2)
                val rootSize = le32(pvd, 156 + 10)
                if (rootLba <= 0 || rootSize <= 0) return null
                val cnf = findFile(raf, layout, rootLba, rootSize, "SYSTEM.CNF") ?: return null
                val text = readExtent(raf, layout, cnf.first, cnf.second.coerceAtMost(64 * 1024))
                    .toString(Charsets.US_ASCII)
                parseBoot(text)
            }
        }.getOrNull()
    }

    private fun detectLayout(raf: RandomAccessFile): Layout? {
        for (layout in listOf(Layout(2048, 0), Layout(2352, 16), Layout(2352, 24))) {
            val sec = runCatching { readLogical(raf, layout, 16) }.getOrNull() ?: continue
            if (sec[0].toInt() == 1 &&
                sec[1] == 'C'.code.toByte() && sec[2] == 'D'.code.toByte() &&
                sec[3] == '0'.code.toByte() && sec[4] == '0'.code.toByte() && sec[5] == '1'.code.toByte()
            ) {
                return layout
            }
        }
        return null
    }

    private fun readLogical(raf: RandomAccessFile, layout: Layout, lba: Int): ByteArray {
        val buf = ByteArray(LOGICAL)
        raf.seek(lba.toLong() * layout.sectorSize + layout.dataOffset)
        raf.readFully(buf)
        return buf
    }

    private fun readExtent(raf: RandomAccessFile, layout: Layout, lba: Int, size: Int): ByteArray {
        val out = ByteArray(size)
        var done = 0
        var sector = lba
        while (done < size) {
            val sec = readLogical(raf, layout, sector)
            val n = minOf(LOGICAL, size - done)
            System.arraycopy(sec, 0, out, done, n)
            done += n
            sector++
        }
        return out
    }

    private fun findFile(
        raf: RandomAccessFile,
        layout: Layout,
        dirLba: Int,
        dirSize: Int,
        target: String,
    ): Pair<Int, Int>? {
        var remaining = dirSize
        var sector = dirLba
        while (remaining > 0) {
            val sec = readLogical(raf, layout, sector)
            var off = 0
            while (off < LOGICAL) {
                val recLen = sec[off].toInt() and 0xFF
                if (recLen == 0) break
                val nameLen = sec[off + 32].toInt() and 0xFF
                if (nameLen > 0 && off + 33 + nameLen <= LOGICAL) {
                    val rawName = String(sec, off + 33, nameLen, Charsets.US_ASCII)
                    val name = rawName.substringBefore(';').trim()
                    if (name.equals(target, ignoreCase = true)) {
                        return le32(sec, off + 2) to le32(sec, off + 10)
                    }
                }
                off += recLen
            }
            remaining -= LOGICAL
            sector++
        }
        return null
    }

    private fun le32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun parseBoot(text: String): String? {
        val line = text.lineSequence().firstOrNull { it.trimStart().startsWith("BOOT", ignoreCase = true) && it.contains("cdrom", ignoreCase = true) }
            ?: return null
        val afterColon = line.substringAfter("cdrom0:", "").trimStart('\\', '/', ' ')
        val token = afterColon.substringBefore(';').substringBefore(' ').trim()
        if (token.isBlank()) return null
        val serial = token.replace("_", "-").replace(".", "").uppercase()
        return serial.takeIf { it.length in 8..12 && it.contains('-') }
    }
}
