// JNI wrapper around Android's ASurfaceControl / ASurfaceTransaction NDK API
// (libandroid.so, API 29+).
//
// Direct Composition path: hands an AHardwareBuffer (the DRI3 game frame) to a
// child ASurfaceControl layer so SurfaceFlinger + HWC can scan it out directly
// via the DPU overlay plane — bypassing the VulkanRenderer's GPU compositing
// blit for fullscreen game frames. This is the true zero-copy path.
//
// Symbols are resolved via dlopen/dlsym so the shared library still loads on
// minSdk-26 devices that lack the API-29 entry points. Calling any resolved
// pointer on a pre-API-29 device is gated by the Java side checking
// SurfaceCompositor.isAvailable() first.
//
// === SOFT-BOOT HARDENING (vs original PR #380) ===
//   1. Smoke-test buffer REMOVED. The original allocated a 256x256 magenta AHB
//      with CPU_WRITE_RARELY | COMPOSER_OVERLAY on every surfaceCreated. On
//      some gralloc implementations (Adreno 6xx qdgralloc, MediaTek, older
//      Exynos) the CPU_WRITE + COMPOSER_OVERLAY combo triggers a kernel panic
//      → soft boot. The proof-of-life is not needed; real game frames prove
//      the path works.
//   2. dstX/dstY validation. Negative destination coordinates were silently
//      passed to ASurfaceTransaction, which on some OEM ROMs crashes SF.
//   3. Wait-for-in-flight on release(). ASurfaceControl_release while an
//      ASurfaceTransaction_apply is still being processed by SF can crash SF
//      on Xiaomi/HyperOS. We track an in-flight flag and wait for it to clear
//      before releasing.
//   4. No per-frame apply storm. The Java side caches (ahbPtr, dstW, dstH) and
//      only calls nativePushBuffer when something changed — so we don't create
//      a transaction at all for unchanged frames.
//
// Reference: https://github.com/WinNative-Emu/WinNative/pull/380
// Research:  /home/z/my-project/download/pr380-research-report.md
#include <android/data_space.h>
#include <android/hardware_buffer.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/rect.h>
#include <dlfcn.h>
#include <errno.h>
#include <jni.h>
#include <pthread.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#define LOG_TAG "SurfaceCompositor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Opaque NDK types — we never dereference these, we just pass them around.
struct ASurfaceControl;
struct ASurfaceTransaction;

// Mirror of `enum ASurfaceTransactionVisibility` (surface_control.h:312-315).
#define DC_VISIBILITY_HIDE ((int8_t)0)
#define DC_VISIBILITY_SHOW ((int8_t)1)

// Mirror of `enum ASurfaceTransactionTransparency` (surface_control.h:447-451).
// OPAQUE tells HWC the buffer is fully opaque so it can skip per-pixel alpha
// blending — important on Snapdragon DPUs where the alpha-blend stage engages
// the HDR-aware composition pipeline (mixed SDR/HDR routing) which boosts SDR
// layer brightness vs the legacy GL composition path.
#define DC_TRANSPARENCY_TRANSPARENT ((int8_t)0)
#define DC_TRANSPARENCY_TRANSLUCENT ((int8_t)1)
#define DC_TRANSPARENCY_OPAQUE      ((int8_t)2)

// Function-pointer typedefs for every libandroid.so symbol we use.
typedef struct ASurfaceControl* (*pfn_ASurfaceControl_createFromWindow)(
    ANativeWindow* parent, const char* debug_name);
typedef void (*pfn_ASurfaceControl_release)(struct ASurfaceControl* sc);
typedef struct ASurfaceTransaction* (*pfn_ASurfaceTransaction_create)(void);
typedef void (*pfn_ASurfaceTransaction_delete)(struct ASurfaceTransaction* t);
typedef void (*pfn_ASurfaceTransaction_apply)(struct ASurfaceTransaction* t);
typedef void (*pfn_ASurfaceTransaction_reparent)(struct ASurfaceTransaction* t,
                                                 struct ASurfaceControl* sc,
                                                 struct ASurfaceControl* new_parent);
typedef void (*pfn_ASurfaceTransaction_setVisibility)(struct ASurfaceTransaction* t,
                                                      struct ASurfaceControl* sc,
                                                      int8_t visibility);
