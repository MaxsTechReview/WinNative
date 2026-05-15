#define _GNU_SOURCE

#include <errno.h>
#include <fcntl.h>
#include <libgen.h>
#include <limits.h>
#include <sched.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static void redirect_webhelper_log(void) {
    const char *override = getenv("WINNATIVE_STEAMWEBHELPER_LOG");
    const char *home = getenv("HOME");
    char path[PATH_MAX];

    if (override != NULL && override[0] != '\0') {
        if (snprintf(path, sizeof(path), "%s", override) >= (int)sizeof(path)) {
            return;
        }
    } else if (home != NULL && home[0] != '\0') {
        if (snprintf(path, sizeof(path), "%s/.steam/steam/logs/steamwebhelper.log", home) >=
            (int)sizeof(path)) {
            return;
        }
    } else {
        if (snprintf(path, sizeof(path), "/tmp/winnative-steamwebhelper.log") >=
            (int)sizeof(path)) {
            return;
        }
    }

    int fd = open(path, O_WRONLY | O_CREAT | O_APPEND, 0600);
    if (fd < 0) {
        return;
    }

    dup2(fd, STDOUT_FILENO);
    dup2(fd, STDERR_FILENO);
    if (fd > STDERR_FILENO) {
        close(fd);
    }
}

static int resolve_self_dir(char *dir, size_t dir_size) {
    char self[PATH_MAX];
    ssize_t len = readlink("/proc/self/exe", self, sizeof(self) - 1);
    if (len < 0 || len >= (ssize_t)sizeof(self)) {
        return -1;
    }
    self[len] = '\0';

    char tmp[PATH_MAX];
    if (snprintf(tmp, sizeof(tmp), "%s", self) >= (int)sizeof(tmp)) {
        return -1;
    }

    char *parent = dirname(tmp);
    if (parent == NULL || parent[0] == '\0') {
        return -1;
    }
    if (snprintf(dir, dir_size, "%s", parent) >= (int)dir_size) {
        return -1;
    }
    return 0;
}

static int has_arg_prefix(int argc, char **argv, const char *prefix) {
    size_t len = strlen(prefix);
    for (int i = 1; i < argc; i++) {
        if (strncmp(argv[i], prefix, len) == 0) {
            return 1;
        }
    }
    return 0;
}

static int has_arg(int argc, char **argv, const char *arg) {
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], arg) == 0) {
            return 1;
        }
    }
    return 0;
}

static int output_has_arg(int argc, char **argv, const char *arg) {
    for (int i = 1; i < argc; i++) {
        if (argv[i] != NULL && strcmp(argv[i], arg) == 0) {
            return 1;
        }
    }
    return 0;
}

static const char *find_arg_value(int argc, char **argv, const char *prefix) {
    size_t len = strlen(prefix);
    for (int i = 1; i < argc; i++) {
        if (strncmp(argv[i], prefix, len) == 0) {
            return argv[i] + len;
        }
    }
    return NULL;
}

static void unlink_path_and_sidecar(const char *path) {
    char sidecar[PATH_MAX];

    unlink(path);
    if (snprintf(sidecar, sizeof(sidecar), "%s.winnative-readlink-target", path) <
        (int)sizeof(sidecar)) {
        unlink(sidecar);
    }
}

static void cleanup_process_singleton_state(int argc, char **argv) {
    static const char *names[] = {
        "SingletonCookie",
        "SingletonLock",
        "SingletonSocket",
    };
    const char *cache_dir = find_arg_value(argc, argv, "-cachedir=");
    char path[PATH_MAX];

    if (cache_dir == NULL || cache_dir[0] == '\0') {
        return;
    }

    for (size_t i = 0; i < sizeof(names) / sizeof(names[0]); i++) {
        if (snprintf(path, sizeof(path), "%s/%s", cache_dir, names[i]) <
            (int)sizeof(path)) {
            unlink_path_and_sidecar(path);
        }
    }
    fprintf(stderr, "winnative steamwebhelper wrapper: cleared ProcessSingleton state in %s\n",
            cache_dir);
}

static int is_visible_ui_launch(void) {
    const char *value = getenv("WINNATIVE_STEAM_VISIBLE_UI");
    return value != NULL && strcmp(value, "1") == 0;
}

static void prepend_ld_preload(const char *library) {
    const char *old_value = getenv("LD_PRELOAD");
    char value[PATH_MAX * 2];

    if (old_value == NULL || old_value[0] == '\0') {
        snprintf(value, sizeof(value), "%s", library);
    } else if (strstr(old_value, library) != NULL) {
        return;
    } else {
        snprintf(value, sizeof(value), "%s:%s", library, old_value);
    }
    setenv("LD_PRELOAD", value, 1);
}

