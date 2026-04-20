package com.winlator.cmod.feature.stores.epic.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.winlator.cmod.feature.stores.common.StoreAuthStatus
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Periodic background refresh for Epic's short-lived (~8h) refresh token.
 *
 * Each successful refresh resets the refresh window back to ~8h, so firing every 4h keeps the
 * session alive indefinitely as long as the device has network reachability. The constraint
 * [NetworkType.CONNECTED] means this worker simply waits when offline; WorkManager picks it
 * back up automatically once connectivity returns. If the user goes offline long enough for
 * the refresh token to die (>8h), the worker will hit a failed refresh on next run and
 * [EpicAuthManager.getStoredCredentials] will clear credentials + emit a SessionExpired event.
 *
 * Why 4 hours: tokens die at +8h, firing at +4h gives a 4h cushion for a failed run. Matches
 * the access-token natural lifetime (~14400s) so every run does meaningful work. Shorter
 * intervals don't meaningfully improve robustness because transient failures are covered by
 * WorkManager's exponential backoff ([Result.retry]).
 */
class EpicTokenRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        return try {
            val status = EpicAuthManager.getAuthStatus(ctx)
            when (status) {
                StoreAuthStatus.LOGGED_OUT, StoreAuthStatus.EXPIRED -> {
                    Timber.tag(TAG).i("No live Epic session (status=$status) — cancelling periodic refresh")
                    cancel(ctx)
                    Result.success()
                }
                StoreAuthStatus.ACTIVE, StoreAuthStatus.REFRESHABLE, StoreAuthStatus.UNKNOWN -> {
                    val refreshResult = EpicAuthManager.getStoredCredentials(ctx)
                    if (refreshResult.isSuccess) {
                        Timber.tag(TAG).d("Epic background refresh succeeded (status was $status)")
                        Result.success()
                    } else {
                        val err = refreshResult.exceptionOrNull()
                        // If getStoredCredentials already cleared creds (e.g. invalid_grant), don't retry.
                        if (!EpicAuthManager.hasStoredCredentials(ctx)) {
                            Timber.tag(TAG).i("Epic creds cleared by refresh failure — stopping worker: ${err?.message}")
                            cancel(ctx)
                            Result.success()
                        } else {
                            Timber.tag(TAG).w(err, "Epic background refresh failed transiently — will retry with backoff")
                            Result.retry()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Unexpected error in EpicTokenRefreshWorker")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "EpicTokenRefreshWorker"
        const val WORK_NAME = "epic_token_refresh"
        private val REFRESH_INTERVAL_HOURS = 4L

        fun schedule(context: Context) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val request =
                PeriodicWorkRequestBuilder<EpicTokenRefreshWorker>(
                    repeatInterval = REFRESH_INTERVAL_HOURS,
                    repeatIntervalTimeUnit = TimeUnit.HOURS,
                ).setConstraints(constraints)
                    .setInitialDelay(REFRESH_INTERVAL_HOURS, TimeUnit.HOURS)
                    .build()

            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    // KEEP: don't reset the schedule if we're already enqueued. The first-fire
                    // delay resets only on REPLACE, which would keep pushing the next run out.
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            Timber.tag(TAG).i("Scheduled periodic Epic token refresh (every ${REFRESH_INTERVAL_HOURS}h)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Timber.tag(TAG).i("Cancelled periodic Epic token refresh")
        }
    }
}
