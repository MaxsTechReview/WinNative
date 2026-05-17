// "System" / NULL driverName  -> /system/lib64/libvulkan.so.
// Any other name              -> adrenotools_open_libvulkan against the user-installed driver,
//                                falling back to the system loader if anything goes wrong.
// Caller owns the returned handle and must dlclose it.

#pragma once

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

void *winlator_open_vulkan(JNIEnv *env, jobject context, const char *driver_name);
void *winlator_open_system_vulkan(void);

#ifdef __cplusplus
}
#endif