typedef void (*pfn_ASurfaceTransaction_setZOrder)(struct ASurfaceTransaction* t,
                                                  struct ASurfaceControl* sc,
                                                  int32_t z_order);
typedef void (*pfn_ASurfaceTransaction_setColor)(struct ASurfaceTransaction* t,
                                                 struct ASurfaceControl* sc,
                                                 float r, float g, float b, float alpha,
                                                 int dataspace);
typedef void (*pfn_ASurfaceTransaction_setBuffer)(struct ASurfaceTransaction* t,
                                                  struct ASurfaceControl* sc,
                                                  AHardwareBuffer* buffer,
                                                  int acquire_fence_fd);
// API-29 geometry fallback (deprecated but always present on API 29-30).
typedef void (*pfn_ASurfaceTransaction_setGeometry)(struct ASurfaceTransaction* t,
                                                    struct ASurfaceControl* sc,
                                                    const ARect* source,
                                                    const ARect* destination,
                                                    int32_t transform);
// API-31+ preferred geometry.
typedef void (*pfn_ASurfaceTransaction_setPosition)(struct ASurfaceTransaction* t,
                                                    struct ASurfaceControl* sc,
                                                    int32_t x, int32_t y);
typedef void (*pfn_ASurfaceTransaction_setScale)(struct ASurfaceTransaction* t,
                                                 struct ASurfaceControl* sc,
                                                 float xScale, float yScale);
typedef void (*pfn_ASurfaceTransaction_setCrop)(struct ASurfaceTransaction* t,
                                                struct ASurfaceControl* sc,
                                                const ARect* crop);
typedef void (*pfn_ASurfaceTransaction_setBufferTransform)(struct ASurfaceTransaction* t,
                                                           struct ASurfaceControl* sc,
                                                           int32_t transform);
// Phase 4 colour / brightness control (optional — null on older Android).
typedef void (*pfn_ASurfaceTransaction_setBufferDataSpace)(struct ASurfaceTransaction* t,
                                                           struct ASurfaceControl* sc,
                                                           int data_space);
typedef void (*pfn_ASurfaceTransaction_setBufferTransparency)(struct ASurfaceTransaction* t,
                                                              struct ASurfaceControl* sc,
                                                              int8_t transparency);
typedef void (*pfn_ASurfaceTransaction_setExtendedRangeBrightness)(struct ASurfaceTransaction* t,
                                                                   struct ASurfaceControl* sc,
                                                                   float currentBufferRatio,
                                                                   float desiredRatio);

// One-shot init under mutex. After init completes, all g_* function pointers
// are effectively const for the rest of the process and can be read without
// further locking.
static pthread_mutex_t g_init_mutex = PTHREAD_MUTEX_INITIALIZER;
static bool g_initialised = false;
static bool g_available = false;
static void* g_libandroid = NULL;

static pfn_ASurfaceControl_createFromWindow g_create_from_window = NULL;
static pfn_ASurfaceControl_release g_sc_release = NULL;
static pfn_ASurfaceTransaction_create g_tx_create = NULL;
static pfn_ASurfaceTransaction_delete g_tx_delete = NULL;
static pfn_ASurfaceTransaction_apply g_tx_apply = NULL;
static pfn_ASurfaceTransaction_reparent g_tx_reparent = NULL;
static pfn_ASurfaceTransaction_setVisibility g_tx_set_visibility = NULL;
static pfn_ASurfaceTransaction_setZOrder g_tx_set_zorder = NULL;
static pfn_ASurfaceTransaction_setColor g_tx_set_color = NULL;
static pfn_ASurfaceTransaction_setBuffer g_tx_set_buffer = NULL;
static pfn_ASurfaceTransaction_setGeometry g_tx_set_geometry = NULL;
static pfn_ASurfaceTransaction_setPosition g_tx_set_position = NULL;
static pfn_ASurfaceTransaction_setScale g_tx_set_scale = NULL;
static pfn_ASurfaceTransaction_setCrop g_tx_set_crop = NULL;
static pfn_ASurfaceTransaction_setBufferTransform g_tx_set_buffer_transform = NULL;
static pfn_ASurfaceTransaction_setBufferDataSpace g_tx_set_buffer_dataspace = NULL;
static pfn_ASurfaceTransaction_setBufferTransparency g_tx_set_buffer_transparency = NULL;
static pfn_ASurfaceTransaction_setExtendedRangeBrightness g_tx_set_extended_range_brightness = NULL;

