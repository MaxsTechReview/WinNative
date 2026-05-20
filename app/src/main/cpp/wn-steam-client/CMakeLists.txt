# ----------------------------------------------------------------------------
# wn-steam-client
# Native Steam protocol client for WinNative. Replaces the JavaSteam JVM
# dependency with a C++ implementation built directly into the app's .so set.
# ----------------------------------------------------------------------------

cmake_minimum_required(VERSION 3.22.1)

include(FetchContent)

# Make our FindOpenSSL.cmake shim discoverable so that FetchContent-included
# third-party libraries (IXWebSocket) that call `find_package(OpenSSL REQUIRED)`
# find the Prefab AAR via our wrapper rather than failing.
list(PREPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")

set(WN_STEAM_CLIENT_VERSION_MAJOR 0)
set(WN_STEAM_CLIENT_VERSION_MINOR 1)
set(WN_STEAM_CLIENT_VERSION_PATCH 0)
set(WN_STEAM_CLIENT_VERSION_STRING
    "${WN_STEAM_CLIENT_VERSION_MAJOR}.${WN_STEAM_CLIENT_VERSION_MINOR}.${WN_STEAM_CLIENT_VERSION_PATCH}")

# ----------------------------------------------------------------------------
# Prefab system deps (declared in app/build.gradle):
#   com.android.ndk.thirdparty:openssl:1.1.1q-beta-1
#   com.android.ndk.thirdparty:curl:7.85.0-beta-1
# ----------------------------------------------------------------------------
find_package(openssl 1.1.1 REQUIRED CONFIG)
find_package(curl    REQUIRED CONFIG)

# ----------------------------------------------------------------------------
# nlohmann/json (header-only). Fetched at configure time. Small enough that
# vendoring it as a single header would also work, but FetchContent keeps the
# version pin in source and lets CMake handle the include path.
# ----------------------------------------------------------------------------
FetchContent_Declare(
    nlohmann_json
    GIT_REPOSITORY https://github.com/nlohmann/json.git
    GIT_TAG        v3.11.3
    GIT_SHALLOW    TRUE
)
set(JSON_BuildTests        OFF CACHE BOOL "" FORCE)
set(JSON_Install           OFF CACHE BOOL "" FORCE)
set(JSON_SystemInclude     OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(nlohmann_json)

# ----------------------------------------------------------------------------
# IXWebSocket. Compiled with TLS via our Prefab OpenSSL (bridged through the
# FindOpenSSL.cmake shim above) and permessage-deflate via zlib.
# ----------------------------------------------------------------------------
FetchContent_Declare(
    ixwebsocket
    GIT_REPOSITORY https://github.com/machinezone/IXWebSocket.git
    GIT_TAG        v11.4.5
    GIT_SHALLOW    TRUE
)
set(USE_TLS              ON  CACHE BOOL "" FORCE)
set(USE_OPEN_SSL         ON  CACHE BOOL "" FORCE)
set(USE_MBED_TLS         OFF CACHE BOOL "" FORCE)
set(USE_ZLIB             ON  CACHE BOOL "" FORCE)
set(USE_WS               OFF CACHE BOOL "" FORCE)   # standalone CLI not needed
set(BUILD_DEMO_SAMPLES   OFF CACHE BOOL "" FORCE)
set(BUILD_TEST_SUITE     OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(ixwebsocket)

# ----------------------------------------------------------------------------
# Zstandard — Phase 5.4 depot-chunk decompression. Modern Steam CDN chunks
# are VZSTD (a raw zstd stream wrapped in a tiny Steam header/footer).
# ----------------------------------------------------------------------------
FetchContent_Declare(
    zstd
    GIT_REPOSITORY https://github.com/facebook/zstd.git
    GIT_TAG        v1.5.6
    GIT_SHALLOW    TRUE
    SOURCE_SUBDIR  build/cmake
)
set(ZSTD_BUILD_STATIC    ON  CACHE BOOL "" FORCE)
set(ZSTD_BUILD_SHARED    OFF CACHE BOOL "" FORCE)
set(ZSTD_BUILD_PROGRAMS  OFF CACHE BOOL "" FORCE)
set(ZSTD_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(ZSTD_LEGACY_SUPPORT  OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(zstd)

# ----------------------------------------------------------------------------
# liblzma (xz) — Phase 5.4b depot-chunk decompression. Older Steam CDN chunks
# are VZip, which is a raw LZMA1 stream wrapped in a Steam header/footer.
# v5.6.3 is post-CVE-2024-3094 (the backdoor was reverted in 5.6.2+).
# ----------------------------------------------------------------------------
FetchContent_Declare(
    xz
    GIT_REPOSITORY https://github.com/tukaani-project/xz.git
    GIT_TAG        v5.6.3
    GIT_SHALLOW    TRUE
)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)   # static liblzma
set(ENABLE_NLS           OFF CACHE BOOL "" FORCE)   # no gettext dependency
FetchContent_MakeAvailable(xz)

# ----------------------------------------------------------------------------
# wnsteam shared library
# ----------------------------------------------------------------------------

add_library(wnsteam SHARED
    src/version.cpp
    src/crypto.cpp
    src/key_dictionary.cpp
    src/handshake_messages.cpp
    src/cm_server.cpp
    src/cm_server_list.cpp
    src/steam_directory.cpp
    src/ws_connection.cpp
    src/encrypted_channel.cpp
    src/heartbeat.cpp
    src/proto_wire.cpp
    src/vdf.cpp
    src/content_manifest.cpp
    src/cdn_client.cpp
    src/depot_chunk.cpp
    src/depot_writer.cpp
    src/depot_config.cpp
    src/depot_downloader.cpp
    src/wine_bridge.cpp
    src/wn_library_store.cpp
    src/wn_ticket_cache.cpp
    src/cmsg_protobuf_header.cpp
    src/proto_envelope.cpp
    src/job_manager.cpp
    src/base64.cpp
    src/rsa_password.cpp
    src/cm_client.cpp
    src/auth_session.cpp
    src/pb/cmsg_clientserver_login.cpp
    src/pb/cmsg_client_license_list.cpp
    src/pb/cmsg_client_pics.cpp
    src/pb/cmsg_client_get_app_ownership_ticket.cpp
    src/pb/cmsg_client_request_encrypted_app_ticket.cpp
    src/pb/cmsg_client_get_user_stats.cpp
    src/pb/cmsg_client_store_user_stats.cpp
    src/pb/cmsg_client_games_played.cpp
    src/pb/cmsg_client_kick_playing_session.cpp
    src/pb/cmsg_client_change_status.cpp
    src/pb/cmsg_client_persona.cpp
    src/pb/cmsg_client_playing_session_state.cpp
    src/pb/cmsg_client_get_depot_decryption_key.cpp
    src/pb/ccloud.cpp
    src/pb/cinventory.cpp
    src/pb/cpublishedfile.cpp
    src/pb/cfamilygroups.cpp
    src/pb/cplayer.cpp
    src/pb/ccontentserverdirectory.cpp
    src/pb/cauthentication.cpp
    jni/wn_steam_jni.cpp
    jni/wn_session_jni.cpp
)

target_include_directories(wnsteam
    PRIVATE include src
    # liblzma's public API dir is PRIVATE on its own target, so add it here.
    PRIVATE ${xz_SOURCE_DIR}/src/liblzma/api
)

# C++20 required: we use std::span throughout the crypto + protocol APIs,
# and NDK 27's libc++ only exposes <span> under -std=c++20.
target_compile_features(wnsteam PRIVATE cxx_std_20)

# Repo convention is -Wall -Wextra. Avoid -Wpedantic — it fights system
# headers (OpenSSL, IXWebSocket, libcurl) we pull in.
target_compile_options(wnsteam PRIVATE
    -Wall -Wextra
    -fvisibility=hidden
    -fvisibility-inlines-hidden
)

target_compile_definitions(wnsteam PRIVATE
    WN_STEAM_CLIENT_VERSION_MAJOR=${WN_STEAM_CLIENT_VERSION_MAJOR}
    WN_STEAM_CLIENT_VERSION_MINOR=${WN_STEAM_CLIENT_VERSION_MINOR}
    WN_STEAM_CLIENT_VERSION_PATCH=${WN_STEAM_CLIENT_VERSION_PATCH}
    WN_STEAM_CLIENT_VERSION_STRING="${WN_STEAM_CLIENT_VERSION_STRING}"
)

target_link_libraries(wnsteam
    PRIVATE
    log
    android
    z                              # zlib for CRC32 (NDK system lib)
    libzstd_static                 # Phase 5.4 depot-chunk (VZSTD) decompression
    liblzma                        # Phase 5.4b depot-chunk (VZip/LZMA1) decompression
    openssl::crypto
    openssl::ssl
    curl::curl
    ixwebsocket
    nlohmann_json::nlohmann_json
)
