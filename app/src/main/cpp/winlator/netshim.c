#define _GNU_SOURCE 1
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <stdarg.h>
#include <unistd.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <errno.h>
#include <ifaddrs.h>
#include <net/if.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <linux/if_packet.h>
#include <linux/if_arp.h>
#include <linux/sockios.h>

#define WN_IF_NAME  "eth0"
#define WN_IF_INDEX 2
#define WN_IF_MTU   1500
#define WN_IF_ADDR  0x0a000002u
#define WN_IF_MASK  0xffffff00u
#define WN_IF_BCAST 0x0a0000ffu

static int wn_enabled = 1;
static int wn_logfd = -2;

static void wn_log(const char *fmt, ...) {
    if (wn_logfd == -2) {
        const char *p = getenv("WN_NET_LOG");
        wn_logfd = p && *p ? open(p, O_WRONLY | O_CREAT | O_APPEND, 0644) : -1;
    }
    if (wn_logfd < 0) return;
    char buf[512];
    int n = snprintf(buf, sizeof(buf), "[netshim %d] ", (int) getpid());
    va_list ap;
    va_start(ap, fmt);
    n += vsnprintf(buf + n, sizeof(buf) - n - 2, fmt, ap);
    va_end(ap);
    if (n > (int) sizeof(buf) - 2) n = sizeof(buf) - 2;
    buf[n++] = '\n';
    ssize_t ignored = write(wn_logfd, buf, n);
    (void) ignored;
}

static void wn_mac_for(const char *iface, unsigned char *out) {
    const char *seed = getenv("WN_NET_MAC_SEED");
    unsigned long h = 0x811c9dc5UL;
    if (seed && *seed) {
        for (const char *p = seed; *p; ++p) {
            h ^= (unsigned char) *p;
            h *= 16777619UL;
        }
    } else {
        h = 0x5741524eUL;
    }
    if (iface) {
        for (const char *p = iface; *p; ++p) {
            h ^= (unsigned char) *p;
            h *= 16777619UL;
        }
    }
    out[0] = 0x02;
    out[1] = (unsigned char) (h >> 24);
    out[2] = (unsigned char) (h >> 16);
    out[3] = (unsigned char) (h >> 8);
    out[4] = (unsigned char) h;
    out[5] = (unsigned char) ((h >> 29) ^ 0x5a);
}

static void wn_mac(unsigned char *out) {
    wn_mac_for(NULL, out);
}

__attribute__((constructor)) static void wn_init(void) {
    const char *off = getenv("WN_NET_SHIM");
    if (off && off[0] == '0') wn_enabled = 0;
    unsigned char m[6];
    wn_mac(m);
    wn_log("loaded enabled=%d mac=%02x:%02x:%02x:%02x:%02x:%02x",
           wn_enabled, m[0], m[1], m[2], m[3], m[4], m[5]);
}

struct wn_block {
    struct ifaddrs inet;
    struct ifaddrs pkt;
    struct sockaddr_in addr;
    struct sockaddr_in mask;
    struct sockaddr_in bcast;
    struct sockaddr_ll ll;
    char name_inet[IFNAMSIZ];
    char name_pkt[IFNAMSIZ];
};

static int wn_real_ifaddrs_usable(struct ifaddrs *list) {
    int usable = 0;
    for (struct ifaddrs *e = list; e; e = e->ifa_next) {
        if (!e->ifa_addr) continue;
        if (e->ifa_addr->sa_family != AF_INET) continue;
        if (e->ifa_flags & IFF_LOOPBACK) continue;
        usable++;
    }
    return usable;
}