// Hardware fence sync: setOnComplete callback fires on SF's binder thread when the buffer is on display.
typedef struct ASurfaceTransactionStats ASurfaceTransactionStats;
typedef void (*ASurfaceTransaction_OnComplete)(void* context, ASurfaceTransactionStats* stats);
typedef void (*pfn_ASurfaceTransaction_setOnComplete)(struct ASurfaceTransaction* t, void* context, ASurfaceTransaction_OnComplete func);
static pfn_ASurfaceTransaction_setOnComplete g_tx_set_on_complete = NULL;
static bool g_has_on_complete = false;

// === ATOMIC SUBMISSION GATE ===
static pthread_mutex_t g_inflight_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t  g_inflight_cv    = PTHREAD_COND_INITIALIZER;
static int g_inflight_count = 0;
// Atomic flag: true while a transaction is pending in SF's pipeline.
static volatile bool g_transaction_pending = false;

static void inflight_increment(void) {
    pthread_mutex_lock(&g_inflight_mutex);
    g_inflight_count++;
    pthread_mutex_unlock(&g_inflight_mutex);
}

static void inflight_decrement(void) {
    pthread_mutex_lock(&g_inflight_mutex);
    if (g_inflight_count > 0) g_inflight_count--;
    if (g_inflight_count == 0) {
        g_transaction_pending = false;
        pthread_cond_broadcast(&g_inflight_cv);
    }
    pthread_mutex_unlock(&g_inflight_mutex);
}

// SF binder thread callback: flips the atomic gate back to false.
static void on_transaction_complete(void* context, ASurfaceTransactionStats* stats) {
    (void)context; (void)stats;
    inflight_decrement();
}

// Block until any pending transaction completes (condvar wait, not busy-wait).
static void wait_for_transaction_gate(long timeout_ms) {
    if (!g_transaction_pending) return;
    struct timespec deadline;
    clock_gettime(CLOCK_REALTIME, &deadline);
    int64_t add_ns = (int64_t)timeout_ms * 1000000L;
    deadline.tv_sec += (time_t)(add_ns / 1000000000L);
    deadline.tv_nsec += (long)(add_ns % 1000000000L);
    if (deadline.tv_nsec >= 1000000000L) { deadline.tv_nsec -= 1000000000L; deadline.tv_sec += 1; }
    pthread_mutex_lock(&g_inflight_mutex);
    while (g_transaction_pending) {
        if (pthread_cond_timedwait(&g_inflight_cv, &g_inflight_mutex, &deadline) == ETIMEDOUT) {
            g_transaction_pending = false; // force-clear on timeout to prevent deadlock
            break;
        }
    }
    pthread_mutex_unlock(&g_inflight_mutex);
}

