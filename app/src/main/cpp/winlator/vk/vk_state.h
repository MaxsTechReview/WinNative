// Master state header for the Vulkan compositor.
// Internal use only — JNI entry points expose a long handle that wraps VkRenderer*.

#pragma once

#include <android/hardware_buffer.h>
#include <android/log.h>
#include <android/native_window.h>
#include <pthread.h>
#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>
#include <vulkan/vulkan.h>

#define VK_LOG_TAG "VkRenderer"
#define VK_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  VK_LOG_TAG, __VA_ARGS__)
#define VK_LOGW(...) __android_log_print(ANDROID_LOG_WARN,  VK_LOG_TAG, __VA_ARGS__)
#define VK_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, VK_LOG_TAG, __VA_ARGS__)

#define VK_FRAMES_IN_FLIGHT 2
#define VK_MAX_SWAPCHAIN_IMAGES 8
#define VK_MAX_EFFECTS 8
#define VK_MAX_RENDERABLE_WINDOWS 64
// Number of in-flight upload slots. Each slot owns a persistently-mapped staging buffer,
// fence, and command pool. An upload only blocks when this many uploads are still pending
// on the GPU — with 8 slots and ~100µs GPU upload time, we can sustain ~80k uploads/sec
// without ever waiting.
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
    VK_EFFECT_FSR = 1,
    VK_EFFECT_HDR = 2,
    VK_EFFECT_NATURAL = 3,
    VK_EFFECT_COUNT
} VkEffectType;

typedef struct VkEffectSlot {
    VkEffectType type;
    int          mode;     // FSR only
    float        param0;   // generic
    float        param1;
    float        param2;   // FSR sharpness; ignored by other effects
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
    VkPipelineLayout      effect_layout;     // push constants: resolution + 2 floats
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
// Master state
// ============================================================

typedef struct VkRenderer {
    // Lifecycle
    bool initialized;
    bool surface_ready;
    // True when we deliberately create a fallback swapchain with a preTransform that differs
    // from caps.currentTransform (Adreno reports SUBOPTIMAL on every present in that case).
    bool ignore_suboptimal;
    pthread_mutex_t scene_mutex;     // guards r->scene + graveyard slots; held briefly by all
    pthread_mutex_t queue_mutex;     // serializes vkQueueSubmit across threads
    pthread_mutex_t texture_mutex;   // guards live_textures
    pthread_mutex_t descriptor_mutex;// external sync for descriptor_pool alloc/free
    pthread_mutex_t render_mutex;    // serializes lifecycle vs render; held by render thread for
                                     // the full acquire+record+submit+present, and by lifecycle
                                     // ops (surface create/change/destroy) before they touch the
                                     // swapchain. Scene producers do NOT take this — they only
                                     // touch scene_mutex, so they never stall behind a frame.

    // Instance + physical/logical device
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

    // Quad vertex buffer (window/cursor)
    VkBuffer         quad_vbo;
    VkDeviceMemory   quad_vbo_memory;

    // Shared sampler for all CPU-uploaded textures and AHB textures that don't need a Ycbcr
    // conversion. Created once at init; vkCreateSampler costs ~50-200µs on Adreno, so giving
    // every texture its own sampler is a non-trivial CPU+GPU tax during pixmap churn.
    VkSampler        shared_sampler;

    // Per-frame
    VkCommandPool    cmd_pool;
    VkFrame          frames[VK_FRAMES_IN_FLIGHT];
    uint32_t         frame_index;

    // Descriptor pool (for texture sampler descriptors)
    VkDescriptorPool descriptor_pool;
    uint32_t         descriptor_pool_capacity;
    uint32_t         descriptor_pool_used;

    // Graveyard
    VkGraveSlot      graveyard[VK_FRAMES_IN_FLIGHT + 1];
    uint32_t         graveyard_index;

    // Live native textures owned by this renderer/device. Java Texture objects can outlive a
    // renderer teardown, so nativeDestroy drains this list before the device is destroyed.
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

    // Async upload pool (created in nativeCreate after device).
    VkStagingPool staging_pool;

    // Scene state
    VkScene scene;

    // FPS limit (nanoseconds per frame; 0 = unlimited)
    int64_t target_frame_time_ns;
    int64_t next_frame_time_ns;

    // Compositor present mode requested by Java (default FIFO). Validated against
    // device-supported modes in create_swapchain; falls back to FIFO if unavailable.
    VkPresentModeKHR target_present_mode;
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
// Async layout transition through the staging pool. Submits a tiny command buffer that runs
// the requested barrier, but does NOT wait for the GPU. The barrier is ordered before all
// subsequent submits on the same queue per Vulkan spec, so callers can sample the image as
// soon as the next render submit happens. Returns false on submit failure.
bool       vkr_submit_async_transition(VkRenderer* r, VkImage image,
                                       VkImageLayout from, VkImageLayout to,
                                       VkPipelineStageFlags src_stage, VkPipelineStageFlags dst_stage,
                                       VkAccessFlags src_access, VkAccessFlags dst_access);

// Staging pool — created in nativeCreate, drained + destroyed in nativeDestroy.
bool            vkr_staging_pool_init(VkRenderer* r);
void            vkr_staging_pool_destroy(VkRenderer* r);
VkStagingSlot*  vkr_staging_pool_acquire(VkRenderer* r, VkDeviceSize needed);
void            vkr_staging_pool_release(VkStagingSlot* slot);
