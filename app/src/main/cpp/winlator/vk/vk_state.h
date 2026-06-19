// Master state header for the Vulkan compositor.

#pragma once

#include <android/hardware_buffer.h>
#include <android/log.h>
#include <android/native_window.h>
#include <pthread.h>
#include <semaphore.h>
#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>

// All vk* calls route through the dispatch table (do not include <vulkan/vulkan.h> directly).
#include "vk_dispatch.h"

#define VK_LOG_TAG "VkRenderer"
#define VK_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  VK_LOG_TAG, __VA_ARGS__)
#define VK_LOGW(...) __android_log_print(ANDROID_LOG_WARN,  VK_LOG_TAG, __VA_ARGS__)
#define VK_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, VK_LOG_TAG, __VA_ARGS__)

#define VK_FRAMES_IN_FLIGHT 3
#define VK_MAX_SWAPCHAIN_IMAGES 8
#define FG_JOB_RING 6u

// A queued FG present job, snapshotted at enqueue and executed by the worker pthread.
typedef struct FgJob {
    uint8_t  mode;          // 1 = INTERP, 2 = PRESENT_LAST
    uint8_t  deep;          // bidirectional warp (model-1)
    float    phase;
    uint32_t curr_idx;      // history slots, snapshotted at enqueue
    uint32_t prev_idx;
    uint64_t deadline_ns;   // free content-rate present target, ns CLOCK_MONOTONIC (worker paces to this)
    uint32_t seq;           // fg_promote_seq snapshot — worker drops the job if the slot was reused
} FgJob;
#define VK_MAX_EFFECTS 8
#define VK_MAX_RENDERABLE_WINDOWS 64
// Number of in-flight upload slots.
#define VK_STAGING_POOL_SIZE 8

#define VK_CHECK(expr) do { \
    VkResult _r = (expr); \
    if (_r != VK_SUCCESS) { \
        VK_LOGE("%s:%d: %s -> %d", __FILE__, __LINE__, #expr, _r); \
    } \
} while (0)

// ============================================================
// Texture (drives both regular CPU-uploaded images and AHB imports)
// ============================================================

// A CPU-uploaded texture's slice of an image sub-allocator block (defined below).
struct VkMemBlock;
typedef struct VkSuballoc {
    struct VkMemBlock* block;        // owning block (NULL => not sub-allocated)
    VkDeviceMemory     memory;       // == block->memory, cached for vkBindImageMemory
    VkDeviceSize       bind_offset;  // aligned offset the image is bound at
    VkDeviceSize       span_offset;  // reserved span start/length, returned on free
    VkDeviceSize       span_size;
} VkSuballoc;

typedef struct VkTexture {
    VkImage image;
    VkImageView view;
    VkDeviceMemory memory;
    VkSampler sampler;                  // owned per-texture (simple); could be cached
    VkSamplerYcbcrConversion ycbcr;     // VK_NULL_HANDLE if unused
    VkDescriptorSet descriptor_set;     // one per texture, lives until destruction

    uint32_t width;
    uint32_t height;
    VkFormat format;
    VkImageLayout layout;

    // Lifetime: when set, owned by texture and freed on destroy.
    AHardwareBuffer* ahb;

    // Track readiness: true once image+view+sampler are valid.
    bool ready;
    // True if this texture should never be uploaded to (e.g. AHB scanout).
    bool external;
    // Prevent duplicate deferred frees if Java schedules destruction more than once.
    bool destroy_scheduled;

    // suballocated: backing comes from the shared sub-allocator (suballoc) and `memory` is
    // unused. AHB imports and the dedicated fallback leave this false.
    bool       suballocated;
    VkSuballoc suballoc;
} VkTexture;

typedef struct VkTextureBatchUpload {
    VkTexture* texture;
    const void* data;
    size_t data_size;
    uint32_t width;
    uint32_t height;
    uint32_t stride_pixels;
    uint32_t dirty_x;
    uint32_t dirty_y;
    uint32_t dirty_w;
    uint32_t dirty_h;
} VkTextureBatchUpload;

