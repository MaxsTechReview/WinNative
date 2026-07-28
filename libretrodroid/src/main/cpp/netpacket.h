#ifndef LIBRETRODROID_NETPACKET_H
#define LIBRETRODROID_NETPACKET_H

#include <cstdint>
#include <mutex>
#include <vector>
#include <atomic>
#include <thread>
#include <string>

#include "retro_netpacket_api.h"

namespace libretrodroid {

class NetpacketBridge {
public:
    static NetpacketBridge& getInstance();

    NetpacketBridge(const NetpacketBridge&) = delete;
    void operator=(const NetpacketBridge&) = delete;

    void setCoreInterface(const wn_netpacket_callback* cb);

    void clearCoreInterface();

    bool hasCoreInterface() const;

    bool startHost(int listenPort);
    bool startClient(const std::string& host, int port);
    void stop();

    bool isActive() const { return active.load(); }
    bool isHostSide() const { return hostSide; }
    uint16_t localClientId() const { return localId; }
    int peerCount() const { return peerCountAtomic.load(); }

    void poll();

    using PeerEventFn = void (*)(bool joined, uint16_t clientId);
    void setPeerEventCallback(PeerEventFn fn);

private:
    NetpacketBridge() = default;
    PeerEventFn peerEventCb = nullptr;

    static void RETRO_CALLCONV frontend_send(int flags, const void* buf, size_t len, uint16_t client_id);
    static void RETRO_CALLCONV frontend_poll_receive(void);

    void sendImpl(int flags, const void* buf, size_t len, uint16_t client_id);
    void pollReceiveImpl();
    void closeSockets();
    void acceptLoop();
    void readLoop();
    bool writeFrame(const uint8_t* data, uint32_t len, uint16_t fromId);
    bool readFrame(std::vector<uint8_t>& out, uint16_t& fromId);

    wn_netpacket_callback coreIface {};
    bool hasIface = false;
    std::mutex stateMutex;

    std::atomic<bool> active { false };
    std::atomic<bool> running { false };
    bool hostSide = false;
    uint16_t localId = 0;
    std::atomic<int> peerCountAtomic { 0 };

    int serverFd = -1;
    int peerFd = -1;
    std::mutex ioMutex;
    std::thread acceptThread;
    std::thread readThread;

    struct Packet {
        uint16_t fromId = 0;
        std::vector<uint8_t> data;
    };
    std::mutex queueMutex;
    std::vector<Packet> inbound;
};

} // namespace libretrodroid

#endif
