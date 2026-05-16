// Native sync_file / eventfd helpers backing SyncExtension's fence FDs.

#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <poll.h>
#include <stdint.h>
#include <sys/eventfd.h>
#include <unistd.h>

#define LOG_TAG "SyncFenceFd"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_runtime_display_connector_SyncFenceFd_pollFd(
    JNIEnv* env, jclass cls, jint fd, jint timeoutMs)
{
    (void)env; (void)cls;
    if (fd < 0) return -1;
    struct pollfd pfd = { .fd = fd, .events = POLLIN, .revents = 0 };
    int rc;
    do {
        rc = poll(&pfd, 1, timeoutMs);
    } while (rc < 0 && errno == EINTR);
    if (rc < 0) return -1;
    if (rc == 0) return 0;
    if (pfd.revents & (POLLERR | POLLNVAL | POLLHUP)) return -1;
    return (pfd.revents & POLLIN) ? 1 : 0;
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
Java_com_winlator_cmod_runtime_display_connector_SyncFenceFd_closeFd(
    JNIEnv* env, jclass cls, jint fd)
{
    (void)env; (void)cls;
    if (fd >= 0) close(fd);
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_runtime_display_connector_SyncFenceFd_openDevNull(
    JNIEnv* env, jclass cls)
{
    (void)env; (void)cls;
    int fd = open("/dev/null", O_RDONLY | O_CLOEXEC);
    if (fd < 0) LOGW("open(/dev/null) failed: %d", errno);
    return fd;
}
