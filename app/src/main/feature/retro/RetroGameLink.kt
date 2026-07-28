package com.winlator.cmod.feature.retro

import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.Variable

object RetroGameLink {
    const val DEFAULT_PORT = 56400

    const val MODE_KEY = "gambatte_gb_link_mode"
    const val PORT_KEY = "gambatte_gb_link_network_port"
    private const val IP_KEY_PREFIX = "gambatte_gb_link_network_server_ip_"

    const val MODE_OFF = "Not Connected"
    const val MODE_SERVER = "Network Server"
    const val MODE_CLIENT = "Network Client"

    fun isSupported(systemId: String?): Boolean =
        systemId == RetroSystems.GAMEBOY.id ||
            systemId == RetroSystems.GAMEBOY_COLOR.id ||
            systemId == RetroSystems.GBA.id

    const val GPSP_SERIAL_KEY = "gpsp_serial"
    const val GPSP_SERIAL_RFU = "rfu"
    const val GBA_NETPACKET_PORT = 55437

    fun clampPort(port: Int): Int =
        when {
            port in 56400..56420 -> port
            else -> DEFAULT_PORT
        }

    fun ipv4ToDigits(ip: String): List<String> {
        val parts =
            ip.trim()
                .split('.')
                .mapNotNull { it.toIntOrNull()?.coerceIn(0, 255) }
        if (parts.size != 4) return List(12) { "0" }
        return parts.flatMap { octet ->
            val s = "%03d".format(octet)
            listOf(s[0].toString(), s[1].toString(), s[2].toString())
        }
    }

    fun digitsToIpv4(digits: List<String>): String {
        if (digits.size < 12) return "0.0.0.0"
        fun octet(a: Int) =
            ((digits.getOrNull(a)?.toIntOrNull() ?: 0) * 100 +
                (digits.getOrNull(a + 1)?.toIntOrNull() ?: 0) * 10 +
                (digits.getOrNull(a + 2)?.toIntOrNull() ?: 0))
                .coerceIn(0, 255)
        return "${octet(0)}.${octet(3)}.${octet(6)}.${octet(9)}"
    }

    fun buildHostVariables(port: Int): List<Variable> {
        val p = clampPort(port)
        return listOf(
            Variable(MODE_KEY, MODE_SERVER),
            Variable(PORT_KEY, p.toString()),
        )
    }

    fun buildClientVariables(
        hostIp: String,
        port: Int,
    ): List<Variable> {
        val p = clampPort(port)
        val digits = ipv4ToDigits(hostIp)
        val vars = mutableListOf(
            Variable(MODE_KEY, MODE_CLIENT),
            Variable(PORT_KEY, p.toString()),
        )
        digits.forEachIndexed { i, d ->
            vars += Variable("$IP_KEY_PREFIX${i + 1}", d)
        }
        return vars
    }

    fun buildDisconnectVariables(): List<Variable> =
        listOf(Variable(MODE_KEY, MODE_OFF))

    fun applyToCore(
        retroView: GLRetroView,
        variables: List<Variable>,
    ) {
        if (variables.isEmpty()) return
        runCatching {
            retroView.updateVariables(*variables.toTypedArray())
        }
    }

    fun startHost(
        retroView: GLRetroView?,
        port: Int,
    ) {
        val vars = buildHostVariables(port)
        if (retroView != null) applyToCore(retroView, vars)
    }

    fun startClient(
        retroView: GLRetroView?,
        hostIp: String,
        port: Int,
    ) {
        val vars = buildClientVariables(hostIp, port)
        if (retroView != null) applyToCore(retroView, vars)
    }

    fun stop(retroView: GLRetroView?) {
        runCatching { retroView?.netpacketStop() }
        if (retroView != null) {
            runCatching { applyToCore(retroView, buildDisconnectVariables()) }
            runCatching {
                retroView.updateVariables(Variable(GPSP_SERIAL_KEY, "disabled"))
            }
        }
    }

    fun startGbaHost(
        retroView: GLRetroView,
        port: Int = GBA_NETPACKET_PORT,
    ): Boolean {
        applyToCore(
            retroView,
            listOf(Variable(GPSP_SERIAL_KEY, GPSP_SERIAL_RFU)),
        )
        if (!retroView.netpacketHasCore()) return false
        return retroView.netpacketStartHost(port)
    }

    fun startGbaClient(
        retroView: GLRetroView,
        hostIp: String,
        port: Int = GBA_NETPACKET_PORT,
    ): Boolean {
        applyToCore(
            retroView,
            listOf(Variable(GPSP_SERIAL_KEY, GPSP_SERIAL_RFU)),
        )
        if (!retroView.netpacketHasCore()) return false
        return retroView.netpacketStartClient(hostIp, port)
    }
}