// ============================================================
// Effects
// ============================================================

typedef enum VkEffectType {
    VK_EFFECT_CRT = 0,
    VK_EFFECT_VIVID = 1,
    VK_EFFECT_HDR = 2,
    VK_EFFECT_NATURAL = 3,
    VK_EFFECT_SGSR1 = 4,
    VK_EFFECT_TOON = 5,
    VK_EFFECT_NTSC = 6,
    VK_EFFECT_COLORADJ = 7,
    VK_EFFECT_COLORGRADE = 8,
    VK_EFFECT_SHARPEN = 9,
    VK_EFFECT_SCANLINES = 10,
    VK_EFFECT_NTSC2 = 11,
    VK_EFFECT_COLORBLIND = 12,
    VK_EFFECT_PIXELATE = 13,
    VK_EFFECT_COUNT
} VkEffectType;

typedef struct VkEffectSlot {
    VkEffectType type;
    int          mode;     // effect-specific mode
    float        param0;   // generic
    float        param1;
    float        param2;   // generic
} VkEffectSlot;

// ============================================================
// Scene snapshot (mutex-protected, written from Java threads, read on render thread)
// ============================================================

typedef struct VkRenderableWindow {
    VkTexture* texture;        // borrowed; not owned
    int        x, y;
    uint32_t   width, height;
    float      u0, v0, u1, v1;
    bool       direct_scanout; // hint, currently unused
} VkRenderableWindow;

typedef struct VkScene {
    VkRenderableWindow windows[VK_MAX_RENDERABLE_WINDOWS];
    uint32_t           window_count;

    VkTexture* cursor_texture;
    int        cursor_x;
    int        cursor_y;
    uint32_t   cursor_width;
    uint32_t   cursor_height;
    bool       cursor_visible;

    // Transform parameters - tmpXForm2 of GLRenderer applied to all windows.
    float xform[6];
    bool  scissor_enabled;
    int   scissor_x, scissor_y, scissor_w, scissor_h;
    int   viewport_x, viewport_y, viewport_w, viewport_h;
    bool  viewport_set;

    // Render dims (logical screen size).
    uint32_t screen_width;
    uint32_t screen_height;
    uint32_t source_width;
    uint32_t source_height;
    bool     swap_rb;

    VkEffectSlot effects[VK_MAX_EFFECTS];
    uint32_t     effect_count;

    bool dirty;
} VkScene;

// ============================================================
// Pipelines - one per shader pass type
// ============================================================

typedef struct VkPipelineSet {
    VkDescriptorSetLayout sampler_set_layout;
    VkPipelineLayout      window_layout;     // push constants: xform[6] + viewSize
    VkPipelineLayout      effect_layout;     // push constants: resolution + effect params
    VkPipeline            window_pipeline;
    VkPipeline            cursor_pipeline;
    VkPipeline            blit_pipeline;
    VkPipeline            effect_pipelines[VK_EFFECT_COUNT];
    VkPipeline            offscreen_window_pipeline;
    VkPipeline            offscreen_cursor_pipeline;
    VkPipeline            offscreen_blit_pipeline;
    VkPipeline            offscreen_effect_pipelines[VK_EFFECT_COUNT];

    // Render passes
    VkRenderPass swapchain_pass;             // load=clear, store=store, final=present
    VkRenderPass offscreen_pass;             // load=clear, store=store, final=shader-read

    // --- Frame generation (created once with the rest; persist across swapchain rebuilds) ---
    VkDescriptorSetLayout fg_motion_layout;      // set0: binding0,1 sampler(prev,curr) + binding2 STORAGE_IMAGE(mv), COMPUTE
    VkDescriptorSetLayout fg_interp_layout;      // set0: 4x COMBINED_IMAGE_SAMPLER (prev,curr,mvBwd,mvFwd), FRAGMENT
    VkPipelineLayout      fg_motion_pipe_layout; // [motion] set + 32B compute push range
    VkPipelineLayout      fg_interp_pipe_layout; // [interp] set + 24B fragment push range
    VkPipeline            fg_motion_pipeline;    // compute (block matching)
    VkPipeline            fg_interp_pipeline;    // graphics (interpolation, swapchain_pass)

    VkDescriptorSetLayout cnn_pyramid_dsl, cnn_conv_dsl, cnn_cost9_dsl,
                          cnn_flowreg_dsl, cnn_warpfollow_dsl, cnn_generate_dsl;
    VkPipelineLayout      cnn_pyramid_pl,  cnn_conv_pl,  cnn_cost9_pl,
                          cnn_flowreg_pl,  cnn_warpfollow_pl,  cnn_generate_pl;
    VkPipeline            cnn_pyramid_pipe, cnn_conv_pipe, cnn_cost9_pipe,
                          cnn_flowreg_pipe, cnn_warpfollow_pipe, cnn_generate_pipe;
} VkPipelineSet;

