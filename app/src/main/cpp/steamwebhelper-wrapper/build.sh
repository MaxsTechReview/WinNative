#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/../../../../.."

out="/tmp/winnative-steamwebhelper-wrapper"
aarch64-linux-gnu-gcc -O2 -Wall -Wextra -static \
    -o "$out" \
    app/src/main/cpp/steamwebhelper-wrapper/steamwebhelper_wrapper.c
aarch64-linux-gnu-strip "$out"

cp -f "$out" app/src/main/assets/xvfb-arm64/winnative-steamwebhelper-wrapper
sha256sum app/src/main/assets/xvfb-arm64/winnative-steamwebhelper-wrapper
