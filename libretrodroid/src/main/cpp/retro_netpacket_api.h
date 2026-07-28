#ifndef LIBRETRODROID_RETRO_NETPACKET_API_H
#define LIBRETRODROID_RETRO_NETPACKET_API_H

#include "../../libretro-common/include/libretro.h"

/* WinNative env-78 netpacket API (avoid patching pinned libretro-common). */
#undef RETRO_ENVIRONMENT_SET_NETPACKET_INTERFACE
#define RETRO_ENVIRONMENT_SET_NETPACKET_INTERFACE 78

#ifndef RETRO_NETPACKET_UNRELIABLE
#define RETRO_NETPACKET_UNRELIABLE  0
#define RETRO_NETPACKET_RELIABLE    (1 << 0)
#define RETRO_NETPACKET_UNSEQUENCED (1 << 1)
#endif

#ifndef RETRO_NETPACKET_FLUSH_HINT
#define RETRO_NETPACKET_FLUSH_HINT  (1 << 2)
#endif

#ifndef RETRO_NETPACKET_BROADCAST
#define RETRO_NETPACKET_BROADCAST 0xFFFF
#endif

typedef void (RETRO_CALLCONV *wn_netpacket_send_t)(
    int flags, const void *buf, size_t len, uint16_t client_id);

typedef void (RETRO_CALLCONV *wn_netpacket_poll_receive_t)(void);

typedef void (RETRO_CALLCONV *wn_netpacket_start_t)(
    uint16_t client_id,
    wn_netpacket_send_t send_fn,
    wn_netpacket_poll_receive_t poll_receive_fn);

typedef void (RETRO_CALLCONV *wn_netpacket_receive_t)(
    const void *buf, size_t len, uint16_t client_id);

typedef void (RETRO_CALLCONV *wn_netpacket_stop_t)(void);

typedef void (RETRO_CALLCONV *wn_netpacket_poll_t)(void);

typedef bool (RETRO_CALLCONV *wn_netpacket_connected_t)(uint16_t client_id);

typedef void (RETRO_CALLCONV *wn_netpacket_disconnected_t)(uint16_t client_id);

struct wn_netpacket_callback {
    wn_netpacket_start_t start;
    wn_netpacket_receive_t receive;
    wn_netpacket_stop_t stop;
    wn_netpacket_poll_t poll;
    wn_netpacket_connected_t connected;
    wn_netpacket_disconnected_t disconnected;
    const char *protocol_version;
};

#endif
