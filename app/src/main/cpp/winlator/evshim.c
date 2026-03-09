/* evshim.c - Multi-Controller & Dynamic SDL Virtual Joystick Shim
 * Creates virtual SDL joysticks backed by shared memory for Wine controller
 * support
 *
 * Optimizations:
 * - Memory-mapped I/O (mmap) instead of read/write syscalls
 * - Single unified polling thread for all controllers
 * - Adaptive polling: fast (0.5ms) during activity, slow (4ms) when idle
 * - Delta-only updates per axis/button to minimize SDL calls
 * - Lock-free design using memory barriers
 */

#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <sched.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <time.h>
#include <unistd.h>

/* SDL2 types - minimal forward declarations */
typedef struct SDL_Joystick SDL_Joystick;
typedef struct {
  int major, minor, patch;
} SDL_version;
typedef struct SDL_VirtualJoystickDesc {
  uint16_t version;
  uint16_t type;
  uint16_t naxes;
  uint16_t nbuttons;
  uint16_t nhats;
  uint16_t vendor_id;
  uint16_t product_id;
  uint16_t padding;
  uint32_t button_mask;
  uint32_t axis_mask;
  const char *name;
  void *userdata;
  void (*Update)(void *);
  void (*SetPlayerIndex)(void *, int);
  int (*Rumble)(void *, uint16_t, uint16_t);
  int (*RumbleTriggers)(void *, uint16_t, uint16_t);
  int (*SetLED)(void *, uint8_t, uint8_t, uint8_t);
  int (*SendEffect)(void *, const void *, int);
} SDL_VirtualJoystickDesc;

#define SDL_VIRTUAL_JOYSTICK_DESC_VERSION 1
#define SDL_JOYSTICK_TYPE_GAMECONTROLLER 1
#define SDL_INIT_JOYSTICK 0x00000200

static int g_debug_enabled = 0;
static int g_spinwait_enabled = 0;
#define LOGI(...) dprintf(STDOUT_FILENO, __VA_ARGS__)
#define LOGE(...) dprintf(STDERR_FILENO, __VA_ARGS__)
#define LOGD(...)                                                              \
  do {                                                                         \
    if (g_debug_enabled)                                                       \
      dprintf(STDOUT_FILENO, __VA_ARGS__);                                     \
  } while (0)

#define MAX_GAMEPADS 4
#define GAMEPAD_MEM_SIZE 64

/* Adaptive polling intervals */
#define POLL_FAST_NS 500000L  /* 0.5ms = 2000Hz during active input */
#define POLL_SLOW_NS 4000000L /* 4ms = 250Hz during idle */
#define IDLE_THRESHOLD 50     /* ~25ms of no activity before slowing down */

#define AXIS_DEADZONE 256 /* ~0.8% deadzone to filter stick noise */

/* Shared memory layout - must match Android side exactly */
struct gamepad_io {
  int16_t lx, ly, rx, ry, lt, rt; /* 12 bytes: axes */
  uint8_t btn[15];                /* 15 bytes: buttons */
  uint8_t hat;                    /* 1 byte: hat/dpad */
  uint8_t _padding[4];            /* 4 bytes: alignment */
  uint16_t low_freq_rumble;       /* 2 bytes: rumble out */
  uint16_t high_freq_rumble;      /* 2 bytes: rumble out */
}; /* Total: 36 bytes */

/* Per-controller state */
struct controller_state {
  SDL_Joystick *js;
  volatile struct gamepad_io *mem; /* mmap'd shared memory */
  int mem_fd;
  int16_t last_axes[6];
  uint8_t last_btns[15];
  uint8_t last_hat;
  int active;
};

static int vjoy_ids[MAX_GAMEPADS] = {-1, -1, -1, -1};
static struct controller_state ctrl[MAX_GAMEPADS] = {0};
static int g_num_players = 0;
static void *handle = NULL;

/* SDL function pointers */
static int (*p_SDL_Init)(uint32_t);
static const char *(*p_SDL_GetError)(void);
static SDL_Joystick *(*p_SDL_JoystickOpen)(int);
static int (*p_SDL_JoystickAttachVirtualEx)(const SDL_VirtualJoystickDesc *);
static int (*p_SDL_JoystickSetVirtualAxis)(SDL_Joystick *, int, int16_t);
static int (*p_SDL_JoystickSetVirtualButton)(SDL_Joystick *, int, uint8_t);
static int (*p_SDL_JoystickSetVirtualHat)(SDL_Joystick *, int, uint8_t);
static void (*p_SDL_PumpEvents)(void);
static void (*p_SDL_Delay)(uint32_t);
static void (*p_SDL_GetVersion)(SDL_version *);