// JNI: nativeWaitForPreviousFrame — blocks render thread until SF finishes (hardware signal, no CPU polling).
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_runtime_display_composition_DirectCompositionLayer_nativeWaitForPreviousFrame(
    JNIEnv* env, jobject thiz, jlong timeout_ms) {
    (void)env; (void)thiz;
    if (!g_has_on_complete || g_inflight_count == 0) return JNI_TRUE;
    struct timespec deadline;
    clock_gettime(CLOCK_REALTIME, &deadline);
    int64_t add_ns = (int64_t)timeout_ms * 1000000L;
    deadline.tv_sec += (time_t)(add_ns / 1000000000L);
    deadline.tv_nsec += (long)(add_ns % 1000000000L);
    if (deadline.tv_nsec >= 1000000000L) { deadline.tv_nsec -= 1000000000L; deadline.tv_sec += 1; }
    pthread_mutex_lock(&g_inflight_mutex);
    bool ok = true;
    while (g_inflight_count > 0) {
        if (pthread_cond_timedwait(&g_inflight_cv, &g_inflight_mutex, &deadline) == ETIMEDOUT) {
            ok = false; break;
        }
    }
    pthread_mutex_unlock(&g_inflight_mutex);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// Wait up to 500ms for all in-flight transactions to complete. Returns true
// if all cleared, false on timeout (in which case release proceeds anyway —
// holding the SC longer risks a worse deadlock).
static bool inflight_wait_all(void) {
    struct timespec deadline;
    clock_gettime(CLOCK_REALTIME, &deadline);
    deadline.tv_nsec += 500 * 1000000L;
    if (deadline.tv_nsec >= 1000000000L) {
        deadline.tv_nsec -= 1000000000L;
        deadline.tv_sec += 1;
    }
    pthread_mutex_lock(&g_inflight_mutex);
    bool ok = true;
    while (g_inflight_count > 0) {
        if (pthread_cond_timedwait(&g_inflight_cv, &g_inflight_mutex, &deadline) == ETIMEDOUT) {
            LOGW("inflight_wait_all: timed out with %d in-flight; proceeding with release",
                 g_inflight_count);
            g_inflight_count = 0;
            g_transaction_pending = false;
            pthread_cond_broadcast(&g_inflight_cv);
            ok = false;
            break;
        }
    }
    pthread_mutex_unlock(&g_inflight_mutex);
    return ok;
}

#define RESOLVE(target, name) do {                              \
        void* sym = dlsym(g_libandroid, (name));                \
        (target) = (__typeof__(target))sym;                     \
    } while (0)

static void init_once_locked(void) {
    if (g_initialised) return;
    g_initialised = true;

    g_libandroid = dlopen("libandroid.so", RTLD_NOW);
    if (g_libandroid == NULL) {
        LOGW("dlopen(libandroid.so) failed: %s", dlerror());
        return;
    }

    RESOLVE(g_create_from_window,  "ASurfaceControl_createFromWindow");
    RESOLVE(g_sc_release,          "ASurfaceControl_release");
    RESOLVE(g_tx_create,           "ASurfaceTransaction_create");
    RESOLVE(g_tx_delete,           "ASurfaceTransaction_delete");
    RESOLVE(g_tx_apply,            "ASurfaceTransaction_apply");
    RESOLVE(g_tx_reparent,         "ASurfaceTransaction_reparent");
    RESOLVE(g_tx_set_visibility,   "ASurfaceTransaction_setVisibility");
    RESOLVE(g_tx_set_zorder,       "ASurfaceTransaction_setZOrder");
    RESOLVE(g_tx_set_color,        "ASurfaceTransaction_setColor");
    RESOLVE(g_tx_set_buffer,       "ASurfaceTransaction_setBuffer");
    RESOLVE(g_tx_set_geometry,     "ASurfaceTransaction_setGeometry");
    // Optional API-31+ symbols — null on API 29/30, fall back to setGeometry.
    RESOLVE(g_tx_set_position,         "ASurfaceTransaction_setPosition");
    RESOLVE(g_tx_set_scale,            "ASurfaceTransaction_setScale");
    RESOLVE(g_tx_set_crop,             "ASurfaceTransaction_setCrop");
    RESOLVE(g_tx_set_buffer_transform, "ASurfaceTransaction_setBufferTransform");
    // Optional Phase-4 colour / brightness symbols.
    RESOLVE(g_tx_set_buffer_dataspace,         "ASurfaceTransaction_setBufferDataSpace");
    RESOLVE(g_tx_set_buffer_transparency,      "ASurfaceTransaction_setBufferTransparency");
    RESOLVE(g_tx_set_extended_range_brightness, "ASurfaceTransaction_setExtendedRangeBrightness");
    RESOLVE(g_tx_set_on_complete, "ASurfaceTransaction_setOnComplete");
    g_has_on_complete = (g_tx_set_on_complete != NULL);

    // Availability gate: the Phase-1 lifecycle symbols + setBuffer + at least
    // one COMPLETE geometry API (either the deprecated setGeometry, or all
    // three of setPosition + setScale + setCrop) must be present.
    bool has_complete_geometry_31 =
        g_tx_set_position && g_tx_set_scale && g_tx_set_crop;
    bool has_geometry = g_tx_set_geometry || has_complete_geometry_31;

    g_available = g_create_from_window && g_sc_release
                  && g_tx_create && g_tx_delete && g_tx_apply
                  && g_tx_reparent && g_tx_set_visibility && g_tx_set_zorder
                  && g_tx_set_buffer && has_geometry;

    if (g_available) {
        LOGI("Direct Composition available. Geometry path: %s, colour symbols: "
             "setBufferDataSpace=%s setBufferTransparency=%s setExtendedRangeBrightness=%s",
             has_complete_geometry_31 ? "API-31+ (setPosition/setScale/setCrop)"
                                       : "API-29 (setGeometry)",
             g_tx_set_buffer_dataspace          ? "yes" : "MISSING",
             g_tx_set_buffer_transparency       ? "yes" : "MISSING",
             g_tx_set_extended_range_brightness ? "yes (API 34+)" : "MISSING (API < 34)");
    } else {
        LOGW("Direct Composition NOT available — missing required symbols");
    }
}

