#include <vulkan/vulkan.h>
#include <iostream>
#include <map>
#include <vector>

#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <sys/stat.h>
#include <fcntl.h>
#include "../adrenotools/include/adrenotools/driver.h"

#define LOG_TAG "System.out"
#define printf(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

PFN_vkGetInstanceProcAddr gip;

const char *getNativeLibraryDir(JNIEnv *env, jobject context) {
    char *native_libdir = NULL;

    if (context != NULL) {
        jclass class_ = env->FindClass("com/winlator/cmod/core/AppUtils");
        jmethodID getNativeLibraryDir = env->GetStaticMethodID(class_, "getNativeLibDir",
                                                               "(Landroid/content/Context;)Ljava/lang/String;");
        jstring nativeLibDir = static_cast<jstring>(env->CallStaticObjectMethod(class_,
                                                                                getNativeLibraryDir,
                                                                                context));
        native_libdir = (char *)env->GetStringUTFChars(nativeLibDir, nullptr);
    }
    return native_libdir;
}

const char *get_driver_path(JNIEnv *env, jobject context, const char *driver_name) {
    char *driver_path;

    jclass contextWrapperClass = env->FindClass("android/content/ContextWrapper");
    jmethodID  getFilesDir = env->GetMethodID(contextWrapperClass, "getFilesDir", "()Ljava/io/File;");
    jobject  filesDirObj = env->CallObjectMethod(context, getFilesDir);
    jclass fileClass = env->GetObjectClass(filesDirObj);
    jmethodID getAbsolutePath = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
    jstring absolutePath = static_cast<jstring>(env->CallObjectMethod(filesDirObj,
                                                                      getAbsolutePath));
    const char *absolute_path = env->GetStringUTFChars(absolutePath, nullptr);
    asprintf(&driver_path, "%s/contents/adrenotools/%s/", absolute_path, driver_name);

    env->ReleaseStringUTFChars(absolutePath, absolute_path);

    return driver_path;
}

const char *get_library_name(JNIEnv *env, jobject context, const char *driver_name) {
    char *library_name;

    jclass adrenotoolsManager = env->FindClass("com/winlator/cmod/contents/AdrenotoolsManager");
    jmethodID constructor = env->GetMethodID(adrenotoolsManager, "<init>", "(Landroid/content/Context;)V");
    jobject  adrenotoolsManagerObj = env->NewObject(adrenotoolsManager, constructor, context);
    jmethodID getLibraryName = env->GetMethodID(adrenotoolsManager, "getLibraryName","(Ljava/lang/String;)Ljava/lang/String;");

    jstring driverName = env->NewStringUTF(driver_name);

    jstring libraryName = static_cast<jstring>(env->CallObjectMethod(adrenotoolsManagerObj,getLibraryName, driverName));

    library_name = (char *)env->GetStringUTFChars(libraryName, nullptr);

    return library_name;
}

void *init_vulkan(JNIEnv  *env, jobject context, const char *driver_name, const char *nativeLibDir) {
    if (!driver_name || !nativeLibDir || !strcmp(driver_name, "System"))
        return dlopen("/system/lib64/libvulkan.so", RTLD_LAZY | RTLD_LOCAL);
    else {
        char *tmpdir;

        const char *driver_path = get_driver_path(env, context, driver_name);
        const char *library_name = get_library_name(env, context, driver_name);

        asprintf(&tmpdir, "%s%s", driver_path, "temp");

        mkdir(tmpdir, S_IRWXU | S_IRWXG);

        return adrenotools_open_libvulkan(RTLD_NOW, ADRENOTOOLS_DRIVER_CUSTOM, tmpdir, nativeLibDir, driver_path, library_name, nullptr, nullptr);
    }
}

