# VPN core (required to actually tunnel traffic)

`app/libs/libv2ray.aar` is the official **2dust/AndroidLibXrayLite v26.11.9** release asset
(SHA-256 `cdfef146218233a31a8b52c8b1016557730d3b63fc121d4c84cacea50e669155`, pinned in `../../core.lock`).

- Re-fetch / repair: `scripts/fetch_core.sh` (downloads, verifies checksum, ABIs and Java API; deletes on mismatch).
- Re-verify: `scripts/verify_core.sh`.
- Self-build: GitHub workflow **Core AAR** with `mode=build`, or per upstream README:
  `gomobile bind -v -androidapi 24 -trimpath -ldflags='-s -w -buildid= -checklinkname=0' ./`
- The AAR is git-ignored (60 MB); CI fetches it. Never commit an empty or placeholder file here.
- Licenses: AndroidLibXrayLite LGPL-3.0, Xray-core MPL-2.0 — keep their notices when distributing the APK.
