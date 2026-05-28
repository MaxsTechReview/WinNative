# wn-libsteamclient — an open-source libsteamclient.so we build ourselves.
#
# Background. The Bionic Steam path needs a `libsteamclient.so` to host
# Valve's Steam client surface in the Android process so Wine's
# `lsteamclient.dll` (running inside the Proton prefix) has a peer to
# talk to over the Steam3Master / SteamClientService TCP services. We
# were bundling a prebuilt copy; this module replaces it with our own
# from-scratch implementation that exposes the same public C ABI but
# is backed by the open-source Rust `wnsteam` CM client we already
# ship.
#
# Initial scope (this commit): the FLAT C entry points (CreateInterface
# + Steam_BLoggedOn + Steam_BGetCallback + the rest of the
# Steam_*/* pipe + user lifecycle exports) plus a stub ISteamClient
# whose GetISteam* methods return empty objects. That's enough for the
# bootstrap's nativeInit to dlopen + dlsym + walk the pipe API without
# crashing. Real backend wiring lands incrementally.
#
# SONAME is `libsteamclient.so` exactly so the staged file at
# <filesDir>/imagefs/usr/lib/libsteamclient.so behaves as a drop-in.

cmake_minimum_required(VERSION 3.22.1)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# IMPORTANT: the output `.so` name must be `libsteamclient.so` (not
# `libwnlibsteamclient.so`) because the Wine `lsteamclient.dll` bridge
# dlopens by that exact name. Use OUTPUT_NAME to override the CMake
# default (`lib<TARGET>.so`).
add_library(wn_libsteamclient SHARED
    src/api_entry.cpp
    src/callback_registry.cpp
    src/iclient_engine.cpp
    src/isteam_client.cpp
    src/isteam_stubs.cpp
    src/jni_pushed_state.cpp
    src/runtime_state.cpp
    src/tcp_services.cpp
)
set_target_properties(wn_libsteamclient PROPERTIES
    OUTPUT_NAME steamclient
    PREFIX "lib"
    SUFFIX ".so"
)

target_include_directories(wn_libsteamclient PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_compile_options(wn_libsteamclient PRIVATE
    -fno-rtti
    -fvisibility=hidden
    -Wall -Wno-unused-parameter
)

# Mirror the public-ABI NEEDED list of Valve's binary so any consumer
# that probes our .so against the original sees the same dynamic deps.
# All of these are part of Android's bionic / NDK; nothing extra.
target_link_libraries(wn_libsteamclient PRIVATE
    android log dl m
    # libwnsteam.so — resolves the wn_cm_* C-ABI bridge symbols at link
    # time. Both .so's load in the app process; the dynamic linker pairs
    # the unresolved bridge references in libsteamclient.so to their
    # default-visibility implementations in libwnsteam.so. Without this
    # link, --no-undefined fails the link.
    wnsteam
)

# Surface unresolved symbols at link time so a missing export shows up
# in the build, not at dlopen on device.
target_link_options(wn_libsteamclient PRIVATE
    -Wl,--no-undefined
)
