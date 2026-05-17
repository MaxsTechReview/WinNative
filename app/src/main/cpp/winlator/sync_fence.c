// Native sync_file / eventfd helpers backing SyncExtension's fence FDs.

#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <poll.h>
#include <stdint.h>
#include <stdlib.h>
#include <sys/eventfd.h>
#include <unistd.h>

#define LOG_TAG "SyncFenceFd"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

JNIEXPORT jintArray JNICALL
Java_com_winlator_cmod_runtime_display_connector_SyncFenceFd_pollFds(
    JNIEnv* env, jclass cls, jintArray fdsArray, jint timeoutMs)
{
    (void)cls;
    if (fdsArray == NULL) return NULL;
    jsize n = (*env)->GetArrayLength(env, fdsArray);
    if (n == 0) return (*env)->NewIntArray(env, 0);

    jint* fdsRaw = (*env)->GetIntArrayElements(env, fdsArray, NULL);
    if (!fdsRaw) return NULL;

    struct pollfd* pfds = calloc((size_t)n, sizeof(struct pollfd));
    if (!pfds) {
        (*env)->ReleaseIntArrayElements(env, fdsArray, fdsRaw, JNI_ABORT);
        return NULL;
    }
    for (jsize i = 0; i < n; i++) {
        pfds[i].fd = fdsRaw[i];
        pfds[i].events = POLLIN;
    }
    (*env)->ReleaseIntArrayElements(env, fdsArray, fdsRaw, JNI_ABORT);

    int rc;
    do {
        rc = poll(pfds, (nfds_t)n, timeoutMs);
    } while (rc < 0 && errno == EINTR);

    if (rc < 0) {
        LOGW("poll() failed: %d", errno);
        free(pfds);
        return NULL;
    }

    jintArray result = (*env)->NewIntArray(env, n);
    if (!result) {
        free(pfds);
        return NULL;
    }

    jint* revents = calloc((size_t)n, sizeof(jint));
    if (!revents) {
        free(pfds);
        return NULL;
    }
    if (rc > 0) {
        for (jsize i = 0; i < n; i++) revents[i] = (jint)pfds[i].revents;
    }
    (*env)->SetIntArrayRegion(env, result, 0, n, revents);
    free(revents);
    free(pfds);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_runtime_display_connector_SyncFenceFd_createSignalEventFd(
    JNIEnv* env, jclass cls)
{
    (void)env; (void)cls;
    int fd = eventfd(0, EFD_NONBLOCK | EFD_CLOEXEC);
    if (fd < 0) LOGW("eventfd() failed: %d", errno);
    return fd;
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_runtime_display_connector_SyncFenceFd_dupFd(
    JNIEnv* env, jclass cls, jint fd)
{
    (void)env; (void)cls;
    if (fd < 0) return -1;
    int dup_fd = fcntl(fd, F_DUPFD_CLOEXEC, 0);
    if (dup_fd < 0) LOGW("dup fd %d failed: %d", fd, errno);
    return dup_fd;
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_runtime_display_connector_SyncFenceFd_signalEventFd(
    JNIEnv* env, jclass cls, jint fd)
{
    (void)env; (void)cls;
    if (fd < 0) return;
    uint64_t one = 1;
    ssize_t r;
    do {
        r = write(fd, &one, sizeof(one));
    } while (r < 0 && errno == EINTR);
    if (r != (ssize_t)sizeof(one) && errno != EAGAIN) {
        LOGW("eventfd signal failed on fd %d: %d", fd, errno);
    }
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_runtime_display_connector_SyncFenceFd_drainEventFd(
    JNIEnv* env, jclass cls, jint fd)
{
    (void)env; (void)cls;
    if (fd < 0) return;
    uint64_t buf;
    ssize_t r;
    do {
        r = read(fd, &buf, sizeof(buf));
    } while (r < 0 && errno == EINTR);
    // EAGAIN (nothing buffered) is expected; any other error is harmless here.
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_runtime_display_connector_SyncFenceFd_closeFd(
    JNIEnv* env, jclass cls, jint fd)
{
    (void)env; (void)cls;
    if (fd >= 0) close(fd);
}
