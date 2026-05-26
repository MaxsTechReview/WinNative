package com.winlator.cmod.app.update
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.winlator.cmod.app.PluviaApp
import com.winlator.cmod.runtime.display.XServerDisplayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern

object UpdateChecker {
    private const val DOWNLOADS_PAGE_URL = "https://winnative.dev/Downloads/"
    private const val RELEASE_NOTES_URL = "${DOWNLOADS_PAGE_URL}release.txt"

    private const val PREF_CHECK_FOR_UPDATES = "check_for_updates"
    private const val PREF_INSTALL_TIMESTAMP = "app_install_timestamp"
    private const val PREF_LAST_UPDATE_CHECK = "last_update_check_time"

    private const val CHECK_INTERVAL_MS = 60 * 60 * 1000L
    private const val MANUAL_CHECK_COOLDOWN_MS = 30 * 1000L
    private const val POST_GAME_CHECK_DELAY_MS = 10 * 1000L

    private val lastManualCheckTime = AtomicLong(0L)

    private val isChecking = AtomicBoolean(false)

    private var backgroundHandler: Handler? = null
    private var backgroundRunnable: Runnable? = null

    private var postGameHandler: Handler? = null
    private var postGameRunnable: Runnable? = null

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    fun isEnabled(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(PREF_CHECK_FOR_UPDATES, false)
    }

