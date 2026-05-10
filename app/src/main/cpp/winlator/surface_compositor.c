// JNI wrapper around Android's ASurfaceControl / ASurfaceTransaction NDK API
// (libandroid.so, API 29+). Phase-by-phase scope:
//   * Phase 1 — `nativeIsAvailable` probe; nothing else.
//   * Phase 2.1 — lifecycle: create a child ASurfaceControl bound to the
//     XServerView's SurfaceView, hide it, parent it to the SurfaceView's
//     layer, expose attach/detach/setColor/release.
//   * Phase 2.2+ — buffer push, sync fence, real game frames.
//
// Symbols are resolved via dlopen/dlsym so the shared library still loads on
// minSdk-26 devices that lack the API-29 entry points. Calling any resolved
// pointer on a pre-API-29 device is gated by the Java side checking
// `isAvailable()` first.
//
// Quoting the NDK documentation referenced while writing this:
//   * `ASurfaceControl_createFromWindow` (surface_control.h:50-65) — caller
//     owns the returned ASurfaceControl and must release it.
//   * `ASurfaceTransaction_reparent` (surface_control.h:298-307) — passing
//     a null new_parent removes the surface from the display.
//   * `ASurfaceTransaction_setVisibility` (surface_control.h:323) — HIDE/SHOW.
//   * `ASurfaceTransaction_setZOrder` (surface_control.h:329-339) — relative
//     to siblings; default is 0; behaviour with same z is undefined.
//   * `ASurfaceTransaction_setColor` (surface_control.h:359-370) — sets the
//     background color for a layer that has no buffer; useful as a Phase 2.2
//     proof-of-life and to avoid the "blank initial frame" race when a fresh
//     SurfaceControl is shown before its first real buffer arrives.
#include <android/data_space.h>
#include <android/hardware_buffer.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/rect.h>
#include <dlfcn.h>
#include <jni.h>
#include <pthread.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <unistd.h>

#define LOG_TAG "SurfaceCompositor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Opaque NDK types — we never dereference these, we just pass them around.
struct ASurfaceControl;
struct ASurfaceTransaction;

// Mirror of `enum ASurfaceTransactionVisibility` (surface_control.h:312-315).
// Hard-coded so we don't need to include <android/surface_control.h> (which
// would fail to compile on minSdk-26 toolchains for direct symbol references).
#define DC_VISIBILITY_HIDE ((int8_t)0)
#define DC_VISIBILITY_SHOW ((int8_t)1)

// Mirror of `enum ASurfaceTransactionTransparency` (surface_control.h:447-451).
// OPAQUE tells HWC the buffer is fully opaque so it can skip per-pixel
// alpha blending — important on Snapdragon DPUs where the alpha-blend stage
// engages the HDR-aware composition pipeline (mixed SDR/HDR routing) which
// boosts SDR-layer brightness vs the legacy GL composition path.
#define DC_TRANSPARENCY_TRANSPARENT ((int8_t)0)
#define DC_TRANSPARENCY_TRANSLUCENT ((int8_t)1)
#define DC_TRANSPARENCY_OPAQUE      ((int8_t)2)

// Function-pointer typedefs for every libandroid.so symbol we use. Kept in
// the order they're documented in surface_control.h for easy cross-reference.
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
                                                 int dataspace /* ADataSpace */);
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
// API-31+ preferred geometry. When all four are present we prefer this path
// per surface_control.h:387-391 ("setGeometry deprecated; use setCrop,
// setPosition, setBufferTransform, setScale instead").
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

// Phase 4 — colour / brightness control to neutralise the Snapdragon DPU's
// HDR-aware composition pipeline that boosts SDR layer brightness vs the
// legacy GL composition path.
//
// `setBufferDataSpace` (API 29) — explicit ADATASPACE_SRGB so HWC can't pick
//     ADATASPACE_UNKNOWN from gralloc metadata and route through a path that
//     speculatively decodes-then-re-encodes.
// `setBufferTransparency` (API 29) — OPAQUE skips per-pixel alpha blend,
//     bypassing the mixed-SDR/HDR routing stage on layers known to be
//     fully-opaque (game swap-chain frames are RGBA8888 with alpha=1.0).
// `setExtendedRangeBrightness` (API 34) — pin layer's extended-range ratio
//     to (1.0, 1.0) so SurfaceFlinger's SDR-on-HDR-panel path doesn't apply
//     a midtone boost. Default is (1.0, 1.0) but the AOSP pipeline only
//     skips the boost when the call is explicit.
//
// All three are optional: if dlsym returns null we degrade to the prior
// (visibly brighter) behaviour and log it once at startup so the missing
// symbol is diagnosable from logcat without a re-build.
typedef void (*pfn_ASurfaceTransaction_setBufferDataSpace)(struct ASurfaceTransaction* t,
                                                           struct ASurfaceControl* sc,
                                                           int data_space /* ADataSpace */);
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

