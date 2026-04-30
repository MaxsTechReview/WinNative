package com.winlator.cmod.feature.sync.google

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Stable per-device, per-app identifier used to scope Play Games Saved Games snapshots
 * (store tokens and download folders) to the device that produced them.
 *
 * The id is **deterministic** from `Settings.Secure.ANDROID_ID`, so a reinstall on the
 * same physical device recovers the same id and finds the device's prior snapshots.
 * It's persisted as a SharedPreferences cache only — if the cache is missing we
 * recompute from ANDROID_ID. A random fallback is used only when ANDROID_ID is blank
 * or matches the historically-buggy `9774d56d682e549c` value (in which case
 * reinstall recovery cannot be guaranteed for this install).
 *
 * The id is the first 16 hex chars of `SHA-256("winnative-device-v1|" + ANDROID_ID)`,
 * which is URL-safe (`[0-9a-f]{16}`) and meets the PGS snapshot-name charset
 * requirement.
 *
 * Raw ANDROID_ID is never published anywhere; only the hashed id leaves the device.
 */
internal object DeviceIdentity {
    private const val PREFS = "winnative_device_identity"
    private const val KEY_ID = "device_id_v1"
    private const val KEY_LABEL_CACHED = "device_label_v1"
    private const val DOMAIN_TAG = "winnative-device-v1"
    private const val KNOWN_BAD_ANDROID_ID = "9774d56d682e549c"

    /**
     * Returns the 16-hex-char device id, computing+persisting on first call.
     * Idempotent across calls within the same install. Stable across reinstalls
     * provided ANDROID_ID is stable (i.e. same signing key, same physical device,
     * no factory reset).
     */
    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_ID, null)?.takeIf { it.isNotEmpty() }?.let { return it }

        val id = computeDeviceId(context)
        prefs.edit().putString(KEY_ID, id).apply()
        return id
    }

    /** Human-readable label for the cross-device picker UI. */
    fun deviceLabel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_LABEL_CACHED, null)?.takeIf { it.isNotEmpty() }?.let { return it }
        val label = "${Build.MANUFACTURER ?: "Unknown"} ${Build.MODEL ?: "Device"}".trim().take(64)
        prefs.edit().putString(KEY_LABEL_CACHED, label).apply()
        return label
    }

    @SuppressLint("HardwareIds")
    private fun computeDeviceId(context: Context): String {
        val raw =
            Settings.Secure
                .getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?.takeIf { it.isNotBlank() && it != KNOWN_BAD_ANDROID_ID }

        return if (raw != null) {
            val md = MessageDigest.getInstance("SHA-256")
            md.update("$DOMAIN_TAG|$raw".toByteArray(StandardCharsets.UTF_8))
            md.digest().take(8).joinToString("") { "%02x".format(it) }
        } else {
            // Fallback: random; reinstall recovery cannot be guaranteed for this install.
            val bytes = ByteArray(8)
            SecureRandom().nextBytes(bytes)
            bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
