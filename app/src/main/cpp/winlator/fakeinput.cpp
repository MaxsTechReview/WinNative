#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include <iostream>
#include <unordered_map>
#include <memory>
#include <fstream>
#include <algorithm>
#include <mutex>

#include <fcntl.h>
#include <dirent.h>
#include <stdio.h>
#include <sys/types.h>
#include <unistd.h>
#include <dlfcn.h>
#include <stdarg.h>
#include <string.h>
#include <stdbool.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/inotify.h>
#include <sys/syscall.h>
#include <sys/sysmacros.h>
#include <linux/input.h>
#include <linux/joystick.h>

#define EXPORT __attribute__((visibility("default"))) extern "C"

static constexpr uint16_t XBOX360_VENDOR_ID = 0x045e;
static constexpr uint16_t XBOX360_PRODUCT_ID = 0x028e;
static constexpr uint16_t XBOX360_VERSION = 0x0114;
static constexpr const char *XBOX360_NAME = "Microsoft X-Box 360 pad";
static constexpr const char *XBOX360_PHYS = "usb-fakeinput/input0";
static constexpr const char *XBOX360_UNIQ = "000000000001";
static constexpr uint8_t XBOX360_AXIS_COUNT = 8;
static constexpr uint8_t XBOX360_BUTTON_COUNT = 11;

std::unordered_map<int, const char *> controller_map;
static bool initialized = false;
static const char *hook_dir = nullptr;
volatile sig_atomic_t stop_flag = 0;

static int (*my_open)(const char *, int, ...) = nullptr;
static int (*my_openat)(int, const char *, int, ...) = nullptr;
static int (*my_stat)(const char *, struct stat *) = nullptr;
static int (*my_fstat)(int fd, struct stat *buf) = nullptr;
static int (*my_scandir)(const char *, struct dirent***, int(*)(const struct dirent *), int(*)(const struct dirent**, const struct dirent**));
static int (*my_inotify_add_watch)(int, const char *, uint32_t);
static int (*my_close)(int);

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
}

void handle_sigint(int sig) {
    stop_flag = 1;
}

void setup_signal_handler() {
    if (!initialized) {
        signal(SIGINT, handle_sigint);
        initialized = true;
    }
}

__attribute__((constructor))
static void library_init() {
    if (!hook_dir)
        hook_dir = getenv("FAKE_EVDEV_DIR") ? getenv("FAKE_EVDEV_DIR") : "/data/data/com.termux/files/home/fake-input";

    Logger::init();
}

__attribute__((visibility("hidden")))
char *from_real_to_fake_path(const char *pathname) {
    const char *event = strrchr(pathname, '/') + 1;
    char *fake_path;
    asprintf(&fake_path, "%s/%s", hook_dir, event);
    return fake_path;
}

__attribute__((visibility("hidden")))
const char *get_event(const char *pathname) {
    const char *event = strrchr(pathname, '/') + 1;
    return event;
}

__attribute__((visibility("hidden")))
int get_event_number(const char *event) {
    int event_number = atoi(event + strlen(event) - 1);
    return event_number;
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
        if (strstr(pathname, "/dev/input/event")) {
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
        if (strstr(pathname, "/dev/input/event")) {
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
        if (strstr(pathname, "/dev/input/event")) {
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

EXPORT int scandir(const char *dirp, struct dirent ***namelist, int(*filter)(const struct dirent *), int(*compar)(const struct dirent **, const struct dirent **)) {
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
        if (strstr(pathname, "/dev/input/event")) {
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
        id.vendor = XBOX360_VENDOR_ID;
        id.product = XBOX360_PRODUCT_ID;
        id.version = XBOX360_VERSION;
        memcpy(argp, (void *)&id, sizeof(id));
        return 0;
    } else if (type == 0x45 && number == 0x6) {
        Logger::log("Hooking ioctl EVIOCGNAME for event %s\n", event);
        snprintf((char *)argp, _IOC_SIZE(op), "%s", XBOX360_NAME);
        return 0;
    } else if (type == 0x45 && number == 0x7) {
        Logger::log("Hooking ioctl EVIOCGPHYS for event %s\n", event);
        snprintf((char *)argp, _IOC_SIZE(op), "%s", XBOX360_PHYS);
        return 0;
    } else if (type == 0x45 && number == 0x8) {
        Logger::log("Hooking ioctl EVIOCGUNIQ for event %s\n", event);
        snprintf((char *)argp, _IOC_SIZE(op), "%s", XBOX360_UNIQ);
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
        for (int i = 0x130; i <= 0x13e; i++) {
            if (i == 0x130)
                bitmask[BTN_A / 8] |= (1 << (BTN_A % 8));
            else if (i == 0x131)
                bitmask[BTN_B / 8] |= (1 << (BTN_B % 8));
            else if (i == 0x132)
                continue;
            else if (i == 0x133)
                bitmask[BTN_X / 8] |= (1 << (BTN_X % 8));
            else if (i == 0x134)
                bitmask[BTN_Y / 8] |= (1 << (BTN_Y % 8));
            else if (i == 0x135)
                continue;
            else
                bitmask[i / 8] |= (1 << (i % 8));
        }
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
        bitmask[FF_RUMBLE / 8] |= 0;
        bitmask[FF_SINE / 8] |= 0;
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
    } else if (type == 0x6A && number == 0x13) {
        Logger::log("Hooking ioctl JSIOCGNAME(len) for event %s\n", event);
        snprintf((char *)argp, _IOC_SIZE(op), "%s", XBOX360_NAME);
        return 0;
    } else if (type == 0x6A && number == 0x11) {
        Logger::log("Hooking ioctl JSIOCGAXES for event %s\n", event);
        *reinterpret_cast<uint8_t *>(argp) = XBOX360_AXIS_COUNT;
        return 0;
    } else if (type == 0x6A && number == 0x12) {
        Logger::log("Hooking ioctl JSIOCGBUTTONS for event %s\n", event);
        *reinterpret_cast<uint8_t *>(argp) = XBOX360_BUTTON_COUNT;
        return 0;
    } else if (type == 0x6A && number == 0x01) {
        Logger::log("Hooking ioctl JSIOCGVERSION for event %s\n", event);
        int version = JS_VERSION;
        memcpy(argp, &version, sizeof(version));
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
        Logger::log("Removing controller, fd %d event %s\n", controller->first, controller->second);
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