int getifaddrs(struct ifaddrs **ifap) {
    static int (*real)(struct ifaddrs **);
    if (!real) real = (int (*)(struct ifaddrs **)) dlsym(RTLD_NEXT, "getifaddrs");
    if (!ifap) return -1;

    if (wn_enabled && real) {
        struct ifaddrs *probe = NULL;
        int rc = real(&probe);
        int usable = rc == 0 ? wn_real_ifaddrs_usable(probe) : -1;
        wn_log("getifaddrs real rc=%d usable_inet=%d", rc, usable);
        if (rc == 0 && usable > 0) {
            *ifap = probe;
            return 0;
        }
        if (rc == 0 && probe) {
            static void (*realfree)(struct ifaddrs *);
            if (!realfree) realfree = (void (*)(struct ifaddrs *)) dlsym(RTLD_NEXT, "freeifaddrs");
            if (realfree) realfree(probe);
        }
    } else if (!wn_enabled && real) {
        return real(ifap);
    }

    struct wn_block *b = (struct wn_block *) calloc(1, sizeof(*b));
    if (!b) return -1;

    snprintf(b->name_inet, sizeof(b->name_inet), "%s", WN_IF_NAME);
    snprintf(b->name_pkt, sizeof(b->name_pkt), "%s", WN_IF_NAME);

    b->addr.sin_family = AF_INET;
    b->addr.sin_addr.s_addr = htonl(WN_IF_ADDR);
    b->mask.sin_family = AF_INET;
    b->mask.sin_addr.s_addr = htonl(WN_IF_MASK);
    b->bcast.sin_family = AF_INET;
    b->bcast.sin_addr.s_addr = htonl(WN_IF_BCAST);

    b->ll.sll_family = AF_PACKET;
    b->ll.sll_ifindex = WN_IF_INDEX;
    b->ll.sll_hatype = ARPHRD_ETHER;
    b->ll.sll_halen = 6;
    wn_mac(b->ll.sll_addr);

    b->inet.ifa_next = &b->pkt;
    b->inet.ifa_name = b->name_inet;
    b->inet.ifa_flags = IFF_UP | IFF_RUNNING | IFF_BROADCAST | IFF_MULTICAST;
    b->inet.ifa_addr = (struct sockaddr *) &b->addr;
    b->inet.ifa_netmask = (struct sockaddr *) &b->mask;
    b->inet.ifa_broadaddr = (struct sockaddr *) &b->bcast;

    b->pkt.ifa_next = NULL;
    b->pkt.ifa_name = b->name_pkt;
    b->pkt.ifa_flags = b->inet.ifa_flags;
    b->pkt.ifa_addr = (struct sockaddr *) &b->ll;

    *ifap = &b->inet;
    wn_log("getifaddrs -> SYNTHETIC %s", WN_IF_NAME);
    return 0;
}

void freeifaddrs(struct ifaddrs *ifa) {
    static void (*real)(struct ifaddrs *);
    if (!real) real = (void (*)(struct ifaddrs *)) dlsym(RTLD_NEXT, "freeifaddrs");
    if (!ifa) return;
    if (ifa->ifa_name && strcmp(ifa->ifa_name, WN_IF_NAME) == 0 && ifa->ifa_next
        && ifa->ifa_next->ifa_addr && ifa->ifa_next->ifa_addr->sa_family == AF_PACKET) {
        free(ifa);
        return;
    }
    if (real) real(ifa);
}

struct if_nameindex *if_nameindex(void) {
    static struct if_nameindex *(*real)(void);
    if (!real) real = (struct if_nameindex *(*)(void)) dlsym(RTLD_NEXT, "if_nameindex");

    if (wn_enabled && real) {
        struct if_nameindex *probe = real();
        int n = 0;
        if (probe) for (struct if_nameindex *e = probe; e->if_index; e++) n++;
        wn_log("if_nameindex real -> %d entries", n);
        if (n > 0) return probe;
        if (probe) {
            static void (*realfree)(struct if_nameindex *);
            if (!realfree) realfree = (void (*)(struct if_nameindex *)) dlsym(RTLD_NEXT, "if_freenameindex");
            if (realfree) realfree(probe);
        }
    } else if (!wn_enabled && real) {
        return real();
    }

    struct if_nameindex *a = (struct if_nameindex *) calloc(2, sizeof(*a));
    if (!a) return NULL;
    a[0].if_index = WN_IF_INDEX;
    a[0].if_name = strdup(WN_IF_NAME);
    wn_log("if_nameindex -> SYNTHETIC %s idx=%d", WN_IF_NAME, WN_IF_INDEX);
    return a;
}

void if_freenameindex(struct if_nameindex *p) {
    static void (*real)(struct if_nameindex *);
    if (!real) real = (void (*)(struct if_nameindex *)) dlsym(RTLD_NEXT, "if_freenameindex");
    if (!p) return;
    if (p[0].if_index == WN_IF_INDEX && p[0].if_name && strcmp(p[0].if_name, WN_IF_NAME) == 0
        && p[1].if_index == 0) {
        free(p[0].if_name);
        free(p);
        return;
    }
    if (real) real(p);
}

unsigned int if_nametoindex(const char *name) {
    static unsigned int (*real)(const char *);
    if (!real) real = (unsigned int (*)(const char *)) dlsym(RTLD_NEXT, "if_nametoindex");
    unsigned int r = real ? real(name) : 0;
    if (r == 0 && wn_enabled && name && strcmp(name, WN_IF_NAME) == 0) return WN_IF_INDEX;
    return r;
}

char *if_indextoname(unsigned int idx, char *name) {
    static char *(*real)(unsigned int, char *);
    if (!real) real = (char *(*)(unsigned int, char *)) dlsym(RTLD_NEXT, "if_indextoname");
    char *r = real ? real(idx, name) : NULL;
    if (!r && wn_enabled && idx == WN_IF_INDEX && name) {
        snprintf(name, IFNAMSIZ, "%s", WN_IF_NAME);
        return name;
    }
    return r;
}

