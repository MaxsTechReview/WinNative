package org.dolphinemu.dolphinemu.wn

import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import org.dolphinemu.dolphinemu.NativeLibrary
import org.dolphinemu.dolphinemu.features.netplay.NetplaySession
import org.dolphinemu.dolphinemu.features.netplay.model.TraversalState
import org.dolphinemu.dolphinemu.features.settings.model.BooleanSetting
import org.dolphinemu.dolphinemu.features.settings.model.IntSetting
import org.dolphinemu.dolphinemu.features.settings.model.NativeConfig
import org.dolphinemu.dolphinemu.features.settings.model.StringSetting
import org.dolphinemu.dolphinemu.model.GameFile

object DolphinNetplay {
    private const val TAG = "WnDolphinNet"

    const val EXTRA_ENABLE = "dolphin_np_enable"
    const val EXTRA_HOST = "dolphin_np_host"
    const val EXTRA_TRAVERSAL = "dolphin_np_traversal"
    const val EXTRA_ADDRESS = "dolphin_np_address"
    const val EXTRA_HOST_CODE = "dolphin_np_host_code"
    const val EXTRA_PORT = "dolphin_np_port"
    const val EXTRA_NICKNAME = "dolphin_np_nickname"

    const val DEFAULT_PORT = 2626

    data class Config(
        val host: Boolean,
        val traversal: Boolean,
        val address: String,
        val hostCode: String,
        val port: Int,
        val nickname: String,
    )

    fun fromIntent(intent: Intent): Config? {
        if (!intent.getBooleanExtra(EXTRA_ENABLE, false)) return null
        return Config(
            host = intent.getBooleanExtra(EXTRA_HOST, true),
            traversal = intent.getBooleanExtra(EXTRA_TRAVERSAL, false),
            address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty(),
            hostCode = intent.getStringExtra(EXTRA_HOST_CODE).orEmpty(),
            port = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT),
            nickname = intent.getStringExtra(EXTRA_NICKNAME).orEmpty(),
        )
    }

    @Volatile
    private var session: NetplaySession? = null

    @Volatile
    private var scope: CoroutineScope? = null

    @Volatile
    private var hostCode: String? = null

    @Volatile
    private var members: List<String> = emptyList()

    val active: Boolean
        get() = session != null

    private fun pushStatus() {
        DolphinHost.onNetplayStatus?.invoke(session != null, hostCode, members)
    }

    private fun applyConfig(cfg: Config) {
        val base = NativeConfig.LAYER_BASE
        StringSetting.NETPLAY_NICKNAME.setString(base, cfg.nickname.ifBlank { "Player" })
        StringSetting.NETPLAY_TRAVERSAL_CHOICE.setString(
            base,
            if (cfg.traversal) "traversal" else "direct",
        )
        if (cfg.host) {
            IntSetting.NETPLAY_HOST_PORT.setInt(base, cfg.port)
            BooleanSetting.NETPLAY_USE_UPNP.setBoolean(base, false)
        } else if (cfg.traversal) {
            StringSetting.NETPLAY_HOST_CODE.setString(base, cfg.hostCode.trim())
        } else {
            StringSetting.NETPLAY_ADDRESS.setString(base, cfg.address.trim())
            IntSetting.NETPLAY_CONNECT_PORT.setInt(base, cfg.port)
        }
    }

    fun startAndAwaitBoot(host: NativeLibrary.WnHost?, romPath: String, cfg: Config): Long {
        runCatching { applyConfig(cfg) }
            .onFailure { Log.e(TAG, "applyConfig failed", it); return 0L }

        val gameFile = runCatching { GameFile.parse(romPath) }.getOrNull()
        if (gameFile == null) {
            Log.e(TAG, "GameFile.parse failed for $romPath")
            host?.onToast("Netplay: unreadable game file", true)
            return 0L
        }

        val s = NetplaySession(arrayOf(gameFile)) { session = null }
        session = s
        val sc = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = sc
        hostCode = null
        members = emptyList()
        pushStatus()

        val bootData = CompletableDeferred<Long>()

        s.players
            .onEach { players ->
                members = players.map { it.name }
                pushStatus()
            }
            .launchIn(sc)

        s.launchGame
            .onEach {
                Log.i(TAG, "netplay boot ready ($it)")
                if (!bootData.isCompleted) bootData.complete(s.consumeBootSessionData())
            }
            .launchIn(sc)

        s.connectionErrors
            .onEach { msg ->
                Log.w(TAG, "netplay error: $msg")
                host?.onToast("Netplay: $msg", true)
            }
            .launchIn(sc)

        s.connectionLost
            .onEach {
                host?.onToast("Netplay connection lost", true)
                if (!bootData.isCompleted) bootData.complete(0L)
            }
            .launchIn(sc)

        if (cfg.host && cfg.traversal) {
            s.traversalState
                .onEach { state ->
                    if (state is TraversalState.Connected) {
                        Log.i(TAG, "traversal host code: ${state.hostCode}")
                        hostCode = state.hostCode
                        pushStatus()
                    }
                }
                .launchIn(sc)
        }

        sc.launch {
            val ok = runCatching { if (cfg.host) s.host() else s.join() }.getOrDefault(false)
            if (!ok) {
                host?.onToast(
                    if (cfg.host) "Netplay: could not host" else "Netplay: could not connect",
                    true,
                )
                if (!bootData.isCompleted) bootData.complete(0L)
                return@launch
            }
            if (cfg.host) {
                host?.onToast("Waiting for player…", false)
                runCatching { s.changeGame(gameFile) }
                val playerCount = AtomicInteger(1)
                val tracker = s.players.onEach { playerCount.set(it.size) }.launchIn(sc)
                withTimeoutOrNull(120_000L) {
                    var announced = false
                    while (isActive) {
                        if (playerCount.get() >= 2) {
                            if (!announced) {
                                announced = true
                                host?.onToast("Player connected — verifying game…", false)
                            }
                            if (runCatching { s.doAllPlayersHaveGame() }.getOrDefault(false)) {
                                host?.onToast("Starting…", false)
                                runCatching { s.startGame() }
                                return@withTimeoutOrNull
                            }
                        }
                        delay(1_000L)
                    }
                }
                tracker.cancel()
            }
        }

        val result = runBlocking {
            withTimeoutOrNull(180_000L) { bootData.await() } ?: 0L
        }
        if (result == 0L) {
            host?.onToast("Netplay: no game started", true)
            stop()
        }
        return result
    }

    fun stop() {
        val s = session
        session = null
        runCatching { runBlocking { s?.close() } }
        scope?.cancel()
        scope = null
        hostCode = null
        members = emptyList()
        pushStatus()
    }
}
