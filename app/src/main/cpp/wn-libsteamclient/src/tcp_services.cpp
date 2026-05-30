#include "wn_libsteamclient/tcp_services.h"
#include "wn_libsteamclient/callbacks.h"
#include "wn_libsteamclient/runtime_state.h"

#include <android/log.h>
#include <arpa/inet.h>
#include <atomic>
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <mutex>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <string>
#include <sys/socket.h>
#include <sys/types.h>
#include <thread>
#include <unistd.h>
#include <vector>

#define WN_TAG "WnLibSteamClient"
#define WN_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  WN_TAG, __VA_ARGS__)
#define WN_LOGW(...) __android_log_print(ANDROID_LOG_WARN,  WN_TAG, __VA_ARGS__)
#define WN_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, WN_TAG, __VA_ARGS__)

namespace wn_libsteamclient {

namespace {

std::once_flag g_start_once;
std::atomic<int> g_accepted_count{0};
std::atomic<bool> g_any_bound{false};

int parse_port(const char* env_value, int fallback_port) {
    if (!env_value || !*env_value) return fallback_port;
    const char* colon = std::strchr(env_value, ':');
    const char* port_str = colon ? colon + 1 : env_value;
    char* end = nullptr;
    long p = std::strtol(port_str, &end, 10);
    if (end == port_str || p <= 0 || p > 65535) {
        WN_LOGW("tcp_services: malformed env value \"%s\", falling back to :%d",
                env_value, fallback_port);
        return fallback_port;
    }
    return static_cast<int>(p);
}

int bind_listener(int port, const char* svc_name) {
    int fd = ::socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) {
        WN_LOGE("tcp_services[%s]: socket() failed: %s", svc_name, std::strerror(errno));
        return -1;
    }
    int one = 1;
    ::setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));
    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_port   = htons(static_cast<uint16_t>(port));
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);  // 127.0.0.1
    if (::bind(fd, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) != 0) {
        WN_LOGE("tcp_services[%s]: bind(127.0.0.1:%d) failed: %s "
                "(port likely already in use — prebuilt libsteamclient.so "
                "may still own it from a previous launch cycle)",
                svc_name, port, std::strerror(errno));
        ::close(fd);
        return -1;
    }
    if (::listen(fd, 8) != 0) {
        WN_LOGE("tcp_services[%s]: listen() failed: %s", svc_name, std::strerror(errno));
        ::close(fd);
        return -1;
    }
    WN_LOGI("tcp_services[%s]: listening on 127.0.0.1:%d (fd=%d)", svc_name, port, fd);
    return fd;
}

std::string hex_dump(const uint8_t* buf, size_t n, size_t max_bytes_in_log = 96) {
    size_t shown = std::min(n, max_bytes_in_log);
    std::string out;
    out.reserve(shown * 3 + 16);
    for (size_t i = 0; i < shown; ++i) {
        char b[4];
        std::snprintf(b, sizeof(b), "%02x ", buf[i]);
        out.append(b);
    }
    if (!out.empty()) out.pop_back();  // strip trailing space
    if (shown < n) {
        char tail[32];
        std::snprintf(tail, sizeof(tail), " ...(+%zu B)", n - shown);
        out.append(tail);
    }
    return out;
}

bool read_exact(int fd, uint8_t* out, size_t n) {
    size_t got = 0;
    while (got < n) {
        ssize_t r = ::recv(fd, out + got, n - got, 0);
        if (r > 0) { got += static_cast<size_t>(r); continue; }
        if (r == 0) return false;                       // EOF
        if (errno == EINTR) continue;
        return false;                                   // hard error
    }
    return true;
}