// `__typeof__` is the documented-extension spelling that doesn't trip
// `-Wgnu-typeof-extension` under pedantic Clang flags. Equivalent to GCC/C23
// `typeof` in every case we use it.
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
    // Optional API-31+ symbols — null on API 29/30, in which case we fall back
    // to setGeometry. Not part of the availability gate.
    RESOLVE(g_tx_set_position,         "ASurfaceTransaction_setPosition");
    RESOLVE(g_tx_set_scale,            "ASurfaceTransaction_setScale");
    RESOLVE(g_tx_set_crop,             "ASurfaceTransaction_setCrop");
    RESOLVE(g_tx_set_buffer_transform, "ASurfaceTransaction_setBufferTransform");
    // Phase 4 colour / brightness symbols. Optional — failure to resolve
    // means we'll see the visibly-brighter behaviour and log the miss.
    RESOLVE(g_tx_set_buffer_dataspace,         "ASurfaceTransaction_setBufferDataSpace");
    RESOLVE(g_tx_set_buffer_transparency,      "ASurfaceTransaction_setBufferTransparency");
    RESOLVE(g_tx_set_extended_range_brightness, "ASurfaceTransaction_setExtendedRangeBrightness");

    // Phase-1 lifecycle symbols + setBuffer + at least one COMPLETE geometry
    // path are mandatory. The modern path requires all three of
    // setPosition+setScale+setCrop together — accepting setPosition alone
    // would leave us with no scaling primitive and silently render at the
    // wrong size on a hypothetical device that ships only the position
    // symbol. Fall back to setGeometry whenever any of the trio is missing.
    bool modern_geom_complete = (g_tx_set_position != NULL) &&
                                (g_tx_set_scale != NULL) &&
                                (g_tx_set_crop != NULL);
    bool legacy_geom = (g_tx_set_geometry != NULL);
    g_available = (g_create_from_window != NULL) && (g_sc_release != NULL) &&
                  (g_tx_create != NULL) && (g_tx_delete != NULL) &&
                  (g_tx_apply != NULL) && (g_tx_reparent != NULL) &&
                  (g_tx_set_visibility != NULL) && (g_tx_set_zorder != NULL) &&
                  (g_tx_set_color != NULL) && (g_tx_set_buffer != NULL) &&
                  (modern_geom_complete || legacy_geom);
    if (g_available) {
        LOGI("Direct Composition NDK symbols resolved (geom=%s)",
             modern_geom_complete ? "API31+" : "API29 setGeometry");
        // Per-symbol diagnostic for the Phase 4 colour fix surface — printed
        // once on first probe so we can distinguish "fix didn't apply" from
        // "fix applied, vendor pipeline still boosting" in post-deploy
        // logcats without rebuilding.
        LOGI("Direct Composition colour symbols: setBufferDataSpace=%s setBufferTransparency=%s setExtendedRangeBrightness=%s",
             g_tx_set_buffer_dataspace          ? "yes" : "MISSING",
             g_tx_set_buffer_transparency       ? "yes" : "MISSING",
             g_tx_set_extended_range_brightness ? "yes" : "MISSING (API < 34)");
    } else {
        LOGW("Direct Composition NDK symbols missing (API < 29 or stripped libandroid)");
    }
}

static bool ensure_initialised(void) {
    pthread_mutex_lock(&g_init_mutex);
    init_once_locked();
    bool available = g_available;
    pthread_mutex_unlock(&g_init_mutex);
    return available;
}

// ---------------------------------------------------------------------------
// JNI: nativeIsAvailable() — Phase 1 probe, unchanged in Phase 2.
// ---------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_runtime_display_composition_SurfaceCompositor_nativeIsAvailable(
    JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    return ensure_initialised() ? JNI_TRUE : JNI_FALSE;
}

