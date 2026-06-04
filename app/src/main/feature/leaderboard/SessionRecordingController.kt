package com.winlator.cmod.feature.leaderboard

import android.app.Activity
import android.content.Context
import android.opengl.GLES20
import android.os.Build
import android.provider.Settings
import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.container.Shortcut
import com.winlator.cmod.runtime.display.ui.FrameRating
import com.winlator.cmod.runtime.system.PerformanceRecorder
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Activity-scoped owner of the per-session perf-stats collector and leaderboard submit.
 *
 * Lifecycle:
 *  1. [start] when the game session is ready (after window/container setup).
 *  2. [attachToFrameRating] each time a [FrameRating] view is constructed (HUD can be
 *     toggled on/off mid-session — re-attaching is idempotent).
 *  3. [stop] in the session-cleanup path. Submits to PGS if the user opted in.
 *
 * Single instance per Activity. Re-calling [start] after [stop] is treated as a new
 * session and resets state.
 */
class SessionRecordingController(
    activity: Activity,
) : FrameRating.FrameObserver {

    // Application context is what we actually need for long-lived work.
    private val appContext: Context = activity.applicationContext

    private val active = AtomicBoolean(false)
    @Volatile private var collector: SessionStatsCollector? = null
    @Volatile private var recorder: PerformanceRecorder? = null

    @JvmOverloads
    fun start(
        shortcut: Shortcut?,
        container: Container?,
        recordToFile: Boolean = false,
    ) {
        if (!active.compareAndSet(false, true)) return
        val ctx = appContext
        val gameSource = shortcut?.getExtra("game_source")?.takeIf { it.isNotBlank() } ?: "CUSTOM_GAME"
        val gameId = resolveGameId(shortcut, gameSource)
        val configFingerprint = buildContainerFingerprint(container, shortcut)
        val gpuRenderer = runCatching { GLES20.glGetString(GLES20.GL_RENDERER) }.getOrNull() ?: "unknown"
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE
        val androidId = runCatching {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull() ?: ""
        val flagsAtStart = buildStartFlags(container, shortcut)
        val c = SessionStatsCollector(
            context = ctx,
            gameSource = gameSource,
            gameId = gameId,
            containerConfigFingerprint = configFingerprint,
            gpuRendererString = gpuRenderer,
            socModelString = socModel,
            androidId = androidId,
            flagsAtStart = flagsAtStart,
        )
        c.start()
        collector = c

        if (recordToFile) {
            val gameName = shortcut?.name ?: shortcut?.path ?: "Untitled"
            val appVersion = runCatching {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
            }.getOrDefault("")
            val effective = buildEffectiveSettings(container, shortcut)
            val metadata = PerformanceRecorder.SessionMetadata(
                gameName = gameName,
                gameSource = gameSource,
                gameId = gameId,
                exePath = shortcut?.path,
                androidId = androidId,
                gpuRenderer = gpuRenderer,
                containerFingerprint = configFingerprint,
                effectiveSettings = effective,
                appVersion = appVersion,
            )
            val r = PerformanceRecorder(ctx, metadata)
            if (r.start()) recorder = r
        }
    }

    private fun buildEffectiveSettings(container: Container?, shortcut: Shortcut?): Map<String, String> {
        if (container == null) return emptyMap()
        return linkedMapOf(
            "screenSize" to container.screenSize.orEmpty(),
            "graphicsDriver" to container.graphicsDriver.orEmpty(),
            "graphicsDriverConfig" to container.graphicsDriverConfig.orEmpty(),
            "dxwrapper" to container.getExtra("dxwrapper"),
            "dxwrapperConfig" to container.getExtra("dxwrapperConfig"),
            "audioDriver" to container.audioDriver.orEmpty(),
            "wincomponents" to container.getExtra("wincomponents"),
            "emulator" to container.emulator.orEmpty(),
            "emulator64" to container.emulator64.orEmpty(),
            "box64Preset" to container.box64Preset.orEmpty(),
            "fexcorePreset" to container.getExtra("fexcorePreset"),
            "inputType" to container.inputType.toString(),
            "envVars" to container.envVars.orEmpty(),
        )
    }

    /**
     * Attach to a FrameRating instance — safe to call multiple times. Each call replaces
     * the observer slot in FrameRating; the previous observer is silently superseded.
     */
    fun attachToFrameRating(frameRating: FrameRating) {
        if (!active.get()) return
        frameRating.setFrameObserver(this)
    }

    override fun onFramePresent(nanoTime: Long) {
        collector?.onFramePresent(nanoTime)
        recorder?.recordFramePresent(nanoTime)
    }

    /**
     * Stop sampling. Idempotent.
     *
     * Note: this controller does not submit to PGS. The collector and recorder still
     * run because the local CSV recording feature ("Record performance to file")
     * remains independent.
     */
    fun stop() {
        if (!active.compareAndSet(true, false)) return
        recorder?.stop()
        recorder = null
        val c = collector ?: return
        c.stop()
        collector = null
    }

    private fun resolveGameId(shortcut: Shortcut?, gameSource: String): String {
        if (shortcut == null) return ""
        val storeId = when (gameSource) {
            "STEAM", "EPIC" -> shortcut.getExtra("app_id")?.takeIf { it.isNotBlank() }
            "GOG" -> shortcut.getExtra("gog_id")?.takeIf { it.isNotBlank() }
                ?: shortcut.getExtra("app_id")?.takeIf { it.isNotBlank() }
            else -> shortcut.path?.takeIf { it.isNotBlank() }
        }
        return storeId ?: shortcut.name ?: ""
    }

    /**
     * Build a stable, content-addressed fingerprint of the runtime config so that two
     * sessions on the same machine with identical settings produce the same hash. The
     * caller hashes this string into a 4-byte prefix via [PerfDigest.shortHash].
     */
    private fun buildContainerFingerprint(container: Container?, shortcut: Shortcut?): String {
        if (container == null) return shortcut?.path ?: ""
        // Uses only public getters / getExtra(key) — fields without dedicated accessors
        // come through the extras map. The exact set of keys is locked once shipped:
        // changing it would mean two installs with identical settings produce different
        // hashes, breaking leaderboard config-grouping. Append, never remove or rename.
        val parts = mutableListOf<String>()
        parts += "screen=${container.screenSize.orEmpty()}"
        parts += "gfx=${container.graphicsDriver.orEmpty()}"
        parts += "gfxcfg=${container.graphicsDriverConfig.orEmpty()}"
        parts += "dx=${container.getExtra("dxwrapper")}"
        parts += "dxcfg=${container.getExtra("dxwrapperConfig")}"
        parts += "audio=${container.audioDriver.orEmpty()}"
        parts += "wcomp=${container.getExtra("wincomponents")}"
        parts += "emu=${container.emulator.orEmpty()}"
        parts += "emu64=${container.emulator64.orEmpty()}"
        parts += "box64=${container.box64Preset.orEmpty()}"
        parts += "fex=${container.getExtra("fexcorePreset")}"
        parts += "input=${container.inputType}"
        return parts.joinToString("|")
    }

    private fun buildStartFlags(container: Container?, shortcut: Shortcut?): Int {
        var flags = 0
        val dxwrapper = container?.getExtra("dxwrapper")?.lowercase().orEmpty()
        if (dxwrapper.contains("dxvk")) flags = flags or PerfDigest.FLAG_DXVK_ENABLED
        if (dxwrapper.contains("vkd3d")) flags = flags or PerfDigest.FLAG_VKD3D_ENABLED
        val gfxConfig = container?.graphicsDriverConfig?.lowercase().orEmpty()
        if (gfxConfig.contains("fsr")) flags = flags or PerfDigest.FLAG_FSR_ENABLED
        val env = container?.envVars?.lowercase().orEmpty()
        if (env.contains("wineesync=1") || env.contains("wineesync_winlator=1")) {
            flags = flags or PerfDigest.FLAG_ESYNC_ENABLED
        }
        return flags
    }

    companion object {
        private const val TAG = "PerfController"
    }
}
