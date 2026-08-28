package com.winlator.cmod.app.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

object UpdateApkInstaller {
    private const val ACTION_STATUS = "com.winlator.cmod.UPDATE_INSTALL_STATUS"
    private const val BUFFER = 1 shl 16
    private const val RELEASE_CERT_SHA256 =
        "94de8fe193e378604f4415d87c63dece030605460b8e9ee6d447e1fc90840980"

    fun isOfficialInstall(context: Context): Boolean =
        runCatching {
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    @Suppress("DEPRECATION")
                    PackageManager.GET_SIGNATURES
                }
            digestsOf(context.packageManager.getPackageInfo(context.packageName, flags))
                .contains(RELEASE_CERT_SHA256)
        }.getOrElse {
            Timber.w(it, "Could not read the installed signing certificate")
            false
        }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun matchesInstalledSignature(
        context: Context,
        apk: File,
    ): Boolean =
        runCatching {
            val pm = context.packageManager
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    @Suppress("DEPRECATION")
                    PackageManager.GET_SIGNATURES
                }
            val candidate = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
            if (candidate.packageName != context.packageName) return false
            val installed = pm.getPackageInfo(context.packageName, flags)
            digestsOf(candidate) == digestsOf(installed)
        }.getOrElse {
            Timber.w(it, "Could not compare update signature")
            false
        }

    private fun digestsOf(info: android.content.pm.PackageInfo): Set<String> {
        val signatures =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.let {
                    if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.orEmpty().map { digest.digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
    }

    fun install(
        context: Context,
        apk: File,
        onFailure: (String) -> Unit,
    ) {
        val appContext = context.applicationContext
        registerReceiver(appContext, onFailure)

        runCatching {
            val installer = appContext.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(appContext.packageName)
            params.setSize(apk.length())

            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("winnative", 0, apk.length()).use { output ->
                    apk.inputStream().use { input ->
                        val buffer = ByteArray(BUFFER)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                    session.fsync(output)
                }
                session.commit(statusIntent(appContext, sessionId).intentSender)
            }
            UpdateDownloader.clear(appContext)
        }.onFailure {
            Timber.e(it, "Update install failed")
            onFailure(it.message ?: "Install failed")
        }
    }

    private fun statusIntent(
        context: Context,
        sessionId: Int,
    ): PendingIntent {
        val intent = Intent(ACTION_STATUS).setPackage(context.packageName)
        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        return PendingIntent.getBroadcast(context, sessionId, intent, flags)
    }

    @Volatile
    private var receiver: BroadcastReceiver? = null

    private fun registerReceiver(
        context: Context,
        onFailure: (String) -> Unit,
    ) {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        val created =
            object : BroadcastReceiver() {
                override fun onReceive(
                    received: Context,
                    intent: Intent,
                ) {
                    when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            val confirm =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                                }
                            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { received.startActivity(confirm) }
                        }
                        PackageInstaller.STATUS_SUCCESS -> {
                            UpdateDownloader.clear(received)
                        }
                        else -> {
                            val message =
                                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed"
                            Timber.w("Update install status: $message")
                            onFailure(message)
                        }
                    }
                }
            }
        val filter = IntentFilter(ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(created, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(created, filter)
        }
        receiver = created
    }
}