// ---------------------------------------------------------------------------
// JNI: nativeAttachToSurface(Surface) -> jlong (ASurfaceControl*)
//
// Creates a child SurfaceControl bound to the SurfaceView's ANativeWindow.
// Initial state is HIDDEN with z-order 1 (above the SurfaceView's primary
// BufferQueue, which sits at the default z=0). Subsequent transactions
// (Phase 2.2+) flip visibility on and push buffers.
//
// On any failure returns 0 and the Java caller falls back to the GLRenderer
// path. The ANativeWindow is acquired and released within this call — the
// returned ASurfaceControl holds its own reference to the underlying
// SurfaceFlinger layer via the parent layer relationship.
// ---------------------------------------------------------------------------
JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_runtime_display_composition_DirectCompositionLayer_nativeAttachToSurface(
    JNIEnv* env, jclass clazz, jobject surface) {
    (void)clazz;
    if (!ensure_initialised()) {
        LOGW("attachToSurface called but NDK is unavailable");
        return 0;
    }
    if (surface == NULL) {
        LOGE("attachToSurface called with null Surface");
        return 0;
    }

    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (window == NULL) {
        LOGE("ANativeWindow_fromSurface returned null");
        return 0;
    }

    struct ASurfaceControl* sc = g_create_from_window(window, "winnative-direct-composition");
    // ANativeWindow_fromSurface incremented the window's refcount; release our
    // ref now — the SurfaceControl holds its own internal reference to the
    // SurfaceFlinger layer that the window referenced.
    ANativeWindow_release(window);

    if (sc == NULL) {
        LOGE("ASurfaceControl_createFromWindow returned null");
        return 0;
    }

    // Initial transaction: hidden and z=1 (above the SurfaceView's primary BQ
    // which is z=0). Per surface_control.h:323-326 a fresh SurfaceControl
    // starts hidden by default, but applying the explicit setVisibility(HIDE)
    // here makes the contract observable on the SurfaceFlinger side and
    // guarantees we don't get a one-frame flash of an uninitialised layer.
    struct ASurfaceTransaction* tx = g_tx_create();
    if (tx == NULL) {
        LOGE("ASurfaceTransaction_create returned null; releasing SC");
        g_sc_release(sc);
        return 0;
    }
    g_tx_set_visibility(tx, sc, DC_VISIBILITY_HIDE);
    g_tx_set_zorder(tx, sc, 1);
    g_tx_apply(tx);
    g_tx_delete(tx);

    LOGI("Direct Composition layer attached (sc=%p)", (void*)sc);
    return (jlong)(uintptr_t)sc;
}

// ---------------------------------------------------------------------------
// JNI: nativeDetachAndRelease(jlong sc) -> void
//
// Reparents the SurfaceControl to null in a transaction, applies, then
// releases. Per the agent research and Chromium's
// android_surface_control_compat.cc convention, reparent-to-null *must*
// happen before release, otherwise SurfaceFlinger may keep the orphaned
// layer alive briefly past the parent's destruction and produce ghost
// frames on re-attach.
// ---------------------------------------------------------------------------
JNIEXPORT void JNICALL
Java_com_winlator_cmod_runtime_display_composition_DirectCompositionLayer_nativeDetachAndRelease(
    JNIEnv* env, jclass clazz, jlong sc_ptr) {
    (void)env;
    (void)clazz;
    if (sc_ptr == 0) return;
    if (!ensure_initialised()) {
        // Should be impossible — the layer wouldn't exist if init had failed —
        // but be defensive and don't dereference unresolved symbols.
        LOGE("detachAndRelease called but NDK is unavailable; leaking SC=%p",
             (void*)(uintptr_t)sc_ptr);
        return;
    }
    struct ASurfaceControl* sc = (struct ASurfaceControl*)(uintptr_t)sc_ptr;

    struct ASurfaceTransaction* tx = g_tx_create();
    if (tx != NULL) {
        g_tx_reparent(tx, sc, NULL);
        g_tx_apply(tx);
        g_tx_delete(tx);
    } else {
        LOGW("detachAndRelease: tx_create failed; releasing without reparent");
    }
    g_sc_release(sc);
    LOGI("Direct Composition layer released (sc=%p)", (void*)sc);
}

