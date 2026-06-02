
#include <windows.h>
#include <stdint.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>

#define WN_STEAMAPI_EXPORT __declspec(dllexport)

__attribute__((visibility("default")))
void wnb_dispatch_callback(int iCallback, const void* data, size_t data_size);
__attribute__((visibility("default")))
void wnb_dispatch_call_result(uint64_t hAPICall, int io_failure,
                              const void* data, size_t data_size);

#define WNB_MAX_LISTENERS 64

typedef struct {
    void*    callback;   /* CCallbackBase* */
    int      iCallback;  /* for CCallback path */
    uint64_t hAPICall;   /* for CCallResult path (0 = CCallback) */
} WnbListener;

static WnbListener g_listeners[WNB_MAX_LISTENERS];
static CRITICAL_SECTION g_listeners_cs;
static int g_listeners_inited = 0;

#define WNB_MAX_PENDING  64
#define WNB_MAX_PAYLOAD  256

typedef struct {
    uint64_t hAPICall;             /* 0 = empty slot */
    int      io_failure;
    size_t   payload_size;
    uint8_t  payload[WNB_MAX_PAYLOAD];
} WnbPendingResult;

static WnbPendingResult g_pending[WNB_MAX_PENDING];

static void listeners_init(void) {
    if (g_listeners_inited) return;
    InitializeCriticalSection(&g_listeners_cs);
    g_listeners_inited = 1;
}

static void listeners_add(void* pCallback, int iCallback, uint64_t hAPICall) {
    if (!pCallback) return;
    listeners_init();
    EnterCriticalSection(&g_listeners_cs);
    for (int i = 0; i < WNB_MAX_LISTENERS; ++i) {
        if (g_listeners[i].callback == NULL) {
            g_listeners[i].callback  = pCallback;
            g_listeners[i].iCallback = iCallback;
            g_listeners[i].hAPICall  = hAPICall;
            break;
        }
    }
    LeaveCriticalSection(&g_listeners_cs);
}

static void listeners_remove(void* pCallback) {
    if (!pCallback) return;
    listeners_init();
    EnterCriticalSection(&g_listeners_cs);
    for (int i = 0; i < WNB_MAX_LISTENERS; ++i) {
        if (g_listeners[i].callback == pCallback) {
            g_listeners[i].callback = NULL;
        }
    }
    LeaveCriticalSection(&g_listeners_cs);
}

typedef void (*VoidFn)(void);
typedef void (*RegisterFn)(void* pCallback, int iCallback);
typedef void (*UnregisterFn)(void* pCallback);
typedef void (*RegisterResultFn)(void* pCallback, uint64_t hAPICall);
typedef void (*UnregisterResultFn)(void* pCallback, uint64_t hAPICall);

static HMODULE g_gbe_fork = NULL;
static RegisterFn         g_gbe_register_callback     = NULL;
static UnregisterFn       g_gbe_unregister_callback   = NULL;
static RegisterResultFn   g_gbe_register_call_result   = NULL;
static UnregisterResultFn g_gbe_unregister_call_result = NULL;
static VoidFn             g_gbe_run_callbacks         = NULL;

static void resolve_gbe_fork(void) {
    if (g_gbe_fork != NULL) return;
    g_gbe_fork = LoadLibraryA("original_steam_api64.dll");
    if (g_gbe_fork == NULL) {
        OutputDebugStringA(
            "[wnb-callbacks] LoadLibrary(original_steam_api64.dll) failed");
        return;
    }
    g_gbe_register_callback =
        (RegisterFn)GetProcAddress(g_gbe_fork, "SteamAPI_RegisterCallback");
    g_gbe_unregister_callback =
        (UnregisterFn)GetProcAddress(g_gbe_fork, "SteamAPI_UnregisterCallback");
    g_gbe_register_call_result =
        (RegisterResultFn)GetProcAddress(g_gbe_fork, "SteamAPI_RegisterCallResult");
    g_gbe_unregister_call_result =
        (UnregisterResultFn)GetProcAddress(g_gbe_fork, "SteamAPI_UnregisterCallResult");
    g_gbe_run_callbacks =
        (VoidFn)GetProcAddress(g_gbe_fork, "SteamAPI_RunCallbacks");
}

WN_STEAMAPI_EXPORT void SteamAPI_RegisterCallback(void* pCallback, int iCallback) {
    resolve_gbe_fork();
    if (g_gbe_register_callback != NULL) {
        g_gbe_register_callback(pCallback, iCallback);
    }
    listeners_add(pCallback, iCallback, /*hAPICall=*/0);
}

WN_STEAMAPI_EXPORT void SteamAPI_UnregisterCallback(void* pCallback) {
    resolve_gbe_fork();
    if (g_gbe_unregister_callback != NULL) {
        g_gbe_unregister_callback(pCallback);
    }
    listeners_remove(pCallback);
}

