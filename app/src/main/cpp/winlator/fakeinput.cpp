#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <iostream>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#include <dirent.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <linux/input.h>
#include <linux/joystick.h>
#include <poll.h>
#include <signal.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <sys/inotify.h>
#include <sys/ioctl.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/sysmacros.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/uio.h>
#include <sys/un.h>
#include <unistd.h>

#define EXPORT __attribute__((visibility("default"))) extern "C"

static constexpr uint16_t GAMEPAD_VENDOR_ID_BASE = 0x1234;
static constexpr uint16_t GAMEPAD_PRODUCT_ID_BASE = 0x5678;
static constexpr uint16_t GAMEPAD_VERSION = 0x0110;
static constexpr const char *GAMEPAD_NAME_TEMPLATE = "Generic HID Gamepad %d";
static constexpr const char *GAMEPAD_PHYS_TEMPLATE = "usb-fakeinput/input%d";
static constexpr const char *GAMEPAD_UNIQ_TEMPLATE = "0000000000%02d";
static constexpr uint8_t GAMEPAD_AXIS_COUNT = 8;
static constexpr uint8_t GAMEPAD_BUTTON_COUNT = 11;

std::unordered_map<int, const char *> controller_map;
static bool initialized = false;
static const char *hook_dir = nullptr;
static bool vibration_enabled = true;
volatile sig_atomic_t stop_flag = 0;

static int (*my_open)(const char *, int, ...) = nullptr;
static int (*my_openat)(int, const char *, int, ...) = nullptr;
static int (*my_stat)(const char *, struct stat *) = nullptr;
static int (*my_fstat)(int fd, struct stat *buf) = nullptr;
static int (*my_scandir)(const char *, struct dirent ***,
                         int (*)(const struct dirent *),
                         int (*)(const struct dirent **,
                                 const struct dirent **));
static int (*my_inotify_add_watch)(int, const char *, uint32_t);
static int (*my_close)(int);
static int (*my_poll)(struct pollfd *, nfds_t, int) = nullptr;
static int (*my_ppoll)(struct pollfd *, nfds_t, const struct timespec *,
                       const sigset_t *) = nullptr;
static int (*my_select)(int, fd_set *, fd_set *, fd_set *,
                        struct timeval *) = nullptr;
static ssize_t (*my_write)(int, const void *, size_t) = nullptr;

static std::unordered_map<int, struct ff_effect> ff_effects;
static int next_ff_id = 0;

namespace Logger {
int log_enabled;

void init() {
  log_enabled = getenv("FAKE_EVDEV_LOG") && atoi(getenv("FAKE_EVDEV_LOG"));
}

void log(const char *message, ...) {
  if (!log_enabled)
    return;

  va_list args;
  va_start(args, message);
  vfprintf(stderr, message, args);
  va_end(args);

  std::cerr.flush();
}
} // namespace Logger

void handle_sigint(int sig) {
  (void)sig;
  stop_flag = 1;
}

void setup_signal_handler() {
  if (!initialized) {
    signal(SIGINT, handle_sigint);
    initialized = true;
  }
}

__attribute__((constructor)) static void library_init() {
  if (!hook_dir)
    hook_dir = getenv("FAKE_EVDEV_DIR")
                   ? getenv("FAKE_EVDEV_DIR")
                   : "/data/data/com.termux/files/home/fake-input";
  vibration_enabled =
      getenv("FAKE_EVDEV_VIBRATION") && atoi(getenv("FAKE_EVDEV_VIBRATION"));

  Logger::init();
}

__attribute__((visibility("hidden"))) static void
send_vibration(int strong, int weak, uint16_t duration_ms, uint16_t slot) {
  if (!vibration_enabled)
    return;

  int sock = socket(AF_UNIX, SOCK_STREAM, 0);
  if (sock < 0)
    return;

  struct sockaddr_un addr = {};
  addr.sun_family = AF_UNIX;
  const char *name = "winlator_vibration";
  memcpy(addr.sun_path + 1, name, strlen(name));
  socklen_t addrlen = offsetof(struct sockaddr_un, sun_path) + 1 + strlen(name);

  if (connect(sock, reinterpret_cast<struct sockaddr *>(&addr), addrlen) < 0) {
    syscall(SYS_close, sock);
    return;
  }

  uint16_t data[4];
  data[0] = static_cast<uint16_t>(strong);
  data[1] = static_cast<uint16_t>(weak);
  data[2] = duration_ms;
  data[3] = slot;
  send(sock, data, sizeof(data), 0);
  syscall(SYS_close, sock);
}