// ---------------------------------------------------------------------------
// JNI: nativeSetColor(jlong sc, float r, float g, float b, float a) -> void
//
// Phase 2.1 proof-of-life. Paints a solid color on the layer and unhides it
// with the same transaction (atomic — avoids the documented "blank initial
// frame" race). Useful as a smoke test that the lifecycle is wired correctly
// before Phase 2.2 plumbs real AHardwareBuffer content.
// ---------------------------------------------------------------------------
JNIEXPORT void JNICALL
Java_com_winlator_cmod_runtime_display_composition_DirectCompositionLayer_nativeSetColor(
    JNIEnv* env, jclass clazz, jlong sc_ptr,
    jfloat r, jfloat g, jfloat b, jfloat a) {
    (void)env;
    (void)clazz;
    if (sc_ptr == 0 || !ensure_initialised()) return;
    struct ASurfaceControl* sc = (struct ASurfaceControl*)(uintptr_t)sc_ptr;

    struct ASurfaceTransaction* tx = g_tx_create();
    if (tx == NULL) {
        LOGE("setColor: tx_create failed");
        return;
    }
    g_tx_set_color(tx, sc, r, g, b, a, ADATASPACE_SRGB);
    g_tx_set_visibility(tx, sc, DC_VISIBILITY_SHOW);
    g_tx_apply(tx);
    g_tx_delete(tx);
}

// ---------------------------------------------------------------------------
// JNI: nativeHide(jlong sc) -> void
//
// Hides the layer (used when falling back to the GLRenderer path on a frame
// where direct-scanout doesn't qualify, see Phase 2.5).
// ---------------------------------------------------------------------------
JNIEXPORT void JNICALL
Java_com_winlator_cmod_runtime_display_composition_DirectCompositionLayer_nativeHide(
    JNIEnv* env, jclass clazz, jlong sc_ptr) {
    (void)env;
    (void)clazz;
    if (sc_ptr == 0 || !ensure_initialised()) return;
    struct ASurfaceControl* sc = (struct ASurfaceControl*)(uintptr_t)sc_ptr;

    struct ASurfaceTransaction* tx = g_tx_create();
    if (tx == NULL) return;
    g_tx_set_visibility(tx, sc, DC_VISIBILITY_HIDE);
    g_tx_apply(tx);
    g_tx_delete(tx);
}

