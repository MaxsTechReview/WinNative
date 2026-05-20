#define _GNU_SOURCE

#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <signal.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ptrace.h>
#include <sys/syscall.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <ucontext.h>
#include <unistd.h>

#ifndef SYS_landlock_create_ruleset
#define SYS_landlock_create_ruleset 444
#endif

#if defined(__aarch64__)
static long raw_syscall6(long number, long a1, long a2, long a3, long a4,
                         long a5, long a6) {
    register long x0 __asm__("x0") = a1;
    register long x1 __asm__("x1") = a2;
    register long x2 __asm__("x2") = a3;
    register long x3 __asm__("x3") = a4;
    register long x4 __asm__("x4") = a5;
    register long x5 __asm__("x5") = a6;
    register long x8 __asm__("x8") = number;
    __asm__ volatile("svc #0"
                     : "+r"(x0)
                     : "r"(x1), "r"(x2), "r"(x3), "r"(x4), "r"(x5), "r"(x8)
                     : "memory", "cc");
    return x0;
}

static long syscall_result(long rc) {
    if (rc < 0 && rc >= -4095) {
        errno = (int)-rc;
        return -1;
    }
    return rc;
}
#else
#error "steamwebhelper preload raw syscall shim is only implemented for aarch64"
#endif

struct cached_link {
    char path[PATH_MAX];
    char target[PATH_MAX];
};

static struct cached_link g_links[64];
static int g_link_count;
static char g_log_path[PATH_MAX];

static int is_steam_singleton_path(const char *path);
static void log_msg(const char *fmt, ...);

static int sidecar_path(const char *path, char *out, size_t out_size) {
    if (!is_steam_singleton_path(path)) {
        return -1;
    }
    if (snprintf(out, out_size, "%s.winnative-readlink-target", path) >= (int)out_size) {
        return -1;
    }
    return 0;
}

