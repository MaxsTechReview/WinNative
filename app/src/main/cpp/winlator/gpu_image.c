// AHardwareBuffer lifecycle for GPUImage.
//
// All EGL/GLES interop has been removed; the Vulkan compositor consumes the AHB directly via
// VK_ANDROID_external_memory_android_hardware_buffer (see vk/vk_image.c). This file is now
// concerned only with allocation, socket import, CPU mapping, and release of the AHB itself.

#include <android/hardware_buffer.h>
#include <android/log.h>
#include <jni.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#define LOG_TAG "GPUImage"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)

#define HAL_PIXEL_FORMAT_BGRA_8888 5

// Anything outside this set would silently misrender if treated as 32-bit RGBA; reject so
// DRI3 falls back to the SHM path.
static int gpuImageFormatSupported(uint32_t format) {
    return format == HAL_PIXEL_FORMAT_BGRA_8888
        || format == AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM
        || format == AHARDWAREBUFFER_FORMAT_R8G8B8X8_UNORM;
}

// ----------------------------------------------------------------------------
// Java GPUImage.nativeAhbCreate(short w, short h) -> jlong (AHardwareBuffer*)
// Allocates a CPU-readable + GPU-sampleable BGRA AHB.
// ----------------------------------------------------------------------------

JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_runtime_display_renderer_GPUImage_nativeAhbCreate(
    JNIEnv* env, jobject obj, jshort width, jshort height)
{
    (void)env; (void)obj;
    AHardwareBuffer_Desc desc = {0};
    desc.width = (uint32_t)width;
    desc.height = (uint32_t)height;
    desc.layers = 1;
    desc.format = HAL_PIXEL_FORMAT_BGRA_8888;
    desc.usage  = AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE
                | AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN
                | AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN;

    AHardwareBuffer* ahb = NULL;
    if (AHardwareBuffer_allocate(&desc, &ahb) != 0 || !ahb) {
        LOGW("AHardwareBuffer_allocate failed (%dx%d BGRA)", width, height);
        return 0;
    }
    AHardwareBuffer_Desc out = {0};
    AHardwareBuffer_describe(ahb, &out);
    LOGI("AHB allocated: %ux%u stride=%u format=%u usage=0x%llx",
         out.width, out.height, out.stride, out.format, (unsigned long long)out.usage);
    return (jlong)(intptr_t)ahb;
}

// ----------------------------------------------------------------------------
// Java GPUImage.nativeAhbImportFromSocket(int fd) -> jlong (AHardwareBuffer*)
// Reads a handle previously sent via AHardwareBuffer_sendHandleToUnixSocket.
// Reciprocates with a 1-byte ack so the sender can close its end.
// ----------------------------------------------------------------------------

JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_runtime_display_renderer_GPUImage_nativeAhbImportFromSocket(
    JNIEnv* env, jobject obj, jint fd)
{
    (void)env; (void)obj;
    struct stat fdStat;
    if (fstat(fd, &fdStat) != 0 || !S_ISSOCK(fdStat.st_mode)) {
        LOGW("AHB import fd %d is not a socket", fd);
        return 0;
    }
    uint8_t ack = 1;
    if (write(fd, &ack, 1) == -1) {
        LOGW("AHB import ack write failed");
        return 0;
    }
    AHardwareBuffer* ahb = NULL;
    if (AHardwareBuffer_recvHandleFromUnixSocket(fd, &ahb) != 0 || !ahb) {
        LOGW("AHardwareBuffer_recvHandleFromUnixSocket failed");
        return 0;
    }

    AHardwareBuffer_Desc desc = {0};
    AHardwareBuffer_describe(ahb, &desc);
    if (!gpuImageFormatSupported(desc.format)) {
        LOGW("AHB import rejected: format=%u not supported", desc.format);
        AHardwareBuffer_release(ahb);
        return 0;
    }
    if (!(desc.usage & AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE)) {
        LOGW("AHB import rejected: usage=0x%llx missing GPU_SAMPLED_IMAGE",
             (unsigned long long)desc.usage);
        AHardwareBuffer_release(ahb);
        return 0;
    }
    LOGI("AHB received from socket: fd=%d size=%ux%u stride=%u format=%u usage=0x%llx",
         fd, desc.width, desc.height, desc.stride, desc.format,
         (unsigned long long)desc.usage);
    return (jlong)(intptr_t)ahb;
}

// ----------------------------------------------------------------------------
// Java GPUImage.nativeAhbLock(long ahb) -> ByteBuffer
// Locks for CPU read+write and reports stride to Java via setStride().
// Returns a direct ByteBuffer over the mapped pixel data, or null on failure.
// ----------------------------------------------------------------------------

JNIEXPORT jobject JNICALL
Java_com_winlator_cmod_runtime_display_renderer_GPUImage_nativeAhbLock(
    JNIEnv* env, jobject obj, jlong ahbPtr)
{
    AHardwareBuffer* ahb = (AHardwareBuffer*)(intptr_t)ahbPtr;
    if (!ahb) return NULL;

    void* virt = NULL;
    int rc = AHardwareBuffer_lock(ahb,
                AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN | AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN,
                -1, NULL, &virt);
    if (rc != 0) {
        rc = AHardwareBuffer_lock(ahb, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN, -1, NULL, &virt);
    }
    if (rc != 0) {
        rc = AHardwareBuffer_lock(ahb, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, -1, NULL, &virt);
    }
    if (rc != 0 || !virt) {
        LOGW("AHardwareBuffer_lock failed");
        return NULL;
    }

    AHardwareBuffer_Desc desc;
    AHardwareBuffer_describe(ahb, &desc);

    jclass cls = (*env)->GetObjectClass(env, obj);
    jmethodID setStride = (*env)->GetMethodID(env, cls, "setStride", "(S)V");
    if (setStride) (*env)->CallVoidMethod(env, obj, setStride, (jshort)desc.stride);

    jlong size = (jlong)desc.stride * desc.height * 4;
    LOGI("AHB CPU mapped: %ux%u stride=%u bytes=%lld",
         desc.width, desc.height, desc.stride, (long long)size);
    return (*env)->NewDirectByteBuffer(env, virt, size);
}

// ----------------------------------------------------------------------------
// Java GPUImage.nativeAhbDestroy(long ahb, boolean locked)
// Unlocks if needed and releases our ref to the AHB.
// ----------------------------------------------------------------------------

JNIEXPORT void JNICALL
Java_com_winlator_cmod_runtime_display_renderer_GPUImage_nativeAhbDestroy(
    JNIEnv* env, jobject obj, jlong ahbPtr, jboolean locked)
{
    (void)env; (void)obj;
    AHardwareBuffer* ahb = (AHardwareBuffer*)(intptr_t)ahbPtr;
    if (!ahb) return;
    if (locked) AHardwareBuffer_unlock(ahb, NULL);
    AHardwareBuffer_release(ahb);
}