// ---------------------------------------------------------------------------
// JNI: nativePushBuffer(sc, ahb, x, y, w, h, fence_fd) -> jboolean
//
// Phase 2.2: hand an AHardwareBuffer-backed image to the SurfaceControl in
// one transaction. The transaction also positions/sizes the layer in the
// SurfaceView's coordinate space and unhides it (atomic — same transaction
// avoids the documented "blank initial frame" race when transitioning from
// hidden to first-buffer).
//
// Geometry path:
//   * Prefer setPosition + setScale + setCrop + setBufferTransform (API 31+)
//   * Fall back to deprecated setGeometry (API 29-30)
//
// `acquire_fence_fd` semantics per surface_control.h:343-348: framework
// takes ownership and closes it. Pass -1 when no GPU writes are pending
// (e.g. the test buffer that was filled on the CPU before we got here).
// ---------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_runtime_display_composition_DirectCompositionLayer_nativePushBuffer(
    JNIEnv* env, jclass clazz, jlong sc_ptr, jlong ahb_ptr,
    jint dst_x, jint dst_y, jint dst_w, jint dst_h, jint acquire_fence_fd,
    jboolean opaque) {
    (void)env;
    (void)clazz;
    if (sc_ptr == 0 || ahb_ptr == 0) {
        // We promised the framework that we'd close any fence FD we received,
        // even on the failure path — otherwise we leak FDs.
        if (acquire_fence_fd >= 0) close(acquire_fence_fd);
        return JNI_FALSE;
    }
    if (!ensure_initialised()) {
        if (acquire_fence_fd >= 0) close(acquire_fence_fd);
        return JNI_FALSE;
    }
    if (dst_w <= 0 || dst_h <= 0) {
        LOGW("pushBuffer: invalid dst rect %dx%d", dst_w, dst_h);
        if (acquire_fence_fd >= 0) close(acquire_fence_fd);
        return JNI_FALSE;
    }

    struct ASurfaceControl* sc = (struct ASurfaceControl*)(uintptr_t)sc_ptr;
    AHardwareBuffer* ahb = (AHardwareBuffer*)(uintptr_t)ahb_ptr;

    // Source rect = the entire buffer extents — query AHB for its native dims.
    AHardwareBuffer_Desc desc;
    memset(&desc, 0, sizeof(desc));
    AHardwareBuffer_describe(ahb, &desc);
    if (desc.width == 0 || desc.height == 0) {
        LOGW("pushBuffer: AHB has zero extents (%ux%u)", desc.width, desc.height);
        if (acquire_fence_fd >= 0) close(acquire_fence_fd);
        return JNI_FALSE;
    }

    struct ASurfaceTransaction* tx = g_tx_create();
    if (tx == NULL) {
        LOGE("pushBuffer: tx_create failed");
        if (acquire_fence_fd >= 0) close(acquire_fence_fd);
        return JNI_FALSE;
    }

    // setBuffer takes ownership of acquire_fence_fd. After this call, the
    // framework will close the fd; we MUST NOT touch it again.
    g_tx_set_buffer(tx, sc, ahb, acquire_fence_fd);

    // Phase 4 colour / brightness control. Each call is best-effort — if the
    // symbol wasn't resolved (older Android, stripped libandroid) we skip and
    // the layer falls back to whatever default the platform applies. The
    // missing-symbol case was logged once at init.
    //
    // Order within the transaction is irrelevant per surface_control.h:
    // properties are committed atomically on apply().
    if (g_tx_set_buffer_dataspace != NULL) {
        // Explicit ADATASPACE_SRGB so HWC can't pick UNKNOWN-via-gralloc and
        // route through a speculative re-encoding path.
        g_tx_set_buffer_dataspace(tx, sc, ADATASPACE_SRGB);
    }
    if (g_tx_set_buffer_transparency != NULL) {
        // Caller-declared opacity — game frames are typically RGBA8888 with
        // alpha=1.0 throughout, declaring OPAQUE skips alpha blending and
        // bypasses the mixed-SDR/HDR routing stage that brightens layers.
        // Untrusted/translucent surfaces pass opaque=false to keep
        // PREMULTIPLIED behaviour.
        g_tx_set_buffer_transparency(tx, sc,
            opaque ? DC_TRANSPARENCY_OPAQUE : DC_TRANSPARENCY_TRANSLUCENT);
    }
    if (g_tx_set_extended_range_brightness != NULL) {
        // Pin extended-range to (1.0, 1.0) — explicit "no HDR headroom
        // requested." Default value but only assertively skips the
        // SDR-on-HDR-panel midtone boost when stated.
        g_tx_set_extended_range_brightness(tx, sc, 1.0f, 1.0f);
    }

    // Geometry. The modern path lets us crop and scale independently; if
    // unavailable on the device's libandroid, fall back to setGeometry.
    if (g_tx_set_position != NULL && g_tx_set_scale != NULL && g_tx_set_crop != NULL) {
        ARect crop = { 0, 0, (int32_t)desc.width, (int32_t)desc.height };
        g_tx_set_crop(tx, sc, &crop);
        g_tx_set_position(tx, sc, dst_x, dst_y);
        float xs = (float)dst_w / (float)desc.width;
        float ys = (float)dst_h / (float)desc.height;
        g_tx_set_scale(tx, sc, xs, ys);
        if (g_tx_set_buffer_transform != NULL) {
            g_tx_set_buffer_transform(tx, sc, 0); // no transform
        }
    } else if (g_tx_set_geometry != NULL) {
        ARect src = { 0, 0, (int32_t)desc.width, (int32_t)desc.height };
        ARect dst = { dst_x, dst_y, dst_x + dst_w, dst_y + dst_h };
        g_tx_set_geometry(tx, sc, &src, &dst, 0);
    } else {
        LOGE("pushBuffer: no geometry function available — should be impossible past availability gate");
    }

    // Unhide in the same transaction so the very first frame is the buffer
    // we just supplied, not a blank/uninit layer.
    g_tx_set_visibility(tx, sc, DC_VISIBILITY_SHOW);
    g_tx_apply(tx);
    g_tx_delete(tx);
    return JNI_TRUE;
}