VkInstance create_instance(const char *driverName, JNIEnv *env, jobject context) {
    VkResult result;
    VkInstance instance;
    VkInstanceCreateInfo create_info = {};
    void *vulkan_handle;

    vulkan_handle = init_vulkan(env, context,driverName, getNativeLibraryDir(env, context));

    gip = (PFN_vkGetInstanceProcAddr)dlsym(vulkan_handle, "vkGetInstanceProcAddr");

    PFN_vkCreateInstance createInstance = (PFN_vkCreateInstance)dlsym(vulkan_handle, "vkCreateInstance");

    create_info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    create_info.pNext = NULL;
    create_info.flags = 0;
    create_info.pApplicationInfo = NULL;
    create_info.enabledLayerCount = 0;
    create_info.enabledExtensionCount = 0;

    result = createInstance(&create_info, NULL, &instance);

    if (result != VK_SUCCESS)
        __android_log_print(ANDROID_LOG_DEBUG, "GPUInformation", "Failed to create instance: %d", result);

    return instance;

}

std::vector<VkPhysicalDevice> get_physical_devices(VkInstance instance) {
    VkResult result = VK_ERROR_UNKNOWN;
    std::vector<VkPhysicalDevice> physical_devices;
    uint32_t deviceCount;

    PFN_vkEnumeratePhysicalDevices enumeratePhysicalDevices = (PFN_vkEnumeratePhysicalDevices)gip(instance, "vkEnumeratePhysicalDevices");

    enumeratePhysicalDevices(instance, &deviceCount, NULL);
    physical_devices.resize(deviceCount);

    if (deviceCount > 0)
        result = enumeratePhysicalDevices(instance, &deviceCount, physical_devices.data());

    if (result != VK_SUCCESS)
        __android_log_print(ANDROID_LOG_DEBUG, "GPUInformation", "Failed to enumerate devices: %d", result);

    return physical_devices;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_core_GPUInformation_getVersion(JNIEnv *env, jclass obj, jstring driverName, jobject context) {
    VkPhysicalDeviceProperties props = {};
    char *driverVersion;
    char *driver_name;
    VkInstance instance;

    if (driverName != NULL)
        driver_name = (char *)env->GetStringUTFChars(driverName, nullptr);
    else
        driver_name = NULL;

    instance = create_instance(driver_name, env, context);
    PFN_vkGetPhysicalDeviceProperties getPhysicalDeviceProperties = (PFN_vkGetPhysicalDeviceProperties)gip(instance, "vkGetPhysicalDeviceProperties");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gip(instance, "vkDestroyInstance");

    for (const auto &pdevice: get_physical_devices(instance)) {
        getPhysicalDeviceProperties(pdevice, &props);
        uint32_t vk_driver_major = VK_VERSION_MAJOR(props.driverVersion);
        uint32_t vk_driver_minor = VK_VERSION_MINOR(props.driverVersion);
        uint32_t vk_driver_patch = VK_VERSION_PATCH(props.driverVersion);
        asprintf(&driverVersion, "%d.%d.%d", vk_driver_major, vk_driver_minor,
                 vk_driver_patch);
    }

    destroyInstance(instance, NULL);
    if (driverName != NULL)
        env->ReleaseStringUTFChars(driverName, driver_name);

    return (env->NewStringUTF(driverVersion));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_core_GPUInformation_getVulkanVersion(JNIEnv *env, jclass obj, jstring driverName, jobject context) {
    VkPhysicalDeviceProperties props = {};
    char *vulkanVersion;
    char *driver_name;
    VkInstance instance;

    if (driverName != NULL)
        driver_name = (char *)env->GetStringUTFChars(driverName, nullptr);
    else
        driver_name = NULL;

    instance = create_instance(driver_name, env, context);
    PFN_vkGetPhysicalDeviceProperties getPhysicalDeviceProperties = (PFN_vkGetPhysicalDeviceProperties)gip(instance, "vkGetPhysicalDeviceProperties");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gip(instance, "vkDestroyInstance");

    for (const auto &pdevice: get_physical_devices(instance)) {
        getPhysicalDeviceProperties(pdevice, &props);
        uint32_t vk_driver_major = VK_VERSION_MAJOR(props.apiVersion);
        uint32_t vk_driver_minor = VK_VERSION_MINOR(props.apiVersion);
        uint32_t vk_driver_patch = VK_VERSION_PATCH(props.apiVersion);
        asprintf(&vulkanVersion, "%d.%d.%d", vk_driver_major, vk_driver_minor,
                 vk_driver_patch);
    }

    destroyInstance(instance, NULL);
    if (driverName != NULL)
        env->ReleaseStringUTFChars(driverName, driver_name);

    return (env->NewStringUTF(vulkanVersion));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_core_GPUInformation_getRenderer(JNIEnv *env, jclass obj, jstring driverName, jobject context) {
    VkPhysicalDeviceProperties props = {};
    char *renderer;
    char *driver_name;
    VkInstance instance;

    if (driverName != NULL)
        driver_name = (char *)env->GetStringUTFChars(driverName, nullptr);
    else
        driver_name = NULL;

    instance = create_instance(driver_name, env, context);
    PFN_vkGetPhysicalDeviceProperties getPhysicalDeviceProperties = (PFN_vkGetPhysicalDeviceProperties)gip(instance, "vkGetPhysicalDeviceProperties");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gip(instance, "vkDestroyInstance");

    for (const auto &pdevice: get_physical_devices(instance)) {
        getPhysicalDeviceProperties(pdevice, &props);
        asprintf(&renderer, "%s", props.deviceName);
    }

    destroyInstance(instance, NULL);
    if (driverName != NULL)
        env->ReleaseStringUTFChars(driverName, driver_name);

    return (env->NewStringUTF(renderer));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_core_GPUInformation_getMemorySize(JNIEnv *env, jclass obj, jstring driverName, jobject context) {
    VkPhysicalDeviceMemoryProperties props = {};
    long memorySize;
    char *driver_name;
    VkInstance instance;

    if (driverName != NULL)
        driver_name = (char *)env->GetStringUTFChars(driverName, nullptr);
    else
        driver_name = NULL;

    instance = create_instance(driver_name, env, context);
    PFN_vkGetPhysicalDeviceMemoryProperties getPhysicalDeviceMemoryProperties = (PFN_vkGetPhysicalDeviceMemoryProperties)gip(instance, "vkGetPhysicalDeviceMemoryProperties");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gip(instance, "vkDestroyInstance");

    for (const auto &pdevice : get_physical_devices(instance)) {
        getPhysicalDeviceMemoryProperties(pdevice, &props);
        memorySize = props.memoryHeaps[0].size;
    }

    destroyInstance(instance, NULL);
    if (driverName != NULL)
        env->ReleaseStringUTFChars(driverName, driver_name);

    return memorySize / 1048576;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_winlator_cmod_core_GPUInformation_enumerateExtensions(JNIEnv *env, jclass obj, jstring driverName, jobject context) {
    jobjectArray extensions;
    VkInstance instance;
    uint32_t extensionCount;
    char *driver_name;
    std::vector<VkExtensionProperties> extensionProperties;

    if (driverName != NULL)
        driver_name = (char *)env->GetStringUTFChars(driverName, nullptr);
    else
        driver_name = NULL;

    instance = create_instance(driver_name, env, context);

    PFN_vkEnumerateDeviceExtensionProperties enumerateDeviceExtensionProperties = (PFN_vkEnumerateDeviceExtensionProperties)gip(instance, "vkEnumerateDeviceExtensionProperties");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gip(instance, "vkDestroyInstance");

    for (const auto &pdevice : get_physical_devices(instance)) {
        enumerateDeviceExtensionProperties(pdevice, NULL, &extensionCount, NULL);
        extensionProperties.resize(extensionCount);
        enumerateDeviceExtensionProperties(pdevice, NULL, &extensionCount, extensionProperties.data());
        extensions = (jobjectArray)env->NewObjectArray(extensionCount, env->FindClass("java/lang/String"), env->NewStringUTF(""));
        int index = 0;
        for (const auto &extensionProperty : extensionProperties) {
            env->SetObjectArrayElement(extensions, index, env->NewStringUTF(extensionProperty.extensionName));
            index++;
        }
    }

    destroyInstance(instance, NULL);
    if (driverName != NULL)
        env->ReleaseStringUTFChars(driverName, driver_name);

    return extensions;
}