#!/usr/bin/env bash
# Verifies app/libs/libv2ray.aar against core.lock:
#  1. SHA-256 of the AAR, of classes.jar and of every native lib
#  2. required ABIs are present
#  3. the Java API used by XrayCoreEngine really exists (javap)
# Usage: scripts/verify_core.sh [path/to/libv2ray.aar]
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck disable=SC1091
source core.lock
AAR="${1:-app/libs/libv2ray.aar}"
[ -s "$AAR" ] || { echo "missing or empty: $AAR" >&2; exit 1; }
W="$(mktemp -d)"; trap 'rm -rf "$W"' EXIT
fail() { echo "FAIL: $*" >&2; exit 1; }

echo "$CORE_AAR_SHA256  $AAR" | sha256sum -c - >/dev/null || fail "AAR sha256 mismatch"
unzip -q "$AAR" -d "$W"
echo "$CORE_CLASSES_JAR_SHA256  $W/classes.jar" | sha256sum -c - >/dev/null || fail "classes.jar sha256 mismatch"
for abi in arm64-v8a armeabi-v7a x86 x86_64; do
  var="CORE_SO_SHA256_${abi//-/_}"
  [ -f "$W/jni/$abi/libgojni.so" ] || fail "missing ABI $abi"
  echo "${!var}  $W/jni/$abi/libgojni.so" | sha256sum -c - >/dev/null || fail "libgojni.so ($abi) sha256 mismatch"
done

J="javap -cp $W/classes.jar -public"
need() { # class, signature fragment
  $J "$1" 2>/dev/null | grep -qF "$2" || fail "API missing: $1 :: $2"
}
need libv2ray.Libv2ray          "static native void initCoreEnv(java.lang.String, java.lang.String)"
need libv2ray.Libv2ray          "static native libv2ray.CoreController newCoreController(libv2ray.CoreCallbackHandler)"
need libv2ray.Libv2ray          "static native java.lang.String checkVersionX()"
need libv2ray.CoreController    "native void startLoop(java.lang.String, int) throws java.lang.Exception"
need libv2ray.CoreController    "native void stopLoop() throws java.lang.Exception"
need libv2ray.CoreController    "native long measureDelay(java.lang.String) throws java.lang.Exception"
need libv2ray.CoreController    "native boolean getIsRunning()"
need libv2ray.CoreCallbackHandler "long startup()"
need libv2ray.CoreCallbackHandler "long shutdown()"
need libv2ray.CoreCallbackHandler "long onEmitStatus(long, java.lang.String)"
echo "OK: $AAR matches core.lock ($CORE_REPO $CORE_TAG), API verified"
