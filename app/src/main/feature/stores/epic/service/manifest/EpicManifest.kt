package com.winlator.cmod.feature.stores.epic.service.manifest
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Inflater

// Base class for Epic Games binary and JSON manifests.
sealed class EpicManifest {
    var headerSize: Int = 41
    var sizeCompressed: Int = 0
    var sizeUncompressed: Int = 0
    var shaHash: ByteArray = ByteArray(20)
    var storedAs: Byte = 0
    var version: Int = 18
    var data: ByteArray = ByteArray(0)

    var meta: ManifestMeta? = null
    var chunkDataList: ChunkDataList? = null
    var fileManifestList: FileManifestList? = null
    var customFields: CustomFields? = null

    val isCompressed: Boolean
        get() = (storedAs.toInt() and 0x1) != 0

    companion object {
        const val HEADER_MAGIC: UInt = 0x44BEC00Cu
        const val DEFAULT_SERIALIZATION_VERSION = 17

        // Detects the manifest format and returns its parser.
        fun detect(data: ByteArray): EpicManifest =
            if (data.size >= 4) {
                val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                val magic = buffer.int.toUInt()
                if (magic == HEADER_MAGIC) {
                    Timber.tag("Epic").i("Binary Manifest Detected!")
                    BinaryManifest()
                } else {
                    Timber.tag("Epic").i("JSON Manifest Detected!")
                    JsonManifest()
                }
            } else {
                Timber.tag("Epic").i("Defaulting to JSON Manifest...")
                JsonManifest()
            }

        // Reads and parses a complete manifest from bytes.
        fun readAll(data: ByteArray): EpicManifest {
            val manifest = detect(data)
            manifest.read(data)
            manifest.parseContents()
            return manifest
        }
    }

    abstract fun read(data: ByteArray)

    abstract fun parseContents()

    abstract fun serialize(): ByteArray

    // Returns the chunk directory for the manifest version.
    fun getChunkDir(): String =
        when {
            version >= 15 -> "ChunksV4"
            version >= 6 -> "ChunksV3"
            version >= 3 -> "ChunksV2"
            else -> "Chunks"
        }
}

