#!/usr/bin/env bash
# Package a FEX UnixLibs component (.wcp) for WinNative.
#
# Payload layout (top dir -> WinNative content-path template; sub-paths are preserved):
#   lib/wine/aarch64-unix/<name>.so -> ${libdir}/wine/aarch64-unix/<name>.so  (FEX host UnixLibs)
#   system32/<name>                 -> ${system32}/<name>                     (FEX PE dlls)
#   syswow64/<name>                 -> ${syswow64}/<name>
#   bin/<name>                      -> ${bindir}/<name>
#
# A .wcp is an xz-compressed tar of profile.json + the payload tree.
#
# Usage:
#   tools/build_fex_unixlibs_wcp.sh -v <versionName> -c <versionCode> -p <payloadDir> -o <out.wcp>
set -euo pipefail

VERSION_NAME=""; VERSION_CODE="0"; PAYLOAD=""; OUT=""
while getopts "v:c:p:o:" opt; do
  case "$opt" in
    v) VERSION_NAME="$OPTARG" ;;
    c) VERSION_CODE="$OPTARG" ;;
    p) PAYLOAD="$OPTARG" ;;
    o) OUT="$OPTARG" ;;
    *) echo "bad option" >&2; exit 2 ;;
  esac
done

[ -n "$VERSION_NAME" ] && [ -n "$PAYLOAD" ] && [ -n "$OUT" ] || {
  echo "usage: $0 -v <versionName> -c <versionCode> -p <payloadDir> -o <out.wcp>" >&2; exit 2; }
[ -d "$PAYLOAD" ] || { echo "payload dir not found: $PAYLOAD" >&2; exit 1; }

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
cp -a "$PAYLOAD/." "$STAGE/"

python3 - "$STAGE" "$VERSION_NAME" "$VERSION_CODE" <<'PY'
import json, os, sys
stage, vname, vcode = sys.argv[1], sys.argv[2], int(sys.argv[3])
tpl = {"lib": "${libdir}", "system32": "${system32}", "syswow64": "${syswow64}", "bin": "${bindir}"}
files = []
for root, _, names in os.walk(stage):
    for n in sorted(names):
        rel = os.path.relpath(os.path.join(root, n), stage)
        parts = rel.split(os.sep, 1)
        top = parts[0]
        if top not in tpl or len(parts) < 2:
            continue
        files.append({"source": rel, "target": f"{tpl[top]}/{parts[1]}"})
files.sort(key=lambda f: f["source"])
profile = {"type": "FEXCore", "versionName": vname, "versionCode": vcode,
           "description": vname, "files": files}
with open(os.path.join(stage, "profile.json"), "w") as f:
    json.dump(profile, f, indent=2)
print(json.dumps(profile, indent=2))
PY

tar -C "$STAGE" -cJf "$OUT" .
echo "wrote $OUT ($(du -h "$OUT" | cut -f1))"
