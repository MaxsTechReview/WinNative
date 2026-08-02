package com.winlator.cmod.feature.retro

import android.content.Context
import android.util.Log
import java.io.File
import java.util.zip.ZipFile

object Gen1ModInstaller {
    private const val TAG = "WnGen1Mod"

    const val MOD_ID = "DRAMATIC_SHAPE"

    private const val SAVE_SUBDIR = "save/pokemon-love2d"

    private const val STAMP = ".winnative-installed"

    fun modsDir(context: Context): File =
        File(context.getExternalFilesDir(null), "$SAVE_SUBDIR/mods")

    fun installedDir(context: Context): File = File(modsDir(context), MOD_ID)

    fun sourceZip(context: Context): File =
        File(RetroBundle.root(context), "data/gen1recomp-mods/$MOD_ID.zip")

    fun isInstalled(context: Context): Boolean =
        File(installedDir(context), "manifest.json").isFile

    fun ensureInstalled(context: Context): Boolean {
        val zip = sourceZip(context)
        if (!zip.isFile) {
            Log.i(TAG, "no mod in bundle at ${zip.absolutePath}")
            return isInstalled(context)
        }

        val target = installedDir(context)
        val stamp = File(target, STAMP)
        val want = "${zip.length()}:${zip.lastModified()}"
        if (isInstalled(context) && runCatching { stamp.readText() }.getOrNull() == want) {
            return true
        }

        Log.i(TAG, "installing $MOD_ID from ${zip.name}")
        return runCatching {
            target.deleteRecursively()
            target.mkdirs()
            unzipInto(zip, target)
            stamp.writeText(want)
            val ok = isInstalled(context)
            if (!ok) Log.w(TAG, "unpacked $MOD_ID but no manifest.json in it")
            ok
        }.getOrElse {
            Log.w(TAG, "could not install $MOD_ID: ${it.message}")
            runCatching { target.deleteRecursively() }
            false
        }
    }

    private fun unzipInto(zip: File, target: File) {
        val root = target.canonicalPath
        ZipFile(zip).use { archive ->
            val entries = archive.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val out = File(target, entry.name)
                if (!out.canonicalPath.startsWith(root + File.separator) &&
                    out.canonicalPath != root
                ) {
                    throw SecurityException("zip entry escapes mod directory: ${entry.name}")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                    continue
                }
                out.parentFile?.mkdirs()
                archive.getInputStream(entry).use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
            }
        }
    }
}
