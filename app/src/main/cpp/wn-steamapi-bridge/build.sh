#!/bin/bash
# build.sh — cross-compile the bridge DLL and stage it into assets/.
#
# Idempotent + standalone — drag the MinGW toolchain into PATH or
# install x86_64-w64-mingw32-gcc on the host and run this. Does NOT
# require gradle / ndk. APK packaging picks up the staged asset on
# next assembleStandardDebug.
set -euo pipefail

cd "$(dirname "$0")"
OUT="build/steam_api64.dll"
ASSET_DIR="../../assets/wnsteam/steampipe"
ASSET="$ASSET_DIR/steam_api64.dll"
GBE_SOURCE="../../../../../References/GameNative/app/src/main/assets/steampipe/steam_api64.dll"

# 1. Refresh gbe_fork export list (input to gen_forward_def.py).
if [ -f "$GBE_SOURCE" ]; then
    x86_64-w64-mingw32-objdump -p "$GBE_SOURCE" 2>/dev/null \
        | awk '/\[Ordinal\/Name Pointer\] Table/,/^$/' \
        | awk 'NR>1 && /\[/{print $NF}' \
        > /tmp/gbe_real.txt
fi

# 2. Generate vtable-dispatch forwarders (input to gen_overrides.py).
#    Output: steam_api_bridge_flat.c
python3 gen_forwarders.py

# 3. Generate .def forwarders to original_steam_api64.dll.
#    Output: steam_api_bridge.def
python3 gen_forward_def.py

# 4. Generate matchmaking-family override stubs.
#    Output: steam_api_bridge_overrides.c
python3 gen_overrides.py

mkdir -p build

# Build the hybrid bridge:
#   - steam_api_bridge_overrides.c: ~55 matchmaking exports we own
#   - steam_api_bridge.def: PE export forwards for the other ~1200
#     exports → original_steam_api64.dll (gbe_fork renamed at install).
#
# We deliberately DO NOT include steam_api_bridge_flat.c (full vtable
# forwarders). gbe_fork handles every non-matchmaking flat-C path, so
# compiling the generated forwarders here would conflict with the .def
# forwards (multiple definitions for the same export name).
x86_64-w64-mingw32-gcc -shared -O2 -fvisibility=hidden \
    -o "$OUT" \
    steam_api_bridge_overrides.c \
    steam_api_bridge_callbacks.c \
    steam_api_bridge_steamclient.c \
    steam_api_bridge_lifecycle.c \
    steam_api_bridge.def \
    -static-libgcc -lkernel32 -luser32 \
    -Wl,--enable-stdcall-fixup \
    -Wl,--kill-at

echo "[build.sh] PE built: $(ls -la "$OUT" | awk '{print $5}') bytes"

mkdir -p "$ASSET_DIR"
cp "$OUT" "$ASSET"
echo "[build.sh] Staged: $ASSET"

x86_64-w64-mingw32-objdump -p "$OUT" \
    | awk '/^\[Ordinal\/Name Pointer\] Table/{f=1;next} f && /^$/{exit} f{print}' \
    | head -40
