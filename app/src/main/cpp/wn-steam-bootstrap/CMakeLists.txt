# ----------------------------------------------------------------------------
# wn-steam-bootstrap
# Small JNI shim that loads Valve's native libsteamclient.so in-process so
# Wine's lsteamclient.dll (running inside our Proton prefix) has a peer to
# talk to over the Steam3Master / SteamClientService TCP sockets.
#
# This module is intentionally tiny:
#   • C++20, no third-party deps
#   • links only `log` (Android logging) and `dl` (dynamic loader)
#   • the heavy lifting is whatever libsteamclient.so does when we call
#     setenv() + dlopen() + a small handful of SteamWorks entry points
#
# Hidden visibility + section gc + strip in release keep the .so small
# (a couple of KB) — this is *only* the bridge; the actual Steam logic
# lives in libsteamclient.so (downloaded/staged at runtime, NOT bundled
# in jniLibs since it's a Valve binary and we don't redistribute it).
# ----------------------------------------------------------------------------

cmake_minimum_required(VERSION 3.22.1)

project(WnSteamBootstrap CXX)

add_library(wnsteambootstrap SHARED
    src/steam_bootstrap.cpp
)

target_include_directories(wnsteambootstrap PRIVATE include)

target_compile_features(wnsteambootstrap PRIVATE cxx_std_20)

target_compile_options(wnsteambootstrap PRIVATE
    -Wall -Wextra
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -fno-asynchronous-unwind-tables
    -fno-unwind-tables
    -ffunction-sections
    -fdata-sections)

target_link_options(wnsteambootstrap PRIVATE
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
    "$<$<NOT:$<CONFIG:Debug>>:-Wl,--strip-all>")

target_link_libraries(wnsteambootstrap PRIVATE log dl)
