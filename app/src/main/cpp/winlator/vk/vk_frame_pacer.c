// Native vsync pacer for the Vulkan compositor.
//
// This deliberately does not render. It only turns dirty render requests into one
// SurfaceView render-thread wakeup on the next display frame.

#include <android/choreographer.h>
#include <android/log.h>
#include <android/looper.h>
#include <dlfcn.h>
#include <jni.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

#define LOG_TAG "VkFramePacer"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)

typedef struct NativeFramePacer {
    JavaVM* vm;
    jobject renderer;
    jmethodID on_frame_tick;
    void (*post_frame_callback64)(AChoreographer* choreographer,
                                  AChoreographer_frameCallback64 callback,
                                  void* data);

    pthread_t thread;
    pthread_mutex_t lock;
    pthread_cond_t ready_cond;

    ALooper* looper;
    AChoreographer* choreographer;
    bool running;
    bool ready;
    bool post_requested;
    bool callback_posted;
} NativeFramePacer;

static void wake_looper_if_available(NativeFramePacer* p) {
    ALooper* looper = NULL;
    pthread_mutex_lock(&p->lock);
    if (p->looper) {
        looper = p->looper;
        ALooper_acquire(looper);
    }
    pthread_mutex_unlock(&p->lock);

    if (looper) {
        ALooper_wake(looper);
        ALooper_release(looper);
    }
}

static JNIEnv* get_env_for_current_thread(NativeFramePacer* p) {
    JNIEnv* env = NULL;
    if ((*p->vm)->GetEnv(p->vm, (void**)&env, JNI_VERSION_1_6) == JNI_OK) {
        return env;
    }
    return NULL;
}

static void frame_callback(int64_t frame_time_nanos, void* data) {
    NativeFramePacer* p = (NativeFramePacer*)data;
    bool should_call_java = false;

    pthread_mutex_lock(&p->lock);
    p->callback_posted = false;
    should_call_java = p->running;
    pthread_mutex_unlock(&p->lock);

    if (should_call_java) {
        JNIEnv* env = get_env_for_current_thread(p);
        if (env) {
            (*env)->CallVoidMethod(env, p->renderer, p->on_frame_tick, (jlong)frame_time_nanos);
            if ((*env)->ExceptionCheck(env)) {
                (*env)->ExceptionDescribe(env);
                (*env)->ExceptionClear(env);
            }
        }
    }
}

static void* frame_pacer_thread_main(void* data) {
    NativeFramePacer* p = (NativeFramePacer*)data;
    JNIEnv* env = NULL;
    JavaVMAttachArgs args = {
        .version = JNI_VERSION_1_6,
        .name = "VkFramePacer",
        .group = NULL,
    };
    bool attached = false;

    if ((*p->vm)->AttachCurrentThread(p->vm, &env, &args) != JNI_OK) {
        LOGE("AttachCurrentThread failed");
        pthread_mutex_lock(&p->lock);
        p->running = false;
        p->ready = true;
        pthread_cond_broadcast(&p->ready_cond);
        pthread_mutex_unlock(&p->lock);
        return NULL;
    }
    attached = true;

    ALooper* looper = ALooper_prepare(ALOOPER_PREPARE_ALLOW_NON_CALLBACKS);
    AChoreographer* choreographer = looper ? AChoreographer_getInstance() : NULL;
    void (*post_frame_callback64)(AChoreographer*, AChoreographer_frameCallback64, void*) = NULL;
    if (choreographer) {
        post_frame_callback64 =
                (void (*)(AChoreographer*, AChoreographer_frameCallback64, void*))
                dlsym(RTLD_DEFAULT, "AChoreographer_postFrameCallback64");
    }

    pthread_mutex_lock(&p->lock);
    if (looper) ALooper_acquire(looper);
    p->looper = looper;
    p->choreographer = choreographer;
    p->post_frame_callback64 = post_frame_callback64;
    p->ready = true;
    if (!looper || !choreographer || !post_frame_callback64) {
        p->running = false;
        LOGE("Failed to initialize API 29 AChoreographer frame callback");
    }
    pthread_cond_broadcast(&p->ready_cond);
    pthread_mutex_unlock(&p->lock);

    while (true) {
        bool should_post = false;

        pthread_mutex_lock(&p->lock);
        if (!p->running) {
            pthread_mutex_unlock(&p->lock);
            break;
        }
        if (p->post_requested && !p->callback_posted && p->choreographer) {
            p->post_requested = false;
            p->callback_posted = true;
            should_post = true;
        }
        pthread_mutex_unlock(&p->lock);

        if (should_post) {
            p->post_frame_callback64(choreographer, frame_callback, p);
        }

        ALooper_pollOnce(-1, NULL, NULL, NULL);
    }

    pthread_mutex_lock(&p->lock);
    p->looper = NULL;
    p->choreographer = NULL;
    p->ready = false;
    p->post_requested = false;
    p->callback_posted = false;
    pthread_mutex_unlock(&p->lock);

    if (looper) ALooper_release(looper);
    if (attached) (*p->vm)->DetachCurrentThread(p->vm);
    return NULL;
}

JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_runtime_display_renderer_VulkanRenderer_nativeCreateFramePacer(
        JNIEnv* env, jclass clazz, jobject renderer) {
    (void)clazz;
    if (!renderer) return 0;

    NativeFramePacer* p = (NativeFramePacer*)calloc(1, sizeof(NativeFramePacer));
    if (!p) return 0;

    if ((*env)->GetJavaVM(env, &p->vm) != JNI_OK) {
        free(p);
        return 0;
    }

    jclass renderer_class = (*env)->GetObjectClass(env, renderer);
    if (!renderer_class) {
        free(p);
        return 0;
    }
    p->on_frame_tick = (*env)->GetMethodID(env, renderer_class, "onNativeFrameTick", "(J)V");
    (*env)->DeleteLocalRef(env, renderer_class);
    if (!p->on_frame_tick) {
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        LOGE("VulkanRenderer.onNativeFrameTick(long) not found");
        free(p);
        return 0;
    }

    p->renderer = (*env)->NewGlobalRef(env, renderer);
    if (!p->renderer) {
        free(p);
        return 0;
    }

    pthread_mutex_init(&p->lock, NULL);
    pthread_cond_init(&p->ready_cond, NULL);
    p->running = true;

    int rc = pthread_create(&p->thread, NULL, frame_pacer_thread_main, p);
    if (rc != 0) {
        LOGE("pthread_create failed: %d", rc);
        p->running = false;
        (*env)->DeleteGlobalRef(env, p->renderer);
        pthread_cond_destroy(&p->ready_cond);
        pthread_mutex_destroy(&p->lock);
        free(p);
        return 0;
    }

    pthread_mutex_lock(&p->lock);
    while (!p->ready) {
        pthread_cond_wait(&p->ready_cond, &p->lock);
    }
    bool running = p->running;
    pthread_mutex_unlock(&p->lock);

    if (!running) {
        pthread_join(p->thread, NULL);
        (*env)->DeleteGlobalRef(env, p->renderer);
        pthread_cond_destroy(&p->ready_cond);
        pthread_mutex_destroy(&p->lock);
        free(p);
        return 0;
    }

    return (jlong)(intptr_t)p;
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_runtime_display_renderer_VulkanRenderer_nativeDestroyFramePacer(
        JNIEnv* env, jclass clazz, jlong handle) {
    (void)clazz;
    NativeFramePacer* p = (NativeFramePacer*)(intptr_t)handle;
    if (!p) return;

    pthread_mutex_lock(&p->lock);
    p->running = false;
    p->post_requested = false;
    pthread_mutex_unlock(&p->lock);
    wake_looper_if_available(p);

    if (!pthread_equal(pthread_self(), p->thread)) {
        pthread_join(p->thread, NULL);
    } else {
        LOGW("nativeDestroyFramePacer called from pacer thread");
    }

    if (p->renderer) (*env)->DeleteGlobalRef(env, p->renderer);
    pthread_cond_destroy(&p->ready_cond);
    pthread_mutex_destroy(&p->lock);
    free(p);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_runtime_display_renderer_VulkanRenderer_nativeRequestFrame(
        JNIEnv* env, jclass clazz, jlong handle) {
    (void)env; (void)clazz;
    NativeFramePacer* p = (NativeFramePacer*)(intptr_t)handle;
    if (!p) return;

    bool should_wake = false;
    pthread_mutex_lock(&p->lock);
    if (p->running) {
        if (!p->callback_posted) {
            p->post_requested = true;
            should_wake = true;
        }
    }
    pthread_mutex_unlock(&p->lock);

    if (should_wake) wake_looper_if_available(p);
}
