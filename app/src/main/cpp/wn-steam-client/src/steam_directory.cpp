#include "wn_steam/steam_directory.h"

#include <android/log.h>
#include <curl/curl.h>
#include <nlohmann/json.hpp>

#include <cstring>

namespace wn_steam {

namespace {

constexpr const char* kDirectoryEndpoint =
    "https://api.steampowered.com/ISteamDirectory/GetCMListForConnect/v1/";

constexpr const char* kLogTag = "WnSteamDir";

#define WN_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)
#define WN_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  kLogTag, __VA_ARGS__)

size_t curl_write_cb(char* ptr, size_t size, size_t nmemb, void* userdata) {
    auto* out = static_cast<std::string*>(userdata);
    const size_t n = size * nmemb;
    out->append(ptr, n);
    return n;
}

CmTransport parse_transport(std::string_view s) noexcept {
    // Steam Directory uses "websockets" (plural). Accept singular too.
    if (s == "websockets" || s == "websocket") return CmTransport::WebSocket;
    if (s == "netfilter")                      return CmTransport::Tcp;
    return CmTransport::Unknown;
}

}  // namespace

SteamDirectoryClient::SteamDirectoryClient() {
    // libcurl global init is documented thread-unsafe but idempotent; the
    // Android Gradle plugin's curl Prefab AAR runs it implicitly via the
    // first easy-init in most loaders. Calling it again is harmless after
    // the first time and necessary if no other consumer pulled it in yet.
    curl_global_init(CURL_GLOBAL_DEFAULT);
}

SteamDirectoryClient::~SteamDirectoryClient() {
    // Note: we intentionally do NOT call curl_global_cleanup here. Other
    // wn-steam-client subsystems also use libcurl and there's no reference
    // counting on the global init. The OS reclaims everything at process
    // teardown.
}

SteamDirectoryResult SteamDirectoryClient::fetch(uint32_t cell_id,
                                                 std::chrono::seconds timeout,
                                                 std::string_view user_agent,
                                                 std::string_view ca_bundle_path) {
    SteamDirectoryResult result;
    result.http_status = 0;

    char url[512];
    // cmtype=websockets so the response excludes TCP CMs we won't use.
    // maxcount=20 mirrors what the official client requests.
    std::snprintf(url, sizeof(url),
                  "%s?cellid=%u&cmtype=websockets&maxcount=20&realm=steamglobal",
                  kDirectoryEndpoint, cell_id);

    CURL* curl = curl_easy_init();
    if (!curl) {
        result.error = "curl_easy_init failed";
        WN_LOGE("%s", result.error.c_str());
        return result;
    }

    std::string body;
    body.reserve(8192);

    std::string ua(user_agent);

    curl_easy_setopt(curl, CURLOPT_URL,            url);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION,  curl_write_cb);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA,      &body);
    curl_easy_setopt(curl, CURLOPT_USERAGENT,      ua.c_str());
    curl_easy_setopt(curl, CURLOPT_TIMEOUT,        static_cast<long>(timeout.count()));
    curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, static_cast<long>(timeout.count()));
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
    curl_easy_setopt(curl, CURLOPT_NOPROGRESS,     1L);

    // TLS — Prefab curl is built against OpenSSL. The default verify
    // settings are on; Android system CAs aren't shipped in the curl AAR
    // sysroot. CURLOPT_CAPATH=/system/etc/security/cacerts (hashed-dir
    // mode) was empirically broken on Android 16 / OnePlus testing, so
    // we instead use CURLOPT_CAINFO pointing at the single-file PEM
    // bundle CaBundleExtractor produced from those same hashed dirs.
    std::string ca_path(ca_bundle_path);
    if (!ca_path.empty()) {
        curl_easy_setopt(curl, CURLOPT_CAINFO, ca_path.c_str());
    }
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 1L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 2L);

    const CURLcode rc = curl_easy_perform(curl);

    long http_status = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_status);
    result.http_status = static_cast<int>(http_status);

    curl_easy_cleanup(curl);

    if (rc != CURLE_OK) {
        result.error = "curl_easy_perform: ";
        result.error += curl_easy_strerror(rc);
        WN_LOGE("%s (http_status=%ld)", result.error.c_str(), http_status);
        return result;
    }

    if (http_status != 200) {
        result.error = "non-200 HTTP status";
        WN_LOGE("Steam Directory returned HTTP %ld", http_status);
        return result;
    }

    // Parse JSON body. Expected shape:
    //   { "response": { "success": bool, "serverlist": [ { ... }, ... ] } }
    try {
        auto root = nlohmann::json::parse(body);
        const auto& response = root.at("response");

        // `success` in the directory response is an int (1 = OK) per the
        // current schema; accept either int or bool for robustness.
        bool ok = false;
        if (response.contains("success")) {
            const auto& s = response["success"];
            if (s.is_boolean())       ok = s.get<bool>();
            else if (s.is_number())   ok = (s.get<int>() == 1);
        }
        if (!ok) {
            result.error = "directory response: success=false";
            WN_LOGE("%s", result.error.c_str());
            return result;
        }

        if (!response.contains("serverlist") || !response["serverlist"].is_array()) {
            result.error = "directory response: missing serverlist";
            WN_LOGE("%s", result.error.c_str());
            return result;
        }

        const auto& list = response["serverlist"];
        result.servers.reserve(list.size());

        for (const auto& entry : list) {
            CmServer s;

            if (entry.contains("endpoint") && entry["endpoint"].is_string()) {
                s.endpoint = entry["endpoint"].get<std::string>();
            } else {
                continue;  // unparseable, skip
            }
            if (!parse_endpoint(s.endpoint, s.host, s.port)) {
                continue;
            }

            if (entry.contains("type") && entry["type"].is_string()) {
                s.transport = parse_transport(entry["type"].get<std::string>());
            }
            // Phase 1 only handles WebSocket transport.
            if (s.transport != CmTransport::WebSocket) continue;

            if (entry.contains("realm") && entry["realm"].is_string())
                s.realm = entry["realm"].get<std::string>();
            if (entry.contains("dc") && entry["dc"].is_string())
                s.datacenter = entry["dc"].get<std::string>();
            if (entry.contains("load") && entry["load"].is_number_integer())
                s.load = entry["load"].get<int32_t>();
            if (entry.contains("wtd_load") && entry["wtd_load"].is_number())
                s.weighted_load = entry["wtd_load"].get<float>();

            result.servers.push_back(std::move(s));
        }

        WN_LOGI("Steam Directory returned %zu WebSocket servers (cellid=%u)",
                result.servers.size(), cell_id);
    } catch (const nlohmann::json::exception& e) {
        result.error = "json parse error: ";
        result.error += e.what();
        WN_LOGE("%s", result.error.c_str());
        result.servers.clear();
    }

    return result;
}

}  // namespace wn_steam
