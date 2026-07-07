#!/usr/bin/env bash
# Cross-compile wn-idle.exe: a windowless PE that sleeps forever. Used as the
# long-lived child of the background input service so its default program never
# launches and the service stays alive for the whole session. The Gradle build
# does NOT compile this — run this after editing, then rebuild the APK.
#
# Usage:   ./build.sh
# Output:  ../../assets/wn-idle.exe
set -euo pipefail
cd "$(dirname "$0")"

CC="${CC:-x86_64-w64-mingw32-gcc}"
STRIP="${STRIP:-x86_64-w64-mingw32-strip}"
OUT_FILE="../../assets/wn-idle.exe"

"$CC" -O2 -Wall -Wextra \
    -static -static-libgcc \
    -Wl,--subsystem,windows \
    -o "$OUT_FILE" \
    src/main.c

"$STRIP" "$OUT_FILE"

echo "Built: $OUT_FILE  ($(stat -c '%s' "$OUT_FILE") bytes)"
file "$OUT_FILE"
