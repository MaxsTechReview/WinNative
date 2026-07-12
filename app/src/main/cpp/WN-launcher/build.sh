#!/usr/bin/env bash
# Cross-compile WN-launcher.exe: the wine /desktop=shell program. It launches
# the target and waits on it (so the session ends when the game exits), applies
# per-launch affinity, and with no args (or /idle) just sleeps forever so it can
# also serve as the background input service's keep-alive child. The Gradle build
# does NOT compile this — run this after editing, then rebuild the APK.
#
# Usage:   ./build.sh
# Output:  ../../assets/WN-launcher.exe
set -euo pipefail
cd "$(dirname "$0")"

CC="${CC:-x86_64-w64-mingw32-gcc}"
STRIP="${STRIP:-x86_64-w64-mingw32-strip}"
OUT_FILE="../../assets/WN-launcher.exe"

"$CC" -O2 -Wall -Wextra \
    -static -static-libgcc \
    -Wl,--subsystem,windows \
    -o "$OUT_FILE" \
    src/main.c \
    -lshell32

"$STRIP" "$OUT_FILE"

echo "Built: $OUT_FILE  ($(stat -c '%s' "$OUT_FILE") bytes)"
file "$OUT_FILE"
