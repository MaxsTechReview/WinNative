#include "wn_steam/wine_bridge.h"

#include <android/log.h>

#include <arpa/inet.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>

#include <cerrno>
#include <cstring>

namespace wn_steam {

namespace {
constexpr const char* kLogTag = "WnSteamWineBr";
#define WB_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  kLogTag, __VA_ARGS__)
#define WB_LOGW(...) __android_log_print(ANDROID_LOG_WARN,  kLogTag, __VA_ARGS__)
#define WB_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)
}  // namespace

WineBridge::WineBridge() = default;

WineBridge::~WineBridge() {
    stop();
}

void WineBridge::set_observer(ClientObserver obs) {
    std::lock_guard<std::mutex> lk(obs_mu_);
    observer_ = std::move(obs);
}

std::string WineBridge::last_error() const {
    std::lock_guard<std::mutex> lk(err_mu_);
    return last_error_;
}

bool WineBridge::start(const Config& cfg) {
    if (running_.load()) return true;
    config_      = cfg;
    snoop_bytes_ = cfg.snoop_bytes;

    if (!bind_listener_(steam3_, config_.steam3_port, config_.bind_host)) {
        return false;
    }
    if (!bind_listener_(client_svc_, config_.client_svc_port, config_.bind_host)) {
        if (steam3_.fd >= 0) ::close(steam3_.fd);
        steam3_.fd = -1;
        return false;
    }

    running_.store(true);
    steam3_.stop.store(false);
    client_svc_.stop.store(false);
    steam3_.thread     = std::thread([this]() { accept_loop_(&steam3_,     config_.steam3_port);     });
    client_svc_.thread = std::thread([this]() { accept_loop_(&client_svc_, config_.client_svc_port); });

    WB_LOGI("wine bridge up: Steam3Master=%s:%u SteamClientService=%s:%u",
            config_.bind_host.c_str(), config_.steam3_port,
            config_.bind_host.c_str(), config_.client_svc_port);
    return true;
}

void WineBridge::stop() {
    if (!running_.exchange(false)) return;
    steam3_.stop.store(true);
    client_svc_.stop.store(true);
    // shutdown() + close() of the listening fd kicks accept() out of its
    // blocking call so the thread can join.
    if (steam3_.fd >= 0)     { ::shutdown(steam3_.fd, SHUT_RDWR);     ::close(steam3_.fd);     steam3_.fd = -1; }
    if (client_svc_.fd >= 0) { ::shutdown(client_svc_.fd, SHUT_RDWR); ::close(client_svc_.fd); client_svc_.fd = -1; }
    if (steam3_.thread.joinable())     steam3_.thread.join();
    if (client_svc_.thread.joinable()) client_svc_.thread.join();
    WB_LOGI("wine bridge stopped");
}

bool WineBridge::bind_listener_(Listener& l, uint16_t port,
                                 const std::string& host) {
    int fd = ::socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) {
        std::lock_guard<std::mutex> lk(err_mu_);
        last_error_ = std::string("socket(): ") + std::strerror(errno);
        WB_LOGE("%s", last_error_.c_str());
        return false;
    }
    int yes = 1;
    ::setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));

    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_port   = htons(port);
    if (::inet_pton(AF_INET, host.c_str(), &addr.sin_addr) != 1) {
        std::lock_guard<std::mutex> lk(err_mu_);
        last_error_ = "inet_pton failed for host=" + host;
        WB_LOGE("%s", last_error_.c_str());
        ::close(fd);
        return false;
    }
    if (::bind(fd, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) < 0) {
        std::lock_guard<std::mutex> lk(err_mu_);
        last_error_ = std::string("bind(") + host + ":" + std::to_string(port) + "): " +
                      std::strerror(errno);
        WB_LOGE("%s", last_error_.c_str());
        ::close(fd);
        return false;
    }
    if (::listen(fd, /*backlog*/ 8) < 0) {
        std::lock_guard<std::mutex> lk(err_mu_);
        last_error_ = std::string("listen(): ") + std::strerror(errno);
        WB_LOGE("%s", last_error_.c_str());
        ::close(fd);
        return false;
    }
    l.fd = fd;
    return true;
}

void WineBridge::accept_loop_(Listener* l, uint16_t port) {
    while (!l->stop.load()) {
        sockaddr_in peer{};
        socklen_t   peerlen = sizeof(peer);
        int client = ::accept(l->fd, reinterpret_cast<sockaddr*>(&peer), &peerlen);
        if (client < 0) {
            if (l->stop.load() || errno == EBADF) break;     // we closed during shutdown
            if (errno == EINTR) continue;
            WB_LOGW("accept(port=%u) failed: %s", port, std::strerror(errno));
            continue;
        }
        handle_connection_(client, port);
    }
}

void WineBridge::handle_connection_(int fd, uint16_t port) {
    sockaddr_in peer_addr{};
    socklen_t   peerlen = sizeof(peer_addr);
    ::getpeername(fd, reinterpret_cast<sockaddr*>(&peer_addr), &peerlen);
    char peer_str[INET_ADDRSTRLEN] = {0};
    ::inet_ntop(AF_INET, &peer_addr.sin_addr, peer_str, sizeof(peer_str));
    std::string peer = std::string(peer_str) + ":" + std::to_string(ntohs(peer_addr.sin_port));

    // Peek the first N bytes for diagnostic purposes (Phase 8b.1 — we just
    // log what Wine sends so the protocol surface area is visible).
    std::vector<uint8_t> first(snoop_bytes_);
    ssize_t n = ::recv(fd, first.data(), first.size(), 0);
    if (n < 0) {
        WB_LOGW("port=%u peer=%s recv failed: %s", port, peer.c_str(), std::strerror(errno));
        ::close(fd);
        return;
    }
    first.resize(static_cast<size_t>(n > 0 ? n : 0));

    // Log a short hex dump.
    char hex[3 * 64 + 1];
    size_t dump = std::min<size_t>(first.size(), 64);
    size_t off = 0;
    for (size_t i = 0; i < dump; ++i) {
        off += static_cast<size_t>(std::snprintf(hex + off, sizeof(hex) - off,
                                                  "%02x ", first[i]));
    }
    WB_LOGI("port=%u peer=%s rx=%zd bytes: %s",
            port, peer.c_str(), n, dump ? hex : "(none)");

    ClientObserver cb;
    { std::lock_guard<std::mutex> lk(obs_mu_); cb = observer_; }
    if (cb) {
        try { cb(static_cast<int>(port), std::move(peer), std::move(first)); }
        catch (...) { WB_LOGE("observer threw"); }
    }

    // Phase 8b.1 — no response yet. Close the socket so lsteamclient.dll
    // retries (or gives up gracefully if Wine treats a closed connection
    // as "Steam not running"). Phase 8b.2 will hold the connection open
    // and proxy frames to libsteamclient.so.
    ::shutdown(fd, SHUT_RDWR);
    ::close(fd);
}

}  // namespace wn_steam
