package com.winlator.cmod.feature.retro

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

object RetroBiosImport {
    private val KNOWN_BIOS: Map<String, String> =
        mapOf(
            "8dd7d5296a650fac7319bce665a6a53c" to "scph5500.bin",
            "490f666e1afb15b7362b406ed1cea246" to "scph5501.bin",
            "32736f17079d0b2b7024407c39bd3050" to "scph5502.bin",
            "924e392ed05558ffdb115408c263dccf" to "scph1001.bin",
            "1e68c231d0896b7eadcad1d7d8e76129" to "scph7001.bin",
        )

    fun importFromUri(
        context: Context,
        uri: Uri,
    ): Result<String> =
        runCatching {
            val bytes =
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not read file")
            val md5 = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
            val canonical =
                KNOWN_BIOS[md5]
                    ?: throw IllegalArgumentException("Not a recognized PlayStation BIOS")
            val dir = RetroCoreManager.systemDir(context)
            val target = File(dir, canonical)
            val tmp = File(dir, "$canonical.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                target.writeBytes(bytes)
                tmp.delete()
            }
            canonical
        }

    fun ps2BiosDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "bios").apply { mkdirs() }

    fun installedPs2Bios(context: Context): List<String> =
        ps2BiosDir(context).listFiles().orEmpty()
            .filter { it.isFile && it.length() >= 3L * 1024 * 1024 }
            .map { it.name }
            .sorted()

    fun deletePs1Bios(context: Context): Int {
        val dir = RetroCoreManager.systemDir(context)
        var removed = 0
        RetroSystems.PSX.biosFiles.forEach { val f = File(dir, it); if (f.isFile && f.delete()) removed++ }
        return removed
    }

    fun deletePs2Bios(context: Context): Int {
        var removed = 0
        ps2BiosDir(context).listFiles().orEmpty().forEach { if (it.isFile && it.delete()) removed++ }
        context.getSharedPreferences("ARMSX2", Context.MODE_PRIVATE).edit().remove("bios").apply()
        return removed
    }

    fun importPs2FromUri(
        context: Context,
        uri: Uri,
    ): Result<String> =
        runCatching {
            val name =
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: "ps2-bios.bin"
            val bytes =
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not read file")
            if (bytes.size !in (3 * 1024 * 1024)..(8 * 1024 * 1024)) {
                throw IllegalArgumentException("Not a PS2 BIOS — expected a single merged .bin dump (3–8MB), not a split ROM0/MEC/NVM set.")
            }
            val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
            if (!head.contains("PS2", true) && !head.contains("Sony", true) && !head.contains("ROMDIR", true)) {
                throw IllegalArgumentException("This file doesn't look like a PS2 BIOS dump.")
            }
            val safe = name.ifBlank { "ps2-bios.bin" }.let { if (it.endsWith(".bin", true)) it else "$it.bin" }
            val target = File(ps2BiosDir(context), safe)
            val tmp = File(ps2BiosDir(context), "$safe.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                target.writeBytes(bytes)
                tmp.delete()
            }
            context.getSharedPreferences("ARMSX2", Context.MODE_PRIVATE)
                .edit().putString("bios", target.absolutePath).apply()
            safe
        }
}