__attribute__((visibility("hidden"))) static void
check_ff_event(const struct input_event *ev, uint16_t slot) {
  if (ev->type != EV_FF)
    return;

  int id = ev->code;
  if (ev->value > 0) {
    auto it = ff_effects.find(id);
    if (it == ff_effects.end())
      return;

    uint16_t duration = it->second.replay.length;
    if (it->second.type == FF_RUMBLE) {
      send_vibration(it->second.u.rumble.strong_magnitude,
                     it->second.u.rumble.weak_magnitude, duration, slot);
    } else if (it->second.type == FF_PERIODIC) {
      send_vibration(it->second.u.periodic.magnitude,
                     it->second.u.periodic.magnitude, duration, slot);
    }
  } else {
    send_vibration(0, 0, 0, slot);
  }
}

__attribute__((visibility("hidden"))) char *
from_real_to_fake_path(const char *pathname) {
  const char *event = strrchr(pathname, '/') + 1;
  char *fake_path;
  asprintf(&fake_path, "%s/%s", hook_dir, event);
  return fake_path;
}

__attribute__((visibility("hidden"))) static bool
is_fake_input_node_path(const char *pathname) {
  return pathname && (!strncmp(pathname, "/dev/input/event", 16) ||
                      !strncmp(pathname, "/dev/input/js", 13));
}

__attribute__((visibility("hidden"))) const char *
get_event(const char *pathname) {
  const char *event = strrchr(pathname, '/') + 1;
  return event;
}

__attribute__((visibility("hidden"))) int get_event_number(const char *event) {
  int event_number = atoi(event + strlen(event) - 1);
  return event_number;
}

__attribute__((visibility("hidden"))) static void
copy_slot_ioctl_string(int op, void *argp, const char *format, int event_number) {
  size_t size = _IOC_SIZE(op);
  if (!argp || size == 0)
    return;

  snprintf(static_cast<char *>(argp), size, format, event_number);
}

__attribute__((visibility("hidden"))) static bool is_fake_input_fd(int fd) {
  return controller_map.find(fd) != controller_map.end();
}

__attribute__((visibility("hidden"))) static bool
fake_fd_has_unread_data(int fd) {
  off_t current = lseek(fd, 0, SEEK_CUR);
  if (current == (off_t)-1)
    return false;

  struct stat st = {};
  if (fstat(fd, &st) != 0)
    return false;

  return current < st.st_size;
}

__attribute__((visibility("hidden"))) static long long
timespec_to_ms(const struct timespec *timeout) {
  if (!timeout)
    return -1;
  return static_cast<long long>(timeout->tv_sec) * 1000LL +
         timeout->tv_nsec / 1000000LL;
}

__attribute__((visibility("hidden"))) static long long
timeval_to_ms(const struct timeval *timeout) {
  if (!timeout)
    return -1;
  return static_cast<long long>(timeout->tv_sec) * 1000LL +
         timeout->tv_usec / 1000LL;
}

__attribute__((visibility("hidden"))) static long long monotonic_ms() {
  struct timespec now = {};
  clock_gettime(CLOCK_MONOTONIC, &now);
  return static_cast<long long>(now.tv_sec) * 1000LL + now.tv_nsec / 1000000LL;
}

EXPORT int open(const char *pathname, int flags, ...) {
  va_list va;
  mode_t mode;
  int fd;
  bool hasMode;
  bool isFromInput;

  va_start(va, flags);

  hasMode = flags & O_CREAT;
  isFromInput = false;

  if (hasMode) {
    mode = va_arg(va, mode_t);
  }

  va_end(va);

  if (!my_open)
    *(void **)&my_open = dlsym(RTLD_NEXT, "open");

  if (pathname) {
    if (is_fake_input_node_path(pathname)) {
      pathname = from_real_to_fake_path(pathname);
      isFromInput = true;
    } else if (!strcmp(pathname, "/dev/input")) {
      pathname = hook_dir;
    }
  }

  if (hasMode)
    fd = my_open(pathname, flags, mode);
  else
    fd = my_open(pathname, flags);

  if (isFromInput) {
    Logger::log("Adding controller, fd %d event %s\n", fd, get_event(pathname));
    controller_map[fd] = strdup(get_event(pathname));
  }

  return fd;
}

