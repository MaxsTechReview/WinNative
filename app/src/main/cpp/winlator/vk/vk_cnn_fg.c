typedef struct CnnPC {
    int32_t  sx, sy;
    float    t;
    float    mvScale;
    uint32_t wBase;
    int32_t  cinT, coutT, flags;
} CnnPC;

#define CNN_GRID(w,h) ((uint32_t)(((w)+15u)/16u)), ((uint32_t)(((h)+15u)/16u)), 1u
#define CNN_FLOW_LEVELS 3

static bool cnn_wanted(void) {
    char v[PROP_VALUE_MAX] = {0};
    if (__system_property_get("debug.winnative.fgcnn", v) > 0 &&
        (v[0] == '0' || v[0] == 'f' || v[0] == 'n')) return false;
    return true;
}

static void cnn_barrier_ml(VkCommandBuffer cmd, VkImage image, uint32_t layers,
                           VkImageLayout from, VkImageLayout to,
                           VkPipelineStageFlags src_stage, VkPipelineStageFlags dst_stage,
                           VkAccessFlags src_access, VkAccessFlags dst_access) {
    VkImageMemoryBarrier b = {VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
    b.oldLayout = from; b.newLayout = to;
    b.srcAccessMask = src_access; b.dstAccessMask = dst_access;
    b.srcQueueFamilyIndex = b.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    b.image = image;
    b.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    b.subresourceRange.levelCount = 1; b.subresourceRange.layerCount = layers;
    vkCmdPipelineBarrier(cmd, src_stage, dst_stage, 0, 0, NULL, 0, NULL, 1, &b);
}
static inline void cnn_to_read(VkCommandBuffer cmd, VkImage im, uint32_t layers) {
    cnn_barrier_ml(cmd, im, layers, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
        VK_ACCESS_SHADER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT);
}
static inline void cnn_to_write(VkCommandBuffer cmd, VkImage im, uint32_t layers) {
    cnn_barrier_ml(cmd, im, layers, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_GENERAL,
        VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
        0, VK_ACCESS_SHADER_WRITE_BIT);
}

typedef struct CnnBind { uint32_t binding; VkDescriptorType type; } CnnBind;

static VkDescriptorSetLayout cnn_make_dsl(VkRenderer* r, const CnnBind* b, uint32_t n) {
    VkDescriptorSetLayoutBinding lb[24]; memset(lb, 0, sizeof(lb));
    for (uint32_t i = 0; i < n; i++) {
        lb[i].binding = b[i].binding;
        lb[i].descriptorType = b[i].type;
        lb[i].descriptorCount = 1;
        lb[i].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
    }
    VkDescriptorSetLayoutCreateInfo ci = {VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
    ci.bindingCount = n; ci.pBindings = lb;
    VkDescriptorSetLayout l = VK_NULL_HANDLE;
    if (vkCreateDescriptorSetLayout(r->device, &ci, NULL, &l) != VK_SUCCESS) return VK_NULL_HANDLE;
    return l;
}

static bool cnn_make_pipe(VkRenderer* r, const uint32_t* spv, size_t spvLen,
                          VkDescriptorSetLayout dsl, VkPipelineLayout* outPL, VkPipeline* outPipe) {
    VkPushConstantRange pcr = { VK_SHADER_STAGE_COMPUTE_BIT, 0, 32 };
    VkPipelineLayoutCreateInfo pli = {VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
    pli.setLayoutCount = 1; pli.pSetLayouts = &dsl;
    pli.pushConstantRangeCount = 1; pli.pPushConstantRanges = &pcr;
    if (vkCreatePipelineLayout(r->device, &pli, NULL, outPL) != VK_SUCCESS) return false;
    VkShaderModule mod = load_shader_module(r, spv, spvLen);
    if (!mod) return false;
    *outPipe = create_compute_pipeline(r, mod, *outPL);
    vkDestroyShaderModule(r->device, mod, NULL);
    return *outPipe != VK_NULL_HANDLE;
}

static void destroy_cnn_pipelines(VkRenderer* r) {
    VkPipelineSet* P = &r->pipelines;
    VkPipeline pipes[] = { P->cnn_pyramid_pipe, P->cnn_conv_pipe, P->cnn_cost9_pipe,
                           P->cnn_flowreg_pipe, P->cnn_warpfollow_pipe, P->cnn_generate_pipe };
    VkPipelineLayout pls[] = { P->cnn_pyramid_pl, P->cnn_conv_pl, P->cnn_cost9_pl,
                               P->cnn_flowreg_pl, P->cnn_warpfollow_pl, P->cnn_generate_pl };
    VkDescriptorSetLayout dsls[] = { P->cnn_pyramid_dsl, P->cnn_conv_dsl, P->cnn_cost9_dsl,
                                     P->cnn_flowreg_dsl, P->cnn_warpfollow_dsl, P->cnn_generate_dsl };
    for (int i = 0; i < 6; i++) {
        if (pipes[i]) vkDestroyPipeline(r->device, pipes[i], NULL);
        if (pls[i])   vkDestroyPipelineLayout(r->device, pls[i], NULL);
        if (dsls[i])  vkDestroyDescriptorSetLayout(r->device, dsls[i], NULL);
    }
    P->cnn_pyramid_pipe = P->cnn_conv_pipe = P->cnn_cost9_pipe =
        P->cnn_flowreg_pipe = P->cnn_warpfollow_pipe = P->cnn_generate_pipe = VK_NULL_HANDLE;
    P->cnn_pyramid_pl = P->cnn_conv_pl = P->cnn_cost9_pl =
        P->cnn_flowreg_pl = P->cnn_warpfollow_pl = P->cnn_generate_pl = VK_NULL_HANDLE;
    P->cnn_pyramid_dsl = P->cnn_conv_dsl = P->cnn_cost9_dsl =
        P->cnn_flowreg_dsl = P->cnn_warpfollow_dsl = P->cnn_generate_dsl = VK_NULL_HANDLE;
}

static bool create_cnn_pipelines(VkRenderer* r) {
    if (!r->fg_float16_supported) return false;
    VkPipelineSet* P = &r->pipelines;
    const VkDescriptorType S = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    const VkDescriptorType I = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    const VkDescriptorType B = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
    const VkDescriptorType U = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;

    const CnnBind pyr[]     = { {0,S},{1,I},{2,S} };
    const CnnBind conv[]    = { {0,S},{1,I},{3,S},{8,B} };
    const CnnBind cost9[]   = { {32,S},{33,S},{34,S},{35,S},{36,S},{37,S},{38,S},{39,S},{40,S},
                                {48,I},{49,I},{50,I},{8,B} };
    const CnnBind flowreg[] = { {32,S},{33,S},{34,S},{35,S},{36,S},{37,S},{48,I},{8,B} };
    const CnnBind warpf[]   = { {0,U},{32,S},{33,S},{34,S},{35,S},{36,S},{37,S},{48,I},{8,B} };
    const CnnBind gen[]     = { {32,S},{33,S},{34,S},{35,S},{36,S},{48,I} };

    P->cnn_pyramid_dsl    = cnn_make_dsl(r, pyr,     3);
    P->cnn_conv_dsl       = cnn_make_dsl(r, conv,    4);
    P->cnn_cost9_dsl      = cnn_make_dsl(r, cost9,  13);
    P->cnn_flowreg_dsl    = cnn_make_dsl(r, flowreg, 8);
    P->cnn_warpfollow_dsl = cnn_make_dsl(r, warpf,   9);
    P->cnn_generate_dsl   = cnn_make_dsl(r, gen,     6);
    if (!P->cnn_pyramid_dsl || !P->cnn_conv_dsl || !P->cnn_cost9_dsl ||
        !P->cnn_flowreg_dsl || !P->cnn_warpfollow_dsl || !P->cnn_generate_dsl) goto cnn_fail;

    if (!cnn_make_pipe(r, cnn_pyramid_comp, cnn_pyramid_comp_size,
                       P->cnn_pyramid_dsl, &P->cnn_pyramid_pl, &P->cnn_pyramid_pipe)) goto cnn_fail;
    if (!cnn_make_pipe(r, cnn_conv_comp, cnn_conv_comp_size,
                       P->cnn_conv_dsl, &P->cnn_conv_pl, &P->cnn_conv_pipe)) goto cnn_fail;
    if (!cnn_make_pipe(r, cnn_correlation_cost9_comp, cnn_correlation_cost9_comp_size,
                       P->cnn_cost9_dsl, &P->cnn_cost9_pl, &P->cnn_cost9_pipe)) goto cnn_fail;
    if (!cnn_make_pipe(r, cnn_flowreg_comp, cnn_flowreg_comp_size,
                       P->cnn_flowreg_dsl, &P->cnn_flowreg_pl, &P->cnn_flowreg_pipe)) goto cnn_fail;
    if (!cnn_make_pipe(r, cnn_correlation_warpfollow_comp, cnn_correlation_warpfollow_comp_size,
                       P->cnn_warpfollow_dsl, &P->cnn_warpfollow_pl, &P->cnn_warpfollow_pipe)) goto cnn_fail;
    if (!cnn_make_pipe(r, cnn_generate_comp, cnn_generate_comp_size,
                       P->cnn_generate_dsl, &P->cnn_generate_pl, &P->cnn_generate_pipe)) goto cnn_fail;

    VK_LOGI("CNN-FG pipelines built");
    return true;
cnn_fail:
    VK_LOGW("CNN-FG pipelines unavailable; classical flow only");
    destroy_cnn_pipelines(r);
    return false;
}

static bool cnn_make_ssbo(VkRenderer* r, int id, const void* data, size_t n) {
    VkBufferCreateInfo bc = {VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
    bc.size = n; bc.usage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
    bc.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    if (vkCreateBuffer(r->device, &bc, NULL, &r->fg_cnn.w[id]) != VK_SUCCESS) return false;
    VkMemoryRequirements mr; vkGetBufferMemoryRequirements(r->device, r->fg_cnn.w[id], &mr);
    VkMemoryAllocateInfo ai = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    ai.allocationSize = mr.size;
    ai.memoryTypeIndex = vkr_find_memory_type(r, mr.memoryTypeBits,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    if (ai.memoryTypeIndex == UINT32_MAX) return false;
    if (vkAllocateMemory(r->device, &ai, NULL, &r->fg_cnn.wMem[id]) != VK_SUCCESS) return false;
    vkBindBufferMemory(r->device, r->fg_cnn.w[id], r->fg_cnn.wMem[id], 0);
    void* p = NULL;
    if (vkMapMemory(r->device, r->fg_cnn.wMem[id], 0, n, 0, &p) != VK_SUCCESS) return false;
    memcpy(p, data, n);
    vkUnmapMemory(r->device, r->fg_cnn.wMem[id]);
    r->fg_cnn.wLen[id] = n;
    return true;
}

static bool cnn_make_img(VkRenderer* r, VkCnnImg* o, uint32_t w, uint32_t h,
                         VkFormat fmt, uint32_t layers, bool arrayView) {
    if (w < 1) w = 1; if (h < 1) h = 1; if (layers < 1) layers = 1;
    memset(o, 0, sizeof(*o));
    o->w = w; o->h = h; o->layers = layers;
    VkImageCreateInfo ic = {VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
    ic.imageType = VK_IMAGE_TYPE_2D; ic.format = fmt;
    ic.extent.width = w; ic.extent.height = h; ic.extent.depth = 1;
    ic.mipLevels = 1; ic.arrayLayers = layers;
    ic.samples = VK_SAMPLE_COUNT_1_BIT; ic.tiling = VK_IMAGE_TILING_OPTIMAL;
    ic.usage = VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
             | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
    ic.sharingMode = VK_SHARING_MODE_EXCLUSIVE; ic.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    if (vkCreateImage(r->device, &ic, NULL, &o->image) != VK_SUCCESS) return false;
    VkMemoryRequirements mr; vkGetImageMemoryRequirements(r->device, o->image, &mr);
    VkMemoryAllocateInfo ai = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    ai.allocationSize = mr.size;
    ai.memoryTypeIndex = vkr_find_memory_type(r, mr.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (ai.memoryTypeIndex == UINT32_MAX) return false;
    if (vkAllocateMemory(r->device, &ai, NULL, &o->memory) != VK_SUCCESS) return false;
    vkBindImageMemory(r->device, o->image, o->memory, 0);

    VkImageViewCreateInfo vi = {VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
    vi.image = o->image; vi.format = fmt;
    vi.viewType = arrayView ? VK_IMAGE_VIEW_TYPE_2D_ARRAY : VK_IMAGE_VIEW_TYPE_2D;
    vi.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    vi.subresourceRange.levelCount = 1;
    vi.subresourceRange.baseArrayLayer = 0;
    vi.subresourceRange.layerCount = arrayView ? layers : 1;
    if (vkCreateImageView(r->device, &vi, NULL, &o->view) != VK_SUCCESS) return false;

    uint32_t nlv = layers < 4 ? layers : 4;
    for (uint32_t k = 0; k < nlv; k++) {
        VkImageViewCreateInfo lv = {VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
        lv.image = o->image; lv.format = fmt; lv.viewType = VK_IMAGE_VIEW_TYPE_2D;
        lv.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        lv.subresourceRange.levelCount = 1;
        lv.subresourceRange.baseArrayLayer = k; lv.subresourceRange.layerCount = 1;
        if (vkCreateImageView(r->device, &lv, NULL, &o->layerView[k]) != VK_SUCCESS) return false;
    }
    return true;
}

static void cnn_free_img(VkRenderer* r, VkCnnImg* o) {
    for (int k = 0; k < 4; k++) if (o->layerView[k]) vkDestroyImageView(r->device, o->layerView[k], NULL);
    if (o->view)   vkDestroyImageView(r->device, o->view, NULL);
    if (o->image)  vkDestroyImage(r->device, o->image, NULL);
    if (o->memory) vkFreeMemory(r->device, o->memory, NULL);
    memset(o, 0, sizeof(*o));
}

static void fg_destroy_cnn_resources(VkRenderer* r) {
    if (!r->device) return;
    VkFgCnn* C = &r->fg_cnn;
    for (int i = 0; i < 64; i++) {
        if (C->w[i])    vkDestroyBuffer(r->device, C->w[i], NULL);
        if (C->wMem[i]) vkFreeMemory(r->device, C->wMem[i], NULL);
        C->w[i] = VK_NULL_HANDLE; C->wMem[i] = VK_NULL_HANDLE; C->wLen[i] = 0;
    }
    if (C->ubo)    { vkDestroyBuffer(r->device, C->ubo, NULL); C->ubo = VK_NULL_HANDLE; }
    if (C->uboMem) { vkFreeMemory(r->device, C->uboMem, NULL); C->uboMem = VK_NULL_HANDLE; }
    VkCnnFeatSet* sets[2] = { &C->featPrev, &C->featCurr };
    for (int s = 0; s < 2; s++)
        for (int L = 0; L < CNN_LEVELS; L++) {
            cnn_free_img(r, &sets[s]->luma[L]);   cnn_free_img(r, &sets[s]->feat4a[L]);
            cnn_free_img(r, &sets[s]->feat4b[L]);  cnn_free_img(r, &sets[s]->feat8[L]);
        }
    for (int L = 0; L < CNN_LEVELS; L++) {
        cnn_free_img(r, &C->feat8_pair[L]); cnn_free_img(r, &C->dpair[L]);
        cnn_free_img(r, &C->hG0[L]);  cnn_free_img(r, &C->hG1[L]);
        cnn_free_img(r, &C->hG23[L]); cnn_free_img(r, &C->hG4[L]);
        cnn_free_img(r, &C->hD0[L]);  cnn_free_img(r, &C->hD1[L]);
        cnn_free_img(r, &C->hD2[L]);  cnn_free_img(r, &C->hD3[L]);
        cnn_free_img(r, &C->hD5[L]);  cnn_free_img(r, &C->hD6[L]);
        cnn_free_img(r, &C->hD7[L]);  cnn_free_img(r, &C->hD8[L]);
        cnn_free_img(r, &C->flowMid[L]); cnn_free_img(r, &C->flowRef[L]);
    }
    cnn_free_img(r, &C->occ); cnn_free_img(r, &C->seedBlack); cnn_free_img(r, &C->dummy);
    for (int pi = 0; pi < CNN_POOLS; pi++)
        if (C->pool[pi]) { vkDestroyDescriptorPool(r->device, C->pool[pi], NULL); C->pool[pi] = VK_NULL_HANDLE; }
    C->ready = false;
}

static bool fg_create_cnn_resources(VkRenderer* r, uint32_t w, uint32_t h) {
    const VkFormat R8 = VK_FORMAT_R8_UNORM, RGBA8 = VK_FORMAT_R8G8B8A8_UNORM,
                   F16 = VK_FORMAT_R16G16B16A16_SFLOAT;
    VkFgCnn* C = &r->fg_cnn;

    float fs = r->fg_flow_scale >= 0.2f ? (r->fg_flow_scale <= 1.0f ? r->fg_flow_scale : 1.0f) : 0.5f;
    uint32_t mw = (uint32_t)((float)w * fs); if (mw < 1u) mw = 1u;
    uint32_t mh = (uint32_t)((float)h * fs); if (mh < 1u) mh = 1u;
    uint32_t lw[CNN_LEVELS], lh[CNN_LEVELS];
    for (int L = 0; L < CNN_LEVELS; L++) {
        lw[L] = (L == 0) ? mw : (lw[L-1] > 1 ? lw[L-1] / 2 : 1u);
        lh[L] = (L == 0) ? mh : (lh[L-1] > 1 ? lh[L-1] / 2 : 1u);
    }

    VkDescriptorPoolSize ps[] = {
        { VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 4096u },
        { VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,          1024u },
        { VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,           64u },
        { VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,          512u },
    };
    VkDescriptorPoolCreateInfo dpc = {VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
    dpc.maxSets = 1024u; dpc.poolSizeCount = 4; dpc.pPoolSizes = ps;
    C->curPool = 0;
    for (int pi = 0; pi < CNN_POOLS; pi++)
        if (vkCreateDescriptorPool(r->device, &dpc, NULL, &C->pool[pi]) != VK_SUCCESS) return false;

    #define CNN_W(ID) if (!cnn_make_ssbo(r, ID, wnfg_##ID##_weights, (size_t)wnfg_##ID##_weights_size)) return false
    CNN_W(05); CNN_W(06); CNN_W(07); CNN_W(14); CNN_W(20); CNN_W(21); CNN_W(22);
    CNN_W(24); CNN_W(25); CNN_W(26); CNN_W(27); CNN_W(28); CNN_W(29);
    CNN_W(36); CNN_W(37); CNN_W(42); CNN_W(51);
    #undef CNN_W

    {
        float ubo[4] = { 1.0f, 0.5f, 0.5f, 0.0f };
        VkBufferCreateInfo bc = {VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
        bc.size = sizeof(ubo); bc.usage = VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
        bc.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        if (vkCreateBuffer(r->device, &bc, NULL, &C->ubo) != VK_SUCCESS) return false;
        VkMemoryRequirements mr; vkGetBufferMemoryRequirements(r->device, C->ubo, &mr);
        VkMemoryAllocateInfo ai = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
        ai.allocationSize = mr.size;
        ai.memoryTypeIndex = vkr_find_memory_type(r, mr.memoryTypeBits,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        if (ai.memoryTypeIndex == UINT32_MAX) return false;
        if (vkAllocateMemory(r->device, &ai, NULL, &C->uboMem) != VK_SUCCESS) return false;
        vkBindBufferMemory(r->device, C->ubo, C->uboMem, 0);
        void* p = NULL;
        if (vkMapMemory(r->device, C->uboMem, 0, sizeof(ubo), 0, &p) != VK_SUCCESS) return false;
        memcpy(p, ubo, sizeof(ubo)); vkUnmapMemory(r->device, C->uboMem);
    }

    uint32_t f2w[CNN_LEVELS], f2h[CNN_LEVELS], fw[CNN_LEVELS], fh[CNN_LEVELS];
    for (int L = 0; L < CNN_LEVELS; L++) {
        f2w[L] = lw[L] > 1 ? lw[L] / 2 : 1u; f2h[L] = lh[L] > 1 ? lh[L] / 2 : 1u;
        fw[L]  = f2w[L] > 1 ? f2w[L] / 2 : 1u; fh[L]  = f2h[L] > 1 ? f2h[L] / 2 : 1u;
    }

    VkCnnFeatSet* fsets[2] = { &C->featPrev, &C->featCurr };
    for (int s = 0; s < 2; s++)
        for (int L = 0; L < CNN_LEVELS; L++) {
            if (!cnn_make_img(r, &fsets[s]->luma[L],   lw[L],  lh[L],  R8,    1, false)) return false;
            if (!cnn_make_img(r, &fsets[s]->feat4a[L], f2w[L], f2h[L], RGBA8, 1, true))  return false;
            if (!cnn_make_img(r, &fsets[s]->feat4b[L], f2w[L], f2h[L], RGBA8, 1, true))  return false;
            if (!cnn_make_img(r, &fsets[s]->feat8[L],  fw[L],  fh[L],  RGBA8, 2, true))  return false;
        }
    for (int L = 0; L < CNN_LEVELS; L++) {
        if (!cnn_make_img(r, &C->feat8_pair[L], fw[L], fh[L], RGBA8, 4, true)) return false;
        if (!cnn_make_img(r, &C->hG0[L],  fw[L], fh[L], RGBA8, 2, true)) return false;
        if (!cnn_make_img(r, &C->hG1[L],  fw[L], fh[L], RGBA8, 2, true)) return false;
        if (!cnn_make_img(r, &C->hG23[L], fw[L], fh[L], RGBA8, 4, true)) return false;
        if (!cnn_make_img(r, &C->hG4[L],  fw[L], fh[L], RGBA8, 2, true)) return false;
        if (!cnn_make_img(r, &C->hD0[L],  fw[L], fh[L], RGBA8, 3, true)) return false;
        if (!cnn_make_img(r, &C->hD1[L],  fw[L], fh[L], RGBA8, 3, true)) return false;
        if (!cnn_make_img(r, &C->hD2[L],  fw[L], fh[L], RGBA8, 2, true)) return false;
        if (!cnn_make_img(r, &C->hD3[L],  fw[L], fh[L], RGBA8, 1, true)) return false;
        if (!cnn_make_img(r, &C->hD5[L],  fw[L], fh[L], RGBA8, 1, true)) return false;
        if (!cnn_make_img(r, &C->hD6[L],  fw[L], fh[L], RGBA8, 2, true)) return false;
        if (!cnn_make_img(r, &C->hD7[L],  fw[L], fh[L], RGBA8, 1, true)) return false;
        if (!cnn_make_img(r, &C->hD8[L],  fw[L], fh[L], RGBA8, 1, true)) return false;
        if (!cnn_make_img(r, &C->dpair[L], fw[L], fh[L], RGBA8, 2, true)) return false;
        if (!cnn_make_img(r, &C->flowMid[L], fw[L], fh[L], F16, 1, false)) return false;
        if (!cnn_make_img(r, &C->flowRef[L], fw[L], fh[L], F16, 1, false)) return false;
    }
    if (!cnn_make_img(r, &C->occ,       mw, mh, F16,   1, false)) return false;
    if (!cnn_make_img(r, &C->seedBlack, mw, mh, F16,   1, false)) return false;
    if (!cnn_make_img(r, &C->dummy,      1,  1, RGBA8, 1, true))  return false;

    C->ready = true;
    VK_LOGI("CNN-FG resources allocated (L0 %ux%u, %d levels, fs=%.2f)", mw, mh, CNN_LEVELS, (double)fs);
    return true;
}

static VkDescriptorSet cnn_alloc(VkRenderer* r, VkDescriptorSetLayout dsl) {
    VkDescriptorSetAllocateInfo a = {VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
    a.descriptorPool = r->fg_cnn.pool[r->fg_cnn.curPool]; a.descriptorSetCount = 1; a.pSetLayouts = &dsl;
    VkDescriptorSet ds = VK_NULL_HANDLE;
    if (vkAllocateDescriptorSets(r->device, &a, &ds) != VK_SUCCESS) return VK_NULL_HANDLE;
    return ds;
}
static inline VkWriteDescriptorSet cnn_wimg(VkDescriptorSet ds, uint32_t b, VkDescriptorType t,
                                            const VkDescriptorImageInfo* ii) {
    VkWriteDescriptorSet w = {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
    w.dstSet = ds; w.dstBinding = b; w.descriptorCount = 1; w.descriptorType = t; w.pImageInfo = ii;
    return w;
}
static inline VkWriteDescriptorSet cnn_wbuf(VkDescriptorSet ds, uint32_t b, VkDescriptorType t,
                                            const VkDescriptorBufferInfo* bi) {
    VkWriteDescriptorSet w = {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
    w.dstSet = ds; w.dstBinding = b; w.descriptorCount = 1; w.descriptorType = t; w.pBufferInfo = bi;
    return w;
}

static void cnn_conv_dispatch(VkRenderer* r, VkCommandBuffer cmd,
                              VkImageView srcArr, VkImageView lumaR8, VkImageView dstArr,
                              int wnfgId, int cinT, int coutT, int flags, uint32_t dW, uint32_t dH) {
    VkPipelineSet* P = &r->pipelines;
    VkDescriptorSet ds = cnn_alloc(r, P->cnn_conv_dsl); if (!ds) return;
    VkDescriptorImageInfo s0 = {r->fg_sampler, srcArr, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
    VkDescriptorImageInfo i1 = {VK_NULL_HANDLE, dstArr, VK_IMAGE_LAYOUT_GENERAL};
    VkDescriptorImageInfo s3 = {r->fg_sampler, lumaR8, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
    VkDescriptorBufferInfo b8 = {r->fg_cnn.w[wnfgId], 0, r->fg_cnn.wLen[wnfgId]};
    VkWriteDescriptorSet w[4] = {
        cnn_wimg(ds,0,VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,&s0),
        cnn_wimg(ds,1,VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,&i1),
        cnn_wimg(ds,3,VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,&s3),
        cnn_wbuf(ds,8,VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,&b8),
    };
    vkUpdateDescriptorSets(r->device, 4, w, 0, NULL);
    CnnPC pc = {0}; pc.sx=(int32_t)dW; pc.sy=(int32_t)dH; pc.t=0.5f; pc.mvScale=1.0f;
    pc.cinT=cinT; pc.coutT=coutT; pc.flags=flags;
    vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, P->cnn_conv_pipe);
    vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, P->cnn_conv_pl, 0, 1, &ds, 0, NULL);
    vkCmdPushConstants(cmd, P->cnn_conv_pl, VK_SHADER_STAGE_COMPUTE_BIT, 0, 32, &pc);
    vkCmdDispatch(cmd, CNN_GRID(dW, dH));
}

static void cnn_pyramid_dispatch(VkRenderer* r, VkCommandBuffer cmd,
                                 VkImageView srcView, VkImageView dstLuma,
                                 bool level0, uint32_t w, uint32_t h) {
    VkPipelineSet* P = &r->pipelines;
    VkDescriptorSet ds = cnn_alloc(r, P->cnn_pyramid_dsl); if (!ds) return;
    VkDescriptorImageInfo s0 = {r->fg_sampler, srcView, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
    VkDescriptorImageInfo i1 = {VK_NULL_HANDLE, dstLuma, VK_IMAGE_LAYOUT_GENERAL};
    VkDescriptorImageInfo s2 = {r->fg_sampler, srcView, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
    VkWriteDescriptorSet ws[3] = {
        cnn_wimg(ds,0,VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,&s0),
        cnn_wimg(ds,1,VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,&i1),
        cnn_wimg(ds,2,VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,&s2),
    };
    vkUpdateDescriptorSets(r->device, 3, ws, 0, NULL);
    CnnPC pc = {0}; pc.sx=(int32_t)w; pc.sy=(int32_t)h; pc.flags = level0 ? 1 : 0;
    vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, P->cnn_pyramid_pipe);
    vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, P->cnn_pyramid_pl, 0, 1, &ds, 0, NULL);
    vkCmdPushConstants(cmd, P->cnn_pyramid_pl, VK_SHADER_STAGE_COMPUTE_BIT, 0, 32, &pc);
    vkCmdDispatch(cmd, CNN_GRID(w, h));
}

static void cnn_cost9_dispatch(VkRenderer* r, VkCommandBuffer cmd,
                               const VkImageView in5[5], const VkImageView out3[3],
                               int wnfgId, uint32_t w, uint32_t h) {
    VkPipelineSet* P = &r->pipelines;
    VkDescriptorSet ds = cnn_alloc(r, P->cnn_cost9_dsl); if (!ds) return;
    VkImageView dmy = r->fg_cnn.dummy.layerView[0];
    VkImageView srcmap[9] = { in5[0],in5[1],in5[2],in5[3], dmy, dmy, dmy, dmy, in5[4] };
    VkDescriptorImageInfo si[9];
    for (int i=0;i<9;i++) si[i]=(VkDescriptorImageInfo){r->fg_sampler, srcmap[i], VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
    VkDescriptorImageInfo oi[3]; for (int i=0;i<3;i++) oi[i]=(VkDescriptorImageInfo){VK_NULL_HANDLE,out3[i],VK_IMAGE_LAYOUT_GENERAL};
    VkDescriptorBufferInfo b8 = {r->fg_cnn.w[wnfgId], 0, r->fg_cnn.wLen[wnfgId]};
    VkWriteDescriptorSet ws[13];
    for (int i=0;i<9;i++) ws[i]=cnn_wimg(ds,32+i,VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,&si[i]);
    for (int i=0;i<3;i++) ws[9+i]=cnn_wimg(ds,48+i,VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,&oi[i]);
    ws[12]=cnn_wbuf(ds,8,VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,&b8);
    vkUpdateDescriptorSets(r->device, 13, ws, 0, NULL);
    CnnPC pc = {0}; pc.sx=(int32_t)w; pc.sy=(int32_t)h; pc.t=0.5f; pc.mvScale=1.0f; pc.cinT=2;
    vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, P->cnn_cost9_pipe);
    vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, P->cnn_cost9_pl, 0, 1, &ds, 0, NULL);
    vkCmdPushConstants(cmd, P->cnn_cost9_pl, VK_SHADER_STAGE_COMPUTE_BIT, 0, 32, &pc);
    vkCmdDispatch(cmd, CNN_GRID(w, h));
}

static void cnn_flowreg_dispatch(VkRenderer* r, VkCommandBuffer cmd,
                                 VkImageView f0, VkImageView f1, VkImageView flowSeed,
                                 VkImageView occ, VkImageView outFlow16f, uint32_t w, uint32_t h) {
    VkPipelineSet* P = &r->pipelines;
    VkDescriptorSet ds = cnn_alloc(r, P->cnn_flowreg_dsl); if (!ds) return;
    VkImageView dmy = r->fg_cnn.dummy.layerView[0];
    VkDescriptorImageInfo s32={r->fg_sampler,f0,VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
    VkDescriptorImageInfo s33={r->fg_sampler,f1,VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
    VkDescriptorImageInfo s34={r->fg_sampler,dmy,VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
    VkDescriptorImageInfo s35={r->fg_sampler,dmy,VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
    VkDescriptorImageInfo s36={r->fg_sampler,flowSeed,VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
    VkDescriptorImageInfo s37={r->fg_sampler,occ,VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
    VkDescriptorImageInfo oi ={VK_NULL_HANDLE,outFlow16f,VK_IMAGE_LAYOUT_GENERAL};
    VkDescriptorBufferInfo b8={r->fg_cnn.w[24],0,r->fg_cnn.wLen[24]};
    VkWriteDescriptorSet ws[8] = {
        cnn_wimg(ds,32,VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,&s32),
        cnn_wimg(ds,33,VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,&s33),
        cnn_wimg(ds,34,VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,&s34),
        cnn_wimg(ds,35,VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,&s35),
        cnn_wimg(ds,36,VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,&s36),
        cnn_wimg(ds,37,VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,&s37),
        cnn_wimg(ds,48,VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,&oi),
        cnn_wbuf(ds,8,VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,&b8),
    };
    vkUpdateDescriptorSets(r->device, 8, ws, 0, NULL);
    CnnPC pc = {0}; pc.sx=(int32_t)w; pc.sy=(int32_t)h; pc.t=0.5f; pc.mvScale=1.0f; pc.cinT=2;
    vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, P->cnn_flowreg_pipe);
    vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, P->cnn_flowreg_pl, 0, 1, &ds, 0, NULL);
    vkCmdPushConstants(cmd, P->cnn_flowreg_pl, VK_SHADER_STAGE_COMPUTE_BIT, 0, 32, &pc);
    vkCmdDispatch(cmd, CNN_GRID(w, h));
}

static void cnn_clear_f16(VkCommandBuffer cmd, VkCnnImg* im) {
    cnn_to_write(cmd, im->image, im->layers);
    VkClearColorValue cc; memset(&cc, 0, sizeof(cc));
    VkImageSubresourceRange sr = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, im->layers};
    cnn_barrier_ml(cmd, im->image, im->layers, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, VK_ACCESS_TRANSFER_WRITE_BIT);
    vkCmdClearColorImage(cmd, im->image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, &cc, 1, &sr);
    cnn_barrier_ml(cmd, im->image, im->layers, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
        VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
        VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT);
}

static void cnn_ingest(VkRenderer* r, VkCommandBuffer cmd, VkImageView frameView, VkCnnFeatSet* FS) {
    for (int L=0; L<CNN_FLOW_LEVELS; L++) {
        cnn_to_write(cmd, FS->luma[L].image, 1);
        cnn_to_write(cmd, FS->feat4a[L].image, 1);
        cnn_to_write(cmd, FS->feat4b[L].image, 1);
        cnn_to_write(cmd, FS->feat8[L].image, 2);
    }

    for (int L=0; L<CNN_FLOW_LEVELS; L++) {
        if (L>0) cnn_to_read(cmd, FS->luma[L-1].image, 1);
        cnn_pyramid_dispatch(r, cmd, (L==0)?frameView:FS->luma[L-1].view,
                             FS->luma[L].view, (L==0), FS->luma[L].w, FS->luma[L].h);
    }
    cnn_to_read(cmd, FS->luma[CNN_FLOW_LEVELS-1].image, 1);

    for (int L=0; L<CNN_FLOW_LEVELS; L++)
        cnn_conv_dispatch(r, cmd, r->fg_cnn.dummy.view, FS->luma[L].view,
                          FS->feat4a[L].view, 5, 1, 1, 1|16, FS->feat4a[L].w, FS->feat4a[L].h);
    for (int L=0; L<CNN_FLOW_LEVELS; L++) cnn_to_read(cmd, FS->feat4a[L].image, 1);

    for (int L=0; L<CNN_FLOW_LEVELS; L++)
        cnn_conv_dispatch(r, cmd, FS->feat4a[L].view, FS->luma[L].view,
                          FS->feat4b[L].view, 6, 1, 1, 0, FS->feat4b[L].w, FS->feat4b[L].h);
    for (int L=0; L<CNN_FLOW_LEVELS; L++) cnn_to_read(cmd, FS->feat4b[L].image, 1);

    for (int L=0; L<CNN_FLOW_LEVELS; L++)
        cnn_conv_dispatch(r, cmd, FS->feat4b[L].view, FS->luma[L].view,
                          FS->feat8[L].view, 7, 1, 2, 2, FS->feat8[L].w, FS->feat8[L].h);
    for (int L=0; L<CNN_FLOW_LEVELS; L++) cnn_to_read(cmd, FS->feat8[L].image, 2);
}

static void cnn_concat4(VkCommandBuffer cmd, VkCnnImg* lo2, VkCnnImg* hi2, VkCnnImg* dst4) {
    cnn_to_write(cmd, dst4->image, 4);
    cnn_barrier_ml(cmd, lo2->image, 2, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_TRANSFER_READ_BIT);
    cnn_barrier_ml(cmd, hi2->image, 2, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_TRANSFER_READ_BIT);
    cnn_barrier_ml(cmd, dst4->image, 4, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_SHADER_WRITE_BIT, VK_ACCESS_TRANSFER_WRITE_BIT);
    VkImageCopy cp[2]; memset(cp, 0, sizeof(cp));
    cp[0].srcSubresource=(VkImageSubresourceLayers){VK_IMAGE_ASPECT_COLOR_BIT,0,0,2};
    cp[0].dstSubresource=(VkImageSubresourceLayers){VK_IMAGE_ASPECT_COLOR_BIT,0,0,2};
    cp[0].extent=(VkExtent3D){dst4->w, dst4->h, 1};
    cp[1]=cp[0]; cp[1].dstSubresource.baseArrayLayer=2;
    vkCmdCopyImage(cmd, lo2->image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, dst4->image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &cp[0]);
    vkCmdCopyImage(cmd, hi2->image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, dst4->image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &cp[1]);
    cnn_barrier_ml(cmd, lo2->image, 2, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
        VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_ACCESS_TRANSFER_READ_BIT, VK_ACCESS_SHADER_READ_BIT);
    cnn_barrier_ml(cmd, hi2->image, 2, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
        VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_ACCESS_TRANSFER_READ_BIT, VK_ACCESS_SHADER_READ_BIT);
    cnn_barrier_ml(cmd, dst4->image, 4, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
        VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT);
}

static void cnn_flow_pass(VkRenderer* r, VkCommandBuffer cmd, uint32_t parity,
                          VkFgImage* prevFrame, VkFgImage* currFrame, bool forward,
                          VkFgImage* outFlow) {
    (void)parity;
    VkFgCnn* C = &r->fg_cnn;
    if (!C->ready) return;
    if (!forward) {
        C->curPool = (C->curPool + 1u) % (uint32_t)CNN_POOLS;
        vkResetDescriptorPool(r->device, C->pool[C->curPool], 0);
    }

    cnn_clear_f16(cmd, &C->occ);
    cnn_clear_f16(cmd, &C->seedBlack);
    cnn_clear_f16(cmd, &C->dummy);

    VkImageView pv = forward ? currFrame->view : prevFrame->view;
    VkImageView cv = forward ? prevFrame->view : currFrame->view;
    cnn_ingest(r, cmd, pv, &C->featPrev);
    cnn_ingest(r, cmd, cv, &C->featCurr);

    for (int L = CNN_FLOW_LEVELS - 1; L >= 0; --L) {
        uint32_t w = C->hG0[L].w, h = C->hG0[L].h;
        VkImageView seedView = (L == CNN_FLOW_LEVELS - 1) ? C->seedBlack.view : C->flowMid[L+1].view;

        cnn_concat4(cmd, &C->featPrev.feat8[L], &C->featCurr.feat8[L], &C->feat8_pair[L]);

        cnn_to_write(cmd, C->hG0[L].image, 2);
        cnn_conv_dispatch(r, cmd, C->feat8_pair[L].view, C->featCurr.luma[L].view, C->hG0[L].view, 36, 4, 2, 0, w, h);
        cnn_to_read(cmd, C->hG0[L].image, 2);
        cnn_to_write(cmd, C->hG1[L].image, 2);
        cnn_conv_dispatch(r, cmd, C->hG0[L].view, C->featCurr.luma[L].view, C->hG1[L].view, 37, 2, 2, 0, w, h);
        cnn_to_read(cmd, C->hG1[L].image, 2);
        cnn_to_write(cmd, C->hG23[L].image, 4);
        cnn_conv_dispatch(r, cmd, C->hG1[L].view, C->featCurr.luma[L].view, C->hG23[L].view, 42, 4, 4, 0, w, h);
        cnn_to_read(cmd, C->hG23[L].image, 4);
        cnn_to_write(cmd, C->hG4[L].image, 2);
        cnn_conv_dispatch(r, cmd, C->hG23[L].view, seedView, C->hG4[L].view, 21, 3, 2, 0, w, h);
        cnn_to_read(cmd, C->hG4[L].image, 2);

        cnn_to_write(cmd, C->hD0[L].image, 3);
        { VkImageView in5[5]={C->hG4[L].layerView[0],C->hG4[L].layerView[1],C->hG23[L].layerView[2],C->hG23[L].layerView[3],seedView};
          VkImageView out3[3]={C->hD0[L].layerView[0],C->hD0[L].layerView[1],C->hD0[L].layerView[2]};
          cnn_cost9_dispatch(r, cmd, in5, out3, 14, w, h); }
        cnn_to_read(cmd, C->hD0[L].image, 3);
        cnn_to_write(cmd, C->hD1[L].image, 3);
        { VkImageView in5[5]={C->hD0[L].layerView[0],C->hD0[L].layerView[1],C->hD0[L].layerView[2],C->hG4[L].layerView[0],seedView};
          VkImageView out3[3]={C->hD1[L].layerView[0],C->hD1[L].layerView[1],C->hD1[L].layerView[2]};
          cnn_cost9_dispatch(r, cmd, in5, out3, 20, w, h); }
        cnn_to_read(cmd, C->hD1[L].image, 3);
        cnn_to_write(cmd, C->hD2[L].image, 2);
        cnn_conv_dispatch(r, cmd, C->hD1[L].view, C->featCurr.luma[L].view, C->hD2[L].view, 22, 2, 2, 0, w, h);
        cnn_to_read(cmd, C->hD2[L].image, 2);
        cnn_to_write(cmd, C->hD3[L].image, 1);
        cnn_conv_dispatch(r, cmd, C->hD2[L].view, C->featCurr.luma[L].view, C->hD3[L].view, 26, 1, 1, 0, w, h);
        cnn_to_read(cmd, C->hD3[L].image, 1);

        VkImageView fdst = (L == 0) ? outFlow->view  : C->flowMid[L].view;
        VkImage     fimg = (L == 0) ? outFlow->image : C->flowMid[L].image;
        uint32_t    dw   = (L == 0) ? outFlow->width  : w;
        uint32_t    dh   = (L == 0) ? outFlow->height : h;
        cnn_to_write(cmd, fimg, 1);
        cnn_flowreg_dispatch(r, cmd, C->hD3[L].layerView[0], C->hD2[L].layerView[0], seedView,
                             C->occ.view, fdst, dw, dh);
        if (L != 0) cnn_to_read(cmd, fimg, 1);
    }

    vkr_image_barrier(cmd, outFlow->image, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
        VK_ACCESS_SHADER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT);
}