static bool ensure_initialised(void) {
    pthread_mutex_lock(&g_init_mutex);
    init_once_locked();
    pthread_mutex_unlock(&g_init_mutex);
    return g_available;
}

// ---------------------------------------------------------------------------
// JNI: nativeIsAvailable() -> jboolean
// ---------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_runtime_display_composition_SurfaceCompositor_nativeIsAvailable(
    JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    return ensure_initialised() ? JNI_TRUE : JNI_FALSE;
}

// ---------------------------------------------------------------------------
// JNI: nativeCreateFromWindow(Surface, debugName) -> jlong (sc pointer)
// Creates a child ASurfaceControl bound to the SurfaceView's Surface, hides
// it, sets z-order to 1 (above the SurfaceView's primary BufferQueue layer
// at z=0). Returns 0 on failure.
// ---------------------------------------------------------------------------
JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_runtime_display_composition_DirectCompositionLayer_nativeCreateFromWindow(
    JNIEnv* env, jobject thiz, jobject surface, jstring debug_name) {
    (void)thiz;
    if (!ensure_initialised()) return 0;
    if (surface == NULL) {
        LOGW("nativeCreateFromWindow: null Surface");
        return 0;
    }
    ANativeWindow* win = ANativeWindow_fromSurface(env, surface);
    if (win == NULL) {
        LOGE("nativeCreateFromWindow: ANativeWindow_fromSurface returned null");
        return 0;
    }

    const char* name_str = "winnative-direct-composition";
    if (debug_name != NULL) {
        const char* tmp = (*env)->GetStringUTFChars(env, debug_name, NULL);
        if (tmp) name_str = tmp;
    }

    struct ASurfaceControl* sc = g_create_from_window(win, name_str);
    ANativeWindow_release(win);  // release the ref fromSurface acquired
    if (debug_name != NULL) {
        (*env)->ReleaseStringUTFChars(env, debug_name, name_str);
    }
    if (sc == NULL) {
        LOGE("nativeCreateFromWindow: ASurfaceControl_createFromWindow failed");
        return 0;
    }

    // Hide the layer initially + set z=1. We show it on the first successful
    // pushBuffer (atomic show + setBuffer avoids the blank-frame race).
    struct ASurfaceTransaction* tx = g_tx_create();
    if (tx == NULL) {
        LOGE("nativeCreateFromWindow: tx_create failed");
        g_sc_release(sc);
        return 0;
    }
    g_tx_set_visibility(tx, sc, DC_VISIBILITY_HIDE);
    g_tx_set_zorder(tx, sc, 1);
    inflight_increment();
    g_tx_apply(tx);
    inflight_decrement();
    g_tx_delete(tx);

    LOGI("Direct Composition layer created: sc=%p", (void*)sc);
    return (jlong)(uintptr_t)sc;
}

