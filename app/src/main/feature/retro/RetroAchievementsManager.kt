package com.winlator.cmod.feature.retro

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.swordfish.libretrodroid.RetroAchievements
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class RetroAchievement(
    val id: Long,
    val title: String,
    val description: String,
    val unlocked: Boolean,
    val points: Int,
    val progress: String,
    val badgeUrl: String,
    val badgeLockedUrl: String,
    val bucket: Int,
)

data class RetroGameSummary(
    val gameId: Long,
    val title: String,
    val badgeUrl: String,
    val total: Int,
    val unlocked: Int,
    val pointsTotal: Int,
    val pointsUnlocked: Int,
)

data class RetroUnlock(
    val id: Long,
    val title: String,
    val description: String,
    val points: Int,
    val badgeUrl: String,
)

object RetroAchievementsManager {
    private const val PREFS_NAME = "retro_achievements"
    private const val KEY_USERNAME = "username"
    private const val KEY_TOKEN = "token"
    private const val KEY_DISPLAY = "display_name"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HARDCORE = "hardcore"

    private const val CLIENT_VERSION = "1.0.0"
    private const val RCHEEVOS_VERSION = "12.3.0"

    @Volatile private var userAgent = "WinNative/$CLIENT_VERSION rcheevos/$RCHEEVOS_VERSION"

    private fun coreTag(systemId: String?): String {
        val core = RetroSystems.fromId(systemId)?.coreFileName ?: return "unknown"
        return core.removePrefix("lib").removeSuffix(".so").removeSuffix("_libretro_android")
    }

    private fun buildUserAgent(systemId: String?): String =
        "WinNative/$CLIENT_VERSION (Android ${android.os.Build.VERSION.RELEASE}) ${coreTag(systemId)} rcheevos/$RCHEEVOS_VERSION"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pumpJob: Job? = null

    private fun postMain(block: () -> Unit) = mainHandler.post(block)

    const val LOAD_IDLE = 0
    const val LOAD_LOADING = 1
    const val LOAD_LOADED = 2
    const val LOAD_EMPTY = 3

    @Volatile private var clientReady = false
    @Volatile private var loadedRomPath: String? = null
    @Volatile var loadState = LOAD_IDLE
        private set
    @Volatile var connected = true
        private set

    var unlockListener: ((RetroUnlock) -> Unit)? = null
    var stateListener: (() -> Unit)? = null
    var resetListener: (() -> Unit)? = null
    var hardcoreNoticeListener: ((String) -> Unit)? = null

    private var loginCallback: ((Boolean, String?) -> Unit)? = null
    private var appContext: Context? = null

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    const val HARDCORE_SUPPORTED = false

    fun isHardcorePreferred(context: Context): Boolean =
        HARDCORE_SUPPORTED && prefs(context).getBoolean(KEY_HARDCORE, false)

