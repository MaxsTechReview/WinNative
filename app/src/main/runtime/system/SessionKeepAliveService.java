package com.winlator.cmod.runtime.system;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.winlator.cmod.R;
import com.winlator.cmod.runtime.display.XServerDisplayActivity;
import com.winlator.cmod.runtime.display.environment.XEnvironment;
import com.winlator.cmod.runtime.display.xserver.XServer;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Foreground service that keeps the WinNative process alive while a wine
 * session is in the background or while a component download/install is
 * running. Without it, Android can reap the app process when the screen is
 * locked, taking the wine container (and any in-flight download) with it.
 *
 * Reasons are reference-counted via static helpers. The service stops itself
 * once no reasons remain. On task removal (user swipe-away) it does a
 * defensive wine cleanup and lets the process exit, matching the previous
 * "swipe = close" behaviour.
 */
public class SessionKeepAliveService extends Service {
    private static final String TAG = "SessionKeepAlive";

    private static final String CHANNEL_ID = "winnative_session_keepalive";

    private static final String ACTION_SESSION_START = "com.winlator.cmod.action.SESSION_START";
    private static final String ACTION_SESSION_STOP = "com.winlator.cmod.action.SESSION_STOP";
    private static final String ACTION_SESSION_PAUSE = "com.winlator.cmod.action.SESSION_PAUSE";
    private static final String ACTION_SESSION_RESUME = "com.winlator.cmod.action.SESSION_RESUME";
    private static final String ACTION_DL_START = "com.winlator.cmod.action.SESSION_DL_START";
    private static final String ACTION_DL_STOP = "com.winlator.cmod.action.SESSION_DL_STOP";
    private static final String EXTRA_TAG = "tag";

    private static final AtomicBoolean sessionActive = new AtomicBoolean(false);
    private static final HashSet<String> activeDownloads = new HashSet<>();
    private static final AtomicBoolean serviceRunning = new AtomicBoolean(false);

    private static XEnvironment activeEnvironment;
    private static XServer activeXServer;

    private static boolean isContainerPaused = false;

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private int notificationId;
    private final Handler protectionHandler = new Handler(Looper.getMainLooper());
    private final Runnable protectionRunnable = new Runnable() {
        @Override
        public void run() {
            if (sessionActive.get()) {
                Log.d(TAG, "Running periodic OOM protection for wine processes");
                new Thread(() -> {
                    try {
                        ProcessHelper.protectAllWineProcesses();
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to run OOM protection", e);
                    }
                }, "WineOomProtection").start();
                protectionHandler.postDelayed(this, 2 * 60 * 1000L); // Every 2 minutes
            }
        }
    };

    public static void startSession(Context ctx) {
        if (ctx == null) return;
        sessionActive.set(true);
        sendCommand(ctx, ACTION_SESSION_START, null);
    }

    public static void stopSession(Context ctx) {
        if (ctx == null) return;
        if (sessionActive.compareAndSet(true, false)) {
            sendCommand(ctx, ACTION_SESSION_STOP, null);
        }
    }

    public static XEnvironment getActiveEnvironment() {
        return activeEnvironment;
    }

    public static void setActiveEnvironment(XEnvironment environment) {
        activeEnvironment = environment;
    }

    public static XServer getActiveXServer() {
        return activeXServer;
    }

    public static void setActiveXServer(XServer xServer) {
        activeXServer = xServer;
    }

    public static void onPauseSession(Context ctx) {
        if (ctx == null) return;
        sessionActive.set(true);
        sendCommand(ctx, ACTION_SESSION_PAUSE, null);
    }

    public static void onResumeSession(Context ctx) {
        if (ctx == null) return;
        sendCommand(ctx, ACTION_SESSION_RESUME, null);
    }

    public static void startDownload(Context ctx, String tag) {
        if (ctx == null) return;
        String key = tag == null ? "default" : tag;
        boolean added;
        synchronized (activeDownloads) {
            added = activeDownloads.add(key);
        }
        if (added) {
            sendCommand(ctx, ACTION_DL_START, key);
        }
    }

    public static void stopDownload(Context ctx, String tag) {
        if (ctx == null) return;
        String key = tag == null ? "default" : tag;
        boolean removed;
        synchronized (activeDownloads) {
            removed = activeDownloads.remove(key);
        }
        if (removed) {
            sendCommand(ctx, ACTION_DL_STOP, key);
        }
    }

    public static boolean isSessionActive() {
        return sessionActive.get();
    }

    private static boolean hasReason() {
        if (sessionActive.get()) return true;
        synchronized (activeDownloads) {
            return !activeDownloads.isEmpty();
        }
    }