static int wn_mac_unusable(const struct ifreq *ifr) {
    const unsigned char *a = (const unsigned char *) ifr->ifr_hwaddr.sa_data;
    if (ifr->ifr_hwaddr.sa_family != ARPHRD_ETHER) return 1;
    int zero = 1, placeholder = (a[0] == 0x02);
    for (int i = 0; i < 6; i++) {
        if (a[i]) zero = 0;
        if (i && a[i]) placeholder = 0;
    }
    return zero || placeholder;
}

int ioctl(int fd, int req, ...) {
    static int (*real)(int, int, void *);
    if (!real) real = (int (*)(int, int, void *)) dlsym(RTLD_NEXT, "ioctl");
    va_list ap;
    va_start(ap, req);
    void *arg = va_arg(ap, void *);
    va_end(ap);

    int rc = real(fd, req, arg);
    if (!wn_enabled || !arg) return rc;

    unsigned int ureq = (unsigned int) req;
    if (ureq != SIOCGIFHWADDR && ureq != SIOCGIFFLAGS && ureq != SIOCGIFMTU
        && ureq != SIOCGIFINDEX && ureq != SIOCGIFADDR && ureq != SIOCGIFNETMASK
        && ureq != SIOCGIFBRDADDR)
        return rc;

    struct ifreq *ifr = (struct ifreq *) arg;
    char name[IFNAMSIZ + 1];
    memcpy(name, ifr->ifr_name, IFNAMSIZ);
    name[IFNAMSIZ] = 0;
    int synth = 0;

    if (ureq == SIOCGIFHWADDR) {
        const unsigned char *a = (const unsigned char *) ifr->ifr_hwaddr.sa_data;
        wn_log("ioctl SIOCGIFHWADDR name=%s rc=%d family=%d mac=%02x:%02x:%02x:%02x:%02x:%02x",
               name, rc, rc == 0 ? ifr->ifr_hwaddr.sa_family : -1,
               a[0], a[1], a[2], a[3], a[4], a[5]);
        int loopback = strcmp(name, "lo") == 0;
        if (rc != 0 || wn_mac_unusable(ifr)) {
            memset(&ifr->ifr_hwaddr, 0, sizeof(ifr->ifr_hwaddr));
            if (loopback) {
                ifr->ifr_hwaddr.sa_family = ARPHRD_LOOPBACK;
            } else {
                ifr->ifr_hwaddr.sa_family = ARPHRD_ETHER;
                wn_mac_for(name, (unsigned char *) ifr->ifr_hwaddr.sa_data);
            }
            synth = 1;
        }
    } else if (rc != 0) {
        switch (ureq) {
        case SIOCGIFFLAGS:
            ifr->ifr_flags = IFF_UP | IFF_RUNNING | IFF_BROADCAST | IFF_MULTICAST;
            synth = 1;
            break;
        case SIOCGIFMTU:
            ifr->ifr_mtu = WN_IF_MTU;
            synth = 1;
            break;
        case SIOCGIFINDEX:
            ifr->ifr_ifindex = WN_IF_INDEX;
            synth = 1;
            break;
        case SIOCGIFADDR:
        case SIOCGIFNETMASK:
        case SIOCGIFBRDADDR: {
            struct sockaddr_in *sin = (struct sockaddr_in *) &ifr->ifr_addr;
            memset(sin, 0, sizeof(*sin));
            sin->sin_family = AF_INET;
            sin->sin_addr.s_addr = htonl(ureq == SIOCGIFADDR ? WN_IF_ADDR
                                       : ureq == SIOCGIFNETMASK ? WN_IF_MASK : WN_IF_BCAST);
            synth = 1;
            break;
        }
        default:
            break;
        }
    }

    if (synth) {
        const unsigned char *a = (const unsigned char *) ifr->ifr_hwaddr.sa_data;
        wn_log("ioctl 0x%x name=%s real_rc=%d -> SYNTHETIC family=%d mac=%02x:%02x:%02x:%02x:%02x:%02x",
               ureq, name, rc, ifr->ifr_hwaddr.sa_family,
               a[0], a[1], a[2], a[3], a[4], a[5]);
        return 0;
    }
    return rc;
}

FILE *fopen(const char *path, const char *mode) {
    static FILE *(*real)(const char *, const char *);
    if (!real) real = (FILE *(*)(const char *, const char *)) dlsym(RTLD_NEXT, "fopen");
    if (wn_enabled && path && strncmp(path, "/sys/class/net/", 15) == 0
        && strstr(path, "/carrier")) {
        FILE *f = real(path, mode);
        if (f) return f;
        wn_log("fopen(%s) -> SYNTHETIC carrier=1", path);
        return fmemopen((void *) "1\n", 2, "r");
    }
    return real(path, mode);
}