EXPORT int openat(int dirfd, const char *pathname, int flags, ...) {
  va_list va;
  mode_t mode;
  int fd;
  bool hasMode;
  bool isFromInput;

  va_start(va, flags);

  isFromInput = false;
  hasMode = flags & O_CREAT;

  if (hasMode) {
    mode = va_arg(va, mode_t);
  }

  va_end(va);

  if (!my_openat)
    *(void **)&my_openat = dlsym(RTLD_NEXT, "openat");

  if (pathname) {
    if (is_fake_input_node_path(pathname)) {
      pathname = from_real_to_fake_path(pathname);
      isFromInput = true;
    } else if (!strcmp(pathname, "/dev/input")) {
      pathname = hook_dir;
    }
  }

  if (hasMode)
    fd = my_openat(dirfd, pathname, flags, mode);
  else
    fd = my_openat(dirfd, pathname, flags);

  if (isFromInput) {
    Logger::log("Adding controller, fd %d event %s\n", fd, get_event(pathname));
    controller_map[fd] = strdup(get_event(pathname));
  }

  return fd;
}

EXPORT int stat(const char *pathname, struct stat *statbuf) {
  if (!my_stat)
    *(void **)&my_stat = dlsym(RTLD_NEXT, "stat");

  const char *event = nullptr;
  int event_number = -1;

  if (pathname) {
    if (is_fake_input_node_path(pathname)) {
      pathname = from_real_to_fake_path(pathname);
      event = get_event(pathname);
      event_number = get_event_number(event);
    } else if (!strcmp(pathname, "/dev/input")) {
      pathname = hook_dir;
    }
  }

  int ret = my_stat(pathname, statbuf);

  if (event && event_number >= 0) {
    statbuf->st_rdev = makedev(1, event_number);
  }

  return ret;
}

EXPORT int fstat(int fd, struct stat *buf) {
  if (!my_fstat)
    *(void **)&my_fstat = dlsym(RTLD_NEXT, "fstat");

  int ret = my_fstat(fd, buf);

  auto controller = controller_map.find(fd);
  if (controller != controller_map.end()) {
    buf->st_rdev = makedev(1, get_event_number(controller->second));
  }

  return ret;
}

EXPORT int scandir(const char *dirp, struct dirent ***namelist,
                   int (*filter)(const struct dirent *),
                   int (*compar)(const struct dirent **,
                                 const struct dirent **)) {
  if (!my_scandir)
    *(void **)&my_scandir = dlsym(RTLD_NEXT, "scandir");

  if (dirp) {
    if (!strcmp(dirp, "/dev/input")) {
      dirp = hook_dir;
    }
  }

  return my_scandir(dirp, namelist, filter, compar);
}

EXPORT int inotify_add_watch(int fd, const char *pathname, uint32_t mask) {
  if (!my_inotify_add_watch)
    *(void **)&my_inotify_add_watch = dlsym(RTLD_NEXT, "inotify_add_watch");

  if (pathname) {
    if (is_fake_input_node_path(pathname)) {
      pathname = from_real_to_fake_path(pathname);
    } else if (!strcmp(pathname, "/dev/input")) {
      pathname = hook_dir;
    }
  }

  return my_inotify_add_watch(fd, pathname, mask);
}

