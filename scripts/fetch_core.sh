#!/usr/bin/env bash
# Downloads the pinned core AAR (see core.lock) and verifies it. Never creates a placeholder:
# on any failure the partial file is deleted and the script exits non-zero.
# Usage: scripts/fetch_core.sh            # uses core.lock
#        scripts/fetch_core.sh <url> <sha256>   # override (e.g. your own mirror)
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck disable=SC1091
source core.lock
URL="${1:-$CORE_AAR_URL}"
SHA="${2:-$CORE_AAR_SHA256}"
mkdir -p app/libs
OUT=app/libs/libv2ray.aar
trap 'rm -f "$OUT.part"' EXIT
curl -fL --retry 3 "$URL" -o "$OUT.part"
echo "$SHA  $OUT.part" | sha256sum -c - || { echo "CHECKSUM MISMATCH - refusing to install" >&2; exit 1; }
mv "$OUT.part" "$OUT"
scripts/verify_core.sh "$OUT"
