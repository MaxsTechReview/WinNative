#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC_DIR="$ROOT_DIR/tools/xinput_virtual_shim"
BUILD_DIR="$ROOT_DIR/build/xinput_virtual_shim"
ASSET_DIR="$ROOT_DIR/app/src/main/assets/wincomponents"
PACKAGE_ROOT="$BUILD_DIR/package"
ASSET_PATH="$ASSET_DIR/xinput_virtual.tzst"

DLL_NAMES=(
  xinput1_1.dll
  xinput1_2.dll
  xinput1_3.dll
  xinput1_4.dll
  xinput9_1_0.dll
  xinputuap.dll
)

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/x64" "$BUILD_DIR/x86" "$PACKAGE_ROOT/system32" "$PACKAGE_ROOT/syswow64" "$ASSET_DIR"

x86_64-w64-mingw32-gcc \
  -O2 -s -shared \
  -Wall -Wextra \
  -Wl,--kill-at \
  "$SRC_DIR/xinput_virtual.c" \
  "$SRC_DIR/xinput_virtual.def" \
  -o "$BUILD_DIR/x64/xinput_virtual.dll"

i686-w64-mingw32-gcc \
  -O2 -s -shared \
  -Wall -Wextra \
  -Wl,--kill-at \
  "$SRC_DIR/xinput_virtual.c" \
  "$SRC_DIR/xinput_virtual.def" \
  -o "$BUILD_DIR/x86/xinput_virtual.dll"

for dll in "${DLL_NAMES[@]}"; do
  cp "$BUILD_DIR/x64/xinput_virtual.dll" "$PACKAGE_ROOT/system32/$dll"
  cp "$BUILD_DIR/x86/xinput_virtual.dll" "$PACKAGE_ROOT/syswow64/$dll"
done

tar -C "$PACKAGE_ROOT" -cf "$BUILD_DIR/xinput_virtual.tar" .
zstd -19 -f "$BUILD_DIR/xinput_virtual.tar" -o "$ASSET_PATH"

echo "Built $ASSET_PATH"