    // Record the app install/update timestamp from PackageManager.
    fun refreshInstallTimestamp(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val installTime = pInfo.lastUpdateTime.coerceAtLeast(pInfo.firstInstallTime)
            prefs.edit().putLong(PREF_INSTALL_TIMESTAMP, installTime).apply()
        } catch (e: PackageManager.NameNotFoundException) {
            if (!prefs.contains(PREF_INSTALL_TIMESTAMP)) {
                prefs.edit().putLong(PREF_INSTALL_TIMESTAMP, System.currentTimeMillis()).apply()
            }
        }
    }

    fun isDueForCheck(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val lastCheck = prefs.getLong(PREF_LAST_UPDATE_CHECK, 0L)
        return System.currentTimeMillis() - lastCheck >= CHECK_INTERVAL_MS
    }

    // Start the hourly background loop when auto-update is enabled.
    fun startBackgroundLoop(context: Context) {
        stopBackgroundLoop()
        if (!isEnabled(context)) return
        if (!isAutoCheckAllowed()) return

        val appContext = context.applicationContext
        backgroundHandler = Handler(Looper.getMainLooper())
        backgroundRunnable =
            object : Runnable {
                override fun run() {
                    if (isEnabled(appContext) && isAutoCheckAllowed()) {
                        checkForUpdate(appContext, force = false)
                        backgroundHandler?.postDelayed(this, CHECK_INTERVAL_MS)
                    }
                }
            }
        backgroundHandler?.postDelayed(backgroundRunnable!!, 5_000L)
    }

    fun stopBackgroundLoop() {
        backgroundRunnable?.let { backgroundHandler?.removeCallbacks(it) }
        backgroundHandler = null
        backgroundRunnable = null
    }

    // Perform an automatic update check.
    fun checkForUpdate(
        context: Context,
        force: Boolean = false,
    ) {
        if (!isEnabled(context)) return
        if (!isAutoCheckAllowed()) return
        if (!force && !isDueForCheck(context)) return
        launchCheck(context)
    }

    // Manual check; returns false while in cooldown.
    fun checkForUpdateManual(context: Context): Boolean {
        val now = System.currentTimeMillis()
        val last = lastManualCheckTime.get()
        if (now - last < MANUAL_CHECK_COOLDOWN_MS) return false
        lastManualCheckTime.set(now)
        launchCheck(context)
        return true
    }

    fun manualCheckCooldownSeconds(): Int {
        val elapsed = System.currentTimeMillis() - lastManualCheckTime.get()
        val remaining = MANUAL_CHECK_COOLDOWN_MS - elapsed
        return if (remaining > 0) ((remaining + 999) / 1000).toInt() else 0
    }

    // Schedule a deferred update check after a game exits.
    fun schedulePostGameCheck(context: Context) {
        cancelPostGameCheck()
        if (!isEnabled(context)) return
        if (!isAutoCheckAllowed()) return
        val appContext = context.applicationContext
        postGameHandler = Handler(Looper.getMainLooper())
        postGameRunnable =
            Runnable {
                if (isAutoCheckAllowed()) {
                    checkForUpdate(appContext, force = true)
                }
            }
        postGameHandler?.postDelayed(postGameRunnable!!, POST_GAME_CHECK_DELAY_MS)
    }

    fun cancelPostGameCheck() {
        postGameRunnable?.let { postGameHandler?.removeCallbacks(it) }
        postGameHandler = null
        postGameRunnable = null
    }

    fun resetCheckTimer(context: Context) {
        PreferenceManager
            .getDefaultSharedPreferences(context)
            .edit()
            .putLong(PREF_LAST_UPDATE_CHECK, 0L)
            .apply()
    }

    private fun isAutoCheckAllowed(): Boolean {
        val activity = PluviaApp.currentForegroundActivity ?: return false
        return activity !is XServerDisplayActivity
    }

    private fun launchCheck(context: Context) {
        if (!isChecking.compareAndSet(false, true)) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                refreshInstallTimestamp(context)
                val result = fetchUpdateInfo(context)
                if (result != null) {
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(context, result)
                    }
                }
                PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .edit()
                    .putLong(PREF_LAST_UPDATE_CHECK, System.currentTimeMillis())
                    .apply()
            } catch (e: Exception) {
                Timber.e(e, "Update check failed")
            } finally {
                isChecking.set(false)
            }
        }
    }

    data class UpdateInfo(
        val serverModified: Date,
        val serverModifiedFormatted: String,
        val serverVersionName: String?,
        val downloadUrl: String,
        val releaseNotes: String?,
    )

    // Fetch the downloads page and compare its "Last Updated" date.
    private fun fetchUpdateInfo(context: Context): UpdateInfo? {
        val pageRequest =
            Request
                .Builder()
                .url(DOWNLOADS_PAGE_URL)
                .header("Cache-Control", "no-cache")
                .build()

        val pageBody =
            client.newCall(pageRequest).execute().use { pageResponse ->
                if (!pageResponse.isSuccessful) {
                    Timber.w("Update check page request failed: ${pageResponse.code}")
                    return null
                }
                pageResponse.body?.string() ?: return null
            }

        val pattern =
            Pattern.compile(
                """Last\s+Updated:\s*(.+?)(?:\r?\n|<)""",
                Pattern.CASE_INSENSITIVE,
            )
        val matcher = pattern.matcher(pageBody)
        if (!matcher.find()) {
            Timber.w("Could not find 'Last Updated' on downloads page")
            return null
        }

        val lastUpdatedStr = matcher.group(1)?.trim() ?: return null
        val serverDate =
            parseLastUpdatedDate(lastUpdatedStr) ?: run {
                Timber.w("Could not parse 'Last Updated' date: $lastUpdatedStr")
                return null
            }

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val installTimestamp = prefs.getLong(PREF_INSTALL_TIMESTAMP, System.currentTimeMillis())

        if (serverDate.time <= installTimestamp) {
            return null
        }

        val apkType =
            when (context.packageName) {
                "com.ludashi.benchmark" -> "ludashi"
                "com.tencent.ig" -> null
                else -> "standard"
            } ?: return null
        val downloadUrl = "${DOWNLOADS_PAGE_URL}download.php?type=$apkType"

        val releaseNotes = fetchReleaseNotes()

        val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.US)
        dateFormat.timeZone = TimeZone.getDefault()

        return UpdateInfo(
            serverModified = serverDate,
            serverModifiedFormatted = dateFormat.format(serverDate),
            serverVersionName = null,
            downloadUrl = downloadUrl,
            releaseNotes = releaseNotes,
        )
    }

    private fun parseLastUpdatedDate(dateStr: String): Date? {
        val formats =
            arrayOf(
                "MMMM d, yyyy, h:mm a z",
                "MMMM d, yyyy, h:mm a zzz",
                "MMMM dd, yyyy, h:mm a z",
                "MMMM dd, yyyy, h:mm a zzz",
                "MMMM d, yyyy, hh:mm a z",
                "MMMM d, yyyy, hh:mm a zzz",
                "MMMM dd, yyyy, hh:mm a z",
                "MMMM dd, yyyy, hh:mm a zzz",
            )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                return sdf.parse(dateStr)
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun fetchReleaseNotes(): String? =
        try {
            val request =
                Request
                    .Builder()
                    .url(RELEASE_NOTES_URL)
                    .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body
                        ?.string()
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Timber.d(e, "Could not fetch release notes")
            null
        }

    private fun showUpdateDialog(
        context: Context,
        info: UpdateInfo,
    ) {
        if (context is android.app.Activity && context.isFinishing) return

        val padding = (16 * context.resources.displayMetrics.density).toInt()
        val smallPad = (8 * context.resources.displayMetrics.density).toInt()

        val container =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, padding)
            }

        val releasedLabel =
            TextView(context).apply {
                text = "Released: ${info.serverModifiedFormatted}"
                setTextColor(0xFFB0B0B0.toInt())
                textSize = 14f
            }
        container.addView(releasedLabel)

        if (!info.releaseNotes.isNullOrBlank()) {
            val divider =
                android.view.View(context).apply {
                    layoutParams =
                        LinearLayout
                            .LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                (1 * context.resources.displayMetrics.density).toInt(),
                            ).apply {
                                topMargin = padding
                                bottomMargin = smallPad
                            }
                    setBackgroundColor(0xFF444444.toInt())
                }
            container.addView(divider)

            val notesHeader =
                TextView(context).apply {
                    text = "Release Notes"
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, smallPad, 0, smallPad)
                }
            container.addView(notesHeader)

            val notesBody =
                TextView(context).apply {
                    text = info.releaseNotes
                    setTextColor(0xFFCCCCCC.toInt())
                    textSize = 13f
                    movementMethod = ScrollingMovementMethod.getInstance()
                    maxLines = 12
                    isVerticalScrollBarEnabled = true
                }

            val scrollView =
                ScrollView(context).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )
                    addView(notesBody)
                }
            container.addView(scrollView)
        }

        val dialog =
            AlertDialog
                .Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Update Available")
                .setView(container)
                .setPositiveButton("Download") { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))
                    context.startActivity(intent)
                }.setNegativeButton("Later", null)
                .create()

        dialog.show()
    }
}