#define GETFUNCPTR(name)                                                       \
  do {                                                                         \
    if (!(p_##name = (typeof(p_##name))dlsym(handle, #name)))                  \
      LOGE("Failed to load SDL: %s\n", #name);                                 \
  } while (0)

/* Portable atomic operations - use builtins if available, else volatile */
#if defined(__GNUC__) || defined(__clang__)
#define ATOMIC_LOAD(ptr) __atomic_load_n(ptr, __ATOMIC_ACQUIRE)
#define ATOMIC_STORE(ptr, val) __atomic_store_n(ptr, val, __ATOMIC_RELEASE)
#else
/* Fallback for non-GCC/Clang: volatile access + compiler barrier */
#define ATOMIC_LOAD(ptr) (*(volatile typeof(*(ptr)) *)(ptr))
#define ATOMIC_STORE(ptr, val)                                                 \
  do {                                                                         \
    *(volatile typeof(*(ptr)) *)(ptr) = (val);                                 \
    __asm__ __volatile__("" ::: "memory");                                     \
  } while (0)
#endif

/* Inline deadzone filter */
static inline int16_t apply_deadzone(int16_t val) {
  int16_t abs_val = val < 0 ? -val : val;
  return abs_val < AXIS_DEADZONE ? 0 : val;
}

/* Rumble callback - writes directly to memory-mapped region */
static int OnRumble(void *userdata, uint16_t low, uint16_t high) {
  int idx = (int)(intptr_t)userdata;
  if (idx < 0 || idx >= MAX_GAMEPADS || !ctrl[idx].mem)
    return -1;

  /* Direct memory write with release semantics for visibility */
  volatile struct gamepad_io *mem = ctrl[idx].mem;
  ATOMIC_STORE(&mem->low_freq_rumble, low);
  ATOMIC_STORE(&mem->high_freq_rumble, high);
  return 0;
}

/* Unified polling thread - handles all controllers in one tight loop */
static void *unified_updater(void *arg) {
  (void)arg;
  struct timespec fast_sleep = {0, POLL_FAST_NS};
  struct timespec slow_sleep = {0, POLL_SLOW_NS};
  int idle_count = 0;

  /* Open all SDL joysticks upfront */
  for (int i = 0; i < g_num_players; i++) {
    if (vjoy_ids[i] < 0 || !ctrl[i].mem)
      continue;
    ctrl[i].js = p_SDL_JoystickOpen(vjoy_ids[i]);
    if (!ctrl[i].js) {
      LOGE("P%d: SDL_JoystickOpen failed\n", i);
      continue;
    }
    ctrl[i].active = 1;
    LOGI("VJOY P%d active\n", i);
  }

  LOGI("VJOY adaptive updater (fast=%ldus, slow=%ldus) PID %d\n",
       POLL_FAST_NS / 1000, POLL_SLOW_NS / 1000, getpid());

  for (;;) {
    int had_updates = 0;

    /* Process all controllers in a single pass */
    for (int i = 0; i < g_num_players; i++) {
      if (!ctrl[i].active)
        continue;

      volatile struct gamepad_io *mem = ctrl[i].mem;
      SDL_Joystick *js = ctrl[i].js;

      /* Read axes with atomic acquire + deadzone filtering */
      int16_t axes[6];
      axes[0] = apply_deadzone(ATOMIC_LOAD(&mem->lx));
      axes[1] = apply_deadzone(ATOMIC_LOAD(&mem->ly));
      axes[2] = apply_deadzone(ATOMIC_LOAD(&mem->rx));
      axes[3] = apply_deadzone(ATOMIC_LOAD(&mem->ry));
      axes[4] = ATOMIC_LOAD(&mem->lt); /* No deadzone for triggers */
      axes[5] = ATOMIC_LOAD(&mem->rt);

      /* Delta update axes - only call SDL when value changes */
      for (int a = 0; a < 6; a++) {
        if (axes[a] != ctrl[i].last_axes[a]) {
          p_SDL_JoystickSetVirtualAxis(js, a, axes[a]);
          ctrl[i].last_axes[a] = axes[a];
          had_updates = 1;
        }
      }

      /* Delta update buttons */
      for (int b = 0; b < 15; b++) {
        uint8_t btn = ATOMIC_LOAD(&mem->btn[b]);
        if (btn != ctrl[i].last_btns[b]) {
          p_SDL_JoystickSetVirtualButton(js, b, btn);
          ctrl[i].last_btns[b] = btn;
          had_updates = 1;
        }
      }

      /* Delta update hat */
      uint8_t hat = ATOMIC_LOAD(&mem->hat);
      if (hat != ctrl[i].last_hat) {
        p_SDL_JoystickSetVirtualHat(js, 0, hat);
        ctrl[i].last_hat = hat;
        had_updates = 1;
      }
    }

    /* Adaptive timing based on activity */
    if (had_updates) {
      idle_count = 0;
      if (g_spinwait_enabled) {
        sched_yield(); /* Ultra-low latency: just yield CPU briefly */
      } else {
        nanosleep(&fast_sleep, NULL); /* 0.5ms during active input */
      }
    } else {
      idle_count++;
      if (idle_count > IDLE_THRESHOLD) {
        nanosleep(&slow_sleep, NULL); /* 4ms when idle - saves CPU */
      } else {
        nanosleep(&fast_sleep, NULL); /* Stay fast briefly after activity */
      }
    }
  }
  return NULL;
}

/* Watchdog wrapper - respawns updater thread if it dies unexpectedly */
static void *watchdog_thread(void *arg) {
  (void)arg;
  struct timespec check_interval = {1, 0}; /* Check every 1 second */

  while (1) {
    pthread_t tid;
    int result = pthread_create(&tid, NULL, unified_updater, NULL);
    if (result != 0) {
      LOGE("Failed to create updater thread: %d\n", result);
      nanosleep(&check_interval, NULL);
      continue;
    }

    /* Wait for the thread to exit (it shouldn't under normal conditions) */
    void *retval;
    pthread_join(tid, &retval);

    /* If we get here, the thread exited unexpectedly - respawn it */
    LOGE("Updater thread exited unexpectedly, respawning in 1s...\n");
    nanosleep(&check_interval, NULL);
  }
  return NULL;
}

/* Hot-plug detection - checks for newly connected controllers */
static char g_data_path[256] = {0};

static void try_attach_controller(int idx) {
  if (ctrl[idx].active || !handle)
    return; /* Already active or SDL not loaded */

  char path[300];
  snprintf(path, sizeof path, "%s/gamepad%s.mem", g_data_path,
           (idx == 0) ? "" : (char[2]){'0' + idx, '\0'});

  /* Check if memory file exists now */
  if (access(path, F_OK) != 0)
    return;

  /* Try to open and map */
  int fd = open(path, O_RDWR);
  if (fd < 0)
    return;

  void *mem =
      mmap(NULL, GAMEPAD_MEM_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
  if (mem == MAP_FAILED) {
    close(fd);
    return;
  }

  /* Create virtual joystick */
  SDL_VirtualJoystickDesc d = {0};
  d.version = SDL_VIRTUAL_JOYSTICK_DESC_VERSION;
  d.type = SDL_JOYSTICK_TYPE_GAMECONTROLLER;
  d.naxes = 6;
  d.nbuttons = 15;
  d.nhats = 1;
  d.Rumble = &OnRumble;
  d.userdata = (void *)(intptr_t)idx;

  char name[64];
  snprintf(name, sizeof name, "Virtual Gamepad P%d", idx + 1);
  d.name = strdup(name);

  int vjoy_id = p_SDL_JoystickAttachVirtualEx(&d);
  if (vjoy_id < 0) {
    munmap(mem, GAMEPAD_MEM_SIZE);
    close(fd);
    return;
  }

  /* Open the SDL joystick */
  SDL_Joystick *js = p_SDL_JoystickOpen(vjoy_id);
  if (!js) {
    munmap(mem, GAMEPAD_MEM_SIZE);
    close(fd);
    return;
  }

  /* Success - store everything */
  ctrl[idx].mem_fd = fd;
  ctrl[idx].mem = (volatile struct gamepad_io *)mem;
  ctrl[idx].js = js;
  vjoy_ids[idx] = vjoy_id;
  ctrl[idx].active = 1;

  /* Update player count if needed */
  if (idx >= g_num_players)
    g_num_players = idx + 1;

  LOGI("HOTPLUG: P%d connected dynamically\n", idx + 1);
}

static void *hotplug_thread(void *arg) {
  (void)arg;
  struct timespec interval = {2, 0}; /* Check every 2 seconds */

  LOGI("EVSHIM hotplug detection started\n");

  while (1) {
    nanosleep(&interval, NULL);

    /* Check for any inactive slots that might have new files */
    for (int i = 0; i < MAX_GAMEPADS; i++) {
      if (!ctrl[i].active) {
        try_attach_controller(i);
      }
    }
  }
  return NULL;
}

__attribute__((constructor)) static void initialize_all_pads(void) {
  const char *dbg = getenv("EVSHIM_DEBUG");
  g_debug_enabled = dbg && strchr("1yY", *dbg);

  const char *spinwait = getenv("EVSHIM_SPINWAIT");
  g_spinwait_enabled = spinwait && strchr("1yY", *spinwait);

  LOGI("EVSHIM initializing (spinwait=%d)...\n", g_spinwait_enabled);

  handle = dlopen("libSDL2-2.0.so.0", RTLD_LAZY | RTLD_GLOBAL);
  if (!handle) {
    LOGE("dlopen SDL failed: %s\n", dlerror());
    return;
  }

  GETFUNCPTR(SDL_Init);
  GETFUNCPTR(SDL_GetError);
  GETFUNCPTR(SDL_JoystickOpen);
  GETFUNCPTR(SDL_JoystickAttachVirtualEx);
  GETFUNCPTR(SDL_JoystickSetVirtualAxis);
  GETFUNCPTR(SDL_JoystickSetVirtualButton);
  GETFUNCPTR(SDL_JoystickSetVirtualHat);
  GETFUNCPTR(SDL_PumpEvents);
  GETFUNCPTR(SDL_Delay);
  GETFUNCPTR(SDL_GetVersion);

  p_SDL_Init(SDL_INIT_JOYSTICK);

  SDL_version v;
  p_SDL_GetVersion(&v);
  LOGI("SDL %d.%d.%d bound\n", v.major, v.minor, v.patch);

  g_num_players =
      getenv("EVSHIM_MAX_PLAYERS") ? atoi(getenv("EVSHIM_MAX_PLAYERS")) : 1;
  if (g_num_players > MAX_GAMEPADS)
    g_num_players = MAX_GAMEPADS;

  const char *data_path = getenv("EVSHIM_DATA_PATH");
  if (!data_path)
    data_path = "/data/data/com.winlator.cmod/files/imagefs/tmp";

  /* Store path globally for hotplug detection */
  strncpy(g_data_path, data_path, sizeof(g_data_path) - 1);

  int attached = 0;
  for (int i = 0; i < g_num_players; ++i) {
    char path[256];
    snprintf(path, sizeof path, "%s/gamepad%s.mem", data_path,
             (i == 0) ? "" : (char[2]){'0' + i, '\0'});

    /* Open for read+write (needed for mmap and rumble writeback) */
    ctrl[i].mem_fd = open(path, O_RDWR);
    if (ctrl[i].mem_fd < 0) {
      LOGE("P%d: open '%s' failed: %s\n", i, path, strerror(errno));
      continue;
    }

    /* Memory-map for zero-copy access - eliminates read() syscall overhead */
    void *mem = mmap(NULL, GAMEPAD_MEM_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED,
                     ctrl[i].mem_fd, 0);
    if (mem == MAP_FAILED) {
      LOGE("P%d: mmap failed: %s\n", i, strerror(errno));
      close(ctrl[i].mem_fd);
      ctrl[i].mem_fd = -1;
      continue;
    }
    ctrl[i].mem = (volatile struct gamepad_io *)mem;

    /* Create virtual joystick */
    SDL_VirtualJoystickDesc d = {0};
    d.version = SDL_VIRTUAL_JOYSTICK_DESC_VERSION;
    d.type = SDL_JOYSTICK_TYPE_GAMECONTROLLER;
    d.naxes = 6;
    d.nbuttons = 15;
    d.nhats = 1;
    d.Rumble = &OnRumble;
    d.userdata = (void *)(intptr_t)i;

    char name[64];
    snprintf(name, sizeof name, "Virtual Gamepad P%d", i + 1);
    d.name = strdup(name);

    vjoy_ids[i] = p_SDL_JoystickAttachVirtualEx(&d);
    if (vjoy_ids[i] < 0) {
      LOGE("P%d: SDL attach failed\n", i);
      munmap((void *)ctrl[i].mem, GAMEPAD_MEM_SIZE);
      ctrl[i].mem = NULL;
      continue;
    }
    attached++;
  }

  /* Start watchdog thread (which manages the updater thread with respawn) */
  if (attached > 0) {
    pthread_t watchdog_tid;
    pthread_create(&watchdog_tid, NULL, watchdog_thread, NULL);
    pthread_detach(watchdog_tid);
    LOGI("EVSHIM: %d controller(s) ready\n", attached);
  }

  /* Start hotplug detection thread for controllers connected later */
  pthread_t hotplug_tid;
  pthread_create(&hotplug_tid, NULL, hotplug_thread, NULL);
  pthread_detach(hotplug_tid);
}

/* Intercept open() to hide /dev/input/event* and prevent conflicts */
static inline int is_event_node(const char *p) {
  return p && !strncmp(p, "/dev/input/event", 16);
}

typedef int (*open_f)(const char *, int, ...);
static open_f real_open;

int open(const char *path, int flags, ...)
    __attribute__((visibility("default")));
int open(const char *path, int flags, ...) {
  if (is_event_node(path)) {
    errno = ENOENT;
    return -1;
  }
  if (!real_open)
    real_open = (open_f)dlsym(RTLD_NEXT, "open");
  va_list ap;
  va_start(ap, flags);
  mode_t mode = (flags & O_CREAT) ? va_arg(ap, mode_t) : 0;
  va_end(ap);
  return real_open(path, flags, mode);
}