EXPORT int ioctl(int fd, int op, ...) {
  va_list va;
  void *argp;

  va_start(va, op);
  argp = va_arg(va, void *);
  va_end(va);

  auto controller = controller_map.find(fd);
  if (controller == controller_map.end()) {
    return syscall(SYS_ioctl, fd, op, argp);
  }

  int type = (op >> 8 & 0xFF);
  int number = (op >> 0 & 0xFF);
  const char *event = controller->second;
  int event_number = get_event_number(event);

  if (type == 0x45 && number == 0x1) {
    Logger::log("Hooking ioctl EVIOCGVERSION for event %s\n", event);
    int version = 65536;
    memcpy(argp, (void *)&version, sizeof(int));
    return 0;
  } else if (type == 0x45 && number == 0x2) {
    Logger::log("Hooking ioctl EVIOCGID for event %s\n", event);
    struct input_id id;
    memset(&id, 0, sizeof(id));
    id.bustype = 0x03;
    id.vendor = static_cast<uint16_t>(GAMEPAD_VENDOR_ID_BASE + event_number);
    id.product = static_cast<uint16_t>(GAMEPAD_PRODUCT_ID_BASE + event_number);
    id.version = GAMEPAD_VERSION;
    memcpy(argp, (void *)&id, sizeof(id));
    return 0;
  } else if (type == 0x45 && number == 0x6) {
    Logger::log("Hooking ioctl EVIOCGNAME for event %s\n", event);
    copy_slot_ioctl_string(op, argp, GAMEPAD_NAME_TEMPLATE, event_number);
    return 0;
  } else if (type == 0x45 && number == 0x7) {
    Logger::log("Hooking ioctl EVIOCGPHYS for event %s\n", event);
    copy_slot_ioctl_string(op, argp, GAMEPAD_PHYS_TEMPLATE, event_number);
    return 0;
  } else if (type == 0x45 && number == 0x8) {
    Logger::log("Hooking ioctl EVIOCGUNIQ for event %s\n", event);
    copy_slot_ioctl_string(op, argp, GAMEPAD_UNIQ_TEMPLATE, event_number);
    return 0;
  } else if (type == 0x45 && number == 0x9) {
    Logger::log("Hooking ioctl EVIOCGPROP for event %s\n", event);
    return 0;
  } else if (type == 0x45 && number == 0x18) {
    Logger::log("Hooking ioctl EVIOCGKEY(len) for event %s\n", event);
    char bitmask[KEY_MAX / 8] = {0};
    memcpy(argp, (void *)&bitmask, sizeof(bitmask));
    return 0;
  } else if (type == 0x45 && number == 0x20) {
    Logger::log("Hooking ioctl EVIOCGBIT(0, len) for event %s\n", event);
    char bitmask[EV_MAX / 8] = {0};
    bitmask[EV_SYN / 8] |= (1 << (EV_SYN % 8));
    bitmask[EV_KEY / 8] |= (1 << (EV_KEY % 8));
    bitmask[EV_ABS / 8] |= (1 << (EV_ABS % 8));
    memcpy(argp, (void *)&bitmask, sizeof(bitmask));
    return 0;
  } else if (type == 0x45 && number == 0x21) {
    Logger::log("Hooking ioctl EVIOCGBIT(EV_KEY, len) for event %s\n", event);
    char bitmask[KEY_MAX / 8] = {0};
    const int xbox_buttons[] = {BTN_A,    BTN_B,      BTN_X,      BTN_Y,
                                BTN_TL,   BTN_TR,     BTN_SELECT, BTN_START,
                                BTN_MODE, BTN_THUMBL, BTN_THUMBR};
    for (int button : xbox_buttons)
      bitmask[button / 8] |= (1 << (button % 8));
    memcpy(argp, (void *)&bitmask, sizeof(bitmask));
    return 0;
  } else if (type == 0x45 && number == 0x22) {
    Logger::log("Hooking ioctl EVIOCGBIT(EV_REL, len) for event %s\n", event);
    char bitmask[REL_MAX / 8] = {0};
    memcpy(argp, (void *)&bitmask, sizeof(bitmask));
    return 0;
  } else if (type == 0x45 && number == 0x23) {
    Logger::log("Hooking ioctl EVIOCGBIT(EV_ABS, len) for event %s\n", event);
    char bitmask[ABS_MAX / 8] = {0};
    bitmask[ABS_X / 8] |= (1 << (ABS_X % 8));
    bitmask[ABS_Y / 8] |= (1 << (ABS_Y % 8));
    bitmask[ABS_RX / 8] |= (1 << (ABS_RX % 8));
    bitmask[ABS_RY / 8] |= (1 << (ABS_RY % 8));
    bitmask[ABS_GAS / 8] |= (1 << (ABS_GAS % 8));
    bitmask[ABS_BRAKE / 8] |= (1 << (ABS_BRAKE % 8));
    bitmask[ABS_HAT0X / 8] |= (1 << (ABS_HAT0X % 8));
    bitmask[ABS_HAT0Y / 8] |= (1 << (ABS_HAT0Y % 8));
    memcpy(argp, (void *)&bitmask, sizeof(bitmask));
    return 0;
  } else if (type == 0x45 && number == 0x35) {
    Logger::log("Hooking ioctl EVIOCGBIT(EV_FF, len) for event %s\n", event);
    char bitmask[FF_MAX / 8] = {0};
    bitmask[FF_RUMBLE / 8] |= (1 << (FF_RUMBLE % 8));
    bitmask[FF_PERIODIC / 8] |= (1 << (FF_PERIODIC % 8));
    memcpy(argp, (void *)&bitmask, sizeof(bitmask));
    return 0;
  } else if (type == 0x45 && number == 0x80) {
    struct ff_effect *effect = static_cast<struct ff_effect *>(argp);
    if (effect->id == -1)
      effect->id = next_ff_id++;
    ff_effects[effect->id] = *effect;

    uint16_t duration = effect->replay.length;
    uint16_t slot = static_cast<uint16_t>(get_event_number(event));
    if (effect->type == FF_RUMBLE) {
      send_vibration(effect->u.rumble.strong_magnitude,
                     effect->u.rumble.weak_magnitude, duration, slot);
    } else if (effect->type == FF_PERIODIC) {
      send_vibration(effect->u.periodic.magnitude, effect->u.periodic.magnitude,
                     duration, slot);
    }
    return 0;
  } else if (type == 0x45 && number == 0x81) {
    int id = (intptr_t)argp;
    ff_effects.erase(id);
    return 0;
  } else if (type == 0x45 && number == 0x84) {
    int max_effects = 16;
    memcpy(argp, &max_effects, sizeof(int));
    return 0;
  } else if (type == 0x45 && number >= 0x40 && number <= 0x51) {
    Logger::log("Hooking ioctl EVIOCGABS(ABS) for event %s\n", event);
    struct input_absinfo abs_info;
    memset(&abs_info, 0, sizeof(abs_info));
    if (number >= 0x40 && number <= 0x44) {
      abs_info.value = 0;
      abs_info.minimum = -32768;
      abs_info.maximum = 32767;
    } else if (number >= 0x49 && number <= 0x4A) {
      abs_info.value = 0;
      abs_info.minimum = 0;
      abs_info.maximum = 255;
    } else if (number >= 0x50 && number <= 0x51) {
      abs_info.value = 0;
      abs_info.minimum = -1;
      abs_info.maximum = 1;
    }
    memcpy(argp, (void *)&abs_info, sizeof(abs_info));
    return 0;
  } else if (type == 0x45 && number == 0x90) {
    Logger::log("Hooking ioctl EVIOCGRAB for event %s\n", event);
    return 0;
  } else if (type == 0x6A && number == 0x1) {
    Logger::log("Hooking ioctl JSIOCGVERSION for event %s\n", event);
    int version = JS_VERSION;
    memcpy(argp, (void *)&version, sizeof(version));
    return 0;
  } else if (type == 0x6A && number == 0x11) {
    Logger::log("Hooking ioctl JSIOCGAXES for event %s\n", event);
    uint8_t axes = GAMEPAD_AXIS_COUNT;
    memcpy(argp, (void *)&axes, sizeof(axes));
    return 0;
  } else if (type == 0x6A && number == 0x12) {
    Logger::log("Hooking ioctl JSIOCGBUTTONS for event %s\n", event);
    uint8_t buttons = GAMEPAD_BUTTON_COUNT;
    memcpy(argp, (void *)&buttons, sizeof(buttons));
    return 0;
  } else if (type == 0x6A && number == 0x13) {
    Logger::log("Hooking ioctl JSIOCGNAME(len) for event %s\n", event);
    copy_slot_ioctl_string(op, argp, GAMEPAD_NAME_TEMPLATE, event_number);
    return 0;
  } else {
    Logger::log("Unhandled evdev ioctl, type %d number %d\n", type, number);
    return syscall(SYS_ioctl, fd, op, argp);
  }
}