static void write_sidecar_link(const char *path, const char *target) {
    char sidecar[PATH_MAX];
    size_t len;
    int fd;

    if (target == NULL || sidecar_path(path, sidecar, sizeof(sidecar)) < 0) {
        return;
    }

    fd = open(sidecar, O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (fd < 0) {
        return;
    }

    len = strlen(target);
    if (write(fd, target, len) != (ssize_t)len) {
        log_msg("winnative steamwebhelper preload: sidecar write failed path=%s errno=%d",
                sidecar, errno);
    }
    close(fd);
}

static const char *read_sidecar_link(const char *path) {
    static char target[PATH_MAX];
    char sidecar[PATH_MAX];
    ssize_t len;
    int fd;

    if (sidecar_path(path, sidecar, sizeof(sidecar)) < 0) {
        return NULL;
    }

    fd = open(sidecar, O_RDONLY);
    if (fd < 0) {
        return NULL;
    }
    len = read(fd, target, sizeof(target) - 1);
    close(fd);
    if (len <= 0) {
        return NULL;
    }
    target[len] = '\0';
    return target;
}

static int sidecar_link_exists(const char *path, char *target, size_t target_size) {
    char sidecar[PATH_MAX];
    ssize_t len;
    int fd;

    if (target == NULL || target_size == 0 ||
        sidecar_path(path, sidecar, sizeof(sidecar)) < 0) {
        return 0;
    }

    fd = open(sidecar, O_RDONLY);
    if (fd < 0) {
        return 0;
    }
    len = read(fd, target, target_size - 1);
    close(fd);
    if (len <= 0) {
        return 0;
    }
    target[len] = '\0';
    return 1;
}

static void init_log_path(void) {
    const char *override = getenv("WINNATIVE_STEAMWEBHELPER_LOG");
    const char *home = getenv("HOME");

    if (g_log_path[0] != '\0') {
        return;
    }
    if (override != NULL && override[0] != '\0') {
        if (snprintf(g_log_path, sizeof(g_log_path), "%s", override) >=
            (int)sizeof(g_log_path)) {
            g_log_path[0] = '\0';
        }
        return;
    }
    if (home != NULL && home[0] != '\0') {
        if (snprintf(g_log_path, sizeof(g_log_path),
                     "%s/.steam/steam/logs/steamwebhelper.log", home) >=
            (int)sizeof(g_log_path)) {
            g_log_path[0] = '\0';
        }
        return;
    }
    if (snprintf(g_log_path, sizeof(g_log_path),
                 "/tmp/winnative-steamwebhelper.log") >= (int)sizeof(g_log_path)) {
        g_log_path[0] = '\0';
    }
}

static void log_msg(const char *fmt, ...) {
    init_log_path();
    if (g_log_path[0] == '\0') {
        return;
    }

    int fd = open(g_log_path, O_WRONLY | O_CREAT | O_APPEND, 0600);
    if (fd < 0) {
        return;
    }

    char line[PATH_MAX * 2];
    va_list ap;
    va_start(ap, fmt);
    int len = vsnprintf(line, sizeof(line), fmt, ap);
    va_end(ap);
    if (len > 0) {
        if (len > (int)sizeof(line) - 2) {
            len = (int)sizeof(line) - 2;
        }
        line[len++] = '\n';
        ssize_t ignored = write(fd, line, (size_t)len);
        (void)ignored;
    }
    close(fd);
}

static int is_steam_singleton_path(const char *path) {
    if (path == NULL) {
        return 0;
    }
    return strstr(path, "/tmp/.com.valvesoftware.Steam.") != NULL ||
           strstr(path, "SingletonCookie") != NULL ||
           strstr(path, "SingletonSocket") != NULL ||
           strstr(path, "SingletonLock") != NULL;
}

static void cache_link(const char *path, const char *target) {
    if (!is_steam_singleton_path(path) || target == NULL) {
        return;
    }
    write_sidecar_link(path, target);

    for (int i = 0; i < g_link_count; i++) {
        if (strcmp(g_links[i].path, path) == 0) {
            snprintf(g_links[i].target, sizeof(g_links[i].target), "%s", target);
            return;
        }
    }

    if (g_link_count >= (int)(sizeof(g_links) / sizeof(g_links[0]))) {
        memmove(&g_links[0], &g_links[1], sizeof(g_links[0]) * (g_link_count - 1));
        g_link_count--;
    }

    snprintf(g_links[g_link_count].path, sizeof(g_links[g_link_count].path), "%s", path);
    snprintf(g_links[g_link_count].target, sizeof(g_links[g_link_count].target), "%s", target);
    g_link_count++;
}

static const char *lookup_link(const char *path) {
    if (!is_steam_singleton_path(path)) {
        return NULL;
    }
    for (int i = g_link_count - 1; i >= 0; i--) {
        if (strcmp(g_links[i].path, path) == 0) {
            return g_links[i].target;
        }
    }
    return read_sidecar_link(path);
}

static ssize_t copy_target(const char *path, const char *target, char *buf, size_t bufsiz) {
    size_t len = strlen(target);
    size_t out = len;
    if (out > bufsiz) {
        out = bufsiz;
    }
    if (out > 0) {
        memcpy(buf, target, out);
    }
    log_msg("winnative steamwebhelper preload: readlink shim path=%s target=%s", path, target);
    return (ssize_t)out;
}

static void fill_symlink_stat(struct stat *st, const char *target) {
    memset(st, 0, sizeof(*st));
    st->st_mode = S_IFLNK | 0777;
    st->st_nlink = 1;
    st->st_uid = getuid();
    st->st_gid = getgid();
    st->st_size = (off_t)strlen(target);
    st->st_blksize = 4096;
}

static int fake_lstat_if_sidecar(const char *path, struct stat *st) {
    char target[PATH_MAX];

    if (st == NULL || !sidecar_link_exists(path, target, sizeof(target))) {
        return -1;
    }
    fill_symlink_stat(st, target);
    log_msg("winnative steamwebhelper preload: lstat shim path=%s target=%s",
            path, target);
    errno = 0;
    return 0;
}

int symlink(const char *target, const char *linkpath) {
    int rc = (int)syscall_result(raw_syscall6(SYS_symlinkat, (long)target, AT_FDCWD,
                                              (long)linkpath, 0, 0, 0));
    if (rc == 0) {
        cache_link(linkpath, target);
    } else if (errno == ENOSYS && is_steam_singleton_path(linkpath)) {
        cache_link(linkpath, target);
        log_msg("winnative steamwebhelper preload: symlink ENOSYS shim link=%s target=%s",
                linkpath, target);
        errno = 0;
        return 0;
    }
    return rc;
}

int symlinkat(const char *target, int newdirfd, const char *linkpath) {
    int rc = (int)syscall_result(raw_syscall6(SYS_symlinkat, (long)target, newdirfd,
                                              (long)linkpath, 0, 0, 0));
    if (rc == 0) {
        cache_link(linkpath, target);
    } else if (errno == ENOSYS && is_steam_singleton_path(linkpath)) {
        cache_link(linkpath, target);
        log_msg("winnative steamwebhelper preload: symlinkat ENOSYS shim link=%s target=%s",
                linkpath, target);
        errno = 0;
        return 0;
    }
    return rc;
}

ssize_t readlink(const char *path, char *buf, size_t bufsiz) {
    ssize_t rc = (ssize_t)syscall_result(raw_syscall6(SYS_readlinkat, AT_FDCWD,
                                                      (long)path, (long)buf,
                                                      (long)bufsiz, 0, 0));
    if (rc >= 0 || errno != ENOSYS) {
        return rc;
    }

    const char *target = lookup_link(path);
    if (target == NULL) {
        return rc;
    }
    errno = 0;
    return copy_target(path, target, buf, bufsiz);
}

ssize_t readlinkat(int dirfd, const char *path, char *buf, size_t bufsiz) {
    ssize_t rc = (ssize_t)syscall_result(raw_syscall6(SYS_readlinkat, dirfd,
                                                      (long)path, (long)buf,
                                                      (long)bufsiz, 0, 0));
    if (rc >= 0 || errno != ENOSYS) {
        return rc;
    }

    const char *target = lookup_link(path);
    if (target == NULL) {
        return rc;
    }
    errno = 0;
    return copy_target(path, target, buf, bufsiz);
}

ssize_t __readlink_chk(const char *path, char *buf, size_t bufsiz,
                       size_t bufsize) {
    if (bufsiz > bufsize) {
        errno = ERANGE;
        return -1;
    }
    return readlink(path, buf, bufsiz);
}

ssize_t __readlinkat_chk(int dirfd, const char *path, char *buf, size_t bufsiz,
                         size_t bufsize) {
    if (bufsiz > bufsize) {
        errno = ERANGE;
        return -1;
    }
    return readlinkat(dirfd, path, buf, bufsiz);
}

int lstat(const char *path, struct stat *st) {
    int rc = (int)syscall_result(raw_syscall6(SYS_newfstatat, AT_FDCWD,
                                              (long)path, (long)st,
                                              AT_SYMLINK_NOFOLLOW, 0, 0));
    if (rc == 0 || errno != ENOENT) {
        return rc;
    }
    return fake_lstat_if_sidecar(path, st);
}

int __lxstat(int ver, const char *path, struct stat *st) {
    (void)ver;
    return lstat(path, st);
}

int __lxstat64(int ver, const char *path, struct stat *st) {
    (void)ver;
    return lstat(path, st);
}

int access(const char *path, int mode) {
    int rc = (int)syscall_result(raw_syscall6(SYS_faccessat, AT_FDCWD,
                                              (long)path, mode, 0, 0, 0));
    if (rc == 0 || errno != ENOENT) {
        return rc;
    }
    if ((mode & X_OK) == 0 && sidecar_link_exists(path, (char[PATH_MAX]){0}, PATH_MAX)) {
        log_msg("winnative steamwebhelper preload: access shim path=%s mode=%d",
                path, mode);
        errno = 0;
        return 0;
    }
    return rc;
}

int faccessat(int dirfd, const char *path, int mode, int flags) {
    int rc = (int)syscall_result(raw_syscall6(SYS_faccessat, dirfd, (long)path,
                                              mode, flags, 0, 0));
    if (rc == 0 || errno != ENOENT || dirfd != AT_FDCWD) {
        return rc;
    }
    if ((mode & X_OK) == 0 && sidecar_link_exists(path, (char[PATH_MAX]){0}, PATH_MAX)) {
        log_msg("winnative steamwebhelper preload: faccessat shim path=%s mode=%d",
                path, mode);
        errno = 0;
        return 0;
    }
    return rc;
}

int unlink(const char *path) {
    char sidecar[PATH_MAX];
    int had_sidecar = 0;
    int rc = (int)syscall_result(raw_syscall6(SYS_unlinkat, AT_FDCWD,
                                              (long)path, 0, 0, 0, 0));
    int saved_errno = errno;

    if (sidecar_path(path, sidecar, sizeof(sidecar)) == 0) {
        had_sidecar = (raw_syscall6(SYS_unlinkat, AT_FDCWD, (long)sidecar,
                                    0, 0, 0, 0) == 0);
    }
    if (rc == 0) {
        return 0;
    }
    errno = saved_errno;
    if (errno == ENOENT && had_sidecar) {
        log_msg("winnative steamwebhelper preload: unlink sidecar-only path=%s",
                path);
        errno = 0;
        return 0;
    }
    return rc;
}

static size_t append_literal(char *buf, size_t pos, size_t size, const char *text) {
    while (text != NULL && *text != '\0' && pos + 1 < size) {
        buf[pos++] = *text++;
    }
    return pos;
}

static size_t append_long(char *buf, size_t pos, size_t size, long value) {
    char tmp[32];
    size_t count = 0;
    unsigned long n;

    if (value < 0) {
        if (pos + 1 < size) {
            buf[pos++] = '-';
        }
        n = (unsigned long)(-value);
    } else {
        n = (unsigned long)value;
    }
    do {
        tmp[count++] = (char)('0' + (n % 10));
        n /= 10;
    } while (n != 0 && count < sizeof(tmp));
    while (count > 0 && pos + 1 < size) {
        buf[pos++] = tmp[--count];
    }
    return pos;
}

static size_t append_hex_ulong(char *buf, size_t pos, size_t size,
                               unsigned long value) {
    static const char digits[] = "0123456789abcdef";
    int shift;

    pos = append_literal(buf, pos, size, "0x");
    for (shift = (int)(sizeof(value) * 8) - 4; shift > 0; shift -= 4) {
        if (((value >> shift) & 0xf) != 0) {
            break;
        }
    }
    for (; shift >= 0 && pos + 1 < size; shift -= 4) {
        buf[pos++] = digits[(value >> shift) & 0xf];
    }
    return pos;
}

static void raw_log_signal(const char *kind, int value) {
    char line[256];
    size_t pos = 0;
    int fd;

    if (g_log_path[0] == '\0') {
        return;
    }
    pos = append_literal(line, pos, sizeof(line),
                         "winnative steamwebhelper preload: ");
    pos = append_literal(line, pos, sizeof(line), kind);
    pos = append_literal(line, pos, sizeof(line), " pid=");
    pos = append_long(line, pos, sizeof(line),
                      raw_syscall6(SYS_getpid, 0, 0, 0, 0, 0, 0));
    pos = append_literal(line, pos, sizeof(line), " tid=");
    pos = append_long(line, pos, sizeof(line),
                      raw_syscall6(SYS_gettid, 0, 0, 0, 0, 0, 0));
    pos = append_literal(line, pos, sizeof(line), " value=");
    pos = append_long(line, pos, sizeof(line), value);
    if (pos + 1 < sizeof(line)) {
        line[pos++] = '\n';
    }

    fd = (int)raw_syscall6(SYS_openat, AT_FDCWD, (long)g_log_path,
                           O_WRONLY | O_CREAT | O_APPEND, 0600, 0, 0);
    if (fd >= 0) {
        raw_syscall6(SYS_write, fd, (long)line, (long)pos, 0, 0, 0);
        raw_syscall6(SYS_close, fd, 0, 0, 0, 0, 0);
    }
}

static void raw_log_signal_context(const char *kind, int sig, void *context) {
    char line[512];
    size_t pos = 0;
    int fd;
    unsigned long pc = 0;
    unsigned long lr = 0;
    unsigned long sp = 0;

#if defined(__aarch64__)
    if (context != NULL) {
        ucontext_t *uc = (ucontext_t *)context;
        pc = (unsigned long)uc->uc_mcontext.pc;
        lr = (unsigned long)uc->uc_mcontext.regs[30];
        sp = (unsigned long)uc->uc_mcontext.sp;
    }
#endif

    if (g_log_path[0] == '\0') {
        return;
    }
    pos = append_literal(line, pos, sizeof(line),
                         "winnative steamwebhelper preload: ");
    pos = append_literal(line, pos, sizeof(line), kind);
    pos = append_literal(line, pos, sizeof(line), " pid=");
    pos = append_long(line, pos, sizeof(line),
                      raw_syscall6(SYS_getpid, 0, 0, 0, 0, 0, 0));
    pos = append_literal(line, pos, sizeof(line), " tid=");
    pos = append_long(line, pos, sizeof(line),
                      raw_syscall6(SYS_gettid, 0, 0, 0, 0, 0, 0));
    pos = append_literal(line, pos, sizeof(line), " signal=");
    pos = append_long(line, pos, sizeof(line), sig);
    pos = append_literal(line, pos, sizeof(line), " pc=");
    pos = append_hex_ulong(line, pos, sizeof(line), pc);
    pos = append_literal(line, pos, sizeof(line), " lr=");
    pos = append_hex_ulong(line, pos, sizeof(line), lr);
    pos = append_literal(line, pos, sizeof(line), " sp=");
    pos = append_hex_ulong(line, pos, sizeof(line), sp);
    if (pos + 1 < sizeof(line)) {
        line[pos++] = '\n';
    }

    fd = (int)raw_syscall6(SYS_openat, AT_FDCWD, (long)g_log_path,
                           O_WRONLY | O_CREAT | O_APPEND, 0600, 0, 0);
    if (fd >= 0) {
        raw_syscall6(SYS_write, fd, (long)line, (long)pos, 0, 0, 0);
        raw_syscall6(SYS_close, fd, 0, 0, 0, 0, 0);
    }
}

static int should_block_crashpad_ptrace(long request) {
    const char *visible = getenv("WINNATIVE_STEAM_VISIBLE_UI");

    if (visible == NULL || strcmp(visible, "1") != 0) {
        return 0;
    }

    return request == PTRACE_ATTACH || request == PTRACE_SEIZE;
}

long ptrace(enum __ptrace_request request, ...) {
    va_list ap;
    long pid;
    long addr;
    long data;

    va_start(ap, request);
    pid = va_arg(ap, long);
    addr = va_arg(ap, long);
    data = va_arg(ap, long);
    va_end(ap);

    if (should_block_crashpad_ptrace((long)request)) {
        log_msg("winnative steamwebhelper preload: ptrace attach shim request=%ld pid=%ld EPERM",
                (long)request, pid);
        errno = EPERM;
        return -1;
    }

    return syscall_result(raw_syscall6(SYS_ptrace, (long)request, pid, addr, data, 0, 0));
}

static void crash_signal_handler(int sig, siginfo_t *info, void *context) {
    (void)info;
    raw_log_signal_context("caught fatal signal", sig, context);
    raw_syscall6(SYS_rt_sigaction, sig, 0, 0, 8, 0, 0);
    raw_syscall6(SYS_tgkill, raw_syscall6(SYS_getpid, 0, 0, 0, 0, 0, 0),
                 raw_syscall6(SYS_gettid, 0, 0, 0, 0, 0, 0), sig, 0, 0, 0);
    raw_syscall6(SYS_exit_group, 128 + sig, 0, 0, 0, 0, 0);
}

__attribute__((constructor)) static void install_crash_logging(void) {
    struct sigaction sa;

    init_log_path();
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = crash_signal_handler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = SA_SIGINFO | SA_RESETHAND;
    sigaction(SIGABRT, &sa, NULL);
    sigaction(SIGSEGV, &sa, NULL);
    sigaction(SIGBUS, &sa, NULL);
    sigaction(SIGILL, &sa, NULL);
    sigaction(SIGTRAP, &sa, NULL);
    sigaction(SIGSYS, &sa, NULL);
}

void abort(void) {
    raw_log_signal("abort called", SIGABRT);
    raw_syscall6(SYS_tgkill, raw_syscall6(SYS_getpid, 0, 0, 0, 0, 0, 0),
                 raw_syscall6(SYS_gettid, 0, 0, 0, 0, 0, 0), SIGABRT, 0, 0, 0);
    raw_syscall6(SYS_exit_group, 134, 0, 0, 0, 0, 0);
    __builtin_unreachable();
}

long syscall(long number, ...) {
    va_list ap;
    long a1;
    long a2;
    long a3;
    long a4;
    long a5;
    long a6;
    long rc;

    va_start(ap, number);
    a1 = va_arg(ap, long);
    a2 = va_arg(ap, long);
    a3 = va_arg(ap, long);
    a4 = va_arg(ap, long);
    a5 = va_arg(ap, long);
    a6 = va_arg(ap, long);
    va_end(ap);

    if (number == SYS_landlock_create_ruleset) {
        log_msg("winnative steamwebhelper preload: landlock_create_ruleset shim flags=%ld ENOSYS->EOPNOTSUPP",
                a3);
        errno = EOPNOTSUPP;
        return -1;
    }

    if (number == SYS_ptrace && should_block_crashpad_ptrace(a1)) {
        log_msg("winnative steamwebhelper preload: syscall ptrace attach shim request=%ld pid=%ld EPERM",
                a1, a2);
        errno = EPERM;
        return -1;
    }

    rc = syscall_result(raw_syscall6(number, a1, a2, a3, a4, a5, a6));
    if (rc >= 0 || errno != ENOSYS) {
        return rc;
    }

    if (number == SYS_symlinkat && is_steam_singleton_path((const char *)a3)) {
        cache_link((const char *)a3, (const char *)a1);
        log_msg("winnative steamwebhelper preload: syscall symlinkat ENOSYS shim link=%s target=%s",
                (const char *)a3, (const char *)a1);
        errno = 0;
        return 0;
    }

    if (number == SYS_readlinkat) {
        const char *target = lookup_link((const char *)a2);
        if (target != NULL) {
            errno = 0;
            return copy_target((const char *)a2, target, (char *)a3, (size_t)a4);
        }
    }

    if (number == SYS_unlinkat && is_steam_singleton_path((const char *)a2)) {
        char sidecar[PATH_MAX];
        if (sidecar_path((const char *)a2, sidecar, sizeof(sidecar)) == 0) {
            raw_syscall6(SYS_unlinkat, a1, (long)sidecar, a3, 0, 0, 0);
        }
    }

    return rc;
}