// ============================================================
// Per-frame resources
// ============================================================

typedef struct VkFrame {
    VkSemaphore image_available;
    VkFence     in_flight;
    VkCommandBuffer cmd;
} VkFrame;

// ============================================================
// Offscreen targets (for effect ping-pong)
// ============================================================

typedef struct VkOffscreen {
    VkImage         image;
    VkImageView     view;
    VkDeviceMemory  memory;
    VkSampler       sampler;
    VkDescriptorSet descriptor_set;
    VkFramebuffer   framebuffer;
    uint32_t        width, height;
} VkOffscreen;

typedef struct VkSgsr1State {
    VkOffscreen source;
    bool        built;
    uint32_t    width;
    uint32_t    height;
} VkSgsr1State;

// A history frame (full res, render target + sampled) or the half-res motion field (rgba16f).
typedef struct VkFgImage {
    VkImage         image;
    VkImageView     view;
    VkDeviceMemory  memory;
    VkFramebuffer   framebuffer;     // history targets only; VK_NULL_HANDLE for the motion field
    VkDescriptorSet blit_set;        // history only: single-binding set (sampler_set_layout) for present
    uint32_t        width, height;
} VkFgImage;

// ============================================================
#define CNN_LEVELS 7
typedef struct VkCnnImg {
    VkImage        image;
    VkImageView    view;
    VkImageView    layerView[4];
    VkDeviceMemory memory;
    uint32_t       w, h, layers;
} VkCnnImg;
typedef struct VkCnnFeatSet {
    VkCnnImg luma  [CNN_LEVELS];
    VkCnnImg feat4a[CNN_LEVELS];
    VkCnnImg feat4b[CNN_LEVELS];
    VkCnnImg feat8 [CNN_LEVELS];
} VkCnnFeatSet;
#define CNN_POOLS 8

typedef struct VkFgCnn {
    bool             ready;
    VkDescriptorPool pool[CNN_POOLS];
    uint32_t         curPool;
    VkCnnFeatSet     featPrev, featCurr;
    VkCnnImg feat8_pair[CNN_LEVELS];
    VkCnnImg hG0[CNN_LEVELS], hG1[CNN_LEVELS], hG23[CNN_LEVELS], hG4[CNN_LEVELS];
    VkCnnImg hD0[CNN_LEVELS], hD1[CNN_LEVELS], hD2[CNN_LEVELS], hD3[CNN_LEVELS];
    VkCnnImg hD5[CNN_LEVELS], hD6[CNN_LEVELS], hD7[CNN_LEVELS], hD8[CNN_LEVELS];
    VkCnnImg dpair[CNN_LEVELS];
    VkCnnImg flowMid[CNN_LEVELS];
    VkCnnImg flowRef[CNN_LEVELS];
    VkCnnImg occ;
    VkCnnImg seedBlack;
    VkCnnImg dummy;
    VkBuffer ubo; VkDeviceMemory uboMem;
    VkBuffer       w[64];
    VkDeviceMemory wMem[64];
    VkDeviceSize   wLen[64];
} VkFgCnn;

