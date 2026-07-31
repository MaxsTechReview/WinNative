package com.winlator.cmod.feature.retro

import android.content.Context
import android.util.Log
import java.io.File
import java.util.zip.ZipFile

/**
 * Installs the 3D mod into the engine's own mod directory.
 *
 * The engine discovers mods by listing `mods/` inside its LOVE save directory,
 * so a mod has to be unpacked there as loose files -- it cannot be read out of
 * the bundle where it ships. This does that unpacking, and it has to be
 * WinNative that does it rather than any outside tool:
 *
 * On Android the save directory lives under the app's external files
 * directory, which is served through FUSE. Files written there by a different
 * uid are only half visible to the app afterwards -- the engine could list the
 * mod's directories and even see the file NAMES, but every regular file
 * stat'ed as absent, so the loader found the mod folder, failed to read its
 * manifest, and reported zero mods installed with no error to explain it.
 * Files the app writes itself do not have that problem. That is the whole
 * reason this class exists rather than a shell script.
 *
 * The mod ships in the retro bundle as a zip, which is also what the mod's own
 * packaging tool (modkit) produces, so what is installed here is byte-for-byte
 * what was published.
 */
object Gen1ModInstaller {
    private const val TAG = "WnGen1Mod"

    /** Matches the manifest id; the engine keys everything off this. */
    const val MOD_ID = "DRAMATIC_SHAPE"

    private const val SAVE_SUBDIR = "save/pokemon-love2d"

    /**
     * Written next to the installed mod so a reinstall only happens when the
     * bundle actually ships something different. Holds the source zip's size
     * and modification time, which is enough to notice an update without
     * hashing a few megabytes on every launch.
     */
    private const val STAMP = ".winnative-installed"

    fun modsDir(context: Context): File =
        File(context.getExternalFilesDir(null), "$SAVE_SUBDIR/mods")

    fun installedDir(context: Context): File = File(modsDir(context), MOD_ID)

    /** Where the bundle publishes the mod. */
    fun sourceZip(context: Context): File =
        File(RetroBundle.root(context), "data/gen1recomp-mods/$MOD_ID.zip")

    fun isInstalled(context: Context): Boolean =
        File(installedDir(context), "manifest.json").isFile

    /**
     * Unpacks the mod if it is missing or out of date. Returns true when the
     * mod is present and usable afterwards.
     *
     * Safe to call on every launch: the common case is one stat of the stamp
     * file. Runs on the caller's thread, so call it before the engine starts
     * rather than from the UI thread mid-frame.
     */
    fun ensureInstalled(context: Context): Boolean {
        val zip = sourceZip(context)
        if (!zip.isFile) {
            // Not an error: the bundle simply does not carry the mod, and the
            // engine runs perfectly well in 2D without it.
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
            // Replaced wholesale rather than merged: a file the new version
            // dropped would otherwise linger and still be loaded.
            target.deleteRecursively()
            target.mkdirs()
            unzipInto(zip, target)
            stamp.writeText(want)
            val ok = isInstalled(context)
            if (!ok) Log.w(TAG, "unpacked $MOD_ID but no manifest.json in it")
            ok
        }.getOrElse {
            Log.w(TAG, "could not install $MOD_ID: ${it.message}")
            // A half-unpacked mod is worse than none: the loader would read a
            // partial tree and fail in a way that looks like a broken mod.
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
                // Refuse anything that would escape the mod directory. The zip
                // comes from our own bundle, but path traversal is cheap to
                // rule out and expensive to discover later.
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
