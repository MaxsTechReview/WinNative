#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "../vk_dispatch.h"

#ifdef __cplusplus
extern "C" {
#endif

#define VKR_LSFG_MAX_GENERATIONS 3u

typedef struct VkrLsfg VkrLsfg;

VkrLsfg* vkr_lsfg_create(VkDevice device, VkPhysicalDevice physical_device,
                         const char* cache_path);
void vkr_lsfg_destroy(VkrLsfg* lsfg);

void vkr_lsfg_configure(VkrLsfg* lsfg, uint32_t multiplier, uint32_t target_rate,
                        float flow_scale);

bool vkr_lsfg_needs_rebuild(const VkrLsfg* lsfg, uint32_t width, uint32_t height,
                            VkFormat format);

bool vkr_lsfg_prepare(VkrLsfg* lsfg, uint32_t width, uint32_t height, VkFormat format);

uint32_t vkr_lsfg_plan(VkrLsfg* lsfg, uint32_t capacity);

void vkr_lsfg_process(VkrLsfg* lsfg, VkCommandBuffer cmd, VkImage source,
                      uint32_t width, uint32_t height);

uint32_t vkr_lsfg_generated_count(const VkrLsfg* lsfg);

void vkr_lsfg_generate_into(VkrLsfg* lsfg, VkCommandBuffer cmd, uint32_t generation,
                            uint32_t target_index, VkImage target_image, VkImageView target_view,
                            uint32_t width, uint32_t height);

// Call whenever the composite targets are recreated: the generate pass caches the last view it
// bound per target slot and would otherwise keep descriptors pointing at destroyed views.
void vkr_lsfg_forget_targets(VkrLsfg* lsfg);

void vkr_lsfg_reset(VkrLsfg* lsfg);

#ifdef __cplusplus
}
#endif
