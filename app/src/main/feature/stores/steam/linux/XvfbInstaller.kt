package com.winlator.cmod.feature.stores.steam.linux

import android.content.Context
import android.util.Log
import com.winlator.cmod.runtime.display.environment.ImageFs
import com.winlator.cmod.shared.io.FileUtils
import java.io.File
import java.io.IOException
import java.security.MessageDigest

// Stages an Xvfb (X virtual framebuffer) X server inside the sniper-arm64
// rootfs so Steam's chromehtml.so / CEF host can connect to a "real" X
// server with all extensions Chromium probes (RENDER, GLX, XKEYBOARD,
// XInputExtension, XFIXES, GenericEventExtension, DAMAGE, COMPOSITE).
// Round-21 Path C (Codex round-20 ranked highest, ~80% likelihood): spawn
// Xvfb inside proot before Steam exec; Steam connects to that display,
// and the Proton wrapper resets DISPLAY=:0 for the GAME so the game
// window surfaces to our Java X server. Display number is dynamic in
// round-21d (Xvfb -displayfd picks the first free number).
// Sniper-arm64 doesn't ship Xvfb. We bundle the Debian bullseye arm64
// libXfont2, libfontenc, libxkbfile) plus xkbcomp (Xvfb fork+execs it on
// every client XKB request). xkb data files (/usr/share/X11/xkb/...)
// already exist in sniper. Total bundle: ~2.9 MB.
// Sources: Debian 11 (bullseye) arm64:
//   xvfb_1.20.11-1+deb11u13_arm64.deb
//   x11-xkb-utils_7.7+5_arm64.deb
//   libpixman-1-0_0.40.0-1.1~deb11u1_arm64.deb
//   libXfont2_2.0.4-1_arm64.deb
//   libfontenc1_1.1.4-1_arm64.deb
//   libxkbfile1_1.1.0-1_arm64.deb
object XvfbInstaller {

    private const val TAG = "XvfbInstaller"
    private const val ASSET_DIR = "xvfb-arm64"

    // Sniper has merged-/usr (usr -> .). We write to files/bin and
    // files/lib/aarch64-linux-gnu directly.
    private const val BIN_DIR_REL = "opt/steam-runtime/files/bin"
    private const val LIB_DIR_REL = "opt/steam-runtime/files/lib/aarch64-linux-gnu"
    private const val DBUS_SHARE_DIR_REL = "opt/steam-runtime/files/share/dbus-1"

    private val BINARIES = listOf("Xvfb", "xkbcomp")

    // versioned filename to SONAME the dynamic linker resolves.
    // libunwind.so.8 added in round-21b after Xvfb smoke-test failed with
    // "libunwind.so.8: cannot open shared object file" (sniper doesn't ship
    // it; codex round-21 flagged this as a likely missing dep).
    private val LIBS = listOf(
        "libpixman-1.so.0.40.0" to "libpixman-1.so.0",
        "libXfont2.so.2.0.0" to "libXfont2.so.2",
        "libfontenc.so.1.0.0" to "libfontenc.so.1",
        "libxkbfile.so.1.0.2" to "libxkbfile.so.1",
        "libunwind.so.8.0.1" to "libunwind.so.8",
        "libapparmor.so.1.6.3" to "libapparmor.so.1",
    )

    private val EXTRA_LIBS = listOf(
        "libwinnative-setxid-noop.so",
        "libwinnative-steamwebhelper-preload.so",
    )

    private val EXTRA_EXECUTABLES = listOf(
        "winnative-steamwebhelper-wrapper",
        "winnative-driverquery-noop",
    )

    private val DBUS_BINARIES = listOf("dbus-daemon", "dbus-uuidgen")

    private val EXPECTED_SHA256 = mapOf(
        "Xvfb" to "1aaaefd376c73197460a7b1c62e0380657fa55bc7a7183fa821b5b27e6aaa1be",
        "xkbcomp" to "bb001ddc4851935aad62d6ed15541c3d230d35e10f76548454640630843b7098",
        "libpixman-1.so.0.40.0" to "e1dfb301bdc6b510d71a581f7cec1547def276fb3b50670147e89c1e42d3c0bc",
        "libXfont2.so.2.0.0" to "9d75b5331f13968169ebc73e2b1c8ee8753142eb3524340e64afd8e4eedfbd06",
        "libfontenc.so.1.0.0" to "70d9bc8d141ed7ce1ffbcb5f8e396a7173b8709ab5dbe96b4c584a06ad5448c1",
        "libxkbfile.so.1.0.2" to "8d8462b7082bd7eaf1ac03e0ea195e81c2bcd52d16db7a7bcd1a258c7322ad58",
        "libunwind.so.8.0.1" to "a6f1a0cb272655f942ae2f92334bd6b16469d0653ba533a91a7b2fe9005efe24",
        "libapparmor.so.1.6.3" to "bedf95282b1fca7a40593d555b25bf73c335183c23f78a4286ca18e2ba496210",
        "libwinnative-setxid-noop.so" to "64ff14864fcaaceb6801ea0e43e12a8885bc4b1ad4ab985275c58d0acc9d720f",
        "libwinnative-steamwebhelper-preload.so" to "adc7371649d2bc27cf4872795400e3d049670fd8a089f6efba7023037b86e8d8",
        "winnative-steamwebhelper-wrapper" to "4fbc0819b4a05de9eefa08f0d7ac358e81327132d793d983e6e258665fe4a1b6",
        "winnative-driverquery-noop" to "3e88d82b42e0809f8fc1b3a6711062a97e2a05c57e85c1cbc01f27240c2df47d",
        "dbus-daemon" to "2f50d490d9ce4e707b2253669e6f353da785a1b944bd9f10cbb98a6314dd8796",
        "dbus-uuidgen" to "e35b5c45f75d58c0fe6d77981810858ca4988cc34d3726be893d5496b6ece45a",
        "session.conf" to "5fd29a946587bc384d14859552bbdfd965ad1bdbc50db60b1b56b9ce8454711c",
    )

