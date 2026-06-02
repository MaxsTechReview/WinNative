#pragma once

#ifdef __cplusplus
extern "C" {
#endif

void wn_launcher_set_log_sink(void (*log_fn)(const char* line));

void wn_launcher_set_game_exe(const char* exeName);

void wn_launcher_arm_clean_shutdown(void* hSteamClient, int pipe, int user,
                                    const char* logPath);

void wn_launcher_set_cloud_context(void* engine, int hUser, int hPipe, unsigned int appId);

int wn_launcher_cloud_sync(void* engine, int hUser, int hPipe,
                           unsigned int appId, int cmd, int flags, int timeoutMs);

void wn_launcher_clean_shutdown_now(const char* reason);

void wn_launcher_wait_clean_shutdown(int maxMs);

#ifdef __cplusplus
}

#include <string.h>

inline bool wn_game_image_matches(const char* procName, const char* gameExe) {
    if (!procName || !gameExe || !gameExe[0]) return false;
    if (_stricmp(procName, gameExe) == 0) return true;
    static const char* const kSteamlessSuffixes[] = { ".original.exe", ".unpacked.exe" };
    size_t glen = strlen(gameExe);
    for (const char* suf : kSteamlessSuffixes) {
        size_t slen = strlen(suf);
        if (glen > slen && _stricmp(gameExe + (glen - slen), suf) == 0) {
            char base[260];
            size_t blen = glen - slen;
            if (blen >= sizeof(base)) blen = sizeof(base) - 1;
            memcpy(base, gameExe, blen);
            base[blen] = '\0';
            return _stricmp(procName, base) == 0;
        }
    }
    return false;
}
#endif
