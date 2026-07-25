package com.winlator.cmod.shared.io

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream

object ArchiveExtractor {
    private const val BUFFER_SIZE = 1 shl 16
    private const val TAR_HEADER_SIZE = 512

    private val COMPOUND_SUFFIXES =
        listOf(".tar.gz", ".tar.bz2", ".tar.xz", ".tar.zst", ".tar.zstd")

    private val SIMPLE_SUFFIXES =
        listOf(
            ".zip", ".7z", ".tar",
            ".tgz", ".tbz2", ".tbz", ".txz", ".tzst",
            ".gz", ".bz2", ".xz", ".zst", ".zstd",
        )

    fun isSupported(file: File): Boolean {
        if (!file.isFile) return false
        val name = file.name.lowercase(Locale.ROOT)
        return COMPOUND_SUFFIXES.any { name.endsWith(it) } || SIMPLE_SUFFIXES.any { name.endsWith(it) }
    }

    fun baseName(file: File): String {
        val name = file.name
        val lower = name.lowercase(Locale.ROOT)
        val suffix =
            COMPOUND_SUFFIXES.firstOrNull { lower.endsWith(it) }
                ?: SIMPLE_SUFFIXES.firstOrNull { lower.endsWith(it) }
        val stripped = if (suffix != null) name.dropLast(suffix.length) else name
        return stripped.ifBlank { name }
    }

    @Throws(IOException::class)
    fun extract(
        source: File,
        destDir: File,
        onProgress: (Float) -> Unit,
        isActive: () -> Boolean,
    ) {
        if (!destDir.exists() && !destDir.mkdirs()) throw IOException("Cannot create ${destDir.name}")
        val name = source.name.lowercase(Locale.ROOT)
        when {
            name.endsWith(".zip") -> extractZip(source, destDir, onProgress, isActive)
            name.endsWith(".7z") -> extractSevenZ(source, destDir, onProgress, isActive)
            else -> extractStream(source, destDir, onProgress, isActive)
        }
    }

    private fun extractZip(
        source: File,
        destDir: File,
        onProgress: (Float) -> Unit,
        isActive: () -> Boolean,
    ) {
        val total = source.length().coerceAtLeast(1L)
        val counting = CountingInputStream(FileInputStream(source))
        ZipArchiveInputStream(BufferedInputStream(counting, BUFFER_SIZE)).use { zip ->
            val reporter = ProgressReporter(onProgress)
            while (true) {
                if (!isActive()) throw CancellationException()
                val entry = zip.nextEntry ?: break
                if (!zip.canReadEntryData(entry)) continue
                val target = safeChild(destDir, entry.name) ?: continue
                if (entry.isDirectory) {
                    target.mkdirs()
                    continue
                }
                writeEntry(zip, target, isActive) { reporter.report(counting.count.toFloat() / total) }
            }
        }
        onProgress(1f)
    }

