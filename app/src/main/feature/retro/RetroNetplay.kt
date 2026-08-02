package com.winlator.cmod.feature.retro

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.swordfish.libretrodroid.GLRetroView
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

enum class RetroNetplayPhase {
    IDLE,
    HOSTING,
    SCANNING,
    SCAN_RESULTS,
    CONNECTING,
    IN_ROOM,
}

data class RetroNetplayDiscoveredRoom(
    val hostAddress: String,
    val port: Int,
    val systemId: String,
    val gameName: String,
    val hostPlayerName: String,
    val roomId: String,
    val lastSeenMs: Long = System.currentTimeMillis(),
)

data class RetroNetplayMember(
    val name: String,
    val isHost: Boolean,
    val isLocal: Boolean,
)

object RetroNetplayLobby {
    const val DEFAULT_TCP_PORT = 55435
    const val DISCOVERY_PORT = 55436
    internal const val DISCOVERY_MAGIC = "WNNP1"

    var phase by mutableStateOf(RetroNetplayPhase.IDLE)
        private set
    var statusText by mutableStateOf("")
        private set
    var members by mutableStateOf<List<RetroNetplayMember>>(emptyList())
        private set
    var discovered by mutableStateOf<List<RetroNetplayDiscoveredRoom>>(emptyList())
        private set
    var roomTitle by mutableStateOf("")
        private set
    var isHost by mutableStateOf(false)
        private set
    var localPlayerName by mutableStateOf("Player")
        private set

    var isGameLink by mutableStateOf(false)
        private set

    private var pendingGameLinkHost: Boolean? = null
    private var pendingGameLinkPort: Int = RetroGameLink.DEFAULT_PORT
    private var pendingGameLinkPeerIp: String = ""

    var eventToast by mutableStateOf<String?>(null)
        private set

    private var session: RetroNetplaySession? = null
    private var advertiser: NetplayAdvertiser? = null
    private var scanner: NetplayScanner? = null
    private val main = Handler(Looper.getMainLooper())
    private var toastClearToken = 0

    fun canStartSession(): Boolean =
        phase == RetroNetplayPhase.IDLE ||
            phase == RetroNetplayPhase.SCANNING ||
            phase == RetroNetplayPhase.SCAN_RESULTS

    fun isInRoomSession(): Boolean =
        phase == RetroNetplayPhase.HOSTING ||
            phase == RetroNetplayPhase.CONNECTING ||
            phase == RetroNetplayPhase.IN_ROOM

    private fun pushToast(message: String) {
        main.post {
            val token = ++toastClearToken
            eventToast = message
            main.postDelayed({
                if (toastClearToken == token) eventToast = null
            }, 2_800L)
        }
    }

    fun notifyPeerJoined(peerName: String) {
        val peer = peerName.ifBlank { "Player 2" }
        main.post {
            discovered = emptyList()
            phase = RetroNetplayPhase.IN_ROOM
            statusText = "Ready"
            val local =
                members.firstOrNull { it.isLocal }
                    ?: RetroNetplayMember(localPlayerName, isHost = isHost, isLocal = true)
            members =
                if (isHost) {
                    listOf(
                        local.copy(isHost = true, isLocal = true),
                        RetroNetplayMember(peer, isHost = false, isLocal = false),
                    )
                } else {
                    listOf(
                        RetroNetplayMember(peer, isHost = true, isLocal = false),
                        local.copy(isHost = false, isLocal = true),
                    )
                }
            pushToast("$peer connected")
        }
    }

    fun notifyPeerLeft(peerName: String = "Player") {
        val left = peerName.ifBlank { "Player" }
        main.post {
            if (isHost) {
                phase = RetroNetplayPhase.HOSTING
                statusText = "Waiting for a player…"
                members =
                    listOf(
                        RetroNetplayMember(localPlayerName, isHost = true, isLocal = true),
                    )
            }
            pushToast("$left disconnected")
        }
    }

    fun clearToast() {
        toastClearToken++
        eventToast = null
    }

    fun defaultPlayerName(context: Context): String {
        val saved = RetroDefaults.netplayPlayerName(context).trim()
        if (saved.isNotEmpty()) return saved
        val device =
            runCatching {
                Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            }.getOrNull()?.trim().orEmpty()
        return device.ifBlank { "Player" }
    }

