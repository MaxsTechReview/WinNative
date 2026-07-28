#include "netpacket.h"

#include <cerrno>
#include <cstring>
#include <unistd.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>

#include "log.h"

namespace libretrodroid {

namespace {
constexpr uint32_t kMagic = 0x4E504B54; // 'NPKT'
constexpr size_t kMaxPayload = 64 * 1024;

void setNonBlocking(int fd) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags >= 0) fcntl(fd, F_SETFL, flags | O_NONBLOCK);
}

void setTcpNoDelay(int fd) {
    int one = 1;
    setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &one, sizeof(one));
}
} // namespace

NetpacketBridge& NetpacketBridge::getInstance() {
    static NetpacketBridge instance;
    return instance;
}

void NetpacketBridge::setCoreInterface(const wn_netpacket_callback* cb) {
    std::lock_guard<std::mutex> lock(stateMutex);
    if (cb == nullptr) {
        hasIface = false;
        coreIface = {};
        return;
    }
    coreIface = *cb;
    hasIface = (coreIface.start != nullptr && coreIface.receive != nullptr);
    LOGI("Netpacket core interface registered (has=%d)", hasIface ? 1 : 0);
}

bool NetpacketBridge::hasCoreInterface() const {
    return hasIface;
}

void NetpacketBridge::setPeerEventCallback(PeerEventFn fn) {
    peerEventCb = fn;
}

void NetpacketBridge::clearCoreInterface() {
    std::lock_guard<std::mutex> lock(stateMutex);
    hasIface = false;
    coreIface = {};
}

void RETRO_CALLCONV NetpacketBridge::frontend_send(int flags, const void* buf, size_t len, uint16_t client_id) {
    getInstance().sendImpl(flags, buf, len, client_id);
}

void RETRO_CALLCONV NetpacketBridge::frontend_poll_receive(void) {
    getInstance().pollReceiveImpl();
}

bool NetpacketBridge::startHost(int listenPort) {
    stop();
    wn_netpacket_start_t startFn = nullptr;
    {
        std::lock_guard<std::mutex> lock(stateMutex);
        if (!hasIface || coreIface.start == nullptr) {
            LOGE("Netpacket: no core interface");
            return false;
        }
        startFn = coreIface.start;
    }

    serverFd = ::socket(AF_INET, SOCK_STREAM, 0);
    if (serverFd < 0) return false;
    int one = 1;
    setsockopt(serverFd, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));
    sockaddr_in addr {};
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons(static_cast<uint16_t>(listenPort));
    if (bind(serverFd, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) < 0) {
        close(serverFd);
        serverFd = -1;
        return false;
    }
    if (listen(serverFd, 1) < 0) {
        close(serverFd);
        serverFd = -1;
        return false;
    }
    setNonBlocking(serverFd);

    hostSide = true;
    localId = 0;
    running = true;
    active = true;
    peerCountAtomic = 0;

    startFn(0, &frontend_send, &frontend_poll_receive);
    LOGI("Netpacket host listening on %d", listenPort);

    acceptThread = std::thread([this]() { acceptLoop(); });
    return true;
}

bool NetpacketBridge::startClient(const std::string& host, int port) {
    stop();
    wn_netpacket_start_t startFn = nullptr;
    {
        std::lock_guard<std::mutex> lock(stateMutex);
        if (!hasIface || coreIface.start == nullptr) {
            LOGE("Netpacket: no core interface");
            return false;
        }
        startFn = coreIface.start;
    }

    int fd = ::socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) return false;
    sockaddr_in addr {};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(static_cast<uint16_t>(port));
    if (inet_pton(AF_INET, host.c_str(), &addr.sin_addr) != 1) {
        close(fd);
        return false;
    }
    if (connect(fd, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) < 0) {
        close(fd);
        return false;
    }
    setTcpNoDelay(fd);
    setNonBlocking(fd);

    {
        std::lock_guard<std::mutex> lock(ioMutex);
        peerFd = fd;
    }

    hostSide = false;
    localId = 1;
    running = true;
    active = true;
    peerCountAtomic = 1;

    startFn(1, &frontend_send, &frontend_poll_receive);
    LOGI("Netpacket client connected to %s:%d", host.c_str(), port);

    readThread = std::thread([this]() { readLoop(); });
    return true;
}

