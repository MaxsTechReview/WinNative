package com.winlator.cmod.runtime.wine
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Extracts the largest icon from a Windows PE (.exe) file.
 * Parses PE resource section to find RT_GROUP_ICON / RT_ICON entries.
 */
object PeIconExtractor {
    fun extractIcon(exeFile: File): Bitmap? {
        if (!exeFile.exists()) return null
        return try {
            RandomAccessFile(exeFile, "r").use { raf -> extractFromPe(raf) }
        } catch (_: Exception) {
            null
        }
    }

    fun extractAndSave(
        exeFile: File,
        outPng: File,
    ): Boolean {
        val bmp = extractIcon(exeFile) ?: return false
        return try {
            outPng.parentFile?.mkdirs()
            outPng.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun extractFromPe(raf: RandomAccessFile): Bitmap? {
        // DOS header: e_lfanew at offset 0x3C
        raf.seek(0x3C)
        val peOffset = readInt(raf)
        raf.seek(peOffset.toLong())

        // PE signature
        val sig = readInt(raf)
        if (sig != 0x00004550) return null // "PE\0\0"

        // COFF header
        raf.skipBytes(2) // machine
        val numSections = readShort(raf)
        raf.skipBytes(12) // skip to sizeOfOptionalHeader
        val optHeaderSize = readShort(raf)
        raf.skipBytes(2) // characteristics

        val optHeaderPos = raf.filePointer
        val magic = readShort(raf)
        val isPe32Plus = magic == 0x20B

        // Data directory: resource table is entry index 2
        val ddOffset = if (isPe32Plus) 112 else 96
        raf.seek(optHeaderPos + ddOffset + 2 * 8L) // skip to 3rd entry (index 2)
        val resRva = readInt(raf)
        val resSize = readInt(raf)
        if (resRva == 0 || resSize == 0) return null

        // Read section headers to find the one containing the resource RVA
        val sectionStart = optHeaderPos + optHeaderSize
        raf.seek(sectionStart)
        var resFileOffset = 0L
        var resSectionRva = 0
        for (i in 0 until numSections) {
            val pos = sectionStart + i * 40L
            raf.seek(pos + 12) // virtualAddress
            val va = readInt(raf)
            val rawSize = readInt(raf)
            val rawPtr = readInt(raf)
            if (resRva >= va && resRva < va + rawSize) {
                resSectionRva = va
                resFileOffset = rawPtr.toLong() + (resRva - va)
                break
            }
        }
        if (resFileOffset == 0L) return null

        // Read entire resource section into a buffer for easier navigation
        raf.seek(resFileOffset)
        val resBuf = ByteArray(resSize)
        raf.readFully(resBuf)
        val bb = ByteBuffer.wrap(resBuf).order(ByteOrder.LITTLE_ENDIAN)

        // Parse root resource directory to find RT_GROUP_ICON (14) and RT_ICON (3)
        val groupIconEntries = findResourceType(bb, 0, 14) // RT_GROUP_ICON
        val iconEntries = findResourceType(bb, 0, 3) // RT_ICON
        if (groupIconEntries.isEmpty()) return null

        // Read the first GROUP_ICON resource
        val grpDataRva = resolveToDataEntry(bb, groupIconEntries[0])
        if (grpDataRva == null) return null

        val grpFileOff = grpDataRva.first - resSectionRva
        val grpSize = grpDataRva.second
        if (grpFileOff < 0 || grpFileOff + grpSize > resBuf.size) return null

        // Parse GRPICONDIR
        bb.position(grpFileOff)
        bb.short // reserved
        bb.short // type
        val count = bb.short.toInt() and 0xFFFF
        if (count == 0) return null

        // Find the largest icon entry
        data class GrpEntry(
            val w: Int,
            val h: Int,
            val bitCount: Int,
            val bytesInRes: Int,
            val id: Int,
        )
        val entries = mutableListOf<GrpEntry>()
        for (i in 0 until count) {
            val w = bb.get().toInt() and 0xFF
            val h = bb.get().toInt() and 0xFF
            bb.get() // colorCount
            bb.get() // reserved
            bb.short // planes
            val bitCount = bb.short.toInt() and 0xFFFF
            val bytesInRes = bb.int
            val id = bb.short.toInt() and 0xFFFF
            val realW = if (w == 0) 256 else w
            val realH = if (h == 0) 256 else h
            entries.add(GrpEntry(realW, realH, bitCount, bytesInRes, id))
        }

        // Prefer largest resolution, then highest bit depth
        val best = entries.maxByOrNull { it.w * it.h * 1000 + it.bitCount } ?: return null

        // Find the RT_ICON with matching ID
        val iconDataRva = resolveIconById(bb, iconEntries, best.id)
        if (iconDataRva == null) return null

        val iconFileOff = iconDataRva.first - resSectionRva
        val iconSize = iconDataRva.second
        if (iconFileOff < 0 || iconFileOff + iconSize > resBuf.size) return null

        val iconData = ByteArray(iconSize)
        System.arraycopy(resBuf, iconFileOff, iconData, 0, iconSize)

        // Try decoding as PNG first (many modern icons embed PNG)
        val pngBmp = BitmapFactory.decodeByteArray(iconData, 0, iconData.size)
        if (pngBmp != null) return pngBmp

        // Fall back: wrap in ICO format and decode
        val ico = buildIco(iconData, best.w, best.h, best.bitCount)
        return BitmapFactory.decodeByteArray(ico, 0, ico.size)
    }

    private fun buildIco(
        data: ByteArray,
        w: Int,
        h: Int,
        bitCount: Int,
    ): ByteArray {
        val bos = ByteArrayOutputStream()
        val buf = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0) // reserved
        buf.putShort(1) // type = icon
        buf.putShort(1) // count
        buf.put((if (w >= 256) 0 else w).toByte())
        buf.put((if (h >= 256) 0 else h).toByte())
        buf.put(0) // color count
        buf.put(0) // reserved
        buf.putShort(1) // planes
        buf.putShort(bitCount.toShort())
        buf.putInt(data.size)
        buf.putInt(22) // offset to data
        bos.write(buf.array())
        bos.write(data)
        return bos.toByteArray()
    }

    // Resource directory parsing helpers
    private fun findResourceType(
        bb: ByteBuffer,
        dirOffset: Int,
        typeId: Int,
    ): List<Int> {
        bb.position(dirOffset + 12)
        val namedCount = bb.short.toInt() and 0xFFFF
        val idCount = bb.short.toInt() and 0xFFFF
        val results = mutableListOf<Int>()
        for (i in 0 until namedCount + idCount) {
            val id = bb.int
            val offset = bb.int
            if (id == typeId && (offset and 0x80000000.toInt()) != 0) {
                results.add(offset and 0x7FFFFFFF)
            }
        }
        return results
    }

    private fun resolveToDataEntry(
        bb: ByteBuffer,
        subdirOffset: Int,
    ): Pair<Int, Int>? {
        // Navigate through sub-directories until we reach a data entry
        bb.position(subdirOffset + 12)
        val namedCount = bb.short.toInt() and 0xFFFF
        val idCount = bb.short.toInt() and 0xFFFF
        if (namedCount + idCount == 0) return null

        bb.int // skip first entry name/id
        val offset = bb.int

        return if ((offset and 0x80000000.toInt()) != 0) {
            // Another subdirectory — go one level deeper
            resolveToDataEntry(bb, offset and 0x7FFFFFFF)
        } else {
            // Data entry
            bb.position(offset)
            val rva = bb.int
            val size = bb.int
            Pair(rva, size)
        }
    }

    private fun resolveIconById(
        bb: ByteBuffer,
        iconSubdirs: List<Int>,
        targetId: Int,
    ): Pair<Int, Int>? {
        for (subdirOff in iconSubdirs) {
            bb.position(subdirOff + 12)
            val namedCount = bb.short.toInt() and 0xFFFF
            val idCount = bb.short.toInt() and 0xFFFF
            for (i in 0 until namedCount + idCount) {
                val id = bb.int
                val offset = bb.int
                if (id == targetId) {
                    return if ((offset and 0x80000000.toInt()) != 0) {
                        resolveToDataEntry(bb, offset and 0x7FFFFFFF)
                    } else {
                        bb.position(offset)
                        val rva = bb.int
                        val size = bb.int
                        Pair(rva, size)
                    }
                }
            }
        }
        return null
    }

    private fun readInt(raf: RandomAccessFile): Int {
        val b = ByteArray(4)
        raf.readFully(b)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun readShort(raf: RandomAccessFile): Int {
        val b = ByteArray(2)
        raf.readFully(b)
        return ByteBuffer
            .wrap(b)
            .order(ByteOrder.LITTLE_ENDIAN)
            .short
            .toInt() and 0xFFFF
    }
}