    fun bindLocalName(context: Context) {
        localPlayerName = defaultPlayerName(context)
    }

    fun localIpv4Addresses(): List<String> {
        val out = mutableListOf<String>()
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().forEach { nif ->
                if (!nif.isUp || nif.isLoopback) return@forEach
                nif.inetAddresses.toList().forEach { addr ->
                    if (addr.isLoopbackAddress || addr.hostAddress?.contains(':') == true) return@forEach
                    val ip = addr.hostAddress ?: return@forEach
                    if (ip.isNotBlank()) out += ip
                }
            }
        }
        return out.distinct()
    }

    fun host(
        systemId: String,
        gameName: String,
        playerName: String,
        port: Int,
        context: Context? = null,
        retroView: GLRetroView? = null,
    ) {
        leave(silent = true)
        isHost = true
        localPlayerName = playerName.ifBlank { "Host" }
        roomTitle = gameName
        discovered = emptyList()
        isGameLink = RetroOnlineSupport.supportsGameLink(systemId)
        phase = RetroNetplayPhase.HOSTING
        val usePort =
            if (isGameLink) RetroGameLink.clampPort(port) else port
        val localIp = localIpv4Addresses().firstOrNull()
        statusText =
            if (isGameLink) {
                if (localIp != null) {
                    "Game Link host · $localIp:$usePort"
                } else {
                    "Game Link host · port $usePort"
                }
            } else if (localIp != null) {
                "Waiting on $localIp:$usePort…"
            } else {
                "Waiting for a player on port $usePort…"
            }
        members =
            listOf(
                RetroNetplayMember(localPlayerName, isHost = true, isLocal = true),
            )
        val roomId = UUID.randomUUID().toString().take(8)
        advertiser =
            NetplayAdvertiser(
                appContext = context?.applicationContext,
                tcpPort = usePort,
                systemId = systemId,
                gameName = gameName,
                hostPlayerName = localPlayerName,
                roomId = roomId,
            ).also { it.start() }

        if (isGameLink) {
            pendingGameLinkHost = true
            pendingGameLinkPort =
                if (RetroOnlineSupport.isGbaNetpacket(systemId)) {
                    RetroGameLink.GBA_NETPACKET_PORT
                } else {
                    usePort
                }
            pendingGameLinkPeerIp = ""
            startPresenceListener()
            bindNetpacketPeerListener()
            if (retroView != null) {
                val ok =
                    if (RetroOnlineSupport.isGbaNetpacket(systemId)) {
                        RetroGameLink.startGbaHost(retroView, pendingGameLinkPort)
                    } else {
                        RetroGameLink.startHost(retroView, pendingGameLinkPort)
                        true
                    }
                phase = RetroNetplayPhase.HOSTING
                statusText =
                    if (ok) {
                        "Game Link host · waiting for player on $pendingGameLinkPort"
                    } else {
                        "Game Link host failed (need gpSP / netpacket)"
                    }
                pushToast(if (ok) "Hosting — waiting for player" else "Game Link host failed")
            }
            return
        }

        val s =
            RetroNetplaySession(
                retroView = retroView,
                localPort = 0,
                remotePort = 1,
                isHost = true,
                onStatus = { msg -> main.post { statusText = msg } },
                onPeerJoined = { peerName ->
                    notifyPeerJoined(peerName.ifBlank { "Player 2" })
                },
                onPeerLeft = {
                    val left = members.firstOrNull { !it.isLocal }?.name ?: "Player"
                    if (phase == RetroNetplayPhase.IN_ROOM || phase == RetroNetplayPhase.HOSTING) {
                        notifyPeerLeft(left)
                    }
                },
                onEnded = { ended ->
                    main.post {
                        if (session === ended && phase != RetroNetplayPhase.IDLE) {
                            leave(silent = false, message = "Room closed")
                        }
                    }
                },
            )
        startPresenceListener()
        session = s
        s.startHost(usePort)
    }

    fun join(
        host: String,
        port: Int,
        playerName: String,
        gameName: String,
        systemId: String? = null,
        retroView: GLRetroView? = null,
    ) {
        leave(silent = true)
        isHost = false
        localPlayerName = playerName.ifBlank { "Player 2" }
        roomTitle = gameName
        discovered = emptyList()
        isGameLink =
            systemId != null && RetroOnlineSupport.supportsGameLink(systemId)
        phase = RetroNetplayPhase.CONNECTING
        val usePort =
            if (isGameLink) RetroGameLink.clampPort(port) else port
        statusText = "Connecting to $host:$usePort…"
        members = emptyList()

        if (isGameLink) {
            pendingGameLinkHost = false
            pendingGameLinkPort =
                if (RetroOnlineSupport.isGbaNetpacket(systemId)) {
                    RetroGameLink.GBA_NETPACKET_PORT
                } else {
                    usePort
                }
            pendingGameLinkPeerIp = host
            members =
                listOf(
                    RetroNetplayMember(host, isHost = true, isLocal = false),
                    RetroNetplayMember(localPlayerName, isHost = false, isLocal = true),
                )
            if (retroView != null) {
                val ok =
                    if (RetroOnlineSupport.isGbaNetpacket(systemId)) {
                        RetroGameLink.startGbaClient(retroView, host, pendingGameLinkPort)
                    } else {
                        RetroGameLink.startClient(retroView, host, pendingGameLinkPort)
                        true
                    }
                phase = RetroNetplayPhase.IN_ROOM
                statusText =
                    if (ok) {
                        "Game Link client · $host:$pendingGameLinkPort"
                    } else {
                        "Game Link join failed (need gpSP / netpacket)"
                    }
                if (ok) {
                    pushToast("Connected to host")
                    sendPresenceJoin(host, pendingGameLinkPort, localPlayerName)
                } else {
                    pushToast("Game Link join failed")
                }
            } else {
                phase = RetroNetplayPhase.IN_ROOM
                statusText = "Game Link ready · launch game to link $host"
                sendPresenceJoin(host, pendingGameLinkPort, localPlayerName)
            }
            return
        }

        val s =
            RetroNetplaySession(
                retroView = retroView,
                localPort = 1,
                remotePort = 0,
                isHost = false,
                onStatus = { msg -> main.post { statusText = msg } },
                onPeerJoined = { peerName ->
                    val peer = peerName.ifBlank { "Host" }
                    main.post {
                        discovered = emptyList()
                        phase = RetroNetplayPhase.IN_ROOM
                        statusText = "Ready"
                        members =
                            listOf(
                                RetroNetplayMember(peer, isHost = true, isLocal = false),
                                RetroNetplayMember(localPlayerName, isHost = false, isLocal = true),
                            )
                        pushToast("Connected to $peer")
                    }
                    sendPresenceJoin(host, usePort, localPlayerName)
                },
                onPeerLeft = {
                    main.post {
                        pushToast("Host disconnected")
                        leave(silent = false, message = "Host disconnected")
                    }
                },
                onEnded = { ended ->
                    main.post {
                        if (session === ended && phase != RetroNetplayPhase.IDLE) {
                            leave(silent = false, message = "Disconnected")
                        }
                    }
                },
            )
        session = s
        s.startClient(host, usePort, localPlayerName)
    }

    fun attachEmulator(retroView: GLRetroView): Boolean {
        if (isGameLink && pendingGameLinkHost != null) {
            val hasNetpacket = runCatching { retroView.netpacketHasCore() }.getOrDefault(false)
            val ok =
                if (pendingGameLinkHost == true) {
                    if (hasNetpacket) {
                        RetroGameLink.startGbaHost(retroView, pendingGameLinkPort)
                    } else {
                        RetroGameLink.startHost(retroView, pendingGameLinkPort)
                        true
                    }
                } else if (hasNetpacket) {
                    RetroGameLink.startGbaClient(
                        retroView,
                        pendingGameLinkPeerIp,
                        pendingGameLinkPort,
                    )
                } else {
                    RetroGameLink.startClient(
                        retroView,
                        pendingGameLinkPeerIp,
                        pendingGameLinkPort,
                    )
                    true
                }
            phase = RetroNetplayPhase.IN_ROOM
            statusText =
                if (ok) {
                    if (pendingGameLinkHost == true) {
                        "Game Link host active · port $pendingGameLinkPort"
                    } else {
                        "Game Link client · ${pendingGameLinkPeerIp}:$pendingGameLinkPort"
                    }
                } else {
                    "Game Link attach failed"
                }
            if (ok) {
                if (pendingGameLinkHost == true) {
                    startPresenceListener()
                    bindNetpacketPeerListener()
                    pushToast("Hosting — waiting for player")
                } else {
                    pushToast("Connected to host")
                    sendPresenceJoin(
                        pendingGameLinkPeerIp,
                        pendingGameLinkPort,
                        localPlayerName,
                    )
                }
            } else {
                pushToast("Game Link failed")
            }
            return true
        }
        val s = session ?: return false
        if (!isInRoomSession()) return false
        s.attachRetroView(retroView)
        if (isHost) {
            startPresenceListener()
        } else {
        }
        return true
    }

    fun prepareMultiplayerPads(retroView: GLRetroView) {
        runCatching {
            retroView.setControllerType(0, 1)
            retroView.setControllerType(1, 1)
        }
    }

    fun scan(
        context: Context,
        systemId: String?,
        durationMs: Long = 6_500L,
    ) {
        if (phase == RetroNetplayPhase.HOSTING || phase == RetroNetplayPhase.IN_ROOM ||
            phase == RetroNetplayPhase.CONNECTING
        ) {
            return
        }
        scanner?.stop()
        scanner = null
        phase = RetroNetplayPhase.SCANNING
        statusText = "Scanning local network…"
        discovered = emptyList()
        val app = context.applicationContext
        scanner =
            NetplayScanner(appContext = app, systemIdFilter = systemId) { rooms ->
                main.post {
                    discovered = rooms
                    if (phase == RetroNetplayPhase.SCANNING) {
                        statusText =
                            if (rooms.isEmpty()) {
                                "Looking for rooms…"
                            } else {
                                "Found ${rooms.size} room${if (rooms.size == 1) "" else "s"}…"
                            }
                    }
                }
            }.also {
                it.start(durationMs) {
                    main.post {
                        if (phase == RetroNetplayPhase.SCANNING) {
                            phase = RetroNetplayPhase.SCAN_RESULTS
                            statusText =
                                if (discovered.isEmpty()) {
                                    "No rooms found on Wi‑Fi"
                                } else {
                                    "Found ${discovered.size} room${if (discovered.size == 1) "" else "s"}"
                                }
                        }
                    }
                }
            }
    }

    fun stopScan() {
        scanner?.stop()
        scanner = null
        if (phase == RetroNetplayPhase.SCANNING) {
            phase = RetroNetplayPhase.SCAN_RESULTS
            statusText =
                if (discovered.isEmpty()) {
                    "Scan stopped"
                } else {
                    "Found ${discovered.size} room${if (discovered.size == 1) "" else "s"}"
                }
        }
    }

    fun dismissScanResults() {
        if (phase == RetroNetplayPhase.SCANNING) {
            scanner?.stop()
            scanner = null
        }
        if (phase == RetroNetplayPhase.SCANNING || phase == RetroNetplayPhase.SCAN_RESULTS) {
            phase = RetroNetplayPhase.IDLE
            discovered = emptyList()
            statusText = ""
        }
    }

    fun leave(
        silent: Boolean = false,
        message: String = "Left room",
        retroView: GLRetroView? = null,
    ) {
        if (!isHost && pendingGameLinkPeerIp.isNotBlank()) {
            sendPresenceLeave(pendingGameLinkPeerIp, pendingGameLinkPort, localPlayerName)
        }
        scanner?.stop()
        scanner = null
        advertiser?.stop()
        advertiser = null
        stopPresenceListener()
        unbindNetpacketPeerListener()
        val s = session
        session = null
        s?.stop()
        if (isGameLink) {
            RetroGameLink.stop(retroView)
        }
        pendingGameLinkHost = null
        pendingGameLinkPeerIp = ""
        isGameLink = false
        phase = RetroNetplayPhase.IDLE
        members = emptyList()
        roomTitle = ""
        discovered = emptyList()
        isHost = false
        if (!silent) {
            statusText = message
            main.postDelayed({
                if (phase == RetroNetplayPhase.IDLE && statusText == message) {
                    statusText = ""
                }
            }, 2_500L)
        } else {
            statusText = ""
        }
    }

    private const val PRESENCE_JOIN = "WNNPJ"
    private const val PRESENCE_LEAVE = "WNNPL"
    private var presenceSocket: DatagramSocket? = null
    private val presenceRunning = AtomicBoolean(false)

    private fun startPresenceListener() {
        stopPresenceListener()
        if (!presenceRunning.compareAndSet(false, true)) return
        thread(name = "retro-netplay-presence", isDaemon = true) {
            try {
                val sock = DatagramSocket(null)
                presenceSocket = sock
                sock.reuseAddress = true
                sock.broadcast = true
                sock.soTimeout = 500
                sock.bind(InetSocketAddress(DISCOVERY_PORT))
                val buf = ByteArray(256)
                while (presenceRunning.get()) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        sock.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    } catch (_: Throwable) {
                        break
                    }
                    val text =
                        String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8)
                    val parts = text.split('|')
                    if (parts.size < 2) continue
                    val name = parts[1].ifBlank { "Player" }
                    when (parts[0]) {
                        PRESENCE_JOIN -> {
                            if (isHost &&
                                (phase == RetroNetplayPhase.HOSTING || phase == RetroNetplayPhase.IN_ROOM)
                            ) {
                                notifyPeerJoined(name)
                            }
                        }
                        PRESENCE_LEAVE -> {
                            if (isHost && phase == RetroNetplayPhase.IN_ROOM) {
                                notifyPeerLeft(name)
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
            } finally {
                runCatching { presenceSocket?.close() }
                presenceSocket = null
                presenceRunning.set(false)
            }
        }
    }

    private fun stopPresenceListener() {
        presenceRunning.set(false)
        runCatching { presenceSocket?.close() }
        presenceSocket = null
    }

    private fun sendPresenceJoin(
        host: String,
        port: Int,
        name: String,
    ) {
        thread(name = "retro-netplay-presence-send", isDaemon = true) {
            runCatching {
                val payload =
                    "$PRESENCE_JOIN|${name.replace('|', '/').take(24)}|$port"
                        .toByteArray(StandardCharsets.UTF_8)
                DatagramSocket().use { sock ->
                    sock.broadcast = true
                    val addr = InetAddress.getByName(host)
                    sock.send(
                        DatagramPacket(payload, payload.size, addr, DISCOVERY_PORT),
                    )
                    runCatching {
                        sock.send(
                            DatagramPacket(
                                payload,
                                payload.size,
                                InetAddress.getByName("255.255.255.255"),
                                DISCOVERY_PORT,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun sendPresenceLeave(
        host: String,
        port: Int,
        name: String,
    ) {
        thread(name = "retro-netplay-presence-leave", isDaemon = true) {
            runCatching {
                val payload =
                    "$PRESENCE_LEAVE|${name.replace('|', '/').take(24)}|$port"
                        .toByteArray(StandardCharsets.UTF_8)
                DatagramSocket().use { sock ->
                    sock.send(
                        DatagramPacket(
                            payload,
                            payload.size,
                            InetAddress.getByName(host),
                            DISCOVERY_PORT,
                        ),
                    )
                }
            }
        }
    }

    private fun bindNetpacketPeerListener() {
        runCatching {
            com.swordfish.libretrodroid.LibretroDroid.setNetpacketPeerListener { joined, _ ->
                if (joined) {
                    notifyPeerJoined("Player 2")
                } else {
                    notifyPeerLeft("Player 2")
                }
            }
        }
    }

    private fun unbindNetpacketPeerListener() {
        runCatching {
            com.swordfish.libretrodroid.LibretroDroid.setNetpacketPeerListener(null)
        }
    }

    fun activeSession(): RetroNetplaySession? = session

    fun sendLocalKey(keyCode: Int, action: Int) {
        session?.sendLocalKey(keyCode, action)
    }

    fun sendLocalMotion(source: Int, x: Float, y: Float) {
        session?.sendLocalMotion(source, x, y)
    }

    fun onFrameRendered() {
        session?.onFrameRendered()
    }
}

private fun acquireMulticastLock(appContext: Context?): WifiManager.MulticastLock? {
    if (appContext == null) return null
    return runCatching {
        val wifi = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null
        wifi.createMulticastLock("winnative-netplay").apply {
            setReferenceCounted(false)
            acquire()
        }
    }.getOrNull()
}

private class NetplayAdvertiser(
    private val appContext: Context?,
    private val tcpPort: Int,
    private val systemId: String,
    private val gameName: String,
    private val hostPlayerName: String,
    private val roomId: String,
) {
    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread(name = "retro-netplay-advertise", isDaemon = true) {
            try {
                multicastLock = acquireMulticastLock(appContext)
                val sock = DatagramSocket()
                socket = sock
                sock.broadcast = true
                val payload =
                    buildString {
                        append(RetroNetplayLobby.DISCOVERY_MAGIC)
                        append('|')
                        append(systemId.replace('|', '/'))
                        append('|')
                        append(tcpPort)
                        append('|')
                        append(gameName.replace('|', '/').take(48))
                        append('|')
                        append(hostPlayerName.replace('|', '/').take(24))
                        append('|')
                        append(roomId)
                    }.toByteArray(StandardCharsets.UTF_8)
                val broadcast = InetAddress.getByName("255.255.255.255")
                while (running.get()) {
                    val packet =
                        DatagramPacket(
                            payload,
                            payload.size,
                            broadcast,
                            RetroNetplayLobby.DISCOVERY_PORT,
                        )
                    runCatching { sock.send(packet) }
                    runCatching {
                        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().forEach { nif ->
                            if (!nif.isUp || nif.isLoopback) return@forEach
                            nif.interfaceAddresses.forEach { addr ->
                                val b = addr.broadcast ?: return@forEach
                                sock.send(
                                    DatagramPacket(payload, payload.size, b, RetroNetplayLobby.DISCOVERY_PORT),
                                )
                            }
                        }
                    }
                    Thread.sleep(800L)
                }
            } catch (_: Throwable) {
            } finally {
                runCatching { socket?.close() }
                socket = null
                runCatching { if (multicastLock?.isHeld == true) multicastLock?.release() }
                multicastLock = null
                running.set(false)
            }
        }
    }

    fun stop() {
        running.set(false)
        runCatching { socket?.close() }
        socket = null
        runCatching { if (multicastLock?.isHeld == true) multicastLock?.release() }
        multicastLock = null
    }
}

private class NetplayScanner(
    private val appContext: Context?,
    private val systemIdFilter: String?,
    private val onUpdate: (List<RetroNetplayDiscoveredRoom>) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val rooms = ConcurrentHashMap<String, RetroNetplayDiscoveredRoom>()

    fun start(
        durationMs: Long,
        onFinished: () -> Unit,
    ) {
        if (!running.compareAndSet(false, true)) return
        thread(name = "retro-netplay-scan", isDaemon = true) {
            try {
                multicastLock = acquireMulticastLock(appContext)
                val sock = DatagramSocket(null)
                socket = sock
                sock.reuseAddress = true
                sock.broadcast = true
                sock.soTimeout = 350
                sock.bind(InetSocketAddress(RetroNetplayLobby.DISCOVERY_PORT))
                val buf = ByteArray(512)
                val deadline = System.currentTimeMillis() + durationMs
                while (running.get() && System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        sock.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    } catch (_: Throwable) {
                        break
                    }
                    val text =
                        String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8)
                    val hostAddr = packet.address?.hostAddress ?: continue
                    val room = parse(text, hostAddr) ?: continue
                    if (systemIdFilter != null && room.systemId != systemIdFilter) continue
                    rooms[room.roomId + "@" + room.hostAddress] = room
                    onUpdate(rooms.values.sortedBy { it.gameName.lowercase() })
                }
            } catch (_: Throwable) {
            } finally {
                runCatching { socket?.close() }
                socket = null
                runCatching { if (multicastLock?.isHeld == true) multicastLock?.release() }
                multicastLock = null
                running.set(false)
                onFinished()
            }
        }
    }

    fun stop() {
        running.set(false)
        runCatching { socket?.close() }
        socket = null
    }

    private fun parse(
        text: String,
        address: String,
    ): RetroNetplayDiscoveredRoom? {
        val parts = text.split('|')
        if (parts.size < 6) return null
        if (parts[0] != RetroNetplayLobby.DISCOVERY_MAGIC) return null
        val port = parts[2].toIntOrNull() ?: return null
        return RetroNetplayDiscoveredRoom(
            hostAddress = address,
            port = port.coerceIn(1, 65535),
            systemId = parts[1],
            gameName = parts[3],
            hostPlayerName = parts[4],
            roomId = parts[5],
        )
    }
}

class RetroNetplaySession(
    retroView: GLRetroView?,
    private val localPort: Int,
    private val remotePort: Int,
    private val isHost: Boolean,
    private val onStatus: (String) -> Unit,
    private val onPeerJoined: (peerName: String) -> Unit = {},
    private val onPeerLeft: () -> Unit = {},
    private val onEnded: (RetroNetplaySession) -> Unit = {},
) {
    private val running = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())
    private val remoteKeys = ConcurrentHashMap.newKeySet<Int>()
    private var socket: Socket? = null
    private var server: ServerSocket? = null
    private var out: DataOutputStream? = null
    private var frameCounter = 0
    private var peerName: String = ""

    @Volatile
    private var retroView: GLRetroView? = retroView

    fun attachRetroView(view: GLRetroView) {
        retroView = view
        RetroNetplayLobby.prepareMultiplayerPads(view)
        if (isHost) {
            pushStateNow()
        } else {
            requestState()
        }
    }

    companion object {
        private const val MSG_KEY = 1
        private const val MSG_STATE = 2
        private const val MSG_HELLO = 3
        private const val MSG_MOTION = 4
        private const val MSG_STATE_REQUEST = 5
        private const val STATE_EVERY_FRAMES = 600
        private const val RETRO_DEVICE_JOYPAD = 1
    }

    val isRunning: Boolean get() = running.get()

    fun startHost(listenPort: Int) {
        if (!running.compareAndSet(false, true)) return
        retroView?.let { RetroNetplayLobby.prepareMultiplayerPads(it) }
        thread(name = "retro-netplay-host", isDaemon = true) {
            try {
                onStatus("Waiting for a player…")
                val ss = ServerSocket()
                server = ss
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(listenPort))
                while (running.get()) {
                    val client =
                        try {
                            ss.accept()
                        } catch (_: Throwable) {
                            break
                        }
                    socket = client
                    wire(client)
                    handshakeAsHost(client)
                    val joinedName = peerName.ifBlank { "Player 2" }
                    onStatus("$joinedName joined")
                    onPeerJoined(joinedName)
                    pushStateNow()
                    readLoop(client)
                    cleanupSocketOnly()
                    onPeerLeft()
                    if (!running.get()) break
                    onStatus("Waiting for a player…")
                }
            } catch (t: Throwable) {
                if (running.get()) onStatus(t.message ?: "Host failed")
            } finally {
                val wasRunning = running.get()
                stopInternal()
                if (wasRunning) onEnded(this)
            }
        }
    }

    fun startClient(
        host: String,
        port: Int,
        localName: String,
    ) {
        if (!running.compareAndSet(false, true)) return
        retroView?.let { RetroNetplayLobby.prepareMultiplayerPads(it) }
        thread(name = "retro-netplay-client", isDaemon = true) {
            try {
                onStatus("Connecting…")
                val s = Socket()
                socket = s
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(host, port), 12_000)
                wire(s)
                handshakeAsClient(s, localName)
                onStatus("Connected")
                onPeerJoined(peerName)
                requestState()
                readLoop(s)
            } catch (t: Throwable) {
                if (running.get()) onStatus(t.message ?: "Connect failed")
            } finally {
                val wasRunning = running.get()
                stopInternal()
                if (wasRunning) onEnded(this)
            }
        }
    }

    private fun requestState() {
        val stream = out ?: return
        thread(name = "retro-netplay-state-req", isDaemon = true) {
            runCatching {
                synchronized(stream) {
                    stream.writeInt(MSG_STATE_REQUEST)
                    stream.flush()
                }
            }
        }
    }

    private fun pushStateNow() {
        val view = retroView ?: return
        val stream = out ?: return
        if (!isHost || !running.get()) return
        thread(name = "retro-netplay-state-push", isDaemon = true) {
            runCatching {
                val state = view.serializeState()
                synchronized(stream) {
                    stream.writeInt(MSG_STATE)
                    stream.writeInt(state.size)
                    stream.write(state)
                    stream.flush()
                }
            }
        }
    }

    private fun handshakeAsHost(s: Socket) {
        val input = DataInputStream(BufferedInputStream(s.getInputStream()))
        val stream = out ?: return
        val type = input.readInt()
        if (type != MSG_HELLO) throw IllegalStateException("Bad handshake")
        peerName = input.readUTF().ifBlank { "Player 2" }
        synchronized(stream) {
            stream.writeInt(MSG_HELLO)
            stream.writeUTF(RetroNetplayLobby.localPlayerName.ifBlank { "Host" })
            stream.flush()
        }
    }

    private fun handshakeAsClient(
        s: Socket,
        localName: String,
    ) {
        val stream = out ?: return
        synchronized(stream) {
            stream.writeInt(MSG_HELLO)
            stream.writeUTF(localName)
            stream.flush()
        }
        val input = DataInputStream(BufferedInputStream(s.getInputStream()))
        val type = input.readInt()
        if (type != MSG_HELLO) throw IllegalStateException("Bad handshake")
        peerName = input.readUTF().ifBlank { "Host" }
    }

    private fun wire(s: Socket) {
        s.tcpNoDelay = true
        out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
    }

    private fun readLoop(s: Socket) {
        val input = DataInputStream(BufferedInputStream(s.getInputStream()))
        while (running.get() && !s.isClosed) {
            val type =
                try {
                    input.readInt()
                } catch (_: Throwable) {
                    break
                }
            when (type) {
                MSG_KEY -> {
                    val keyCode = input.readInt()
                    val action = input.readInt()
                    main.post {
                        if (!running.get()) return@post
                        if (action == KeyEvent.ACTION_DOWN) remoteKeys.add(keyCode) else remoteKeys.remove(keyCode)
                        retroView?.sendKeyEvent(action, keyCode, remotePort)
                    }
                }
                MSG_MOTION -> {
                    val source = input.readInt()
                    val x = input.readFloat()
                    val y = input.readFloat()
                    main.post {
                        if (!running.get()) return@post
                        retroView?.sendMotionEvent(source, x, y, remotePort)
                    }
                }
                MSG_STATE -> {
                    val len = input.readInt().coerceIn(0, 8 * 1024 * 1024)
                    val bytes = ByteArray(len)
                    input.readFully(bytes)
                    if (!isHost) {
                        main.post {
                            val view = retroView ?: return@post
                            runCatching { view.unserializeState(bytes) }
                            remoteKeys.forEach { code ->
                                view.sendKeyEvent(KeyEvent.ACTION_DOWN, code, remotePort)
                            }
                        }
                    }
                }
                MSG_STATE_REQUEST -> {
                    if (isHost) pushStateNow()
                }
                MSG_HELLO -> {
                    runCatching { input.readUTF() }
                }
                else -> break
            }
        }
    }

    fun sendLocalKey(
        keyCode: Int,
        action: Int,
    ) {
        val stream = out ?: return
        if (!running.get()) return
        thread(name = "retro-netplay-send", isDaemon = true) {
            runCatching {
                synchronized(stream) {
                    stream.writeInt(MSG_KEY)
                    stream.writeInt(keyCode)
                    stream.writeInt(action)
                    stream.flush()
                }
            }
        }
    }

    fun sendLocalMotion(
        source: Int,
        x: Float,
        y: Float,
    ) {
        val stream = out ?: return
        if (!running.get()) return
        thread(name = "retro-netplay-motion", isDaemon = true) {
            runCatching {
                synchronized(stream) {
                    stream.writeInt(MSG_MOTION)
                    stream.writeInt(source)
                    stream.writeFloat(x)
                    stream.writeFloat(y)
                    stream.flush()
                }
            }
        }
    }

    fun onFrameRendered() {
        if (!isHost || !running.get() || out == null || retroView == null) return
        frameCounter++
        if (frameCounter % STATE_EVERY_FRAMES != 0) return
        pushStateNow()
    }

    fun stop() {
        stopInternal()
    }

    private fun cleanupSocketOnly() {
        runCatching { out?.close() }
        runCatching { socket?.close() }
        out = null
        socket = null
        val view = retroView
        remoteKeys.forEach { code ->
            main.post { view?.sendKeyEvent(KeyEvent.ACTION_UP, code, remotePort) }
        }
        remoteKeys.clear()
    }

    private fun stopInternal() {
        if (!running.getAndSet(false)) {
            cleanupSocketOnly()
            runCatching { server?.close() }
            server = null
            return
        }
        cleanupSocketOnly()
        runCatching { server?.close() }
        server = null
    }
}
