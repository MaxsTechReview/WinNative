#pragma once
// Clean Steam logoff for the in-Wine WinNative launcher (steam.exe).
// Call wn_launcher_arm_clean_shutdown(...) after logon with a valid (pipe, user)
// to avoid force-close leaving Steam "running" / AlreadyRunning on relaunch.

#ifdef __cplusplus
extern "C" {
#endif

// Route [wn-launcher] markers through the host logger; a second handle to
// C:\wn-launcher.log can lose the markers the Android close path depends on.
// Call before arming with one already-"[wn-launcher] "-prefixed line; NULL falls back to fopen.
void wn_launcher_set_log_sink(void (*log_fn)(const char* line));

// Tell the module which game image (e.g. "Balls.exe") was launched so teardown
// can close it before logoff. Safe any time after arming; NULL/"" disables it.
void wn_launcher_set_game_exe(const char* exeName);

// hSteamClient: LoadLibrary handle for steamclient64.dll.
// pipe/user: live HSteamPipe / HSteamUser from launcher logon.
// logPath: launcher log path, or NULL to disable [wn-launcher] markers; ignored after wn_launcher_set_log_sink().
void wn_launcher_arm_clean_shutdown(void* hSteamClient, int pipe, int user,
                                    const char* logPath);

// Trigger teardown synchronously, e.g. on game exit so normal close also logs
// off cleanly. Idempotent; safe after arming because the watcher/ctrl-handler
// no-op afterwards.
void wn_launcher_clean_shutdown_now(const char* reason);

// Block up to maxMs for an in-flight teardown to finish (no-op if none).
// main() uses this to avoid exiting mid-teardown and cutting the logoff flush.
void wn_launcher_wait_clean_shutdown(int maxMs);

#ifdef __cplusplus
}
#endif
