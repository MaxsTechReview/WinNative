// VkTexture allocation, upload, AHB import.
//
// Two creation paths:
//   1. CPU-uploaded: caller hands us BGRA pixel data; we allocate VkImage in DEVICE_LOCAL memory,
//      stage the upload through a host-visible buffer, and transition to SHADER_READ_OPTIMAL.
//   2. AHardwareBuffer import: caller hands us an AHB; we allocate dedicated memory backed by the
//      AHB (no copy) and bind it to a VkImage. For non-RGB formats (DRI3 vendor formats), we use
//      a Ycbcr conversion so the sampler can read them.
//
// Texture lifetimes:
//   - Created/updated synchronously on caller's thread (Java/render).
//   - Submits go through vkQueueSubmit which is serialized via VkRenderer::queue_mutex.
//   - Destruction is deferred via the graveyard so in-flight frames don't see freed handles.

#include "vk_state.h"
#include <stdlib.h>
#include <string.h>

#define HAL_PIXEL_FORMAT_BGRA_8888 5

uint32_t vkr_find_memory_type(VkRenderer* r, uint32_t type_bits, VkMemoryPropertyFlags props) {
    for (uint32_t i = 0; i < r->mem_props.memoryTypeCount; i++) {
        if ((type_bits & (1u << i))
            && (r->mem_props.memoryTypes[i].propertyFlags & props) == props) {
            return i;
        }
    }
    return UINT32_MAX;
}

void vkr_image_barrier(VkCommandBuffer cmd, VkImage image, VkImageLayout from, VkImageLayout to,
                       VkPipelineStageFlags src_stage, VkPipelineStageFlags dst_stage,
                       VkAccessFlags src_access, VkAccessFlags dst_access) {
    VkImageMemoryBarrier b = {0};
    b.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    b.oldLayout = from;
    b.newLayout = to;
    b.srcAccessMask = src_access;
    b.dstAccessMask = dst_access;
    b.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    b.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    b.image = image;
    b.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    b.subresourceRange.baseMipLevel = 0;
    b.subresourceRange.levelCount = 1;
    b.subresourceRange.baseArrayLayer = 0;
    b.subresourceRange.layerCount = 1;
    vkCmdPipelineBarrier(cmd, src_stage, dst_stage, 0, 0, NULL, 0, NULL, 1, &b);
}

// ============================================================
// Staging pool — async upload infrastructure
// ============================================================
//
// Each slot owns a VkBuffer, persistently-mapped HOST_VISIBLE memory, a VkCommandPool with
// one VkCommandBuffer, and a VkFence. Round-robin acquisition under a tiny mutex; per-slot
// mutex provides exclusive ownership for the lifetime of an upload (acquire→submit→release).
//
// On a single graphics queue, the upload's terminal pipeline barrier (TRANSFER_WRITE →
// SHADER_READ, dstStage=FRAGMENT_SHADER) extends into all subsequent submits per Vulkan
// spec — so the renderer needs no extra synchronization to safely sample a freshly-updated
// texture as long as the upload was submitted before the render.