    fun binDir(context: Context): File =
        File(ImageFs.find(context).rootDir, BIN_DIR_REL)

    fun libDir(context: Context): File =
        File(ImageFs.find(context).rootDir, LIB_DIR_REL)

    private fun dbusShareDir(context: Context): File =
        File(ImageFs.find(context).rootDir, DBUS_SHARE_DIR_REL)

    fun xvfbBinary(context: Context): File =
        File(binDir(context), "Xvfb")

    @Throws(IOException::class)
    fun ensureInstalled(context: Context) {
        val bin = binDir(context)
        val lib = libDir(context)

        if (!bin.parentFile!!.exists()) {
            throw IOException(
                "sniper rootfs missing: " + bin.parentFile +
                    " - was the runtime installed?",
            )
        }
        if (!lib.exists()) {
            throw IOException(
                "sniper lib dir missing: " + lib + " - was the runtime installed?",
            )
        }
        bin.mkdirs()

        for (name in BINARIES) {
            val asset = ASSET_DIR + "/" + name
            val target = File(bin, name)
            stageAsset(context, asset, target, executable = true)
        }

        for ((versioned, soname) in LIBS) {
            val asset = ASSET_DIR + "/" + versioned
            val targetVer = File(lib, versioned)
            val targetSoname = File(lib, soname)
            stageAsset(context, asset, targetVer, executable = false)
            ensureSymlink(targetSoname, versioned)
        }

        for (name in EXTRA_LIBS) {
            val asset = ASSET_DIR + "/" + name
            val target = File(lib, name)
            stageAsset(context, asset, target, executable = false)
        }

        for (name in EXTRA_EXECUTABLES) {
            val asset = ASSET_DIR + "/" + name
            val target = File(lib, name)
            stageAsset(context, asset, target, executable = true)
        }

        for (name in DBUS_BINARIES) {
            val asset = "$ASSET_DIR/dbus/$name"
            val target = File(bin, name)
            stageAsset(context, asset, target, executable = true)
        }

        stageAsset(
            context,
            "$ASSET_DIR/dbus/session.conf",
            File(dbusShareDir(context), "session.conf"),
            executable = false,
        )

        Log.i(TAG, "Xvfb/dbus arm64 staged: bin=" + bin + ", libs=" + lib)
    }

    private fun stageAsset(
        context: Context,
        asset: String,
        dest: File,
        executable: Boolean,
    ) {
        val expected = EXPECTED_SHA256[dest.name]
            ?: throw IOException("no expected sha256 for " + dest.name)
        val current = if (dest.exists()) sha256(dest) else null
        if (current.equals(expected, ignoreCase = true)) {
            applyMode(dest, executable)
            return
        }
        copyAsset(context, asset, dest)
        val got = sha256(dest)
        if (!got.equals(expected, ignoreCase = true)) {
            dest.delete()
            throw IOException(
                "asset sha256 mismatch for " + asset +
                    ": expected " + expected + " got " + got,
            )
        }
        applyMode(dest, executable)
    }

    private fun copyAsset(context: Context, asset: String, dest: File) {
        dest.parentFile?.mkdirs()
        val tmp = File(
            dest.parentFile,
            dest.name + "." + android.os.Process.myPid() + "." +
                System.nanoTime() + ".part",
        )
        tmp.delete()
        try {
            context.assets.open(asset).use { input ->
                tmp.outputStream().buffered().use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                throw IOException("Could not promote " + tmp + " -> " + dest)
            }
        } finally {
            tmp.delete()
        }
    }

    private fun ensureSymlink(link: File, target: String) {
        val path = link.toPath()
        if (java.nio.file.Files.isSymbolicLink(path)) {
            val current = java.nio.file.Files.readSymbolicLink(path).toString()
            if (current == target) return
            link.delete()
        } else if (link.exists()) {
            link.delete()
        }
        FileUtils.symlink(target, link.absolutePath)
        if (!java.nio.file.Files.isSymbolicLink(path) ||
            java.nio.file.Files.readSymbolicLink(path).toString() != target
        ) {
            throw IOException("symlink " + link.absolutePath + " -> " + target + " failed")
        }
    }

    private fun applyMode(file: File, executable: Boolean) {
        if (!file.setReadable(true, false)) {
            Log.w(TAG, "could not set readable: " + file)
        }
        if (executable && !file.setExecutable(true, false)) {
            Log.w(TAG, "could not set executable: " + file)
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