// ============================================================
// Staging pool for async texture uploads
// ============================================================

typedef struct VkStagingSlot {
    pthread_mutex_t mutex;        // held by current owner from acquire to release
    VkCommandPool   cmd_pool;     // exclusive to this slot, no global cmd pool sync needed
    VkCommandBuffer cmd;
    VkBuffer        buffer;
    VkDeviceMemory  memory;
    void*           mapped;       // persistently mapped HOST_VISIBLE memory
    VkDeviceSize    size;         // current allocation; grows on demand
    VkFence         fence;        // signaled when this slot's last submission completes
} VkStagingSlot;

typedef struct VkStagingPool {
    VkStagingSlot   slots[VK_STAGING_POOL_SIZE];
    uint32_t        valid_slots;  // count of slots whose per-slot mutex is initialized
    uint64_t        next;         // round-robin counter
    pthread_mutex_t mutex;        // protects `next` only
    bool            mutex_init;   // pool-mutex initialization flag (for safe destroy)
    bool            initialized;
} VkStagingPool;

// ============================================================
// Deferred destruction graveyard
// ============================================================

typedef struct VkGraveSlot {
    VkTexture** textures;
    uint32_t    count;
    uint32_t    capacity;
} VkGraveSlot;

// ============================================================
// Device caps (queried once after create_device)
// ============================================================

typedef struct VkDeviceCaps {
    // Identity
    uint32_t vendor_id;
    uint32_t device_id;
    uint32_t driver_version;
    bool     is_adreno;             // vendor_id == 0x5143 (Qualcomm)

    // Limits / sizing
    VkPhysicalDeviceLimits limits;
    uint32_t descriptor_pool_capacity;

    // Format choices resolved against driver feature support
    VkFormat offscreen_format;      // BGRA preferred, RGBA fallback
    VkFormat upload_format;         // BGRA preferred; RGBA fallback uses CPU-side swizzle
    bool     upload_needs_bgra_swizzle;

    // Diagnostic
    bool ahb_bgra_supported;        // VK_FORMAT_B8G8R8A8_UNORM importable from AHB
} VkDeviceCaps;

// ============================================================
// Image sub-allocator
// ============================================================
// CPU-uploaded textures share large DEVICE_LOCAL blocks via a first-fit free list;
// AHB imports stay dedicated.

#define VK_SUBALLOC_BLOCK_SIZE (32u * 1024u * 1024u)  // 32 MiB default block

typedef struct VkMemRegion {
    VkDeviceSize        offset;
    VkDeviceSize        size;
    struct VkMemRegion* next;       // free list, sorted ascending by offset
} VkMemRegion;

typedef struct VkMemBlock {
    VkDeviceMemory     memory;
    VkDeviceSize       size;
    uint32_t           memory_type_index;
    VkMemRegion*       free_list;
    struct VkMemBlock* next;
} VkMemBlock;

typedef struct VkImageSuballocator {
    VkMemBlock*     blocks;
    VkDeviceSize    block_size;     // size used when carving a new block
    pthread_mutex_t mutex;          // alloc (producer threads) vs free (render thread)
    bool            mutex_init;
} VkImageSuballocator;

// ============================================================
// Master state
// ============================================================