bool vkr_staging_pool_init(VkRenderer* r) {
    if (r->staging_pool.initialized) return true;
    pthread_mutex_init(&r->staging_pool.mutex, NULL);
    r->staging_pool.mutex_init = true;
    r->staging_pool.next = 0;
    r->staging_pool.valid_slots = 0;

    VkFenceCreateInfo fi = {VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
    fi.flags = VK_FENCE_CREATE_SIGNALED_BIT;  // first acquire of each slot finds the fence ready

    for (uint32_t i = 0; i < VK_STAGING_POOL_SIZE; i++) {
        VkStagingSlot* s = &r->staging_pool.slots[i];
        pthread_mutex_init(&s->mutex, NULL);
        r->staging_pool.valid_slots = i + 1;  // mutex is now valid; destroy must clean it up

        VkCommandPoolCreateInfo cpci = {VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
        cpci.flags = VK_COMMAND_POOL_CREATE_TRANSIENT_BIT;
        cpci.queueFamilyIndex = r->graphics_queue_family;
        if (vkCreateCommandPool(r->device, &cpci, NULL, &s->cmd_pool) != VK_SUCCESS) {
            VK_LOGE("staging pool: vkCreateCommandPool slot %u failed", i);
            return false;
        }

        VkCommandBufferAllocateInfo cbai = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
        cbai.commandPool = s->cmd_pool;
        cbai.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        cbai.commandBufferCount = 1;
        if (vkAllocateCommandBuffers(r->device, &cbai, &s->cmd) != VK_SUCCESS) {
            VK_LOGE("staging pool: vkAllocateCommandBuffers slot %u failed", i);
            return false;
        }

        if (vkCreateFence(r->device, &fi, NULL, &s->fence) != VK_SUCCESS) {
            VK_LOGE("staging pool: vkCreateFence slot %u failed", i);
            return false;
        }
        // buffer/memory allocated lazily on first use, sized to the actual upload.
    }
    r->staging_pool.initialized = true;
    return true;
}

void vkr_staging_pool_destroy(VkRenderer* r) {
    // Tolerates partially-initialized pools — only iterate the slots whose mutexes were
    // successfully initialized.
    for (uint32_t i = 0; i < r->staging_pool.valid_slots; i++) {
        VkStagingSlot* s = &r->staging_pool.slots[i];
        if (s->fence) {
            // Drain any pending submission so the buffer/memory are safe to free.
            vkWaitForFences(r->device, 1, &s->fence, VK_TRUE, UINT64_MAX);
            vkDestroyFence(r->device, s->fence, NULL);
        }
        if (s->mapped && s->memory) vkUnmapMemory(r->device, s->memory);
        if (s->buffer)   vkDestroyBuffer(r->device, s->buffer, NULL);
        if (s->memory)   vkFreeMemory(r->device, s->memory, NULL);
        if (s->cmd_pool) vkDestroyCommandPool(r->device, s->cmd_pool, NULL);
        pthread_mutex_destroy(&s->mutex);
        memset(s, 0, sizeof(*s));
    }
    if (r->staging_pool.mutex_init) {
        pthread_mutex_destroy(&r->staging_pool.mutex);
    }
    memset(&r->staging_pool, 0, sizeof(r->staging_pool));
}

// Re-allocate a slot's staging buffer to at least `needed` bytes. Caller must own the slot.
static bool grow_staging_slot(VkRenderer* r, VkStagingSlot* s, VkDeviceSize needed) {
    // Round up to 64 KiB so consecutive size bumps don't trigger reallocs.
    VkDeviceSize new_size = (needed + 65535ull) & ~(VkDeviceSize)65535ull;

    if (s->mapped && s->memory) { vkUnmapMemory(r->device, s->memory); s->mapped = NULL; }
    if (s->buffer) { vkDestroyBuffer(r->device, s->buffer, NULL); s->buffer = VK_NULL_HANDLE; }
    if (s->memory) { vkFreeMemory(r->device, s->memory, NULL); s->memory = VK_NULL_HANDLE; }
    // Reset size now so a later allocation failure leaves the slot in a state where the next
    // acquire will retry grow_staging_slot rather than skip it and hand back a NULL buffer.
    s->size = 0;

    VkBufferCreateInfo bi = {VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
    bi.size = new_size;
    bi.usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
    bi.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    if (vkCreateBuffer(r->device, &bi, NULL, &s->buffer) != VK_SUCCESS) return false;

    VkMemoryRequirements mr;
    vkGetBufferMemoryRequirements(r->device, s->buffer, &mr);

    VkMemoryAllocateInfo ai = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    ai.allocationSize = mr.size;
    ai.memoryTypeIndex = vkr_find_memory_type(r, mr.memoryTypeBits,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        | VK_MEMORY_PROPERTY_HOST_CACHED_BIT);
    if (ai.memoryTypeIndex == UINT32_MAX) {
        ai.memoryTypeIndex = vkr_find_memory_type(r, mr.memoryTypeBits,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    }
    if (ai.memoryTypeIndex == UINT32_MAX) {
        vkDestroyBuffer(r->device, s->buffer, NULL); s->buffer = VK_NULL_HANDLE;
        return false;
    }
    if (vkAllocateMemory(r->device, &ai, NULL, &s->memory) != VK_SUCCESS) {
        vkDestroyBuffer(r->device, s->buffer, NULL); s->buffer = VK_NULL_HANDLE;
        return false;
    }
    vkBindBufferMemory(r->device, s->buffer, s->memory, 0);
    if (vkMapMemory(r->device, s->memory, 0, VK_WHOLE_SIZE, 0, &s->mapped) != VK_SUCCESS) {
        vkFreeMemory(r->device, s->memory, NULL); s->memory = VK_NULL_HANDLE;
        vkDestroyBuffer(r->device, s->buffer, NULL); s->buffer = VK_NULL_HANDLE;
        return false;
    }
    s->size = new_size;
    return true;
}

VkStagingSlot* vkr_staging_pool_acquire(VkRenderer* r, VkDeviceSize needed) {
    if (!r->staging_pool.initialized) return NULL;

    pthread_mutex_lock(&r->staging_pool.mutex);
    uint32_t idx = (uint32_t)(r->staging_pool.next++ % VK_STAGING_POOL_SIZE);
    pthread_mutex_unlock(&r->staging_pool.mutex);

    VkStagingSlot* s = &r->staging_pool.slots[idx];

    // Per-slot lock guards the slot's resources (buffer/cmd/fence) until release. Round-robin
    // means contention only happens once VK_STAGING_POOL_SIZE acquires have wrapped — i.e.
    // when the producer is consistently faster than the GPU can drain uploads.
    pthread_mutex_lock(&s->mutex);

    // Wait for the slot's previous submission to retire. With pool_size=8 this almost never
    // blocks because the fence signaled long ago. The fence is left signaled here on purpose
    // — it gets reset right before vkQueueSubmit, so any no-submit failure path between here
    // and submit leaves the fence safely signaled and the slot reusable.
    vkWaitForFences(r->device, 1, &s->fence, VK_TRUE, UINT64_MAX);
    vkResetCommandPool(r->device, s->cmd_pool, 0);

    if (s->size < needed && !grow_staging_slot(r, s, needed)) {
        pthread_mutex_unlock(&s->mutex);
        return NULL;
    }
    return s;
}

void vkr_staging_pool_release(VkStagingSlot* slot) {
    if (!slot) return;
    pthread_mutex_unlock(&slot->mutex);
}

void vkr_run_one_shot_cmd(VkRenderer* r, void (*fn)(VkCommandBuffer, void*), void* user) {
    VkCommandBufferAllocateInfo ai = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
    ai.commandPool = r->cmd_pool;
    ai.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    ai.commandBufferCount = 1;

    VkCommandBuffer cmd;
    VK_CHECK(vkAllocateCommandBuffers(r->device, &ai, &cmd));

    VkCommandBufferBeginInfo bi = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
    bi.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    VK_CHECK(vkBeginCommandBuffer(cmd, &bi));

    fn(cmd, user);

    VK_CHECK(vkEndCommandBuffer(cmd));

    VkSubmitInfo si = {VK_STRUCTURE_TYPE_SUBMIT_INFO};
    si.commandBufferCount = 1;
    si.pCommandBuffers = &cmd;

    VkFenceCreateInfo fi = {VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
    VkFence fence;
    VK_CHECK(vkCreateFence(r->device, &fi, NULL, &fence));

    pthread_mutex_lock(&r->queue_mutex);
    VK_CHECK(vkQueueSubmit(r->graphics_queue, 1, &si, fence));
    pthread_mutex_unlock(&r->queue_mutex);

    VK_CHECK(vkWaitForFences(r->device, 1, &fence, VK_TRUE, UINT64_MAX));

    vkDestroyFence(r->device, fence, NULL);
    vkFreeCommandBuffers(r->device, r->cmd_pool, 1, &cmd);
}

// Forward declaration — defined in vk_renderer.c.
VkDescriptorSet vkr_alloc_descriptor_set(VkRenderer* r);

static bool create_sampler(VkRenderer* r, VkSamplerYcbcrConversion ycbcr, VkSampler* out) {
    VkSamplerYcbcrConversionInfo yi = {VK_STRUCTURE_TYPE_SAMPLER_YCBCR_CONVERSION_INFO};
    yi.conversion = ycbcr;

    VkSamplerCreateInfo si = {VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO};
    if (ycbcr != VK_NULL_HANDLE) si.pNext = &yi;
    si.magFilter = VK_FILTER_LINEAR;
    si.minFilter = VK_FILTER_LINEAR;
    si.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    si.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    si.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    si.borderColor = VK_BORDER_COLOR_FLOAT_OPAQUE_BLACK;
    si.unnormalizedCoordinates = VK_FALSE;
    si.mipmapMode = VK_SAMPLER_MIPMAP_MODE_NEAREST;

    return vkCreateSampler(r->device, &si, NULL, out) == VK_SUCCESS;
}

static void write_descriptor_set(VkRenderer* r, VkDescriptorSet set, VkImageView view, VkSampler sampler) {
    VkDescriptorImageInfo ii = {0};
    ii.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    ii.imageView = view;
    ii.sampler = sampler;

    VkWriteDescriptorSet w = {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
    w.dstSet = set;
    w.dstBinding = 0;
    w.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    w.descriptorCount = 1;
    w.pImageInfo = &ii;
    vkUpdateDescriptorSets(r->device, 1, &w, 0, NULL);
}

static void destroy_texture_resources(VkRenderer* r, VkTexture* tex) {
    if (!tex) return;
    if (tex->descriptor_set != VK_NULL_HANDLE) {
        vkFreeDescriptorSets(r->device, r->descriptor_pool, 1, &tex->descriptor_set);
    }
    if (tex->sampler != VK_NULL_HANDLE) vkDestroySampler(r->device, tex->sampler, NULL);
    if (tex->view != VK_NULL_HANDLE)    vkDestroyImageView(r->device, tex->view, NULL);
    if (tex->ycbcr != VK_NULL_HANDLE && r->fnDestroyYcbcr) r->fnDestroyYcbcr(r->device, tex->ycbcr, NULL);
    if (tex->image != VK_NULL_HANDLE)   vkDestroyImage(r->device, tex->image, NULL);
    if (tex->memory != VK_NULL_HANDLE)  vkFreeMemory(r->device, tex->memory, NULL);
    if (tex->ahb != NULL)               AHardwareBuffer_release(tex->ahb);
    free(tex);
}

static bool track_live_texture(VkRenderer* r, VkTexture* tex) {
    if (!tex) return false;
    pthread_mutex_lock(&r->texture_mutex);
    if (r->live_texture_count >= r->live_texture_capacity) {
        uint32_t new_cap = r->live_texture_capacity ? r->live_texture_capacity * 2 : 64;
        VkTexture** next = realloc(r->live_textures, new_cap * sizeof(VkTexture*));
        if (!next) {
            pthread_mutex_unlock(&r->texture_mutex);
            return false;
        }
        r->live_textures = next;
        r->live_texture_capacity = new_cap;
    }
    r->live_textures[r->live_texture_count++] = tex;
    pthread_mutex_unlock(&r->texture_mutex);
    return true;
}

static void untrack_live_texture(VkRenderer* r, VkTexture* tex) {
    if (!tex) return;
    pthread_mutex_lock(&r->texture_mutex);
    for (uint32_t i = 0; i < r->live_texture_count; i++) {
        if (r->live_textures[i] == tex) {
            r->live_textures[i] = r->live_textures[--r->live_texture_count];
            r->live_textures[r->live_texture_count] = NULL;
            break;
        }
    }
    pthread_mutex_unlock(&r->texture_mutex);
}

static VkTexture* pop_live_texture(VkRenderer* r) {
    pthread_mutex_lock(&r->texture_mutex);
    VkTexture* tex = NULL;
    if (r->live_texture_count > 0) {
        tex = r->live_textures[--r->live_texture_count];
        r->live_textures[r->live_texture_count] = NULL;
    }
    pthread_mutex_unlock(&r->texture_mutex);
    return tex;
}

// ----------------------------------------------------------------------
// CPU-uploaded path
// ----------------------------------------------------------------------

typedef struct UploadCtx {
    VkBuffer staging;
    VkImage  dst;
    uint32_t w, h;
    bool     to_shader_read;
} UploadCtx;

static void upload_cmds(VkCommandBuffer cmd, void* user) {
    UploadCtx* u = (UploadCtx*)user;

    vkr_image_barrier(cmd, u->dst,
        VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
        VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
        0, VK_ACCESS_TRANSFER_WRITE_BIT);

    VkBufferImageCopy bic = {0};
    bic.bufferRowLength = 0;
    bic.bufferImageHeight = 0;
    bic.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    bic.imageSubresource.layerCount = 1;
    bic.imageExtent.width = u->w;
    bic.imageExtent.height = u->h;
    bic.imageExtent.depth = 1;
    vkCmdCopyBufferToImage(cmd, u->staging, u->dst,
                           VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &bic);

    if (u->to_shader_read) {
        vkr_image_barrier(cmd, u->dst,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
            VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT);
    }
}

static bool create_image_basic(VkRenderer* r, uint32_t w, uint32_t h, VkFormat fmt,
                               VkImageUsageFlags usage,
                               VkImage* out_img, VkDeviceMemory* out_mem) {
    VkImageCreateInfo ic = {VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
    ic.imageType = VK_IMAGE_TYPE_2D;
    ic.format = fmt;
    ic.extent.width = w;
    ic.extent.height = h;
    ic.extent.depth = 1;
    ic.mipLevels = 1;
    ic.arrayLayers = 1;
    ic.samples = VK_SAMPLE_COUNT_1_BIT;
    ic.tiling = VK_IMAGE_TILING_OPTIMAL;
    ic.usage = usage;
    ic.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    ic.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;

    if (vkCreateImage(r->device, &ic, NULL, out_img) != VK_SUCCESS) return false;

    VkMemoryRequirements mr;
    vkGetImageMemoryRequirements(r->device, *out_img, &mr);

    VkMemoryAllocateInfo ai = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    ai.allocationSize = mr.size;
    ai.memoryTypeIndex = vkr_find_memory_type(r, mr.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (ai.memoryTypeIndex == UINT32_MAX) {
        vkDestroyImage(r->device, *out_img, NULL);
        return false;
    }

    if (vkAllocateMemory(r->device, &ai, NULL, out_mem) != VK_SUCCESS) {
        vkDestroyImage(r->device, *out_img, NULL);
        return false;
    }
    vkBindImageMemory(r->device, *out_img, *out_mem, 0);
    return true;
}

VkTexture* vkr_texture_create_uploaded(VkRenderer* r, uint32_t width, uint32_t height,
                                       const void* data, size_t data_size, uint32_t stride_pixels) {
    if (width == 0 || height == 0) return NULL;

    VkTexture* t = calloc(1, sizeof(VkTexture));
    if (!t) return NULL;
    t->width = width;
    t->height = height;
    t->format = VK_FORMAT_B8G8R8A8_UNORM;

    VkImageUsageFlags usage = VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
    if (!create_image_basic(r, width, height, t->format, usage, &t->image, &t->memory)) {
        free(t);
        return NULL;
    }

    VkImageViewCreateInfo vi = {VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
    vi.image = t->image;
    vi.viewType = VK_IMAGE_VIEW_TYPE_2D;
    vi.format = t->format;
    vi.components.r = VK_COMPONENT_SWIZZLE_IDENTITY;
    vi.components.g = VK_COMPONENT_SWIZZLE_IDENTITY;
    vi.components.b = VK_COMPONENT_SWIZZLE_IDENTITY;
    vi.components.a = VK_COMPONENT_SWIZZLE_IDENTITY;
    vi.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    vi.subresourceRange.levelCount = 1;
    vi.subresourceRange.layerCount = 1;
    if (vkCreateImageView(r->device, &vi, NULL, &t->view) != VK_SUCCESS) {
        vkDestroyImage(r->device, t->image, NULL);
        vkFreeMemory(r->device, t->memory, NULL);
        free(t);
        return NULL;
    }

    if (!create_sampler(r, VK_NULL_HANDLE, &t->sampler)) {
        vkDestroyImageView(r->device, t->view, NULL);
        vkDestroyImage(r->device, t->image, NULL);
        vkFreeMemory(r->device, t->memory, NULL);
        free(t);
        return NULL;
    }

    t->descriptor_set = vkr_alloc_descriptor_set(r);
    if (t->descriptor_set == VK_NULL_HANDLE) {
        vkDestroySampler(r->device, t->sampler, NULL);
        vkDestroyImageView(r->device, t->view, NULL);
        vkDestroyImage(r->device, t->image, NULL);
        vkFreeMemory(r->device, t->memory, NULL);
        free(t);
        return NULL;
    }
    write_descriptor_set(r, t->descriptor_set, t->view, t->sampler);

    if (data && data_size > 0) {
        vkr_texture_update(r, t, width, height, data, data_size, stride_pixels);
    } else {
        // No initial data — transition the image to SHADER_READ so it's safe to sample as black.
        VkCommandBufferAllocateInfo ai = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
        ai.commandPool = r->cmd_pool;
        ai.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        ai.commandBufferCount = 1;
        VkCommandBuffer cmd;
        VK_CHECK(vkAllocateCommandBuffers(r->device, &ai, &cmd));

        VkCommandBufferBeginInfo bi = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
        bi.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
        VK_CHECK(vkBeginCommandBuffer(cmd, &bi));
        vkr_image_barrier(cmd, t->image,
            VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
            0, VK_ACCESS_SHADER_READ_BIT);
        VK_CHECK(vkEndCommandBuffer(cmd));

        VkSubmitInfo si = {VK_STRUCTURE_TYPE_SUBMIT_INFO};
        si.commandBufferCount = 1;
        si.pCommandBuffers = &cmd;
        VkFenceCreateInfo fi = {VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
        VkFence fence;
        VK_CHECK(vkCreateFence(r->device, &fi, NULL, &fence));
        pthread_mutex_lock(&r->queue_mutex);
        VK_CHECK(vkQueueSubmit(r->graphics_queue, 1, &si, fence));
        pthread_mutex_unlock(&r->queue_mutex);
        vkWaitForFences(r->device, 1, &fence, VK_TRUE, UINT64_MAX);
        vkDestroyFence(r->device, fence, NULL);
        vkFreeCommandBuffers(r->device, r->cmd_pool, 1, &cmd);
    }
    t->layout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    t->ready = true;
    if (!track_live_texture(r, t)) {
        destroy_texture_resources(r, t);
        return NULL;
    }
    return t;
}

bool vkr_texture_update(VkRenderer* r, VkTexture* tex, uint32_t width, uint32_t height,
                        const void* data, size_t data_size, uint32_t stride_pixels) {
    if (!tex || tex->external || !data || data_size == 0) return false;
    if (width != tex->width || height != tex->height) {
        // Caller is expected to size-match. Reject mismatches to avoid silent corruption.
        VK_LOGW("vkr_texture_update size mismatch (have %ux%u, got %ux%u)",
                tex->width, tex->height, width, height);
        return false;
    }

    // BGRA8 = 4 bytes per pixel. Caller provides stride_pixels (per-row pixel count).
    size_t needed = (size_t)width * height * 4;
    if (stride_pixels == 0) stride_pixels = width;

    VkStagingSlot* slot = vkr_staging_pool_acquire(r, needed);
    if (!slot) {
        VK_LOGE("vkr_texture_update: staging pool acquire failed");
        return false;
    }

    if (stride_pixels == width) {
        memcpy(slot->mapped, data, needed);
    } else {
        const uint8_t* src = (const uint8_t*)data;
        uint8_t* dst = (uint8_t*)slot->mapped;
        size_t row = (size_t)width * 4;
        size_t src_pitch = (size_t)stride_pixels * 4;
        for (uint32_t y = 0; y < height; y++) {
            memcpy(dst + y * row, src + y * src_pitch, row);
        }
    }

    VkCommandBufferBeginInfo cbi = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
    cbi.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    if (vkBeginCommandBuffer(slot->cmd, &cbi) != VK_SUCCESS) {
        vkr_staging_pool_release(slot);
        return false;
    }

    UploadCtx ctx = { slot->buffer, tex->image, width, height, true };
    upload_cmds(slot->cmd, &ctx);

    if (vkEndCommandBuffer(slot->cmd) != VK_SUCCESS) {
        vkr_staging_pool_release(slot);
        return false;
    }

    VkSubmitInfo si = {VK_STRUCTURE_TYPE_SUBMIT_INFO};
    si.commandBufferCount = 1;
    si.pCommandBuffers = &slot->cmd;

    // Reset fence here, not in acquire — guarantees that the only path that leaves a fence
    // unsignaled is one where vkQueueSubmit also runs to take ownership of it.
    vkResetFences(r->device, 1, &slot->fence);

    pthread_mutex_lock(&r->queue_mutex);
    VkResult sr = vkQueueSubmit(r->graphics_queue, 1, &si, slot->fence);
    pthread_mutex_unlock(&r->queue_mutex);
    if (sr != VK_SUCCESS) {
        VK_LOGE("vkr_texture_update: vkQueueSubmit -> %d", sr);
        // Submit failed but we already reset the fence, so it's unsignaled and would deadlock
        // the next acquire. Replace with a signaled fence. (Submit failures usually mean
        // device-lost; the renderer is going to need a restart anyway.)
        vkDestroyFence(r->device, slot->fence, NULL);
        VkFenceCreateInfo rfi = {VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
        rfi.flags = VK_FENCE_CREATE_SIGNALED_BIT;
        vkCreateFence(r->device, &rfi, NULL, &slot->fence);
        vkr_staging_pool_release(slot);
        return false;
    }

    // The barrier emitted by upload_cmds (TRANSFER_WRITE → SHADER_READ, dstStage=
    // FRAGMENT_SHADER) extends into all subsequent submits on the same queue, so the next
    // render submit will observe the writes without any additional renderer-side barrier.
    tex->layout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    vkr_staging_pool_release(slot);
    return true;
}

// ----------------------------------------------------------------------
// AHB import path (zero-copy)
// ----------------------------------------------------------------------

VkTexture* vkr_texture_import_ahb(VkRenderer* r, AHardwareBuffer* ahb, bool transfer_ownership) {
    if (!r->ext_ahb || !r->fnGetAhbProps || !ahb) return NULL;

    VkAndroidHardwareBufferFormatPropertiesANDROID format_props = {
        VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_FORMAT_PROPERTIES_ANDROID
    };
    VkAndroidHardwareBufferPropertiesANDROID props = {
        VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_PROPERTIES_ANDROID
    };
    props.pNext = &format_props;
    if (r->fnGetAhbProps(r->device, ahb, &props) != VK_SUCCESS) {
        VK_LOGW("vkGetAndroidHardwareBufferPropertiesANDROID failed");
        return NULL;
    }

    AHardwareBuffer_Desc desc = {0};
    AHardwareBuffer_describe(ahb, &desc);

    VkTexture* t = calloc(1, sizeof(VkTexture));
    if (!t) return NULL;
    t->width = desc.width;
    t->height = desc.height;
    t->external = true;
    t->ahb = transfer_ownership ? ahb : NULL;
    if (transfer_ownership) AHardwareBuffer_acquire(ahb);

    VkExternalFormatANDROID extfmt = {VK_STRUCTURE_TYPE_EXTERNAL_FORMAT_ANDROID};
    extfmt.externalFormat = format_props.externalFormat;

    VkExternalMemoryImageCreateInfo emi = {VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO};
    emi.handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID;
    if (format_props.format == VK_FORMAT_UNDEFINED) emi.pNext = &extfmt;

    VkImageCreateInfo ic = {VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
    ic.pNext = &emi;
    ic.imageType = VK_IMAGE_TYPE_2D;
    ic.format = format_props.format;  // may be UNDEFINED for vendor formats
    ic.extent.width = desc.width;
    ic.extent.height = desc.height;
    ic.extent.depth = 1;
    ic.mipLevels = 1;
    ic.arrayLayers = 1;
    ic.samples = VK_SAMPLE_COUNT_1_BIT;
    ic.tiling = VK_IMAGE_TILING_OPTIMAL;
    ic.usage = VK_IMAGE_USAGE_SAMPLED_BIT;
    ic.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    ic.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;

    if (vkCreateImage(r->device, &ic, NULL, &t->image) != VK_SUCCESS) {
        VK_LOGW("AHB vkCreateImage failed");
        if (t->ahb) AHardwareBuffer_release(t->ahb);
        free(t);
        return NULL;
    }
    t->format = format_props.format;

    // Dedicated allocation pulls memory from the AHB handle.
    VkImportAndroidHardwareBufferInfoANDROID import = {
        VK_STRUCTURE_TYPE_IMPORT_ANDROID_HARDWARE_BUFFER_INFO_ANDROID
    };
    import.buffer = ahb;

    VkMemoryDedicatedAllocateInfo dedicated = {VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO};
    dedicated.image = t->image;
    dedicated.pNext = &import;

    VkMemoryAllocateInfo mai = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    mai.pNext = &dedicated;
    mai.allocationSize = props.allocationSize;
    mai.memoryTypeIndex = vkr_find_memory_type(r, props.memoryTypeBits, 0);
    if (mai.memoryTypeIndex == UINT32_MAX) {
        VK_LOGW("AHB no compatible memory type");
        vkDestroyImage(r->device, t->image, NULL);
        if (t->ahb) AHardwareBuffer_release(t->ahb);
        free(t);
        return NULL;
    }

    if (vkAllocateMemory(r->device, &mai, NULL, &t->memory) != VK_SUCCESS) {
        VK_LOGW("AHB vkAllocateMemory failed");
        vkDestroyImage(r->device, t->image, NULL);
        if (t->ahb) AHardwareBuffer_release(t->ahb);
        free(t);
        return NULL;
    }
    vkBindImageMemory(r->device, t->image, t->memory, 0);

    // Ycbcr conversion is required when format is UNDEFINED and we use externalFormat.
    if (format_props.format == VK_FORMAT_UNDEFINED && r->ext_ycbcr) {
        VkSamplerYcbcrConversionCreateInfo yi = {
            VK_STRUCTURE_TYPE_SAMPLER_YCBCR_CONVERSION_CREATE_INFO
        };
        yi.pNext = &extfmt;
        yi.format = VK_FORMAT_UNDEFINED;
        yi.ycbcrModel = format_props.suggestedYcbcrModel;
        yi.ycbcrRange = format_props.suggestedYcbcrRange;
        yi.components = format_props.samplerYcbcrConversionComponents;
        yi.xChromaOffset = format_props.suggestedXChromaOffset;
        yi.yChromaOffset = format_props.suggestedYChromaOffset;
        yi.chromaFilter = VK_FILTER_LINEAR;
        yi.forceExplicitReconstruction = VK_FALSE;
        if (!r->fnCreateYcbcr || r->fnCreateYcbcr(r->device, &yi, NULL, &t->ycbcr) != VK_SUCCESS) {
            VK_LOGW("vkCreateSamplerYcbcrConversion failed");
            vkDestroyImage(r->device, t->image, NULL);
            vkFreeMemory(r->device, t->memory, NULL);
            if (t->ahb) AHardwareBuffer_release(t->ahb);
            free(t);
            return NULL;
        }
    }

    VkSamplerYcbcrConversionInfo yview = {VK_STRUCTURE_TYPE_SAMPLER_YCBCR_CONVERSION_INFO};
    yview.conversion = t->ycbcr;

    VkImageViewCreateInfo vi = {VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
    if (t->ycbcr != VK_NULL_HANDLE) vi.pNext = &yview;
    vi.image = t->image;
    vi.viewType = VK_IMAGE_VIEW_TYPE_2D;
    vi.format = format_props.format != VK_FORMAT_UNDEFINED
                ? format_props.format : VK_FORMAT_UNDEFINED;
    vi.components = format_props.samplerYcbcrConversionComponents;
    vi.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    vi.subresourceRange.levelCount = 1;
    vi.subresourceRange.layerCount = 1;
    if (vkCreateImageView(r->device, &vi, NULL, &t->view) != VK_SUCCESS) {
        VK_LOGW("AHB vkCreateImageView failed");
        if (t->ycbcr && r->fnDestroyYcbcr) r->fnDestroyYcbcr(r->device, t->ycbcr, NULL);
        vkDestroyImage(r->device, t->image, NULL);
        vkFreeMemory(r->device, t->memory, NULL);
        if (t->ahb) AHardwareBuffer_release(t->ahb);
        free(t);
        return NULL;
    }

    if (!create_sampler(r, t->ycbcr, &t->sampler)) {
        VK_LOGW("AHB create_sampler failed");
        vkDestroyImageView(r->device, t->view, NULL);
        if (t->ycbcr && r->fnDestroyYcbcr) r->fnDestroyYcbcr(r->device, t->ycbcr, NULL);
        vkDestroyImage(r->device, t->image, NULL);
        vkFreeMemory(r->device, t->memory, NULL);
        if (t->ahb) AHardwareBuffer_release(t->ahb);
        free(t);
        return NULL;
    }

    t->descriptor_set = vkr_alloc_descriptor_set(r);
    if (t->descriptor_set == VK_NULL_HANDLE) {
        vkDestroySampler(r->device, t->sampler, NULL);
        vkDestroyImageView(r->device, t->view, NULL);
        if (t->ycbcr && r->fnDestroyYcbcr) r->fnDestroyYcbcr(r->device, t->ycbcr, NULL);
        vkDestroyImage(r->device, t->image, NULL);
        vkFreeMemory(r->device, t->memory, NULL);
        if (t->ahb) AHardwareBuffer_release(t->ahb);
        free(t);
        return NULL;
    }
    write_descriptor_set(r, t->descriptor_set, t->view, t->sampler);

    // Transition to SHADER_READ layout once.
    VkCommandBufferAllocateInfo cai = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
    cai.commandPool = r->cmd_pool;
    cai.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    cai.commandBufferCount = 1;
    VkCommandBuffer cmd;
    VK_CHECK(vkAllocateCommandBuffers(r->device, &cai, &cmd));
    VkCommandBufferBeginInfo cbi = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
    cbi.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    VK_CHECK(vkBeginCommandBuffer(cmd, &cbi));
    vkr_image_barrier(cmd, t->image,
        VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
        VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
        0, VK_ACCESS_SHADER_READ_BIT);
    VK_CHECK(vkEndCommandBuffer(cmd));
    VkSubmitInfo si = {VK_STRUCTURE_TYPE_SUBMIT_INFO};
    si.commandBufferCount = 1;
    si.pCommandBuffers = &cmd;
    VkFenceCreateInfo fi = {VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
    VkFence fence;
    VK_CHECK(vkCreateFence(r->device, &fi, NULL, &fence));
    pthread_mutex_lock(&r->queue_mutex);
    VK_CHECK(vkQueueSubmit(r->graphics_queue, 1, &si, fence));
    pthread_mutex_unlock(&r->queue_mutex);
    vkWaitForFences(r->device, 1, &fence, VK_TRUE, UINT64_MAX);
    vkDestroyFence(r->device, fence, NULL);
    vkFreeCommandBuffers(r->device, r->cmd_pool, 1, &cmd);

    t->layout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    t->ready = true;
    if (!track_live_texture(r, t)) {
        destroy_texture_resources(r, t);
        return NULL;
    }
    return t;
}

// ----------------------------------------------------------------------
// Destruction
// ----------------------------------------------------------------------

void vkr_texture_destroy(VkRenderer* r, VkTexture* tex) {
    if (!tex) return;
    untrack_live_texture(r, tex);
    destroy_texture_resources(r, tex);
}

void vkr_texture_destroy_all_live(VkRenderer* r) {
    for (;;) {
        VkTexture* tex = pop_live_texture(r);
        if (!tex) break;
        destroy_texture_resources(r, tex);
    }
}

void vkr_texture_schedule_destroy(VkRenderer* r, VkTexture* tex) {
    if (!tex) return;
    pthread_mutex_lock(&r->scene_mutex);

    if (tex->destroy_scheduled) {
        pthread_mutex_unlock(&r->scene_mutex);
        return;
    }
    tex->destroy_scheduled = true;

    // Defensive: drop any references in the live scene state.
    for (uint32_t i = 0; i < r->scene.window_count; i++) {
        if (r->scene.windows[i].texture == tex) {
            r->scene.windows[i].texture = NULL;
        }
    }
    if (r->scene.cursor_texture == tex) r->scene.cursor_texture = NULL;

    uint32_t retire_slot = (r->graveyard_index + VK_FRAMES_IN_FLIGHT)
                         % (VK_FRAMES_IN_FLIGHT + 1);
    VkGraveSlot* slot = &r->graveyard[retire_slot];
    if (slot->count >= slot->capacity) {
        uint32_t new_cap = slot->capacity ? slot->capacity * 2 : 16;
        VkTexture** ng = realloc(slot->textures, new_cap * sizeof(VkTexture*));
        if (!ng) {
            pthread_mutex_unlock(&r->scene_mutex);
            // As a last resort, leak rather than crash. Better than UAF.
            VK_LOGE("graveyard alloc failed; leaking texture %p", (void*)tex);
            return;
        }
        slot->textures = ng;
        slot->capacity = new_cap;
    }
    slot->textures[slot->count++] = tex;

    pthread_mutex_unlock(&r->scene_mutex);
}
