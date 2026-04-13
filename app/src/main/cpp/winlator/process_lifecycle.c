#include <android/log.h>
#include <errno.h>
#include <jni.h>
#include <pthread.h>
#include <signal.h>
#include <sys/prctl.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

#define LOG_TAG "WinlatorLifecycle"

static pthread_mutex_t reaper_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t reaper_cond = PTHREAD_COND_INITIALIZER;
static int reaper_thread_started = 0;
static int reaper_window_active = 0;
static int reaper_thread_stop = 0;
static struct timespec reaper_deadline = {0};

static void reap_dead_children(int signo) {
  (void)signo;
  int saved_errno = errno;
  while (waitpid(-1, NULL, WNOHANG) > 0) {
  }
  errno = saved_errno;
}

static int reap_dead_children_now(void) {
  int reaped = 0;
  int saved_errno = errno;
  while (waitpid(-1, NULL, WNOHANG) > 0) {
    reaped++;
  }
  errno = saved_errno;
  return reaped;
}

static struct timespec timespec_now(void) {
  struct timespec now;
  clock_gettime(CLOCK_MONOTONIC, &now);
  return now;
}

static struct timespec timespec_add_ms(struct timespec base, jint duration_ms) {
  base.tv_sec += duration_ms / 1000;
  base.tv_nsec += (long)(duration_ms % 1000) * 1000000L;
  if (base.tv_nsec >= 1000000000L) {
    base.tv_sec += 1;
    base.tv_nsec -= 1000000000L;
  }
  return base;
}

static int timespec_compare(struct timespec a, struct timespec b) {
  if (a.tv_sec != b.tv_sec)
    return (a.tv_sec > b.tv_sec) ? 1 : -1;
  if (a.tv_nsec != b.tv_nsec)
    return (a.tv_nsec > b.tv_nsec) ? 1 : -1;
  return 0;
}

static void *reaper_thread_main(void *arg) {
  (void)arg;

  pthread_mutex_lock(&reaper_mutex);
  for (;;) {
    while (!reaper_window_active && !reaper_thread_stop) {
      pthread_cond_wait(&reaper_cond, &reaper_mutex);
    }
    if (reaper_thread_stop) {
      pthread_mutex_unlock(&reaper_mutex);
      return NULL;
    }

    struct timespec deadline = reaper_deadline;
    pthread_mutex_unlock(&reaper_mutex);

    for (;;) {
      int reaped = reap_dead_children_now();
      if (reaped > 0) {
        __android_log_print(
            ANDROID_LOG_INFO, LOG_TAG,
            "Native reaper thread collected %d dead child processes", reaped);
      }

      struct timespec now = timespec_now();
      if (timespec_compare(now, deadline) >= 0) {
        break;
      }

      struct timespec sleep_until = timespec_add_ms(now, 100);
      if (timespec_compare(sleep_until, deadline) > 0) {
        sleep_until = deadline;
      }
      clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &sleep_until, NULL);
    }

    pthread_mutex_lock(&reaper_mutex);
    if (timespec_compare(reaper_deadline, deadline) <= 0) {
      reaper_window_active = 0;
    }
  }
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_runtime_system_ProcessHelper_reapDeadChildrenNow(
    JNIEnv *env, jclass clazz) {
  (void)env;
  (void)clazz;
  return reap_dead_children_now();
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_runtime_system_ProcessHelper_startNativeReaperWindow(
    JNIEnv *env, jclass clazz, jint durationMs) {
  (void)env;
  (void)clazz;

  if (durationMs <= 0)
    return;

  pthread_mutex_lock(&reaper_mutex);

  if (!reaper_thread_started) {
    pthread_t thread;
    if (pthread_create(&thread, NULL, reaper_thread_main, NULL) == 0) {
      pthread_detach(thread);
      reaper_thread_started = 1;
      __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                          "Native reaper thread started");
    } else {
      __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
                          "Failed to start native reaper thread");
      pthread_mutex_unlock(&reaper_mutex);
      return;
    }
  }

  struct timespec new_deadline = timespec_add_ms(timespec_now(), durationMs);
  if (!reaper_window_active ||
      timespec_compare(new_deadline, reaper_deadline) > 0) {
    reaper_deadline = new_deadline;
  }
  reaper_window_active = 1;
  pthread_cond_signal(&reaper_cond);
  pthread_mutex_unlock(&reaper_mutex);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  (void)vm;
  (void)reserved;

  if (prctl(PR_SET_CHILD_SUBREAPER, 1, 0, 0, 0) != 0) {
    __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
                        "Failed to enable child subreaper: errno=%d", errno);
  } else {
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                        "Child subreaper enabled for app process");
  }

  struct sigaction sa;
  sa.sa_handler = reap_dead_children;
  sigemptyset(&sa.sa_mask);
  sa.sa_flags = SA_RESTART | SA_NOCLDSTOP;
  if (sigaction(SIGCHLD, &sa, NULL) != 0) {
    __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
                        "Failed to install SIGCHLD handler: errno=%d", errno);
  } else {
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "SIGCHLD reaper installed");
  }

  int reaped = reap_dead_children_now();
  if (reaped > 0) {
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                        "Reaped %d dead child processes on load", reaped);
  }

  return JNI_VERSION_1_6;
}