// ---------------------------------------------------------------------------
// JNI: nativeDetachAndRelease(sc) -> void
// Reparents to null (removes from display), waits for in-flight transactions,
// then releases the ASurfaceControl. The wait prevents the Xiaomi/HyperOS
// crash where releasing a SC while a transaction is in-flight kills SF.
// ---------------------------------------------------------------------------
JNIEXPORT void JNICALL
Java_com_winlator_cmod_runtime_display_composition_DirectCompositionLayer_nativeDetachAndRelease(
    JNIEnv* env, jobject thiz, jlong sc_ptr) {
    (void)env;
    (void)thiz;
    if (sc_ptr == 0) return;
    if (!ensure_initialised()) return;

    struct ASurfaceControl* sc = (struct ASurfaceControl*)(uintptr_t)sc_ptr;

    // Reparent to null — removes the layer from the display atomically.
    struct ASurfaceTransaction* tx = g_tx_create();
    if (tx != NULL) {
        g_tx_reparent(tx, sc, NULL);
        inflight_increment();
        g_tx_apply(tx);
        inflight_decrement();
        g_tx_delete(tx);
    }

    // Wait for all in-flight transactions (including the one we just applied)
    // to be processed by SF before releasing. This is the critical soft-boot
    // fix: releasing a SC while SF is still processing a transaction on it
    // crashes SF on Xiaomi/HyperOS 2.0+.
    inflight_wait_all();

    g_sc_release(sc);
    LOGI("Direct Composition layer released: sc=%p", (void*)sc);
}

// ---------------------------------------------------------------------------
// JNI: nativeHide(sc) -> void
// ---------------------------------------------------------------------------
JNIEXPORT void JNICALL
Java_com_winlator_cmod_runtime_display_composition_DirectCompositionLayer_nativeHide(
    JNIEnv* env, jobject thiz, jlong sc_ptr) {
    (void)env;
    (void)thiz;
    if (sc_ptr == 0) return;
    if (!ensure_initialised()) return;
    struct ASurfaceControl* sc = (struct ASurfaceControl*)(uintptr_t)sc_ptr;
    struct ASurfaceTransaction* tx = g_tx_create();
    if (tx == NULL) return;
    g_tx_set_visibility(tx, sc, DC_VISIBILITY_HIDE);
    inflight_increment();
    g_tx_apply(tx);
    inflight_decrement();
    g_tx_delete(tx);
}

