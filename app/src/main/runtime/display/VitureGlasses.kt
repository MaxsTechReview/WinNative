package com.winlator.cmod.runtime.display

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log

// Controls Viture XR glasses over their USB MCU interface (protocol RE'd from SpaceWalker's
// libglasses-internal.so): force panel refresh/3D and set brightness/electrochromic shade. Active
// only when real Viture glasses (vendor 0x35CA) are connected; TVs/monitors use the Android path.
class VitureGlasses(private val context: Context) {

    fun interface ConnectionListener {
        fun onVitureConnectionChanged(connected: Boolean)
    }

    companion object {
        private const val TAG = "VitureGlasses"
        const val VENDOR_ID = 0x35CA
        private const val ACTION_PERMISSION = "com.winlator.cmod.USB_PERMISSION_VITURE"

        // Product ids grouped by the command dialect the firmware speaks (from the decompiled tables).
        private val PID_ONE = intArrayOf(0x1011, 0x1013, 0x1015, 0x1017, 0x101b)
        private val PID_BEAST = intArrayOf(0x1201, 0x1211)

        // MCU command ids (msgId). Display/3D are the high-confidence path; the rest vary per model.
        private const val MSG_DISPLAY = 0x0008
        private const val MSG_DISPLAY_BEAST = 0x0124
        private const val MSG_BRIGHTNESS = 0x0006
        private const val MSG_BRIGHTNESS_BEAST = 0x0122
        private const val MSG_FILM_BINARY = 0x000E     // One: clear/dark only
        private const val MSG_FILM_STEPPED = 0x0330    // newer: 0..8 steps
        private const val MSG_VOLUME = 0x0033
        private const val MSG_VOLUME_BEAST = 0x0201

        // Display-mode bytes the firmware accepts (1080p family).
        const val MODE_1080P_60 = 0x31
        const val MODE_3840X1080_60 = 0x32 // side-by-side / 3D
        const val MODE_1080P_90 = 0x33
        const val MODE_1080P_120 = 0x34

        private const val PACKET_SIZE = 64

        // CRC-16/XMODEM table (poly 0x1021, init 0) — matches the table at 0x1273f2 in the binary.
        private val CRC_TABLE = IntArray(256).also { t ->
            for (i in 0 until 256) {
                var c = i shl 8
                repeat(8) { c = if (c and 0x8000 != 0) (c shl 1) xor 0x1021 else c shl 1 }
                t[i] = c and 0xFFFF
            }
        }
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var connection: UsbDeviceConnection? = null
    private var commandInterface: UsbInterface? = null
    private var controlInterface: UsbInterface? = null
    private var outEndpoint: UsbEndpoint? = null
    private var inEndpoint: UsbEndpoint? = null   // MCU INT-IN (ep 0x82) — state reports / verify
    private var productId = 0
    private var permissionPending = false
    private var listener: ConnectionListener? = null
    private var receiversRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PERMISSION -> {
                    permissionPending = false
                    val device = intent.deviceExtra()
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null) {
                        openDevice(device)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> attach()
                UsbManager.ACTION_USB_DEVICE_DETACHED ->
                    if (intent.deviceExtra()?.vendorId == VENDOR_ID) closeConnection()
            }
        }
    }

    fun setConnectionListener(l: ConnectionListener?) {
        listener = l
    }

    // Detect connected Viture glasses and open the command interface (requesting USB permission if needed).
    fun attach() {
        registerReceivers()
        if (connection != null) return
        val device = findVitureDevice() ?: return
        productId = device.productId
        if (usbManager.hasPermission(device)) {
            openDevice(device)
        } else if (!permissionPending) {
            requestPermission(device)
        }
    }

    fun detach() {
        closeConnection()
        if (receiversRegistered) {
            try { context.unregisterReceiver(receiver) } catch (ignore: Exception) {}
            receiversRegistered = false
        }
    }

    private fun closeConnection() {
        val wasConnected = connection != null
        connection?.let { c ->
            commandInterface?.let { c.releaseInterface(it) }
            controlInterface?.let { c.releaseInterface(it) }
            c.close()
        }
        connection = null
        commandInterface = null
        controlInterface = null
        outEndpoint = null
        inEndpoint = null
        if (wasConnected) listener?.onVitureConnectionChanged(false)
    }

    fun isConnected(): Boolean = connection != null && outEndpoint != null

    fun modelName(): String = when {
        productId == 0 -> "Viture"
        isBeast() -> "Viture Beast"
        isOne() -> "Viture One"
        else -> "Viture"
    }

    // ── Capabilities (all gated on a real connection) ──────────────────────

    fun supportsBrightness(): Boolean = isConnected()
    fun supports3D(): Boolean = isConnected()
    fun supportsFilm(): Boolean = isConnected()
    // Only Beast uses the stepped 0..8 film (msgId 0x0330); every other model is binary on/off (0x000E).
    fun filmIsStepped(): Boolean = isBeast()
    fun brightnessMax(): Int = if (isOne()) 6 else 8

    // ── High-level controls ────────────────────────────────────────────────

    /** Force the panel timing. hz 120/90/60 maps to the 1080p mode bytes (0x34/0x33/0x31). */
    fun forceRefreshHz(hz: Int): Boolean {
        val mode = when {
            hz >= 120 -> MODE_1080P_120
            hz >= 90 -> MODE_1080P_90
            else -> MODE_1080P_60
        }
        return setDisplayMode(mode)
    }

    fun setDisplayMode(modeByte: Int): Boolean {
        val msg = if (isBeast()) MSG_DISPLAY_BEAST else MSG_DISPLAY
        return send(msg, byteArrayOf(modeByte.toByte()))
    }

    /** 3D/SBS is the same display-mode command (0x32 = side-by-side on, 0x31 = off). */
    fun set3D(enabled: Boolean): Boolean =
        setDisplayMode(if (enabled) MODE_3840X1080_60 else MODE_1080P_60)

    fun setBrightness(level: Int): Boolean {
        val msg = if (isBeast()) MSG_BRIGHTNESS_BEAST else MSG_BRIGHTNESS
        val v = level.coerceIn(0, brightnessMax())
        return send(msg, byteArrayOf(v.toByte(), 0))
    }

    // Electrochromic shade — stepped 0..8 (newer) or binary 0/1 (One).
    fun setFilm(level: Int): Boolean {
        return if (filmIsStepped()) {
            send(MSG_FILM_STEPPED, byteArrayOf(level.coerceIn(0, 8).toByte()))
        } else {
            send(MSG_FILM_BINARY, byteArrayOf(if (level > 0) 1 else 0))
        }
    }

    // Glasses hardware volume (0..15 on Beast, else 0..8).
    fun setVolume(level: Int): Boolean {
        val msg = if (isBeast()) MSG_VOLUME_BEAST else MSG_VOLUME
        return send(msg, byteArrayOf(level.coerceIn(0, volumeMax()).toByte(), 0))
    }

    fun supportsVolume(): Boolean = isConnected()
    fun volumeMax(): Int = if (isBeast()) 15 else 8

    // ── USB plumbing ───────────────────────────────────────────────────────

    private fun findVitureDevice(): UsbDevice? =
        usbManager.deviceList.values.firstOrNull { it.vendorId == VENDOR_ID }

    private fun registerReceivers() {
        if (receiversRegistered) return
        val filter = IntentFilter(ACTION_PERMISSION).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        receiversRegistered = true
    }

    private fun requestPermission(device: UsbDevice) {
        registerReceivers()
        permissionPending = true
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(context, 0, Intent(ACTION_PERMISSION).setPackage(context.packageName), flags)
        usbManager.requestPermission(device, pi)
    }

    private fun openDevice(device: UsbDevice) {
        logInterfaces(device)
        val cmd = findCommandInterface(device) ?: run {
            Log.w(TAG, "No OUT endpoint found on ${device.productId.toHex16()}")
            return
        }
        val conn = usbManager.openDevice(device) ?: run { Log.w(TAG, "openDevice failed"); return }
        if (!conn.claimInterface(cmd.first, true)) {
            conn.close()
            Log.w(TAG, "claimInterface ${cmd.first.id} failed")
            return
        }
        // CDC-Data (class 10) command channels usually need the paired CDC control interface claimed
        // and DTR/RTS asserted (SET_CONTROL_LINE_STATE) before the bulk pipe carries data.
        var ctrl: UsbInterface? = null
        if (cmd.first.interfaceClass == 10) {
            ctrl = findInterfaceByClass(device, 2)
            if (ctrl != null && conn.claimInterface(ctrl, true)) {
                conn.controlTransfer(0x21, 0x22, 0x03, ctrl.id, null, 0, 200)
            }
        }
        connection = conn
        commandInterface = cmd.first
        controlInterface = ctrl
        outEndpoint = cmd.second
        inEndpoint = findInEndpoint(cmd.first)
        productId = device.productId
        Log.i(TAG, "Viture ${modelName()} opened: pid=${productId.toHex16()} cmdIface=${cmd.first.id} class=${cmd.first.interfaceClass} outEp=0x%02X(%s) ctrlIface=%s"
            .format(cmd.second.address, epType(cmd.second.type), ctrl?.id?.toString() ?: "-"))
        listener?.onVitureConnectionChanged(true)
    }

    // Commands go to the MCU, which is the highest-numbered HID interface with an interrupt-OUT
    // endpoint (the lower-numbered HID interface is the IMU). Falls back to bulk/any OUT otherwise.
    private fun findCommandInterface(device: UsbDevice): Pair<UsbInterface, UsbEndpoint>? {
        var hidMcu: Pair<UsbInterface, UsbEndpoint>? = null
        var bulk: Pair<UsbInterface, UsbEndpoint>? = null
        var intr: Pair<UsbInterface, UsbEndpoint>? = null
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.direction != UsbConstants.USB_DIR_OUT) continue
                when (ep.type) {
                    UsbConstants.USB_ENDPOINT_XFER_INT -> {
                        if (intf.interfaceClass == UsbConstants.USB_CLASS_HID &&
                            (hidMcu == null || intf.id > hidMcu!!.first.id)
                        ) hidMcu = intf to ep
                        if (intr == null) intr = intf to ep
                    }
                    UsbConstants.USB_ENDPOINT_XFER_BULK -> if (bulk == null) bulk = intf to ep
                }
            }
        }
        return hidMcu ?: bulk ?: intr
    }

    private fun findInterfaceByClass(device: UsbDevice, cls: Int): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == cls) return intf
        }
        return null
    }

    // The command interface's interrupt-IN endpoint (ep 0x82) — carries MCU state reports.
    private fun findInEndpoint(intf: UsbInterface): UsbEndpoint? {
        for (e in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(e)
            if (ep.direction == UsbConstants.USB_DIR_IN) return ep
        }
        return null
    }

    // Read one MCU report (FF FD | crc | len | 0000 | seq | msgId | flag | value) if one is queued.
    fun readState(timeoutMs: Int = 200): ByteArray? {
        val conn = connection ?: return null
        val ep = inEndpoint ?: return null
        val buf = ByteArray(PACKET_SIZE)
        val n = conn.bulkTransfer(ep, buf, buf.size, timeoutMs)
        return if (n > 0) buf.copyOf(n) else null
    }

    private fun logInterfaces(device: UsbDevice) {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            val eps = (0 until intf.endpointCount).joinToString {
                val ep = intf.getEndpoint(it)
                "0x%02X/%s/%s".format(ep.address, if (ep.direction == UsbConstants.USB_DIR_OUT) "OUT" else "IN", epType(ep.type))
            }
            Log.i(TAG, "iface ${intf.id} class=${intf.interfaceClass} eps=[$eps]")
        }
    }

    private fun epType(t: Int): String = when (t) {
        UsbConstants.USB_ENDPOINT_XFER_BULK -> "BULK"
        UsbConstants.USB_ENDPOINT_XFER_INT -> "INT"
        UsbConstants.USB_ENDPOINT_XFER_ISOC -> "ISO"
        else -> "CTRL"
    }

    // Build the V1 packet (FF FE | CRC16 | len | 8-byte header | msgId | 00 00 | payload) and send it.
    private fun send(msgId: Int, payload: ByteArray): Boolean {
        val conn = connection
        val ep = outEndpoint
        if (conn == null || ep == null) {
            Log.w(TAG, "send 0x%04X dropped — not connected".format(msgId))
            return false
        }
        val pkt = buildPacket(msgId, payload)
        val n = conn.bulkTransfer(ep, pkt, pkt.size, 250)
        Log.i(TAG, "send msgId=0x%04X payload=%s -> %d".format(msgId, payload.toHex(), n))
        return n >= 0
    }

    private fun buildPacket(msgId: Int, payload: ByteArray): ByteArray {
        val buf = ByteArray(PACKET_SIZE)
        buf[0] = 0xFF.toByte()
        buf[1] = 0xFE.toByte()
        val len = if (payload.isEmpty()) 0x0C else payload.size + 0x0C
        buf[4] = (len and 0xFF).toByte()
        buf[5] = ((len shr 8) and 0xFF).toByte()
        buf[14] = (msgId and 0xFF).toByte()
        buf[15] = ((msgId shr 8) and 0xFF).toByte()
        if (payload.isNotEmpty()) System.arraycopy(payload, 0, buf, 18, payload.size)
        val crc = crc16(buf, 4, len + 2)
        buf[2] = (crc and 0xFF).toByte()
        buf[3] = ((crc shr 8) and 0xFF).toByte()
        return buf
    }

    private fun crc16(data: ByteArray, offset: Int, length: Int): Int {
        var crc = 0
        for (i in offset until offset + length) {
            val b = data[i].toInt() and 0xFF
            crc = (CRC_TABLE[(b xor (crc ushr 8)) and 0xFF] xor (crc shl 8)) and 0xFFFF
        }
        return crc
    }

    private fun isOne(): Boolean = productId in PID_ONE
    private fun isBeast(): Boolean = productId in PID_BEAST

    private fun Intent.deviceExtra(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION") getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }

    private fun Int.toHex16(): String = "0x%04X".format(this)
    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}
