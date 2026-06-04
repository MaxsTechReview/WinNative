#pragma once


#include <cstdint>
#include <vector>

namespace wn_libsteamclient {

constexpr size_t kCCallbackBaseFlagsOffset    = 8;
constexpr size_t kCCallbackBaseIdOffset       = 12;
constexpr uint8_t kCallbackFlagsRegistered    = 0x01;
constexpr uint8_t kCallbackFlagsGameServer    = 0x02;

void register_callback(void* cb, int iCallback);

void unregister_callback(void* cb);

[[nodiscard]] std::vector<void*> find_callbacks(int iCallback);

[[nodiscard]] size_t registry_size();


void register_call_result(void* cb, uint64_t hCall);
void unregister_call_result(void* cb, uint64_t hCall);
[[nodiscard]] std::vector<void*> find_call_result_cbs(uint64_t hCall);

[[nodiscard]] size_t call_result_registry_size();

}  // namespace wn_libsteamclient