static void drain_pending_for(void* pCallback, uint64_t hAPICall) {
    if (!pCallback || hAPICall == 0) return;
    listeners_init();
    uint8_t  payload[WNB_MAX_PAYLOAD];
    size_t   payload_size = 0;
    int      io_failure = 0;
    int      found = 0;
    EnterCriticalSection(&g_listeners_cs);
    for (int i = 0; i < WNB_MAX_PENDING; ++i) {
        if (g_pending[i].hAPICall == hAPICall) {
            payload_size = g_pending[i].payload_size;
            if (payload_size > WNB_MAX_PAYLOAD) payload_size = WNB_MAX_PAYLOAD;
            memcpy(payload, g_pending[i].payload, payload_size);
            io_failure = g_pending[i].io_failure;
            g_pending[i].hAPICall = 0;
            g_pending[i].payload_size = 0;
            found = 1;
            break;
        }
    }
    LeaveCriticalSection(&g_listeners_cs);
    if (!found) return;
    listeners_remove(pCallback);
    typedef void (*RunResultFn)(void* /*this*/, void* /*pvParam*/, int /*bIOFailure*/);
    void** vtable = *(void***)pCallback;
    RunResultFn run = (RunResultFn)vtable[1];
    run(pCallback, payload, io_failure);
}

WN_STEAMAPI_EXPORT void SteamAPI_RegisterCallResult(void* pCallback, uint64_t hAPICall) {
    resolve_gbe_fork();
    if (g_gbe_register_call_result != NULL) {
        g_gbe_register_call_result(pCallback, hAPICall);
    }
    listeners_add(pCallback, /*iCallback=*/0, hAPICall);
    drain_pending_for(pCallback, hAPICall);
}

WN_STEAMAPI_EXPORT void SteamAPI_UnregisterCallResult(void* pCallback, uint64_t hAPICall) {
    resolve_gbe_fork();
    if (g_gbe_unregister_call_result != NULL) {
        g_gbe_unregister_call_result(pCallback, hAPICall);
    }
    listeners_remove(pCallback);
}

__attribute__((visibility("default")))
void wnb_publish_dispatch_pointers(void) {
    const char* dir = getenv("WN_STATE_DIR");
    if (!dir || !*dir) dir = "/tmp";
    char path[512];
    snprintf(path, sizeof(path), "%s/wnb_ptrs.txt", dir);
    FILE* f = fopen(path, "w");
    if (!f) {
        OutputDebugStringA("[wnb-callbacks] publish_dispatch_pointers: fopen failed");
        return;
    }
    fprintf(f, "dispatch_callback %llu\n",
            (unsigned long long)(uintptr_t)wnb_dispatch_callback);
    fprintf(f, "dispatch_call_result %llu\n",
            (unsigned long long)(uintptr_t)wnb_dispatch_call_result);
    fclose(f);
}

__attribute__((visibility("default")))
void wnb_dispatch_callback(int iCallback, const void* data, size_t data_size) {
    (void)data_size;  /* CCallback fan-out doesn't need late-bind; the
    listeners_init();
    EnterCriticalSection(&g_listeners_cs);
    void* matches[WNB_MAX_LISTENERS];
    int   n = 0;
    for (int i = 0; i < WNB_MAX_LISTENERS; ++i) {
        if (g_listeners[i].callback != NULL
                && g_listeners[i].hAPICall == 0
                && g_listeners[i].iCallback == iCallback) {
            matches[n++] = g_listeners[i].callback;
        }
    }
    LeaveCriticalSection(&g_listeners_cs);
    typedef void (*RunFn)(void* /*this*/, const void* /*pvParam*/);
    for (int i = 0; i < n; ++i) {
        void** vtable = *(void***)matches[i];
        RunFn run = (RunFn)vtable[0];
        run(matches[i], data);
    }
}

__attribute__((visibility("default")))
void wnb_dispatch_call_result(uint64_t hAPICall, int io_failure,
                              const void* data, size_t data_size) {
    listeners_init();
    EnterCriticalSection(&g_listeners_cs);
    void* matches[WNB_MAX_LISTENERS];
    int   n = 0;
    for (int i = 0; i < WNB_MAX_LISTENERS; ++i) {
        if (g_listeners[i].callback != NULL
                && g_listeners[i].hAPICall == hAPICall) {
            matches[n++] = g_listeners[i].callback;
            g_listeners[i].callback = NULL;
        }
    }
    if (n == 0) {
        size_t copy = data_size > WNB_MAX_PAYLOAD ? WNB_MAX_PAYLOAD : data_size;
        for (int i = 0; i < WNB_MAX_PENDING; ++i) {
            if (g_pending[i].hAPICall == 0) {
                g_pending[i].hAPICall     = hAPICall;
                g_pending[i].io_failure   = io_failure;
                g_pending[i].payload_size = copy;
                if (data && copy) memcpy(g_pending[i].payload, data, copy);
                break;
            }
        }
    }
    LeaveCriticalSection(&g_listeners_cs);
    typedef void (*RunResultFn)(void* /*this*/, const void* /*pvParam*/, int /*bIOFailure*/);
    for (int i = 0; i < n; ++i) {
        void** vtable = *(void***)matches[i];
        RunResultFn run = (RunResultFn)vtable[1];
        run(matches[i], data, io_failure);
    }
}

extern void wnb_pump_valve_callbacks(void);

WN_STEAMAPI_EXPORT void SteamAPI_RunCallbacks(void) {
    resolve_gbe_fork();
    if (g_gbe_run_callbacks != NULL) {
        g_gbe_run_callbacks();
    }
    wnb_pump_valve_callbacks();
}
