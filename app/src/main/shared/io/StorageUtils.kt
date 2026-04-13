package com.winlator.cmod.shared.io
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes

object StorageUtils {
    fun getAvailableSpace(path: String): Long {
        var file: File? = File(path)
        while (file != null && !file.exists()) {
            file = file.parentFile
        }
        if (file == null) {
            throw IllegalArgumentException("Invalid path: $path")
        }
        val stat = StatFs(file.absolutePath)
        return stat.blockSizeLong * stat.availableBlocksLong
    }

    suspend fun getFolderSize(folderPath: String): Long {
        val folder = File(folderPath)
        if (folder.exists()) {
            var bytes = 0L
            val tree = folder.walk()
            tree.forEach {
                bytes += it.length()
                yield()
            }
            return bytes
        }
        return 0L
    }

    fun formatBinarySize(
        bytes: Long,
        decimalPlaces: Int = 2,
    ): String {
        require(bytes > Long.MIN_VALUE) { "Out of range" }
        require(decimalPlaces >= 0) { "Negative decimal places unsupported" }

        val isNegative = bytes < 0
        val absBytes = kotlin.math.abs(bytes)

        if (absBytes < 1024) {
            return "$bytes B"
        }

        val units = arrayOf("KiB", "MiB", "GiB", "TiB", "PiB")
        val digitGroups = (63 - absBytes.countLeadingZeroBits()) / 10
        val value = absBytes.toDouble() / (1L shl (digitGroups * 10))

        val result =
            "%.${decimalPlaces}f %s".format(
                if (isNegative) -value else value,
                units[digitGroups - 1],
            )

        return result
    }
}
