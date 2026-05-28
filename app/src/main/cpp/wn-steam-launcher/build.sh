#!/usr/bin/env bash
# Cross-compile steam.exe (the Steam Launcher in-Wine host) for Wine
# (x86_64 / 64-bit PE) and stage it into the APK assets.

set -euo pipefail

cd "$(dirname "$0")"

CXX="${CXX:-x86_64-w64-mingw32-g++}"
STRIP="${STRIP:-x86_64-w64-mingw32-strip}"
OUT_DIR_REL="../../assets/wnsteam/bionic"
OUT_FILE="$OUT_DIR_REL/steam.exe"

command -v "$CXX" >/dev/null 2>&1 || {
    echo "$CXX not found. Install mingw-w64 or set CXX to a Windows x86_64 cross compiler." >&2
    exit 1
}
command -v "$STRIP" >/dev/null 2>&1 || {
    echo "$STRIP not found. Install mingw-w64 or set STRIP to a matching strip tool." >&2
    exit 1
}

mkdir -p "$OUT_DIR_REL"

"$CXX" -std=c++17 -O2 -Wall -Wextra -Wno-unused-parameter \
    -static -static-libgcc -static-libstdc++ \
    -Wl,--subsystem,windows \
    -o "$OUT_FILE" \
    src/main.cpp \
    -ladvapi32 -lkernel32 -luser32

"$STRIP" "$OUT_FILE"

echo "Built: $OUT_FILE ($(stat -c '%s' "$OUT_FILE") bytes)"
file "$OUT_FILE"
