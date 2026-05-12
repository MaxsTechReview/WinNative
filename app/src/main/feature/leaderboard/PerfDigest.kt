package com.winlator.cmod.feature.leaderboard

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Compact 48-byte performance digest packed into a Play Games Services scoreTag.
 *
 * The scoreTag field is constrained by RFC 3986 §2.3 to 64 URI-safe characters, which is
 * exactly 48 raw bytes when base64url-encoded with no padding (48 × 8 / 6 = 64). Each game
 * session that submits to the global "performance_v1" leaderboard packs its summary stats
 * plus four short content-addressed hashes (game id, gpu, soc, container config) plus a
 * truncated android id hash into this fixed layout.
 *
 * No HMAC is included: the secret would ship inside the APK and is trivially extractable.
 * Identity verification is provided by PGS (Google account auth on submit). Stats-recompute
 * anti-cheat lives server-side in the future Tier 1 backend.
 */
data class PerfDigest(
    val schemaVersion: Byte = SCHEMA_VERSION,
    val flags: Int,
    val sessionDurationSeconds: Long,
    val avgFps: Float,
    val p1LowFps: Float,
    val avgFrametimeMs: Float,
    val p99FrametimeMs: Float,
    val medianFrametimeMs: Float,
    /** Self-process CPU usage 0..100. Values above 127 are clipped on encode (u8 / 2 storage). */
    val avgCpuPct: Int,
    /** GPU busy percentage 0..100, or 0 when sysfs is unreadable. Same 0..127 storage limit. */
    val avgGpuPct: Int,
    val peakSocTempC: Float,
    val avgSocTempC: Float,
    val batteryDropPct: Int,
    /**
     * Average battery current magnitude in milliamps. The caller is responsible for
     * converting microamps → milliamps and taking absolute value (sign of
     * BATTERY_PROPERTY_CURRENT_NOW is OEM-dependent). u16 caps at 65,535 mA.
     */
    val avgBatteryCurrentMa: Int,
    val thermalThrottleSeconds: Int,
    val gameIdHash: ByteArray,
    val gpuModelHash: ByteArray,
    val socModelHash: ByteArray,
    val containerConfigHash: ByteArray,
    val androidIdShortHash: ByteArray,
) {
    init {
        // Internal invariants — all hash producers in this codebase return exactly HASH_SIZE
        // bytes via PerfDigest.shortHash / HardwareDictionary.*HashOf. A violation here
        // indicates a programming bug, not user input, so check (not require).
        check(gameIdHash.size == HASH_SIZE) { "gameIdHash must be $HASH_SIZE bytes" }
        check(gpuModelHash.size == HASH_SIZE) { "gpuModelHash must be $HASH_SIZE bytes" }
        check(socModelHash.size == HASH_SIZE) { "socModelHash must be $HASH_SIZE bytes" }
        check(containerConfigHash.size == HASH_SIZE) { "containerConfigHash must be $HASH_SIZE bytes" }
        check(androidIdShortHash.size == HASH_SIZE) { "androidIdShortHash must be $HASH_SIZE bytes" }
    }

    fun isThermalThrottled(): Boolean = (flags and FLAG_THERMAL_THROTTLED) != 0

    /** True when the submitter opted into display-anonymity for this run. */
    fun isAnonymous(): Boolean = (flags and FLAG_ANONYMOUS) != 0

    fun encode(): String {
        val buf = ByteBuffer.allocate(BYTE_LENGTH).order(ByteOrder.BIG_ENDIAN)
        buf.put(schemaVersion)
        buf.put((flags and 0xFF).toByte())
        buf.putInt(sessionDurationSeconds.coerceIn(0L, 0xFFFFFFFFL).toInt())
        buf.putShort(encodeU16(avgFps * 10f))
        buf.putShort(encodeU16(p1LowFps * 10f))
        buf.putShort(encodeU16(avgFrametimeMs * 100f))
        buf.putShort(encodeU16(p99FrametimeMs * 100f))
        buf.putShort(encodeU16(medianFrametimeMs * 100f))
        buf.put(encodeU8((avgCpuPct * 2).toFloat()))
        buf.put(encodeU8((avgGpuPct * 2).toFloat()))
        buf.putShort(encodeU16(peakSocTempC * 10f))
        buf.putShort(encodeU16(avgSocTempC * 10f))
        buf.put(encodeU8(batteryDropPct.toFloat()))
        buf.putShort(encodeU16(avgBatteryCurrentMa.toFloat()))
        buf.putShort(encodeU16(thermalThrottleSeconds.toFloat()))
        buf.put(gameIdHash)
        buf.put(gpuModelHash)
        buf.put(socModelHash)
        buf.put(containerConfigHash)
        buf.put(androidIdShortHash)
        buf.put(0.toByte())
        check(!buf.hasRemaining()) { "PerfDigest layout mismatch: ${buf.remaining()} bytes unused" }
        val out = Base64.encodeToString(buf.array(), BASE64_FLAGS)
        check(out.length == ENCODED_LENGTH) { "encoded tag length=${out.length}, expected=$ENCODED_LENGTH" }
        return out
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PerfDigest) return false
        return schemaVersion == other.schemaVersion &&
            flags == other.flags &&
            sessionDurationSeconds == other.sessionDurationSeconds &&
            avgFps == other.avgFps &&
            p1LowFps == other.p1LowFps &&
            avgFrametimeMs == other.avgFrametimeMs &&
            p99FrametimeMs == other.p99FrametimeMs &&
            medianFrametimeMs == other.medianFrametimeMs &&
            avgCpuPct == other.avgCpuPct &&
            avgGpuPct == other.avgGpuPct &&
            peakSocTempC == other.peakSocTempC &&
            avgSocTempC == other.avgSocTempC &&
            batteryDropPct == other.batteryDropPct &&
            avgBatteryCurrentMa == other.avgBatteryCurrentMa &&
            thermalThrottleSeconds == other.thermalThrottleSeconds &&
            gameIdHash.contentEquals(other.gameIdHash) &&
            gpuModelHash.contentEquals(other.gpuModelHash) &&
            socModelHash.contentEquals(other.socModelHash) &&
            containerConfigHash.contentEquals(other.containerConfigHash) &&
            androidIdShortHash.contentEquals(other.androidIdShortHash)
    }

    override fun hashCode(): Int {
        var result = schemaVersion.toInt()
        result = 31 * result + flags
        result = 31 * result + sessionDurationSeconds.hashCode()
        result = 31 * result + avgFps.hashCode()
        result = 31 * result + p1LowFps.hashCode()
        result = 31 * result + avgFrametimeMs.hashCode()
        result = 31 * result + p99FrametimeMs.hashCode()
        result = 31 * result + medianFrametimeMs.hashCode()
        result = 31 * result + avgCpuPct
        result = 31 * result + avgGpuPct
        result = 31 * result + peakSocTempC.hashCode()
        result = 31 * result + avgSocTempC.hashCode()
        result = 31 * result + batteryDropPct
        result = 31 * result + avgBatteryCurrentMa
        result = 31 * result + thermalThrottleSeconds
        result = 31 * result + gameIdHash.contentHashCode()
        result = 31 * result + gpuModelHash.contentHashCode()
        result = 31 * result + socModelHash.contentHashCode()
        result = 31 * result + containerConfigHash.contentHashCode()
        result = 31 * result + androidIdShortHash.contentHashCode()
        return result
    }

    companion object {
        const val SCHEMA_VERSION: Byte = 1
        const val BYTE_LENGTH: Int = 48
        const val ENCODED_LENGTH: Int = 64
        const val HASH_SIZE: Int = 4

        const val FLAG_THERMAL_THROTTLED: Int = 0x01
        const val FLAG_DXVK_ENABLED: Int = 0x02
        const val FLAG_FSR_ENABLED: Int = 0x04
        const val FLAG_VKD3D_ENABLED: Int = 0x08
        const val FLAG_ESYNC_ENABLED: Int = 0x10
        const val FLAG_GYRO_ENABLED: Int = 0x20

        /**
         * When set, our in-app leaderboard renders other players' rows as "Anonymous"
         * instead of the PGS gamertag. Note: this is display-only anonymity inside
         * this app. The native Google Play Games UI and the raw PGS REST API still
         * show the underlying player. Real cryptographic anonymity belongs to Tier 1.
         */
        const val FLAG_ANONYMOUS: Int = 0x40

        private const val BASE64_FLAGS: Int = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        private val URL_SAFE_64 = Regex("^[A-Za-z0-9_-]{${ENCODED_LENGTH}}$")

        /**
         * Returns null if the tag has the wrong length, fails to decode, or carries an
         * unsupported schema version. Forward-compat: a future v2 digest will be decoded
         * by [decodeV2], leaving this v1 path stable.
         */
        fun decode(scoreTag: String?): PerfDigest? {
            if (scoreTag.isNullOrEmpty() || scoreTag.length != ENCODED_LENGTH) return null
            if (!URL_SAFE_64.matches(scoreTag)) return null
            val bytes = runCatching { Base64.decode(scoreTag, BASE64_FLAGS) }.getOrNull() ?: return null
            if (bytes.size != BYTE_LENGTH) return null
            if (bytes[0] != SCHEMA_VERSION) return null
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val version = buf.get()
            val flags = buf.get().toInt() and 0xFF
            val duration = buf.int.toLong() and 0xFFFFFFFFL
            val avgFps = decodeU16(buf.short) / 10f
            val p1Low = decodeU16(buf.short) / 10f
            val avgFt = decodeU16(buf.short) / 100f
            val p99Ft = decodeU16(buf.short) / 100f
            val medFt = decodeU16(buf.short) / 100f
            val avgCpu = (decodeU8(buf.get()) / 2)
            val avgGpu = (decodeU8(buf.get()) / 2)
            val peakTemp = decodeU16(buf.short) / 10f
            val avgTemp = decodeU16(buf.short) / 10f
            val battDrop = decodeU8(buf.get())
            val battCurrent = decodeU16(buf.short)
            val throttle = decodeU16(buf.short)
            val gameHash = ByteArray(HASH_SIZE).also { buf.get(it) }
            val gpuHash = ByteArray(HASH_SIZE).also { buf.get(it) }
            val socHash = ByteArray(HASH_SIZE).also { buf.get(it) }
            val configHash = ByteArray(HASH_SIZE).also { buf.get(it) }
            val deviceHash = ByteArray(HASH_SIZE).also { buf.get(it) }
            // Skip reserved byte (read but unused).
            buf.get()
            return PerfDigest(
                schemaVersion = version,
                flags = flags,
                sessionDurationSeconds = duration,
                avgFps = avgFps,
                p1LowFps = p1Low,
                avgFrametimeMs = avgFt,
                p99FrametimeMs = p99Ft,
                medianFrametimeMs = medFt,
                avgCpuPct = avgCpu,
                avgGpuPct = avgGpu,
                peakSocTempC = peakTemp,
                avgSocTempC = avgTemp,
                batteryDropPct = battDrop,
                avgBatteryCurrentMa = battCurrent,
                thermalThrottleSeconds = throttle,
                gameIdHash = gameHash,
                gpuModelHash = gpuHash,
                socModelHash = socHash,
                containerConfigHash = configHash,
                androidIdShortHash = deviceHash,
            )
        }

        /**
         * Computes a 4-byte SHA-256 prefix used as a short content-addressed identifier
         * for game ids, GPU/SoC model strings, container configs, and device ids.
         */
        fun shortHash(input: String): ByteArray {
            val full = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return full.copyOf(HASH_SIZE)
        }

        /**
         * Encodes the game source + game id into the canonical 4-byte hash used by the
         * client-side leaderboard filter. Format: "STEAM:1091500", "EPIC:Fortnite",
         * "GOG:1207658886", "CUSTOM:<exePath>".
         */
        fun gameIdHashOf(gameSource: String, gameId: String): ByteArray =
            shortHash("$gameSource:$gameId")

        private fun encodeU16(value: Float): Short {
            if (value.isNaN()) return 0
            val rounded = Math.round(value.coerceIn(0f, 65535f))
            return (rounded and 0xFFFF).toShort()
        }

        private fun encodeU8(value: Float): Byte {
            if (value.isNaN()) return 0
            val rounded = Math.round(value.coerceIn(0f, 255f))
            return (rounded and 0xFF).toByte()
        }

        private fun decodeU16(value: Short): Int = value.toInt() and 0xFFFF

        private fun decodeU8(value: Byte): Int = value.toInt() and 0xFF
    }
}