EXPORT int close(int fd) {
  if (!my_close)
    *(void **)&my_close = dlsym(RTLD_NEXT, "close");

  auto controller = controller_map.find(fd);
  if (controller != controller_map.end()) {
    Logger::log("Removing controller, fd %d event %s\n", controller->first,
                controller->second);
    free((void *)controller->second);
    controller_map.erase(fd);
  }

  return my_close(fd);
}

EXPORT ssize_t read(int fd, void *buf, size_t count) {
  auto controller = controller_map.find(fd);

  if (controller != controller_map.end()) {
    ssize_t bytes_read = 0;
    int flags = fcntl(fd, F_GETFL);
    bool isNonBlock = flags & O_NONBLOCK;
    bytes_read = syscall(SYS_read, fd, buf, count);
    if (bytes_read == 0 && isNonBlock) {
      errno = EAGAIN;
      return -1;
    }
    while (bytes_read == 0 && !isNonBlock) {
      setup_signal_handler();
      if (stop_flag) {
        bytes_read = -1;
        errno = EINTR;
        return bytes_read;
      }
      bytes_read = syscall(SYS_read, fd, buf, count);
      continue;
    }

    return bytes_read;
  }
  return syscall(SYS_read, fd, buf, count);
}

EXPORT ssize_t write(int fd, const void *buf, size_t count) {
  if (!my_write)
    *(void **)&my_write = dlsym(RTLD_NEXT, "write");

  auto controller = controller_map.find(fd);
  if (controller != controller_map.end() &&
      count == sizeof(struct input_event)) {
    const struct input_event *ev = static_cast<const struct input_event *>(buf);
    uint16_t slot = static_cast<uint16_t>(get_event_number(controller->second));
    check_ff_event(ev, slot);
    // FF control events are commands sent to the fake device, not controller input.
    // Writing them back into the fake evdev file lets Wine read them as input and can
    // stall or corrupt controller state, so consume them here.
    if (ev->type == EV_FF)
      return static_cast<ssize_t>(count);
  }
  return my_write(fd, buf, count);
}