void NetpacketBridge::stop() {
    const bool wasActive = active.exchange(false);
    running = false;
    closeSockets();

    if (acceptThread.joinable()) {
        if (acceptThread.get_id() != std::this_thread::get_id()) {
            acceptThread.join();
        } else {
            acceptThread.detach();
        }
    }
    if (readThread.joinable()) {
        if (readThread.get_id() != std::this_thread::get_id()) {
            readThread.join();
        } else {
            readThread.detach();
        }
    }

    wn_netpacket_stop_t stopFn = nullptr;
    {
        std::lock_guard<std::mutex> lock(stateMutex);
        if (wasActive && hasIface && coreIface.stop != nullptr) {
            stopFn = coreIface.stop;
        }
    }
    if (stopFn != nullptr) {
        stopFn();
    }

    peerCountAtomic = 0;
    {
        std::lock_guard<std::mutex> lock(queueMutex);
        inbound.clear();
    }
}

void NetpacketBridge::closeSockets() {
    std::lock_guard<std::mutex> lock(ioMutex);
    if (peerFd >= 0) {
        ::shutdown(peerFd, SHUT_RDWR);
        close(peerFd);
        peerFd = -1;
    }
    if (serverFd >= 0) {
        close(serverFd);
        serverFd = -1;
    }
}

void NetpacketBridge::acceptLoop() {
    while (running.load()) {
        int listenFd;
        {
            std::lock_guard<std::mutex> lock(ioMutex);
            listenFd = serverFd;
        }
        if (listenFd < 0) break;

        sockaddr_in cli {};
        socklen_t len = sizeof(cli);
        int fd = accept(listenFd, reinterpret_cast<sockaddr*>(&cli), &len);
        if (fd < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                usleep(50 * 1000);
                continue;
            }
            break;
        }
        if (!running.load()) {
            close(fd);
            break;
        }
        setTcpNoDelay(fd);
        setNonBlocking(fd);
        {
            std::lock_guard<std::mutex> lock(ioMutex);
            if (peerFd >= 0) {
                close(fd);
                continue;
            }
            peerFd = fd;
        }
        peerCountAtomic = 1;
        uint16_t peerId = 1;
        wn_netpacket_connected_t connectedFn = nullptr;
        {
            std::lock_guard<std::mutex> lock(stateMutex);
            if (hasIface) connectedFn = coreIface.connected;
        }
        if (connectedFn != nullptr) {
            if (!connectedFn(peerId)) {
                LOGE("Netpacket: core rejected peer");
                std::lock_guard<std::mutex> lock(ioMutex);
                if (peerFd >= 0) {
                    close(peerFd);
                    peerFd = -1;
                }
                peerCountAtomic = 0;
                continue;
            }
        }
        LOGI("Netpacket peer accepted id=%u", peerId);
        if (peerEventCb) {
            peerEventCb(true, peerId);
        }
        if (readThread.joinable()) {
            if (readThread.get_id() != std::this_thread::get_id()) {
                readThread.join();
            } else {
                readThread.detach();
            }
        }
        if (!running.load()) break;
        readThread = std::thread([this]() { readLoop(); });
    }
}

void NetpacketBridge::readLoop() {
    while (running.load()) {
        std::vector<uint8_t> payload;
        uint16_t fromId = 0;
        if (!readFrame(payload, fromId)) {
            int fd;
            {
                std::lock_guard<std::mutex> lock(ioMutex);
                fd = peerFd;
            }
            if (fd < 0 || !running.load()) break;
            usleep(2 * 1000);
            char probe = 0;
            ssize_t r = ::recv(fd, &probe, 1, MSG_PEEK | MSG_DONTWAIT);
            if (r == 0) {
                {
                    std::lock_guard<std::mutex> lock(ioMutex);
                    if (peerFd == fd) {
                        close(peerFd);
                        peerFd = -1;
                    }
                }
                peerCountAtomic = 0;
                if (hostSide && peerEventCb) {
                    peerEventCb(false, 1);
                }
                wn_netpacket_disconnected_t discFn = nullptr;
                {
                    std::lock_guard<std::mutex> lock(stateMutex);
                    if (hostSide && hasIface) discFn = coreIface.disconnected;
                }
                if (discFn != nullptr) {
                    discFn(1);
                }
                break;
            }
            continue;
        }
        {
            std::lock_guard<std::mutex> lock(queueMutex);
            inbound.push_back(Packet { fromId, std::move(payload) });
        }
    }
}