// Binary format manifest parser.
class BinaryManifest : EpicManifest() {
    /**
     * Binary manifest parse flow (high-level):
     *
     * 1) Read the fixed 41-byte header in little-endian order:
     *    - magic, header size, uncompressed size, compressed size,
     *      SHA-1 of the uncompressed body, storage flags, manifest version.
     * 2) Seek to `headerSize` and read the remaining bytes as the body payload.
     * 3) If `storedAs` indicates compression, inflate with zlib to `sizeUncompressed`.
     * 4) Validate integrity by comparing SHA-1(uncompressedBody) with header hash.
     * 5) Keep the verified body in `data`; later `parseContents()` decodes sections
     *    in strict order: `ManifestMeta` -> `ChunkDataList` -> `FileManifestList`
     *    -> `CustomFields`.
     */
    override fun read(data: ByteArray) {
        val input = ByteArrayInputStream(data)
        val buffer = ByteBuffer.allocate(data.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(data)
        buffer.flip()

        val magic = buffer.int.toUInt()
        if (magic != HEADER_MAGIC) {
            throw IllegalArgumentException("Invalid manifest header magic: 0x${magic.toString(16)}")
        }

        headerSize = buffer.int
        sizeUncompressed = buffer.int
        sizeCompressed = buffer.int
        buffer.get(shaHash)
        storedAs = buffer.get()
        version = buffer.int

        if (buffer.position() != headerSize) {
            buffer.position(headerSize)
        }

        val bodyData = ByteArray(buffer.remaining())
        buffer.get(bodyData)

        this.data =
            if (isCompressed) {
                val inflater = Inflater()
                inflater.setInput(bodyData)
                val decompressed = ByteArray(sizeUncompressed)
                val resultLength = inflater.inflate(decompressed)
                inflater.end()

                if (resultLength != sizeUncompressed) {
                    throw IllegalStateException("Manifest decompression size mismatch: expected $sizeUncompressed, got $resultLength")
                }

                val md = MessageDigest.getInstance("SHA-1")
                val computedHash = md.digest(decompressed)
                if (!computedHash.contentEquals(shaHash)) {
                    throw IllegalStateException("Manifest hash mismatch!")
                }

                decompressed
            } else {
                bodyData
            }
    }

    override fun parseContents() {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        meta = ManifestMeta.read(buffer)
        chunkDataList = ChunkDataList.read(buffer, meta?.featureLevel ?: version)
        fileManifestList = FileManifestList.read(buffer)
        customFields = CustomFields.read(buffer)

        data = ByteArray(0)
    }

    private fun estimateBodySize(): Int {
        var size = 0
        meta?.let { size += 10000 }
        chunkDataList?.let { size += it.elements.size * 57 + 1000 }
        fileManifestList?.let { fml ->
            size +=
                fml.elements.sumOf { fm ->
                    fm.filename.length + fm.symlinkTarget.length + 100 +
                        fm.installTags.sumOf { it.length + 4 } +
                        fm.chunkParts.size * 28
                }
        }
        customFields?.let { size += 1000 }
        return maxOf(size, 256 * 1024)
    }

    override fun serialize(): ByteArray {
        val bodyStream = java.io.ByteArrayOutputStream()

        // Cloud saves use version 18: dataVersion=0, no MD5/SHA256 in FML.
        val targetVersion =
            maxOf(DEFAULT_SERIALIZATION_VERSION, meta?.featureLevel ?: version)
                .coerceAtMost(21)

        meta?.featureLevel = targetVersion

        // Each section's `write()` does placeholder-then-back-fill of its size field, so it
        // requires a buffer that supports random-access mutation (i.e. ByteBuffer, not a
        // pure stream). Legendary side-steps this with Python's BytesIO which auto-grows;
        // a fixed-capacity ByteBuffer can't, so when a single section's actual size exceeds
        // the initial estimate (large saves with many files), the per-section write throws
        // BufferOverflowException and the whole upload fails. Track the buffer in a `var`
        // and reallocate to a larger size if a section won't fit, dumping any prior
        // already-written bytes to the body stream first.
        var bodyBuffer = ByteBuffer.allocate(estimateBodySize()).order(ByteOrder.LITTLE_ENDIAN)

        fun flushAndReset() {
            if (bodyBuffer.position() > 0) {
                bodyStream.write(bodyBuffer.array(), 0, bodyBuffer.position())
                bodyBuffer.clear()
            }
        }

        fun ensureSpace(needed: Int) {
            if (bodyBuffer.remaining() < needed) {
                flushAndReset()
                if (bodyBuffer.capacity() < needed) {
                    val newCapacity = maxOf(bodyBuffer.capacity() * 2, needed + 64 * 1024)
                    bodyBuffer = ByteBuffer.allocate(newCapacity).order(ByteOrder.LITTLE_ENDIAN)
                }
            }
        }

        meta?.let { m ->
            ensureSpace(10_000)
            m.write(bodyBuffer)
        }

        chunkDataList?.let { cdl ->
            ensureSpace(cdl.elements.size * 57 + 1_000)
            cdl.write(bodyBuffer, targetVersion)
        }

        fileManifestList?.let { fml ->
            // Worst-case oversize the estimate: filenames may be UTF-16 (× 2), and the
            // section header itself adds ~8 bytes of overhead per element on top of the
            // 28-byte chunk-part stride. Doubling the per-element estimate keeps
            // `write()`'s back-fill in-bounds without a second reallocation pass.
            val needed =
                fml.elements.sumOf {
                    (it.filename.length + it.symlinkTarget.length) * 2 + 200 +
                        it.installTags.sumOf { t -> t.length * 2 + 8 } +
                        it.chunkParts.size * 32
                }
            ensureSpace(needed + 4_000)
            fml.write(bodyBuffer)
        }

        customFields?.let { cf ->
            // CustomFields stores both keys and values as FStrings, so size on the wire is
            // ~ sum((len * 2 + 8) for UTF-16 pessimism) per key plus the same per value, plus a small section header.
            val needed = cf.estimatedSerializedSize() + 4_000
            ensureSpace(needed)
            cf.write(bodyBuffer)
        }

        flushAndReset()

        val uncompressedData = bodyStream.toByteArray()

        // Compress body with zlib
        val compressedData = java.io.ByteArrayOutputStream()
        java.util.zip
            .DeflaterOutputStream(compressedData)
            .use { it.write(uncompressedData) }
        val compressed = compressedData.toByteArray()

        // SHA-1 of uncompressed body — written into the manifest header
        val sha = MessageDigest.getInstance("SHA-1").digest(uncompressedData)

        // Build the 41-byte manifest header
        val headerBuffer = ByteBuffer.allocate(41).order(ByteOrder.LITTLE_ENDIAN)
        headerBuffer.putInt(HEADER_MAGIC.toInt()) // magic
        headerBuffer.putInt(41) // header size (always 41)
        headerBuffer.putInt(uncompressedData.size) // size_uncompressed
        headerBuffer.putInt(compressed.size) // size_compressed
        headerBuffer.put(sha) // SHA-1 (20 bytes)
        headerBuffer.put(0x01.toByte()) // stored_as = compressed
        headerBuffer.putInt(targetVersion) // manifest version

        val result = ByteArray(41 + compressed.size)
        System.arraycopy(headerBuffer.array(), 0, result, 0, 41)
        System.arraycopy(compressed, 0, result, 41, compressed.size)
        return result
    }
}

/**
 * JSON format manifest parser (older format, less common)
 */
class JsonManifest : EpicManifest() {
    override fun read(data: ByteArray) {
        this.data = data
        storedAs = 0 // Never compressed
    }