void handle_connection(int conn_fd, const char* svc_name) {
    uint8_t header[4];
    if (!read_exact(conn_fd, header, sizeof(header))) {
        WN_LOGI("tcp_services[%s]: conn fd=%d closed before any header",
                svc_name, conn_fd);
        ::close(conn_fd);
        return;
    }
    uint32_t first_len =
        static_cast<uint32_t>(header[0]) |
        (static_cast<uint32_t>(header[1]) <<  8) |
        (static_cast<uint32_t>(header[2]) << 16) |
        (static_cast<uint32_t>(header[3]) << 24);
    const bool framed = (first_len > 0 && first_len <= 256 * 1024);

    if (!framed) {
        WN_LOGI("tcp_services[%s]: conn fd=%d raw-stream (first 4B=%s)",
                svc_name, conn_fd, hex_dump(header, sizeof(header)).c_str());
        uint8_t chunk[256];
        for (;;) {
            ssize_t r = ::recv(conn_fd, chunk, sizeof(chunk), 0);
            if (r > 0) {
                WN_LOGI("tcp_services[%s]: conn fd=%d raw %zd B: %s",
                        svc_name, conn_fd, r,
                        hex_dump(chunk, static_cast<size_t>(r)).c_str());
                continue;
            }
            if (r < 0 && errno == EINTR) continue;
            break;
        }
        WN_LOGI("tcp_services[%s]: conn fd=%d closed (raw mode)", svc_name, conn_fd);
        ::close(conn_fd);
        return;
    }

    WN_LOGI("tcp_services[%s]: conn fd=%d framed-mode entry, first body=%u B",
            svc_name, conn_fd, first_len);
    int frame_idx = 0;
    uint32_t length = first_len;
    for (;;) {
        std::vector<uint8_t> body(length);
        if (!read_exact(conn_fd, body.data(), length)) {
            WN_LOGI("tcp_services[%s]: conn fd=%d EOF mid-frame %d (expected %u B)",
                    svc_name, conn_fd, frame_idx, length);
            break;
        }
        WN_LOGI("tcp_services[%s]: conn fd=%d frame[%d] %u B: %s",
                svc_name, conn_fd, frame_idx, length,
                hex_dump(body.data(), body.size()).c_str());
        ++frame_idx;
        if (!read_exact(conn_fd, header, sizeof(header))) {
            WN_LOGI("tcp_services[%s]: conn fd=%d closed cleanly after %d frame(s)",
                    svc_name, conn_fd, frame_idx);
            break;
        }
        length =
            static_cast<uint32_t>(header[0]) |
            (static_cast<uint32_t>(header[1]) <<  8) |
            (static_cast<uint32_t>(header[2]) << 16) |
            (static_cast<uint32_t>(header[3]) << 24);
        if (length == 0 || length > 1024 * 1024) {
            WN_LOGW("tcp_services[%s]: conn fd=%d unreasonable next-frame "
                    "length=%u after %d frame(s) — closing", svc_name, conn_fd,
                    length, frame_idx);
            break;
        }
    }
    ::close(conn_fd);
}

void listener_loop(int listen_fd, std::string svc_name) {
    for (;;) {
        sockaddr_in peer{};
        socklen_t plen = sizeof(peer);
        int conn = ::accept(listen_fd, reinterpret_cast<sockaddr*>(&peer), &plen);
        if (conn < 0) {
            if (errno == EINTR) continue;
            WN_LOGW("tcp_services[%s]: accept() failed: %s — terminating loop "
                    "(emitting IPCFailure_t kFailurePipeFail)",
                    svc_name.c_str(), std::strerror(errno));
            namespace cb = wn_libsteamclient::callbacks;
            cb::IPCFailure payload{};
            payload.m_eFailureType = cb::kFailurePipeFail;
            wn_libsteamclient::push_callback(
                wn_libsteamclient::state().user.load(),
                cb::kIPCFailure, &payload, sizeof(payload));
            break;
        }
        g_accepted_count.fetch_add(1, std::memory_order_relaxed);
        char peer_ip[INET_ADDRSTRLEN] = {0};
        ::inet_ntop(AF_INET, &peer.sin_addr, peer_ip, sizeof(peer_ip));
        WN_LOGI("tcp_services[%s]: accepted from %s:%u (conn fd=%d, total=%d)",
                svc_name.c_str(), peer_ip, ntohs(peer.sin_port),
                conn, g_accepted_count.load(std::memory_order_relaxed));
        std::thread(handle_connection, conn, svc_name.c_str()).detach();
    }
    ::close(listen_fd);
}

bool spawn_service(const char* env_key, int fallback_port, const char* svc_name) {
    int port = parse_port(::getenv(env_key), fallback_port);
    int fd = bind_listener(port, svc_name);
    if (fd < 0) return false;
    std::thread(listener_loop, fd, std::string(svc_name)).detach();
    g_any_bound.store(true);
    return true;
}

void start_tcp_services_once() {
    bool a = spawn_service("Steam3Master",       57343, "Steam3Master");
    bool b = spawn_service("SteamClientService", 57344, "SteamClientService");
    WN_LOGI("tcp_services: Steam3Master=%s SteamClientService=%s",
            a ? "OK" : "FAIL", b ? "OK" : "FAIL");
}

}  // namespace

bool start_tcp_services() {
    std::call_once(g_start_once, start_tcp_services_once);
    return g_any_bound.load();
}

int accepted_connection_count() {
    return g_accepted_count.load(std::memory_order_relaxed);
}

}  // namespace wn_libsteamclient