bool NetpacketBridge::writeFrame(const uint8_t* data, uint32_t len, uint16_t fromId) {
    std::lock_guard<std::mutex> lock(ioMutex);
    if (peerFd < 0) return false;
    uint32_t magic = htonl(kMagic);
    uint32_t nlen = htonl(len);
    uint16_t nfrom = htons(fromId);
    auto sendAll = [&](const void* p, size_t n) -> bool {
        const uint8_t* b = static_cast<const uint8_t*>(p);
        size_t off = 0;
        while (off < n) {
            ssize_t w = ::send(peerFd, b + off, n - off, MSG_NOSIGNAL);
            if (w < 0) {
                if (errno == EAGAIN || errno == EWOULDBLOCK) {
                    usleep(1000);
                    continue;
                }
                return false;
            }
            off += static_cast<size_t>(w);
        }
        return true;
    };
    if (!sendAll(&magic, 4)) return false;
    if (!sendAll(&nlen, 4)) return false;
    if (!sendAll(&nfrom, 2)) return false;
    if (len > 0 && data != nullptr) {
        if (!sendAll(data, len)) return false;
    }
    return true;
}

bool NetpacketBridge::readFrame(std::vector<uint8_t>& out, uint16_t& fromId) {
    int fd;
    {
        std::lock_guard<std::mutex> lock(ioMutex);
        fd = peerFd;
    }
    if (fd < 0) return false;

    auto recvAll = [&](void* p, size_t n) -> bool {
        uint8_t* b = static_cast<uint8_t*>(p);
        size_t off = 0;
        while (off < n) {
            ssize_t r = ::recv(fd, b + off, n - off, 0);
            if (r == 0) return false;
            if (r < 0) {
                if (errno == EAGAIN || errno == EWOULDBLOCK) {
                    if (off == 0) return false;
                    usleep(1000);
                    continue;
                }
                return false;
            }
            off += static_cast<size_t>(r);
        }
        return true;
    };

    uint32_t magic = 0, nlen = 0;
    uint16_t nfrom = 0;
    if (!recvAll(&magic, 4)) return false;
    magic = ntohl(magic);
    if (magic != kMagic) return false;
    if (!recvAll(&nlen, 4)) return false;
    nlen = ntohl(nlen);
    if (nlen > kMaxPayload) return false;
    if (!recvAll(&nfrom, 2)) return false;
    fromId = ntohs(nfrom);
    out.resize(nlen);
    if (nlen > 0 && !recvAll(out.data(), nlen)) return false;
    return true;
}

void NetpacketBridge::sendImpl(int /*flags*/, const void* buf, size_t len, uint16_t client_id) {
    if (!active.load()) return;
    if (buf == nullptr || len == 0) {
        pollReceiveImpl();
        return;
    }
    if (len > kMaxPayload) len = kMaxPayload;
    (void) client_id;
    writeFrame(static_cast<const uint8_t*>(buf), static_cast<uint32_t>(len), localId);
}

void NetpacketBridge::pollReceiveImpl() {
    std::vector<Packet> batch;
    {
        std::lock_guard<std::mutex> lock(queueMutex);
        batch.swap(inbound);
    }
    wn_netpacket_receive_t receiveFn = nullptr;
    {
        std::lock_guard<std::mutex> lock(stateMutex);
        if (hasIface) receiveFn = coreIface.receive;
    }
    if (receiveFn == nullptr) return;
    for (auto& p : batch) {
        receiveFn(p.data.data(), p.data.size(), p.fromId);
    }
}

void NetpacketBridge::poll() {
    if (!active.load()) return;
    pollReceiveImpl();
    wn_netpacket_poll_t pollFn = nullptr;
    {
        std::lock_guard<std::mutex> lock(stateMutex);
        if (hasIface) pollFn = coreIface.poll;
    }
    if (pollFn != nullptr) {
        pollFn();
    }
}

} // namespace libretrodroid
