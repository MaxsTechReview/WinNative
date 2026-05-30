#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../../rust/wn-steam-tools"
exec ./build-linux-glibc.sh