// ---------------------------------------------------------------------------
// JNI: nativeAllocateTestBuffer(width, height, argb_color) -> jlong
//
// Phase 2.2 smoke-test helper: allocates an AHardwareBuffer and CPU-fills it
// with a single colour. Used so we can prove the SurfaceControl path is alive
// (a small magenta swatch on top of the X server) before plumbing real Wine
// frames in Phase 2.3.
//
// Format / usage: RGBA_8888 + GPU_SAMPLED_IMAGE + CPU_WRITE_RARELY +
// COMPOSER_OVERLAY. Per surface_control.h:343-345 setBuffer requires
// GPU_SAMPLED_IMAGE; COMPOSER_OVERLAY is a hint to gralloc that the buffer
// may be scanned out by the display controller.
// ---------------------------------------------------------------------------
JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_runtime_display_composition_DirectCompositionLayer_nativeAllocateTestBuffer(
    JNIEnv* env, jclass clazz, jint width, jint height, jint argb_color) {
    (void)env;
    (void)clazz;
    if (width <= 0 || height <= 0) return 0;

    // Try the ideal flag set first: GPU sampling, CPU write (so we can fill
    // the buffer in software), and COMPOSER_OVERLAY (hint to gralloc that
    // this buffer should be eligible for HWC overlay-plane scanout). Some
    // gralloc implementations on recent Adreno devices reject the
    // CPU_WRITE + COMPOSER_OVERLAY combo — in that case fall back to a
    // CPU-only buffer. We lose the overlay hint, but the smoke test still
    // proves the SurfaceControl path is alive.
    AHardwareBuffer_Desc desc;
    memset(&desc, 0, sizeof(desc));
    desc.width = (uint32_t)width;
    desc.height = (uint32_t)height;
    desc.layers = 1;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE
               | AHARDWAREBUFFER_USAGE_CPU_WRITE_RARELY
               | AHARDWAREBUFFER_USAGE_COMPOSER_OVERLAY;

    AHardwareBuffer* ahb = NULL;
    int rc = AHardwareBuffer_allocate(&desc, &ahb);
    if (rc != 0 || ahb == NULL) {
        LOGW("allocateTestBuffer: GPU+CPU+OVERLAY failed (rc=%d), retrying without OVERLAY", rc);
        desc.usage = AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE
                   | AHARDWAREBUFFER_USAGE_CPU_WRITE_RARELY;
        rc = AHardwareBuffer_allocate(&desc, &ahb);
        if (rc != 0 || ahb == NULL) {
            LOGW("allocateTestBuffer: both flag combos failed (rc=%d) for %dx%d",
                 rc, width, height);
            return 0;
        }
    }

    void* mapped = NULL;
    if (AHardwareBuffer_lock(
            ahb, AHARDWAREBUFFER_USAGE_CPU_WRITE_RARELY,
            -1, NULL, &mapped) != 0 || mapped == NULL) {
        LOGW("allocateTestBuffer: AHardwareBuffer_lock failed");
        AHardwareBuffer_release(ahb);
        return 0;
    }

    // After lock we need the actual stride from gralloc — re-describe.
    AHardwareBuffer_Desc realDesc;
    memset(&realDesc, 0, sizeof(realDesc));
    AHardwareBuffer_describe(ahb, &realDesc);

    // Convert ARGB jint to little-endian RGBA u32. AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM
    // is byte-packed: R, G, B, A. Java jint = 0xAARRGGBB. Layout the bytes as R,G,B,A.
    uint8_t a = (uint8_t)((argb_color >> 24) & 0xFF);
    uint8_t r = (uint8_t)((argb_color >> 16) & 0xFF);
    uint8_t g = (uint8_t)((argb_color >>  8) & 0xFF);
    uint8_t b = (uint8_t)((argb_color      ) & 0xFF);

    uint8_t* base = (uint8_t*)mapped;
    for (uint32_t y = 0; y < realDesc.height; ++y) {
        uint8_t* row = base + (size_t)y * (size_t)realDesc.stride * 4u;
        for (uint32_t x = 0; x < realDesc.width; ++x) {
            row[x*4 + 0] = r;
            row[x*4 + 1] = g;
            row[x*4 + 2] = b;
            row[x*4 + 3] = a;
        }
    }

    if (AHardwareBuffer_unlock(ahb, NULL) != 0) {
        LOGW("allocateTestBuffer: AHardwareBuffer_unlock failed");
        // Keep the buffer anyway; SurfaceFlinger doesn't care about lock state.
    }
    return (jlong)(uintptr_t)ahb;
}

// ---------------------------------------------------------------------------
// JNI: nativeReleaseBuffer(ahbPtr) -> void
//
// Drops our reference to a test AHardwareBuffer. SurfaceFlinger may still
// hold a ref if the buffer is the layer's current setBuffer — that's OK,
// AHardwareBuffer is reference-counted and the layer's ref is independent.
// ---------------------------------------------------------------------------
JNIEXPORT void JNICALL
Java_com_winlator_cmod_runtime_display_composition_DirectCompositionLayer_nativeReleaseBuffer(
    JNIEnv* env, jclass clazz, jlong ahb_ptr) {
    (void)env;
    (void)clazz;
    if (ahb_ptr == 0) return;
    AHardwareBuffer_release((AHardwareBuffer*)(uintptr_t)ahb_ptr);
}