static int should_skip_visible_ui_arg(const char *arg) {
    return strcmp(arg, "--disable-gpu") == 0 ||
           strcmp(arg, "--disable-gpu-compositing") == 0 ||
           strcmp(arg, "--disable-gpu-rasterization") == 0 ||
           strcmp(arg, "--disable-gpu-sandbox") == 0 ||
           strcmp(arg, "--single-process") == 0 ||
           strcmp(arg, "--ignore-gpu-blocklist") == 0 ||
           strcmp(arg, "--valve-enable-site-isolation") == 0 ||
           strcmp(arg, "--disable-software-rasterizer") == 0;
}

static int should_rewrite_visible_ui_arg(const char *arg, char *out, size_t out_size) {
    const char *features_prefix = "--enable-features=";
    const char *disabled_features_prefix = "--disable-features=";
    if (strncmp(arg, features_prefix, strlen(features_prefix)) == 0) {
        const char *features = arg + strlen(features_prefix);
        char value[512];
        value[0] = '\0';

        char copy[512];
        snprintf(copy, sizeof(copy), "%s", features);
        char *saveptr = NULL;
        for (char *token = strtok_r(copy, ",", &saveptr);
             token != NULL;
             token = strtok_r(NULL, ",", &saveptr)) {
            if (strcmp(token, "V4L2VideoDecode") == 0) {
                continue;
            }
            if (value[0] != '\0') {
                strncat(value, ",", sizeof(value) - strlen(value) - 1);
            }
            strncat(value, token, sizeof(value) - strlen(value) - 1);
        }

        if (value[0] == '\0') {
            out[0] = '\0';
        } else {
            snprintf(out, out_size, "%s%s", features_prefix, value);
        }
        return 1;
    }

    if (strncmp(arg, disabled_features_prefix, strlen(disabled_features_prefix)) == 0) {
        if (strstr(arg, "NotReachedIsFatal") != NULL) {
            return 0;
        }
        snprintf(out, out_size, "%s,NotReachedIsFatal", arg);
        return 1;
    }

    return 0;
}

