package com.winlator.cmod.feature.stores.steam.enums
import java.util.Locale

enum class DownloadPhase {
    UNKNOWN,
    PREPARING,
    DOWNLOADING,
    PAUSED,
    FAILED,
    VERIFYING,
    PATCHING,
    APPLYING_DATA,
    FINALIZING,
    QUEUED,
    UNPACKING,
    COMPLETE,
    CANCELLED,
    ;

    companion object {
        private val parseRules: List<Pair<DownloadPhase, List<String>>> =
            listOf(
                FAILED to listOf("fail", "error", "abort", "cancelled"),
                PAUSED to listOf("pause", "paused"),
                VERIFYING to listOf("verify", "validat", "checksum", "hash", "integrity", "scan"),
                PATCHING to listOf("patch", "delta", "differential"),
                FINALIZING to listOf("final", "finishing", "commit", "cleanup", "clean up", "register", "ready"),
                APPLYING_DATA to
                    listOf("decompress", "extract", "unpack", "decrypt", "assemble", "apply", "install", "writing", "moving", "processing"),
                PREPARING to listOf("queue", "queued", "waiting", "prepar", "initial", "manifest", "resolve", "starting", "setup", "init"),
                DOWNLOADING to listOf("download", "retriev", "fetch", "allocat", "prealloc", "reserve", "chunk", "transfer", "cdn"),
            )

        fun fromMessage(message: String): DownloadPhase? {
            val lower = message.lowercase(Locale.US)
            return parseRules
                .firstOrNull { (_, keywords) ->
                    keywords.any(lower::contains)
                }?.first
        }
    }
}