    fun setHardcorePreferred(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_HARDCORE, value).apply()
        if (clientReady) RetroAchievements.INSTANCE.nativeSetHardcore(value)
    }

    fun isLoggedIn(context: Context): Boolean =
        prefs(context).getString(KEY_TOKEN, null).isNullOrBlank().not()

    fun displayName(context: Context): String? = prefs(context).getString(KEY_DISPLAY, null)
        ?: prefs(context).getString(KEY_USERNAME, null)

    fun username(context: Context): String? = prefs(context).getString(KEY_USERNAME, null)

    fun apiToken(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)

    fun consoleId(systemId: String?): Int =
        when (systemId) {
            RetroSystems.NES.id -> 7
            RetroSystems.SNES.id -> 3
            RetroSystems.GAMEBOY.id -> 4
            RetroSystems.GAMEBOY_COLOR.id -> 6
            RetroSystems.GBA.id -> 5
            RetroSystems.GENESIS.id -> 1
            RetroSystems.MASTER_SYSTEM.id -> 11
            RetroSystems.GAME_GEAR.id -> 15
            RetroSystems.N64.id -> 2
            RetroSystems.PSX.id -> 12
            RetroSystems.PS2.id -> 21
            RetroSystems.GAMECUBE.id -> 16
            RetroSystems.WII.id -> 19
            else -> 0
        }

    private fun ensureClient(context: Context) {
        appContext = context.applicationContext
        if (!clientReady) {
            RetroAchievements.INSTANCE.nativeInit(isHardcorePreferred(context))
            clientReady = true
        }
        startPumps()
    }

    fun login(context: Context, username: String, password: String, onResult: (Boolean, String?) -> Unit) {
        ensureClient(context)
        prefs(context).edit().putString(KEY_USERNAME, username).apply()
        loginCallback = onResult
        RetroAchievements.INSTANCE.nativeLoginPassword(username, password)
    }

    fun logout(context: Context) {
        prefs(context).edit()
            .remove(KEY_TOKEN)
            .remove(KEY_DISPLAY)
            .apply()
        loadedRomPath = null
        loadState = LOAD_IDLE
        endSession()
    }

    fun prefetch(context: Context, systemId: String?, romPath: String) {
        if (!isEnabled(context) || !isLoggedIn(context)) return
        if (consoleId(systemId) == 0) return
        loadGameForBrowsing(context, systemId, romPath)
    }

    fun startSession(context: Context, systemId: String?, romPath: String) {
        if (!isEnabled(context) || !isLoggedIn(context)) return
        val console = consoleId(systemId)
        if (console == 0) return
        ensureClient(context)
        val username = prefs(context).getString(KEY_USERNAME, null) ?: return
        val token = prefs(context).getString(KEY_TOKEN, null) ?: return
        RetroAchievements.active = true
        userAgent = buildUserAgent(systemId)
        if (loadedRomPath == romPath && loadState != LOAD_LOADING) return
        loadedRomPath = romPath
        loadState = LOAD_LOADING
        scope.launch {
            RetroAchievements.INSTANCE.nativeLoginToken(username, token)
            RetroAchievements.INSTANCE.nativeLoadGame(console, romPath)
        }
    }

    fun loadGameForBrowsing(context: Context, systemId: String?, romPath: String) {
        if (!isLoggedIn(context)) return
        val console = consoleId(systemId)
        if (console == 0) return
        ensureClient(context)
        val username = prefs(context).getString(KEY_USERNAME, null) ?: return
        val token = prefs(context).getString(KEY_TOKEN, null) ?: return
        userAgent = buildUserAgent(systemId)
        if (loadedRomPath == romPath && loadState != LOAD_LOADING) return
        loadedRomPath = romPath
        loadState = LOAD_LOADING
        scope.launch {
            RetroAchievements.INSTANCE.nativeLoginToken(username, token)
            RetroAchievements.INSTANCE.nativeLoadGame(console, romPath)
        }
    }

    fun endSession() {
        RetroAchievements.active = false
        loadedRomPath = null
        loadState = LOAD_IDLE
        stopPumps()
        if (clientReady) RetroAchievements.INSTANCE.nativeUnloadGame()
    }

    fun idle() {
        if (clientReady) RetroAchievements.INSTANCE.nativeIdle()
    }

    fun onEmulatorReset() {
        if (clientReady) RetroAchievements.INSTANCE.nativeReset()
    }

    fun isHardcoreActive(): Boolean =
        HARDCORE_SUPPORTED && clientReady && RetroAchievements.INSTANCE.nativeGetHardcore()

    fun getAchievements(): List<RetroAchievement> {
        if (!clientReady) return emptyList()
        return RetroAchievements.INSTANCE.nativeGetAchievements().mapNotNull { row ->
            val parts = row.split('\t')
            if (parts.size < 9) return@mapNotNull null
            RetroAchievement(
                id = parts[0].toLongOrNull() ?: 0L,
                title = parts[1],
                description = parts[2],
                unlocked = parts[3] == "1",
                points = parts[4].toIntOrNull() ?: 0,
                progress = parts[5],
                badgeUrl = parts[6],
                badgeLockedUrl = parts[7],
                bucket = parts[8].toIntOrNull() ?: 0,
            )
        }
    }

    fun getSummary(): RetroGameSummary? {
        if (!clientReady) return null
        val text = RetroAchievements.INSTANCE.nativeGetGameSummary()
        if (text.isBlank()) return null
        val parts = text.split('\t')
        if (parts.size < 7) return null
        return RetroGameSummary(
            gameId = parts[0].toLongOrNull() ?: 0L,
            title = parts[1],
            badgeUrl = parts[2],
            total = parts[3].toIntOrNull() ?: 0,
            unlocked = parts[4].toIntOrNull() ?: 0,
            pointsTotal = parts[5].toIntOrNull() ?: 0,
            pointsUnlocked = parts[6].toIntOrNull() ?: 0,
        )
    }

    private fun startPumps() {
        if (pumpJob?.isActive == true) return
        pumpJob = scope.launch {
            while (isActive && clientReady) {
                pumpHttp()
                pumpEvents()
                delay(50)
            }
        }
    }

    private fun stopPumps() {
        pumpJob?.cancel()
        pumpJob = null
    }

    fun stopPolling() = stopPumps()

    private suspend fun CoroutineScope.pumpHttp() {
        while (isActive) {
            val request = RetroAchievements.INSTANCE.nativePollHttpRequest() ?: break
            val id = request[0].toIntOrNull() ?: continue
            val url = request[1]
            val postData = request[2]
            val contentType = request[3]
            launch { performHttp(id, url, postData, contentType) }
        }
    }

    private suspend fun performHttp(id: Int, url: String, postData: String, contentType: String) {
        var status = 0
        var body: ByteArray? = null
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30000
                readTimeout = 30000
                setRequestProperty("User-Agent", userAgent)
                if (postData.isNotEmpty()) {
                    doOutput = true
                    requestMethod = "POST"
                    if (contentType.isNotEmpty()) setRequestProperty("Content-Type", contentType)
                    outputStream.use { it.write(postData.toByteArray(Charsets.UTF_8)) }
                } else {
                    requestMethod = "GET"
                }
            }
            status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            body = stream?.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            }
            connection.disconnect()
        } catch (e: Exception) {
            status = 0
            body = null
        }
        withContext(Dispatchers.IO) {
            RetroAchievements.INSTANCE.nativeProvideResponse(id, status, body)
        }
    }

    private fun pumpEvents() {
        val events = RetroAchievements.INSTANCE.nativePollEvents()
        for (event in events) {
            val parts = event.split('\t')
            when (parts.getOrNull(0)) {
                "unlock" -> {
                    val unlock = RetroUnlock(
                        id = parts.getOrNull(1)?.toLongOrNull() ?: 0L,
                        title = parts.getOrNull(2) ?: "",
                        description = parts.getOrNull(3) ?: "",
                        points = parts.getOrNull(4)?.toIntOrNull() ?: 0,
                        badgeUrl = parts.getOrNull(5) ?: "",
                    )
                    postMain {
                        unlockListener?.invoke(unlock)
                        stateListener?.invoke()
                    }
                }
                "login_ok" -> {
                    val display = parts.getOrNull(1) ?: ""
                    val token = parts.getOrNull(2) ?: ""
                    appContext?.let { ctx ->
                        prefs(ctx).edit()
                            .putString(KEY_DISPLAY, display)
                            .putString(KEY_TOKEN, token)
                            .apply()
                    }
                    val callback = loginCallback
                    loginCallback = null
                    postMain { callback?.invoke(true, display) }
                }
                "login_failed" -> {
                    val message = parts.getOrNull(2) ?: "Login failed"
                    val callback = loginCallback
                    loginCallback = null
                    postMain { callback?.invoke(false, message) }
                }
                "game_loaded" -> {
                    loadState = LOAD_LOADED
                    if (RetroAchievements.INSTANCE.nativeGetHardcore() &&
                        !RetroAchievements.INSTANCE.nativeHardcoreCompatible()
                    ) {
                        RetroAchievements.INSTANCE.nativeSetHardcore(false)
                        postMain {
                            hardcoreNoticeListener?.invoke(
                                "Hardcore disabled: a core option is not allowed in hardcore mode",
                            )
                        }
                    }
                    postMain { stateListener?.invoke() }
                }
                "game_unidentified", "game_load_failed" -> {
                    loadState = LOAD_EMPTY
                    postMain { stateListener?.invoke() }
                }
                "game_completed" -> postMain { stateListener?.invoke() }
                "reset" -> postMain { resetListener?.invoke() }
                "disconnected" -> {
                    connected = false
                    postMain { stateListener?.invoke() }
                }
                "reconnected" -> {
                    connected = true
                    postMain { stateListener?.invoke() }
                }
            }
        }
    }
}