EXPORT ssize_t writev(int fd, const struct iovec *iov, int iovcnt) {
  auto controller = controller_map.find(fd);
  if (controller != controller_map.end()) {
    uint16_t slot = static_cast<uint16_t>(get_event_number(controller->second));
    std::vector<struct iovec> filtered;
    filtered.reserve(iovcnt);
    ssize_t filtered_out_bytes = 0;

    for (int i = 0; i < iovcnt; i++) {
      if (iov[i].iov_len == sizeof(struct input_event)) {
        const struct input_event *ev =
            static_cast<const struct input_event *>(iov[i].iov_base);
        check_ff_event(ev, slot);
        if (ev->type == EV_FF) {
          filtered_out_bytes += static_cast<ssize_t>(iov[i].iov_len);
          continue;
        }
      }
      filtered.push_back(iov[i]);
    }

    if (filtered.empty())
      return filtered_out_bytes;

    ssize_t written =
        syscall(SYS_writev, fd, filtered.data(), static_cast<int>(filtered.size()));
    if (written < 0)
      return written;
    return written + filtered_out_bytes;
  }
  return syscall(SYS_writev, fd, iov, iovcnt);
}

EXPORT int poll(struct pollfd *fds, nfds_t nfds, int timeout) {
  if (!my_poll)
    *(void **)&my_poll = dlsym(RTLD_NEXT, "poll");

  bool has_fake_fds = false;
  std::vector<struct pollfd> real_fds;
  real_fds.reserve(nfds);

  for (nfds_t i = 0; i < nfds; i++) {
    if (is_fake_input_fd(fds[i].fd)) {
      has_fake_fds = true;
    }
    real_fds.push_back(fds[i]);
    if (is_fake_input_fd(real_fds[i].fd)) {
      real_fds[i].fd = -1;
      real_fds[i].revents = 0;
    }
  }

  if (!has_fake_fds)
    return my_poll ? my_poll(fds, nfds, timeout) : -1;

  const long long deadline_ms = timeout < 0 ? -1 : monotonic_ms() + timeout;

  while (true) {
    int ready = 0;

    for (nfds_t i = 0; i < nfds; i++)
      fds[i].revents = 0;

    int real_ready = my_poll ? my_poll(real_fds.data(), nfds, 0) : 0;
    if (real_ready > 0) {
      for (nfds_t i = 0; i < nfds; i++) {
        if (!is_fake_input_fd(fds[i].fd)) {
          fds[i].revents = real_fds[i].revents;
          if (fds[i].revents)
            ready++;
        }
      }
    }

    for (nfds_t i = 0; i < nfds; i++) {
      if (!is_fake_input_fd(fds[i].fd))
        continue;

      short revents = 0;
      if ((fds[i].events & (POLLIN | POLLRDNORM)) &&
          fake_fd_has_unread_data(fds[i].fd))
        revents |= (fds[i].events & (POLLIN | POLLRDNORM));

      fds[i].revents = revents;
      if (revents)
        ready++;
    }

    if (ready > 0)
      return ready;

    if (timeout == 0)
      return 0;

    if (deadline_ms >= 0 && monotonic_ms() >= deadline_ms)
      return 0;

    struct timespec sleep_time = {0, 5 * 1000 * 1000};
    nanosleep(&sleep_time, nullptr);
  }
}

