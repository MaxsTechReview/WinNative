#!/usr/bin/env bash
# Cross-compile steam.exe (the Steam Launcher in-Wine host) for Wine (x86_64 /
# 64-bit PE) and stage it into the APK assets. The Android Gradle build does NOT
# compile this — it only packages the prebuilt binary — so run this after editing
# any wn-steam-launcher source, then rebuild the APK.
#
# Built 64-bit on purpose: it hosts Valve's real steamclient64.dll so
# IClientAppManager::LaunchApp drives the game through steamclient's own
# app-launch path. Named "steam.exe" because steamclient's CGameLauncher path
# requires its host process to look like real Steam.
#
# Usage:   ./build.sh
# Output:  ../../assets/wnsteam/bionic/steam.exe
#
# Requires the POSIX-threads mingw-w64 cross compiler (clean_shutdown.cpp uses
# std::thread, which the default win32-threads variant does not provide).
set -euo pipefail
cd "$(dirname "$0")"

CXX="${CXX:-x86_64-w64-mingw32-g++-posix}"
STRIP="${STRIP:-x86_64-w64-mingw32-strip}"
OUT_FILE="../../assets/wnsteam/bionic/steam.exe"

# -Wl,--subsystem,windows: no console, so Wine doesn't map a transient console
# X11 window at startup (which raced the X server and cut the preloader short).
# Static link the runtime so no MinGW DLLs are dragged into the wine prefix.
"$CXX" -std=c++17 -O2 -Wall -Wextra -Wno-unused-parameter \
    -static -static-libgcc -static-libstdc++ \
    -Wl,--subsystem,windows \
    -I. \
    -o "$OUT_FILE" \
    src/main.cpp clean_shutdown.cpp \
    -ladvapi32 -lkernel32 -luser32

"$STRIP" "$OUT_FILE"

echo "Built: $OUT_FILE  ($(stat -c '%s' "$OUT_FILE") bytes)"
file "$OUT_FILE"