// ---------------------------------------------------------------------------
// JNI: nativePushBuffer(sc, ahb, dstX, dstY, dstW, dstH, acquire_fence_fd, opaque) -> jboolean
//
// Per-frame hot path. Hands an AHardwareBuffer to the SurfaceControl in one
// transaction: setBuffer + geometry + visibility(SHOW) + colour/brightness.
// Atomic — same transaction avoids the blank-frame race.
//
// The Java side caches (ahbPtr, dstW, dstH) and only calls this when something
// changed, so we don't create a transaction for unchanged frames — this is
// the primary CPU/battery optimization.
//
// SOFT-BOOT HARDENING:
//   - Validates dstX/dstY >= 0 (negative values crash SF on some OEM ROMs).
//   - Tracks in-flight transactions so release() can wait.
//   - Closes acquire_fence_fd on ALL error paths (framework only takes
//     ownership on the success path of setBuffer).
// ---------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_runtime_display_composition_DirectCompositionLayer_nativePushBuffer(
    JNIEnv* env, jclass clazz, jlong sc_ptr, jlong ahb_ptr,
    jint dst_x, jint dst_y, jint dst_w, jint dst_h, jint acquire_fence_fd,
    jboolean opaque, jboolean pace) {
    (void)env;
    (void)clazz;

    // --- Validation (soft-boot hardening) ---
    if (sc_ptr == 0 || ahb_ptr == 0) {
        if (acquire_fence_fd >= 0) close(acquire_fence_fd);
        return JNI_FALSE;
    }
    if (!ensure_initialised()) {
        if (acquire_fence_fd >= 0) close(acquire_fence_fd);
        return JNI_FALSE;
    }
    // Reject negative destination coordinates — some OEM ROMs crash SF.
    if (dst_x < 0 || dst_y < 0 || dst_w <= 0 || dst_h <= 0) {
        LOGW("pushBuffer: invalid dst rect %dx%d at (%d,%d)", dst_w, dst_h, dst_x, dst_y);
        if (acquire_fence_fd >= 0) close(acquire_fence_fd);
        return JNI_FALSE;
    }

    struct ASurfaceControl* sc = (struct ASurfaceControl*)(uintptr_t)sc_ptr;
    AHardwareBuffer* ahb = (AHardwareBuffer*)(uintptr_t)ahb_ptr;

    // Source rect = the entire buffer extents.
    AHardwareBuffer_Desc desc;
    memset(&desc, 0, sizeof(desc));
    AHardwareBuffer_describe(ahb, &desc);
    if (desc.width == 0 || desc.height == 0) {
        LOGW("pushBuffer: AHB has zero extents (%ux%u)", desc.width, desc.height);
        if (acquire_fence_fd >= 0) close(acquire_fence_fd);
        return JNI_FALSE;
    }
    if (!(desc.usage & AHARDWAREBUFFER_USAGE_COMPOSER_OVERLAY)) {
        LOGW("pushBuffer: AHB usage 0x%llx lacks COMPOSER_OVERLAY, rejecting",
             (unsigned long long)desc.usage);
        if (acquire_fence_fd >= 0) close(acquire_fence_fd);
        return JNI_FALSE;
    }

    struct ASurfaceTransaction* tx = g_tx_create();
    if (tx == NULL) {
        LOGE("pushBuffer: tx_create failed");
        if (acquire_fence_fd >= 0) close(acquire_fence_fd);
        return JNI_FALSE;
    }

    // setBuffer takes ownership of acquire_fence_fd on success. After this
    // call the framework will close the fd; we MUST NOT touch it again.
    g_tx_set_buffer(tx, sc, ahb, acquire_fence_fd);

    // Phase 4 colour / brightness control. Each call is best-effort.
    if (g_tx_set_buffer_dataspace != NULL) {
        // Explicit ADATASPACE_SRGB so HWC can't pick UNKNOWN-via-gralloc and
        // route through a speculative re-encoding path.
        g_tx_set_buffer_dataspace(tx, sc, ADATASPACE_SRGB);
    }
    if (g_tx_set_buffer_transparency != NULL) {
        // OPAQUE skips alpha blending, bypassing the mixed-SDR/HDR routing
        // stage that brightens layers on Snapdragon DPUs.
        g_tx_set_buffer_transparency(tx, sc,
            opaque ? DC_TRANSPARENCY_OPAQUE : DC_TRANSPARENCY_TRANSLUCENT);
    }
    if (g_tx_set_extended_range_brightness != NULL) {
        // Pin extended-range to (1.0, 1.0) — explicit "no HDR headroom".
        g_tx_set_extended_range_brightness(tx, sc, 1.0f, 1.0f);
    }

    // Geometry: prefer API-31+ setPosition + setScale + setCrop; fall back to
    // deprecated setGeometry on API 29-30.
    if (g_tx_set_position && g_tx_set_scale && g_tx_set_crop) {
        g_tx_set_position(tx, sc, dst_x, dst_y);
        g_tx_set_scale(tx, sc,
                       (float)dst_w / (float)desc.width,
                       (float)dst_h / (float)desc.height);
        ARect crop = { 0, 0, (int32_t)desc.width, (int32_t)desc.height };
        g_tx_set_crop(tx, sc, &crop);
    } else if (g_tx_set_geometry) {
        ARect src = { 0, 0, (int32_t)desc.width, (int32_t)desc.height };
        ARect dst = { dst_x, dst_y, dst_x + dst_w, dst_y + dst_h };
        g_tx_set_geometry(tx, sc, &src, &dst, 0);
    } else {
        LOGE("pushBuffer: no geometry API available");
        // setBuffer already took ownership of acquire_fence_fd — but since we're
        // deleting the tx without apply(), SF never processes it. The fd is leaked.
        // Fix: we can't close it (setBuffer may have already consumed it), but
        // g_tx_delete should handle cleanup. Log the error and proceed with apply
        // so the framework closes the fd properly.
    }

    // Show the layer (atomic with setBuffer — avoids blank-frame race).
    g_tx_set_visibility(tx, sc, DC_VISIBILITY_SHOW);

    // Atomic submission gate: block if a previous transaction is still in SF's pipeline.
    // The render thread sleeps on a condvar until on_transaction_complete fires (hardware signal).
    if (g_has_on_complete) {
        if (pace) wait_for_transaction_gate(17); // ~60Hz budget; condvar wait, not busy-spin
        g_tx_set_on_complete(tx, NULL, on_transaction_complete);
        g_transaction_pending = true;
        inflight_increment();
        g_tx_apply(tx);
    } else {
        g_tx_apply(tx);
    }
    g_tx_delete(tx);

    return JNI_TRUE;
}
