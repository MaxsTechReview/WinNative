package com.winlator.cmod.runtime.display

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.winlator.cmod.runtime.system.ProcessHelper

/**
 * App-wide teardown service. Started by PluviaApp.onCreate, lives for the
 * whole process lifetime. Its sole purpose is to receive onTaskRemoved when
 * the user swipes the app away from Recents, do synchronous cleanup of
 * native child processes (Wine/box64/wineserver/etc.), and terminate the JVM
 * so the app fully closes and the next launch starts from a clean slate.
 *
 * Deliberately NOT declared with android:stopWithTask="true" — the Android
 * framework silently skips onTaskRemoved when that flag is set, which would
 * defeat the entire mechanism.
 */
class SessionTeardownService : Service() {
    companion object {
        private const val TAG = "SessionTeardown"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created — ready to handle task removal")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand received")
        // START_NOT_STICKY: we don't want Android to restart us after our own
        // Process.killProcess (it would just respawn a zombie service in a
        // process we're trying to kill).
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "Task removed — tearing down app")

        // Release any game-session-owned sockets and SIGKILL Wine children if
        // a game was active. Null-safe if no active session.
        try {
            XServerDisplayActivity.emergencyTeardown("onTaskRemoved")
        } catch (t: Throwable) {
            Log.e(TAG, "emergencyTeardown threw", t)
        }

        // Also kill any lingering Wine/box64/wineserver processes even when no
        // game session was active (stale children from a prior crashed run).
        // Tight timeout: the app is dying anyway, kernel will reap the rest.
        try {
            ProcessHelper.terminateSessionProcessesAndWait(500, true)
            ProcessHelper.drainDeadChildren("SessionTeardown onTaskRemoved")
        } catch (t: Throwable) {
            Log.e(TAG, "Wine process kill threw", t)
        }

        super.onTaskRemoved(rootIntent)
        stopSelf()

        // Kill the JVM after a short delay so Android finishes task
        // bookkeeping and any pending log lines flush. Bypasses onDestroy on
        // other components — that's intended: we want the process gone so
        // bound sockets are released and relaunch is clean.
        Handler(Looper.getMainLooper()).postDelayed({
            Log.w(TAG, "Killing process ${android.os.Process.myPid()}")
            android.os.Process.killProcess(android.os.Process.myPid())
        }, 150)
    }
}