EXPORT int ppoll(struct pollfd *fds, nfds_t nfds,
                 const struct timespec *timeout, const sigset_t *sigmask) {
  if (!my_ppoll)
    *(void **)&my_ppoll = dlsym(RTLD_NEXT, "ppoll");

  if (sigmask)
    return my_ppoll ? my_ppoll(fds, nfds, timeout, sigmask)
                    : syscall(SYS_ppoll, fds, nfds, timeout, sigmask,
                              sizeof(sigset_t));

  return poll(fds, nfds, static_cast<int>(timespec_to_ms(timeout)));
}

EXPORT int select(int nfds, fd_set *readfds, fd_set *writefds,
                  fd_set *exceptfds, struct timeval *timeout) {
  if (!my_select)
    *(void **)&my_select = dlsym(RTLD_NEXT, "select");

  fd_set original_readfds;
  fd_set original_writefds;
  fd_set original_exceptfds;
  fd_set real_readfds;
  fd_set real_writefds;
  fd_set real_exceptfds;
  bool has_fake_fds = false;

  if (readfds) {
    original_readfds = *readfds;
    real_readfds = *readfds;
  } else {
    FD_ZERO(&original_readfds);
    FD_ZERO(&real_readfds);
  }
  if (writefds) {
    original_writefds = *writefds;
    real_writefds = *writefds;
  } else {
    FD_ZERO(&original_writefds);
    FD_ZERO(&real_writefds);
  }
  if (exceptfds) {
    original_exceptfds = *exceptfds;
    real_exceptfds = *exceptfds;
  } else {
    FD_ZERO(&original_exceptfds);
    FD_ZERO(&real_exceptfds);
  }

  for (int fd = 0; fd < nfds; fd++) {
    if (!is_fake_input_fd(fd))
      continue;
    has_fake_fds = true;
    FD_CLR(fd, &real_readfds);
    FD_CLR(fd, &real_writefds);
    FD_CLR(fd, &real_exceptfds);
  }

  if (!has_fake_fds)
    return my_select ? my_select(nfds, readfds, writefds, exceptfds, timeout)
                     : -1;

  const long long timeout_ms = timeval_to_ms(timeout);
  const long long deadline_ms =
      timeout_ms < 0 ? -1 : monotonic_ms() + timeout_ms;

  while (true) {
    int ready = 0;

    if (readfds)
      FD_ZERO(readfds);
    if (writefds)
      FD_ZERO(writefds);
    if (exceptfds)
      FD_ZERO(exceptfds);

    fd_set iter_readfds = real_readfds;
    fd_set iter_writefds = real_writefds;
    fd_set iter_exceptfds = real_exceptfds;
    struct timeval zero_timeout = {0, 0};

    int real_ready =
        my_select
            ? my_select(nfds, readfds ? &iter_readfds : nullptr,
                        writefds ? &iter_writefds : nullptr,
                        exceptfds ? &iter_exceptfds : nullptr, &zero_timeout)
            : 0;

    if (real_ready > 0) {
      for (int fd = 0; fd < nfds; fd++) {
        if (readfds && FD_ISSET(fd, &iter_readfds)) {
          FD_SET(fd, readfds);
          ready++;
        }
        if (writefds && FD_ISSET(fd, &iter_writefds)) {
          FD_SET(fd, writefds);
          ready++;
        }
        if (exceptfds && FD_ISSET(fd, &iter_exceptfds)) {
          FD_SET(fd, exceptfds);
          ready++;
        }
      }
    }

    for (int fd = 0; fd < nfds; fd++) {
      if (!is_fake_input_fd(fd))
        continue;
      if (readfds && FD_ISSET(fd, &original_readfds) &&
          fake_fd_has_unread_data(fd)) {
        FD_SET(fd, readfds);
        ready++;
      }
    }

    if (ready > 0)
      return ready;

    if (timeout_ms == 0)
      return 0;

    if (deadline_ms >= 0 && monotonic_ms() >= deadline_ms)
      return 0;

    struct timespec sleep_time = {0, 5 * 1000 * 1000};
    nanosleep(&sleep_time, nullptr);
  }
}