    override fun parseContents() {
        // Use JsonManifestParser to parse the JSON data
        val parsedManifest = JsonManifestParser.parse(data)

        // Copy parsed data to this instance
        this.version = parsedManifest.version
        this.headerSize = parsedManifest.headerSize
        this.storedAs = parsedManifest.storedAs
        this.meta = parsedManifest.meta
        this.chunkDataList = parsedManifest.chunkDataList
        this.fileManifestList = parsedManifest.fileManifestList
        this.customFields = parsedManifest.customFields

        // Clear raw data to save memory
        this.data = ByteArray(0)
    }

    override fun serialize(): ByteArray {
        // JSON manifests are not commonly created, convert to binary
        val binary = BinaryManifest()
        binary.version = this.version
        binary.meta = this.meta
        binary.chunkDataList = this.chunkDataList
        binary.fileManifestList = this.fileManifestList
        binary.customFields = this.customFields
        return binary.serialize()
    }
}

/**
 * Manifest metadata containing game information
 */
data class ManifestMeta(
    var metaSize: Int = 0,
    var dataVersion: Byte = 0,
    var featureLevel: Int = 18,
    var isFileData: Boolean = false,
    var appId: Int = 0,
    var appName: String = "",
    var buildVersion: String = "",
    var launchExe: String = "",
    var launchCommand: String = "",
    var prereqIds: List<String> = emptyList(),
    var prereqName: String = "",
    var prereqPath: String = "",
    var prereqArgs: String = "",
    var uninstallActionPath: String = "",
    var uninstallActionArgs: String = "",
    var buildId: String = "",
) {
    companion object {
        fun read(buffer: ByteBuffer): ManifestMeta {
            val meta = ManifestMeta()
            val startPos = buffer.position()

            meta.metaSize = buffer.int
            meta.dataVersion = buffer.get()
            meta.featureLevel = buffer.int
            meta.isFileData = buffer.get() == 1.toByte()
            meta.appId = buffer.int
            meta.appName = readFString(buffer)
            meta.buildVersion = readFString(buffer)
            meta.launchExe = readFString(buffer)
            meta.launchCommand = readFString(buffer)

            // Prerequisite IDs list
            val prereqCount = buffer.int
            meta.prereqIds = List(prereqCount) { readFString(buffer) }

            meta.prereqName = readFString(buffer)
            meta.prereqPath = readFString(buffer)
            meta.prereqArgs = readFString(buffer)

            // Data version 1+ includes build ID
            if (meta.dataVersion >= 1) {
                meta.buildId = readFString(buffer)
            }

            // Data version 2+ includes uninstall actions
            if (meta.dataVersion >= 2) {
                meta.uninstallActionPath = readFString(buffer)
                meta.uninstallActionArgs = readFString(buffer)
            }

            // Verify we read the expected amount
            val bytesRead = buffer.position() - startPos
            if (bytesRead != meta.metaSize) {
                // Skip remaining bytes if we didn't read all
                buffer.position(startPos + meta.metaSize)
            }

            return meta
        }
    }

    fun write(buffer: ByteBuffer) {
        val startPos = buffer.position()

        // Placeholder for size
        val sizePos = buffer.position()
        buffer.putInt(0)

        buffer.put(dataVersion)
        buffer.putInt(featureLevel)
        buffer.put(if (isFileData) 1.toByte() else 0.toByte())
        buffer.putInt(appId)
        writeFString(buffer, appName)
        writeFString(buffer, buildVersion)
        writeFString(buffer, launchExe)
        writeFString(buffer, launchCommand)

        // Prerequisite IDs list
        buffer.putInt(prereqIds.size)
        prereqIds.forEach { writeFString(buffer, it) }

        writeFString(buffer, prereqName)
        writeFString(buffer, prereqPath)
        writeFString(buffer, prereqArgs)

        // Data version 1+ includes build ID
        if (dataVersion >= 1) {
            writeFString(buffer, buildId)
        }

        // Data version 2+ includes uninstall actions
        if (dataVersion >= 2) {
            writeFString(buffer, uninstallActionPath)
            writeFString(buffer, uninstallActionArgs)
        }

        val endPos = buffer.position()
        val size = endPos - startPos
        buffer.putInt(sizePos, size)
    }
}

/**
 * Chunk Data List - contains all downloadable chunks
 */
data class ChunkDataList(
    var version: Byte = 0,
    var size: Int = 0,
    var count: Int = 0,
    val elements: MutableList<ChunkInfo> = mutableListOf(),
    private var manifestVersion: Int = 18,
) {
    private val guidMap: MutableMap<String, Int> by lazy {
        elements.mapIndexed { index, chunk -> chunk.guidStr to index }.toMap(mutableMapOf())
    }

    private val guidIntMap: MutableMap<Pair<ULong, ULong>, Int> by lazy {
        elements.mapIndexed { index, chunk -> chunk.guidNum to index }.toMap(mutableMapOf())
    }

    fun getChunkByGuid(guid: String): ChunkInfo? = guidMap[guid.lowercase()]?.let { elements[it] }

    fun getChunkByGuidNum(guidNum: Pair<ULong, ULong>): ChunkInfo? = guidIntMap[guidNum]?.let { elements[it] }

    companion object {
        fun read(
            buffer: ByteBuffer,
            manifestVersion: Int,
        ): ChunkDataList {
            val cdl = ChunkDataList(manifestVersion = manifestVersion)
            val startPos = buffer.position()

            cdl.size = buffer.int
            cdl.version = buffer.get()
            cdl.count = buffer.int

            // Pre-allocate chunk list
            repeat(cdl.count) {
                cdl.elements.add(ChunkInfo(manifestVersion = manifestVersion))
            }

            // Read data in columnar format (all GUIDs, then all hashes, etc.)
            // GUIDs (128-bit each)
            cdl.elements.forEach { chunk ->
                chunk.guid = intArrayOf(buffer.int, buffer.int, buffer.int, buffer.int)
            }

            // Hashes (64-bit each)
            cdl.elements.forEach { chunk ->
                chunk.hash = buffer.long.toULong()
            }

            // SHA1 hashes (160-bit each)
            cdl.elements.forEach { chunk ->
                buffer.get(chunk.shaHash)
            }

            cdl.elements.forEach { chunk ->
                chunk.groupNum = buffer.get().toInt() and 0xFF
            }

            // Window sizes (32-bit each) - uncompressed size
            cdl.elements.forEach { chunk ->
                chunk.windowSize = buffer.int
            }

            // File sizes (64-bit each) - compressed download size
            cdl.elements.forEach { chunk ->
                chunk.fileSize = buffer.long
            }

            // Verify size
            val bytesRead = buffer.position() - startPos
            if (bytesRead != cdl.size) {
                buffer.position(startPos + cdl.size)
            }

            return cdl
        }
    }

    fun write(
        buffer: ByteBuffer,
        manifestVersion: Int,
    ) {
        val startPos = buffer.position()

        // Placeholder for size
        val sizePos = buffer.position()
        buffer.putInt(0)

        buffer.put(version)
        buffer.putInt(elements.size)

        // Write data in columnar format (all GUIDs, then all hashes, etc.)
        // GUIDs (128-bit each)
        elements.forEach { chunk ->
            chunk.guid.forEach { buffer.putInt(it) }
        }

        // Hashes (64-bit each)
        elements.forEach { chunk ->
            buffer.putLong(chunk.hash.toLong())
        }

        // SHA1 hashes (160-bit each)
        elements.forEach { chunk ->
            buffer.put(chunk.shaHash)
        }

        elements.forEach { chunk ->
            buffer.put(chunk.groupNum.toByte())
        }

        // Window sizes (32-bit each)
        elements.forEach { chunk ->
            buffer.putInt(chunk.windowSize)
        }

        // File sizes (64-bit each)
        elements.forEach { chunk ->
            buffer.putLong(chunk.fileSize)
        }

        val endPos = buffer.position()
        val size = endPos - startPos
        buffer.putInt(sizePos, size)
    }
}

/**
 * Information about a single chunk
 */
data class ChunkInfo(
    var guid: IntArray = IntArray(4),
    var hash: ULong = 0u,
    var shaHash: ByteArray = ByteArray(20),
    var groupNum: Int = 0,
    var windowSize: Int = 0,
    var fileSize: Long = 0,
    var useHashPrefixForV3: Boolean = false,
    private val manifestVersion: Int = 18,
) {
    val guidStr: String by lazy {
        guid.joinToString("-") { "%08x".format(it) }
    }

    // 128-bit GUID represented as Pair<ULong, ULong> (high 64 bits, low 64 bits)
    val guidNum: Pair<ULong, ULong> by lazy {
        val high = (guid[0].toULong() shl 32) or guid[1].toULong()
        val low = (guid[2].toULong() shl 32) or guid[3].toULong()
        Pair(high, low)
    }

    /**
     * Get the download path for this chunk
     * For V3/V4: subfolder is groupNum
     */
    fun getPath(chunkDir: String = getChunkDir(manifestVersion)): String {
        val guidHex = guid.joinToString("") { "%08X".format(it) }
        val hashHex = hash.toString(16).uppercase().padStart(16, '0')
        val subfolder =
            when (chunkDir) {
                "ChunksV3" -> {
                    if (useHashPrefixForV3) hashHex.substring(0, 2) else "%02d".format(groupNum)
                }

                else -> {
                    "%02d".format(groupNum)
                }
            }
        return "$chunkDir/$subfolder/${hashHex}_$guidHex.chunk"
    }

    companion object {
        private fun getChunkDir(version: Int): String {
            Timber.tag("EpicManifest").i("Found Manifest version: $version")
            return when {
                version >= 15 -> "ChunksV4"
                version >= 6 -> "ChunksV3"
                version >= 3 -> "ChunksV2"
                else -> "Chunks"
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ChunkInfo
        return guid.contentEquals(other.guid)
    }

    override fun hashCode(): Int = guid.contentHashCode()
}

/**
 * File Manifest List - contains all game files
 */
data class FileManifestList(
    var version: Byte = 0,
    var size: Int = 0,
    var count: Int = 0,
    val elements: MutableList<FileManifest> = mutableListOf(),
) {
    private val pathMap: MutableMap<String, Int> by lazy {
        elements.mapIndexed { index, fm -> fm.filename to index }.toMap(mutableMapOf())
    }

    fun getFileByPath(path: String): FileManifest? = pathMap[path]?.let { elements[it] }

    companion object {
        fun read(buffer: ByteBuffer): FileManifestList {
            val fml = FileManifestList()
            val startPos = buffer.position()

            fml.size = buffer.int
            fml.version = buffer.get()
            fml.count = buffer.int

            // Pre-allocate file list
            repeat(fml.count) {
                fml.elements.add(FileManifest())
            }

            // Read in columnar format
            // Filenames
            fml.elements.forEach { fm ->
                fm.filename = readFString(buffer)
            }

            // Symlink targets
            fml.elements.forEach { fm ->
                fm.symlinkTarget = readFString(buffer)
            }

            // SHA1 hashes
            fml.elements.forEach { fm ->
                buffer.get(fm.hash)
            }

            // Flags
            fml.elements.forEach { fm ->
                fm.flags = buffer.get().toInt() and 0xFF
            }

            // Install tags
            fml.elements.forEach { fm ->
                val tagCount = buffer.int
                fm.installTags = List(tagCount) { readFString(buffer) }
            }

            // Chunk parts
            fml.elements.forEach { fm ->
                val partCount = buffer.int
                var fileOffset = 0L

                repeat(partCount) {
                    val partStartPos = buffer.position()
                    val partSize = buffer.int

                    val part =
                        ChunkPart(
                            guid = intArrayOf(buffer.int, buffer.int, buffer.int, buffer.int),
                            offset = buffer.int,
                            size = buffer.int,
                            fileOffset = fileOffset,
                        )

                    fm.chunkParts.add(part)
                    fileOffset += part.size.toLong()

                    // Ensure we read the expected size
                    val partBytesRead = buffer.position() - partStartPos
                    if (partBytesRead < partSize) {
                        buffer.position(partStartPos + partSize)
                    }
                }

                fm.fileSize = fileOffset
            }

            // Version 1+: MD5 hashes and MIME types
            if (fml.version >= 1) {
                fml.elements.forEach { fm ->
                    val hasMd5 = buffer.int
                    if (hasMd5 != 0) {
                        buffer.get(fm.hashMd5)
                    }
                }

                fml.elements.forEach { fm ->
                    fm.mimeType = readFString(buffer)
                }
            }

            // Version 2+: SHA256 hashes
            if (fml.version >= 2) {
                fml.elements.forEach { fm ->
                    buffer.get(fm.hashSha256)
                }
            }

            // Verify size
            val bytesRead = buffer.position() - startPos
            if (bytesRead != fml.size) {
                buffer.position(startPos + fml.size)
            }

            return fml
        }
    }

    fun write(buffer: ByteBuffer) {
        val startPos = buffer.position()

        // Placeholder for size
        val sizePos = buffer.position()
        buffer.putInt(0)

        buffer.put(version)
        buffer.putInt(elements.size)

        // Write in columnar format
        // Filenames
        elements.forEach { fm ->
            writeFString(buffer, fm.filename)
        }

        // Symlink targets
        elements.forEach { fm ->
            writeFString(buffer, fm.symlinkTarget)
        }

        // SHA1 hashes
        elements.forEach { fm ->
            buffer.put(fm.hash)
        }

        // Flags
        elements.forEach { fm ->
            buffer.put(fm.flags.toByte())
        }

        // Install tags
        elements.forEach { fm ->
            buffer.putInt(fm.installTags.size)
            fm.installTags.forEach { tag -> writeFString(buffer, tag) }
        }

        // Chunk parts
        elements.forEach { fm ->
            buffer.putInt(fm.chunkParts.size)

            fm.chunkParts.forEach { part ->
                val partStartPos = buffer.position()

                // Placeholder for part size
                val partSizePos = buffer.position()
                buffer.putInt(0)

                part.guid.forEach { buffer.putInt(it) }
                buffer.putInt(part.offset)
                buffer.putInt(part.size)

                val partEndPos = buffer.position()
                val partSize = partEndPos - partStartPos
                buffer.putInt(partSizePos, partSize)
            }
        }

        // Version 1+: MD5 hashes and MIME types
        if (version >= 1) {
            elements.forEach { fm ->
                val hasMd5 = if (fm.hashMd5.any { it != 0.toByte() }) 1 else 0
                buffer.putInt(hasMd5)
                if (hasMd5 != 0) {
                    buffer.put(fm.hashMd5)
                }
            }

            elements.forEach { fm ->
                writeFString(buffer, fm.mimeType)
            }
        }

        // Version 2+: SHA256 hashes
        if (version >= 2) {
            elements.forEach { fm ->
                buffer.put(fm.hashSha256)
            }
        }

        val endPos = buffer.position()
        val size = endPos - startPos
        buffer.putInt(sizePos, size)
    }
}

/**
 * Represents a single file in the manifest
 */
data class FileManifest(
    var filename: String = "",
    var symlinkTarget: String = "",
    var hash: ByteArray = ByteArray(20),
    var flags: Int = 0,
    var installTags: List<String> = emptyList(),
    var chunkParts: MutableList<ChunkPart> = mutableListOf(),
    var fileSize: Long = 0,
    var hashMd5: ByteArray = ByteArray(16),
    var mimeType: String = "",
    var hashSha256: ByteArray = ByteArray(32),
) {
    val isReadOnly: Boolean get() = (flags and 0x1) != 0
    val isCompressed: Boolean get() = (flags and 0x2) != 0
    val isExecutable: Boolean get() = (flags and 0x4) != 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FileManifest
        return filename == other.filename
    }

    override fun hashCode(): Int = filename.hashCode()
}

/**
 * Represents a part of a file that comes from a chunk
 */
data class ChunkPart(
    val guid: IntArray,
    val offset: Int,
    val size: Int,
    val fileOffset: Long,
) {
    val guidStr: String by lazy {
        guid.joinToString("-") { "%08x".format(it) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ChunkPart
        return guid.contentEquals(other.guid) && offset == other.offset
    }

    override fun hashCode(): Int {
        var result = guid.contentHashCode()
        result = 31 * result + offset
        return result
    }
}

/**
 * Custom fields in the manifest
 */
data class CustomFields(
    private val fields: MutableMap<String, String> = mutableMapOf(),
) {
    operator fun get(key: String): String? = fields[key]

    operator fun set(
        key: String,
        value: String,
    ) {
        fields[key] = value
    }

    /**
     * Worst-case bytes-on-wire for this section, used by `BinaryManifest.serialize()` to
     * size its scratch buffer. Each FString costs `length * 2 + 8` (UTF-16 pessimism +
     * length prefix + null terminator). Plus 9 bytes of section header (size + version +
     * count). Always returns at least 256 so the empty case still gets a slab of headroom.
     */
    fun estimatedSerializedSize(): Int {
        val payload = fields.entries.sumOf { (k, v) -> k.length * 2 + 8 + v.length * 2 + 8 }
        return maxOf(payload + 9, 256)
    }

    companion object {
        fun read(buffer: ByteBuffer): CustomFields {
            val cf = CustomFields()

            if (buffer.hasRemaining()) {
                val startPos = buffer.position()
                val size = buffer.int
                val version = buffer.get() // version byte — must be read to match write() layout
                val count = buffer.int

                val keys = Array(count) { readFString(buffer) }
                val values = Array(count) { readFString(buffer) }
                keys.forEachIndexed { i, key -> cf[key] = values[i] }

                // Skip any unread bytes in the section
                val bytesRead = buffer.position() - startPos
                if (bytesRead != size) {
                    buffer.position(startPos + size)
                }
            }

            return cf
        }
    }

    fun write(buffer: ByteBuffer) {
        val startPos = buffer.position()
        buffer.putInt(0) // placeholder for size
        buffer.put(0) // version byte — omitting it shifts all subsequent reads by 1 byte
        buffer.putInt(fields.size)

        fields.keys.forEach { key -> writeFString(buffer, key) }
        fields.values.forEach { value -> writeFString(buffer, value) }

        val endPos = buffer.position()
        val size = endPos - startPos
        buffer.putInt(startPos, size)
    }
}

/**
 * Read a variable-length string from the buffer (Epic's FString format)
 */
private fun readFString(buffer: ByteBuffer): String {
    val length = buffer.int

    return when {
        length < 0 -> {
            // UTF-16 encoded (negative length)
            val absLength = -length * 2
            val bytes = ByteArray(absLength - 2)
            buffer.get(bytes)
            buffer.position(buffer.position() + 2) // Skip null terminator
            String(bytes, Charsets.UTF_16LE)
        }

        length > 0 -> {
            // ASCII encoded
            val bytes = ByteArray(length - 1)
            buffer.get(bytes)
            buffer.position(buffer.position() + 1) // Skip null terminator
            String(bytes, Charsets.US_ASCII)
        }

        else -> {
            ""
        }
    }
}

/**
 * Write a variable-length string to the buffer (Epic's FString format)
 */
private fun writeFString(
    buffer: ByteBuffer,
    str: String,
) {
    if (str.isEmpty()) {
        buffer.putInt(0)
        return
    }

    val isAscii = str.all { it.code < 128 }

    if (isAscii) {
        // ASCII encoded
        val bytes = str.toByteArray(Charsets.US_ASCII)
        buffer.putInt(bytes.size + 1) // +1 for null terminator
        buffer.put(bytes)
        buffer.put(0) // null terminator
    } else {
        // UTF-16 encoded (negative length)
        val bytes = str.toByteArray(Charsets.UTF_16LE)
        buffer.putInt(-(bytes.size / 2 + 1)) // negative indicates UTF-16, +1 for null terminator
        buffer.put(bytes)
        buffer.put(0) // null terminator (2 bytes)
        buffer.put(0)
    }
}
