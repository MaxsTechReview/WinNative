#include "wn_steam/cm_server.h"

#include <charconv>

namespace wn_steam {

std::string CmServer::websocket_url() const {
    if (transport != CmTransport::WebSocket || host.empty() || port == 0) {
        return {};
    }
    std::string url;
    url.reserve(host.size() + 32);
    url.append("wss://");
    url.append(host);
    url.append(":");
    url.append(std::to_string(port));
    url.append("/cmsocket/");
    return url;
}

bool parse_endpoint(std::string_view endpoint,
                    std::string& host,
                    uint16_t& port) noexcept {
    // Find the LAST ':' (IPv6 literals contain colons too, though Steam
    // doesn't currently advertise any). We accept "[v6]:port" too — strip
    // brackets if present.
    auto colon = endpoint.rfind(':');
    if (colon == std::string_view::npos || colon == 0 || colon + 1 == endpoint.size()) {
        return false;
    }

    std::string_view host_sv = endpoint.substr(0, colon);
    std::string_view port_sv = endpoint.substr(colon + 1);

    if (host_sv.size() >= 2 && host_sv.front() == '[' && host_sv.back() == ']') {
        host_sv.remove_prefix(1);
        host_sv.remove_suffix(1);
    }

    uint32_t parsed_port = 0;
    auto [ptr, ec] = std::from_chars(port_sv.data(),
                                     port_sv.data() + port_sv.size(),
                                     parsed_port);
    if (ec != std::errc{} || ptr != port_sv.data() + port_sv.size()) return false;
    if (parsed_port == 0 || parsed_port > 0xFFFFu) return false;

    host.assign(host_sv);
    port = static_cast<uint16_t>(parsed_port);
    return true;
}

}  // namespace wn_steam