    private fun extractSevenZ(
        source: File,
        destDir: File,
        onProgress: (Float) -> Unit,
        isActive: () -> Boolean,
    ) {
        SevenZFile.builder().setFile(source).get().use { archive ->
            val total = archive.entries.sumOf { it.size.coerceAtLeast(0L) }.coerceAtLeast(1L)
            var done = 0L
            val reporter = ProgressReporter(onProgress)
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                if (!isActive()) throw CancellationException()
                val entry = archive.nextEntry ?: break
                val target = safeChild(destDir, entry.name) ?: continue
                if (entry.isDirectory) {
                    target.mkdirs()
                    continue
                }
                target.parentFile?.mkdirs()
                target.outputStream().use { out ->
                    while (true) {
                        if (!isActive()) throw CancellationException()
                        val read = archive.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        done += read
                        reporter.report(done.toFloat() / total)
                    }
                }
            }
        }
        onProgress(1f)
    }

    private fun extractStream(
        source: File,
        destDir: File,
        onProgress: (Float) -> Unit,
        isActive: () -> Boolean,
    ) {
        val total = source.length().coerceAtLeast(1L)
        val counting = CountingInputStream(FileInputStream(source))
        val decompressed = wrapCompressor(source, BufferedInputStream(counting, BUFFER_SIZE))
        BufferedInputStream(decompressed, BUFFER_SIZE).use { stream ->
            val reporter = ProgressReporter(onProgress)
            val progress = { reporter.report(counting.count.toFloat() / total) }
            if (looksLikeTar(stream)) {
                extractTar(stream, destDir, isActive, progress)
            } else {
                val target = File(destDir, baseName(source))
                writeEntry(stream, target, isActive, progress)
            }
        }
        onProgress(1f)
    }

    private fun wrapCompressor(
        source: File,
        stream: InputStream,
    ): InputStream {
        val name = source.name.lowercase(Locale.ROOT)
        return when {
            name.endsWith(".gz") || name.endsWith(".tgz") -> GzipCompressorInputStream(stream, true)
            name.endsWith(".bz2") || name.endsWith(".tbz2") || name.endsWith(".tbz") ->
                BZip2CompressorInputStream(stream, true)
            name.endsWith(".xz") || name.endsWith(".txz") -> XZCompressorInputStream(stream, true)
            name.endsWith(".zst") || name.endsWith(".zstd") || name.endsWith(".tzst") ->
                ZstdCompressorInputStream(stream)
            else -> stream
        }
    }

    private fun looksLikeTar(stream: BufferedInputStream): Boolean {
        stream.mark(TAR_HEADER_SIZE + 1)
        val header = ByteArray(TAR_HEADER_SIZE)
        var read = 0
        while (read < TAR_HEADER_SIZE) {
            val n = stream.read(header, read, TAR_HEADER_SIZE - read)
            if (n < 0) break
            read += n
        }
        stream.reset()
        if (read < TAR_HEADER_SIZE) return false
        val magic = String(header, 257, 5, Charsets.US_ASCII)
        return magic == "ustar"
    }

    private fun extractTar(
        stream: InputStream,
        destDir: File,
        isActive: () -> Boolean,
        onProgress: () -> Unit,
    ) {
        TarArchiveInputStream(stream).use { tar ->
            while (true) {
                if (!isActive()) throw CancellationException()
                val entry = tar.nextEntry ?: break
                if (entry.isSymbolicLink || entry.isLink) continue
                val target = safeChild(destDir, entry.name) ?: continue
                if (entry.isDirectory) {
                    target.mkdirs()
                    continue
                }
                if (!entry.isFile) continue
                writeEntry(tar, target, isActive, onProgress)
            }
        }
    }

    private fun writeEntry(
        stream: InputStream,
        target: File,
        isActive: () -> Boolean,
        onProgress: () -> Unit,
    ) {
        target.parentFile?.mkdirs()
        val buffer = ByteArray(BUFFER_SIZE)
        target.outputStream().use { out ->
            while (true) {
                if (!isActive()) throw CancellationException()
                val read = stream.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                onProgress()
            }
        }
    }

    private fun safeChild(
        destDir: File,
        entryName: String,
    ): File? {
        val normalized = entryName.replace('\\', '/').trim()
        if (normalized.isEmpty() || normalized.startsWith("/")) return null
        if (normalized.split('/').any { it == ".." }) return null
        val target = File(destDir, normalized)
        val root = destDir.canonicalPath
        val path = target.canonicalPath
        return if (path == root || path.startsWith(root + File.separator)) target else null
    }

    private class ProgressReporter(private val onProgress: (Float) -> Unit) {
        private var lastPercent = -1

        fun report(fraction: Float) {
            val clamped = fraction.coerceIn(0f, 1f)
            val percent = (clamped * 100).toInt()
            if (percent != lastPercent) {
                lastPercent = percent
                onProgress(clamped)
            }
        }
    }

    private class CountingInputStream(private val delegate: InputStream) : InputStream() {
        var count = 0L
            private set

        override fun read(): Int {
            val b = delegate.read()
            if (b >= 0) count++
            return b
        }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int {
            val n = delegate.read(b, off, len)
            if (n > 0) count += n
            return n
        }

        override fun available(): Int = delegate.available()

        override fun close() = delegate.close()
    }
}
