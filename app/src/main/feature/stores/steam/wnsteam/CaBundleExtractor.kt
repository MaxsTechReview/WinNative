package com.winlator.cmod.feature.stores.steam.wnsteam

import android.content.Context
import timber.log.Timber
import java.io.File

/**
 * IXWebSocket's OpenSSL backend expects a single PEM trust bundle file
 * (`SSL_CTX_load_verify_locations` CAfile slot). Android's CA store is a
 * **directory** of hashed-filename PEMs under `/system/etc/security/cacerts`,
 * which OpenSSL can't consume in that form via IXWebSocket's API.
 *
 * This helper concatenates every `*.0` PEM in the system CA directory into
 * a single file under the app's `filesDir/wnsteam_cacert.pem`, on first run.
 * Subsequent calls reuse the cached bundle.
 *
 * The output path is what [WnSteamSession.setCaBundlePath] expects.
 */
object CaBundleExtractor {

    private const val OUT_NAME = "wnsteam_cacert.pem"
    private const val SYS_CA_DIR = "/system/etc/security/cacerts"

    /**
     * Ensures the bundle exists; returns its absolute path. Empty string
     * on failure (in which case TLS handshakes will be rejected).
     */
    fun ensureBundle(context: Context): String {
        val out = File(context.filesDir, OUT_NAME)
        if (out.exists() && out.length() > 1024) {
            // Sanity floor: a single empty file means a prior extraction failed.
            return out.absolutePath
        }

        val src = File(SYS_CA_DIR)
        if (!src.isDirectory) {
            Timber.w("WnSteam CA bundle: %s missing, TLS will fail", SYS_CA_DIR)
            return ""
        }
        val pems = src.listFiles { f -> f.isFile && f.name.endsWith(".0") }
            ?: emptyArray()
        if (pems.isEmpty()) {
            Timber.w("WnSteam CA bundle: no *.0 files under %s", SYS_CA_DIR)
            return ""
        }

        try {
            out.bufferedWriter().use { w ->
                for (f in pems) {
                    try {
                        f.bufferedReader().use { r ->
                            r.copyTo(w)
                        }
                        w.newLine()
                    } catch (e: Exception) {
                        Timber.w(e, "WnSteam CA: skipped %s", f.name)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "WnSteam CA: bundle write failed")
            return ""
        }
        Timber.i("WnSteam CA bundle ready: %s (%d certs, %d bytes)",
            out.absolutePath, pems.size, out.length())
        return out.absolutePath
    }
}