typedef struct VkRenderer {
    // Lifecycle
    bool initialized;
    bool surface_ready;
    // Set when using a fallback swapchain whose preTransform differs from currentTransform.
    bool ignore_suboptimal;
    pthread_mutex_t scene_mutex;     // guards r->scene + graveyard slots; held briefly by all
    pthread_mutex_t queue_mutex;     // serializes vkQueueSubmit across threads
    pthread_mutex_t texture_mutex;   // guards live_textures
    pthread_mutex_t descriptor_mutex;// external sync for descriptor_pool alloc/free
    pthread_mutex_t render_mutex;    // serializes lifecycle vs render

    // Instance + physical/logical device
    void*            vulkan_handle;          // dlopen handle for libvulkan
    VkInstance       instance;
    bool             validation_enabled;
    bool             debug_utils_enabled;
    VkDebugUtilsMessengerEXT debug_messenger;
    VkPhysicalDevice physical_device;
    VkPhysicalDeviceMemoryProperties mem_props;
    uint32_t         graphics_queue_family;
    VkDevice         device;
    VkQueue          graphics_queue;

    // Surface + swapchain
    ANativeWindow*   anw;
    VkSurfaceKHR     surface;
    VkSwapchainKHR   swapchain;
    VkFormat         swapchain_format;
    VkSurfaceTransformFlagBitsKHR swapchain_transform;
    VkExtent2D       surface_extent;
    VkExtent2D       swapchain_extent;
    uint32_t         swapchain_image_count;
    VkImage          swapchain_images[VK_MAX_SWAPCHAIN_IMAGES];
    VkImageView      swapchain_views[VK_MAX_SWAPCHAIN_IMAGES];
    VkFramebuffer    swapchain_framebuffers[VK_MAX_SWAPCHAIN_IMAGES];
    VkSemaphore      swapchain_render_finished[VK_MAX_SWAPCHAIN_IMAGES];

    // Pipelines / passes
    VkPipelineSet    pipelines;
    bool             pipelines_built;

    // Offscreen ping-pong (created lazily when effects are present)
    VkOffscreen      offscreen[2];
    bool             offscreen_built;
    VkSgsr1State     sgsr1;

    // --- Frame generation ---
    bool             fg_enabled;
    bool             fg_float16_supported;   // shaderFloat16 available (selects the fp16 motion shader)
    bool             fg_built;               // history + motion images allocated at fg_dims
    VkExtent2D       fg_dims;                // extent the fg images were built for
    VkFgImage        fg_history[3];          // composited-scene ring; fg_history_curr = newest
    VkFgImage        fg_motion[3];           // per-parity rgba16f half-res backward-flow ring (1 per history slot)
    VkFgImage        fg_motion_fwd[3];       // per-parity rgba16f half-res forward-flow ring (Quality bidirectional)
    VkFgImage        fg_coarse[3];           // per-parity quarter-res backward coarse-flow (coarse-to-fine seed)
    VkFgImage        fg_coarse_fwd[3];       // per-parity quarter-res forward coarse-flow
    VkSampler        fg_sampler;             // linear, clamp — for all fg sampled reads
    VkDescriptorSet  fg_motion_set[3];       // [curr] prev,curr,coarse samplers + motion storage (fine pass)
    VkDescriptorSet  fg_motion_set_fwd[3];   // [curr] swapped prev,curr + fwd-coarse + fwd-motion storage
    VkDescriptorSet  fg_coarse_set[3];       // [curr] prev,curr + coarse-bwd storage (coarse pass)
    VkDescriptorSet  fg_coarse_set_fwd[3];   // [curr] swapped prev,curr + coarse-fwd storage
    VkDescriptorSet  fg_interp_set[3];       // [curr] prev,curr,mvBwd,mvFwd samplers (interpolate.frag)
    VkDescriptorSet  fg_interp_set_deep[3];  // deep mode: interp the pair one step behind the newest
    VkFence          fg_slot_fence[3];       // last submit that used each history slot
    uint32_t         fg_history_curr;        // index (0..2) of the most-recent composited frame
    uint32_t         fg_history_count;       // 0,1,2,3 — valid history frames
    uint64_t         fg_present_count;       // actual vkQueuePresentKHR calls; guarded by queue_mutex
    bool             fg_motion_valid;        // backward flow current for the live pair (reused across multi-interp)
    bool             fg_motion_fwd_valid;    // forward flow current — computed on the 2nd interp (Quality) to spread cost
    bool             fg_deep_mode;           // quality pipeline: bidirectional warp (adds a forward flow)
    bool             fg_extrapolate;         // false=interpolate, true=extrapolate forward
    uint32_t         fg_target_fif;          // requested compositor frames-in-flight 1..3 (applied live)
    float            fg_occ_lo;              // interpolate.frag consistency lower bound (smoothness)
    float            fg_occ_hi;              // interpolate.frag consistency upper bound (smoothness)
    int32_t          fg_min_step;            // motion.comp lowest TSS step (quality preset; 1 = full search)
    float            fg_flow_scale;          // flow-field resolution scale [0.2,1.0] (preset GPU-cost dial)
    float            fg_built_flow_scale;    // flow_scale baked into the current motion resources

    bool             fg_use_cnn;
    bool             fg_cnn_capable;
    uint32_t         fg_cnn_flow_seq;
    VkFgCnn          fg_cnn;

    // --- Content-duplicate detection ------------------------------------------------------------
    // Each composited frame is downsampled to a tiny host buffer; the HOLD promotes the interp
    // pair only on a genuine content change so duplicate inputs don't advance.
    VkImage          fg_sig_img;             // tiny blit target, reused each HOLD
    VkDeviceMemory   fg_sig_img_mem;
    VkBuffer         fg_sig_buf[3];          // per-slot host-visible downsample of each history slot
    VkDeviceMemory   fg_sig_buf_mem[3];
    void*            fg_sig_ptr[3];          // persistent map of fg_sig_buf
    bool             fg_sig_supported;       // blit+readback path created OK (else dedup disabled)
    int32_t          fg_stage_slot;          // history slot holding the pending (un-promoted) frame, -1 = none
    VkFence          fg_stage_fence;         // fence that produced fg_sig_buf[fg_stage_slot]
    uint64_t         fg_last_promote_ns;     // last promotion time (freeze backstop)
    double           fg_last_sig_delta;      // last measured content delta (diagnostics)
    uint64_t         fg_dup_dropped, fg_distinct;  // dedup telemetry
    uint64_t         fg_promote_count;       // monotonic count of promotions (distinct content committed)
    uint64_t         fg_promote_ns;          // CLOCK_MONOTONIC of the most recent promotion (for Java phase anchor)

    // Quad vertex buffer (window/cursor)
    VkBuffer         quad_vbo;
    VkDeviceMemory   quad_vbo_memory;

    // Shared sampler for all CPU-uploaded textures and AHB textures that don't need a Ycbcr
    // conversion. Created once at init.
    VkSampler        shared_sampler;
    VkSampler        shared_sampler_nearest;
    VkSampler        shared_sampler_cubic;
    bool             ext_filter_cubic;
    int              scale_filter;

    // Per-frame
    VkCommandPool    cmd_pool;
    VkFrame          frames[VK_FRAMES_IN_FLIGHT];
    uint32_t         frame_index;

    // Descriptor pool (for texture sampler descriptors)
    VkDescriptorPool descriptor_pool;
    uint32_t         descriptor_pool_capacity;
    uint32_t         descriptor_pool_used;
    VkDescriptorSet* descriptor_free_list;
    uint32_t         descriptor_free_count;
    uint32_t         descriptor_free_capacity;

    // Graveyard
    VkGraveSlot      graveyard[VK_FRAMES_IN_FLIGHT + 1];
    uint32_t         graveyard_index;

    // Live native textures owned by this renderer/device; drained on nativeDestroy.
    VkTexture**      live_textures;
    uint32_t         live_texture_count;
    uint32_t         live_texture_capacity;

    // Extensions present
    bool ext_ahb;
    bool ext_ycbcr;

    // Cached device capabilities populated by query_device_caps().
    VkDeviceCaps caps;

    // Function pointers loaded via vkGetDeviceProcAddr (not all are statically exported by
    // the Android Vulkan loader, even in Vulkan 1.1).
    PFN_vkGetAndroidHardwareBufferPropertiesANDROID fnGetAhbProps;
    PFN_vkCreateSamplerYcbcrConversion              fnCreateYcbcr;
    PFN_vkDestroySamplerYcbcrConversion             fnDestroyYcbcr;
    PFN_vkCreateDebugUtilsMessengerEXT              fnCreateDebugUtilsMessenger;
    PFN_vkDestroyDebugUtilsMessengerEXT             fnDestroyDebugUtilsMessenger;

    // VK_GOOGLE_display_timing — capability-gated FG present-pacing hint + telemetry (no-op when absent).
    bool             ext_display_timing;
    uint64_t         refresh_duration_ns;   // panel vsync period from the swapchain (fallback)
    uint64_t         fg_present_period_ns;   // target inter-present interval (ns) fed from Java
    uint64_t         fg_present_deadline_ns; // unsnapped deadline accumulator (target-rate grid)
    uint64_t         fg_present_target_ns;   // free content-rate sleep/present target for the next present
    uint64_t         fg_display_period_ns;   // live panel vsync period fed from Java (Choreographer EMA)
    uint64_t         fg_vsync_anchor_ns;     // latest Choreographer vsync timestamp (CLOCK_MONOTONIC)
    uint64_t         fg_prev_arrival_ns;     // real-frame arrival times, for time-based interp phase
    uint64_t         fg_curr_arrival_ns;
    uint64_t         fg_t_last_ns;           // present-interval telemetry accumulators
    uint32_t         fg_t_count;
    double           fg_t_sum_ms;
    double           fg_t_sumsq_ms;
    double           fg_t_min_ms;
    double           fg_t_max_ms;
    double           fg_fw_sum_ms;           // in_flight fence-wait telemetry (GL-thread block before present)
    double           fg_fw_max_ms;
    uint32_t         fg_fw_n;
    float            fg_dbg_phase[8];        // interp phases accumulated for the in-progress period
    float            fg_dbg_done[8];         // last completed period's phases (logged in telemetry)
    uint32_t         fg_dbg_n;
    uint32_t         fg_dbg_done_n;
    uint64_t         fg_dbg_last_curr;
    uint32_t         fg_present_id;

    // FG generation worker: GL thread enqueues; this pthread runs flow+generate+pace+present
    // and owns the swapchain present path while FG is on.
    FgJob            fg_job_ring[FG_JOB_RING];   // SPSC: GL produces (tail), worker consumes (head)
    volatile uint32_t fg_job_head;
    volatile uint32_t fg_job_tail;
    sem_t            fg_gen_sem;
    pthread_t        fg_gen_thread;
    volatile int     fg_gen_running;
    bool             fg_gen_started;
    VkCommandPool    fg_worker_pool;             // worker-owned (command pools are not thread-safe)
    VkFrame          fg_worker_frames[3];        // worker-owned cmd + fence + image_available
    uint32_t         fg_worker_index;
    volatile uint32_t fg_promote_seq;            // ++ on each HOLD promote; jobs snapshot it
    volatile uint32_t fg_swapchain_gen;          // ++ on swapchain recreate; worker drops present across a change

    PFN_vkGetRefreshCycleDurationGOOGLE   fnGetRefreshCycleDuration;
    PFN_vkGetPastPresentationTimingGOOGLE fnGetPastPresentationTiming;

    // VK_NV_optical_flow: driver-accelerated motion estimation (replaces the classical block-match flow).
    bool                                  fg_optical_flow;   // extension available + feature enabled
    PFN_vkGetPhysicalDeviceOpticalFlowImageFormatsNV fnOFFormats;
    PFN_vkCreateOpticalFlowSessionNV      fnOFCreate;
    PFN_vkDestroyOpticalFlowSessionNV     fnOFDestroy;
    PFN_vkBindOpticalFlowSessionImageNV   fnOFBind;
    PFN_vkCmdOpticalFlowExecuteNV         fnOFExecute;

    // Async upload pool (created in nativeCreate after device).
    VkStagingPool staging_pool;

    // Image sub-allocator for CPU-uploaded textures (created in nativeCreate after device).
    VkImageSuballocator image_suballoc;

    // Grow-only scratch for the batch upload path (render-thread-only, so unlocked; zero
    // steady-state heap traffic).
    VkTextureBatchUpload* batch_entry_scratch;     // nativeBatchUpdate: parsed JNI entries
    uint32_t              batch_entry_cap;
    void*                 batch_prepared_scratch;  // vkr_texture_batch_update: PreparedBatchUpload[]
    uint32_t              batch_prepared_cap;

    // Scene state
    VkScene scene;

    // Compositor present mode requested by Java (default FIFO). Validated against
    // device-supported modes in create_swapchain; falls back to FIFO if unavailable.
    VkPresentModeKHR target_present_mode;
    // Present mode actually selected by the last create_swapchain (target may fall back). Read by
    // Java (nativeGetActivePresentMode) so frame generation knows whether presents are non-blocking.
    VkPresentModeKHR active_present_mode;
} VkRenderer;