    private static void sendCommand(Context ctx, String action, @Nullable String tag) {
        Context app = ctx.getApplicationContext();
        Intent intent = new Intent(app, SessionKeepAliveService.class);
        intent.setAction(action);
        if (tag != null) intent.putExtra(EXTRA_TAG, tag);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    (ACTION_SESSION_PAUSE.equals(action) || ACTION_SESSION_START.equals(action) || ACTION_DL_START.equals(action))) {
                app.startForegroundService(intent);
            } else {
                app.startService(intent);
            }
        } catch (Exception e) {
            // If starting the service fails, try starting it as a foreground service as a fallback.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent);
            }
            Log.w(TAG, "Failed to send command " + action, e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        generateNotificationId();

        // Keep the CPU alive to prevent OS from killing the process when the screen is off.
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WinNative:KeepAlive");
//            wakeLock.acquire();
        }

        // Keep the Wi-Fi alive to prevent network interruptions. Useful for games that stream assets from the network or have online features.
        WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
        if (wm != null) {
            int lockType = WifiManager.WIFI_MODE_FULL;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                lockType = WifiManager.WIFI_MODE_FULL_HIGH_PERF;
            }
            wifiLock = wm.createWifiLock(lockType, "WinNative:WifiKeepAlive");
        }

        ensureChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_SESSION_START.equals(action)) {
            sessionActive.set(true);
            isContainerPaused = false;
            protectionHandler.removeCallbacks(protectionRunnable);
            protectionHandler.post(protectionRunnable);
        } else if (ACTION_SESSION_PAUSE.equals(action)) {
            isContainerPaused = true;
        } else if (ACTION_SESSION_RESUME.equals(action)) {
            isContainerPaused = false;
        }
        else if (ACTION_SESSION_STOP.equals(action)) {
            sessionActive.set(false);
            isContainerPaused = false;
            protectionHandler.removeCallbacks(protectionRunnable);
            if (activeEnvironment != null) {
                final XEnvironment env = activeEnvironment;
                activeEnvironment = null;
                activeXServer = null;
                new Thread(() -> {
                    try {
                        env.stopEnvironmentComponents();
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to stop environment components during session stop", e);
                    }
                }, "XServerTeardown").start();
            }
        }

        // Ensure wake lock, wifi lock and OOM adj are correct based on current state
        if (hasReason()) {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            if (wifiLock != null && !wifiLock.isHeld()) wifiLock.acquire();
            ProcessHelper.setOomScoreAdj(android.os.Process.myPid(), -1000);
            new Thread(() -> {
                try {
                    ProcessHelper.protectAllWineProcesses();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to run initial OOM protection", e);
                }
            }, "InitialWineOomProtection").start();
        } else {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
            ProcessHelper.setOomScoreAdj(android.os.Process.myPid(), 0);
        }

        // Always promote to foreground first so Android does not consider
        // the start a violation (and so the notification reflects current
        // reasons), even if the command immediately tells us to stop.
        ensureForeground();
        serviceRunning.set(true);

        if (!hasReason()) {
            Log.d(TAG, "No active reason; stopping keep-alive service");
            stopForegroundCompat();
            stopSelf();
            serviceRunning.set(false);
        }
        return START_STICKY;
    }

    private void ensureForeground() {
        Notification n = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(notificationId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            }
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(notificationId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            }
            else {
                startForeground(notificationId, n);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to startForeground", e);
        }
    }

    private void stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to stopForeground", e);
        }
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "WinNative session keep-alive",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(
                "Keeps WinNative running in the background so a paused game session or "
                        + "an active component download is not interrupted by screen lock.");
        channel.setShowBadge(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        boolean container = sessionActive.get();
        boolean dl;
        synchronized (activeDownloads) {
            dl = !activeDownloads.isEmpty();
        }
        String content;
        if (container && dl) {
            content = "Session paused — downloads continuing in background";
        } else if (container) {
            if (isContainerPaused)
                content = "Container session is paused";
            else
                content = "There is a container session running";
        } else if (dl) {
            content = "Downloading components in the background";
        } else {
            content = "WinNative is running in the background";
        }

        Intent openIntent = new Intent(this, XServerDisplayActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_ab_gear_0011)
                .setContentTitle("WinNative")
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(contentIntent)
                .build();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.i(TAG, "Task removed (user swipe). Tearing down session and exiting process.");

        // Clear reasons so any subsequent re-entry will not keep us alive.
        sessionActive.set(false);
        synchronized (activeDownloads) {
            activeDownloads.clear();
        }

        // Give the activity's own onDestroy → performForcedSessionCleanup a
        // chance to run first; then defensively clean any wine processes that
        // might still be alive, and exit the process so swipe behaves like the
        // pre-existing "swipe-away closes everything" flow.
        new Thread(() -> {
            try {
                Thread.sleep(1500L);
                if (com.winlator.cmod.feature.stores.steam.service.SteamService
                        .Companion.isBionicHandoffActive()) {
                    try {
                        boolean kicked = com.winlator.cmod.feature.stores.steam.service.SteamService
                                .Companion.bionicHandoffReleaseAndKickPlayingSessionBlocking(true, 2500L);
                        Log.i(TAG, "Task removal Steam cleanup: kickedPlayingSession=" + kicked);
                    } catch (Throwable t) {
                        Log.w(TAG, "Task removal Steam cleanup failed", t);
                    }
                }
                ProcessHelper.terminateSessionProcessesAndWait(1500, true);
                ProcessHelper.drainDeadChildren("session keep-alive task removed");
            } catch (Throwable t) {
                Log.w(TAG, "Defensive wine cleanup on task removal failed", t);
            }
            new Handler(Looper.getMainLooper()).post(() -> {
                stopForegroundCompat();
                stopSelf();
                serviceRunning.set(false);
                // Match the previous swipe behaviour: actually exit the process.
                // We do this in a slight delay to ensure stopSelf() has processed.
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    android.os.Process.killProcess(android.os.Process.myPid());
                }, 500L);
            });
        }, "SessionKeepAliveCleanup").start();
    }

    @Override
    public void onDestroy() {
        protectionHandler.removeCallbacks(protectionRunnable);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        serviceRunning.set(false);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void generateNotificationId() {
        // Generate a unique ID based on the package name to avoid conflicts with other forks/flavors.
        String contextKey = getPackageName() + ".winnative.keepAlive";
        notificationId = contextKey.hashCode() & 0x7FFFFFFF; // Avoid negative IDs
    }
}