int main(int argc, char **argv) {
    char dir[PATH_MAX];
    char target[PATH_MAX];
    char wrapper_path[PATH_MAX];
    char browser_subprocess_arg[PATH_MAX + 32];
    int top_level_webhelper = !has_arg_prefix(argc, argv, "--type=");
    int visible_ui = is_visible_ui_launch();
    const char *common_extra_args[] = {
        "--no-sandbox",
        "--disable-seccomp-filter-sandbox",
        "--disable-setuid-sandbox",
        "--no-xshm",
        "--winhttp-proxy-resolver",
    };
    const char *hidden_extra_args[] = {
        "--single-process",
        "--disable-gpu",
        "--disable-gpu-compositing",
        "--disable-gpu-rasterization",
        "--disable-gpu-sandbox",
    };
    const char *visible_extra_args[] = {
        "--single-process",
        "--disable-gpu",
        "--disable-gpu-compositing",
        "--disable-breakpad",
        "--disable-crash-reporter",
        "--enable-logging=stderr",
        "--v=1",
        "--log-file=/opt/steam-arm64/client/logs/cef_log.txt",
    };
    const char *visible_child_extra_args[] = {
        "--disable-breakpad",
        "--disable-crash-reporter",
        "--enable-logging=stderr",
        "--v=1",
        "--log-file=/opt/steam-arm64/client/logs/cef_log.txt",
    };
    const int common_extra_count =
        (int)(sizeof(common_extra_args) / sizeof(common_extra_args[0]));
    const int hidden_extra_count =
        (int)(sizeof(hidden_extra_args) / sizeof(hidden_extra_args[0]));
    const int visible_extra_count =
        (int)(sizeof(visible_extra_args) / sizeof(visible_extra_args[0]));
    const int visible_child_extra_count =
        (int)(sizeof(visible_child_extra_args) / sizeof(visible_child_extra_args[0]));

    if (resolve_self_dir(dir, sizeof(dir)) == 0) {
        if (chdir(dir) != 0) {
            fprintf(stderr, "winnative steamwebhelper wrapper: chdir %s failed: %s\n",
                    dir, strerror(errno));
        }
        if (snprintf(wrapper_path, sizeof(wrapper_path), "%s/steamwebhelper.winnative-real", dir) >=
            (int)sizeof(wrapper_path)) {
            snprintf(wrapper_path, sizeof(wrapper_path), "./steamwebhelper.winnative-real");
        }
        if (snprintf(target, sizeof(target), "%s/steamwebhelper.winnative-real.bin", dir) >=
            (int)sizeof(target) || access(target, X_OK) != 0) {
            if (snprintf(target, sizeof(target), "%s/steamwebhelper.winnative-real", dir) >=
                (int)sizeof(target)) {
                snprintf(target, sizeof(target), "./steamwebhelper.winnative-real");
            }
        }
    } else {
        snprintf(target, sizeof(target), "./steamwebhelper.winnative-real.bin");
        snprintf(dir, sizeof(dir), ".");
        snprintf(wrapper_path, sizeof(wrapper_path), "./steamwebhelper.winnative-real");
    }

    if (getenv("DISPLAY") == NULL || getenv("DISPLAY")[0] == '\0') {
        setenv("DISPLAY", ":0", 1);
    }
    setenv("XKB_DISABLE", "1", 1);
    setenv("LIBGL_KOPPER_DISABLE", "true", 1);
    prepend_ld_preload("/lib/aarch64-linux-gnu/libwinnative-steamwebhelper-preload.so");
    cpu_set_t affinity;
    CPU_ZERO(&affinity);
    for (int cpu = 1; cpu <= 6; cpu++) {
        CPU_SET(cpu, &affinity);
    }
    sched_setaffinity(0, sizeof(affinity), &affinity);
    redirect_webhelper_log();
    if (top_level_webhelper) {
        cleanup_process_singleton_state(argc, argv);
    }

    char **child_argv = calloc((size_t)argc + 2 + common_extra_count + hidden_extra_count +
                                   visible_extra_count + visible_child_extra_count,
                               sizeof(char *));
    if (child_argv != NULL) {
        child_argv[0] = wrapper_path;
        int out = 1;
        char **rewritten_args = calloc((size_t)argc, sizeof(char *));
        for (int i = 1; i < argc; i++) {
            if (visible_ui && should_skip_visible_ui_arg(argv[i])) {
                continue;
            }
            if (visible_ui) {
                char rewritten[1024];
                if (should_rewrite_visible_ui_arg(argv[i], rewritten, sizeof(rewritten))) {
                    if (rewritten[0] == '\0') {
                        continue;
                    }
                    char *rewritten_arg = strdup(rewritten);
                    if (rewritten_arg != NULL) {
                        if (rewritten_args != NULL) {
                            rewritten_args[i] = rewritten_arg;
                        }
                        child_argv[out++] = rewritten_arg;
                    }
                    continue;
                }
            }
            child_argv[out++] = argv[i];
        }
        if (top_level_webhelper) {
            for (int i = 0; i < common_extra_count; i++) {
                if (!has_arg(argc, argv, common_extra_args[i])) {
                    child_argv[out++] = (char *)common_extra_args[i];
                }
            }
        }
        if (top_level_webhelper &&
            !has_arg_prefix(argc, argv, "--browser-subprocess-path=") &&
            snprintf(browser_subprocess_arg, sizeof(browser_subprocess_arg),
                     "--browser-subprocess-path=%s/steamwebhelper.winnative-real", dir) <
                (int)sizeof(browser_subprocess_arg)) {
            child_argv[out++] = browser_subprocess_arg;
        }
        if (top_level_webhelper) {
            if (visible_ui) {
                for (int i = 0; i < visible_extra_count; i++) {
                    if (!output_has_arg(out, child_argv, visible_extra_args[i])) {
                        child_argv[out++] = (char *)visible_extra_args[i];
                    }
                }
            } else {
                for (int i = 0; i < hidden_extra_count; i++) {
                    if (!output_has_arg(out, child_argv, hidden_extra_args[i])) {
                        child_argv[out++] = (char *)hidden_extra_args[i];
                    }
                }
            }
        }
        if (visible_ui && !top_level_webhelper &&
            !has_arg(argc, argv, "--type=crashpad-handler")) {
            for (int i = 0; i < visible_child_extra_count; i++) {
                if (!output_has_arg(out, child_argv, visible_child_extra_args[i])) {
                    child_argv[out++] = (char *)visible_child_extra_args[i];
                }
            }
        }
        fprintf(stderr,
                "winnative steamwebhelper wrapper: target=%s wrapper_path=%s top_level=%d visible_ui=%d argc_in=%d argc_out=%d\n",
                target, wrapper_path, top_level_webhelper, visible_ui, argc, out);
        fprintf(stderr, "winnative steamwebhelper wrapper: LD_PRELOAD=%s\n",
                getenv("LD_PRELOAD") != NULL ? getenv("LD_PRELOAD") : "");
        for (int i = 0; i < out; i++) {
            fprintf(stderr, "winnative steamwebhelper argv[%d]=%s\n", i, child_argv[i]);
        }
        execv(target, child_argv);
        if (rewritten_args != NULL) {
            for (int i = 0; i < argc; i++) {
                free(rewritten_args[i]);
            }
            free(rewritten_args);
        }
        free(child_argv);
    }

    argv[0] = wrapper_path;
    execv(target, argv);
    fprintf(stderr, "winnative steamwebhelper wrapper: exec %s failed: %s\n",
            target, strerror(errno));
    return 126;
}