// ============================================================
// Public functions implemented in vk_image.c
// ============================================================

VkTexture* vkr_texture_create_uploaded(VkRenderer* r, uint32_t width, uint32_t height,
                                       const void* data, size_t data_size, uint32_t stride_pixels);
bool       vkr_texture_update(VkRenderer* r, VkTexture* tex, uint32_t width, uint32_t height,
                              const void* data, size_t data_size, uint32_t stride_pixels,
                              uint32_t dirty_x, uint32_t dirty_y,
                              uint32_t dirty_w, uint32_t dirty_h);
bool       vkr_texture_batch_update(VkRenderer* r, const VkTextureBatchUpload* uploads,
                                    uint32_t upload_count);
VkTexture* vkr_texture_import_ahb(VkRenderer* r, AHardwareBuffer* ahb, bool transfer_ownership);
void       vkr_texture_destroy(VkRenderer* r, VkTexture* tex);
void       vkr_texture_destroy_all_live(VkRenderer* r);
void       vkr_texture_schedule_destroy(VkRenderer* r, VkTexture* tex);

// Helpers
uint32_t   vkr_find_memory_type(VkRenderer* r, uint32_t type_bits, VkMemoryPropertyFlags props);
void       vkr_run_one_shot_cmd(VkRenderer* r, void (*fn)(VkCommandBuffer, void*), void* user);
void       vkr_image_barrier(VkCommandBuffer cmd, VkImage image, VkImageLayout from, VkImageLayout to,
                             VkPipelineStageFlags src_stage, VkPipelineStageFlags dst_stage,
                             VkAccessFlags src_access, VkAccessFlags dst_access);
bool       vkr_create_sampler(VkRenderer* r, VkSamplerYcbcrConversion ycbcr, VkSampler* out);
void       vkr_retarget_shared_sampler(VkRenderer* r);
// Async layout transition through the staging pool; does not wait for the GPU.
// Returns false on submit failure.
bool       vkr_submit_async_transition(VkRenderer* r, VkImage image,
                                       VkImageLayout from, VkImageLayout to,
                                       VkPipelineStageFlags src_stage, VkPipelineStageFlags dst_stage,
                                       VkAccessFlags src_access, VkAccessFlags dst_access);

// Staging pool — created in nativeCreate, drained + destroyed in nativeDestroy.
bool            vkr_staging_pool_init(VkRenderer* r);
void            vkr_staging_pool_destroy(VkRenderer* r);
VkStagingSlot*  vkr_staging_pool_acquire(VkRenderer* r, VkDeviceSize needed);
void            vkr_staging_pool_release(VkStagingSlot* slot);

// Image sub-allocator lifecycle (alloc/free are static in vk_image.c). Destroy after all
// textures are released.
void            vkr_suballoc_init(VkRenderer* r);
void            vkr_suballoc_destroy(VkRenderer* r);
