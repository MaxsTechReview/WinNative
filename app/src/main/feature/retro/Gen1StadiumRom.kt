package com.winlator.cmod.feature.retro

import android.content.Context
import android.net.Uri
import java.io.File

object Gen1StadiumRom {
    const val ROW_ID = "DRAMATIC_SHAPE:stadiumRom"

    private const val SAVE_SUBDIR = "save/pokemon-love2d"
    private const val PICKED = "picked_stadium.z64"
    private const val PACK_SUBDIR = "dramatic_shape/stadium"
    private const val ROM_SUBDIR = "baseroms"
    private const val MIN_ROM_BYTES = 4L * 1024 * 1024

    private val MAGICS =
        listOf(
            byteArrayOf(0x80.toByte(), 0x37, 0x12, 0x40),
            byteArrayOf(0x37, 0x80.toByte(), 0x40, 0x12),
            byteArrayOf(0x40, 0x12, 0x37, 0x80.toByte()),
        )

    fun saveRoot(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, SAVE_SUBDIR)

    private fun packDir(context: Context): File = File(saveRoot(context), PACK_SUBDIR)

    private fun romDir(context: Context): File = File(saveRoot(context), ROM_SUBDIR)

    fun isBuilding(value: String): Boolean = value.trim().equals("BUILDING", ignoreCase = true)

    fun isInstalled(context: Context): Boolean {
        val marker = File(packDir(context), "pack.info")
        if (!marker.isFile) return false
        return runCatching { marker.readText().trim().isNotEmpty() }.getOrDefault(false)
    }

    fun stage(
        context: Context,
        uri: Uri,
    ): Result<Long> =
        runCatching {
            val root = saveRoot(context).apply { mkdirs() }
            val staging = File(root, "$PICKED.part")
            staging.delete()

            val copied =
                context.contentResolver.openInputStream(uri)?.use { input ->
                    staging.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("Could not read that file")

            if (copied < MIN_ROM_BYTES) {
                staging.delete()
                throw IllegalArgumentException("That file is too small to be a Nintendo 64 ROM")
            }
            if (!looksLikeN64(staging)) {
                staging.delete()
                throw IllegalArgumentException("That is not a Nintendo 64 ROM (.z64, .v64 or .n64)")
            }

            val target = File(root, PICKED)
            target.delete()
            if (!staging.renameTo(target)) {
                staging.delete()
                throw IllegalStateException("Could not hand the ROM to the engine")
            }
            copied
        }

    private fun looksLikeN64(file: File): Boolean =
        runCatching {
            val head = ByteArray(4)
            file.inputStream().use { if (it.read(head) != 4) return false }
            MAGICS.any { magic -> magic.contentEquals(head) }
        }.getOrDefault(false)

    fun hasStagedPick(context: Context): Boolean = File(saveRoot(context), PICKED).isFile

    fun delete(context: Context): Boolean =
        runCatching {
            var removed = packDir(context).deleteRecursively()
            val roms = romDir(context)
            if (roms.isDirectory) {
                roms.listFiles().orEmpty().forEach { if (it.isFile && it.delete()) removed = true }
            }
            File(saveRoot(context), PICKED).delete()
            File(saveRoot(context), "$PICKED.part").delete()
            removed
        }.getOrDefault(false)
}
