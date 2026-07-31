package com.winlator.cmod.feature.retro

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.winlator.cmod.runtime.container.Shortcut
import java.io.File
import java.security.MessageDigest

/**
 * Launches a compatible Game Boy title into the 3D engine instead of the
 * libretro core, mirroring DolphinEmbedLaunch: WinNative resolves the settings
 * and hands them to the hosting activity, so the engine never shows a UI of its
 * own.
 *
 * Compatibility is decided by the ROM's SHA-1 rather than its filename, because
 * that is what the engine itself verifies on import -- a renamed or hacked dump
 * that would be rejected there must not be offered the toggle here.
 */
object Gen1EmbedLaunch {
    /** Per-game extra: "1" launches into the 3D engine. */
    const val KEY_ENGINE_3D = "retro_engine_3d"

    /** Mod id of the voxel renderer, as it appears in the engine's options. */
    const val VOXEL_MOD_ID = "DRAMATIC_SHAPE"

    private val COMPATIBLE = mapOf(
        "ea9bcae617fdf159b045185467ae58b2e4a48b9a" to "red",
        "d7037c83e1ae5b39bde3c30787637ba1d4c48ce2" to "blue",
        "cc7d03262ebfaf2f06772c1a480c7d9d5f4a38e1" to "yellow",
    )

    /**
     * The engine's version id for this ROM, or null when the ROM is not one of
     * the three the engine accepts. Hashing a 1 MiB file is cheap, but this is
     * still called off the UI thread by its callers.
     */
    fun versionForRom(rom: File): String? {
        if (!rom.isFile || rom.length() != 1024L * 1024L) return null
        val digest = MessageDigest.getInstance("SHA-1")
        rom.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        val sha1 = digest.digest().joinToString("") { "%02x".format(it) }
        return COMPATIBLE[sha1]
    }

    /** Whether this shortcut is a game the 3D engine can run at all. */
    fun isCompatible(context: Context, shortcut: Shortcut): Boolean =
        Gen1EngineActivity.isInstalled(context) &&
            versionForRom(File(RetroShortcuts.romPath(shortcut))) != null

    /** Whether the user has actually turned the 3D toggle on for this game. */
    fun isEnabled(shortcut: Shortcut): Boolean =
        shortcut.getExtra(KEY_ENGINE_3D) == "1"

    fun shouldLaunch(context: Context, shortcut: Shortcut): Boolean =
        isEnabled(shortcut) && isCompatible(context, shortcut)

    fun launch(context: Context, shortcut: Shortcut) {
        val intent = launchIntent(context, shortcut) ?: return
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * The Intent for this shortcut, or null if the 3D engine cannot run it.
     *
     * Deciding and building are one step on purpose. Both need the ROM's
     * version, and working that out means hashing the file -- so a caller that
     * asked [shouldLaunch] first and then built the Intent hashed the same
     * megabyte twice, and the second time was usually on the main thread. Call
     * this from a background thread and start the Intent it returns.
     */
    fun launchIntentIfSupported(context: Context, shortcut: Shortcut): Intent? =
        if (Gen1EngineActivity.isInstalled(context)) launchIntent(context, shortcut) else null

    fun launchIntent(context: Context, shortcut: Shortcut): Intent? {
        val rom = File(RetroShortcuts.romPath(shortcut))
        val version = versionForRom(rom) ?: return null

        return Intent(context, Gen1EngineActivity::class.java).apply {
            // GameActivity takes its game path from the Intent data when the
            // embed resource is false, which is how the engine archive can live
            // in the retro bundle and still be found.
            data = Uri.fromFile(Gen1EngineActivity.gameArchive(context))
            putExtra(Gen1EngineActivity.EXTRA_ROM_PATH, rom.absolutePath)
            putExtra(Gen1EngineActivity.EXTRA_VERSION, version)
            putExtra(
                Gen1EngineActivity.EXTRA_GAME_NAME,
                shortcut.getExtra("custom_name", shortcut.name),
            )
            putExtra(Gen1EngineActivity.EXTRA_SHORTCUT_PATH, shortcut.file.absolutePath)
            // The loading screen shown during a first-boot ROM import uses the
            // game's own artwork, so the player sees the game they picked
            // rather than the engine's splash.
            putExtra(
                Gen1EngineActivity.EXTRA_ARTWORK_PATH,
                shortcut.getExtra("customCoverArtPath"),
            )
        }
    }
}
